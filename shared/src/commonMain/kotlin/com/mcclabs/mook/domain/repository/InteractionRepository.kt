package com.mcclabs.mook.domain.repository

import com.mcclabs.mook.domain.model.MatchResult

interface InteractionRepository {
    suspend fun swipeUser(toUserId: String, isLike: Boolean): MatchResult
    suspend fun checkMutualMatch(withUserId: String): Boolean

    /** Removes the caller's latest pass through the server-authoritative rewind flow. */
    suspend fun rewindLastPass(): Result<String>

    /** Starts the server-authoritative 30-minute discovery Boost. */
    suspend fun activateBoost(): Result<Long>
}
