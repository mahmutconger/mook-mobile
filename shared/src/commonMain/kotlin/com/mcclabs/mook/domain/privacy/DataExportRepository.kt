package com.mcclabs.mook.domain.privacy

/**
 * Gereksinim 5 (Faz 6, KVKK Madde 11 -- Veri Taşınabilirliği): sunucudaki `exportUserData`
 * Cloud Function'ını TETİKLEYEN tek nokta.
 *
 * Bu depo BİLEREK "ateşle ve unut" (fire-and-forget) şeklindedir: dışa aktarma işleminin
 * KENDİSİ (tüm koleksiyonların okunması, bir JSON dosyasının derlenmesi, Cloud Storage'a
 * yüklenmesi, imzalı bir indirme bağlantısının e-postayla gönderilmesi) sunucu tarafında,
 * ASENKRON olarak tamamlanır -- istemci yalnızca isteğin KABUL EDİLDİĞİNİ öğrenir, dosyanın
 * KENDİSİNİ asla görmez/taşımaz (bkz. görev tanımı: "sends a download link via email").
 */
interface DataExportRepository {
    /**
     * Dışa aktarma isteğini sunucuya iletir.
     *
     * @return Başarıda `Result.success(Unit)` (istek KABUL EDİLDİ, e-posta AYRI bir
     *   arka plan adımında gönderilecek); aksi halde açıklayıcı bir [Throwable] taşıyan
     *   `Result.failure`.
     */
    suspend fun requestExport(): Result<Unit>
}
