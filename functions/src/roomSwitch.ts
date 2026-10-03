/**
 * Oda değiştirme kararını veren SAF (yan etkisiz, Firestore'a dokunmayan) planlayıcı.
 *
 * `switchRoom` callable'ı bu fonksiyonu transaction İÇİNDE okuduğu belgelerle çağırır ve
 * dönen planı aynı transaction'da yazar; böylece slot seçimi, günlük hak düşümü ve aktif oda
 * tek bir atomik birim olarak değişir. Mantığın ayrı tutulması, emülatör gerektirmeden birim
 * testine izin verir (bkz. `test/roomSwitch.test.js`).
 *
 * KURALLAR ("Atomik Oda Değiştirme"):
 * 1. Hedef oda zaten aktif oda ise hiçbir şey yapılmaz ve hak düşülmez.
 * 2. İlk (zorunlu) oda seçimi onboarding'dir; hak düşülmez.
 * 3. Günlük hak kontrolü kapasite kontrolünden ÖNCE yapılır ve aşılırsa her zaman
 *    `daily-room-switch-limit` hatası verilir (istemci bunu limit sayfasına çevirir).
 * 4. Hedef oda kullanıcının açık odaları arasındaysa yalnızca aktif oda değişir.
 * 5. Hedef oda yeni ise ve slotlar doluysa, kapasite hatası VERİLMEZ: en uzun süredir
 *    aktif kullanılmayan oda(lar) çıkarılır ve yerine yeni oda eklenir (atomik takas).
 *    Bir kademe düşüşü sonrası slot sayısı aşılmışsa, aynı takas fazlalığı da temizler.
 * 6. Sınırsız slot (`roomSlots === null`) planlarda hiçbir oda çıkarılmaz.
 */

export const DAILY_ROOM_SWITCH_LIMIT_ERROR = "daily-room-switch-limit";

export class RoomSwitchLimitError extends Error {
    constructor() {
        super(DAILY_ROOM_SWITCH_LIMIT_ERROR);
        this.name = "RoomSwitchLimitError";
    }
}

export interface RoomSwitchInput {
    /** Kullanıcının şu an açık olan odaları (eklenme sırasına göre). */
    rooms: string[];
    /** Şu an aktif oda; hiç oda seçilmemişse `null`. */
    activeRoom: string | null;
    /** Oda kodu → en son aktif yapıldığı an (epoch ms). Eksik kayıt 0 sayılır. */
    lastActiveAt: Record<string, number>;
    /** Geçilmek istenen oda kodu. */
    target: string;
    /** Planın aynı anda açık tutabileceği oda sayısı; `null` = sınırsız. */
    roomSlots: number | null;
    /** Bugün harcanmış oda değiştirme hakkı. */
    switchesUsedToday: number;
    /** Bugünkü toplam hak (ödüllü bonus dahil); `null` = sınırsız. */
    dailySwitchLimit: number | null;
}

export type RoomSwitchPlan =
    | { kind: "noop"; rooms: string[]; activeRoom: string }
    | {
        kind: "switch";
        rooms: string[];
        activeRoom: string;
        /** Takas nedeniyle kapatılan odalar (boş olabilir). */
        evictedRooms: string[];
        /** Bu geçiş günlük haktan düşülecek mi? */
        chargesQuota: boolean;
    };

export function planRoomSwitch(input: RoomSwitchInput): RoomSwitchPlan {
    const rooms = [...new Set(input.rooms)];
    if (input.activeRoom === input.target) {
        return { kind: "noop", rooms, activeRoom: input.target };
    }

    // Kural 2: hiç odası olmayan kullanıcının ilk seçimi ücretsizdir.
    const chargesQuota = rooms.length > 0;
    // Kural 3: günlük hak, kapasiteden ÖNCE kontrol edilir.
    if (chargesQuota && input.dailySwitchLimit !== null && input.switchesUsedToday >= input.dailySwitchLimit) {
        throw new RoomSwitchLimitError();
    }

    // Kural 4: zaten açık bir odaya geçiş — slot değişmez.
    if (rooms.includes(input.target)) {
        return { kind: "switch", rooms, activeRoom: input.target, evictedRooms: [], chargesQuota };
    }

    // Kural 5 ve 6: yeni oda için yer aç (gerekirse en eski kullanılanı çıkar).
    const capacity = input.roomSlots === null ? Number.POSITIVE_INFINITY : Math.max(1, input.roomSlots);
    const kept = [...rooms];
    const evictedRooms: string[] = [];
    while (kept.length >= capacity) {
        const victim = leastRecentlyUsed(kept, input.lastActiveAt);
        kept.splice(kept.indexOf(victim), 1);
        evictedRooms.push(victim);
    }
    return { kind: "switch", rooms: [...kept, input.target], activeRoom: input.target, evictedRooms, chargesQuota };
}

