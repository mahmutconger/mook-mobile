package com.mcclabs.mook.feature.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mcclabs.mook.domain.model.UpdateState
import com.mcclabs.mook.platform.getStoreUrl
import com.mcclabs.mook.ui.components.NeonPrimaryButton
import com.mcclabs.mook.ui.theme.NeonColors
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.ui.platform.LocalUriHandler

@Composable
fun GlobalUpdateWrapper(
    viewModel: UpdateViewModel = koinViewModel()
) {
    val state by viewModel.updateState.collectAsStateWithLifecycle()
    var dismissedOptional by remember { mutableStateOf(false) }

    when (state) {
        UpdateState.ForceUpdateRequired -> {
            ForceUpdateDialog()
        }
        UpdateState.OptionalUpdateAvailable -> {
            if (!dismissedOptional) {
                OptionalUpdateDialog(onDismiss = { dismissedOptional = true })
            }
        }
        UpdateState.None -> {
            // Do nothing
        }
    }
}

@Composable
fun ForceUpdateDialog() {
    val uriHandler = LocalUriHandler.current
    
    Dialog(
        onDismissRequest = { /* Cannot dismiss */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        UpdateDialogContent(
            title = "Update Required",
            description = "A new version of Mook is required to continue. Please update to the latest version to enjoy the best experience and new features.",
            onUpdateClick = { uriHandler.openUri(getStoreUrl()) },
            onDismissClick = null
        )
    }
}

@Composable
fun OptionalUpdateDialog(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        UpdateDialogContent(
            title = "Update Available",
            description = "A new version of Mook is available. Update now to check out the latest features and improvements.",
            onUpdateClick = { 
                uriHandler.openUri(getStoreUrl()) 
                onDismiss()
            },
            onDismissClick = onDismiss
        )
    }
}

@Composable
private fun UpdateDialogContent(
    title: String,
    description: String,
    onUpdateClick: () -> Unit,
    onDismissClick: (() -> Unit)?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(NeonColors.GlassBackground)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = NeonColors.Primary,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = NeonColors.TextSecondary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        NeonPrimaryButton(
            text = "Update Now",
            onClick = onUpdateClick,
            modifier = Modifier.fillMaxWidth()
        )
        
        if (onDismissClick != null) {
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = onDismissClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Later",
                    style = MaterialTheme.typography.bodyLarge,
                    color = NeonColors.TextTertiary
                )
            }
        }
    }
}
