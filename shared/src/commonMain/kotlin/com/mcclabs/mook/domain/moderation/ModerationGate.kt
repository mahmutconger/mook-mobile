package com.mcclabs.mook.domain.moderation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Gereksinim 1.13 (Moderasyon & Yasaklamalar): sunucudan gelen `isBanned == true` bilgisinin
 * yansıtıldığı, TÜM Cloud Functions çağrılarının geçtiği tek, global kapı.
 *
 * Bu depoda (repository seviyesinde) paylaşılan tek bir ağ istemcisi/interceptor katmanı
 * hiç yoktu — her repository `Firebase.functions.httpsCallable(...)`'ı doğrudan çağırıyordu.
 * Bu nesne, o boşluğu doldurmak için [com.mcclabs.mook.data.appHttpsCallable] tarafından
 * kullanılan, DI'dan bağımsız (dolayısıyla her yerden erişilebilen) basit bir global anahtardır:
 * [ModerationRepositoryImpl][com.mcclabs.mook.data.repository.ModerationRepositoryImpl]
 * kullanıcının Firestore'daki `isBanned` alanını CANLI izler ve bu alanı günceller; her
 * `appHttpsCallable()` çağrısı istekten ÖNCE burayı kontrol eder ve `true` ise isteği
 * sunucuya HİÇ GÖNDERMEDEN reddeder.
 *
 * NOT (kapsam sınırı): bu kapı yalnızca Cloud Functions callable'larını (uygulamanın durum
 * DEĞİŞTİREN tüm eylemlerinin — kaydırma, mesaj, oda değişimi, vb. — geçtiği tek yüzey) kapsar.
 * Firestore'un kendi gerçek zamanlı DİNLEYİCİLERİ (ör. bu banın kendisinin gözlemlenmesi)
 * bilinçli olarak kapsam DIŞI bırakılmıştır — aksi halde ban durumu hiç gözlemlenemezdi.
 * Doğrudan istemci taraflı Firestore YAZMALARI (ör. `blockedUsers`) için asıl güvenlik sınırı
 * `firestore.rules`'daki sunucu tarafı kurallardır (bkz. `users/{uid}` alan kısıtlamaları).
 */
object ModerationGate {
    private val mutableIsBanned = MutableStateFlow(false)
    val isBanned: StateFlow<Boolean> = mutableIsBanned.asStateFlow()

    fun setBanned(banned: Boolean) {
        mutableIsBanned.value = banned
    }
}

/**
 * [ModerationGate.isBanned] `true` iken bir Cloud Functions çağrısı denendiğinde fırlatılır.
 * İstek AĞA HİÇ ÇIKMADAN burada durdurulduğundan, çağıran taraflar bunu normal bir ağ
 * hatasıymış gibi (ör. genel bir "bir şeyler ters gitti" mesajıyla) ele alabilir — kullanıcı
 * zaten aynı anda [ModerationRepositoryImpl][com.mcclabs.mook.data.repository.ModerationRepositoryImpl]
 * tarafından "Hesap Askıya Alındı" ekranına yönlendirilmiş olacaktır.
 */
class BannedUserException : IllegalStateException("Bu hesap askıya alınmış durumda; istek engellendi.")
