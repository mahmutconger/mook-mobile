package com.mcclabs.mook.domain.repository

import com.mcclabs.mook.domain.model.DiscoverProfile
import com.mcclabs.mook.domain.model.LikedProfile
import com.mcclabs.mook.domain.model.MatchSettings

data class LikeUsage(val likes: Int = 0, val rewardedLikes: Int = 0, val likesEver: Int = 0)

interface DiscoverRepository {
    /**
     * Returns the next page of profiles matching [settings].
     *
     * Paging is stateful: each call continues after the last document the previous call
     * consumed, and changing [settings] restarts from the top. An empty result means the
     * room is exhausted for these filters — see [hasMoreProfiles].
     */
    suspend fun getDiscoverProfiles(settings: MatchSettings): List<DiscoverProfile>

    /**
     * Whether another page is available for the filters last passed to
     * [getDiscoverProfiles]. Lets the grid stop asking instead of firing a
     * request per scroll that can only come back empty.
     */
    fun hasMoreProfiles(): Boolean

    /**
     * Drops the paging cursor so the next [getDiscoverProfiles] call starts from the top.
     * Used by pull-to-refresh; the swiped/blocked exclusion cache is rebuilt too, so people
     * who were passed on in another session do not reappear.
     */
    fun resetDiscoverPaging()

    /**
     * Records that the user has liked, passed on, reported or blocked [profileId], so later
     * pages and refreshes skip them. Screens use it to keep the feed consistent after an
     * action taken somewhere else (e.g. liking from the profile screen).
     */
    fun markActedOn(profileId: String)

    /** Re-includes a profile after the server has successfully rewound its last pass. */
    fun unmarkActedOn(profileId: String)

    /**
     * Every id excluded from Discover for this session — past interactions, blocks, and
     * anything passed to [markActedOn]. Discovery prunes its visible cards against this on
     * resume instead of re-querying.
     */
    fun actedOnProfileIds(): Set<String>

    suspend fun getProfileDetails(profileId: String): DiscoverProfile?

    /**
     * Returns the people the current user has liked, newest first, each flagged with
     * whether the like is mutual. Backs the "Beğendiklerim" tab.
     */
    suspend fun getLikedProfiles(): List<LikedProfile>

    /**
     * How many times the current user has swiped (like or pass) since local midnight.
     * Server-derived so the free daily limit cannot be reset by clearing local state.
     * Returns 0 on failure so a transient read error never hard-blocks swiping.
     */
    suspend fun getLikeUsageToday(): LikeUsage
}
