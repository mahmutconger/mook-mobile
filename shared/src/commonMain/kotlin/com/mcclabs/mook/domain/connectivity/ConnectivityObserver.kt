package com.mcclabs.mook.domain.connectivity

import kotlinx.coroutines.flow.StateFlow

/**
 * Cihazın ağ bağlantısı olup olmadığını gözlemleyen tek nokta.
 *
 * Gereksinim 1.2 kapsamında `ProfileVisit`, oda değişimi, ödüllü reklamlar ve paywall gibi
 * ağ gerektiren eylemler, bu akışı kontrol ederek bağlantı yokken kullanıcıyı beklemeye ya da
 * belirsiz bir hataya sürüklemek yerine anında yerelleştirilmiş bir hata gösterir.
 *
 * ÖNEMLİ: [isOnline] yalnızca cihazın "internete gidebilecek doğrulanmış bir ağ arayüzüne"
 * sahip olduğunu bildirir; belirli bir sunucuya (Firestore, Cloud Functions, AdMob, RevenueCat)
 * gerçekten ulaşılabildiğini garanti etmez. Bu yüzden `true` olsa dahi asıl çağrı yine de
 * başarısız olabilir — çağıranlar gerçek çağrının sonucunu esas almaya devam etmeli, bu akışı
 * yalnızca "denemeye bile değmez" durumunu ucuza ve anında tespit etmek için kullanmalıdır.
 */
interface ConnectivityObserver {
    /** Son bilinen bağlantı durumu; toplanmaya başlar başlamaz mevcut durumla yayınlanır. */
    val isOnline: StateFlow<Boolean>
}
