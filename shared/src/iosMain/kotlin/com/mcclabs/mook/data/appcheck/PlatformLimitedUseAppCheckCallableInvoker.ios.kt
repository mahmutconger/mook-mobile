package com.mcclabs.mook.data.appcheck

import com.mcclabs.mook.domain.appcheck.AppCheckCallResult
import com.mcclabs.mook.domain.appcheck.LimitedUseAppCheckCallableInvoker

actual fun createLimitedUseAppCheckCallableInvoker(): LimitedUseAppCheckCallableInvoker =
    NoOpLimitedUseAppCheckCallableInvoker

/**
 * iOS reklam/analiz entegrasyonu ile AYNI gerekçe (bkz. `PlatformConsentRepository.ios.kt`
 * KDoc'u): bu görev tanımı KASITLI olarak "Focus EXCLUSIVELY on Android" ile SINIRLANDIRILDI.
 * Bu, yalnızca `expect`/`actual` sözleşmesini KMP derlemesi için yerine getiren, GÜVENLİ
 * (her zaman [AppCheckCallResult.Unavailable] dönen) bir gövdedir -- iOS App Check (DeviceCheck/
 * App Attest tabanlı) teklifi canlıya alındığında gerçek bir implementasyonla değiştirilmelidir.
 */
private object NoOpLimitedUseAppCheckCallableInvoker : LimitedUseAppCheckCallableInvoker {
    override suspend fun invoke(
        functionName: String,
        payload: Map<String, Any?>,
    ): AppCheckCallResult<Map<String, Any?>> = AppCheckCallResult.Unavailable
}
