package com.mcclabs.mook.feature.settings

import mook.shared.generated.resources.settings_update_payment_method
import mook.shared.generated.resources.settings_billing_issue_notice
import mook.shared.generated.resources.settings_scheduled_change
import mook.shared.generated.resources.settings_scheduled_change_on
import androidx.compose.foundation.layout.width
import mook.shared.generated.resources.settings_incognito_subtitle_locked
import mook.shared.generated.resources.settings_incognito_subtitle
import mook.shared.generated.resources.settings_incognito_title
import com.mcclabs.mook.ui.components.PremiumBadge
import mook.shared.generated.resources.settings_privacy_consent_value
import mook.shared.generated.resources.settings_privacy_consent_label
import com.mcclabs.mook.feature.consent.PrivacyConsentViewModel
import com.mcclabs.mook.feature.consent.PrivacyConsentHost
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
import com.mcclabs.mook.domain.billing.playStoreSubscriptionManagementUrl
import com.mcclabs.mook.ads.AdPrivacyOptionsEntry
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
private const val PLAY_STORE_SUBSCRIPTIONS_URL =
    // Gereksinim 1.8: `package` parametresi eklenerek genel abonelik listesi yerine
    // doğrudan bu uygulamanın (Mook) aboneliklerine derin bağlantı kurulur.
    "https://play.google.com/store/account/subscriptions?package=com.mcclabs.mook"

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToFilters: () -> Unit = {},
    onNavigateToPaywall: () -> Unit = {},
    viewModel: SettingsViewModel = koinViewModel()
) {
    // KVKK tercih düzenleyicisi; kök ekrandaki zorunlu onaydan bağımsız örnek.
    val consentViewModel: PrivacyConsentViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.NavigateToLogin -> onNavigateToLogin()
                is SettingsEvent.AccountDeleted -> onNavigateToLogin()
                is SettingsEvent.NavigateToPaywall -> onNavigateToPaywall()
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

                    val deleteState = state.deleteAccount
                    // Gereksinim 1.8: abonelik uyarısı artık HER ZAMAN değil, yalnızca use case
                    // gerçekten aktif ücretli bir abonelik tespit ettiğinde gösterilir — Free
                    // bir kullanıcı artık kendisiyle alakasız bir uyarı görmez.
                    if (deleteState is DeleteAccountUiState.ActiveSubscriptionWarning) {
                        Spacer(modifier = Modifier.height(12.dp))
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
                    }

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
                val deleteState = state.deleteAccount
                val isAwaitingSubscriptionAck = deleteState is DeleteAccountUiState.ActiveSubscriptionWarning
                Button(
                    onClick = {
                        // Gereksinim 1.8: kullanıcı zaten aktif abonelik uyarısını gördüyse bu
                        // tıklama "yine de sil" onayıdır (kontrol tekrar edilmez); aksi halde
                        // normal silme denemesi başlatılır — ki bu deneme abonelik varsa
                        // yukarıdaki uyarı durumuna geçecektir.
                        if (isAwaitingSubscriptionAck) {
                            viewModel.onDeleteConfirmDespiteActiveSubscription()
                        } else {
                            viewModel.onDeleteConfirm()
                        }
                    },
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
                            text = stringResource(
                                if (isAwaitingSubscriptionAck) Res.string.settings_delete_account_confirm_anyway
                                else Res.string.settings_delete_account_confirm
                            ),
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
            // Gereksinim 1.10: yalnızca "Deneme" değil, kalan gün sayısını da göster.
            val daysLeft = com.mcclabs.mook.domain.billing.TrialPeriod.remainingDays(
                state.subscription,
                com.mcclabs.mook.util.getCurrentTimeMillis(),
            )
            if (daysLeft != null) {
                stringResource(Res.string.settings_membership_trial_days, daysLeft)
            } else {
                stringResource(Res.string.settings_membership_trial)
            }
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
        // Ertelenmiş düşürme: "Şu tarihte Ekonomik pakete geçilecek".
        val status = state.subscriptionStatus
        status.scheduledTier?.let { target ->
            val targetName = target.displayName()
            SettingsNotice(
                text = status.scheduledAtMillis
                    ?.let { stringResource(Res.string.settings_scheduled_change_on, it.formatSubscriptionDate(), targetName) }
                    ?: stringResource(Res.string.settings_scheduled_change, targetName),
                color = NeonColors.TextSecondary,
            )
        }
        // Ödeme sorunu: kullanıcı mağazada ödeme yöntemini güncelleyene kadar uyarı + bağlantı.
        if (status.showBillingIssue) {
            SettingsNotice(
                text = stringResource(Res.string.settings_billing_issue_notice),
                color = NeonColors.Error,
            )
            SettingsRow(
                title = stringResource(Res.string.settings_update_payment_method),
                value = stringResource(Res.string.settings_view_label),
                onClick = { uriHandler.openUri(status.paymentUpdateUrl) },
            )
        }
        if (state.subscription.tier != Tier.FREE) {
            SettingsRow(
                title = stringResource(Res.string.settings_membership_manage),
                value = stringResource(Res.string.settings_view_label),
                onClick = { uriHandler.openUri(PLAY_STORE_SUBSCRIPTIONS_URL) },
            )
        }
        // Gereksinim 2.3/2.4: bu cihazda en son satın alındığından bu yana fiyat artışı
        // tespit edildiyse (bkz. `PriceChangeConfirmationUseCase`), kullanıcıyı Google'ın
        // RESMİ Play Store onay yüzeyine yönlendiren bir bilgi şeridi gösterilir — Billing
        // Library'nin artık kaldırdığı uygulama içi bir onay ekranı TAKLİT EDİLMEZ (bkz.
        // use case'in sınıf yorumu).
        state.priceChangeNotice?.let { notice ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(
                    Res.string.settings_price_change_notice,
                    notice.previousLocalizedPrice,
                    notice.currentLocalizedPrice,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = NeonColors.Error,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            TextButton(
                onClick = {
                    uriHandler.openUri(
                        playStoreSubscriptionManagementUrl(
                            packageName = "com.mcclabs.mook",
                            productIdentifier = notice.productIdentifier,
                        ),
                    )
                },
            ) {
                Text(
                    text = stringResource(Res.string.settings_price_change_cta),
                    color = NeonColors.Primary,
                    fontWeight = FontWeight.Bold,
                )
            }
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
        // Gizli mod (Premium): Keşfet'te yalnızca beğendiğin kişilere görünürsün ve profil
        // ziyaretlerin kaydedilmez. Premium olmayan kullanıcıda anahtar Paywall'a götürür.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(Res.string.settings_incognito_title),
                        style = MaterialTheme.typography.bodyLarge,
                        color = NeonColors.TextPrimary
                    )
                    if (!state.subscription.limits.incognito) {
                        Spacer(modifier = Modifier.width(8.dp))
                        PremiumBadge(compact = true)
                    }
                }
                Text(
                    text = stringResource(
                        if (state.subscription.limits.incognito) Res.string.settings_incognito_subtitle
                        else Res.string.settings_incognito_subtitle_locked
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonColors.TextSecondary
                )
                state.incognitoMessage?.let { message ->
                    Text(text = message, style = MaterialTheme.typography.labelSmall, color = NeonColors.Error)
                }
            }
            NeonToggle(
                checked = state.incognitoEnabled,
                onCheckedChange = viewModel::onIncognitoChange
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
        AdPrivacyOptionsEntry()
        // KVKK: kullanıcı açık rızasını istediği an geri alabilmeli ya da değiştirebilmeli.
        SettingsRow(
            title = stringResource(Res.string.settings_privacy_consent_label),
            value = stringResource(Res.string.settings_privacy_consent_value),
            onClick = consentViewModel::openEditor,
        )
        PrivacyConsentHost(promptWhenRequired = false, viewModel = consentViewModel)

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

        // ── Gereksinim 5 (Faz 6, KVKK Madde 11): Verilerimi Dışa Aktar ─────────
        val isExporting = state.exportData is ExportDataUiState.Loading
        Button(
            onClick = viewModel::onExportDataClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NeonColors.CardBorder),
            shape = RoundedCornerShape(12.dp),
            enabled = !isExporting,
        ) {
            if (isExporting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = NeonColors.TextPrimary)
            } else {
                Text(stringResource(Res.string.settings_export_data_button), fontWeight = FontWeight.Bold)
            }
        }
        when (val exportState = state.exportData) {
            is ExportDataUiState.Requested -> {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(Res.string.settings_export_data_requested),
                    style = MaterialTheme.typography.bodySmall,
                    color = NeonColors.TextSecondary,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                )
            }
            is ExportDataUiState.Error -> {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = exportState.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = NeonColors.Error,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                )
            }
            else -> Unit
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

/** Abonelik bölümünde tek satırlık bilgilendirme metni. */
@Composable
private fun SettingsNotice(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
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
