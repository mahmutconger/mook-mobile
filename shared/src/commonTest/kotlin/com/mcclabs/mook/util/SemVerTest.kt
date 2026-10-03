package com.mcclabs.mook.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Zorla Güncelleme mekanizmasının dayandığı [compareSemVer]in saf mantık testleri. */
class SemVerTest {

    @Test
    fun `esit surumler sifir doner`() {
        assertEquals(0, compareSemVer("1.3.4", "1.3.4"))
    }

    @Test
    fun `daha kucuk surum negatif doner`() {
        assertTrue(compareSemVer("1.2.0", "1.3.0") < 0)
    }

    @Test
    fun `daha buyuk surum pozitif doner`() {
        assertTrue(compareSemVer("2.0.0", "1.9.9") > 0)
    }

    @Test
    fun `farkli basamak sayisi esit kabul edilir`() {
        assertEquals(0, compareSemVer("1.4", "1.4.0"))
    }

    @Test
    fun `eksik basamak sifir olarak ele alinir ve kucuk sayilir`() {
        assertTrue(compareSemVer("1.4", "1.4.1") < 0)
    }

    @Test
    fun `sayisal olmayan basamak cokmeden sifir kabul edilir`() {
        assertEquals(0, compareSemVer("1.3.x", "1.3.0"))
    }

    @Test
    fun `force update senaryosu min_required_version altindaki surum tespit edilir`() {
        val currentVersion = "1.3.4"
        val minRequiredVersion = "1.4.0"
        assertTrue(compareSemVer(currentVersion, minRequiredVersion) < 0)
    }
}
