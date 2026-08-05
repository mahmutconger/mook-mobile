package com.mcclabs.mook.domain.repository

import com.mcclabs.mook.domain.model.DiscoverProfile
import com.mcclabs.mook.domain.model.LikedProfile
import com.mcclabs.mook.domain.model.MatchSettings

interface DiscoverRepository {
    /** Returns the next batch of swipeable profiles matching [settings]. */
    suspend fun getDiscoverProfiles(settings: MatchSettings): List<DiscoverProfile>

    suspend fun getProfileDetails(profileId: String): DiscoverProfile?

    /**
     * Returns the people the current user has liked, newest first, each flagged with
     * whether the like is mutual. Backs the "Beğendiklerim" tab.
     */
    suspend fun getLikedProfiles(): List<LikedProfile>
}
