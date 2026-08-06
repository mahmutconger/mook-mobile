package com.mcclabs.mook.navigation

import androidx.compose.runtime.Composable
import androidx.savedstate.read
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.mcclabs.mook.feature.discover.DiscoverScreen
import com.mcclabs.mook.feature.eula.EulaGateScreen
import com.mcclabs.mook.feature.filters.FiltersScreen
import com.mcclabs.mook.feature.login.LoginScreen
import com.mcclabs.mook.feature.match.MatchScreen
import com.mcclabs.mook.feature.liked.LikedScreen
import com.mcclabs.mook.feature.profile.ProfileDetailsScreen
import com.mcclabs.mook.feature.profile.edit.EditProfileScreen
import com.mcclabs.mook.feature.registration.RegistrationScreen
import com.mcclabs.mook.feature.room.RoomGateScreen
import com.mcclabs.mook.feature.room.RoomSwitchScreen
import com.mcclabs.mook.feature.settings.SettingsScreen
import com.mcclabs.mook.feature.sso.SsoAuthorizeScreen
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    // A logged-out user starts at Login; an authenticated user goes through the EULA
    // gate, which forwards straight to Discover if they have already accepted.
    val startDestination = if (Firebase.auth.currentUser != null) NavRoutes.EulaGate.route else NavRoutes.Login.route

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(NavRoutes.Login.route) {
            LoginScreen(
                onNavigateToRegistration = {
                    navController.navigate(NavRoutes.Registration.route)
                },
                onNavigateToHome = {
                    // Route through the EULA gate; it forwards to Discover if already accepted.
                    navController.navigate(NavRoutes.EulaGate.route) {
                        popUpTo(NavRoutes.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(NavRoutes.EulaGate.route) {
            EulaGateScreen(
                onAccepted = {
                    navController.navigate(NavRoutes.RoomGate.route) {
                        popUpTo(NavRoutes.EulaGate.route) { inclusive = true }
                    }
                },
                onLogout = {
                    // The gate view-model performs the actual sign-out before this fires.
                    navController.navigate(NavRoutes.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(NavRoutes.Registration.route) {
            RegistrationScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToHome = {
                    navController.navigate(NavRoutes.RoomGate.route) {
                        popUpTo(NavRoutes.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(NavRoutes.RoomGate.route) {
            RoomGateScreen(
                onProceed = {
                    navController.navigate(NavRoutes.Discover.route) {
                        popUpTo(NavRoutes.RoomGate.route) { inclusive = true }
                    }
                }
            )
        }

        composable(NavRoutes.RoomSwitch.route) {
            RoomSwitchScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(NavRoutes.Discover.route) {
            DiscoverScreen(
                onNavigateToProfile = { profileId ->
                    navController.navigate(NavRoutes.ProfileDetails.createRoute(profileId))
                },
                onNavigateToMatch = { matchedUserId ->
                    navController.navigate(NavRoutes.Match.createRoute(matchedUserId))
                },
                onNavigateToLiked = {
                    navController.navigate(NavRoutes.Liked.route) { launchSingleTop = true }
                },
                onNavigateToRoomSwitch = {
                    navController.navigate(NavRoutes.RoomSwitch.route)
                }
            )
        }

        composable(NavRoutes.Liked.route) {
            LikedScreen(
                onNavigateToProfile = { profileId ->
                    navController.navigate(NavRoutes.ProfileDetails.createRoute(profileId))
                },
                onNavigateToDiscover = {
                    // Return to the existing Discover rather than stacking a new one.
                    navController.popBackStack(NavRoutes.Discover.route, inclusive = false)
                }
            )
        }

        composable(NavRoutes.ProfileDetails.route) { backStackEntry ->
            ProfileDetailsScreen(
                profileId = backStackEntry.arguments
                    ?.read { getStringOrNull(NavRoutes.ProfileDetails.ARG_PROFILE_ID) }
                    .orEmpty(),
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSettings = {
                    navController.navigate(NavRoutes.Settings.route)
                },
                onNavigateToEditProfile = {
                    navController.navigate(NavRoutes.EditProfile.route)
                }
            )
        }

        composable(NavRoutes.EditProfile.route) {
            EditProfileScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(NavRoutes.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToFilters = {
                    // Navigate back to Discover so user can open drawer from there
                    // Or we could pop back to Discover
                    navController.popBackStack(NavRoutes.Discover.route, inclusive = false)
                },
                onNavigateToLogin = {
                    navController.navigate(NavRoutes.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(NavRoutes.Match.route) { backStackEntry ->
            MatchScreen(
                matchedUserId = backStackEntry.arguments
                    ?.read { getStringOrNull(NavRoutes.Match.ARG_MATCHED_USER_ID) }
                    .orEmpty(),
                onKeepSwiping = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = NavRoutes.SsoAuthorize.route,
            deepLinks = listOf(navDeepLink { uriPattern = "mook://authorize?client={client}&callback={callback}" })
        ) {
            SsoAuthorizeScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToLogin = {
                    navController.navigate(NavRoutes.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
