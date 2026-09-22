package com.mcclabs.mook.feature.sso

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mcclabs.mook.ui.theme.NeonColors
import mook.shared.generated.resources.Res
import mook.shared.generated.resources.sso_allow
import mook.shared.generated.resources.sso_blocked_body
import mook.shared.generated.resources.sso_blocked_title
import mook.shared.generated.resources.sso_body
import mook.shared.generated.resources.sso_close
import mook.shared.generated.resources.sso_deny
import mook.shared.generated.resources.sso_error_client_unavailable
import mook.shared.generated.resources.sso_error_network
import mook.shared.generated.resources.sso_error_server
import mook.shared.generated.resources.sso_expired_body
import mook.shared.generated.resources.sso_expired_title
import mook.shared.generated.resources.sso_retry
import mook.shared.generated.resources.sso_title
import mook.shared.generated.resources.sso_verifying
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SsoAuthorizeScreen(
    onNavigateBack: () -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: SsoAuthorizeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val redirectLauncher = rememberSsoRedirectLauncher()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SsoAuthorizeEvent.DeliverToClient ->
                    viewModel.onDeliveryResult(redirectLauncher.deliver(event.redirectUrl, event.client))
                SsoAuthorizeEvent.NavigateToLogin -> onNavigateToLogin()
                SsoAuthorizeEvent.Close -> onNavigateBack()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NeonColors.Background)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(NeonColors.Surface)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (val current = state) {
                SsoAuthorizeUiState.Verifying -> VerifyingContent()
                is SsoAuthorizeUiState.AwaitingConsent -> ConsentContent(
                    state = current,
                    onAllow = viewModel::onAllowClicked,
                    onDeny = viewModel::onDenyClicked,
                )
                is SsoAuthorizeUiState.Blocked -> BlockedContent(
                    reason = current.reason,
                    onClose = viewModel::onCloseClicked,
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.VerifyingContent() {
    CircularProgressIndicator(color = NeonColors.Primary, modifier = Modifier.size(40.dp))
    Spacer(Modifier.height(16.dp))
    Text(
        text = stringResource(Res.string.sso_verifying),
        style = MaterialTheme.typography.bodyMedium,
        color = NeonColors.TextSecondary,
    )
}

@Composable
private fun ConsentContent(
    state: SsoAuthorizeUiState.AwaitingConsent,
    onAllow: () -> Unit,
    onDeny: () -> Unit,
) {
    Text(
        text = stringResource(Res.string.sso_title),
        style = MaterialTheme.typography.titleLarge,
        color = NeonColors.TextPrimary,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(16.dp))
    Text(
        // İstemci adı istekten değil beyaz listeden gelir.
        text = stringResource(Res.string.sso_body, state.clientName),
        style = MaterialTheme.typography.bodyLarge,
        color = NeonColors.TextSecondary,
        textAlign = TextAlign.Center,
    )
    state.accountEmail?.let { email ->
        Spacer(Modifier.height(8.dp))
        Text(
            text = email,
            style = MaterialTheme.typography.bodyMedium,
            color = NeonColors.Primary,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
    Spacer(Modifier.height(24.dp))

    state.error?.let { error ->
        Text(
            text = when (error) {
                SsoConsentError.NETWORK -> stringResource(Res.string.sso_error_network)
                SsoConsentError.SERVER -> stringResource(Res.string.sso_error_server)
                SsoConsentError.CLIENT_APP_UNAVAILABLE ->
                    stringResource(Res.string.sso_error_client_unavailable, state.clientName)
            },
            style = MaterialTheme.typography.bodySmall,
            color = NeonColors.Error,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
    }

    if (state.isAuthorizing) {
        CircularProgressIndicator(color = NeonColors.Primary, modifier = Modifier.size(48.dp))
    } else {
        PrimaryButton(
            // Hata sonrası aynı düğme "Tekrar dene" olur; kullanıcı akıştan çıkmak zorunda kalmaz.
            text = stringResource(if (state.error == null) Res.string.sso_allow else Res.string.sso_retry),
            onClick = onAllow,
        )
        Spacer(Modifier.height(12.dp))
        SecondaryButton(text = stringResource(Res.string.sso_deny), onClick = onDeny)
    }
}

@Composable
private fun BlockedContent(reason: SsoBlockReason, onClose: () -> Unit) {
    val (title, body) = when (reason) {
        SsoBlockReason.EXPIRED_REQUEST -> Res.string.sso_expired_title to Res.string.sso_expired_body
        SsoBlockReason.UNVERIFIED_REQUEST -> Res.string.sso_blocked_title to Res.string.sso_blocked_body
    }
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.titleLarge,
        color = NeonColors.TextPrimary,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(16.dp))
    Text(
        text = stringResource(body),
        style = MaterialTheme.typography.bodyMedium,
        color = NeonColors.TextSecondary,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(24.dp))
    PrimaryButton(text = stringResource(Res.string.sso_close), onClick = onClose)
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = NeonColors.Primary),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(text, color = NeonColors.Background, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SecondaryButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(text, color = NeonColors.TextSecondary, fontWeight = FontWeight.Bold)
    }
}
