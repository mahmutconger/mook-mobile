package com.mcclabs.mook.feature.walktalkdemo

/**
 * One pre-filled line of the opening conversation.
 *
 * Seed originals are authored in the default pair's languages ([ChatSide.LEFT] in
 * Turkish, [ChatSide.RIGHT] in English) and are translated through the real
 * [com.mcclabs.mook.domain.translation.Translator] on first load, so the very first
 * thing the user sees is a genuine translation rather than canned copy.
 */
data class SeedLine(val side: ChatSide, val text: String)

/** The conversation the demo opens with: short, neutral, and about the product itself. */
object DemoSeed {
    val DEFAULT: List<SeedLine> = listOf(
        SeedLine(ChatSide.LEFT, "Merhaba! Ben Türkçe yazıyorum, sen kendi dilinde okuyorsun."),
        SeedLine(ChatSide.RIGHT, "That is wild — I do not speak a word of Turkish."),
        SeedLine(ChatSide.LEFT, "Aynen. Çeviri arada, biz fark etmeden çalışıyor."),
    )
}
