package com.mcclabs.mook.domain.billing

/**
 * Bir kademe için günlük ve aylık karakter tabanlı çeviri kotası (Gereksinim 2.2).
 *
 * `null` bir değer taşımaz — sınırsız kademe (ör. eskiden Premium/Standart'ın mesaj
 * sayısı sınırsızdı) burada artık YOK, çünkü asıl maliyet mesaj SAYISI değil, DeepL'e
 * gönderilen karakter HACMİDİR. Her kademe daima somut bir tavana sahiptir; en cömert
 * kademe bile (Premium) sınırsız değildir, yalnızca en yüksek tavana sahiptir.
 */
data class CharacterQuotaLimits(
    val dailyChars: Int,
    val monthlyChars: Int,
)

/** [CharacterQuotaManager.check] sonucunun hangi pencerede (gün/ay) tükendiğini belirtir. */
enum class CharacterQuotaScope { DAILY, MONTHLY }

/**
 * [CharacterQuotaManager.check]'in dönebileceği durumlar.
 *
 * `QuotaExhausted`, hangi pencerenin (günlük mü aylık mı) dolduğunu taşır — sunum
 * katmanı bu bilgiyle iki farklı Türkçe metin arasında seçim yapabilir (ör. "bugünkü
 * çeviri hakkın bitti" ile "bu ayki çeviri hakkın bitti").
 */
sealed interface CharacterQuotaCheck {
    data object Allowed : CharacterQuotaCheck
    data class QuotaExhausted(val scope: CharacterQuotaScope) : CharacterQuotaCheck
}

/**
 * Kademe başına varsayılan günlük/aylık karakter kotaları.
 *
 * Değerler `functions/src/monetization.ts` içindeki `CHARACTER_QUOTA_LIMITS` sabitiyle
 * BİLİNÇLİ olarak birebir aynı tutulur — asıl zorlama her zaman sunucudadır (bkz.
 * [CharacterQuotaManager] sınıf yorumu), bu tablo yalnızca istemcinin aynı sınırları
 * önceden tahmin edip UI'da anında geri bildirim verebilmesi içindir. İki taraf
 * birbirinden sapabilir diye burada yalnızca TEK bir kaynak (bu dosya + sunucudaki
 * karşılığı) tutulur; sayılar değiştirilecekse ikisi birlikte güncellenmelidir.
 */
object DefaultCharacterQuotas {
    val byTier: Map<Tier, CharacterQuotaLimits> = mapOf(
        Tier.FREE to CharacterQuotaLimits(dailyChars = 1_000, monthlyChars = 10_000),
        Tier.ECONOMY to CharacterQuotaLimits(dailyChars = 2_500, monthlyChars = 25_000),
        Tier.STANDARD to CharacterQuotaLimits(dailyChars = 5_000, monthlyChars = 50_000),
        Tier.PREMIUM to CharacterQuotaLimits(dailyChars = 10_000, monthlyChars = 100_000),
    )
}

/**
 * Karakter tabanlı çeviri kotasını değerlendiren saf (side-effect'siz) domain sınıfı
 * (Gereksinim 2.2).
 *
 * ## Neden mesaj SAYISI değil karakter SAYISI?
 * Eski model her kademeye günlük düz bir mesaj sayısı tavanı (ör. sınırsız kademeler
 * için 2.000 mesaj/gün) koyuyordu. Ancak DeepL'e ödenen gerçek maliyet mesaj başına
 * değil, karakter başınadır — 10 karakterlik "Selam" ile 1.000 karakterlik uzun bir
 * paragraf sunucuya AYNI "1 mesaj" olarak yansıyordu. Bu, uzun mesajlar göndererek
 * asıl çeviri maliyetini kotanın çok üzerine taşımayı mümkün kılan bir birim ekonomisi
 * açığıydı. Bu sınıf, tavanı doğrudan harcanan karaktere bağlayarak bu açığı kapatır.
 *
 * ## Sorumluluk sınırı (SOLID: Single Responsibility)
 * Bu sınıf YALNIZCA "şu an gönderilmek istenen mesaj, kalan kotaya sığar mı?" sorusunu
 * yanıtlar — ne kotanın nasıl saklandığından (Firestore `usage/{uid}` belgesi), ne
 * günlük/aylık sıfırlama zamanlamasından (sunucudaki `resetUsage()`), ne de UI'da
 * hangi metnin gösterileceğinden ([CharacterQuotaScope]'u sunum katmanına bırakır)
 * haberdardır. Kullanılan gerçek karakter sayıları çağıran tarafından (ViewModel/
 * Repository) sağlanır; bu sınıf hiçbir global/mutable durum tutmaz, bu da onu hem
 * tamamen deterministik hem de birim testinde sahte (mock) bağımlılık gerektirmeyecek
 * kadar basit kılar.
 *
 * ## Asıl zorlama sunucudadır
 * Tıpkı [FeatureGate] ve `enforceMessageQuota` (functions/src/monetization.ts) gibi:
 * istemci tarafındaki bu kontrol yalnızca anında geri bildirim (ör. gönder düğmesini
 * önceden devre dışı bırakma) için vardır. Gerçek yaptırım her zaman Cloud Function
 * içindeki atomik Firestore işleminde uygulanır — istemci atlanır/manipüle edilirse
 * bile sunucu asıl kotayı korur.
 */
class CharacterQuotaManager(
    private val quotasByTier: Map<Tier, CharacterQuotaLimits> = DefaultCharacterQuotas.byTier,
) {

    /**
     * [messageLength] karakterlik bir mesaj, [tier] kademesinin kotasına sığar mı?
     *
     * @param charsUsedToday Bu takvim günü içinde şimdiye kadar harcanan karakter sayısı
     *   (mesaj HENÜZ eklenmemiş haliyle).
     * @param charsUsedThisMonth Bu takvim ayı içinde şimdiye kadar harcanan karakter
     *   sayısı (mesaj HENÜZ eklenmemiş haliyle).
     */
    fun check(
        tier: Tier,
        messageLength: Int,
        charsUsedToday: Int,
        charsUsedThisMonth: Int,
    ): CharacterQuotaCheck {
        val limits = quotasByTier[tier] ?: return CharacterQuotaCheck.Allowed
        return when {
            // Günlük kontrol önce yapılır: bir kullanıcı aylık kotası dolmadan önce
            // neredeyse her zaman günlük kotasını dolduracaktır, bu yüzden günlük mesaj
            // kullanıcıya daha erken ve daha doğru bir sinyal verir.
            charsUsedToday + messageLength > limits.dailyChars ->
                CharacterQuotaCheck.QuotaExhausted(CharacterQuotaScope.DAILY)
            charsUsedThisMonth + messageLength > limits.monthlyChars ->
                CharacterQuotaCheck.QuotaExhausted(CharacterQuotaScope.MONTHLY)
            else -> CharacterQuotaCheck.Allowed
        }
    }

    /** [tier] için bugün kalan karakter hakkı, ya da tanımsız bir kademe için `null`. */
    fun remainingDailyChars(tier: Tier, charsUsedToday: Int): Int? =
        quotasByTier[tier]?.let { (it.dailyChars - charsUsedToday).coerceAtLeast(0) }

    /** [tier] için bu ay kalan karakter hakkı, ya da tanımsız bir kademe için `null`. */
    fun remainingMonthlyChars(tier: Tier, charsUsedThisMonth: Int): Int? =
        quotasByTier[tier]?.let { (it.monthlyChars - charsUsedThisMonth).coerceAtLeast(0) }
}
