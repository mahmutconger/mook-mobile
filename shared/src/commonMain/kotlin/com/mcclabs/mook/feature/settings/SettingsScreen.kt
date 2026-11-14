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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import com.mcclabs.mook.domain.theme.ThemePreference
import com.mcclabs.mook.ui.theme.BrandGradient
import com.mcclabs.mook.ui.theme.LocalAppColors
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
import com.mcclabs.mook.ui.legal.LocalLegalLinks
import com.mcclabs.mook.domain.billing.Tier
import com.mcclabs.mook.domain.billing.playStoreSubscriptionManagementUrl
import com.mcclabs.mook.ads.AdPrivacyOptionsEntry
import com.mcclabs.mook.ui.components.NeonToggle
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
    onNavigateToEditProfile: () -> Unit = {},
    themeViewModel: com.mcclabs.mook.feature.theme.ThemeViewModel = koinViewModel(),
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
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = {
                Text(
                    text = stringResource(Res.string.settings_delete_account_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = stringResource(Res.string.settings_delete_account_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                        // Kullanıcıyı Google Play abonelik yönetimi sayfasına derin bağlantıyla götürür.
                        TextButton(onClick = { uriHandler.openUri(PLAY_STORE_SUBSCRIPTIONS_URL) }) {
                            Text(
                                text = stringResource(Res.string.settings_delete_account_manage_subs),
                                color = MaterialTheme.colorScheme.primary,
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        is DeleteAccountUiState.Error -> {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = deleteState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
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
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onError,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = stringResource(
                                if (isAwaitingSubscriptionAck) Res.string.settings_delete_account_confirm_anyway
                                else Res.string.settings_delete_account_confirm
                            ),
                            color = MaterialTheme.colorScheme.onError,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            dismissButton = {
                Button(
                    onClick = viewModel::onDeleteDismiss,
                    enabled = !isDeleting,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Text(stringResource(Res.string.settings_delete_account_cancel), color = MaterialTheme.colorScheme.onSurface)
                }
            }
        )
    }

    // ── Tema seçim iletişim kutusu ─────────────────────────────────────────
    val themeState by themeViewModel.uiState.collectAsState()
    var showThemeDialog by remember { mutableStateOf(false) }
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = {
                Text(
                    text = stringResource(Res.string.settings_theme_dialog_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column {
                    ThemePreference.entries.forEach { preference ->
                        val selected = themeState.preference == preference
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = !themeState.isLoading) {
                                    themeViewModel.selectTheme(preference)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selected, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = preference.displayName(),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text(stringResource(Res.string.settings_language_dialog_close), color = MaterialTheme.colorScheme.primary)
                }
            },
        )
    }

    // ── Dil seçim iletişim kutusu ──────────────────────────────────────────
    val languageOptions = listOf(
        "en" to "English", "tr" to "Türkçe", "es" to "Español", "fr" to "Français",
        "de" to "Deutsch", "it" to "Italiano", "pt" to "Português", "ru" to "Русский",
        "zh" to "中文", "ja" to "日本語",
    )
    if (state.showLanguageDialog) {
        AlertDialog(
            onDismissRequest = viewModel::onLanguageDismiss,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = {
                Text(
                    text = stringResource(Res.string.settings_language_dialog_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    languageOptions.forEach { (code, name) ->
                        val selected = state.appLanguage == code
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { viewModel.onLanguageSelected(code) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selected, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::onLanguageDismiss) {
                    Text(stringResource(Res.string.settings_language_dialog_close), color = MaterialTheme.colorScheme.primary)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        // ── Üst çubuk: geri + çıkış ──────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    painter = painterResource(Res.drawable.back_svgrepo_com),
                    contentDescription = stringResource(Res.string.settings_title),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { viewModel.logout() }) {
                Text(
                    text = stringResource(Res.string.settings_logout_short),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        // ── Profil başlığı ───────────────────────────────────────────────────
        ProfileHeader(
            name = state.profileName,
            email = state.email,
            photoUrl = state.profilePhotoUrl,
            onEditProfile = onNavigateToEditProfile,
        )

        // ── Üyelik ───────────────────────────────────────────────────────────
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
        SettingsItem(
            icon = Icons.Outlined.WorkspacePremium,
            title = stringResource(Res.string.settings_membership_current_plan),
            value = "$tierLabel · $membershipStatus",
            onClick = onNavigateToPaywall,
        )
        state.subscription.expiresAtMillis?.let { expiresAt ->
            SettingsItem(
                icon = Icons.Outlined.Event,
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Ödeme sorunu: kullanıcı mağazada ödeme yöntemini güncelleyene kadar uyarı + bağlantı.
        if (status.showBillingIssue) {
            SettingsNotice(
                text = stringResource(Res.string.settings_billing_issue_notice),
                color = MaterialTheme.colorScheme.error,
            )
            SettingsItem(
                icon = Icons.Outlined.CreditCard,
                title = stringResource(Res.string.settings_update_payment_method),
                value = stringResource(Res.string.settings_view_label),
                onClick = { uriHandler.openUri(status.paymentUpdateUrl) },
            )
        }
        if (state.subscription.tier != Tier.FREE) {
            SettingsItem(
                icon = Icons.Outlined.ManageAccounts,
                title = stringResource(Res.string.settings_membership_manage),
                onClick = { uriHandler.openUri(PLAY_STORE_SUBSCRIPTIONS_URL) },
            )
        }
        // Gereksinim 2.3/2.4: bu cihazda en son satın alındığından bu yana fiyat artışı
        // tespit edildiyse (bkz. `PriceChangeConfirmationUseCase`), kullanıcıyı Google'ın
        // RESMİ Play Store onay yüzeyine yönlendiren bir bilgi şeridi gösterilir — Billing
        // Library'nin artık kaldırdığı uygulama içi bir onay ekranı TAKLİT EDİLMEZ (bkz.
        // use case'in sınıf yorumu).
        state.priceChangeNotice?.let { notice ->
            SettingsNotice(
                text = stringResource(
                    Res.string.settings_price_change_notice,
                    notice.previousLocalizedPrice,
                    notice.currentLocalizedPrice,
                ),
                color = MaterialTheme.colorScheme.error,
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
                modifier = Modifier.padding(horizontal = 12.dp),
            ) {
                Text(
                    text = stringResource(Res.string.settings_price_change_cta),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        SettingsItem(
            icon = Icons.Outlined.Restore,
            title = stringResource(Res.string.settings_membership_restore),
            value = if (state.isRestoringPurchases) stringResource(Res.string.settings_membership_restoring) else null,
            onClick = viewModel::restorePurchases,
        )
        state.restorePurchasesMessage?.let { message ->
            SettingsNotice(text = message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // ── Tercihler ────────────────────────────────────────────────────────
        SectionLabel(stringResource(Res.string.settings_preferences_section))
        SettingsItem(
            icon = Icons.Outlined.Translate,
            title = stringResource(Res.string.settings_app_language_label),
            value = languageOptions.toMap()[state.appLanguage] ?: "English",
            onClick = viewModel::onLanguageClick,
        )
        SettingsItem(
            icon = Icons.Outlined.Palette,
            title = stringResource(Res.string.settings_theme_label),
            value = themeState.preference.displayName(),
            onClick = { showThemeDialog = true },
        )
        themeState.errorMessage?.let { SettingsNotice(text = it, color = MaterialTheme.colorScheme.error) }

        // ── Keşfet ───────────────────────────────────────────────────────────
        SectionLabel(stringResource(Res.string.settings_discovery_section))
        SettingsItem(
            icon = Icons.Outlined.Visibility,
            title = stringResource(Res.string.settings_show_in_discover_title),
            subtitle = stringResource(Res.string.settings_show_in_discover_subtitle),
            trailing = {
                NeonToggle(
                    checked = state.discoverVisible,
                    onCheckedChange = viewModel::onDiscoverVisibleChange
                )
            },
        )
        // Gizli mod (Premium): Keşfet'te yalnızca beğendiğin kişilere görünürsün ve profil
        // ziyaretlerin kaydedilmez. Premium olmayan kullanıcıda anahtar Paywall'a götürür.
        SettingsItem(
            icon = Icons.Outlined.VisibilityOff,
            title = stringResource(Res.string.settings_incognito_title),
            titleBadge = if (!state.subscription.limits.incognito) ({ PremiumBadge(compact = true) }) else null,
            subtitle = stringResource(
                if (state.subscription.limits.incognito) Res.string.settings_incognito_subtitle
                else Res.string.settings_incognito_subtitle_locked
            ),
            error = state.incognitoMessage,
            trailing = {
                NeonToggle(
                    checked = state.incognitoEnabled,
                    onCheckedChange = viewModel::onIncognitoChange
                )
            },
        )
        SettingsItem(
            icon = Icons.Outlined.Tune,
            title = stringResource(Res.string.settings_age_preferences_label),
            value = "${state.ageRangeStart} - ${state.ageRangeEnd}",
            onClick = onNavigateToFilters,
        )

        // ── Yasal ────────────────────────────────────────────────────────────
        SectionLabel(stringResource(Res.string.settings_legal_section))
        SettingsLegalLinks()
        // KVKK: kullanıcı açık rızasını istediği an geri alabilmeli ya da değiştirebilmeli.
        SettingsItem(
            icon = Icons.Outlined.PrivacyTip,
            title = stringResource(Res.string.settings_privacy_consent_label),
            value = stringResource(Res.string.settings_privacy_consent_value),
            onClick = consentViewModel::openEditor,
        )
        Box(modifier = Modifier.padding(horizontal = 12.dp)) { AdPrivacyOptionsEntry() }
        PrivacyConsentHost(promptWhenRequired = false, viewModel = consentViewModel)

        // ── Verilerin ────────────────────────────────────────────────────────
        SectionLabel(stringResource(Res.string.settings_data_section))
        // Gereksinim 5 (Faz 6, KVKK Madde 11): Verilerimi Dışa Aktar
        val isExporting = state.exportData is ExportDataUiState.Loading
        SettingsItem(
            icon = Icons.Outlined.Download,
            title = stringResource(Res.string.settings_export_data_button),
            onClick = if (isExporting) null else viewModel::onExportDataClick,
            trailing = if (isExporting) ({
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp,
                )
            }) else null,
        )
        when (val exportState = state.exportData) {
            is ExportDataUiState.Requested -> SettingsNotice(
                text = stringResource(Res.string.settings_export_data_requested),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            is ExportDataUiState.Error -> SettingsNotice(text = exportState.message, color = MaterialTheme.colorScheme.error)
            else -> Unit
        }
        // ── Delete Account (Google Play zorunluluğu) ─────────────────────────
        SettingsItem(
            icon = Icons.Outlined.DeleteOutline,
            title = stringResource(Res.string.settings_delete_account_button),
            destructive = true,
            onClick = if (state.isLoading) null else viewModel::onDeleteAccountClick,
        )

        // Error message if deletion failed. The state is modelled as a sealed type,
        // rather than as a separate nullable string, so loading and error UI cannot
        // accidentally disagree.
        val deleteError = (state.deleteAccount as? DeleteAccountUiState.Error)?.message
        if (deleteError != null) {
            SettingsNotice(text = deleteError, color = MaterialTheme.colorScheme.error)
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

/** Görseldeki gibi ortalanmış profil kartı: fotoğraf, ad, e-posta ve "Profili düzenle". */
@Composable
private fun ProfileHeader(
    name: String,
    email: String,
    photoUrl: String?,
    onEditProfile: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(92.dp)
                .clip(CircleShape)
                .background(BrandGradient),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = (name.ifBlank { email }).firstOrNull()?.uppercase() ?: "",
                style = MaterialTheme.typography.headlineMedium,
                color = LocalAppColors.current.onGradient,
                fontWeight = FontWeight.Bold,
            )
            if (photoUrl != null) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        if (name.isNotBlank()) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
        }
        if (email.isNotBlank()) {
            Text(
                text = email,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onEditProfile,
            shape = RoundedCornerShape(50),
            contentPadding = PaddingValues(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text(stringResource(Res.string.settings_edit_profile), fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * Görseldeki satır düzeni: solda yuvarlatılmış kare içinde ikon, başlık (+ isteğe bağlı alt
 * metin), sağda değer ve ok ya da özel bir öğe (ör. anahtar).
 */
@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    value: String? = null,
    subtitle: String? = null,
    error: String? = null,
    destructive: Boolean = false,
    titleBadge: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val contentColor = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIconTile(icon = icon, tint = contentColor)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = contentColor,
                    fontWeight = FontWeight.Medium,
                )
                if (titleBadge != null) {
                    Spacer(Modifier.width(8.dp))
                    titleBadge()
                }
            }
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (error != null) {
                Text(text = error, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
        if (value != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 160.dp),
            )
        }
        when {
            trailing != null -> {
                Spacer(Modifier.width(8.dp))
                trailing()
            }
            onClick != null -> Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun SettingsIconTile(icon: ImageVector, tint: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ThemePreference.displayName(): String = stringResource(
    when (this) {
        ThemePreference.System -> Res.string.settings_theme_system
        ThemePreference.Light -> Res.string.settings_theme_light
        ThemePreference.Dark -> Res.string.settings_theme_dark
        ThemePreference.Ocean -> Res.string.settings_theme_ocean
    }
)

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp)
    )
}

/** Abonelik bölümünde tek satırlık bilgilendirme metni. */
@Composable
private fun SettingsNotice(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = Modifier.padding(start = 72.dp, end = 20.dp, top = 2.dp, bottom = 6.dp),
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

/** Yasal bölümün üç satırı; her biri seçili dildeki ilgili sayfayı açar. */
@Composable
internal fun SettingsLegalLinks() {
    val uriHandler = LocalUriHandler.current
    val legalLinks = LocalLegalLinks.current
    SettingsItem(
        icon = Icons.Outlined.Lock,
        title = stringResource(Res.string.legal_privacy_policy),
        onClick = { uriHandler.openUri(legalLinks.privacyPolicyUrl) }
    )
    SettingsItem(
        icon = Icons.Outlined.Description,
        title = stringResource(Res.string.legal_terms_of_use),
        onClick = { uriHandler.openUri(legalLinks.termsOfUseUrl) }
    )
    SettingsItem(
        icon = Icons.Outlined.ChildCare,
        title = stringResource(Res.string.legal_child_safety),
        onClick = { uriHandler.openUri(legalLinks.childSafetyUrl) }
    )
}
