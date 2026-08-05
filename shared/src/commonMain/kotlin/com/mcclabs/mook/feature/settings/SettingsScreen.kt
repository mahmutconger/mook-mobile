package com.mcclabs.mook.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalUriHandler
import com.mcclabs.mook.ui.components.NeonToggle
import com.mcclabs.mook.ui.theme.NeonColors
import mook.shared.generated.resources.Res
import mook.shared.generated.resources.back_svgrepo_com
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToFilters: () -> Unit = {},
    viewModel: SettingsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.NavigateToLogin -> onNavigateToLogin()
                is SettingsEvent.AccountDeleted -> onNavigateToLogin()
            }
        }
    }

    val uriHandler = LocalUriHandler.current

    // ── Delete-account confirmation dialog ───────────────────────────────────
    if (state.showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = viewModel::onDeleteDismiss,
            containerColor = NeonColors.Card,
            title = {
                Text(
                    text = "Hesabı Sil",
                    style = MaterialTheme.typography.titleMedium,
                    color = NeonColors.TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Hesabını kalıcı olarak silmek istediğinden emin misin? " +
                        "Profil bilgilerin, fotoğrafların ve tüm eşleşmelerin silinecek. " +
                        "Bu işlem geri alınamaz.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeonColors.TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = viewModel::onDeleteConfirm,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonColors.Error)
                ) {
                    Text("Evet, Sil", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Button(
                    onClick = viewModel::onDeleteDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonColors.CardBorder)
                ) {
                    Text("İptal", color = NeonColors.TextPrimary)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NeonColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    painter = painterResource(Res.drawable.back_svgrepo_com),
                    contentDescription = "Back",
                    tint = NeonColors.TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleLarge,
                color = NeonColors.TextPrimary,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Account
        SectionLabel("ACCOUNT")
        SettingsRow(title = "Email", value = state.email.ifEmpty { "—" })

        Spacer(modifier = Modifier.height(24.dp))

        // Appearance
        SectionLabel("APPEARANCE")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Koyu Tema",
                    style = MaterialTheme.typography.bodyLarge,
                    color = NeonColors.TextPrimary
                )
                Text(
                    text = "Uygulama genelinde koyu tema kullan",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonColors.TextSecondary
                )
            }
            NeonToggle(
                checked = state.isDarkMode,
                onCheckedChange = viewModel::onDarkModeChange
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Discovery
        SectionLabel("DISCOVERY")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Show me in Discover",
                    style = MaterialTheme.typography.bodyLarge,
                    color = NeonColors.TextPrimary
                )
                Text(
                    text = "Let others discover your profile",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonColors.TextSecondary
                )
            }
            NeonToggle(
                checked = state.discoverVisible,
                onCheckedChange = viewModel::onDiscoverVisibleChange
            )
        }
        SettingsRow(
            title = "Age preferences",
            value = "${state.ageRangeStart} - ${state.ageRangeEnd}",
            onClick = onNavigateToFilters
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Legal
        SectionLabel("LEGAL")
        SettingsRow(
            title = "Privacy Policy & Terms of Use",
            value = "View",
            onClick = { uriHandler.openUri("https://walktalkk.com/legal.html") }
        )

        Spacer(modifier = Modifier.height(40.dp))

        // Logout
        Button(
            onClick = { viewModel.logout() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NeonColors.CardBorder),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Çıkış Yap", color = NeonColors.Error, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Delete Account (Google Play zorunluluğu) ─────────────────────────
        Button(
            onClick = viewModel::onDeleteAccountClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NeonColors.Error),
            shape = RoundedCornerShape(12.dp),
            enabled = !state.isLoading
        ) {
            Text(
                text = "Hesabı Kalıcı Olarak Sil",
                color = androidx.compose.ui.graphics.Color.White,
                fontWeight = FontWeight.Bold
            )
        }

        // Error message if deletion failed
        if (state.deleteError != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = state.deleteError!!,
                style = MaterialTheme.typography.bodySmall,
                color = NeonColors.Error,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = NeonColors.Primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
    )
}

@Composable
private fun SettingsRow(
    title: String,
    value: String,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = NeonColors.TextPrimary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = NeonColors.TextSecondary
        )
    }
}
