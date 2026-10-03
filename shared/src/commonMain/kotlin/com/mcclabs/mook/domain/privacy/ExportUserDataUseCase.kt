package com.mcclabs.mook.domain.privacy

/** [ExportUserDataUseCase.invoke]'un dönebileceği sonuçlar (Gereksinim 5, KVKK Madde 11). */
sealed interface DataExportRequestResult {
    /** İstek sunucu tarafında KABUL EDİLDİ; e-posta AYRI, asenkron bir adımda gönderilecek. */
    data object Requested : DataExportRequestResult

    /** İstek başarısız oldu; [message] kullanıcıya gösterilecek Türkçe metindir. */
    data class Failed(val message: String) : DataExportRequestResult
}

/**
 * Gereksinim 5 (Faz 6, KVKK Madde 11 -- "İlgili kişinin kişisel verilerinin işlenip
 * işlenmediğini öğrenme" ve veri taşınabilirliği hakkı): [DataExportRepository]'yi tek bir
 * sorumlulukla (SRP) sarmalar -- ham sunucu hatasını `SettingsViewModel`e SIZDIRMAZ,
 * Türkçe, kullanıcıya gösterilebilir bir sonuca çevirir (bkz. `DeleteAccountUseCase` ile
 * AYNI ilke, o sınıfın KDoc'u).
 */
class ExportUserDataUseCase(
    private val dataExportRepository: DataExportRepository,
) {
    suspend operator fun invoke(): DataExportRequestResult =
        dataExportRepository.requestExport().fold(
            onSuccess = { DataExportRequestResult.Requested },
            onFailure = { error ->
                DataExportRequestResult.Failed(
                    error.message ?: "Veri dışa aktarma isteği gönderilemedi.",
                )
            },
        )
}
