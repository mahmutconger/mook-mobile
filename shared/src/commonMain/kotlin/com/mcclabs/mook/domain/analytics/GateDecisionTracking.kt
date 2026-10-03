package com.mcclabs.mook.domain.analytics

import com.mcclabs.mook.domain.billing.EntitlementState
import com.mcclabs.mook.domain.billing.Feature
import com.mcclabs.mook.domain.billing.GateDecision

/**
 * Bir [GateDecision]'ı analitiğe bildirir: her kararda `gate_decision`, karar bir limitse
 * ayrıca `limit_reached`. [FeatureGate][com.mcclabs.mook.domain.billing.FeatureGate] saf
 * kalsın diye kayıt, kararı veren çağıranın sorumluluğundadır.
 *
 * @param source Kararın verildiği ekran (ör. "discover", "profile_details").
 */
fun AnalyticsRepository.trackGateDecision(
    feature: Feature,
    entitlement: EntitlementState,
    decision: GateDecision,
    source: String,
) {
    track(AnalyticsEvent.GateDecisionMade.from(feature, entitlement.tier, decision))
    if (decision is GateDecision.LimitReached) {
        track(
            AnalyticsEvent.LimitReached(
                reason = decision.reason,
                tier = entitlement.tier,
                source = source,
                upgradeAvailable = decision.upgradeTo != null,
            ),
        )
    }
}
