package com.mcclabs.mook.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.mcclabs.mook.domain.account.AccountMergeResult
import com.mcclabs.mook.domain.billing.Tier
import mook.shared.generated.resources.Res
import mook.shared.generated.resources.account_conflict_message
import mook.shared.generated.resources.account_conflict_support_button
import mook.shared.generated.resources.account_conflict_title
import mook.shared.generated.resources.account_conflict_transfer_button
import mook.shared.generated.resources.settings_membership_economy
import mook.shared.generated.resources.settings_membership_free
import mook.shared.generated.resources.settings_membership_premium
import mook.shared.generated.resources.settings_membership_standard
import org.jetbrains.compose.resources.stringResource

/**
 * Gereksinim 1.9: "Hesap Çakışması" diyaloğu.
 *
 * Kullanıcı yeni bir SSO sağlayıcısıyla giriş yaptığında, cihazda ZATEN başka bir kimliğe ait
 * aktif ücretli bir abonelik tespit edilirse (bkz. `AccountMergeUseCase.detectConflict`)
 * gösterilir. Kullanıcının iki seçeneği vardır:
 * - **Aktar**: RevenueCat'in "Transfer" restore davranışına güvenerek aboneliği yeni kimliğe
 *   taşır (`AccountMergeUseCase.proceedWithTransfer`).
 * - **Destek**: otomatik aktarım yerine bir insanla konuşmayı tercih eden kullanıcılar için
 *   (ör. abonelik aslında başka bir kişiye/hesaba ait olduğundan aktarılmaması gerekiyorsa).
 *
 * Diyalog kapatılamaz (dışarı tıklama/geri tuşu ile) — kullanıcının bilinçli bir seçim yapması
 * gerekir, çünkü sessizce yok sayılırsa RevenueCat kimliği eski (önceki) kullanıcıda kalmaya
 * devam eder ve yeni kullanıcı yanlışlıkla ücretsiz kademede kalabilir.
 */
@Composable
fun AccountConflictDialog(
    conflict: AccountMergeResult.ConflictDetected,
    onTransferConfirmed: () -> Unit,
    onContactSupport: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { /* Bilinçli bir seçim gerektirir — dışarı tıklayarak kapatılamaz. */ },
        title = { Text(stringResource(Res.string.account_conflict_title)) },
        text = { Text(stringResource(Res.string.account_conflict_message, conflict.activeTier.displayName())) },
        confirmButton = {
            TextButton(onClick = onTransferConfirmed) {
                Text(stringResource(Res.string.account_conflict_transfer_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onContactSupport) {
                Text(stringResource(Res.string.account_conflict_support_button))
            }
        },
    )
}

@Composable
private fun Tier.displayName(): String = when (this) {
    Tier.FREE -> stringResource(Res.string.settings_membership_free)
    Tier.ECONOMY -> stringResource(Res.string.settings_membership_economy)
    Tier.STANDARD -> stringResource(Res.string.settings_membership_standard)
    Tier.PREMIUM -> stringResource(Res.string.settings_membership_premium)
}
