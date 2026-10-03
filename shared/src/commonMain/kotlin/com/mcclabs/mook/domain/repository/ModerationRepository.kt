package com.mcclabs.mook.domain.repository

import kotlinx.coroutines.flow.StateFlow

/**
 * Gereksinim 1.13 (Moderasyon & Yasaklamalar): sunucunun (backend) giriş yapmış kullanıcıya
 * uyguladığı yasaklama durumunu CANLI olarak gözlemler.
 *
 * `users/{uid}.isBanned` sunucu tarafında (Admin SDK ile, `firestore.rules`'ta istemci
 * yazmasına kapalı — bkz. `moderateUser` Cloud Function'ı) ayarlanır; bu depo yalnızca o
 * alanı gerçek zamanlı dinler ve okunabilir bir [StateFlow]'a çevirir.
 */
interface ModerationRepository {
    /** `true` olduğu AN kullanıcı zorla çıkış yapılmalı ve tüm API çağrıları durmalıdır. */
    val isBanned: StateFlow<Boolean>
}
