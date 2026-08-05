package com.mcclabs.mook.domain.model

/**
 * Represents a user's complete profile in the Mook application.
 *
 * @property id Unique identifier for the user.
 * @property email User's email address.
 * @property displayName User's chosen display name.
 * @property bio Short biography or description written by the user.
 * @property avatarUrl URL pointing to the user's profile avatar image, if set.
 * @property nativeLanguage The language the user speaks natively.
 * @property targetLanguage The language the user is learning or wants to practice.
 * @property proficiencyLevel The user's self-assessed proficiency in their target language.
 * @property interests A list of the user's interests for matching purposes.
 * @property gender The user's gender identity, if provided.
 * @property birthDateMillis The user's date of birth as epoch milliseconds, if provided.
 * @property languageCode The user's preferred/spoken language code (DeepL codes, e.g. "EN-US").
 * @property countryCode ISO 3166-1 region code of the user's country, if provided.
 * @property discoverVisible Whether the user opts in to appear in Discover.
 */
data class UserProfile(
    val id: String,
    val email: String,
    val displayName: String,
    val bio: String = "",
    val avatarUrl: String? = null,
    val discoveryPhotos: List<String> = emptyList(),
    val nativeLanguage: Language? = null,
    val targetLanguage: Language? = null,
    val proficiencyLevel: ProficiencyLevel = ProficiencyLevel.BEGINNER,
    val interests: List<String> = emptyList(),
    val gender: Gender? = null,
    val birthDateMillis: Long? = null,
    val languageCode: String? = null,
    val countryCode: String? = null,
    val discoverVisible: Boolean = true,
    val lastActiveTimestamp: Long = 0L
)
