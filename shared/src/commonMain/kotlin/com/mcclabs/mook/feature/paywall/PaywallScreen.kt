package com.mcclabs.mook.feature.paywall

import mook.shared.generated.resources.error_offline
import mook.shared.generated.resources.paywall_retry
import mook.shared.generated.resources.paywall_load_error
import mook.shared.generated.resources.paywall_most_popular
import com.mcclabs.mook.domain.billing.LimitReason
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mcclabs.mook.domain.billing.Period
import com.mcclabs.mook.domain.billing.PlanPackage
import com.mcclabs.mook.domain.billing.ReplacementPolicy
import com.mcclabs.mook.domain.billing.SubscriptionChange
import com.mcclabs.mook.domain.billing.SubscriptionChangePolicy
import com.mcclabs.mook.domain.billing.SubscriptionChangeType
import com.mcclabs.mook.domain.billing.Tier
import com.mcclabs.mook.ui.theme.NeonColors
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import mook.shared.generated.resources.Res
import mook.shared.generated.resources.paywall_active_title
import mook.shared.generated.resources.paywall_billed_monthly
import mook.shared.generated.resources.paywall_billed_yearly
import mook.shared.generated.resources.paywall_choose_plan
import mook.shared.generated.resources.paywall_close
import mook.shared.generated.resources.paywall_distance_selling_contract_waiver
import mook.shared.generated.resources.paywall_continue_with
import mook.shared.generated.resources.paywall_change_terms_deferred
import mook.shared.generated.resources.paywall_change_terms_immediate
import mook.shared.generated.resources.paywall_schedule_downgrade_to
import mook.shared.generated.resources.paywall_schedule_switch_to_monthly
import mook.shared.generated.resources.paywall_switch_to_yearly
import mook.shared.generated.resources.paywall_upgrade_to
import mook.shared.generated.resources.paywall_current_plan
import mook.shared.generated.resources.paywall_done
import mook.shared.generated.resources.paywall_ends_date
import mook.shared.generated.resources.paywall_fair_use_disclaimer
import mook.shared.generated.resources.paywall_economy_likes
import mook.shared.generated.resources.paywall_economy_no_ads
import mook.shared.generated.resources.paywall_economy_rooms
import mook.shared.generated.resources.paywall_included_title
import mook.shared.generated.resources.paywall_manage_google_play
import mook.shared.generated.resources.paywall_manage_plan
import mook.shared.generated.resources.paywall_not_ready
import mook.shared.generated.resources.paywall_period_monthly
import mook.shared.generated.resources.paywall_period_yearly
import mook.shared.generated.resources.paywall_plan_options
import mook.shared.generated.resources.paywall_premium_boosts
import mook.shared.generated.resources.paywall_premium_privacy
import mook.shared.generated.resources.paywall_premium_roam
import mook.shared.generated.resources.paywall_purchase_success_text
import mook.shared.generated.resources.paywall_purchase_success_title
import mook.shared.generated.resources.paywall_renews_date
import mook.shared.generated.resources.paywall_renewal_terms
import mook.shared.generated.resources.paywall_renewal_terms_trial
import mook.shared.generated.resources.paywall_restore
import mook.shared.generated.resources.paywall_standard_liked_by
import mook.shared.generated.resources.paywall_standard_likes
import mook.shared.generated.resources.paywall_standard_tools
import mook.shared.generated.resources.paywall_start_free_trial
import mook.shared.generated.resources.paywall_subtitle
import mook.shared.generated.resources.paywall_tier_economy
import mook.shared.generated.resources.paywall_tier_premium
import mook.shared.generated.resources.paywall_tier_standard
import mook.shared.generated.resources.paywall_terms_privacy
import mook.shared.generated.resources.paywall_title
import mook.shared.generated.resources.paywall_trial_cancelled_detail
import mook.shared.generated.resources.paywall_trial_days_remaining
import mook.shared.generated.resources.paywall_trial_detail
import mook.shared.generated.resources.paywall_unlimited_messaging
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private const val PLAY_STORE_SUBSCRIPTIONS_URL = "https://play.google.com/store/account/subscriptions"
private const val LEGAL_URL = "https://walktalkk.com/legal.html"

