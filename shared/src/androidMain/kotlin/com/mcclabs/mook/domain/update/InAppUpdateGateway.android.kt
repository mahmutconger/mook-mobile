package com.mcclabs.mook.domain.update

import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.mcclabs.mook.util.Log
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Play Core'un (`com.google.android.play:app-update`) gerçek Android implementasyonu.
 *
 * `AppUpdateManager.appUpdateInfo`, Google'ın Task tabanlı (callback) API'sidir — bu
 * kod tabanının RevenueCat/AdMob SDK çağrılarında ZATEN kurduğu
 * `suspendCancellableCoroutine` ile Task'ı sarmalama deseni birebir izlenir.
 */
private class AndroidInAppUpdateGateway : InAppUpdateGateway {

    override suspend fun startImmediateUpdate(): Boolean {
        val activity = InAppUpdateActivityHolder.activity ?: return false
        val launcher = InAppUpdateActivityHolder.launcher ?: return false
        val appUpdateManager = AppUpdateManagerFactory.create(activity)

        // Tip parametresi AÇIKÇA belirtilir: `resume(info)` (AppUpdateInfo) ile
        // `resume(null)` dallarının BİRLİKTE çıkarımı, açık bir tip olmadan derleyicinin
        // yanlışlıkla `Nothing?`e daralmasına yol açabiliyordu.
        val appUpdateInfo = suspendCancellableCoroutine<AppUpdateInfo?> { continuation ->
            appUpdateManager.appUpdateInfo
                .addOnSuccessListener { info ->
                    if (continuation.isActive) continuation.resume(info)
                }
                .addOnFailureListener { error ->
                    Log.e("Play Core güncelleme bilgisi alınamadı", error)
                    if (continuation.isActive) continuation.resume(null)
                }
        } ?: return false

        val canStartImmediateUpdate = appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
            appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
        if (!canStartImmediateUpdate) return false

        return try {
            appUpdateManager.startUpdateFlowForResult(
                appUpdateInfo,
                launcher,
                AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
            )
            true
        } catch (e: Exception) {
            // Gereksinim: bu çağrı kullanıcıyı ASLA kilit ekranında sıkışmış bırakmamalı —
            // başarısız olursa `false` döner, kilit ekranı görünür kalır ve kullanıcı
            // butona tekrar dokunabilir.
            Log.e("Play Core Immediate güncelleme akışı başlatılamadı", e)
            false
        }
    }
}

actual fun createInAppUpdateGateway(): InAppUpdateGateway = AndroidInAppUpdateGateway()
