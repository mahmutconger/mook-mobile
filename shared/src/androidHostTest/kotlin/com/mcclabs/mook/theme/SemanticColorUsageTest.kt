package com.mcclabs.mook.theme

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class SemanticColorUsageTest {
    @Test fun uiSourcesContainNoLiteralPaletteColors() {
        val project = listOf(File("."), File("shared")).first { File(it, "src/commonMain/kotlin").isDirectory }
        val literal = Regex("""(?<![A-Za-z])Color\(|Color\.(?:White|Black|Gray|Red|Green|Blue|Yellow|Cyan|Magenta|LightGray|DarkGray|rgb|argb)\b""")
        val violations = listOf("commonMain", "androidMain", "iosMain").flatMap { sourceSet ->
            File(project, "src/$sourceSet/kotlin").walkTopDown().filter {
                it.extension == "kt" && "/ui/theme/" !in it.path
            }.flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    if (literal.containsMatchIn(line)) "${file.path}:${index + 1}" else null
                }
            }.toList()
        }
        assertTrue(violations.isEmpty(), "Sabit renk yerine semantik rol kullanılmalı: ${violations.joinToString()}")
    }
}
