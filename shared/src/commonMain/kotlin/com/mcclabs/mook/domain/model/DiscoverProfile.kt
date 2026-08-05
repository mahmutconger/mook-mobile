package com.mcclabs.mook.domain.model

/**
 * A profile as shown in Discover and on the profile details screen.
 *
 * @property age Age in whole years, derived from the stored birth date, or `null` if unknown.
 * @property country The user's country, resolved from their stored ISO region code.
 * @property language The language the user speaks, resolved from their stored language code.
 * @property verified Whether the account is verified (shows the badge next to the name).
 */
data class DiscoverProfile(
    val id: String,
    val name: String,
    val age: Int?,
    val country: Country?,
    val language: Language?,
    val photoUrls: List<String>,
    val bio: String,
    val interests: List<String>,
    val verified: Boolean = false,
    val hasLikedMe: Boolean = false
)
