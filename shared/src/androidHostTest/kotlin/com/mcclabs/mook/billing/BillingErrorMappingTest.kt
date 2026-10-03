package com.mcclabs.mook.billing

import com.mcclabs.mook.data.billing.toBillingError
import com.mcclabs.mook.domain.billing.BillingError
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.PurchasesErrorCode
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Gereksinim 1.11: [PurchasesError.toBillingError]'ın RevenueCat/Play Billing hata
 * kodlarını sağlayıcıdan bağımsız [BillingError]'a doğru eşlediğini doğrular.
 *
 * [PurchasesError] somut (final) bir sınıf olduğundan ve gerçek kurucusunun tam imzası bu
 * ortamda (Gradle derlemesi çalıştırılamadığından) doğrulanamadığından, MockK ile sahte bir
 * örnek üretmek — kurucuyu hiç çağırmadan yalnızca `code`/`message` özelliklerini sahtelemek —
 * gerçek bir kurucu çağrısı tahmin etmekten daha güvenlidir.
 */
@DisplayName("PurchasesError.toBillingError")
class BillingErrorMappingTest {

    private fun errorOf(code: PurchasesErrorCode, message: String = "raw-message"): PurchasesError {
        val error = mockk<PurchasesError>()
        every { error.code } returns code
        every { error.message } returns message
        return error
    }

    @Test
    fun `StoreProblemError BillingUnavailable'a eslenir`() {
        assertEquals(BillingError.BillingUnavailable, errorOf(PurchasesErrorCode.StoreProblemError).toBillingError())
    }

    @Test
    fun `ProductAlreadyPurchasedError ItemAlreadyOwned'a eslenir`() {
        assertEquals(BillingError.ItemAlreadyOwned, errorOf(PurchasesErrorCode.ProductAlreadyPurchasedError).toBillingError())
    }

    @Test
    fun `NetworkError NetworkError'a eslenir`() {
        assertEquals(BillingError.NetworkError, errorOf(PurchasesErrorCode.NetworkError).toBillingError())
    }

    @Test
    fun `OperationAlreadyInProgressError OperationInProgress'e eslenir`() {
        assertEquals(BillingError.OperationInProgress, errorOf(PurchasesErrorCode.OperationAlreadyInProgressError).toBillingError())
    }

    @Test
    fun `ReceiptAlreadyInUseError SubscriptionLinkedToAnotherAccount'a eslenir`() {
        // Gereksinim 2.1: restore, makbuzun zaten baska bir hesaba bagli oldugunu tespit ettiginde.
        assertEquals(
            BillingError.SubscriptionLinkedToAnotherAccount,
            errorOf(PurchasesErrorCode.ReceiptAlreadyInUseError).toBillingError(),
        )
    }

    @Test
    fun `eslenmemis bir kod ham mesaji koruyan Unknown'a duser`() {
        val error = errorOf(PurchasesErrorCode.UnknownError, message = "beklenmeyen saglayici hatasi")
        assertEquals(BillingError.Unknown("beklenmeyen saglayici hatasi"), error.toBillingError())
    }
}
