package com.mcclabs.mook.feature.room

import com.mcclabs.mook.domain.billing.PaywallRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.mcclabs.mook.domain.billing.LimitReason
import com.mcclabs.mook.ui.components.LimitSheet
import com.mcclabs.mook.ui.components.TimedInfoDialog
import com.mcclabs.mook.ui.theme.NeonColors
import mook.shared.generated.resources.Res
import mook.shared.generated.resources.back_svgrepo_com
import mook.shared.generated.resources.ic_info_circle
import mook.shared.generated.resources.room_downgrade_body
import mook.shared.generated.resources.room_downgrade_confirm
import mook.shared.generated.resources.room_downgrade_later
import mook.shared.generated.resources.room_downgrade_title
import mook.shared.generated.resources.room_info_body
import mook.shared.generated.resources.room_info_confirm
import mook.shared.generated.resources.room_info_title
import mook.shared.generated.resources.room_switch_back_cd
import mook.shared.generated.resources.room_error_slot_limit
import mook.shared.generated.resources.room_error_unavailable
import mook.shared.generated.resources.room_error_daily_limit
import mook.shared.generated.resources.error_offline
import mook.shared.generated.resources.room_switch_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Lets the user switch their current language room from Discover. */
@Composable
fun RoomSwitchScreen(
    onNavigateBack: () -> Unit,
    /** Günlük oda değiştirme limitinde kullanıcı yükseltmeyi seçerse çağrılır. */
    onNavigateToPaywall: (PaywallRequest) -> Unit = {},
    viewModel: RoomSwitchViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is RoomSwitchEvent.Done -> onNavigateBack()
                is RoomSwitchEvent.NavigateToPaywall -> onNavigateToPaywall(event.request)
            }
        }
    }

    // Günlük oda değiştirme hakkı doldu: genel hata yerine ortak limit sayfası.
    if (state.showDailyLimitSheet) {
        LimitSheet(
            reason = LimitReason.ROOM_SWITCHES,
            entitlement = state.entitlement,
            rewardedLikesToday = 0,
            isFairUseCap = false,
            showRewardedAd = state.canEarnRoomSwitchReward,
            onRewardConfirmed = viewModel::onRoomSwitchRewardConfirmed,
            onUpgrade = viewModel::onUpgradeFromLimitSheet,
            onStartTrial = viewModel::onTrialFromLimitSheet,
            onDismiss = viewModel::onDailyLimitSheetDismissed,
        )
    }

    if (state.showInfo) {
        TimedInfoDialog(
            icon = Res.drawable.ic_info_circle,
            title = stringResource(Res.string.room_info_title),
            body = stringResource(Res.string.room_info_body),
            confirmText = stringResource(Res.string.room_info_confirm),
            onConfirm = { viewModel.onInfoDismissed() },
        )
    }

    // Gereksinim 1.7: bir kademe düşüşü açık oda sayısını yeni sınırın üzerine
    // çıkardıysa, kullanıcı hangi fazla odaları kapatacağını seçmeli.
    state.downgradePrompt?.let { prompt ->
        RoomDowngradeDialog(
            prompt = prompt,
            onToggle = { viewModel.onToggleRoomToClose(it) },
            onConfirm = { viewModel.onConfirmCloseRooms() },
            onDismiss = { viewModel.onDowngradePromptDismissed() },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NeonColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    painter = painterResource(Res.drawable.back_svgrepo_com),
                    contentDescription = stringResource(Res.string.room_switch_back_cd),
                    tint = NeonColors.TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = stringResource(Res.string.room_switch_title),
                style = MaterialTheme.typography.titleLarge,
                color = NeonColors.TextPrimary,
                fontWeight = FontWeight.Bold
            )
        }

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NeonColors.Primary)
            }
        } else {
            RoomPickerGrid(
                languages = state.languages,
                selectedCode = state.selectedCode,
                onSelect = { viewModel.onRoomSelected(it) },
                modifier = Modifier.weight(1f)
            )
            state.error?.let { error ->
                Text(
                    text = stringResource(
                        when (error) {
                            RoomSelectionError.SLOT_LIMIT -> Res.string.room_error_slot_limit
                            RoomSelectionError.OFFLINE -> Res.string.error_offline
                            RoomSelectionError.UNAVAILABLE -> Res.string.room_error_unavailable
                            RoomSelectionError.DAILY_LIMIT -> Res.string.room_error_daily_limit
                        },
                    ),
                    color = NeonColors.Error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                )
            }
        }
    }
}

/**
 * Gereksinim 1.7: bir kademe düşüşü sonrası kullanıcının hangi fazla oda slotlarını
 * kapatacağını seçtiği istem. [onDismiss] "daha sonra" anlamına gelir — kapatma
 * kararını yok saymak değil, en eski kullanılan odaların otomatik kapatılmasını
 * tetikler (bkz. `RoomSwitchViewModel.onDowngradePromptDismissed`).
 */
@Composable
private fun RoomDowngradeDialog(
    prompt: RoomDowngradePrompt,
    onToggle: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NeonColors.Card,
        title = {
            Text(
                text = stringResource(Res.string.room_downgrade_title),
                color = NeonColors.TextPrimary,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(Res.string.room_downgrade_body, prompt.allowedSlots),
                    color = NeonColors.TextSecondary,
                )
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    prompt.openRooms.forEach { language ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = language.code in prompt.selectedToClose,
                                onCheckedChange = { onToggle(language.code) },
                            )
                            Text(
                                text = "${language.flagEmoji} ${language.name}",
                                color = NeonColors.TextPrimary,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = prompt.canConfirm) {
                Text(stringResource(Res.string.room_downgrade_confirm), color = NeonColors.Primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.room_downgrade_later), color = NeonColors.TextSecondary)
            }
        },
    )
}
