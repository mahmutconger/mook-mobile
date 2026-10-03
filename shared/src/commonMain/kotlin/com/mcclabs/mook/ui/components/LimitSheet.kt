package com.mcclabs.mook.ui.components

import mook.shared.generated.resources.limit_sheet_come_back_tomorrow
import mook.shared.generated.resources.limit_sheet_try_standard_trial
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mcclabs.mook.ads.RewardedAdButton
import com.mcclabs.mook.domain.billing.RewardType
import com.mcclabs.mook.domain.billing.BillingConfig
import com.mcclabs.mook.domain.billing.EntitlementState
import com.mcclabs.mook.domain.billing.LimitReason
import com.mcclabs.mook.ui.theme.NeonColors
import mook.shared.generated.resources.Res
import mook.shared.generated.resources.discover_block_cancel
import mook.shared.generated.resources.discover_swipe_limit_body
import mook.shared.generated.resources.discover_swipe_limit_title
import mook.shared.generated.resources.discover_upgrade_cta
import mook.shared.generated.resources.limit_sheet_body_boosts
import mook.shared.generated.resources.limit_sheet_body_boosts_fair_use
import mook.shared.generated.resources.limit_sheet_body_liked_me
import mook.shared.generated.resources.limit_sheet_body_messages
import mook.shared.generated.resources.limit_sheet_body_new_chats
import mook.shared.generated.resources.limit_sheet_body_rewinds
import mook.shared.generated.resources.limit_sheet_body_room_switches
import mook.shared.generated.resources.limit_sheet_ok_button
import mook.shared.generated.resources.limit_sheet_title_fair_use
import mook.shared.generated.resources.limit_sheet_title_monthly
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Bir [LimitReason]'a göre dinamik olarak içerik değiştiren, tek/paylaşılan Limit Sheet
 * (Gereksinim 1.5). Daha önce Discover ve Profil Detayı ekranlarının kendi ayrı (ve içerik
 * olarak yanlış — her ikisi de her zaman "+5 beğeni" ve sabit "beğeni" metni gösteriyordu)
 * diyalogları vardı; bu tek bileşen ikisinin de yerini alır.
 *
 * @param reason Limitin hangi özellikle ilgili olduğu — metni ve "+5 beğeni izle" düğmesinin
 *   görünürlüğünü belirler ("Mesaj ya da Oda limiti ise +5 Beğeni GÖSTERİLMEMELİ").
 * @param entitlement Efektif sayısal limiti hesaplamak için (tavan + Free bonus/adil kullanım).
 * @param rewardedLikesToday Yalnızca [LimitReason.DAILY_LIKES] için efektif limit hesabında kullanılır.
 * @param isFairUseCap `true` ise hiçbir üst kademe bu limiti gevşetmez (bkz.
 *   `GateDecision.LimitReached.upgradeTo == null`) — bu durumda "planlara bak" YERİNE bir
 *   adil kullanım açıklaması ve yalnızca "Anladım" düğmesi gösterilir; kullanıcıyı asla
 *   çözmeyecek bir yükseltmeye yönlendirmeyiz.
 * @param showRewardedAd Kullanıcı bu limit için bugün hâlâ ödüllü reklamla hak kazanabiliyorsa `true`
 *   (Free/Economy; bkz. [RewardType.forLimitReason]).
 */
