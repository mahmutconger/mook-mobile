package com.mcclabs.mook.domain.billing

/**
 * Profil ziyareti geçiş reklamı (her 3. ziyaret) iş kuralı.
 *
 * Akış: ziyaret sayacı artırılır → [FeatureGate] `PROFILE_VISIT` kararı üretilir (reklam
 * zamanı geldiyse `AdFirst(PROFILE_VISIT_INTERSTITIAL)`) → reklam gösterilirse sayaç sıfırlanır.
 * Reklam gösterilemezse sayaç korunur ve bir sonraki ziyarette yeniden denenir.
 * Reklamsız planlarda (Ekonomik ve üstü) ziyaretler sayılmaz bile.
 */
class ProfileVisitInterstitialUseCase(
    private val adCounters: AdCounterRepository,
    private val interstitialGateway: LikeInterstitialGateway,
    private val featureGate: FeatureGate = FeatureGate(),
) {
    /**
     * @return Verilen kapı kararı (analitik için) ve reklamın gösterilip gösterilmediği.
     *   Plan durumu bilinmiyorsa veya plan reklamsızsa `null` (hiçbir şey yapılmadı).
     */
    suspend operator fun invoke(entitlement: EntitlementState): ProfileVisitAdResult? {
        if (!entitlement.isResolved || !entitlement.limits.showsAds) return null
        val visits = adCounters.incrementProfileVisits()
        val decision = featureGate.decide(
            feature = Feature.PROFILE_VISIT,
            state = entitlement,
            usage = UsageSnapshot(),
            showInterstitial = AdFrequencyRules.isProfileVisitAdDue(visits),
        )
        val shown = decision is GateDecision.AdFirst &&
            interstitialGateway.showProfileVisitInterstitial(isFree = true)
        if (shown) adCounters.resetProfileVisits()
        return ProfileVisitAdResult(decision = decision, adShown = shown)
    }
}

data class ProfileVisitAdResult(val decision: GateDecision, val adShown: Boolean)
