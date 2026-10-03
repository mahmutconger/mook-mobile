package com.mcclabs.mook.domain.update

import com.mcclabs.mook.domain.model.UpdateState
import com.mcclabs.mook.domain.repository.UpdateRepository
import kotlinx.coroutines.flow.Flow

/**
 * Zorla/opsiyonel güncelleme akışının tek giriş noktası — [UpdateRepository] (Remote
 * Config okuma + sürüm karşılaştırma) ile [InAppUpdateGateway]'i (Play Core akışı)
 * birleştirir, böylece ViewModel/Compose katmanı ikisinden HİÇBİRİNE doğrudan bağımlı
 * olmaz (Clean Architecture — SDK çağrıları Repository/Gateway'de KALIR).
 *
 * ## Neden bu bir UseCase, doğrudan Repository DEĞİL
 * Firestore kuralları yeni bir alan/kısıt bekleyerek deploy edildiğinde, o kısıtı
 * karşılamayan eski istemcilerin sunucuyla konuşmaya DEVAM etmesi veri bozulmasına yol
 * açabilir. Bu yüzden [evaluate] her zaman UYGULAMA AÇILIŞINDA çağrılmalı ve sonucu
 * [UpdateState.ForceUpdateRequired] ise arayan taraf (bkz. `App.kt`) `AppNavGraph()`'ı
 * HİÇ compose ETMEMELİDİR — `UserModerationUseCase.isBanned` kilidiyle AYNI ilke.
 */
class ForceUpdateUseCase(
    private val updateRepository: UpdateRepository,
    private val inAppUpdateGateway: InAppUpdateGateway,
) {

    /** UI'ın tepki vereceği güncel [UpdateState] — `App.kt`'de `collectAsState` ile izlenir. */
    val updateState: Flow<UpdateState> = updateRepository.updateState

    /**
     * Remote Config'i sorgular ve [UpdateState]'i günceller. Uygulama açılışında BİR KEZ
     * çağrılması yeterlidir; ağ hatası her zaman [UpdateState.None]'a düşer (bkz.
     * `UpdateRepositoryImpl`'in `catch` bloğu) — geçici bir Remote Config kesintisi
     * kullanıcıyı asla haksız yere kilitlemez.
     */
    suspend fun evaluate(): UpdateState = updateRepository.checkUpdateStatus()

    /**
     * Play Core'un Immediate akışını başlatır. Akış başarıyla BAŞLATILAMAZSA (ör. Play
     * Store yüklü değil, güncelleme zaten mevcut değil, iOS'ta her zaman) `false` döner
     * ve kilit ekranı görünür kalır — kullanıcı butona tekrar dokunabilir.
     */
    suspend fun startImmediateUpdate(): Boolean = inAppUpdateGateway.startImmediateUpdate()
}
