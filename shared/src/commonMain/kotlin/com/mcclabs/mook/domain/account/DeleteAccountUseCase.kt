package com.mcclabs.mook.domain.account

import com.mcclabs.mook.domain.model.AuthResult
import com.mcclabs.mook.domain.repository.AuthRepository

/**
 * Hesap silme iş kuralını tek bir yerde toplayan use case.
 *
 * Sunum katmanını (ViewModel) veri kaynağı ayrıntılarından yalıtır: ViewModel yalnızca
 * bu use case'i çağırır, silmenin arkasında bir Cloud Function mı yoksa başka bir kaynak
 * mı olduğunu bilmez. Böylece Tek Sorumluluk ve Bağımlılığın Tersine Çevrilmesi (SOLID)
 * ilkelerine uyulur ve iş kuralı bağımsız olarak test edilebilir.
 */
class DeleteAccountUseCase(
    private val authRepository: AuthRepository,
) {
    /**
     * Hesabı ve tüm ilişkili veriyi kalıcı olarak siler.
     *
     * @return Başarıda [AuthResult.Success] (`Unit`), aksi halde [AuthResult.Error].
     */
    suspend operator fun invoke(): AuthResult<Unit> = authRepository.deleteAccount()
}
