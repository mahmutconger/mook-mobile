package com.mcclabs.mook.data.connectivity

import com.mcclabs.mook.domain.connectivity.ConnectivityObserver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * iOS'ta reklam/abonelik akışları henüz bilinçli olarak devreye alınmamış olduğundan (bkz.
 * `ads/AdPlatformUi.ios.kt` ve `data/billing/PlatformSubscriptionRepository.ios.kt`'deki no-op
 * uygulamalarla aynı desen), bu da güvenli bir yer tutucudur: her zaman çevrimiçi bildirir.
 *
 * Bu, mevcut davranışı KÖTÜLEŞTİRMEZ — değişiklikten önce iOS'ta hiçbir çevrimdışı kontrolü
 * yoktu; şimdi paylaşılan (commonMain) kod bu arayüzü çağırabiliyor ve iOS'ta gerçek ağ
 * çağrısına düşüp mevcut hata yakalama akışlarıyla (try/catch) başarısız oluyor — tıpkı önceki
 * davranış gibi. Gerçek bir gözlemci gerektiğinde `platform.Network` (`NWPathMonitor`) ile
 * kablosuz olarak değiştirilebilir; imzayı (`ConnectivityObserver`) hiçbir çağıran kodu
 * etkilemeden değiştirmeden.
 *
 * NOT: Bilinçli olarak `NWPathMonitor` cinterop entegrasyonuna burada girilmedi — Apple'ın
 * Network.framework C API'sinin Kotlin/Native taraflı üretilen imzaları (blok tipi callback'ler,
 * enum erişimi) Kotlin/Native sürümüne göre değişebiliyor ve bu ortamda gerçek bir iOS derlemesi
 * ile doğrulanamadı. Yanlış bir imza tüm iOS hedefinin derlenmesini kırar; bu yüzden iOS ağ
 * işi bilinçli olarak kapsama alındığında, Xcode/KMP üzerinde derlenerek doğrulanmış gerçek bir
 * `NWPathMonitor` uygulamasıyla değiştirilmelidir.
 */
actual fun createConnectivityObserver(): ConnectivityObserver = AlwaysOnlineConnectivityObserver

private object AlwaysOnlineConnectivityObserver : ConnectivityObserver {
    override val isOnline: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()
}
