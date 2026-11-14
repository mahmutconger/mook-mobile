package com.mcclabs.mook.feature.eula

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalUriHandler
import com.mcclabs.mook.ui.legal.LocalLegalLinks
import kotlinx.coroutines.flow.first
import mook.shared.generated.resources.eula_full_terms_link
import mook.shared.generated.resources.eula_gate_read_hint
import mook.shared.generated.resources.eula_gate_wait
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mcclabs.mook.ui.components.NeonPrimaryButton
import mook.shared.generated.resources.Res
import mook.shared.generated.resources.eula_gate_accept
import mook.shared.generated.resources.eula_gate_continue
import mook.shared.generated.resources.eula_gate_logout
import mook.shared.generated.resources.eula_gate_subtitle
import mook.shared.generated.resources.eula_gate_welcome
import mook.shared.generated.resources.legal_eula_body
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Mandatory EULA / UGC-policy gate shown after login. The user cannot enter the app
 * without ticking acceptance; a "Log out" escape hatch lets them leave instead.
 *
 * Okuma zorunludur: onay kutusu, metin sonuna kadar kaydırılıp en kısa okuma süresi
 * ([EulaGateViewModel.MIN_READ_SECONDS]) dolmadan açılmaz. Kabul edilen şartlar daha sonra
 * giriş ekranından ([EulaTermsDialog]) yeniden okunabilir.
 */
@Composable
fun EulaGateScreen(
    onAccepted: () -> Unit,
    onLogout: () -> Unit,
    viewModel: EulaGateViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val legalLinks = LocalLegalLinks.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is EulaGateEvent.Proceed -> onAccepted()
                is EulaGateEvent.LoggedOut -> onLogout()
            }
        }
    }

    if (state.isChecking) {
        Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(Res.string.eula_gate_welcome),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.eula_gate_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Okuma zorunluluğu: metnin sonuna ulaşıldığında (metin ekrana sığıyorsa ölçülür
        // ölçülmez) ViewModel'e bildirilir. `maxValue` ölçümden önce Int.MAX_VALUE'dur.
        val scrollState = rememberScrollState()
        LaunchedEffect(scrollState) {
            snapshotFlow { scrollState.maxValue != Int.MAX_VALUE && scrollState.value >= scrollState.maxValue }
                .first { it }
            viewModel.onReadToEnd()
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(vertical = 20.dp)
        ) {
            Text(
                text = stringResource(Res.string.legal_eula_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { uriHandler.openUri(legalLinks.termsOfUseUrl) }) {
                Text(stringResource(Res.string.eula_full_terms_link), color = MaterialTheme.colorScheme.primary)
            }
        }

        if (!state.canAccept) {
            Text(
                text = when {
                    !state.hasReadToEnd -> stringResource(Res.string.eula_gate_read_hint)
                    else -> stringResource(Res.string.eula_gate_wait, state.secondsLeft)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (state.canAccept) 1f else 0.5f)
                .clickable(enabled = state.canAccept) { viewModel.onAcceptedChange(!state.accepted) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = state.accepted,
                onCheckedChange = viewModel::onAcceptedChange,
                enabled = state.canAccept,
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.outlineVariant,
                    checkmarkColor = MaterialTheme.colorScheme.background
                )
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(Res.string.eula_gate_accept),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(Modifier.height(12.dp))
        NeonPrimaryButton(
            text = stringResource(Res.string.eula_gate_continue),
            onClick = viewModel::onContinue,
            modifier = Modifier.fillMaxWidth(),
            isLoading = state.isSubmitting,
            enabled = state.accepted && state.canAccept && !state.isSubmitting
        )
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = viewModel::logout,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(stringResource(Res.string.eula_gate_logout), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))
    }
}
