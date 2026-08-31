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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mcclabs.mook.ui.theme.BrandGradient
import com.mcclabs.mook.ui.theme.NeonColors
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
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NeonColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
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

        Spacer(Modifier.weight(1f))

        // Placeholder for RevenueCat offerings + purchase buttons.
        Text(
            text = stringResource(Res.string.paywall_not_ready),
            style = MaterialTheme.typography.bodyMedium,
            color = NeonColors.TextTertiary,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        )
    }
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
