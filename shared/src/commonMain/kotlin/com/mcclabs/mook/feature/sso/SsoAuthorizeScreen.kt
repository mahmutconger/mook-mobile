package com.mcclabs.mook.feature.sso

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mcclabs.mook.ui.theme.NeonColors
import mook.shared.generated.resources.Res
import mook.shared.generated.resources.sso_title
import mook.shared.generated.resources.sso_body
import mook.shared.generated.resources.sso_allow
import mook.shared.generated.resources.sso_deny
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlinx.coroutines.delay

@Composable
fun SsoAuthorizeScreen(
    onNavigateBack: () -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: SsoAuthorizeViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SsoAuthorizeEvent.NavigateBack -> onNavigateBack()
                is SsoAuthorizeEvent.NavigateToLogin -> onNavigateToLogin()
                is SsoAuthorizeEvent.RedirectToCallback -> {
                    // Open the callback URL which will deep link back to WalkTalk
                    uriHandler.openUri(event.url)
                    // Add a small delay then exit this screen so the user doesn't get stuck
                    delay(500)
                    onNavigateBack()
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NeonColors.Background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(NeonColors.Surface)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(Res.string.sso_title),
                style = MaterialTheme.typography.titleLarge,
                color = NeonColors.TextPrimary,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = stringResource(Res.string.sso_body, state.clientName),
                style = MaterialTheme.typography.bodyLarge,
                color = NeonColors.TextSecondary,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = state.userEmail,
                style = MaterialTheme.typography.bodyMedium,
                color = NeonColors.Primary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            if (state.error != null) {
                Text(
                    text = state.error!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = NeonColors.Error,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            if (state.isLoading) {
                CircularProgressIndicator(
                    color = NeonColors.Primary,
                    modifier = Modifier.size(48.dp)
                )
            } else {
                Button(
                    onClick = viewModel::onAllowClicked,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonColors.Primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(Res.string.sso_allow), color = NeonColors.Background, fontWeight = FontWeight.Bold)
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Button(
                    onClick = viewModel::onDenyClicked,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(Res.string.sso_deny), color = NeonColors.TextSecondary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
