package com.mcclabs.mook.navigation

import com.mcclabs.mook.domain.billing.PaywallRequest

sealed class NavRoutes(val route: String) {
    data object Onboarding : NavRoutes("onboarding")
    data object Login : NavRoutes("login")
    data object Registration : NavRoutes("registration")

    /** Mandatory EULA/UGC-policy acceptance gate shown after login. */
    data object EulaGate : NavRoutes("eula_gate")

    /** Mandatory language-room selection gate shown after the EULA gate, before Discover. */
    data object RoomGate : NavRoutes("room_gate")

    /** Lets the user change their current language room from Discover. */
    data object RoomSwitch : NavRoutes("room_switch")

    data object Discover : NavRoutes("discover")
    data object Filters : NavRoutes("filters")
    data object Settings : NavRoutes("settings")

    /** "Beğendiklerim" — the people the user has liked (Chats tab destination). */
    data object Liked : NavRoutes("liked")

    /** "Beni Beğenenler" — kullanıcıyı beğenen kişiler (kilitliler bulanık gösterilir). */
    data object LikedMe : NavRoutes("liked_me")

    data object ProfileDetails : NavRoutes("profile/{profileId}") {
        const val ARG_PROFILE_ID = "profileId"
        fun createRoute(profileId: String) = "profile/$profileId"
    }

    data object Match : NavRoutes("match/{matchedUserId}") {
        const val ARG_MATCHED_USER_ID = "matchedUserId"
        fun createRoute(matchedUserId: String) = "match/$matchedUserId"
    }

    /** Parametre adları OAuth 2.0 ile aynıdır ve App Link sorgusundan birebir okunur. */
    data object SsoAuthorize : NavRoutes(
        "sso_authorize?client_id={client_id}&redirect_uri={redirect_uri}&state={state}&mook_state={mook_state}",
    ) {
        const val ARG_CLIENT_ID = "client_id"
        const val ARG_REDIRECT_URI = "redirect_uri"
        const val ARG_STATE = "state"
        const val ARG_MOOK_STATE = "mook_state"
    }

    data object EditProfile : NavRoutes("edit_profile")

    /**
     * Gereksinim 2.13 (Faz 4): [ARG_PRESELECT_TRIAL] isteğe bağlı sorgu parametresi --
     * Remote Config'in `show_onboarding_trial_offer` bayrağı açıkken profil onboarding'i
     * bitiren kullanıcıyı Deneme teklifi ÖNCEDEN SEÇİLMİŞ olarak Paywall'a yönlendirmek
     * için kullanılır (bkz. `RegistrationViewModel.completeProfile()`). Belirtilmezse `false`
     * varsayılır ve ekran eskisi gibi davranır.
     */
    data object Paywall : NavRoutes("paywall?preselectTrial={preselectTrial}&reason={reason}") {
        const val ARG_PRESELECT_TRIAL = "preselectTrial"
        /** Paywall'ı açan limit (LimitReason adı); başlık buna göre dinamik değişir. */
        const val ARG_REASON = "reason"
        fun createRoute(preselectTrial: Boolean = false) = "paywall?preselectTrial=$preselectTrial"
        fun createRoute(request: PaywallRequest): String =
            "paywall?preselectTrial=${request.preselectTrial}" + (request.reason?.let { "&reason=${it.name}" } ?: "")
    }

    /** Interactive live-translation showcase for the companion WalkTalk app. */
    data object WalkTalkDemo : NavRoutes("walktalk_demo")

    /** 1-on-1 real-time chat with a matched user. */
    data object Chat : NavRoutes("chat/{chatId}/{peerUid}") {
        const val ARG_CHAT_ID = "chatId"
        const val ARG_PEER_UID = "peerUid"
        fun createRoute(chatId: String, peerUid: String) = "chat/$chatId/$peerUid"
    }

    /** Chat list (inbox) showing active conversations. */
    data object ChatList : NavRoutes("chatlist")
}
