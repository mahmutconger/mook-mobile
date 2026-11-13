/**
 * Kullanıcı engelleme kuralları — SAF yardımcılar (Firestore'a dokunmaz).
 *
 * Engelleme listesi `users/{uid}.blockedUsers` dizisinde tutulur ve istemci tarafından
 * (`ProfileDetailsViewModel.confirmBlock`) yazılır. Bir engel İKİ YÖNLÜDÜR: A, B'yi
 * engellediyse ne A B'ye ne de B A'ya mesaj gönderebilir, beğeni bırakabilir veya
 * Keşfet'te onu görebilir. Bu dosya, aynı kuralın `sendMessage`, `swipe`
 * (beğeni/geçme) ve `getDiscoverFeed` arasında birebir aynı uygulanmasını sağlar.
 */

/** Engel bilgisi sızdırmadan istemciye dönen ortak hata mesajı. */
export const BLOCKED_USER_ERROR = "blocked-user";

/**
 * Bir kullanıcı belgesinden engellenen uid'leri okur.
 *
 * Alan yoksa, dizi değilse veya içinde metin olmayan girdiler varsa bunlar sessizce
 * yok sayılır — bozuk bir alan hiçbir zaman "herkes engelli" ya da hata anlamına gelmez.
 */
export function blockedUsersOf(userData: Record<string, unknown> | undefined | null): Set<string> {
    const raw = userData?.blockedUsers;
    if (!Array.isArray(raw)) return new Set();
    return new Set(raw.filter((entry): entry is string => typeof entry === "string" && entry.length > 0));
}

/**
 * İki kullanıcı arasında HERHANGİ bir yönde engel olup olmadığını döner.
 *
 * @param uid İsteği yapan kullanıcının uid'i.
 * @param ownData İsteği yapan kullanıcının `users` belgesi.
 * @param peerUid Karşı tarafın uid'i.
 * @param peerData Karşı tarafın `users` belgesi.
 */
export function isBlockedBetween(
    uid: string,
    ownData: Record<string, unknown> | undefined | null,
    peerUid: string,
    peerData: Record<string, unknown> | undefined | null,
): boolean {
    return blockedUsersOf(ownData).has(peerUid) || blockedUsersOf(peerData).has(uid);
}
