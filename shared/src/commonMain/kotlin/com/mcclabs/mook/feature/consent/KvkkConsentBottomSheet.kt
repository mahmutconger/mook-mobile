package com.mcclabs.mook.feature.consent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mcclabs.mook.ui.theme.NeonColors
import mook.shared.generated.resources.Res
import mook.shared.generated.resources.consent_accept_all
import mook.shared.generated.resources.consent_cross_border_body
import mook.shared.generated.resources.consent_cross_border_title
import mook.shared.generated.resources.consent_intro
import mook.shared.generated.resources.consent_personalized_ads_body
import mook.shared.generated.resources.consent_personalized_ads_title
import mook.shared.generated.resources.consent_read_notice
import mook.shared.generated.resources.consent_reject_all
import mook.shared.generated.resources.consent_save_choices
import mook.shared.generated.resources.consent_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** KVKK aydınlatma metninin yayınlandığı adres (Ayarlar'daki gizlilik politikasıyla aynı). */
private const val PRIVACY_NOTICE_URL = "https://walktalkk.com/legal.html"

/**
 * KVKK onay ekranını gerektiğinde gösteren kapsayıcı.
 *
 * @param promptWhenRequired `true` ise (uygulama kökü) kullanıcı mevcut politika sürümü için
 *   karar vermediğinde ekran ZORUNLU olarak açılır ve karar verilmeden kapatılamaz. `false`
 *   ise (Ayarlar) yalnızca [PrivacyConsentViewModel.openEditor] ile açılır ve kapatılabilir.
 */
@Composable
fun PrivacyConsentHost(
    promptWhenRequired: Boolean,
    viewModel: PrivacyConsentViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isMandatory = promptWhenRequired && state.requiresDecision && !state.isEditorOpen
    if (!isMandatory && !state.isEditorOpen) return

    val uriHandler = LocalUriHandler.current
    KvkkConsentBottomSheet(
        choices = state.choices,
        isMandatory = isMandatory,
        onCrossBorderTransferToggled = viewModel::onCrossBorderTransferToggled,
        onPersonalizedAdsToggled = viewModel::onPersonalizedAdsToggled,
        onAcceptAll = viewModel::acceptAll,
        onRejectAll = viewModel::rejectAll,
        onSaveChoices = viewModel::saveChoices,
        onDismiss = viewModel::dismissEditor,
        onReadNotice = { uriHandler.openUri(PRIVACY_NOTICE_URL) },
    )
}

/**
 * Durumsuz (stateless) KVKK onay alt sayfası. İki ayrı ve isteğe bağlı açık rıza içerir:
 * analiz için yurt dışına veri aktarımı ve kişiselleştirilmiş reklamlar. "Tümünü reddet",
 * "Tümünü kabul et" ile aynı görünürlüktedir; hiçbir izin önceden işaretli değildir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KvkkConsentBottomSheet(
    choices: ConsentChoices,
    isMandatory: Boolean,
    onCrossBorderTransferToggled: (Boolean) -> Unit,
    onPersonalizedAdsToggled: (Boolean) -> Unit,
    onAcceptAll: () -> Unit,
    onRejectAll: () -> Unit,
    onSaveChoices: () -> Unit,
    onDismiss: () -> Unit,
    onReadNotice: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        // Zorunlu modda sayfa kaydırılarak ya da dışına dokunularak kapatılamaz; kullanıcı
        // üç seçenekten birini (kabul / reddet / seçimlerimi kaydet) seçmelidir.
        confirmValueChange = { target -> !(isMandatory && target == SheetValue.Hidden) },
    )
    ModalBottomSheet(
        onDismissRequest = { if (!isMandatory) onDismiss() },
        sheetState = sheetState,
        containerColor = NeonColors.Card,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Text(
                text = stringResource(Res.string.consent_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = NeonColors.TextPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.consent_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = NeonColors.TextSecondary,
            )
            Spacer(Modifier.height(16.dp))
            ConsentOptionRow(
                title = stringResource(Res.string.consent_cross_border_title),
                body = stringResource(Res.string.consent_cross_border_body),
                checked = choices.crossBorderTransfer,
                onCheckedChange = onCrossBorderTransferToggled,
            )
            Spacer(Modifier.height(12.dp))
            ConsentOptionRow(
                title = stringResource(Res.string.consent_personalized_ads_title),
                body = stringResource(Res.string.consent_personalized_ads_body),
                checked = choices.personalizedAds,
                onCheckedChange = onPersonalizedAdsToggled,
            )
            TextButton(onClick = onReadNotice, modifier = Modifier.padding(top = 4.dp)) {
                Text(stringResource(Res.string.consent_read_notice), color = NeonColors.Primary)
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onAcceptAll,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonColors.TextPrimary,
                    contentColor = NeonColors.Background,
                ),
            ) { Text(stringResource(Res.string.consent_accept_all), fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(10.dp))
            // Reddetmek kabul etmek kadar kolay olmalı: aynı boyut ve belirginlikte bir buton.
            OutlinedButton(
                onClick = onRejectAll,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(18.dp),
            ) { Text(stringResource(Res.string.consent_reject_all), color = NeonColors.TextPrimary, fontWeight = FontWeight.Bold) }
            TextButton(onClick = onSaveChoices, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text(stringResource(Res.string.consent_save_choices), color = NeonColors.TextSecondary)
            }
        }
    }
}

@Composable
private fun ConsentOptionRow(
    title: String,
    body: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = NeonColors.TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodySmall, color = NeonColors.TextSecondary)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = NeonColors.Primary),
        )
    }
}
