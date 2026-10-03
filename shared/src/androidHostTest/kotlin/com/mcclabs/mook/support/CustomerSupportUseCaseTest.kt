package com.mcclabs.mook.support

import com.mcclabs.mook.domain.support.CustomerSupportRepository
import com.mcclabs.mook.domain.support.CustomerSupportUseCase
import com.mcclabs.mook.domain.support.PromotionalEntitlement
import com.mcclabs.mook.domain.support.PromotionalGrantDuration
import com.mcclabs.mook.domain.support.PromotionalGrantResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Gereksinim 7 (Faz 6, Gözlemlenebilirlik & Destek Araçları): [CustomerSupportUseCase]'in
 * [CustomerSupportRepository]'nin ham `Result`ını doğru şekilde gösterilebilir bir
 * [PromotionalGrantResult]e çevirdiğini VE parametreleri repository'ye DEĞİŞTİRMEDEN
 * ilettiğini doğrular.
 */
@DisplayName("CustomerSupportUseCase")
class CustomerSupportUseCaseTest {

    @Test
    fun `basarili bagis Granted doner ve parametreleri degistirmeden iletir`() = runBlocking {
        val repository = mockk<CustomerSupportRepository>()
        coEvery {
            repository.requestPromotionalGrant(
                targetUid = "buggy-user-uid",
                entitlement = PromotionalEntitlement.PREMIUM,
                duration = PromotionalGrantDuration.WEEKLY,
                reason = "crash-on-paywall-ticket-1234",
            )
        } returns Result.success(Unit)
        val useCase = CustomerSupportUseCase(repository)

        val result = useCase(
            targetUid = "buggy-user-uid",
            entitlement = PromotionalEntitlement.PREMIUM,
            duration = PromotionalGrantDuration.WEEKLY,
            reason = "crash-on-paywall-ticket-1234",
        )

        assertEquals(PromotionalGrantResult.Granted, result)
        coVerify(exactly = 1) {
            repository.requestPromotionalGrant(
                "buggy-user-uid",
                PromotionalEntitlement.PREMIUM,
                PromotionalGrantDuration.WEEKLY,
                "crash-on-paywall-ticket-1234",
            )
        }
    }

    @Test
    fun `yetkisiz cagiran sunucu hatasini Turkce Failed mesajina cevirir`() = runBlocking {
        val repository = mockk<CustomerSupportRepository>()
        coEvery {
            repository.requestPromotionalGrant(any(), any(), any(), any())
        } returns Result.failure(Exception("permission-denied"))
        val useCase = CustomerSupportUseCase(repository)

        val result = useCase(
            targetUid = "some-uid",
            entitlement = PromotionalEntitlement.ECONOMY,
            duration = PromotionalGrantDuration.DAILY,
            reason = "test",
        )

        assertTrue(result is PromotionalGrantResult.Failed)
        assertEquals("permission-denied", (result as PromotionalGrantResult.Failed).message)
    }

    @Test
    fun `hata mesaji bos ise varsayilan Turkce metin kullanilir`() = runBlocking {
        val repository = mockk<CustomerSupportRepository>()
        coEvery {
            repository.requestPromotionalGrant(any(), any(), any(), any())
        } returns Result.failure(Exception())
        val useCase = CustomerSupportUseCase(repository)

        val result = useCase(
            targetUid = "some-uid",
            entitlement = PromotionalEntitlement.STANDARD,
            duration = PromotionalGrantDuration.LIFETIME,
            reason = "test",
        )

        assertEquals(
            PromotionalGrantResult.Failed("Promosyonel hak tanımlanamadı."),
            result,
        )
    }
}
