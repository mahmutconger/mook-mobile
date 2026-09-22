package com.mcclabs.mook.feature.sso

import androidx.compose.runtime.Composable
import com.mcclabs.mook.domain.sso.SsoClientPolicy
import com.mcclabs.mook.domain.sso.SsoDeliveryResult

/**
 * Token içeren geri dönüş adresini **yalnızca** doğrulanmış istemci uygulamaya teslim eder.
 *
 * Genel bir "adresi aç" çağrısı kullanılmaz: istemci uygulama yüklü değilse adres
 * tarayıcıda açılır ve token geçmişe, eşitlenen sekmelere ya da başka bir uygulamaya
 * sızabilirdi. Teslim edilemiyorsa token atılır.
 */
fun interface SsoRedirectLauncher {
    suspend fun deliver(redirectUrl: String, client: SsoClientPolicy): SsoDeliveryResult
}

/**
 * - **Android:** Intent paket adına sabitlenir, varsa imza sertifikası doğrulanır; tarayıcıya düşülmez.
 * - **iOS:** Yalnızca Universal Link olarak açılır (`universalLinksOnly`); Safari'ye düşülmez.
 */
@Composable
expect fun rememberSsoRedirectLauncher(): SsoRedirectLauncher
