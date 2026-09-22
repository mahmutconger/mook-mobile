package com.mcclabs.mook.feature.sso

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.pm.PackageInfoCompat
import com.mcclabs.mook.domain.sso.SsoClientPolicy
import com.mcclabs.mook.domain.sso.SsoDeliveryResult

@Composable
actual fun rememberSsoRedirectLauncher(): SsoRedirectLauncher {
    val context = LocalContext.current
    return remember(context) {
        SsoRedirectLauncher { url, client -> deliverToClientApp(context, url, client) }
    }
}

private fun deliverToClientApp(
    context: Context,
    redirectUrl: String,
    client: SsoClientPolicy,
): SsoDeliveryResult {
    val packageManager = context.packageManager
    val packageName = client.androidPackageName

    // Android 11+ için manifest'teki <queries> girdisi gerekir; yoksa paket yüklü görünmez.
    val installed = runCatching { packageManager.getPackageInfo(packageName, 0) }.isSuccess
    if (!installed) return SsoDeliveryResult.CLIENT_APP_UNAVAILABLE

    // Paket adı tek başına kanıt değildir: gerçek uygulama yüklü değilken aynı paket adıyla
    // dışarıdan yüklenmiş sahte bir uygulama token'ı alabilirdi.
    if (client.androidSigningCertSha256.isNotEmpty() && !isSignedByTrustedCert(packageManager, client)) {
        return SsoDeliveryResult.CLIENT_APP_UNTRUSTED
    }

    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(redirectUrl))
        .setPackage(packageName)
        .addCategory(Intent.CATEGORY_BROWSABLE)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    // ActivityNotFoundException: uygulama yüklü ama bu adresi karşılayan filtresi yok
    // (eski sürüm). Tarayıcıya düşmek yerine kullanıcıdan güncellemesini isteriz.
    return if (runCatching { context.startActivity(intent) }.isSuccess) {
        SsoDeliveryResult.DELIVERED
    } else {
        SsoDeliveryResult.CLIENT_APP_UNAVAILABLE
    }
}

/** Sabitlenen parmak izlerinden herhangi biri eşleşirse güvenilir sayılır (anahtar rotasyonu için). */
private fun isSignedByTrustedCert(packageManager: PackageManager, client: SsoClientPolicy): Boolean =
    client.androidSigningCertSha256.any { fingerprint ->
        val certBytes = parseSha256Fingerprint(fingerprint) ?: return@any false
        runCatching {
            PackageInfoCompat.hasSignatures(
                packageManager,
                client.androidPackageName,
                mapOf(certBytes to PackageManager.CERT_INPUT_SHA256),
                false,
            )
        }.getOrDefault(false)
    }

/** `AB:CD:…` biçimindeki 32 baytlık parmak izini çözer; bozuk girdi `null` döner. */
private fun parseSha256Fingerprint(fingerprint: String): ByteArray? {
    val hex = fingerprint.replace(":", "")
    if (hex.length != 64 || !hex.all { it.isDigit() || it.uppercaseChar() in 'A'..'F' }) return null
    return ByteArray(32) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
}
