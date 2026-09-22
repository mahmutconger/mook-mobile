package com.mcclabs.mook.feature.paywall

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalUriHandler
import com.mcclabs.mook.ui.theme.BrandGradient
import com.mcclabs.mook.ui.theme.NeonColors
import com.mcclabs.mook.domain.billing.Period
import com.mcclabs.mook.domain.billing.PlanPackage
import com.mcclabs.mook.domain.billing.Tier
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mook.shared.generated.resources.Res
import mook.shared.generated.resources.app_logo_transparent
import mook.shared.generated.resources.paywall_benefit_filters
import mook.shared.generated.resources.paywall_benefit_likes
import mook.shared.generated.resources.paywall_benefit_unlimited
import mook.shared.generated.resources.paywall_close
import mook.shared.generated.resources.paywall_not_ready
import mook.shared.generated.resources.paywall_subtitle
import mook.shared.generated.resources.paywall_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private const val PLAY_STORE_SUBSCRIPTIONS_URL = "https://play.google.com/store/account/subscriptions"

/**
 * Mook Premium paywall.
 *
 * Currently a scaffold: it presents the value proposition (the three gated features) and a
 * placeholder where the RevenueCat offerings + purchase buttons will render once the store
 * products and RevenueCat dashboard are configured. No prices are hard-coded — the store is
 * the source of truth, so plan cards are added here when `RevenueCatPremiumRepository` is wired.
 */
@Composable
fun PaywallScreen(
    onClose: () -> Unit,
    viewModel: PaywallViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    if (state.purchaseComplete) {
        AlertDialog(
            onDismissRequest = viewModel::dismissPurchaseComplete,
            containerColor = NeonColors.Card,
            title = { Text("Subscription active", color = NeonColors.TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("Your Mook benefits are ready to use.", color = NeonColors.TextSecondary) },
            confirmButton = {
                Button(onClick = viewModel::dismissPurchaseComplete) { Text("Done") }
            },
        )
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NeonColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Close button
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onClose) {
                Text(
                    text = stringResource(Res.string.paywall_close),
                    color = NeonColors.TextSecondary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Brand mark on a gradient chip
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(BrandGradient),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(Res.drawable.app_logo_transparent),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(Res.string.paywall_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = NeonColors.Primary
        )
        Text(
            text = stringResource(Res.string.paywall_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = NeonColors.TextSecondary
        )

        Spacer(Modifier.height(24.dp))
        BenefitRow(stringResource(Res.string.paywall_benefit_unlimited))
        BenefitRow(stringResource(Res.string.paywall_benefit_likes))
        BenefitRow(stringResource(Res.string.paywall_benefit_filters))

        if (state.entitlement.tier != Tier.FREE) {
            Spacer(Modifier.height(24.dp))
            ActivePlanCard(
                tier = state.entitlement.tier,
                expiresAtMillis = state.entitlement.expiresAtMillis,
                willRenew = state.entitlement.willRenew,
                inTrial = state.entitlement.inTrial,
                onManage = { uriHandler.openUri(PLAY_STORE_SUBSCRIPTIONS_URL) },
            )
        }

        Spacer(Modifier.height(24.dp))

        when {
            state.isLoading -> Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) {
                CircularProgressIndicator(color = NeonColors.Primary)
            }
            state.offer != null -> state.offer!!.packages
                .sortedWith(compareBy<PlanPackage> { it.tier.ordinal }.thenBy { it.period.ordinal })
                .forEach { plan ->
                    PlanCard(
                        plan = plan,
                        currentTier = state.entitlement.tier,
                        purchasing = state.isPurchasing,
                        onPurchase = viewModel::purchase,
                    )
                }
            else -> Text(
                text = state.message ?: stringResource(Res.string.paywall_not_ready),
                style = MaterialTheme.typography.bodyMedium,
                color = NeonColors.TextTertiary,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            )
        }
        state.message?.takeIf { state.offer != null }?.let { message ->
            Text(message, color = if (state.purchaseComplete) NeonColors.Primary else NeonColors.TextSecondary, modifier = Modifier.padding(vertical = 12.dp))
        }
        OutlinedButton(onClick = viewModel::restore, enabled = !state.isPurchasing, modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp)) {
            Text("Restore purchases")
        }
        if (state.entitlement.tier != Tier.FREE) {
            TextButton(onClick = { uriHandler.openUri(PLAY_STORE_SUBSCRIPTIONS_URL) }, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Text("Manage subscription in Google Play", color = NeonColors.Primary)
            }
        }
    }
}

@Composable
private fun PlanCard(
    plan: PlanPackage,
    currentTier: Tier,
    purchasing: Boolean,
    onPurchase: (PlanPackage) -> Unit,
) {
    val isCurrent = plan.tier == currentTier
    val canPurchase = plan.tier.ordinal > currentTier.ordinal
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(plan.tier.displayName(), color = NeonColors.TextPrimary, fontWeight = FontWeight.Bold)
                Text(plan.localizedPrice, color = NeonColors.Primary, fontWeight = FontWeight.Bold)
            }
            Text(if (plan.period == Period.MONTHLY) "Monthly" else "Annual", color = NeonColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            if (plan.isRecommended) Text("Most popular", color = NeonColors.Primary, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { onPurchase(plan) },
                enabled = !purchasing && canPurchase,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when {
                        isCurrent -> "Current plan"
                        !canPurchase -> "Manage in Google Play"
                        else -> "Continue"
                    },
                )
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
    onManage: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NeonColors.Primary.copy(alpha = 0.14f)),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text("Your ${tier.displayName()} plan is active", color = NeonColors.TextPrimary, fontWeight = FontWeight.Bold)
            val detail = when {
                inTrial -> "You are currently in a trial."
                expiresAtMillis != null && willRenew -> "Renews ${expiresAtMillis.formatSubscriptionDate()}"
                expiresAtMillis != null -> "Ends ${expiresAtMillis.formatSubscriptionDate()}"
                else -> null
            }
            detail?.let { Text(it, color = NeonColors.TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
            TextButton(onClick = onManage, modifier = Modifier.padding(top = 4.dp)) {
                Text("Manage in Google Play", color = NeonColors.Primary)
            }
        }
    }
}

private fun Tier.displayName() = when (this) {
    Tier.ECONOMY -> "Economy"
    Tier.STANDARD -> "Standard"
    Tier.PREMIUM -> "Premium"
    Tier.FREE -> "Free"
}

private fun Long.formatSubscriptionDate(): String {
    val date = Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.currentSystemDefault()).date
    return "${date.dayOfMonth.toString().padStart(2, '0')}.${date.monthNumber.toString().padStart(2, '0')}.${date.year}"
}

@Composable
private fun BenefitRow(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(BrandGradient),
            contentAlignment = Alignment.Center
        ) {
            Text("✓", color = Color.White, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = NeonColors.TextPrimary,
            fontWeight = FontWeight.Medium
        )
    }
}
