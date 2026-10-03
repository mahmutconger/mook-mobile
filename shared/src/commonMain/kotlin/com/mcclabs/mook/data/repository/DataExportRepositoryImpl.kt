package com.mcclabs.mook.data.repository

import com.mcclabs.mook.data.appHttpsCallable
import com.mcclabs.mook.domain.privacy.DataExportRepository

/**
 * [DataExportRepository]'nin gerçek implementasyonu -- bkz. arayüz KDoc'u.
 *
 * `exportUserData` "highly sensitive" bir işlem OLARAK sınıflandırılmaz (Gereksinim 2'nin
 * tek kullanımlık App Check jetonu gerektirdiği `admobSsv`/"purchase validation"dan FARKLI
 * olarak burada gizli bir üçüncü taraf API anahtarı TETİKLENMEZ) -- bu yüzden [appHttpsCallable]
 * (standart App Check jetonu + yasaklı kullanıcı engeli) ile TEK global geçiş noktasından
 * çağrılması yeterlidir; `LimitedUseAppCheckCallableInvoker` baypası GEREKMEZ.
 */
class DataExportRepositoryImpl : DataExportRepository {
    override suspend fun requestExport(): Result<Unit> = runCatching {
        appHttpsCallable("exportUserData").invoke()
        Unit
    }
}
