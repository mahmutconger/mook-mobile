package com.mcclabs.mook.account

import com.mcclabs.mook.domain.account.AccountMergeResult
import com.mcclabs.mook.domain.account.AccountMergeUseCase
import com.mcclabs.mook.domain.billing.PlanCatalog
import com.mcclabs.mook.domain.billing.EntitlementState
import com.mcclabs.mook.domain.billing.SubscriptionRepository
import com.mcclabs.mook.domain.billing.Tier
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Gereksinim 1.9: [AccountMergeUseCase]'in, cihazda hâlihazırda tanımlı olan RevenueCat
 * kimliğiyle YENİ giriş yapılan Firebase UID'si arasında doğru şekilde çakışma tespit
 * ettiğini (ya da etmediğini) ve onaylanan aktarımı doğru şekilde uyguladığını doğrular.
 */
@DisplayName("AccountMergeUseCase")
class AccountMergeUseCaseTest {

    private fun repositoryOf(currentId: String?, entitlement: EntitlementState): SubscriptionRepository {
        val repository = mockk<SubscriptionRepository>()
        every { repository.currentIdentifiedUserId() } returns currentId
        every { repository.state } returns MutableStateFlow(entitlement)
        coEvery { repository.logIn(any()) } returns Unit
        return repository
    }

    @Test
    fun `daha once tanimli kimlik yoksa cakisma yoktur`() {
        val repository = repositoryOf(currentId = null, entitlement = EntitlementState(isResolved = false))
        val useCase = AccountMergeUseCase(repository)

        val result = useCase.detectConflict("new-uid")

        assertEquals(AccountMergeResult.NoConflict, result)
    }

    @Test
    fun `soguk baslangicta ayni kimlik devam ederse cakisma yoktur`() {
        val entitlement = EntitlementState(
            tier = Tier.PREMIUM,
            isResolved = true,
            limits = PlanCatalog.BUNDLED.limitsFor(Tier.PREMIUM),
        )
        val repository = repositoryOf(currentId = "same-uid", entitlement = entitlement)
        val useCase = AccountMergeUseCase(repository)

        val result = useCase.detectConflict("same-uid")

        assertEquals(AccountMergeResult.NoConflict, result)
    }

    @Test
    fun `farkli kimlik ama yetkilendirme henuz cozulmemisse cakisma yoktur`() {
        val repository = repositoryOf(currentId = "old-uid", entitlement = EntitlementState(isResolved = false))
        val useCase = AccountMergeUseCase(repository)

        val result = useCase.detectConflict("new-uid")

        assertEquals(AccountMergeResult.NoConflict, result)
    }

    @Test
    fun `farkli kimlik ama aktif kademe FREE ise cakisma yoktur`() {
        val entitlement = EntitlementState(
            tier = Tier.FREE,
            isResolved = true,
            limits = PlanCatalog.BUNDLED.limitsFor(Tier.FREE),
        )
        val repository = repositoryOf(currentId = "old-uid", entitlement = entitlement)
        val useCase = AccountMergeUseCase(repository)

        val result = useCase.detectConflict("new-uid")

        assertEquals(AccountMergeResult.NoConflict, result)
    }

    @Test
    fun `farkli kimlikte aktif ucretli abonelik varsa cakisma tespit edilir`() {
        val entitlement = EntitlementState(
            tier = Tier.STANDARD,
            isResolved = true,
            expiresAtMillis = 1_700_000_000_000L,
            limits = PlanCatalog.BUNDLED.limitsFor(Tier.STANDARD),
        )
        val repository = repositoryOf(currentId = "old-uid", entitlement = entitlement)
        val useCase = AccountMergeUseCase(repository)

        val result = useCase.detectConflict("new-uid")

        assertTrue(result is AccountMergeResult.ConflictDetected)
        val conflict = result as AccountMergeResult.ConflictDetected
        assertEquals(Tier.STANDARD, conflict.activeTier)
        assertEquals(1_700_000_000_000L, conflict.expiresAtMillis)
    }

    @Test
    fun `aktarim onaylaninca repository logIn yeni kimlikle cagrilir`() = runBlocking {
        val entitlement = EntitlementState(
            tier = Tier.PREMIUM,
            isResolved = true,
            limits = PlanCatalog.BUNDLED.limitsFor(Tier.PREMIUM),
        )
        val repository = repositoryOf(currentId = "old-uid", entitlement = entitlement)
        val useCase = AccountMergeUseCase(repository)

        useCase.proceedWithTransfer("new-uid")

        coVerify(exactly = 1) { repository.logIn("new-uid") }
    }
}
