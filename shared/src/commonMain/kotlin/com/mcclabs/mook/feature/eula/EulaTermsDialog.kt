package com.mcclabs.mook.feature.eula

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mcclabs.mook.ui.components.NeonPrimaryButton
import com.mcclabs.mook.ui.legal.LocalLegalLinks
import mook.shared.generated.resources.Res
import mook.shared.generated.resources.eula_dialog_close
import mook.shared.generated.resources.eula_dialog_title
import mook.shared.generated.resources.eula_full_terms_link
import mook.shared.generated.resources.legal_eula_body
import org.jetbrains.compose.resources.stringResource

/**
 * Kullanım şartları (EULA) metninin salt okunur, kapatılabilir görünümü. Giriş ekranından
 * açılır: kullanıcı ilk girişte kabul ettiği şartları istediği zaman yeniden okuyabilir.
 * Metin, EULA kapısında gösterilenle aynıdır (`legal_eula_body`).
 */
@Composable
fun EulaTermsDialog(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val legalLinks = LocalLegalLinks.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(Res.string.eula_dialog_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 20.dp),
            ) {
                Text(
                    text = stringResource(Res.string.legal_eula_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { uriHandler.openUri(legalLinks.termsOfUseUrl) }) {
                    Text(
                        text = stringResource(Res.string.eula_full_terms_link),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            NeonPrimaryButton(
                text = stringResource(Res.string.eula_dialog_close),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
