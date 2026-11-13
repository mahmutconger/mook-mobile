package com.mcclabs.mook.legal

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertTrue
import org.w3c.dom.Element

/** Yasal bağlantı etiketleri her dil klasöründe bulunmalı (İngilizceye sessiz düşüş olmasın). */
class LegalStringsCompletenessTest {
    private val requiredKeys = setOf("legal_privacy_policy", "legal_terms_of_use", "legal_child_safety")

    @Test fun everyValuesFolderHasTheLegalKeys() {
        val shared = listOf(File("."), File("shared")).first { File(it, "src/commonMain/composeResources").isDirectory }
        val folders = File(shared, "src/commonMain/composeResources").listFiles { f -> f.isDirectory && f.name.startsWith("values") }.orEmpty()
        assertTrue(folders.size >= 39, "Beklenenden az dil klasörü: ${folders.size}")

        val missing = folders.flatMap { folder ->
            val file = File(folder, "strings.xml")
            val values = if (file.isFile) namedValues(file) else emptyMap()
            requiredKeys.filter { values[it].isNullOrBlank() }.map { "${folder.name}#$it" }
        }
        assertTrue(missing.isEmpty(), "Eksik yasal metin anahtarları: ${missing.joinToString()}")
    }

    private fun namedValues(file: File): Map<String, String> {
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement.getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) as Element }.associate { it.getAttribute("name") to it.textContent }
    }
}
