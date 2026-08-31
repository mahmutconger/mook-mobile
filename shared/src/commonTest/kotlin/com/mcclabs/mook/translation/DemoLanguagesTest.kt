package com.mcclabs.mook.translation

import com.mcclabs.mook.domain.model.Languages
import com.mcclabs.mook.domain.translation.DemoLanguages
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Guards the single-source-of-truth rule: the demo may narrow the app's language
 * catalogue, never fork it. A code added here that does not exist in [Languages] fails
 * the build rather than failing at runtime as an unsupported-language error from DeepL.
 */
class DemoLanguagesTest {

    @Test
    fun everyDemoCodeResolvesInTheSharedCatalogue() {
        DemoLanguages.OPTIONS.forEach { language ->
            assertNotNull(
                Languages.fromCode(language.code),
                "Demo language ${language.code} is not in Languages.ALL",
            )
        }
    }

    @Test
    fun demoOptionsAreDistinct() {
        val codes = DemoLanguages.OPTIONS.map { it.code }
        assertEquals(codes.size, codes.toSet().size, "Duplicate language codes: $codes")
    }

    @Test
    fun defaultPairIsTurkishToAmericanEnglish() {
        assertEquals("TR", DemoLanguages.DEFAULT_LEFT.code)
        assertEquals("EN-US", DemoLanguages.DEFAULT_RIGHT.code)
    }

    @Test
    fun defaultPairIsSelectable() {
        val codes = DemoLanguages.OPTIONS.map { it.code }
        assertTrue("TR" in codes)
        assertTrue("EN-US" in codes)
    }

    @Test
    fun codesUseDeepLSpelling() {
        // DeepL codes are upper-case and use a hyphen for regional variants ("PT-BR"),
        // never an underscore or a lower-case tag.
        DemoLanguages.OPTIONS.forEach { language ->
            assertEquals(language.code.uppercase(), language.code)
            assertTrue('_' !in language.code, "${language.code} must not contain '_'")
        }
    }

    @Test
    fun sentinelRoomCodeIsNotOfferedAsATranslationTarget() {
        assertTrue(
            DemoLanguages.OPTIONS.none { it.code == Languages.LANGUAGE_INDEPENDENT_ROOM_CODE },
            "The language-independent room sentinel is not a translatable language",
        )
    }
}
