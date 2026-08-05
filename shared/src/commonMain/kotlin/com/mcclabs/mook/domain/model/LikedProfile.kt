package com.mcclabs.mook.domain.model

/**
 * A profile the current user has liked, shown in the "Beğendiklerim" tab.
 *
 * @property profile The liked person's profile.
 * @property isMatch `true` when the like is mutual (a `matches` document exists),
 *   so the row can badge it as an actual match.
 */
data class LikedProfile(
    val profile: DiscoverProfile,
    val isMatch: Boolean
)
