package com.mcclabs.mook.data.repository

import com.mcclabs.mook.data.appHttpsCallable
import com.mcclabs.mook.domain.support.CustomerSupportRepository
import com.mcclabs.mook.domain.support.PromotionalEntitlement
import com.mcclabs.mook.domain.support.PromotionalGrantDuration
import kotlinx.serialization.Serializable

/** Payload adları BİLEREK `functions/src/customerSupport.ts`i birebir yansıtır. */
@Serializable
private data class GrantPromotionalEntitlementRequest(
    val targetUid: String,
    val entitlementIdentifier: String,
    val duration: String,
    val reason: String,
)

/**
 * [CustomerSupportRepository]'nin gerçek implementasyonu -- bkz. arayüz KDoc'u.
 *
 * [appHttpsCallable] (standart App Check jetonu + yasaklı kullanıcı engeli) ile TEK global
 * geçiş noktasından çağrılır -- bu "highly sensitive" (Gereksinim 2'nin tek kullanımlık App
 * Check jetonu gerektirdiği anlamda) bir işlem OLARAK sınıflandırılmaz: asıl güvenlik sınırı
 * sunucudaki `admin` claim kontrolüdür (bkz. Cloud Function KDoc'u), `LimitedUseAppCheckCallableInvoker`
 * baypası GEREKMEZ.
 */
class CustomerSupportRepositoryImpl : CustomerSupportRepository {
    override suspend fun requestPromotionalGrant(
        targetUid: String,
        entitlement: PromotionalEntitlement,
        duration: PromotionalGrantDuration,
        reason: String,
    ): Result<Unit> = runCatching {
        appHttpsCallable("grantPromotionalEntitlement").invoke(
            GrantPromotionalEntitlementRequest(
                targetUid = targetUid,
                entitlementIdentifier = entitlement.wireValue,
                duration = duration.wireValue,
                reason = reason,
            ),
        )
        Unit
    }
}