/** En küçük `lastActiveAt` değerine sahip odayı döner; eşitlikte listede önce gelen seçilir. */
function leastRecentlyUsed(rooms: string[], lastActiveAt: Record<string, number>): string {
    return rooms.reduce((oldest, room) =>
        (lastActiveAt[room] ?? 0) < (lastActiveAt[oldest] ?? 0) ? room : oldest, rooms[0]);
}

export interface RoomTrimInput {
    /** Kullanıcının şu an açık olan odaları (eklenme sırasına göre). */
    rooms: string[];
    /** Şu an aktif oda; hiç oda seçilmemişse `null`. */
    activeRoom: string | null;
    /** Oda kodu → en son aktif yapıldığı an (epoch ms). Eksik kayıt 0 sayılır. */
    lastActiveAt: Record<string, number>;
    /** Yeni planın aynı anda açık tutabileceği oda sayısı; `null` = sınırsız. */
    roomSlots: number | null;
}

export interface RoomTrimPlan {
    /** Korunan odalar (orijinal eklenme sırası korunur). */
    rooms: string[];
    /** Kırpma sonrası aktif oda. */
    activeRoom: string | null;
    /** Sunucu tarafından kapatılan fazla odalar (boşsa değişiklik yoktur). */
    closedRooms: string[];
}

/**
 * Kademe düşüşü (ör. Premium → Ücretsiz) sonrasında, yeni planın slot sayısını aşan odaları
 * kapatan SAF planlayıcı. RevenueCat senkronizasyonu (`revenuecatSync.ts`) bunu transaction
 * içinde çağırır; istemcinin `closeRoomSlots` çağırmasını BEKLEMEDEN fazlalık sunucuda temizlenir.
 *
 * KURALLAR:
 * 1. Sınırsız slot (`null`) veya slot sayısı aşılmamışsa hiçbir oda kapatılmaz.
 * 2. Aktif oda HER ZAMAN korunur (kullanıcı uygulamayı açtığında aynı odada kalır).
 * 3. Kalan slotlar en son aktif kullanılan odalara verilir; eşitlikte listede önce gelen kazanır.
 * 4. Aktif oda yoksa (veya listede değilse), korunanlar içinden en son kullanılan aktif olur.
 * 5. Slot sayısı en az 1 kabul edilir — kullanıcı asla odasız bırakılmaz.
 */
export function planRoomTrim(input: RoomTrimInput): RoomTrimPlan {
    const rooms = [...new Set(input.rooms)];
    const capacity = input.roomSlots === null ? Number.POSITIVE_INFINITY : Math.max(1, input.roomSlots);
    const activeInRooms = input.activeRoom !== null && rooms.includes(input.activeRoom);
    if (rooms.length <= capacity) {
        const activeRoom = activeInRooms || rooms.length === 0
            ? input.activeRoom
            : mostRecentlyUsed(rooms, input.lastActiveAt);
        return { rooms, activeRoom, closedRooms: [] };
    }

    // Öncelik sırası: önce aktif oda, ardından en son kullanılandan en eskiye diğerleri.
    const others = rooms
        .map((room, index) => ({ room, index }))
        .filter(({ room }) => !(activeInRooms && room === input.activeRoom))
        .sort((a, b) =>
            (input.lastActiveAt[b.room] ?? 0) - (input.lastActiveAt[a.room] ?? 0) || a.index - b.index)
        .map(({ room }) => room);
    const priority = activeInRooms ? [input.activeRoom as string, ...others] : others;
    const keep = new Set(priority.slice(0, capacity));
    const kept = rooms.filter((room) => keep.has(room));
    const closedRooms = rooms.filter((room) => !keep.has(room));
    const activeRoom = activeInRooms ? input.activeRoom : mostRecentlyUsed(kept, input.lastActiveAt);
    return { rooms: kept, activeRoom, closedRooms };
}

/** En büyük `lastActiveAt` değerine sahip odayı döner; eşitlikte listede önce gelen seçilir. */
function mostRecentlyUsed(rooms: string[], lastActiveAt: Record<string, number>): string {
    return rooms.reduce((best, room) =>
        (lastActiveAt[room] ?? 0) > (lastActiveAt[best] ?? 0) ? room : best, rooms[0]);
}
