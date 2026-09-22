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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
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
import com.mcclabs.mook.domain.billing.Tier
import com.mcclabs.mook.ui.components.NeonToggle
import com.mcclabs.mook.ui.theme.NeonColors
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import mook.shared.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Google Play abonelik yönetimi derin bağlantısı (Play Store uygulamasında açılır). */
private const val PLAY_STORE_SUBSCRIPTIONS_URL = "https://play.google.com/store/account/subscriptions"

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToFilters: () -> Unit = {},
    onNavigateToPaywall: () -> Unit = {},
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

    // ── Hesap silme onay iletişim kutusu ─────────────────────────────────────
    if (state.showDeleteConfirmDialog) {
        val isDeleting = state.deleteAccount is DeleteAccountUiState.Loading
        AlertDialog(
            // Silme sürerken kapatmaya izin verilmez ki işlem yarıda kalmasın.
            onDismissRequest = { if (!isDeleting) viewModel.onDeleteDismiss() },
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
                Column {
                    Text(
                        text = stringResource(Res.string.settings_delete_account_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = NeonColors.TextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    // Play Store zorunluluğu: silmenin abonelikleri iptal ETMEDİĞİ uyarısı.
                    Text(
                        text = stringResource(Res.string.settings_delete_account_subscription_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = NeonColors.Error,
                        fontWeight = FontWeight.SemiBold
                    )
                    // Kullanıcıyı Google Play abonelik yönetimi sayfasına derin bağlantıyla götürür.
                    TextButton(onClick = { uriHandler.openUri(PLAY_STORE_SUBSCRIPTIONS_URL) }) {
                        Text(
                            text = stringResource(Res.string.settings_delete_account_manage_subs),
                            color = NeonColors.Primary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    val deleteState = state.deleteAccount
                    when (deleteState) {
                        is DeleteAccountUiState.Loading -> {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(Res.string.settings_delete_account_progress),
                                style = MaterialTheme.typography.bodySmall,
                                color = NeonColors.TextSecondary
                            )
                        }
                        is DeleteAccountUiState.Error -> {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = deleteState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = NeonColors.Error
                            )
                        }
                        else -> Unit
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = viewModel::onDeleteConfirm,
                    enabled = !isDeleting,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonColors.Error)
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = androidx.compose.ui.graphics.Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = stringResource(Res.string.settings_delete_account_confirm),
                            color = androidx.compose.ui.graphics.Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            dismissButton = {
                Button(
                    onClick = viewModel::onDeleteDismiss,
                    enabled = !isDeleting,
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

        // Keep subscriptions reachable before a user hits a quota. Besides being
        // necessary for license testing, this is the expected entry point for a
        // voluntary upgrade rather than making the limit dialog the only route.
        SectionLabel(stringResource(Res.string.settings_membership_section))
        val tierLabel = state.subscription.tier.displayName()
        val membershipStatus = if (state.subscription.inTrial) {
            stringResource(Res.string.settings_membership_trial)
        } else {
            stringResource(Res.string.settings_membership_active)
        }
        SettingsRow(
            title = stringResource(Res.string.settings_membership_current_plan),
            value = "$tierLabel · $membershipStatus",
            onClick = onNavigateToPaywall,
        )
        state.subscription.expiresAtMillis?.let { expiresAt ->
            SettingsRow(
                title = stringResource(
                    if (state.subscription.willRenew) Res.string.settings_membership_renews
                    else Res.string.settings_membership_ends,
                ),
                value = expiresAt.formatSubscriptionDate(),
            )
        }
        if (state.subscription.tier != Tier.FREE) {
            SettingsRow(
                title = stringResource(Res.string.settings_membership_manage),
                value = stringResource(Res.string.settings_view_label),
                onClick = { uriHandler.openUri(PLAY_STORE_SUBSCRIPTIONS_URL) },
            )
        }
        SettingsRow(
            title = stringResource(Res.string.settings_membership_restore),
            value = if (state.isRestoringPurchases) {
                stringResource(Res.string.settings_membership_restoring)
            } else {
                stringResource(Res.string.settings_membership_action)
            },
            onClick = viewModel::restorePurchases,
        )
        state.restorePurchasesMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = NeonColors.TextSecondary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
        }

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

        // Error message if deletion failed. The state is modelled as a sealed type,
        // rather than as a separate nullable string, so loading and error UI cannot
        // accidentally disagree.
        val deleteError = (state.deleteAccount as? DeleteAccountUiState.Error)?.message
        if (deleteError != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = deleteError,
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
private fun Tier.displayName(): String = when (this) {
    Tier.FREE -> stringResource(Res.string.settings_membership_free)
    Tier.ECONOMY -> stringResource(Res.string.settings_membership_economy)
    Tier.STANDARD -> stringResource(Res.string.settings_membership_standard)
    Tier.PREMIUM -> stringResource(Res.string.settings_membership_premium)
}

private fun Long.formatSubscriptionDate(): String {
    val date = Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.currentSystemDefault()).date
    return "${date.dayOfMonth.toString().padStart(2, '0')}.${date.monthNumber.toString().padStart(2, '0')}.${date.year}"
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
