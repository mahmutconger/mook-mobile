package com.mcclabs.mook.domain.update

/**
 * Google Play In-App Updates SDK'sını (Play Core) soyutlayan platforma özgü kapı —
 * [com.mcclabs.mook.ads.LikeInterstitialGateway] ile AYNI desen: üçüncü taraf SDK
 * çağrısı YALNIZCA androidMain'deki gerçek implementasyonda yaşar, ortak katman
 * (bkz. [ForceUpdateUseCase]) yalnızca bu arayüze bağımlıdır.
 */
interface InAppUpdateGateway {
    /**
     * Play Core'un "Immediate" güncelleme akışını başlatır — kullanıcı güncellemeyi
     * TAMAMLAYANA ya da iptal edene kadar tam ekran, geri alınamaz bir Google Play
     * akışıdır (Play Store'un kendisi tarafından çizilir). Akış başarıyla
     * BAŞLATILABİLDİYSE `true` döner (kullanıcının güncellemeyi TAMAMLADIĞI anlamına
     * GELMEZ — bu, uygulamanın kendisini yeniden başlatmasıyla doğal olarak
     * gerçekleşir). Güncelleme mevcut değilse, akış başlatılamazsa ya da platform
     * desteklemiyorsa (ör. iOS'ta her zaman) `false` döner ve arayan taraf kilit
     * ekranını göstermeye devam eder.
     */
    suspend fun startImmediateUpdate(): Boolean
}

/** Platforma özgü gerçek [InAppUpdateGateway] sağlayıcısı. */
expect fun createInAppUpdateGateway(): InAppUpdateGateway
