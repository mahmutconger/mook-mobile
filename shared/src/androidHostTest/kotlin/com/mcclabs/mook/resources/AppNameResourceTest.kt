package com.mcclabs.mook.resources

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertTrue
import org.w3c.dom.Element

/**
 * Uygulamanın eski adı ("WalkMatch") kullanıcıya görünen hiçbir metinde kalmamalı.
 * Yalnızca strings.xml değerleri denetlenir; yorumlar ve kod tanımlayıcıları kapsam dışıdır.
 */
class AppNameResourceTest {
    @Test fun noStringValueContainsTheOldAppName() {
        val shared = listOf(File("."), File("shared")).first { File(it, "src/commonMain/composeResources").isDirectory }.canonicalFile
        val roots = listOf(
            File(shared, "src/commonMain/composeResources"),
            File(shared.parentFile, "androidApp/src/main/res"),
        )
        roots.forEach { assertTrue(it.isDirectory, "Kaynak kökü bulunamadı: ${it.path}") }

        val files = roots.flatMap { root ->
            root.listFiles { dir -> dir.isDirectory && dir.name.startsWith("values") }.orEmpty()
                .map { File(it, "strings.xml") }.filter { it.isFile }
        }
        assertTrue(files.size > 2, "strings.xml dosyaları bulunamadı")

        val violations = files.flatMap { file -> stringValues(file).filter { (_, value) -> value.contains("walkmatch", ignoreCase = true) }
            .map { (name, _) -> "${file.parentFile.name}/${file.name}#$name" } }
        assertTrue(violations.isEmpty(), "Eski uygulama adı \"WalkMatch\" hâlâ görünüyor: ${violations.joinToString()}")
    }

    /** <string>, <plurals>/<item> ve <string-array>/<item> değerlerini (ad, metin) olarak döndürür. */
    private fun stringValues(file: File): List<Pair<String, String>> {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
        val children = root.childNodes
        return (0 until children.length).mapNotNull { children.item(it) as? Element }
            .map { it.getAttribute("name") to it.textContent }
    }
}
