package com.mcclabs.mook.data.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.google.firebase.FirebaseApp
import com.mcclabs.mook.domain.connectivity.ConnectivityObserver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Paylaşılan katmanın Koin grafiğinde Context yok; Firebase ise ContentProvider ile uygulama
// açılışında zaten başlatılmış durumda, bu yüzden uygulama Context'i oradan alınır — aynı
// desen data/sso/PlatformSsoSecurity.android.kt içinde de kullanılır.
actual fun createConnectivityObserver(): ConnectivityObserver =
    AndroidConnectivityObserver { FirebaseApp.getInstance().applicationContext }

/**
 * `ConnectivityManager.NetworkCallback` ile gerçek zamanlı ağ değişikliklerini dinleyen
 * singleton gözlemci. Yalnızca "internete gidebilecek doğrulanmış bir ağ var mı" sorusuna
 * cevap verir (bkz. [ConnectivityObserver] KDoc'u) — belirli bir sunucuya erişilebilirliği
 * garanti etmez.
 *
 * Koin grafiğinde `single` olarak kaydedilir ve uygulama ömrü boyunca yaşar; bu yüzden
 * callback kaydı kasıtlı olarak hiç kaldırılmaz (diğer process-wide singleton'larla —
 * ör. RevenueCat'in `updatedCustomerInfoListener`'ı ile — aynı yaşam döngüsü deseni).
 */
private class AndroidConnectivityObserver(contextProvider: () -> Context) : ConnectivityObserver {

    private val connectivityManager =
        contextProvider().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val mutableIsOnline = MutableStateFlow(currentlyOnline())
    override val isOnline: StateFlow<Boolean> = mutableIsOnline.asStateFlow()

    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching {
            connectivityManager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    mutableIsOnline.value = currentlyOnline()
                }

                override fun onLost(network: Network) {
                    mutableIsOnline.value = currentlyOnline()
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    mutableIsOnline.value = currentlyOnline()
                }
            })
        }
    }

    private fun currentlyOnline(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
