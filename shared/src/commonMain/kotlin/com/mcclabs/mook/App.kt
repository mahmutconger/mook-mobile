package com.mcclabs.mook

import com.mcclabs.mook.domain.billing.PlanCatalogRepository
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.delay
import com.mcclabs.mook.domain.billing.SubscriptionChangeTracker
import com.mcclabs.mook.domain.billing.AdConfigRepository
import com.mcclabs.mook.feature.consent.PrivacyConsentHost
import androidx.compose.runtime.Composable
import coil3.EventListener
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.NullRequestDataException
import coil3.request.crossfade
import com.mcclabs.mook.di.appModule
import com.mcclabs.mook.navigation.AppNavGraph
import com.mcclabs.mook.ui.theme.MookTheme
import com.mcclabs.mook.util.Log
import org.koin.compose.KoinApplication

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import com.mcclabs.mook.domain.account.AccountMergeResult
import com.mcclabs.mook.domain.account.AccountMergeUseCase
import com.mcclabs.mook.domain.billing.SubscriptionRepository
import com.mcclabs.mook.domain.moderation.UserModerationUseCase
import com.mcclabs.mook.domain.model.UpdateState
import com.mcclabs.mook.domain.update.ForceUpdateUseCase
import com.mcclabs.mook.feature.update.ForceUpdateScreen
import com.mcclabs.mook.domain.analytics.AnalyticsRepository
import com.mcclabs.mook.domain.repository.PushTokenRepository
import com.mcclabs.mook.domain.repository.MookProfileRepository
import com.mcclabs.mook.domain.repository.UserTimeZoneRepository
import com.mcclabs.mook.domain.repository.SettingsRepository
import com.mcclabs.mook.feature.moderation.AccountSuspendedScreen
import com.mcclabs.mook.ui.components.AccountConflictDialog
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.mcclabs.mook.util.applyAppLocale
import org.koin.compose.koinInject

