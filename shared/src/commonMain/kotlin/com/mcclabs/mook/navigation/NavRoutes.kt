package com.mcclabs.mook.navigation

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

    data object ProfileDetails : NavRoutes("profile/{profileId}") {
        const val ARG_PROFILE_ID = "profileId"
        fun createRoute(profileId: String) = "profile/$profileId"
    }

    data object Match : NavRoutes("match/{matchedUserId}") {
        const val ARG_MATCHED_USER_ID = "matchedUserId"
        fun createRoute(matchedUserId: String) = "match/$matchedUserId"
    }

    data object SsoAuthorize : NavRoutes("sso_authorize?client={client}&callback={callback}") {
        const val ARG_CLIENT = "client"
        const val ARG_CALLBACK = "callback"
    }

    data object EditProfile : NavRoutes("edit_profile")

    data object Paywall : NavRoutes("paywall")

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
