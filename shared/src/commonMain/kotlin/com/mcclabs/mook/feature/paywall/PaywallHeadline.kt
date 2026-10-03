package com.mcclabs.mook.feature.paywall

import com.mcclabs.mook.domain.billing.LimitReason
import mook.shared.generated.resources.Res
import mook.shared.generated.resources.paywall_choose_plan
import mook.shared.generated.resources.paywall_limit_title_boosts
import mook.shared.generated.resources.paywall_limit_title_liked_me
import mook.shared.generated.resources.paywall_limit_title_likes
import mook.shared.generated.resources.paywall_limit_title_messages
import mook.shared.generated.resources.paywall_limit_title_new_chats
import mook.shared.generated.resources.paywall_limit_title_rewinds
import mook.shared.generated.resources.paywall_limit_title_room_switches
import org.jetbrains.compose.resources.StringResource

/**
 * Dinamik Paywall başlığı: Paywall'ı açan limite göre ana başlık değişir
 * (ör. "Beğeni hakkın bitti", "Günlük mesaj limitine ulaştın"). Limit yoksa (alt menüden,
 * ayarlardan açıldıysa) genel "Planını seç" başlığı gösterilir.
 */
object PaywallHeadline {
    fun titleFor(reason: LimitReason?): StringResource = when (reason) {
        null -> Res.string.paywall_choose_plan
        LimitReason.DAILY_LIKES -> Res.string.paywall_limit_title_likes
        LimitReason.DAILY_MESSAGES -> Res.string.paywall_limit_title_messages
        LimitReason.DAILY_NEW_CHATS -> Res.string.paywall_limit_title_new_chats
        LimitReason.ROOM_SWITCHES -> Res.string.paywall_limit_title_room_switches
        LimitReason.LIKED_ME_UNLOCKS -> Res.string.paywall_limit_title_liked_me
        LimitReason.REWINDS -> Res.string.paywall_limit_title_rewinds
        LimitReason.BOOSTS -> Res.string.paywall_limit_title_boosts
    }
}