@Composable
fun App(onDarkModeChange: (Boolean) -> Unit = {}) {
    // Coil only auto-registers a network fetcher on JVM, so profile photos served over
    // https would not load on iOS unless the fetcher is added explicitly here.
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory()) }
            .crossfade(true)
            .build()
    }

    KoinApplication(application = {
        modules(appModule)
    }) {
        val settingsRepository = koinInject<SettingsRepository>()
        val isDark by settingsRepository.observeIsDarkMode().collectAsState(initial = false)

        LaunchedEffect(isDark) {
            onDarkModeChange(isDark)
        }

        // Prime the stored language so it applies on startup, not just after the user
        // opens Settings.
        LaunchedEffect(Unit) {
            runCatching { settingsRepository.getAppLanguage() }
        }

        // Reklam ayarları (Remote Config): açılışta ve uygulama açık kaldığı sürece periyodik
        // olarak yenilenir — reklam kill-switch'i çalışan oturumlara da en geç bu aralıkta ulaşır.
        val adConfigRepository = koinInject<AdConfigRepository>()
        LaunchedEffect(Unit) {
            while (true) {
                adConfigRepository.refresh()
                delay(AD_CONFIG_REFRESH_INTERVAL)
            }
        }

        // `subscription_changed` analitiği: kademe geçişleri uygulama ömrü boyunca izlenir.
        val subscriptionChangeTracker = koinInject<SubscriptionChangeTracker>()
        LaunchedEffect(Unit) {
            subscriptionChangeTracker.run()
        }

        // Record this device's push token. Keyed on the signed-in uid rather than Unit:
        // on a fresh install App composes before anyone has logged in, and a
        // fire-once effect would register nothing and never retry. Tokens also rotate
        // (reinstall, restore, cache clear), so re-running per session is correct —
        // the write is an arrayUnion, so re-registering the same token costs nothing.
        // Without this the server has no address to push a new-message notification to.
        val pushTokenRepository = koinInject<PushTokenRepository>()
        val signedInUid by Firebase.auth.authStateChanged
            .map { it?.uid }
            .collectAsState(initial = null)
        LaunchedEffect(signedInUid) {
            if (signedInUid != null) pushTokenRepository.registerCurrentDevice()
        }

        // Giriş backend akışı: `isMookActive` yalnızca sunucuda (activateMookProfile) açılır.
        // Giriş, kayıt, SSO ve uygulamanın yeniden açılışı dahil HER oturum bu noktadan geçtiği
        // için tek bir çağrı yeri yeterlidir; çağrı idempotenttir, başarısız olursa bir sonraki
        // açılışta yeniden denenir.
        val mookProfileRepository = koinInject<MookProfileRepository>()
        LaunchedEffect(signedInUid) {
            if (signedInUid != null) mookProfileRepository.activate()
        }

        // Kota sıfırlamaları sunucuda kullanıcının KAYITLI saat dilimine göre yapılır
        // (`users/{uid}.timeZone`). Uygulama açılışında ve her girişte cihazın güncel dilimi
        // gönderilir; dilim değişmemişse sunucu hiçbir şey yazmaz (bekleme süresi sıfırlanmaz).
        // Plan sınırları (`config/plans`) açılışta ve her girişte sunucudan tazelenir; okuma
        // yalnızca oturum açmış kullanıcıya izinlidir (Firestore kuralları).
        val planCatalogRepository = koinInject<PlanCatalogRepository>()
        LaunchedEffect(signedInUid) {
            if (signedInUid != null) planCatalogRepository.refresh()
        }

        val userTimeZoneRepository = koinInject<UserTimeZoneRepository>()
        LaunchedEffect(signedInUid) {
            if (signedInUid != null) userTimeZoneRepository.syncDeviceTimeZone()
        }

        // Gereksinim 5 (Faz 6, KVKK Madde 11): bkz. [AnalyticsRepository.identifyUser]
        // KDoc'u -- GA4 Kullanıcı Silme isteğinin eşleşebileceği kimlik BURADA kurulur.
        val analyticsRepository = koinInject<AnalyticsRepository>()
        LaunchedEffect(signedInUid) {
            val uid = signedInUid ?: return@LaunchedEffect
            analyticsRepository.identifyUser(uid)
        }

        // Gereksinim 1.9: RevenueCat kimlik BAĞLAMA (logIn) burada, TEK bir global noktada
        // yapılır — `MookApplication`'daki eski koşulsuz dinleyicinin yerini alır. Yeni bir
        // UID her göründüğünde (yeni giriş/kayıt VEYA soğuk başlangıçta aynı kullanıcının
        // devamı) önce `AccountMergeUseCase.detectConflict()` çalışır: cihazda BAŞKA bir
        // kimliğe ait aktif ücretli bir abonelik varsa `logIn()` HEMEN çağrılmaz, kullanıcıya
        // önce "Hesap Çakışması" diyaloğu gösterilir.
        val accountMergeUseCase = koinInject<AccountMergeUseCase>()
        val subscriptionRepository = koinInject<SubscriptionRepository>()
        val coroutineScope = rememberCoroutineScope()
        var pendingConflict by remember { mutableStateOf<AccountMergeResult.ConflictDetected?>(null) }
        var conflictUserId by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(signedInUid) {
            val uid = signedInUid ?: return@LaunchedEffect
            when (val result = accountMergeUseCase.detectConflict(uid)) {
                is AccountMergeResult.ConflictDetected -> {
                    pendingConflict = result
                    conflictUserId = uid
                }
                AccountMergeResult.NoConflict -> subscriptionRepository.logIn(uid)
            }
        }
        val uriHandler = LocalUriHandler.current
        pendingConflict?.let { conflict ->
            AccountConflictDialog(
                conflict = conflict,
                onTransferConfirmed = {
                    val uid = conflictUserId
                    pendingConflict = null
                    conflictUserId = null
                    if (uid != null) {
                        coroutineScope.launch { accountMergeUseCase.proceedWithTransfer(uid) }
                    }
                },
                onContactSupport = {
                    pendingConflict = null
                    conflictUserId = null
                    uriHandler.openUri("mailto:destek@walktalkk.com")
                },
            )
        }
        val appLanguage by settingsRepository.observeAppLanguage().collectAsState(initial = "en")

        // Override the platform locale before the keyed subtree composes, so every
        // stringResource resolves in the selected language. The key() forces a full
        // recomposition whenever the language changes (Android: live; iOS: next launch).
        applyAppLocale(appLanguage)

        // Gereksinim 1.13: sunucu tarafından yasaklanan bir kullanıcı zorla çıkış yapılır ve
        // uygulama, hangi ekranda olursa olsun, kalıcı "Hesap Askıya Alındı" ekranına döner.
        // `isBanned` `true` olduğunda `AppNavGraph()` compose bile EDİLMEZ — bu, geri
        // tuşu/gezinmeyle bu durumdan kaçılamamasının en güçlü garantisidir.
        val userModerationUseCase = koinInject<UserModerationUseCase>()
        val isBanned by userModerationUseCase.isBanned.collectAsState(initial = false)
        LaunchedEffect(isBanned) {
            if (isBanned) userModerationUseCase.enforceBan()
        }

        // Zorla Güncelleme: `isBanned` kilidiyle AYNI ilke — `min_required_version`in
        // altındaki bir istemci için `AppNavGraph()` HİÇ compose EDİLMEZ. Bu kontrol
        // Firestore'a DEĞİL yalnızca Remote Config'e bağımlı olduğundan `isBanned`'den
        // BAĞIMSIZ, uygulama açılışında en erken anda çalışır — yeni bir Firestore
        // kuralı/alanı bekleyen eski bir istemcinin sunucuyla konuşmaya DEVAM edip veri
        // bozulmasına yol açması bu sayede en baştan engellenir.
        val forceUpdateUseCase = koinInject<ForceUpdateUseCase>()
        val updateState by forceUpdateUseCase.updateState.collectAsState(initial = UpdateState.None)
        LaunchedEffect(Unit) {
            forceUpdateUseCase.evaluate()
        }

        key(appLanguage) {
            MookTheme {
                when {
                    isBanned -> AccountSuspendedScreen()
                    updateState == UpdateState.ForceUpdateRequired -> ForceUpdateScreen(
                        onUpdateNowClicked = {
                            coroutineScope.launch { forceUpdateUseCase.startImmediateUpdate() }
                        },
                    )
                    else -> {
                        AppNavGraph()
                        // KVKK: politika sürümü için karar verilmediyse onay ekranı zorunlu olarak
                        // açılır (Android'de UMP formu çözüldükten SONRA). Analytics ve kişisel
                        // reklamlar karar verilene kadar kapalıdır.
                        PrivacyConsentHost(promptWhenRequired = true)
                    }
                }
            }
        }
    }
}

/** Reklam ayarlarının (Remote Config) yeniden çekilme aralığı. */
private val AD_CONFIG_REFRESH_INTERVAL = 30.minutes
