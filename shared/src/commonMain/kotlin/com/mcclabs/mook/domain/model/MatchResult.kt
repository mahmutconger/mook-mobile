package com.mcclabs.mook.domain.model

sealed class MatchResult {
    object MutualMatch : MatchResult()
    object SingleLike : MatchResult()
    object Pass : MatchResult()
    data class Error(val message: String) : MatchResult()
}
