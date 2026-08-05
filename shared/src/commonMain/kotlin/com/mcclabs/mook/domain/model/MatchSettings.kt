package com.mcclabs.mook.domain.model

/**
 * The current user's Discover filters.
 *
 * Only filters that can actually be applied are modelled here: age is derived from
 * each profile's stored birth date. Distance and verification filters await a
 * location signal and a verification flag, neither of which is collected yet.
 */
data class MatchSettings(
    val ageRangeStart: Int = 18,
    val ageRangeEnd: Int = 35,
    val targetCountries: List<String> = emptyList(),
    val targetLanguages: List<String> = emptyList()
)
