package com.mcclabs.mook.data.sso

import com.mcclabs.mook.domain.sso.SsoClientPolicy
import com.mcclabs.mook.domain.sso.SsoClientRegistry

/** WalkTalk'un `client_id` değeri; Mook'un başlattığı akışta state bu kimliğe bağlanır. */
const val WALKTALK_SSO_CLIENT_ID: String = "walktalk"

/**
 * Uygulamaya gömülü (sabit kodlu) SSO beyaz listesi.
 *
 * Uzaktan yapılandırma yerine bilinçli olarak sabit kod tercih edildi: Remote Config gibi
 * bir kaynağın ele geçirilmesi ya da yanlış yayınlanması beyaz listeyi anında
 * genişletebilirdi. Buradaki bir değişiklik ise kod incelemesinden ve mağaza sürümünden
 * geçmek zorundadır. Gerekirse [SsoClientRegistry] arayüzü üzerinden, yalnızca bu listeyi
 * **daraltabilen** uzak bir kaynak eklenebilir.
 */
class StaticSsoClientRegistry(
    clients: List<SsoClientPolicy> = DEFAULT_CLIENTS,
) : SsoClientRegistry {

    private val clientsById: Map<String, SsoClientPolicy> = clients.associateBy { it.clientId }

    init {
        require(clientsById.size == clients.size) { "Beyaz listede yinelenen clientId var" }
    }

    override fun findClient(clientId: String): SsoClientPolicy? = clientsById[clientId]

    companion object {
        val WALKTALK: SsoClientPolicy = SsoClientPolicy(
            clientId = WALKTALK_SSO_CLIENT_ID,
            displayName = "WalkTalk",
            allowedRedirectUris = setOf("https://walktalkk.com/sso-callback"),
            androidPackageName = "com.istaps.walktalk2",
            // YAPILACAK: WalkTalk'un Play App Signing SHA-256 parmak izi eklenmeli
            // (Play Console → WalkTalk → Uygulama bütünlüğü → Uygulama imzalama).
            // Boşken teslimat yalnızca paket adına sabitlenir; imza sabitlemesi devre dışıdır.
            androidSigningCertSha256 = emptySet(),
        )

        val DEFAULT_CLIENTS: List<SsoClientPolicy> = listOf(WALKTALK)
    }
}
