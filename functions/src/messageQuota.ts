/**
 * Mesaj kotası için SAF (Firestore'a dokunmayan) planlayıcı — "Yalnızca Çeviri Kotası" kuralı.
 *
 * KURALLAR:
 * 1. Mesaj SAYISI (FREE/ECONOMY günlük tavanı), dakikalık hız sınırı ve yeni sohbet tavanı
 *    her mesaj için geçerlidir; aşılırsa `MessageQuotaError` fırlatılır ve mesaj gönderilmez.
 * 2. Karakter kotası YALNIZCA gerçekten çeviri servisine (DeepL) gidecek mesajlarda kontrol
 *    edilir ve düşülür. Aynı dildeki mesajlarda `translationChars = 0` geçilir; kota ne
 *    kontrol edilir ne de düşülür.
 * 3. Karakter kotası yetmiyorsa mesaj ENGELLENMEZ: çeviri atlanır, hiçbir karakter
 *    düşülmez ve `translationQuotaExhausted = true` döner (yumuşak geri dönüş). İstemci
 *    kullanıcıya mesajın çevrilmeden gönderildiğini bildirir.
 */

export const DAILY_MESSAGE_LIMIT_ERROR = "daily-message-limit";
export const RATE_LIMIT_ERROR = "rate-limit-exceeded";
export const DAILY_NEW_CHAT_LIMIT_ERROR = "daily-new-chat-limit";

export class MessageQuotaError extends Error {
    constructor(readonly code: string) {
        super(code);
        this.name = "MessageQuotaError";
    }
}

export interface MessageQuotaCounters {
    messages?: number;
    newChats?: number;
    translationCharsDaily?: number;
    translationCharsMonthly?: number;
    messageWindowStart?: number;
    messageWindowCount?: number;
}

export interface MessageQuotaInput {
    /** Günlük/aylık sıfırlaması ZATEN uygulanmış kullanım sayaçları. */
    usage: MessageQuotaCounters;
    /** Günlük mesaj sayısı tavanı; `null` = sayı tavanı yok. */
    dailyMessages: number | null;
    /** Günlük yeni sohbet tavanı; `null` = sınırsız. */
    dailyNewChats: number | null;
    dailyChars: number;
    monthlyChars: number;
    isNewChat: boolean;
    /** Çeviriye gidecek karakter sayısı; çeviri yapılmayacaksa 0. */
    translationChars: number;
    now: number;
    rateLimit: number;
    rateWindowMs: number;
}

export interface MessageQuotaPlan {
    /** Yazılacak güncel sayaçlar. */
    counters: Required<MessageQuotaCounters>;
    /** DeepL çağrılmalı mı? (karakterler bu durumda düşülmüştür) */
    translate: boolean;
    /** Çeviri istendi ama karakter kotası yetmedi mi? */
    translationQuotaExhausted: boolean;
}

export function planMessageQuota(input: MessageQuotaInput): MessageQuotaPlan {
    const usage = input.usage;
    const messages = Number(usage.messages ?? 0);
    if (input.dailyMessages !== null && messages >= input.dailyMessages) {
        throw new MessageQuotaError(DAILY_MESSAGE_LIMIT_ERROR);
    }

    const windowStart = Number(usage.messageWindowStart ?? 0);
    const windowCount = input.now - windowStart >= input.rateWindowMs ? 0 : Number(usage.messageWindowCount ?? 0);
    if (windowCount >= input.rateLimit) {
        throw new MessageQuotaError(RATE_LIMIT_ERROR);
    }

    let newChats = Number(usage.newChats ?? 0);
    if (input.isNewChat) {
        if (input.dailyNewChats !== null && newChats >= input.dailyNewChats) {
            throw new MessageQuotaError(DAILY_NEW_CHAT_LIMIT_ERROR);
        }
        newChats += 1;
    }

    const charsToday = Number(usage.translationCharsDaily ?? 0);
    const charsThisMonth = Number(usage.translationCharsMonthly ?? 0);
    const requested = Math.max(0, Math.floor(input.translationChars));
    const fits = charsToday + requested <= input.dailyChars && charsThisMonth + requested <= input.monthlyChars;
    const translate = requested > 0 && fits;

    return {
        counters: {
            messages: messages + 1,
            newChats,
            translationCharsDaily: translate ? charsToday + requested : charsToday,
            translationCharsMonthly: translate ? charsThisMonth + requested : charsThisMonth,
            messageWindowStart: windowCount === 0 ? input.now : windowStart,
            messageWindowCount: windowCount + 1,
        },
        translate,
        translationQuotaExhausted: requested > 0 && !fits,
    };
}
