package com.mcclabs.mook.domain.model

/**
 * A profile as shown in Discover and on the profile details screen.
 *
 * @property age Age in whole years, derived from the stored birth date, or `null` if unknown.
 * @property country The user's country, resolved from their stored ISO region code.
 * @property language The language the user speaks, resolved from their stored language code.
 * @property verified Whether the account is verified (shows the badge next to the name).
 * @property lastActiveMillis Epoch millis of the user's last recorded activity, or `null`
 *   if the document predates the field. Backs [isOnline].
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
    val hasLikedMe: Boolean = false,
    /** Sunucunun bildirdiği Premium abonelik — profilde Premium rozeti gösterilir. */
    val isPremium: Boolean = false,
    val lastActiveMillis: Long? = null,
) {
    /**
     * Whether to show the green "online" dot on the discovery card.
     *
     * Presence is derived from `lastActiveTimestamp` rather than a live connection, so the
     * window is deliberately generous: the app writes that field on foreground, and a user
     * who was active a couple of minutes ago is still, for the purposes of "should I say
     * hello", online. Unknown timestamps read as offline rather than optimistically online.
     */
    fun isOnline(nowMillis: Long): Boolean {
        val last = lastActiveMillis ?: return false
        val delta = nowMillis - last
        return delta in 0..ONLINE_WINDOW_MILLIS
    }

    companion object {
        /** How long after the last recorded activity a user still counts as online (5 min). */
        const val ONLINE_WINDOW_MILLIS: Long = 5 * 60 * 1000L
    }
}
