import { createCipheriv, createDecipheriv, createHash, randomBytes } from "node:crypto";

/**
 * "Beni Beğenenler" listesinin SAF (Firestore'a dokunmayan) yardımcıları.
 *
 * GİZLİLİK MODELİ: kilitli bir girişte beğenenin uid'si istemciye ASLA gönderilmez — uid ile
 * `public_profiles/{uid}` doğrudan okunabildiği için bulanıklaştırma anlamsız kalırdı. Bunun
 * yerine yalnızca bu izleyiciye özel, şifreli ve opak bir `entryToken` döner; kilit açma
 * (`unlockLikedMe`) bu jetonu sunucuda çözer.
 */

const TOKEN_VERSION = 1;
const IV_LENGTH = 12;
const TAG_LENGTH = 16;

/** Gizli anahtar metninden 32 baytlık AES-256 anahtarı türetir. */
export function deriveTokenKey(secret: string): Buffer {
    if (secret.trim().length < 16) throw new Error("LIKED_ME_TOKEN_SECRET en az 16 karakter olmalı");
    return createHash("sha256").update(`liked-me-token:v${TOKEN_VERSION}:${secret}`).digest();
}

/** İzleyici ve beğenen uid'sini AES-256-GCM ile şifreleyip base64url jeton üretir. */
export function encodeEntryToken(key: Buffer, viewerUid: string, likerUid: string): string {
    const iv = randomBytes(IV_LENGTH);
    const cipher = createCipheriv("aes-256-gcm", key, iv);
    const ciphertext = Buffer.concat([cipher.update(`${viewerUid}\n${likerUid}`, "utf8"), cipher.final()]);
    return Buffer.concat([iv, ciphertext, cipher.getAuthTag()]).toString("base64url");
}

/**
 * Jetonu çözer. Jeton bozuksa, başka bir anahtarla üretildiyse veya BAŞKA bir izleyiciye
 * aitse `null` döner (bir kullanıcı başkasının jetonuyla kilit açamaz).
 */
export function decodeEntryToken(key: Buffer, token: string, expectedViewerUid: string): string | null {
    try {
        const raw = Buffer.from(token, "base64url");
        if (raw.length <= IV_LENGTH + TAG_LENGTH) return null;
        const iv = raw.subarray(0, IV_LENGTH);
        const tag = raw.subarray(raw.length - TAG_LENGTH);
        const ciphertext = raw.subarray(IV_LENGTH, raw.length - TAG_LENGTH);
        const decipher = createDecipheriv("aes-256-gcm", key, iv);
        decipher.setAuthTag(tag);
        const plain = Buffer.concat([decipher.update(ciphertext), decipher.final()]).toString("utf8");
        const [viewerUid, likerUid, ...rest] = plain.split("\n");
        if (rest.length > 0 || viewerUid !== expectedViewerUid || !likerUid) return null;
        return likerUid;
    } catch {
        return null;
    }
}

export interface LikerCandidate {
    likerUid: string;
    likedAt: number;
    /** İzleyici bu kişiyi zaten beğendi/geçti mi (eşleşme veya ret)? */
    viewerAlreadyActed: boolean;
    /** Bu izleyici için kilidi daha önce açıldı mı? */
    unlocked: boolean;
    /** Herkese açık profil (yoksa veya aktif değilse `null`). */
    profile: PublicLikerProfile | null;
}

export interface PublicLikerProfile {
    displayName: string;
    /** Sunucuda hesaplanmış yaş (public_profiles.age); doğum tarihi burada YOKTUR. */
    age: number | null;
    countryCode: string | null;
    languageCode: string | null;
    photoUrl: string | null;
    bio: string;
    verified: boolean;
    isMookActive: boolean;
    incognito: boolean;
    visibleTo: string[];
}

export interface LikedMeEntry {
    entryToken: string;
    likedAt: number;
    unlocked: boolean;
    /** Bulanık önizleme veya açık profil için fotoğraf. */
    photoUrl: string | null;
    /** Yalnızca kilit açıksa dolu. */
    profile: {
        uid: string;
        displayName: string;
        age: number | null;
        countryCode: string | null;
        languageCode: string | null;
        bio: string;
        verified: boolean;
    } | null;
}

/**
 * Aday listesinden istemciye dönecek girişleri üretir.
 *
 * KURALLAR:
 * 1. İzleyicinin zaten beğendiği (eşleşme) veya geçtiği kişiler listelenmez.
 * 2. Profili silinmiş, aktif olmayan veya izleyiciye görünmeyen (gizli mod) kişiler listelenmez.
 * 3. [revealAll] (Premium) veya daha önce açılmış girişlerde profil açık döner; diğerlerinde
 *    yalnızca opak jeton ve bulanıklaştırılacak önizleme fotoğrafı döner.
 * 4. En yeni beğeni en üstte.
 */
export function planLikedMeEntries(input: {
    viewerUid: string;
    candidates: LikerCandidate[];
    revealAll: boolean;
    now: number;
    makeToken: (likerUid: string) => string;
}): LikedMeEntry[] {
    return input.candidates
        .filter((candidate) => !candidate.viewerAlreadyActed && candidate.likerUid !== input.viewerUid)
        .filter((candidate) => {
            const profile = candidate.profile;
            if (!profile || !profile.isMookActive) return false;
            return !profile.incognito || profile.visibleTo.includes(input.viewerUid);
        })
        .sort((a, b) => b.likedAt - a.likedAt)
        .map((candidate) => {
            const profile = candidate.profile as PublicLikerProfile;
            const unlocked = input.revealAll || candidate.unlocked;
            return {
                entryToken: input.makeToken(candidate.likerUid),
                likedAt: candidate.likedAt,
                unlocked,
                photoUrl: profile.photoUrl,
                profile: unlocked ? {
                    uid: candidate.likerUid,
                    displayName: profile.displayName,
                    age: profile.age,
                    countryCode: profile.countryCode,
                    languageCode: profile.languageCode,
                    bio: profile.bio,
                    verified: profile.verified,
                } : null,
            };
        });
}
