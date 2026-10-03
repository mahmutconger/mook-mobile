package com.mcclabs.mook.data.repository

import com.mcclabs.mook.data.appFirestore
import com.mcclabs.mook.domain.moderation.ModerationGate
import com.mcclabs.mook.domain.repository.ModerationRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Gereksinim 1.13: [ModerationRepository]'nin gerçek uygulaması.
 *
 * Oturum açık olan kullanıcının Firestore'daki `users/{uid}` dokümanını CANLI (gerçek zamanlı
 * dinleyici — `.snapshots`, tek seferlik bir okuma DEĞİL) izler; `isBanned` alanı `true` olduğu
 * ANDA, uygulama açıkken bile, bu tespit edilir — kullanıcının uygulamayı yeniden başlatmasını
 * beklemez.
 *
 * Bu sınıf tek başına (`singleOf`) yaşar ve kendi ömrünü (`SupervisorJob`) kendi yönetir; bir
 * ViewModel'e veya Compose yaşam döngüsüne bağlı DEĞİLDİR — çünkü ban tespiti, kullanıcı hangi
 * ekranda olursa olsun çalışmaya devam etmelidir.
 *
 * [ModerationGate]'i de burada, TEK yerden günceller: böylece [isBanned] (UI için) ve
 * [ModerationGate.isBanned] (ağ katmanı interceptor'ı için) HER ZAMAN birbiriyle senkron kalır.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ModerationRepositoryImpl : ModerationRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutableIsBanned = MutableStateFlow(false)
    override val isBanned: StateFlow<Boolean> = mutableIsBanned.asStateFlow()

    // `uid == null` (çıkış) geçişinde SIFIRLANMAMASI gereken, en son bilinen ban durumu —
    // bkz. init{} içindeki KDoc.
    private var latchedBanned = false

    init {
        scope.launch {
            Firebase.auth.authStateChanged
                .map { it?.uid }
                .distinctUntilChanged()
                .flatMapLatest { uid ->
                    if (uid == null) {
                        // KRİTİK: `UserModerationUseCase.enforceBan()` bu geçişi (zorla çıkış)
                        // BİZZAT tetikler. `false` yayınlarsak, "Hesap Askıya Alındı" ekranı
                        // çıkıştan hemen SONRA aniden kaybolup Login ekranına döner — bu da
                        // "kalıcı ekran" gereksinimini (1.13) bozar. Bu yüzden çıkışta son
                        // bilinen değeri KORURUZ; kullanıcı (aynı ya da başka bir hesapla)
                        // yeniden giriş yaptığında aşağıdaki `else` dalı zaten TAZE bir canlı
                        // okuma yapıp gerçek durumu yeniden değerlendirecektir.
                        flowOf(latchedBanned)
                    } else {
                        appFirestore.collection("users").document(uid).snapshots
                            .map { snapshot -> runCatching { snapshot.get<Boolean?>("isBanned") }.getOrNull() ?: false }
                    }
                }
                // Gereksinim 1.2 ile aynı güvenli-taraf ilkesi: bir dinleyici hatasında
                // (ör. geçici ağ sorunu) kullanıcı ASLA yanlışlıkla "yasaklı" gösterilmemeli —
                // önceki latch değeri değil, güvenli `false` döner (hata YENİ bir uid'in İLK
                // okumasında da oluşabilir, bu durumda latch zaten `false`'tur).
                .catch { emit(false) }
                .collect { banned ->
                    latchedBanned = banned
                    mutableIsBanned.value = banned
                    ModerationGate.setBanned(banned)
                }
        }
    }
}
