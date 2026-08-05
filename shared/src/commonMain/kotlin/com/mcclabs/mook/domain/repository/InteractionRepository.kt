package com.mcclabs.mook.domain.repository

import com.mcclabs.mook.domain.model.MatchResult

interface InteractionRepository {
    suspend fun swipeUser(toUserId: String, isLike: Boolean): MatchResult
}
