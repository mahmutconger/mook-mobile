package com.mcclabs.mook.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mcclabs.mook.ui.theme.NeonColors
import mook.shared.generated.resources.Res
import mook.shared.generated.resources.upsell_daily_body
import mook.shared.generated.resources.upsell_daily_cta
import mook.shared.generated.resources.upsell_daily_dismiss
import mook.shared.generated.resources.upsell_daily_title
import org.jetbrains.compose.resources.stringResource

/**
 * Bir geçiş reklamı kapatıldıktan hemen sonra, günde EN FAZLA BİR KEZ gösterilen Premium
 * upsell kartı (bkz. `DailyUpsellCoordinator`). Kullanıcıyı engellemez; tek dokunuşla kapanır.
 */
@Composable
fun DailyUpsellCard(onUpgrade: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(NeonColors.Card)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Filled.Star, contentDescription = null, tint = NeonColors.Primary, modifier = Modifier.size(40.dp))
            Text(
                stringResource(Res.string.upsell_daily_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = NeonColors.TextPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(Res.string.upsell_daily_body),
                style = MaterialTheme.typography.bodyMedium,
                color = NeonColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(4.dp))
            Button(
                onClick = onUpgrade,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = NeonColors.Primary),
            ) { Text(stringResource(Res.string.upsell_daily_cta)) }
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.upsell_daily_dismiss), color = NeonColors.TextSecondary)
            }
        }
    }
}
