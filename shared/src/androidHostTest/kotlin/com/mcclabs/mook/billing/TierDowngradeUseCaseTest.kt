package com.mcclabs.mook.billing

import com.mcclabs.mook.domain.billing.TierDowngradeResult
import com.mcclabs.mook.domain.billing.TierDowngradeUseCase
import com.mcclabs.mook.domain.model.RoomUsage
import com.mcclabs.mook.domain.repository.RoomSlotRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Gereksinim 1.7: bir kademe düşüşü sonrası fazla oda slotlarının çözülmesi.
 *
 * [TierDowngradeUseCase] bir güvenlik sınırı değil, saf bir ürün kararıdır (sunucu
 * `switchRoom` callable'ında yeni oda açılmasını zaten reddeder) — bu yüzden bu
 * testler [RoomSlotRepository]'yi MockK ile sahteler ve yalnızca "hangi odalar
 * kapatılmalı, ne zaman" kararına odaklanır.
 */
@DisplayName("TierDowngradeUseCase")
class TierDowngradeUseCaseTest {

    private val repository = mockk<RoomSlotRepository>()
    private val useCase = TierDowngradeUseCase(repository)

    private fun room(code: String, lastActiveAtMillis: Long) = RoomUsage(code, lastActiveAtMillis)

    @Nested
    @DisplayName("Eylem gerekmeyen durumlar")
    inner class NoActionNeeded {

        @Test
        fun `sinirsiz kademede (allowedSlots null) hicbir zaman eylem gerekmez`() = runTest {
            val result = useCase(allowedSlots = null)

            assertEquals(TierDowngradeResult.NoActionNeeded, result)
            // Sınırsız kademe için açık odaları okumaya bile gerek yok.
            confirmVerified(repository)
        }

        @Test
        fun `acik oda sayisi sinira esitse eylem gerekmez`() = runTest {
            coEvery { repository.openRooms() } returns listOf(
                room("TR", 1_000), room("EN-US", 2_000), room("FR", 3_000),
            )

            val result = useCase(allowedSlots = 3)

            assertEquals(TierDowngradeResult.NoActionNeeded, result)
            coVerify(exactly = 0) { repository.closeRooms(any()) }
        }

        @Test
        fun `acik oda sayisi sinirin altindaysa eylem gerekmez`() = runTest {
            coEvery { repository.openRooms() } returns listOf(room("TR", 1_000))

            assertEquals(TierDowngradeResult.NoActionNeeded, useCase(allowedSlots = 5))
        }
    }

    @Nested
    @DisplayName("Kullanıcıya sorma (varsayılan davranış)")
    inner class RoomSelectionRequired {

        @Test
        fun `acik oda sayisi siniri asarsa kullanicidan secim istenir ve hicbir oda kapatilmaz`() = runTest {
            val openRooms = listOf(
                room("TR", 5_000), room("EN-US", 3_000), room("FR", 1_000), room("DE", 4_000),
            )
            coEvery { repository.openRooms() } returns openRooms

            val result = useCase(allowedSlots = 2, autoCloseIfOverLimit = false)

            val prompt = result as TierDowngradeResult.RoomSelectionRequired
            assertEquals(openRooms, prompt.openRooms)
            assertEquals(2, prompt.allowedSlots)
            coVerify(exactly = 0) { repository.closeRooms(any()) }
        }
    }

    @Nested
    @DisplayName("Otomatik kapatma (kullanıcı yok saydığında)")
    inner class AutoClose {

        @Test
        fun `en eski kullanilan odalar kapatilir, en son kullanilanlar acik kalir`() = runTest {
            // Premium'da (sınırsız) açılmış 4 oda, Economy'ye (3 slot) düşünce 1
            // fazlalık oluşuyor — en eski kullanılan "FR" (1_000) kapatılmalı.
            coEvery { repository.openRooms() } returns listOf(
                room("TR", 5_000), room("EN-US", 3_000), room("FR", 1_000), room("DE", 4_000),
            )
            coEvery { repository.closeRooms(any()) } just runs

            val result = useCase(allowedSlots = 3, autoCloseIfOverLimit = true)

            assertEquals(TierDowngradeResult.RoomsAutoClosed(listOf("FR")), result)
            coVerify(exactly = 1) { repository.closeRooms(listOf("FR")) }
        }

        @Test
        fun `birden fazla fazla oda varsa en eski kullanilan N tanesi kapatilir`() = runTest {
            // Premium'dan Free'ye (1 slot) düşüş — 4 açık odadan 3'ü kapanmalı, yalnızca
            // en son kullanılan "DE" (4_000) açık kalmalı.
            coEvery { repository.openRooms() } returns listOf(
                room("TR", 2_000), room("EN-US", 3_000), room("FR", 1_000), room("DE", 4_000),
            )
            coEvery { repository.closeRooms(any()) } just runs

            val result = useCase(allowedSlots = 1, autoCloseIfOverLimit = true)

            val closed = (result as TierDowngradeResult.RoomsAutoClosed).closedCodes
            assertEquals(setOf("FR", "TR", "EN-US"), closed.toSet())
            assertEquals(3, closed.size)
            coVerify(exactly = 1) { repository.closeRooms(match { it.toSet() == setOf("FR", "TR", "EN-US") }) }
        }

        @Test
        fun `sinirin altindaysa otomatik modda bile hicbir sey kapatilmaz`() = runTest {
            coEvery { repository.openRooms() } returns listOf(room("TR", 1_000))

            val result = useCase(allowedSlots = 5, autoCloseIfOverLimit = true)

            assertEquals(TierDowngradeResult.NoActionNeeded, result)
            coVerify(exactly = 0) { repository.closeRooms(any()) }
        }
    }

    @Nested
    @DisplayName("Eşit zamanlı odalar (uç durum)")
    inner class TiedTimestamps {

        @Test
        fun `esit son-kullanim zamanli odalarda bile toplam kapatilan sayi dogru olur`() = runTest {
            // Üç oda da hiç aktifleştirilmemiş gibi (ör. eski, roomLastActiveAt'ten önceki
            // veri) — hangi ikisinin kapandığı belirsiz olabilir ama TOPLAM sayı kesin
            // olmalı: sıralama kararsız olsa da `roomsToAutoClose` her zaman tam olarak
            // `openRooms.size - allowedSlots` kadar oda kapatır.
            coEvery { repository.openRooms() } returns listOf(room("A", 1_000), room("B", 1_000), room("C", 1_000))
            coEvery { repository.closeRooms(any()) } just runs

            val result = useCase(allowedSlots = 1, autoCloseIfOverLimit = true) as TierDowngradeResult.RoomsAutoClosed

            assertEquals(2, result.closedCodes.size)
            assertTrue(result.closedCodes.toSet().all { it in setOf("A", "B", "C") })
        }
    }
}