@Composable
fun PaywallScreen(
    onClose: () -> Unit,
    /**
     * Gereksinim 2.13 (Faz 4): `true` ise, Remote Config'in `show_onboarding_trial_offer`
     * bayrağı açıkken profil onboarding'ini bitiren kullanıcı için bir deneme (trial)
     * teklifi olan kademe/paket ÖNCEDEN SEÇİLİR (bkz. `RegistrationViewModel`,
     * `RemoteConfigRepository`). Cihaz zaten bir deneme tükettiyse (Gereksinim 2.7 & 2.8 --
     * `hasFreeTrialAvailable` sunucu tarafından zaten `false`'a düşürülmüş olur) bu bayrak
     * sessizce hiçbir şey değiştirmez -- aşağıdaki aramalar hiçbir eşleşme bulamaz ve ekran
     * NORMAL (varsayılan) davranışına geri döner.
     */
    preselectTrial: Boolean = false,
    /** Paywall'ı açan limit; `null` ise genel başlık (bkz. [PaywallHeadline]). */
    limitReason: LimitReason? = null,
    viewModel: PaywallViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(preselectTrial) {
        viewModel.onPaywallViewed(
            source = when {
                limitReason != null -> "limit_${limitReason.name.lowercase()}"
                preselectTrial -> "onboarding_trial"
                else -> "in_app"
            },
            limitReason = limitReason,
        )
    }
    val uriHandler = LocalUriHandler.current
    val plans = state.offer?.packages.orEmpty()
    // Gereksinim 2.13: deneme teklifi olan İLK kademe -- cihaz zaten bir deneme tükettiyse
    // (bkz. yukarıdaki KDoc) `hasFreeTrialAvailable` hiçbir pakette true olmayacağından bu
    // `null` kalır ve aşağıdaki `preferredTier` normal mantığına sessizce düşer.
    val trialTier = plans.firstOrNull { it.hasFreeTrialAvailable }?.tier
    val preferredTier = when {
        state.entitlement.tier != Tier.FREE -> state.entitlement.tier
        preselectTrial && trialTier != null -> trialTier
        // "En Popüler" kademe RevenueCat teklif meta verisinden gelir (bkz. OfferingMetadataParser).
        state.offer?.mostPopularTier?.let { popular -> plans.any { it.tier == popular } } == true ->
            state.offer?.mostPopularTier ?: Tier.STANDARD
        plans.any { it.tier == Tier.STANDARD } -> Tier.STANDARD
        else -> plans.firstOrNull()?.tier ?: Tier.STANDARD
    }
    var selectedTier by remember(plans, state.entitlement.tier) { mutableStateOf(preferredTier) }
    val tierPlans = plans.filter { it.tier == selectedTier }
        .sortedBy { it.period.ordinal }
    var selectedPackageId by remember(tierPlans) {
        mutableStateOf(
            (tierPlans.firstOrNull { preselectTrial && it.hasFreeTrialAvailable }
                ?: tierPlans.firstOrNull { it.period == Period.MONTHLY }
                ?: tierPlans.firstOrNull { it.isRecommended }
                ?: tierPlans.firstOrNull())?.identifier,
        )
    }
    val selectedPlan = tierPlans.firstOrNull { it.identifier == selectedPackageId } ?: tierPlans.firstOrNull()
    val selectedTierName = stringResource(selectedTier.resource())
    // Seçilen paketin mevcut aboneliğe göre yeni satın alma / yükseltme / düşürme / çapraz geçiş
    // olup olmadığı. `purchase()` aynı politikayı kullanır; arayüz ile mağazaya giden istek
    // ASLA ayrışmaz. Yalnızca "zaten aktif" ya da doğrulanamayan durumlar satın alınamaz.
    val subscriptionChange = remember(state.entitlement, selectedPlan, plans) {
        selectedPlan?.let { SubscriptionChangePolicy.evaluate(state.entitlement, it, plans) }
    }
    val canPurchase = SubscriptionChangePolicy.isPurchasable(subscriptionChange)
    val replacement = subscriptionChange as? SubscriptionChange.Replace

    if (state.purchaseComplete) {
        AlertDialog(
            onDismissRequest = viewModel::dismissPurchaseComplete,
            containerColor = NeonColors.Card,
            title = {
                Text(
                    stringResource(Res.string.paywall_purchase_success_title),
                    color = NeonColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = { Text(stringResource(Res.string.paywall_purchase_success_text), color = NeonColors.TextSecondary) },
            confirmButton = {
                Button(onClick = viewModel::dismissPurchaseComplete) {
                    Text(stringResource(Res.string.paywall_done))
                }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NeonColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(Res.string.paywall_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = NeonColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = onClose) {
                    Text(stringResource(Res.string.paywall_close), color = NeonColors.Primary)
                }
            }

            Text(
                text = stringResource(PaywallHeadline.titleFor(state.limitReason ?: limitReason)),
                style = MaterialTheme.typography.headlineLarge,
                color = NeonColors.TextPrimary,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = stringResource(Res.string.paywall_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = NeonColors.TextSecondary,
                modifier = Modifier.padding(top = 6.dp, bottom = 22.dp),
            )

            if (state.entitlement.tier != Tier.FREE) {
                ActivePlanCard(
                    tier = state.entitlement.tier,
                    expiresAtMillis = state.entitlement.expiresAtMillis,
                    willRenew = state.entitlement.willRenew,
                    inTrial = state.entitlement.inTrial,
                    // Gereksinim 1.10: kalan gün sayısı burada, render anında hesaplanır — UI
                    // durumunda ÖNBELLEKLENMEZ, aksi halde ekran açık kaldıkça sayı donuk kalırdı.
                    trialDaysRemaining = com.mcclabs.mook.domain.billing.TrialPeriod.remainingDays(
                        state.entitlement,
                        com.mcclabs.mook.util.getCurrentTimeMillis(),
                    ),
                )
                Spacer(Modifier.height(20.dp))
            }

            TierSelector(
                selectedTier = selectedTier,
                currentTier = state.entitlement.tier,
                popularTier = state.offer?.mostPopularTier,
                onSelect = { selectedTier = it },
            )
            Spacer(Modifier.height(16.dp))

            if (selectedTierPlansEmpty(plans, selectedTier)) {
                when (val loadState = state.loadState) {
                    PaywallLoadState.Loading -> {
                        Box(Modifier.fillMaxWidth().padding(vertical = 30.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = NeonColors.Primary)
                        }
                    }
                    is PaywallLoadState.Error -> PlansLoadError(offline = loadState.offline, onRetry = { viewModel.load() })
                    PaywallLoadState.Ready -> Text(
                        text = stringResource(Res.string.paywall_not_ready),
                        style = MaterialTheme.typography.bodyMedium,
                        color = NeonColors.TextSecondary,
                        modifier = Modifier.padding(vertical = 20.dp),
                    )
                }
            } else {
                Text(
                    text = stringResource(Res.string.paywall_plan_options),
                    style = MaterialTheme.typography.titleMedium,
                    color = NeonColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                tierPlans.forEach { plan ->
                    PlanOptionCard(
                        plan = plan,
                        selected = plan.identifier == selectedPlan?.identifier,
                        // Rozet koda gömülü değil: RevenueCat `offering.metadata`dan gelir.
                        recommended = plan.isRecommended,
                        enabled = !state.isPurchasing,
                        onClick = { selectedPackageId = plan.identifier },
                    )
                    Spacer(Modifier.height(10.dp))
                }
                IncludedFeaturesCard(tier = selectedTier, tierName = selectedTierName)
            }

            state.message?.let { message ->
                Text(
                    text = message,
                    color = if (state.purchaseComplete) NeonColors.Success else NeonColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(NeonColors.Background)
                .padding(horizontal = 22.dp)
                .padding(top = 12.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            selectedPlan?.takeIf { canPurchase }?.let { plan ->
                val billingPeriod = stringResource(
                    if (plan.period == Period.MONTHLY) Res.string.paywall_billed_monthly
                    else Res.string.paywall_billed_yearly,
                )
                Text(
                    text = stringResource(
                        when {
                            // Plan değişikliğinde kullanıcı, ücretin ne zaman ve nasıl
                            // uygulanacağını satın almadan ÖNCE görmelidir.
                            replacement?.policy == ReplacementPolicy.IMMEDIATE_WITH_TIME_PRORATION -> Res.string.paywall_change_terms_immediate
                            replacement?.policy == ReplacementPolicy.DEFERRED -> Res.string.paywall_change_terms_deferred
                            plan.hasFreeTrialAvailable -> Res.string.paywall_renewal_terms_trial
                            else -> Res.string.paywall_renewal_terms
                        },
                        plan.localizedPrice,
                        billingPeriod,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonColors.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                )
            }

            // Gereksinim 6 (Faz 6, Tüketici Hukuku): yalnızca YENİ bir satın alma başlatılacaksa
            // (`canPurchase`) gösterilir -- mevcut bir aboneliği yönetmek (Play Store'a yönlendirme)
            // yeni bir dijital içerik ifası başlatmadığından Cayma Hakkı İstisnası'nı gerektirmez.
            if (canPurchase) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .clickable {
                            viewModel.setDistanceSellingContractAccepted(!state.distanceSellingContractAccepted)
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = state.distanceSellingContractAccepted,
                        onCheckedChange = viewModel::setDistanceSellingContractAccepted,
                        colors = CheckboxDefaults.colors(checkedColor = NeonColors.Primary),
                    )
                    Text(
                        text = stringResource(Res.string.paywall_distance_selling_contract_waiver),
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonColors.TextSecondary,
                    )
                }
            }

            Button(
                onClick = {
                    if (canPurchase && selectedPlan != null) viewModel.purchase(selectedPlan)
                    else uriHandler.openUri(PLAY_STORE_SUBSCRIPTIONS_URL)
                },
                // Gereksinim 6: yeni bir satın alma (`canPurchase`) yalnızca onay kutusu
                // işaretliyken etkindir -- mevcut aboneliği yönetme akışı bundan ETKİLENMEZ.
                enabled = !state.isPurchasing &&
                    (selectedPlan != null || state.entitlement.tier != Tier.FREE) &&
                    (!canPurchase || state.distanceSellingContractAccepted),
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonColors.TextPrimary,
                    contentColor = NeonColors.Background,
                    disabledContainerColor = NeonColors.TextTertiary.copy(alpha = 0.3f),
                ),
            ) {
                if (state.isPurchasing) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        text = when {
                            // Gereksinim 1.5: yalnızca bu ürün için hâlâ uygunsa (Play Billing
                            // tarafında filtrelenir) "ücretsiz deneme" metni gösterilir; kullanıcı
                            // denemeyi daha önce kullandıysa bu dal hiç tetiklenmez.
                            // Deneme teklifi yalnızca YENİ satın almada geçerlidir; Play mevcut
                            // aboneye plan değişikliğinde deneme vermez.
                            subscriptionChange is SubscriptionChange.NewPurchase && selectedPlan?.hasFreeTrialAvailable == true ->
                                stringResource(Res.string.paywall_start_free_trial)
                            replacement?.type == SubscriptionChangeType.UPGRADE ->
                                stringResource(Res.string.paywall_upgrade_to, selectedTierName)
                            replacement?.type == SubscriptionChangeType.DOWNGRADE ->
                                stringResource(Res.string.paywall_schedule_downgrade_to, selectedTierName)
                            replacement?.type == SubscriptionChangeType.CROSSGRADE &&
                                replacement.policy == ReplacementPolicy.IMMEDIATE_WITH_TIME_PRORATION ->
                                stringResource(Res.string.paywall_switch_to_yearly)
                            replacement?.type == SubscriptionChangeType.CROSSGRADE ->
                                stringResource(Res.string.paywall_schedule_switch_to_monthly)
                            canPurchase -> stringResource(Res.string.paywall_continue_with, selectedTierName)
                            else -> stringResource(Res.string.paywall_manage_plan)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = viewModel::restore, enabled = !state.isPurchasing) {
                    Text(stringResource(Res.string.paywall_restore), color = NeonColors.TextSecondary)
                }
                if (state.entitlement.tier != Tier.FREE) {
                    TextButton(onClick = { uriHandler.openUri(PLAY_STORE_SUBSCRIPTIONS_URL) }) {
                        Text(stringResource(Res.string.paywall_manage_google_play), color = NeonColors.Primary)
                    }
                }
            }
            TextButton(onClick = { uriHandler.openUri(LEGAL_URL) }) {
                Text(stringResource(Res.string.paywall_terms_privacy), color = NeonColors.TextTertiary)
            }
        }
    }
}

@Composable
private fun TierSelector(
    selectedTier: Tier,
    currentTier: Tier,
    /** RevenueCat teklif meta verisindeki "En Popüler" kademe; yoksa rozet gösterilmez. */
    popularTier: Tier?,
    onSelect: (Tier) -> Unit,
) {
    val tiers = listOf(Tier.ECONOMY, Tier.STANDARD, Tier.PREMIUM)
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(NeonColors.Surface)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tiers.forEach { tier ->
            val selected = tier == selectedTier
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) NeonColors.Background else Color.Transparent)
                    .clickable { onSelect(tier) }
                    .padding(horizontal = 4.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(tier.resource()),
                    color = if (selected) NeonColors.TextPrimary else NeonColors.TextSecondary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                )
                if (tier == popularTier) {
                    Text(
                        text = stringResource(Res.string.paywall_most_popular),
                        color = NeonColors.Primary,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                } else if (tier == currentTier) {
                    Text(
                        text = stringResource(Res.string.paywall_current_plan),
                        color = NeonColors.Success,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanOptionCard(
    plan: PlanPackage,
    selected: Boolean,
    recommended: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) NeonColors.Primary.copy(alpha = 0.07f) else NeonColors.Surface)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) NeonColors.Primary else NeonColors.CardBorder,
                shape = shape,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .border(2.dp, if (selected) NeonColors.Primary else NeonColors.TextTertiary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(Modifier.size(11.dp).clip(CircleShape).background(NeonColors.Primary))
            }
        }
        Spacer(Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    text = stringResource(
                        if (plan.period == Period.MONTHLY) Res.string.paywall_period_monthly
                        else Res.string.paywall_period_yearly,
                    ),
                    color = NeonColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (recommended) {
                    Text(
                        text = stringResource(Res.string.paywall_most_popular),
                        color = NeonColors.Primary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Text(
                text = stringResource(
                    if (plan.period == Period.MONTHLY) Res.string.paywall_billed_monthly
                    else Res.string.paywall_billed_yearly,
                ),
                color = NeonColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = plan.localizedPrice,
            color = NeonColors.TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * Gereksinim 2.11: bir paket özelliğinin metni + bu özelliğin "Sınırsız" (Unlimited) iddiası
 * taşıyıp taşımadığı. [isUnlimitedClaim] `true` olan HER satırın hemen altında, tüketici
 * yasaları/Play Store uyumu için ZORUNLU olan Adil Kullanım Kotası açıklaması otomatik olarak
 * render edilir — bu ilişki [IncludedFeaturesCard] içinde TEK bir yerde kurulur, böylece yeni
 * bir "sınırsız" özellik eklendiğinde açıklamanın unutulması yapısal olarak imkânsız hâle gelir.
 */
private data class TierBenefit(val textRes: StringResource, val isUnlimitedClaim: Boolean = false)

@Composable
private fun IncludedFeaturesCard(tier: Tier, tierName: String) {
    val features = when (tier) {
        Tier.ECONOMY -> listOf(
            TierBenefit(Res.string.paywall_economy_likes),
            TierBenefit(Res.string.paywall_economy_rooms),
            TierBenefit(Res.string.paywall_economy_no_ads),
        )
        Tier.STANDARD -> listOf(
            TierBenefit(Res.string.paywall_standard_likes),
            TierBenefit(Res.string.paywall_standard_liked_by),
            TierBenefit(Res.string.paywall_standard_tools),
            // Gereksinim 2.11: Standart'ta günlük mesaj SAYISI tavanı yoktur (`dailyMessages
            // == null`) — bu gerçek bir "sınırsız" iddiasıdır ve bu yüzden Adil Kullanım
            // açıklamasını ZORUNLU kılar (mesaj sayısı sınırsızdır; çeviri ise kademenin
            // günlük/aylık karakter kotasıyla sınırlıdır, kota bitince mesajlar çevrilmeden gider).
            TierBenefit(Res.string.paywall_unlimited_messaging, isUnlimitedClaim = true),
        )
        Tier.PREMIUM -> listOf(
            TierBenefit(Res.string.paywall_premium_roam),
            TierBenefit(Res.string.paywall_premium_privacy),
            TierBenefit(Res.string.paywall_premium_boosts),
            TierBenefit(Res.string.paywall_unlimited_messaging, isUnlimitedClaim = true),
        )
        Tier.FREE -> emptyList()
    }
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF17171D))
            .padding(20.dp),
    ) {
        Text(
            text = stringResource(Res.string.paywall_included_title, tierName),
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(12.dp))
        features.forEach { feature ->
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.size(24.dp).clip(CircleShape).background(Color.White),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("✓", color = Color(0xFF17171D), fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(feature.textRes),
                        color = Color(0xFFE5E7EB),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (feature.isUnlimitedClaim) {
                    // Gereksinim 2.11: tüketici yasaları/Play Store uyumu — "Sınırsız" iddiası
                    // taşıyan HER özelliğin hemen altında AÇIKÇA görünür olmalı.
                    Text(
                        text = stringResource(Res.string.paywall_fair_use_disclaimer),
                        color = Color(0xFF9CA3AF),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 36.dp, top = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivePlanCard(
    tier: Tier,
    expiresAtMillis: Long?,
    willRenew: Boolean,
    inTrial: Boolean,
    trialDaysRemaining: Int?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NeonColors.Primary.copy(alpha = 0.09f)),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier.size(34.dp).clip(CircleShape).background(NeonColors.Primary),
                contentAlignment = Alignment.Center,
            ) {
                Text("✓", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(Res.string.paywall_active_title, stringResource(tier.resource())),
                    color = NeonColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                )
                val detail = when {
                    // Gereksinim 1.10: deneme sırasında iptal edilmişse (willRenew = false)
                    // kullanıcı erişimini KAYBETMEZ — bitiş tarihine kadar devam eder. Bunu
                    // genel "Bitiş: X" mesajından AYRI, açık bir metinle bildiriyoruz ki
                    // kullanıcı erişiminin hemen kesildiğini sanmasın.
                    inTrial && !willRenew && expiresAtMillis != null -> stringResource(
                        Res.string.paywall_trial_cancelled_detail,
                        expiresAtMillis.formatSubscriptionDate(),
                    )
                    inTrial && trialDaysRemaining != null -> stringResource(
                        Res.string.paywall_trial_days_remaining,
                        trialDaysRemaining,
                    )
                    inTrial -> stringResource(Res.string.paywall_trial_detail)
                    expiresAtMillis != null && willRenew -> stringResource(
                        Res.string.paywall_renews_date,
                        expiresAtMillis.formatSubscriptionDate(),
                    )
                    expiresAtMillis != null -> stringResource(
                        Res.string.paywall_ends_date,
                        expiresAtMillis.formatSubscriptionDate(),
                    )
                    else -> null
                }
                detail?.let {
                    Text(it, color = NeonColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun selectedTierPlansEmpty(plans: List<PlanPackage>, tier: Tier) = plans.none { it.tier == tier }

private fun Tier.resource() = when (this) {
    Tier.ECONOMY -> Res.string.paywall_tier_economy
    Tier.STANDARD -> Res.string.paywall_tier_standard
    Tier.PREMIUM -> Res.string.paywall_tier_premium
    Tier.FREE -> Res.string.paywall_tier_economy
}

internal fun Long.formatSubscriptionDate(): String {
    val date = Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.currentSystemDefault()).date
    return "${date.dayOfMonth.toString().padStart(2, '0')}.${date.monthNumber.toString().padStart(2, '0')}.${date.year}"
}

/**
 * RevenueCat ürünleri yüklenemediğinde gösterilen yerelleştirilmiş hata ve "Tekrar Dene" düğmesi.
 */
@Composable
private fun PlansLoadError(offline: Boolean, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(if (offline) Res.string.error_offline else Res.string.paywall_load_error),
            style = MaterialTheme.typography.bodyMedium,
            color = NeonColors.TextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRetry) { Text(stringResource(Res.string.paywall_retry)) }
    }
}
