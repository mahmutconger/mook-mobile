package com.mcclabs.mook.data.appcheck

import com.google.android.gms.tasks.Task
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.HttpsCallableOptions
import com.mcclabs.mook.domain.appcheck.AppCheckCallResult
import com.mcclabs.mook.domain.appcheck.LimitedUseAppCheckCallableInvoker
import com.mcclabs.mook.util.Log
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

actual fun createLimitedUseAppCheckCallableInvoker(): LimitedUseAppCheckCallableInvoker =
    AndroidLimitedUseAppCheckCallableInvoker

/** bkz. [LimitedUseAppCheckCallableInvoker] arayüz KDoc'u (tam gerekçe orada). */
private object AndroidLimitedUseAppCheckCallableInvoker : LimitedUseAppCheckCallableInvoker {
    // `HttpsCallableOptions` sabittir (tek bir bayrak taşır) -- her çağrıda yeniden
    // OLUŞTURULMAZ, bu nesne yaşadığı sürece TEK BİR ÖRNEK paylaşılır.
    private val limitedUseOptions = HttpsCallableOptions.Builder()
        .setLimitedUseAppCheckTokens(true)
        .build()

    override suspend fun invoke(
        functionName: String,
        payload: Map<String, Any?>,
    ): AppCheckCallResult<Map<String, Any?>> {
        val callable = FirebaseFunctions.getInstance().getHttpsCallable(functionName, limitedUseOptions)
        return try {
            val task = if (payload.isEmpty()) callable.call() else callable.call(payload)
            val result = task.awaitTask()
            @Suppress("UNCHECKED_CAST")
            val data = (result.data as? Map<String, Any?>) ?: emptyMap()
            AppCheckCallResult.Success(data)
        } catch (e: FirebaseFunctionsException) {
            // Gereksinim 2: sunucu, `consumeAppCheckToken: true` (bkz.
            // `subscriptionVerification.ts`) ile geçersiz/eksik/TEKRAR OYNATILAN bir jetonu
            // `UNAUTHENTICATED` olarak reddeder -- bu, bütünlük kanıtlanamadığı anlamına gelir.
            if (e.code == FirebaseFunctionsException.Code.UNAUTHENTICATED) {
                Log.e("App Check limited-use doğrulaması reddedildi (UNAUTHENTICATED)", e)
                AppCheckCallResult.AttestationFailed
            } else {
                Log.e("Limited-use App Check callable başarısız", e)
                AppCheckCallResult.Unavailable
            }
        } catch (e: Exception) {
            // Gereksinim 2: "Include error handling ... if Play Integrity API limits are
            // exceeded." Kurulu SDK sürümü, jeton alma
            // ([com.google.firebase.appcheck.FirebaseAppCheck.getLimitedUseAppCheckToken])
            // başarısızlığı için AYRI TİPLİ bir istisna DIŞA VERMEZ (derleme-zamanında
            // `javap` ile doğrulandı: `FirebaseAppCheckException` bu SDK'da mevcut değil) --
            // bu hata `call()` Task'ının GENEL bir istisnasıyla yüzeye çıkar. Bu yüzden en
            // iyi çaba (best-effort) ile mesaj içeriğine bakılır -- YANLIŞ NEGATİF (kotayı
            // `Unavailable`e düşürmek) GÜVENLİ taraftadır: her iki durumda da çağıran bu turu
            // SESSİZCE atlar, kullanıcı ASLA engellenmez (bkz. çağıran kod: `verifyTierViaRevenueCatRest`).
            val message = e.message.orEmpty().lowercase()
            val isQuotaRelated = listOf("quota", "throttle", "rate limit", "resource_exhausted", "too many")
                .any { it in message }
            Log.e("Limited-use App Check jetonu alınamadı", e)
            if (isQuotaRelated) AppCheckCallResult.QuotaExceeded else AppCheckCallResult.Unavailable
        }
    }
}

/**
 * [Task]ı suspend fonksiyonuna çevirir -- `kotlinx-coroutines-play-services` bağımlılığı
 * EKLEMEK yerine (bu dosyadaki TEK kullanım için), bu depoda BAŞKA yerlerde de (ör.
 * `PlatformSubscriptionRepository.android.kt`deki `readClaimedTierFromIdToken`) AYNI el ile
 * yazılan [suspendCancellableCoroutine] deseni yeniden kullanılır.
 */
private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
}
