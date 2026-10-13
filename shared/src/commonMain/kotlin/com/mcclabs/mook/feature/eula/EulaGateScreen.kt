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
 */
@Composable
fun EulaGateScreen(
    onAccepted: () -> Unit,
    onLogout: () -> Unit,
    viewModel: EulaGateViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

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

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 20.dp)
        ) {
            Text(
                text = stringResource(Res.string.legal_eula_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { viewModel.onAcceptedChange(!state.accepted) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = state.accepted,
                onCheckedChange = viewModel::onAcceptedChange,
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
            enabled = state.accepted && !state.isSubmitting
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
