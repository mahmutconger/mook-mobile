package com.mcclabs.mook.feature.room

import com.mcclabs.mook.domain.model.Language
import com.mcclabs.mook.domain.model.Languages
import mook.shared.generated.resources.Res
import mook.shared.generated.resources.room_language_independent
import org.jetbrains.compose.resources.getString

/**
 * The synthetic "language-independent" room shown at the top of every room picker.
 * Its [Language.code] is the [Languages.LANGUAGE_INDEPENDENT_ROOM_CODE] sentinel, so
 * selecting it makes Discover show everyone regardless of native language.
 */
suspend fun languageIndependentRoom(): Language = Language(
    code = Languages.LANGUAGE_INDEPENDENT_ROOM_CODE,
    name = getString(Res.string.room_language_independent),
    flagEmoji = "🌐",
)
