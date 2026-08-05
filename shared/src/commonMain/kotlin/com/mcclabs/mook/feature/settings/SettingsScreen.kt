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
import mook.shared.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
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
                    text = stringResource(Res.string.settings_delete_account_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = NeonColors.TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(Res.string.settings_delete_account_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeonColors.TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = viewModel::onDeleteConfirm,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonColors.Error)
                ) {
                    Text(stringResource(Res.string.settings_delete_account_confirm), color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Button(
                    onClick = viewModel::onDeleteDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonColors.CardBorder)
                ) {
                    Text(stringResource(Res.string.settings_delete_account_cancel), color = NeonColors.TextPrimary)
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
                text = stringResource(Res.string.settings_title),
                style = MaterialTheme.typography.titleLarge,
                color = NeonColors.TextPrimary,
                fontWeight = FontWeight.Bold
            )
        }

        // ── Language Selection Dialog ───────────────────────────────────────────
        if (state.showLanguageDialog) {
            AlertDialog(
                onDismissRequest = viewModel::onLanguageDismiss,
                containerColor = NeonColors.Card,
                title = {
                    Text(
                        text = stringResource(Res.string.settings_language_dialog_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = NeonColors.TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column {
                        val languages = listOf(
                            "en" to "English",
                            "tr" to "Türkçe",
                            "es" to "Español",
                            "fr" to "Français",
                            "de" to "Deutsch",
                            "it" to "Italiano",
                            "pt" to "Português",
                            "ru" to "Русский",
                            "zh" to "中文",
                            "ja" to "日本語"
                        )
                        languages.forEach { (code, name) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.onLanguageSelected(code) }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (state.appLanguage == code) NeonColors.Primary else NeonColors.TextPrimary,
                                    fontWeight = if (state.appLanguage == code) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = viewModel::onLanguageDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = NeonColors.CardBorder)
                    ) {
                        Text(stringResource(Res.string.settings_language_dialog_close), color = NeonColors.TextPrimary)
                    }
                }
            )
        }

        // Account Details Section
        Spacer(modifier = Modifier.height(24.dp))
        SectionLabel(stringResource(Res.string.settings_account_section))
        SettingsRow(title = stringResource(Res.string.settings_email_label), value = state.email.ifEmpty { "—" })

        Spacer(modifier = Modifier.height(24.dp))

        // Appearance
        SectionLabel(stringResource(Res.string.settings_appearance_section))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.settings_dark_mode_title),
                    style = MaterialTheme.typography.bodyLarge,
                    color = NeonColors.TextPrimary
                )
                Text(
                    text = stringResource(Res.string.settings_dark_mode_subtitle),
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonColors.TextSecondary
                )
            }
            NeonToggle(
                checked = state.isDarkMode,
                onCheckedChange = viewModel::onDarkModeChange
            )
        }

        val languageMap = mapOf(
            "en" to "English", "tr" to "Türkçe", "es" to "Español", "fr" to "Français",
            "de" to "Deutsch", "it" to "Italiano", "pt" to "Português", "ru" to "Русский",
            "zh" to "中文", "ja" to "日本語"
        )
        val currentLangName = languageMap[state.appLanguage] ?: "English"

        SettingsRow(
            title = stringResource(Res.string.settings_app_language_label),
            value = currentLangName,
            onClick = viewModel::onLanguageClick
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Discovery
        SectionLabel(stringResource(Res.string.settings_discovery_section))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.settings_show_in_discover_title),
                    style = MaterialTheme.typography.bodyLarge,
                    color = NeonColors.TextPrimary
                )
                Text(
                    text = stringResource(Res.string.settings_show_in_discover_subtitle),
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
            title = stringResource(Res.string.settings_age_preferences_label),
            value = "${state.ageRangeStart} - ${state.ageRangeEnd}",
            onClick = onNavigateToFilters
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Legal
        SectionLabel(stringResource(Res.string.settings_legal_section))
        SettingsRow(
            title = stringResource(Res.string.settings_privacy_policy_label),
            value = stringResource(Res.string.settings_view_label),
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
            Text(stringResource(Res.string.settings_logout_button), color = NeonColors.Error, fontWeight = FontWeight.Bold)
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
                text = stringResource(Res.string.settings_delete_account_button),
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