@Composable
fun LimitSheet(
    reason: LimitReason,
    entitlement: EntitlementState,
    rewardedLikesToday: Int,
    isFairUseCap: Boolean,
    showRewardedAd: Boolean,
    onRewardConfirmed: () -> Unit,
    onUpgrade: () -> Unit,
    onDismiss: () -> Unit,
    /**
     * "Standart'ı 3 gün ücretsiz dene" seçildiğinde (Paywall deneme paketi seçili açılır).
     * `null` ise seçenek hiç gösterilmez. Seçenek ayrıca yalnızca deneme uygunluğu
     * doğrulandıysa görünür (bkz. [TrialOfferUseCase]).
     */
    onStartTrial: (() -> Unit)? = null,
    viewModel: LimitSheetViewModel = koinViewModel(),
) {
    val sheetState by viewModel.state.collectAsState()
    LaunchedEffect(reason) { viewModel.refresh() }
    val trialOffer = sheetState.trialOffer?.takeIf { onStartTrial != null && !entitlement.inTrial && !isFairUseCap }
    val limits = entitlement.limits
    val limit = when (reason) {
        LimitReason.DAILY_LIKES -> BillingConfig.effectiveDailyLikeLimit(limits, entitlement.tier, rewardedLikesToday)
        LimitReason.DAILY_MESSAGES -> BillingConfig.effectiveDailyMessageLimit(limits)
        LimitReason.DAILY_NEW_CHATS -> limits.dailyNewChats
        LimitReason.ROOM_SWITCHES -> limits.roomSwitchesPerDay
        LimitReason.LIKED_ME_UNLOCKS -> limits.likedMeUnlocksPerDay
        LimitReason.REWINDS -> limits.rewindsPerDay
        LimitReason.BOOSTS -> limits.boostsPerMonth
    } ?: 0

    val titleRes: StringResource = when {
        isFairUseCap -> Res.string.limit_sheet_title_fair_use
        reason == LimitReason.BOOSTS -> Res.string.limit_sheet_title_monthly
        else -> Res.string.discover_swipe_limit_title
    }
    val bodyRes: StringResource = when (reason) {
        LimitReason.DAILY_LIKES -> Res.string.discover_swipe_limit_body
        LimitReason.DAILY_MESSAGES -> Res.string.limit_sheet_body_messages
        LimitReason.DAILY_NEW_CHATS -> Res.string.limit_sheet_body_new_chats
        LimitReason.ROOM_SWITCHES -> Res.string.limit_sheet_body_room_switches
        LimitReason.LIKED_ME_UNLOCKS -> Res.string.limit_sheet_body_liked_me
        LimitReason.REWINDS -> Res.string.limit_sheet_body_rewinds
        LimitReason.BOOSTS -> if (isFairUseCap) Res.string.limit_sheet_body_boosts_fair_use else Res.string.limit_sheet_body_boosts
    }
    // Ödüllü reklam yalnızca kazanç yolu olan limitlerde teklif edilir: beğeni (+5),
    // "beni beğenenler" (+1 açma) ve oda değiştirme (+1). Mesaj/sohbet limitlerinde reklamla
    // hak kazanılamaz (bkz. RewardType.forLimitReason).
    val rewardType = RewardType.forLimitReason(reason)
    val offerRewardedAd = showRewardedAd && rewardType != null

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NeonColors.Card,
        title = {
            Text(
                text = stringResource(titleRes),
                color = NeonColors.TextPrimary,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(bodyRes, limit),
                    color = NeonColors.TextSecondary,
                )
                if (offerRewardedAd && rewardType != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    RewardedAdButton(
                        rewardType = rewardType,
                        enabled = true,
                        onRewardConfirmed = onRewardConfirmed,
                    )
                }
                if (trialOffer != null && onStartTrial != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = onStartTrial, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(Res.string.limit_sheet_try_standard_trial, trialOffer.trialDays),
                            color = NeonColors.Primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                // "Yarın tekrar gel": günlük haklar yerel gece yarısı yenilenir (Boost aylık döngüdedir).
                if (reason != LimitReason.BOOSTS) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(Res.string.limit_sheet_come_back_tomorrow),
                        style = MaterialTheme.typography.labelLarge,
                        color = NeonColors.TextSecondary,
                    )
                    MidnightCountdownText()
                }
            }
        },
        confirmButton = {
            when {
                isFairUseCap -> {
                    // Yükseltme bu limiti çözmeyeceğinden yükseltme CTA'sı hiç gösterilmez.
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(Res.string.limit_sheet_ok_button), color = NeonColors.Primary)
                    }
                }
                // Gereksinim 1.10: aktif bir denemedeyken genel "Yükselt" teşviki bastırılır.
                // LimitSheet belirli bir hedef kademe bilmediğinden (yalnızca Paywall'a genel
                // bir yönlendirme yapar) TrialPeriod.shouldShowUpgradePrompt'un "cross-grade"
                // ayrımı burada uygulanamaz; bu yüzden deneme sırasında doğrudan bastırılır —
                // kullanıcı zaten bir kademeyi deniyorken tekrar "yükselt" göstermek gürültüdür.
                entitlement.inTrial -> {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(Res.string.limit_sheet_ok_button), color = NeonColors.Primary)
                    }
                }
                else -> {
                    TextButton(onClick = onUpgrade) {
                        Text(stringResource(Res.string.discover_upgrade_cta), color = NeonColors.Primary)
                    }
                }
            }
        },
        dismissButton = {
            if (!isFairUseCap) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.discover_block_cancel), color = NeonColors.TextSecondary)
                }
            }
        },
    )
}
