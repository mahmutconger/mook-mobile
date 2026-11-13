# Google Play Veri Güvenliği Formu — Cevap Belgesi (Mook, `com.mcclabs.mook`)

**Hazırlanma tarihi:** 14 Kasım 2026
**Kod tabanı:** `main` @ `0ac961c` (Android 2.1.1, versionCode 15)
**Politika kaynağı:** `~/mook-web/yasal-kaynak/Gizlilik_Politikasi.md` (13 Kasım 2026, yayımlanan TR metin) ve `en/privacy-policy.md`. Bu belgede politika **değiştirilmedi**; çelişkiler Bölüm 3'te listelenir.

Bu belge, Play Console > **Uygulama içeriği > Veri güvenliği** formunu doldururken kullanılacak cevapları ve her cevabın kanıtını içerir. "Kanıt" sütunundaki `dosya:satır` referansları bu depoya, URL'ler resmî belgelere aittir.

## 0. Kaynaklar ve tanımlar

### 0.1 Okunan resmî belgeler

| Kısaltma | Belge | Not |
|---|---|---|
| **[PLAY]** | Play Console Yardım, "Provide information for Google Play's Data safety section" — https://support.google.com/googleplay/android-developer/answer/10787469 | Tanımlar, veri türleri, amaçlar, form sırası |
| **[PLAY-DEL]** | "Understanding Google Play's app account deletion requirements" — https://support.google.com/googleplay/android-developer/answer/13327111 | Hesap silme URL'si şartları |
| **[ADMOB]** | https://developers.google.com/admob/android/privacy/play-data-disclosure | Sayfa yalnızca **25.5.0** sürümünü kapsar; depoda da `play-services-ads:25.5.0` var (`shared/build.gradle.kts:64`) |
| **[FIREBASE]** | https://firebase.google.com/docs/android/play-data-disclosure | Auth, Firestore, Storage, Functions, FCM, Installations, Remote Config, App Check |
| **[GA]** | https://support.google.com/analytics/answer/11582702 | Google Analytics for Firebase (Firebase sayfası buraya yönlendiriyor) |
| **[RC]** | https://www.revenuecat.com/docs/platform-resources/google-platform-resources/google-plays-data-safety | RevenueCat SDK `10.15.1` (`gradle/libs.versions.toml`) |

### 0.2 [PLAY] tanımları (bu belgede uygulanan kurallar)

- **Toplama:** Verinin cihazdan dışarı iletilmesidir. Uygulamadaki SDK'ların gönderdiği veri de sayılır. **Yalnızca cihazda işlenip dışarı gönderilmeyen veri toplanmış sayılmaz.** Takma adlı (pseudonim) veri de beyan edilir.
- **Paylaşma:** Toplanan verinin **üçüncü tarafa** aktarılmasıdır. İstisnalar: (a) **hizmet sağlayıcı**, yani veriyi geliştirici adına ve onun talimatıyla işleyen taraf; (b) yasal amaçlar; (c) kullanıcının başlattığı ve paylaşımı makul olarak beklediği eylem ya da belirgin açıklama ve onay; (d) tamamen anonim veri. [PLAY] SSS'sine göre birden çok müşteri için reklam profili oluşturan bir SDK hizmet sağlayıcı **değildir**.
- **Geçici (ephemeral) işleme:** Veri yalnızca bellekte, isteği gerçek zamanlı karşılamak için gereken süre kadar tutulur. **Reklam veya kullanıcı profili oluşturmak hiçbir zaman geçici sayılmaz.** Geçici veri de formda beyan edilir. Geçici olduğu işaretlenirse mağaza sayfasında gösterilmeyebilir.
- **İsteğe bağlı / Zorunlu:** "İsteğe bağlı" yalnızca **bölge ve cihazdan bağımsız olarak tüm kullanıcılar** reddedebiliyorsa seçilebilir. Uygulamanın herhangi bir sürümü veriyi zorunlu tutuyorsa "Zorunlu" seçilir. Form paket adı başına küreseldir.
- **IP'den çıkarılan konum:** IP veya APN'den çıkarılan yaklaşık konum, "Yaklaşık konum" olarak beyan edilmelidir.
- **SDK sorumluluğu:** SDK'ların topladığı ve paylaştığı veriler geliştiricinin formunda yer almalıdır.

### 0.3 Kod envanteri özeti

**Birleşik manifest izinleri** (`./gradlew :androidApp:processReleaseManifest` ile 14.11.2026'da yeniden üretildi, `androidApp/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml`):

- **Uygulamanın kendi izinleri:** `INTERNET`, `com.google.android.gms.permission.AD_ID`, `POST_NOTIFICATIONS`.
- **Kütüphanelerden gelen izinler:** `ACCESS_NETWORK_STATE`, `WAKE_LOCK`, `c2dm.permission.RECEIVE`, `ACCESS_ADSERVICES_AD_ID`, `ACCESS_ADSERVICES_ATTRIBUTION`, `ACCESS_ADSERVICES_TOPICS`, `com.android.vending.BILLING`, `READ_GSERVICES`, `BIND_GET_INSTALL_REFERRER_SERVICE`, `FOREGROUND_SERVICE` ve `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`.
- **Bulunmayan izinler:** Konum, kamera, mikrofon, kişiler, takvim, SMS, telefon ve depolama izinleri yok. `READ_EXTERNAL_STORAGE` izni `tools:node="remove"` ile kaldırılmış (`androidApp/src/main/AndroidManifest.xml:23-25`).

**SDK'lar:**

| Kaynak | SDK'lar |
|---|---|
| `shared/build.gradle.kts:54-73`, `androidApp/build.gradle.kts:26-33` | Firebase BoM 33.1.2: Auth, Firestore, Storage, Functions, Remote Config, Analytics, Messaging, App Check (Play Integrity) |
| aynı | Google Mobile Ads `25.5.0`, UMP `4.0.0`, Play App Update `2.1.0`, RevenueCat `10.15.1` |
| aynı | Coil ve Ktor (yalnızca görsel indirme) |

Crashlytics, Sentry veya başka bir çökme raporlama SDK'sı **yok**.

**Sunucunun üçüncü taraflara gönderdiği veriler (Cloud Functions):**

| Alıcı | Gönderilen veri | Kanıt |
|---|---|---|
| DeepL | Mesaj metni ve dil kodları | `functions/src/index.ts:338-361` (`sendMessage`), `:110-176` (`translateText`, WalkTalk demosu) |
| FCM | Gönderen adı ve mesaj metni ya da çevirisi | `functions/src/index.ts:413-437`; abonelik bildirimleri `functions/src/push.ts:30-46` |
| RevenueCat REST | UID | `functions/src/revenuecatSync.ts:60`, `deleteAccount.ts:156`, `customerSupport.ts:94` |
| Google Analytics Admin API | Kullanıcı silme isteği (UID) | `functions/src/deleteAccount.ts:204` |
| Gmail SMTP (Trigger Email uzantısı) | E-posta adresi ve 7 gün geçerli indirme bağlantısı | `functions/src/exportUserData.ts:8,99-106` |
| AdMob SSV doğrulaması | Veri gönderilmez; yalnızca anahtar indirilir | `functions/src/admobRewardedSsv.ts:32,100` |

---

## 1. Genel sorular

| Soru (Play Console) | Cevap | Gerekçe ve kanıt |
|---|---|---|
| **Uygulamanız, zorunlu kullanıcı veri türlerinden herhangi birini topluyor veya paylaşıyor mu?** | **Evet** | Hesap, profil, mesaj ve etkileşim verileri Firebase'e yazılıyor (`shared/.../data/repository/AuthRepositoryImpl.kt:98-113,177-200`). AdMob, IP adresi, reklam kimlikleri, etkileşim ve tanılama verilerini Google ile paylaşıyor [ADMOB]. |
| **Uygulamanızın topladığı tüm kullanıcı verileri aktarım sırasında şifreleniyor mu?** | **Evet** | `cleartextTrafficPermitted="false"` (`androidApp/src/main/res/xml/network_security_config.xml:10`). Firebase "HTTPS ile şifreler" [FIREBASE]. AdMob verileri TLS ile şifrelenir [ADMOB]. GA verileri TLS ile şifrelenir [GA]. RevenueCat aktarımda şifreler [RC]. DeepL ve RevenueCat REST çağrıları `https://` ile yapılıyor (`functions/src/index.ts:141`, `revenuecatSync.ts:60`). |
| **Hesap oluşturma yöntemleri** | **Kullanıcı adı ve şifre** (e-posta + şifre) | `Firebase.auth.createUserWithEmailAndPassword` (`AuthRepositoryImpl.kt:98`). OAuth veya telefonla giriş yok. Mook'un WalkTalk için SSO sağlaması (`functions/src/index.ts:96`) bir hesap oluşturma yöntemi değildir; Mook burada kimlik sağlayıcısıdır. |
| **Hesap silme URL'si** | `https://mook-sso.web.app/account-deletion.html` | Sayfa depoda: `hosting/mook-sso/account-deletion.html`. Uygulama içi silme: Ayarlar > "Hesabı Kalıcı Olarak Sil" → `deleteAccount` (`AuthRepositoryImpl.kt:239`, `functions/src/deleteAccount.ts:240`). [PLAY-DEL], tam uygulama içi silme olsa bile web bağlantısını şart koşar. ⚠ Sayfadaki geliştirici adı ve e-posta için Bölüm 3, Ç-2'ye bakın. |
| **Kullanıcılar hesaplarını silmeden verilerinin bir kısmını veya tamamını silmeyi talep edebilir mi?** | **Evet** (URL alanı **boş bırakılmalı**; bkz. K-7) | Uygulama içinde kullanıcı kendi mesajını silebilir; metin ve çeviri temizlenir (`functions/src/index.ts:489-525`). Profil fotoğraflarını ve bilgilerini düzenleyebilir (`feature/profile/edit/EditProfileViewModel.kt:242`). Web sayfası şu an yalnızca hesap silmeyi anlatıyor. |
| **Kullanıcıların veri silme talebinde bulunabileceği bir yöntem sunuyor musunuz?** | **Evet** | Uygulama içi silme ve web sayfasındaki e-posta yolu. Silme planı tüm koleksiyonları kapsıyor (`functions/src/deletionSchema.ts:61-115`). Analytics için GA'ya silme isteği gönderiliyor (`deleteAccount.ts:204`). RevenueCat abone kaydı siliniyor (`deleteAccount.ts:156`). |
| **Bağımsız güvenlik incelemesi** | **Hayır** | MASA veya OWASP MASVS denetimine dair depoda ya da politikada kanıt yok. Bu rozet isteğe bağlıdır [PLAY]. |
| **Aileler / hedef kitle notu** | Veri güvenliği formunda ayrı bir soru değildir. Uygulama **18+**'dır, Aileler programında **yer almamalıdır**. "Play Aileler Politikası'na uyma taahhüdü" rozeti **işaretlenmemelidir**. | Kayıtta yaş kontrolü yapılıyor (politika §2). AdMob TFUA=false ve içerik derecelendirmesi en fazla "T" (`shared/src/androidMain/.../ads/AdMobConsentManager.kt:199-207`). Hedef kitle ve içerik formunda yalnızca **18 ve üzeri** seçilmeli. |
| **UPI ödeme rozeti** | İşaretlenmez | Uygulamada UPI yok. |

---

## 2. Veri türleri tablosu

**Kısaltmalar:**

- **Amaçlar:** AF = App functionality, AN = Analytics, DC = Developer communications, AD = Advertising or marketing, FP = Fraud prevention, security, and compliance, PE = Personalization, AM = Account management.
- **Sütunlar:** "Geçici" = ephemeral, Z = Zorunlu, İB = İsteğe bağlı.
- **Kanıt yolları:** `S/` = `shared/src/commonMain/kotlin/com/mcclabs/mook/`, `SA/` = `shared/src/androidMain/kotlin/com/mcclabs/mook/`, `F/` = `functions/src/`.

### 2.1 Konum

| Veri türü | Toplanıyor mu | Paylaşılıyor mu | Geçici mi | Z / İB | Amaçlar | Kaynak | Kanıt | Politika bölümü |
|---|---|---|---|---|---|---|---|---|
| **Yaklaşık konum** | **Evet** | **Evet** | Hayır | **Z** | AD, AN, FP | AdMob SDK (IP adresi, toplanır ve paylaşılır); GA (maskelenmiş IP'den kaba konum) | [ADMOB] "IP address … may be used to estimate the general location"; [GA] maskelenmiş IP'den kaba konum çıkarılır; [PLAY] IP'den çıkarılan konum beyan edilmelidir. **Kodda konum API'si yok** (yalnızca SDK kanıtı). | §7.2, §8.2 (⚠ §4.4 ile çelişki, Ç-5) |
| **Hassas konum** | **Hayır** | Hayır | — | — | — | — | Birleşik manifestte `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` yok. Kodda `LocationManager` veya FusedLocation yok. Saat dilimi kimliği konum değildir (`F/monetization.ts:763`). | §3.1, §4.4 |

**Karar (yaklaşık konum):** Uygulama konuma kendisi erişmez. Ancak AdMob, ücretsiz plandaki **her** kullanıcı için IP adresini toplayıp paylaşır ve Google bunun genel konum tahmini için kullanılabileceğini açıkça söyler. [PLAY], IP'den çıkarılan yaklaşık konumun beyan edilmesini şart koşar. Bu yüzden "Evet / Evet" seçilir. Ücretsiz planda reklamı reddetmenin bir yolu olmadığı için (KVKK kararında reddeden kullanıcı da kişiselleştirilmemiş reklam görür: `S/domain/consent/ConsentRepository.kt:105`) seçim **Zorunlu** olmalıdır. Daha az muhafazakâr seçenek için K-1'e bakın.

### 2.2 Kişisel bilgiler

| Veri türü | Toplanıyor mu | Paylaşılıyor mu | Geçici mi | Z / İB | Amaçlar | Kaynak | Kanıt | Politika bölümü |
|---|---|---|---|---|---|---|---|---|
| **Ad** | **Evet** (görünen ad) | Hayır | Hayır | **Z** | AF, AM | Kendi kodumuz → Firestore | `S/data/repository/AuthRepositoryImpl.kt:104` (`displayName`). Bildirim başlığında FCM ile alıcıya gider (`F/index.ts:419-427`). Bu, Google'ın hizmet sağlayıcı olarak yaptığı iletimdir ve kullanıcının başlattığı mesaj gönderimidir; paylaşma sayılmaz. | §4.1, §6.1 |
| **E-posta adresi** | **Evet** | Hayır | Hayır | **Z** | AF, AM | Firebase Auth + Firestore `users/{uid}.email`; Gmail SMTP (dışa aktarma bağlantısı) | `AuthRepositoryImpl.kt:98,103`; `F/exportUserData.ts:99-106`. Gmail burada hizmet sağlayıcıdır. RevenueCat'e e-posta gönderilmez (kodda `setEmail` yok). | §4.1, §14 |
| **Kullanıcı kimlikleri** | **Evet** (Firebase UID; RevenueCat app user ID = UID; GA User-ID) | **Evet** | Hayır | **Z** | AF, AM, AN, FP | Kendi kodumuz, RevenueCat, GA, AdMob SSV | GA: `S/data/analytics/AnalyticsRepositoryImpl.kt:57` (`setUserId`). RevenueCat: `SA/data/billing/PlatformSubscriptionRepository.android.kt:495` (`logIn(uid)`). AdMob ödüllü reklam SSV: `SA/ads/RewardedAdButton.android.kt:216-220` (`setUserId(uid)`) → Google → `F/admobRewardedSsv.ts:133`. Şikâyetlerde `reporterUid` / `reportedUid` alanları: `S/data/repository/ChatRepositoryImpl.kt:274-283`, `S/feature/discover/DiscoverViewModel.kt:648-658`. | §4.3, §7.3, §8.3, §9.2 |
| **Adres** | **Hayır** | Hayır | — | — | — | — | Adres alanı yok. Analitik filtresi de `address` anahtarını çıkarıyor (`S/domain/analytics/AnalyticsParameterSanitizer.kt:33-39`). | §4.4 |
| **Telefon numarası** | **Hayır** | Hayır | — | — | — | — | Telefonla kimlik doğrulama yok. `TelephonyManager` kullanılmıyor. | §4.4 |
| **Irk ve etnik köken** | **Hayır** | Hayır | — | — | — | — | Böyle bir alan yok. Kullanıcı biyografide kendisi yazarsa "Diğer kullanıcı içeriği" kapsamındadır. | §13 (özel nitelikli veri notu) |
| **Siyasi veya dini inançlar** | **Hayır** | Hayır | — | — | — | — | Böyle bir alan yok. | §13 |
| **Cinsel yönelim** | **Hayır** | Hayır | — | — | — | — | "İlgilenilen cinsiyet" veya benzeri bir alan yok. `Gender` yalnızca kişinin kendi cinsiyetidir (`S/domain/model/Gender.kt:8-14`). | §13 |
| **Diğer bilgiler** | **Evet**: doğum tarihi, cinsiyet, dil, ülke, eşleşme filtreleri (yaş aralığı, hedef ülkeler), saat dilimi kimliği | Hayır | Hayır | **Z** | AF, PE, FP | Kendi kodumuz → Firestore | `AuthRepositoryImpl.kt:188` (`gender`), `:191` (`birthDateMillis`), `:194` (`languageCode`), `:197` (`countryCode`). Cinsiyet kayıtta zorunlu (`S/feature/registration/RegistrationViewModel.kt:183-191`; "Belirtmek istemiyorum" seçeneği var). Filtreler: `S/data/repository/SettingsRepositoryImpl.kt:65-67`. Saat dilimi: `S/data/repository/UserTimeZoneRepositoryImpl.kt:28` → `F/monetization.ts:763`. Remote Config SDK da ülke ve dil kodu ile saat dilimi toplar [FIREBASE]. | §4.2, §4.3 |

**Karar (doğum tarihi ve cinsiyet):**

- [PLAY], "Diğer bilgiler" türünü "doğum tarihi, cinsiyet kimliği…" örnekleriyle tanımlar. Bu yüzden ikisi de **Kişisel bilgiler > Diğer bilgiler** altında beyan edilir.
- Cinsiyet seçenekleri arasında `NON_BINARY` bulunur, ancak bu cinsel yönelim değil, **cinsiyet kimliğidir**. Bu yüzden "Cinsel yönelim" **Hayır** kalır.
- Amaç: doğum tarihi 18 yaş kontrolü için FP'dir (uyumluluk), yaş gösterimi ve filtreleme için AF/PE'dir. Cinsiyet eşleşme ve filtreleme için AF/PE'dir.
- İkisi de Google ile paylaşılmaz. Analitik filtresi `age`, `birthdate`, `gender` anahtarlarını çıkarır (`AnalyticsParameterSanitizer.kt:33-39`). Kişiselleştirilmemiş reklamda profil verisi AdMob'a gönderilmez; kodda `setGender` veya `setBirthday` yok.

**Karar (kullanıcı kimlikleri — paylaşılıyor):** Paylaşımın kaynağı AdMob ödüllü reklam SSV akışıdır.

- UID, `ServerSideVerificationOptions` ile Google AdMob'a gider. Oradan imzalı geri çağrıyla sunucumuza döner.
- Bu akışta AdMob sunucumuza teslimat yapan bir aracı gibi çalışır. Ancak aynı SDK birden çok müşteri için reklam profili oluşturan bir reklam ağıdır ve [PLAY] SSS'si bu tür SDK'ları hizmet sağlayıcı saymaz.
- Muhafazakâr seçim **Paylaşılıyor = Evet**, paylaşım amaçları **AF ve FP** (ödülün doğrulanması). Bkz. K-3.
- GA ve RevenueCat ise hizmet sağlayıcı olarak değerlendirilir. GA için bu, Google signals, Google Ads bağlantısı ve "Data sharing settings" ayarlarının **kapalı** olmasına bağlıdır; bkz. K-6.

### 2.3 Finansal bilgiler

| Veri türü | Toplanıyor mu | Paylaşılıyor mu | Geçici mi | Z / İB | Amaçlar | Kaynak | Kanıt | Politika bölümü |
|---|---|---|---|---|---|---|---|---|
| **Kullanıcı ödeme bilgileri** | **Hayır** | Hayır | — | — | — | Google Play Billing | Kart verisi uygulamaya hiç gelmez. RevenueCat yalnızca satın alma belirtecini görür [RC]. | §4.4, §9.1 |
| **Satın alma geçmişi** | **Evet** | Hayır | Hayır | **Z** (bkz. K-4) | AF, AN | RevenueCat SDK ve REST; GA otomatik uygulama içi satın alma olayları; kendi analitik olaylarımız | [RC]: "Purchase history" zorunlu, toplanır, geçici değil, amaçlar App functionality ve Analytics. Sunucu: `F/revenuecatSync.ts:102-111` (`customers/{uid}`: yetkiler, tier). Analitik olayları: `S/domain/analytics/AnalyticsEvent.kt:130-196` (`purchase_started`, `purchase_completed`, `subscription_changed`, SKU parametresi). [GA]: GA uygulama içi satın almaları ürün kimliği, adı ve fiyatıyla otomatik toplar. | §8.2, §9 |
| **Kredi notu** | **Hayır** | Hayır | — | — | — | — | — | — |
| **Diğer finansal bilgiler** | **Hayır** | Hayır | — | — | — | — | Reklam geliri (`ad_impression_revenue`, `AnalyticsRepositoryImpl.kt:11,32-40`) kullanıcının finansal bilgisi değildir; geliştiricinin reklam gelirine ait bir ölçümdür. | §7.4 |

**Karar (satın alma geçmişi):**

- RevenueCat ve Play, geliştirici adına abonelik durumunu yöneten hizmet sağlayıcılardır. [RC] de üçüncü taraf entegrasyon yoksa yalnızca "Collected" beyan edilmesini önerir. Uygulamada atıf veya pazarlama entegrasyonu yok (`MookApplication.kt:34-35`, yalnızca `PurchasesConfiguration.Builder(...)`). Bu yüzden **Toplanıyor = Evet, Paylaşılıyor = Hayır**.
- GA'nın otomatik satın alma olayları da K-6'daki koşulla hizmet sağlayıcı kapsamında kalır.

### 2.4 Sağlık ve fitness

| Veri türü | Toplanıyor mu | Paylaşılıyor mu | Geçici mi | Z / İB | Amaçlar | Kaynak | Kanıt | Politika bölümü |
|---|---|---|---|---|---|---|---|---|
| **Sağlık bilgileri** | **Hayır** | Hayır | — | — | — | — | Health Connect yok; böyle bir alan yok. | §13 |
| **Fitness bilgileri** | **Hayır** | Hayır | — | — | — | — | Aktivite tanıma izni yok. | — |

### 2.5 Mesajlar

| Veri türü | Toplanıyor mu | Paylaşılıyor mu | Geçici mi | Z / İB | Amaçlar | Kaynak | Kanıt | Politika bölümü |
|---|---|---|---|---|---|---|---|---|
| **E-postalar** | **Hayır** | Hayır | — | — | — | — | Kullanıcının e-postalarına erişilmez. Dışa aktarma e-postası geliştiricinin gönderdiği bir iletidir; kullanıcının e-postası değildir. | — |
| **SMS veya MMS** | **Hayır** | Hayır | — | — | — | — | SMS izni yok. | — |
| **Diğer uygulama içi mesajlar** | **Evet** | Hayır | Hayır | **Z** (bkz. K-4) | AF, FP | Kendi kodumuz → Cloud Functions → Firestore (düz metin); DeepL (çeviri); FCM (bildirim) | Kayıt: `F/index.ts:402-409` (`text`, `translatedText`, `senderLanguage`). Sohbet önizlemesi: `F/index.ts:383` (`lastMessage`). DeepL: `F/index.ts:338-361`. FCM gövdesi: `F/index.ts:421-427`. İstemci mesajı doğrudan yazamaz (`firestore.rules:378`). Uçtan uca şifreleme yok; bu yüzden [PLAY]'in E2EE istisnası **uygulanamaz**. | §5 |

**Karar (mesajlar ve DeepL çevirisi):**

- **Toplanıyor = Evet.** Mesajlar Firestore'da düz metin olarak saklanıyor. [PLAY]'in "uçtan uca şifreli veri toplama sayılmaz" istisnası geçerli değil.
- **Geçici = Hayır.** Mesaj ve çevirisi kalıcı olarak saklanıyor. WalkTalk demo çevirisi (`translateText`, `F/index.ts:110-176`) kayıt tutmaz ve geçici sayılabilir. Ancak form veri türü düzeyinde sorulduğu için mesajlar zaten "geçici değil" olarak beyan edilir.
- **Paylaşılıyor = Hayır** (koşullu). DeepL, mesajı **bizim adımıza ve talimatımızla** çevirip geri döndürür. Bu, [PLAY]'deki hizmet sağlayıcı tanımına uyar. FCM ile iletim de Google'ın hizmet sağlayıcı olarak yaptığı teslimattır. Alıcı kullanıcıya teslim ise kullanıcının başlattığı bir eylemdir.
- **Koşul:** DeepL ile bir veri işleme sözleşmesi (DPA) bulunmalı ve kullanılan anahtar, metni DeepL'in kendi amaçları için kullanmasına izin vermeyen bir plan olmalıdır. Kod `:fx` ile biten **ücretsiz API** anahtarını da destekliyor (`F/index.ts:341`). Politika §15'e göre sözleşme süreçleri hâlâ "devam etmektedir". Bkz. **K-2**: bu doğrulanamazsa muhafazakâr seçim "Paylaşılıyor = Evet"tir.
- **Amaç FP:** Politika §5.1'e göre mesaj içeriğine şikâyet incelemesi için erişilebilir. Şikâyet kaydında mesaj kimliği tutulur (`ChatRepositoryImpl.kt:274-283`).

### 2.6 Fotoğraflar ve videolar

| Veri türü | Toplanıyor mu | Paylaşılıyor mu | Geçici mi | Z / İB | Amaçlar | Kaynak | Kanıt | Politika bölümü |
|---|---|---|---|---|---|---|---|---|
| **Fotoğraflar** | **Evet** | Hayır | Hayır | **Z** | AF | Kendi kodumuz → Firebase Storage `users/{uid}/…` | `AuthRepositoryImpl.kt:38` (Storage yükleme), `:166-176` (avatar ve en az bir Keşfet fotoğrafı zorunlu; yükleme başarısız olursa kayıt tamamlanmaz). Kurallar: `storage.rules:27-33`. Seçim sistem Fotoğraf Seçicisi ile yapılıyor; galeri izni yok. | §3.2, §4.2, §6.3 |
| **Videolar** | **Hayır** | Hayır | — | — | — | — | Video yükleme yok. `stories/` yolu WalkTalk'a ait; Mook istemcisi bu yola yazmıyor (istemcide `stories` veya `posts` koleksiyonu kullanılmıyor). | §5.5 |

### 2.7 Ses dosyaları

| Veri türü | Toplanıyor mu | Paylaşılıyor mu | Geçici mi | Z / İB | Amaçlar | Kaynak | Kanıt | Politika bölümü |
|---|---|---|---|---|---|---|---|---|
| **Ses veya ses kayıtları** | **Hayır** | Hayır | — | — | — | — | `RECORD_AUDIO` izni yok. `AudioPlayerPill.kt` yalnızca bir arayüz bileşeni. | §3.1 |
| **Müzik dosyaları** | **Hayır** | Hayır | — | — | — | — | — | — |
| **Diğer ses dosyaları** | **Hayır** | Hayır | — | — | — | — | — | — |

### 2.8 Dosyalar ve belgeler

| Veri türü | Toplanıyor mu | Paylaşılıyor mu | Geçici mi | Z / İB | Amaçlar | Kaynak | Kanıt | Politika bölümü |
|---|---|---|---|---|---|---|---|---|
| **Dosyalar ve belgeler** | **Hayır** | Hayır | — | — | — | — | Dosya seçici yok; yalnızca görsel seçiliyor ve bu "Fotoğraflar" altında beyan ediliyor. Veri dışa aktarma JSON'u sunucuda bizim verimizden üretiliyor (`F/exportUserData.ts:41-48`); kullanıcının dosyası değildir. | §3.2 |

### 2.9 Takvim ve kişiler

| Veri türü | Toplanıyor mu | Paylaşılıyor mu | Geçici mi | Z / İB | Amaçlar | Kaynak | Kanıt | Politika bölümü |
|---|---|---|---|---|---|---|---|---|
| **Takvim etkinlikleri** | **Hayır** | Hayır | — | — | — | — | Takvim izni yok. | §3.1 |
| **Kişiler** | **Hayır** | Hayır | — | — | — | — | `READ_CONTACTS` izni yok. Eşleşmeler ve uygulama içi etkileşimler cihaz rehberi değildir; "Diğer işlemler" altında beyan ediliyor. | §3.1, §4.4 |

### 2.10 Uygulama etkinliği

| Veri türü | Toplanıyor mu | Paylaşılıyor mu | Geçici mi | Z / İB | Amaçlar | Kaynak | Kanıt | Politika bölümü |
|---|---|---|---|---|---|---|---|---|
| **Uygulama etkileşimleri** | **Evet** | **Evet** | Hayır | **Z** | AN, AD, FP | AdMob (paylaşılır); GA (ekran görüntülemeleri, oturumlar, özel olaylar) | [ADMOB]: "user product interactions … app launch, taps, and video views" toplanır ve paylaşılır. [GA]: ekran görüntülemeleri ve oturumlar. Özel olaylar: `S/domain/analytics/AnalyticsEvent.kt:38-196` (`gate_decision`, `limit_reached`, `ad_shown`, `paywall_view` vb.); `AnalyticsRepositoryImpl.kt:11-13`. GA rıza ile açılır (`androidApp/src/main/AndroidManifest.xml:61`, `SA/ads/AdMobConsentManager.kt:219-234`), ancak AdMob yüzünden tür Zorunlu kalır. | §7.2, §8 |
| **Uygulama içi arama geçmişi** | **Hayır** | Hayır | — | — | — | — | Arama özelliği yok. Ülke açılır listesinde filtreleme cihazda yapılıyor (`S/ui/components/CountryDropdown.kt`) ve sunucuya gönderilmiyor. | — |
| **Yüklü uygulamalar** | **Hayır** | Hayır | — | — | — | — | Yalnızca `com.istaps.walktalk2` paketinin cihazda olup olmadığına bakılıyor (`androidApp/src/main/AndroidManifest.xml:40-42`). Sonuç cihazdan çıkmıyor; [PLAY]'e göre yalnızca cihazda işlenen veri toplanmış sayılmaz. | §3.3 |
| **Diğer kullanıcı içeriği** | **Evet** (biyografi, ilgi alanları) | Hayır | Hayır | **Z** | AF, PE | Kendi kodumuz → Firestore | `AuthRepositoryImpl.kt:179-180`. Biyografi kayıtta doğrulanıyor (`RegistrationViewModel.kt:250-284`). [PLAY], "Diğer kullanıcı içeriği" türüne biyografiyi açıkça örnek verir. | §4.2, §6.1 |
| **Diğer işlemler** | **Evet**: beğeni/geçme, eşleşmeler, profil ziyaretleri, "Beni Beğenenler" kilit açmaları, dil odası seçimi, geri alma, boost, gizli mod, şikâyetler, okundu bilgisi, kullanım kotaları, ödüllü reklam kayıtları | Hayır | Hayır | **Z** | AF, PE, FP, AN | Kendi kodumuz → Cloud Functions → Firestore | Etkileşimler: `F/monetization.ts:350-385` (`interactions`: `fromUserId`, `toUserId`, `type`). Kilit açma: `:610`. Ziyaretler: `:657`. Şikâyetler: `ChatRepositoryImpl.kt:274-283`, `DiscoverViewModel.kt:648-658` (`reason`, `chatId`, `messageId`). Okundu bilgisi: `ChatRepositoryImpl.kt:395-405`. Kötüye kullanım sinyalleri: `F/abuseFlags.ts:88`. [PLAY], "Diğer işlemler" türüne beğenileri örnek verir. | §4.3 |

**Karar (şikâyetler):** Şikâyet kaydı şu alanları içerir: şikâyet eden ve edilen UID'leri, listeden seçilen bir neden, ve mesaj şikâyetinde `chatId` ile `messageId`. Mesaj metni kayda kopyalanmaz. Bu kayıt iki türe dağıtılır:

- Kimlikler → **Kullanıcı kimlikleri**.
- Şikâyet eylemi ve nedeni → **Diğer işlemler** (amaç: **FP**).

Şikâyetler istemciden okunamaz (`firestore.rules:480`). Üçüncü tarafa gönderilmez.

### 2.11 Web'de gezinme

| Veri türü | Toplanıyor mu | Paylaşılıyor mu | Geçici mi | Z / İB | Amaçlar | Kaynak | Kanıt | Politika bölümü |
|---|---|---|---|---|---|---|---|---|
| **Web'de gezinme geçmişi** | **Hayır** | Hayır | — | — | — | — | Uygulama içinde WebView ile gezinme yok. Yasal bağlantılar harici tarayıcıda açılıyor (`S/domain/legal/LegalLinks.kt`). | — |

### 2.12 Uygulama bilgileri ve performans

| Veri türü | Toplanıyor mu | Paylaşılıyor mu | Geçici mi | Z / İB | Amaçlar | Kaynak | Kanıt | Politika bölümü |
|---|---|---|---|---|---|---|---|---|
| **Kilitlenme günlükleri** | **Hayır** | Hayır | — | — | — | — | Crashlytics, Sentry veya benzeri bir SDK yok (Gradle dosyaları). Kendi hata günlüklerimiz yalnızca logcat'e yazılıyor. [ADMOB] ve [FIREBASE] çökme günlüğü listelemiyor. Play'in kendi çökme raporları geliştiricinin toplaması sayılmaz. | §12 |
| **Teşhis** | **Evet** | **Evet** | Hayır | **Z** | AN, AD, FP | AdMob SDK; Firebase user agent (cihaz modeli, OS, SDK sürümleri) | [ADMOB]: "Diagnostic information … app launch time, hang rate, and energy usage" toplanır ve paylaşılır. [FIREBASE]: user agent'ın kullanıcı veya cihaz kimliğine hiç bağlanmadığını belirtir. **Kod kanıtı yok** (yalnızca SDK belgesi). | §7.2, §12 |
| **Diğer uygulama performans verileri** | **Hayır** | Hayır | — | — | — | — | — | — |

### 2.13 Cihaz veya diğer kimlikler

| Veri türü | Toplanıyor mu | Paylaşılıyor mu | Geçici mi | Z / İB | Amaçlar | Kaynak | Kanıt | Politika bölümü |
|---|---|---|---|---|---|---|---|---|
| **Cihaz veya diğer kimlikler** | **Evet** | **Evet** | Hayır | **Z** | AF, AN, AD, FP | AdMob (AAID, App Set ID: paylaşılır); kendi kodumuz (SHA-256 `ANDROID_ID` özeti); FCM jetonu; Firebase Installation ID; GA App Instance ID ve AAID (yalnızca `ad_storage` açıkken); Play Integrity / App Check jetonu | AAID izni: `androidApp/src/main/AndroidManifest.xml:13`. [ADMOB]: "Android advertising (ad) ID", "app set ID" toplanır ve paylaşılır. `ANDROID_ID` özeti: `SA/data/billing/PlatformDeviceIdentifier.android.kt:28-36` → `checkDeviceTrialEligibility` ve `recordDeviceTrialConsumption` (`PlatformSubscriptionRepository.android.kt:322,332`) → `device_trial_ledger` (`F/monetization.ts:980-998`). FCM: `S/data/repository/PushTokenRepositoryImpl.kt:47`. App Check: `SA/appcheck/AppCheckInstaller.kt:20`. Installations ve Functions FCM jetonu: [FIREBASE]. | §7.2, §8.2, §10, §11 |

**Karar (hashlenmiş ANDROID_ID, AAID, App Set ID):**

- **SHA-256 ANDROID_ID özeti:** Geri döndürülemez olsa da aynı cihaz için her zaman aynı değeri üretir. Bu yüzden cihaza bağlı, takma adlı bir kimliktir ve [PLAY]'e göre takma adlı veri beyan edilir. Tür: **Cihaz veya diğer kimlikler**, amaç **FP** (ücretsiz deneme kötüye kullanımının önlenmesi). Özet yalnızca kendi sunucumuzda saklanır; bu kaynaktan paylaşım yoktur.
- **AAID ve App Set ID:** AdMob, ücretsiz plandaki her kullanıcıda, kişiselleştirilmemiş reklamlarda bile toplar ve paylaşır (politika §7.2 de bunu söylüyor). Bu yüzden tür **Paylaşılıyor = Evet**, amaçlar **AD, AN, FP**.
- **GA'daki AAID:** Yalnızca `ad_storage` izni verildiğinde kullanılır (`AdMobConsentManager.kt:243-249`). Ancak tür düzeyinde cevap AdMob nedeniyle zaten "Evet / Zorunlu"dur.
- **FCM jetonu ve Installation ID:** Amaç **AF** (bildirim gönderimi).
- **RevenueCat:** Reklam kimliği toplamıyor. `collectDeviceIdentifiers` veya atıf entegrasyonu yok; [RC], cihaz kimliklerinin yalnızca bu tür entegrasyonlarda beyan edilmesi gerektiğini söylüyor.

### 2.14 Formda olmayan ama kayda geçmesi gereken işlemler

| Konu | Karar |
|---|---|
| **Profil verilerinin diğer kullanıcılara gösterilmesi** (ad, yaş, ülke, fotoğraflar, biyografi) | Diğer kullanıcılar üçüncü taraf değildir. Profil yayımlamak kullanıcının başlattığı eylemdir. Paylaşma sayılmaz. |
| **WalkTalk SSO** (`generateSsoToken`, `F/index.ts:96`) | WalkTalk aynı geliştiricinin uygulamasıdır (politika §14.1). Birinci taraf aktarımıdır ve kullanıcının başlattığı bir eylemdir. Paylaşma sayılmaz. Ancak Play'de iki uygulamanın **aynı geliştirici hesabında** yayımlanması gerekir; bkz. K-8. |
| **Onay (KVKK) tercihleri** | Yalnızca cihazda SharedPreferences'ta tutuluyor (`SA/ads/AdMobConsentManager.kt:67-113`). Cihazdan çıkmadığı için toplanmış sayılmaz. Bkz. Ç-6. |
| **Remote Config** | Ülke ve dil kodu, saat dilimi, OS sürümü ve Installation ID gönderir [FIREBASE]. Bunlar zaten "Diğer bilgiler" ve "Cihaz veya diğer kimlikler" altında beyan edildi. |

---

## 3. Politika ile çelişkiler

| # | Çelişki | Kod / SDK kanıtı | Politika | Önerilen düzeltme tarafı |
|---|---|---|---|---|
| **Ç-1** | Politika, AdMob'un KVKK kararından **önce** başlayabileceğini ve reklam yükleyebileceğini söylüyor. Kod artık karar verilmeden SDK'yı başlatmıyor. | `76e9c2c fix(ads): AdMob'u KVKK onay kararından sonra başlat`; `S/domain/consent/ConsentRepository.kt:105` (`adsMayStart`); `SA/ads/AdMobConsentManager.kt:189-210` | TR §7.1 "Önemli" kutusu ve EN §7.1 "Important" kutusu | **Politika.** Kod daha koruyucu. `yasal-kaynak/Gizlilik_Politikasi_yamalardan_sonra.md` bu metni zaten doğru biçimde içeriyor; o sürüm (ve EN karşılığı) yayımlanmalı. |
| **Ç-2** | Politika, Android'de eski sürümler için depolama izni bildirildiğini söylüyor. Manifest bu izni kaldırıyor. | `a8d44bf`; `androidApp/src/main/AndroidManifest.xml:23-25`; yeniden üretilen birleşik manifestte yok | TR §3.2 Not, EN §3.2 Note | **Politika** (aynı "yamalardan sonra" sürümü). |
| **Ç-3** | Hesap silme web sayfası, destek adresi olarak `destek@walktalkk.com` ve geliştirici olarak "MCCLabs" yazıyor. Politika ise veri sorumlusu olarak bir gerçek kişiyi ve farklı bir e-posta adresini gösteriyor. [PLAY-DEL], sayfanın Play mağaza kaydındaki uygulama ya da geliştirici adını içermesini şart koşuyor. | `hosting/mook-sso/account-deletion.html` | §1, §19.4 | **Web sayfası** (veya mağaza kaydı). Mağazadaki geliştirici adıyla aynı ad ve politikadaki başvuru e-postası kullanılmalı. Hangisinin doğru olduğuna kullanıcı karar vermeli. |
| **Ç-4** | Hesap silme sayfasında silinen veriler arasında "konum tercihleri" yazıyor. Uygulama konum toplamıyor; kastedilen, hedef ülke ve yaş aralığı filtreleri. | `SettingsRepositoryImpl.kt:65-67` | §3.1, §4.4 | **Web sayfası:** "eşleşme filtreleri (yaş aralığı, hedef ülkeler)" olarak düzeltilmeli. |
| **Ç-5** | Politika §4.4 "Kesin veya yaklaşık konum (GPS)" toplamadığını söylüyor. Veri güvenliği formunda ise IP'den çıkarılan **yaklaşık konum** beyan edilecek. | [ADMOB] IP → genel konum; [GA] maskelenmiş IP → kaba konum | §4.4 (ama §7.2 ve §8.2 IP'den konum tahminini anlatıyor) | **Politika.** §4.4 şöyle netleştirilmeli: "Cihazınızın GPS veya ağ konumuna erişmeyiz; Google SDK'ları IP adresinden ülke veya şehir düzeyinde yaklaşık konum çıkarabilir (bkz. §7.2, §8.2)." Aksi halde form ile politika çelişkili görünür. |
| **Ç-6** | Politika §4.3, "Onay tercihleriniz"i toplanan veriler arasında sayıyor. Kodda bu tercihler yalnızca cihazda SharedPreferences'ta tutuluyor; sunucuya yazıldığına dair kanıt yok. | `SA/ads/AdMobConsentManager.kt:67-69,113` | §4.3 (§18 ise bunu doğru biçimde yerel sayıyor) | **Politika**, düşük öncelik. Satır §18'e taşınabilir ya da "cihazınızda saklanır" diye düzeltilebilir. Formda beyan edilmez. Fazladan açıklama yapmak zarar vermez. |
| **Ç-7** | Eşleşme filtreleri (yaş aralığı `ageRangeStart`/`End`, `targetCountries`) ve uygulama dili (`appLanguage`) Firestore'a yazılıyor, ama politika §4'te listelenmiyor. | `SettingsRepositoryImpl.kt:65-67,146` | §4.2–4.3 | **Politika:** §4.3'e "Eşleşme filtreleri ve uygulama dili tercihi" satırı eklenmeli. |
| **Ç-8** | WalkTalk canlı çeviri demosunda kullanıcının yazdığı metin DeepL'e gönderiliyor (kaydedilmiyor). Politika §5.2 yalnızca özel mesaj çevirisini anlatıyor. | `F/index.ts:110-176`; `WALKTALK_DEMO.md` | §5.2, §14 (DeepL satırı) | **Politika:** §5.2'ye demo çevirisinin de DeepL'e gittiği ve saklanmadığı eklenmeli. |
| **Ç-9** | Birleşik manifestte Privacy Sandbox izinleri var (`ACCESS_ADSERVICES_AD_ID`, `ACCESS_ADSERVICES_ATTRIBUTION`, `ACCESS_ADSERVICES_TOPICS`; Ads SDK'sından geliyor). Politika §3.3 bu kütüphane izinlerini yalnızca genel olarak anıyor; ilgi alanı tabanlı Topics sinyalinden söz etmiyor. | Birleşik manifest satır 77-79 | §3.3, §7 | **Politika**, düşük öncelik: §7'ye bir cümle eklenebilir. Alternatif olarak kodda `tools:node="remove"` ile kaldırılabilir; ancak bu görev kapsamında kod değiştirilmedi. |
| **Ç-10** | Yayımlanan politika ve `Gizlilik_Politikasi_yamalardan_sonra.md` iki ayrı sürüm. "Yamalardan sonra" sürümünde tarih yer tutucu (`[Yayın tarihi]`) ve Apple satırı "şu anda aktarım yok" notunu kaybetmiş (iOS yayımlanmadığı için yanlış). | `diff Gizlilik_Politikasi.md Gizlilik_Politikasi_yamalardan_sonra.md` | §14 (Apple satırı), §15 | **Politika:** Ç-1 ve Ç-2 için "yamalardan sonra" sürümü yayımlanırken Apple notu ve KVKK §15 cümlesi yayımlanan sürümden korunmalı. |

Kod, SDK belgeleri ve politika şu konularda **tutarlı**: E2EE olmaması (§5), DeepL'e mesaj gönderimi, FCM bildirim içeriği, ANDROID_ID özeti (§10), RevenueCat'te AAID olmaması (§9.2), analitik hassas parametre filtresi (§8.3 listesi `AnalyticsParameterSanitizer.kt:33-39` ile birebir), ödüllü reklamda UID (§7.3), 7 günlük dışa aktarma bağlantısı (§19.3, `exportUserData.ts:8`), hesap silme kapsamı (§16.2, `deletionSchema.ts`), çökme raporlama SDK'sı olmaması (§12).

---

## 4. Karar gerekli

Her maddede **muhafazakâr seçenek** kalın yazıldı. Bölüm 2'deki tablo, aksi belirtilmedikçe muhafazakâr seçeneği kullanır.

| # | Konu | Seçenekler | Öneri |
|---|---|---|---|
| **K-1** | IP'den çıkarılan yaklaşık konum | (a) **Toplanıyor + Paylaşılıyor, Zorunlu; amaçlar AD, AN, FP**. (b) Hiç beyan etmemek: uygulama konuma erişmiyor, IP'yi konum için biz kullanmıyoruz. | **(a).** [PLAY], IP'den çıkarılan konumu açıkça kapsıyor. [ADMOB] de IP'nin genel konum tahmini için kullanılabileceğini söylüyor. Bu cevap politika §4.4'ün düzeltilmesini gerektirir (Ç-5). |
| **K-2** | Mesajların DeepL'e gönderilmesi | (a) Hizmet sağlayıcı: **Paylaşılıyor = Hayır**. (b) **Paylaşılıyor = Evet**, amaç AF. | DeepL ile imzalı bir DPA varsa ve canlı ortam **DeepL API Pro** anahtarı (`:fx` ile bitmeyen) kullanıyorsa (a). İkisinden biri doğrulanamazsa **(b)**. Politika §15'te sözleşme süreci "devam ediyor" dendiği için şu an (b) daha güvenli. Kontrol: `firebase functions:secrets:access DEEPL_AUTH_KEY` çıktısının `:fx` ile bitip bitmediğine bakılmalı (anahtarın kendisi paylaşılmadan). |
| **K-3** | Ödüllü reklamda UID'nin AdMob'a gitmesi | (a) **Kullanıcı kimlikleri → Paylaşılıyor = Evet** (AF, FP). (b) Google'ı SSV teslimatında hizmet sağlayıcı sayıp "Hayır". | **(a).** AdMob, birden çok müşteri için reklam profili oluşturan bir ağ; [PLAY] SSS'si bunu hizmet sağlayıcı saymıyor. Fark yalnızca "Kullanıcı kimlikleri" satırını etkiler. |
| **K-4** | Mesajlar, satın alma geçmişi ve fotoğraflar için Zorunlu / İsteğe bağlı | (a) **Zorunlu**. (b) Mesajlar ve satın alma geçmişi için İsteğe bağlı: kullanıcı mesaj göndermeden ve satın almadan uygulamayı kullanabilir. | **(a).** [PLAY], uygulamanın temel işlevi için gereken veriyi Zorunlu sayar. Mesajlaşma tanışma uygulamasının çekirdeğidir. [RC] satın alma geçmişi için "required" der. Fotoğraf kayıtta zorunludur, İsteğe bağlı **seçilemez**. Zorunlu yerine yanlışlıkla İsteğe bağlı seçmek ihlaldir; tersi değildir. |
| **K-5** | AdMob teşhis verisi | (a) **Teşhis: Evet / Evet**. (b) Beyan etmemek. | **(a).** [ADMOB] açıkça "Diagnostic information" diyor. Kod kanıtı yok; yalnızca SDK belgesi var. |
| **K-6** | GA'nın hizmet sağlayıcı sayılması (Kullanıcı kimlikleri, Satın alma geçmişi, Uygulama etkileşimleri) | (a) GA hizmet sağlayıcı: GA kaynaklı paylaşım beyanı yok. (b) **GA kaynaklı türler de "Paylaşılıyor".** | Firebase ve GA konsolunda **Google signals kapalı**, **Google Ads bağlantısı yok** ve "Data sharing settings" (ör. "Google products & services") **kapalı** ise (a). Bunlar koddan doğrulanamaz; açık olan varsa (b). (b) seçilse bile yalnızca "Satın alma geçmişi" satırı değişir; diğer türler AdMob yüzünden zaten "Paylaşılıyor". |
| **K-7** | Hesap silmeden kısmi veri silme | (a) "Evet" + **URL boş**: uygulama içinde mesaj, fotoğraf ve profil düzenleme var. (b) "Evet" + web sayfasına kısmi silme talebi bölümü ekleyip URL'yi girmek. (c) "Hayır". | **(a)** şimdilik yeterli ve doğru. (b) için web sayfası değişmeli; bu görev kapsamında değiştirilmedi. (c), uygulama içi mesaj silme varken eksik beyan olur. |
| **K-8** | WalkTalk ile ortak `users` koleksiyonu ve SSO | (a) Birinci taraf: paylaşma yok. (b) **WalkTalk farklı bir Play geliştirici hesabındaysa Kullanıcı kimlikleri, Ad, E-posta → "Paylaşılıyor".** | Politika §14.1 aynı veri sorumlusunu söylüyor. WalkTalk'un paket adı `com.istaps.walktalk2`, Mook'unki `com.mcclabs.mook`: iki farklı ad alanı. Play'deki "birinci taraf" genellikle mağaza kaydındaki yayıncıdır. İki uygulama **aynı Play geliştirici hesabında değilse** (b) seçilmeli. Bunu kullanıcı doğrulamalı. |
| **K-9** | Kişisel bilgiler > Diğer bilgiler: ülke (kullanıcının seçtiği) | (a) **Diğer bilgiler**. (b) Yaklaşık konum. | **(a).** Ülke kullanıcının kendi beyanıdır, cihaz konumu değildir. Yaklaşık konum zaten K-1 nedeniyle "Evet". |
| **K-10** | "Developer communications" amacı | (a) Hiçbir türde seçmemek. (b) E-posta ve Cihaz kimlikleri (FCM) için seçmek. | **(a)**, şu an için. Abonelik bildirimleri (`F/push.ts`) ve dışa aktarma e-postası kullanıcının hesabıyla ilgili işlevsel iletilerdir (AF/AM); pazarlama veya duyuru gönderimi yok. Politika §21'deki "önemli değişiklikleri e-posta ile duyururuz" taahhüdü uygulanmaya başlarsa E-posta için **DC** eklenmeli. |

---

## 5. Play Console'da adım adım

> Yol: **Play Console > Mook > Politika ve programlar > Uygulama içeriği > Veri güvenliği > Başlat / Yönet**. [PLAY]'deki sıra: Genel bakış → Veri toplama ve güvenlik → Veri türleri → Veri kullanımı ve işleme → Mağaza girişi önizlemesi → Gönder.

### Adım 1 — Genel bakış
1. Metni okuyun, **İleri**'ye tıklayın.

### Adım 2 — Veri toplama ve güvenlik
1. "Uygulamanız, zorunlu kullanıcı veri türlerinden herhangi birini topluyor veya paylaşıyor mu?" → **Evet**.
2. "Uygulamanızın topladığı tüm kullanıcı verileri aktarım sırasında şifreleniyor mu?" → **Evet**.
3. "Kullanıcıların uygulamanızda hesap oluşturmasına izin veriyor musunuz?" / hesap oluşturma yöntemleri:
   - **Kullanıcı adı ve şifre** kutusunu işaretleyin.
   - Diğer kutuları (OAuth, Diğer, "hesap oluşturulmasına izin vermiyorum") **boş** bırakın.
4. "Hesap silme URL'si" → `https://mook-sso.web.app/account-deletion.html` (önce Ç-3 ve Ç-4'teki düzeltmeleri yapın).
5. "Kullanıcıların hesaplarını silmeden verilerinin bir kısmını veya tamamını silmeyi talep etmesi için bir yol sağlıyor musunuz?" → **Evet**. İsteğe bağlı URL alanını **boş** bırakın (K-7).
6. (Gösterilirse) Rozetler bölümü:
   - "Bağımsız güvenlik incelemesi" → **işaretlemeyin**.
   - "Aileler Politikası taahhüdü" → **işaretlemeyin**.
   - "UPI" → **işaretlemeyin**.
7. **İleri**'ye tıklayın.

### Adım 3 — Veri türleri
**Yalnızca** aşağıdaki kutuları işaretleyin. Diğer tüm kutular boş kalmalı.

| Kategori | İşaretlenecek kutular |
|---|---|
| **Konum** | Yaklaşık konum |
| **Kişisel bilgiler** | Ad, E-posta adresi, Kullanıcı kimlikleri, Diğer bilgiler |
| **Finansal bilgiler** | Satın alma geçmişi |
| **Mesajlar** | Diğer uygulama içi mesajlar |
| **Fotoğraflar ve videolar** | Fotoğraflar |
| **Uygulama etkinliği** | Uygulama etkileşimleri, Diğer kullanıcı içeriği, Diğer işlemler |
| **Uygulama bilgileri ve performans** | Teşhis |
| **Cihaz veya diğer kimlikler** | Cihaz veya diğer kimlikler |

**Boş kalacak kategoriler:** Sağlık ve fitness, Ses dosyaları, Dosyalar ve belgeler, Takvim, Kişiler, Web'de gezinme. Ayrıca Hassas konum, Adres, Telefon numarası, Irk ve etnik köken, Siyasi veya dini inançlar, Cinsel yönelim, Kullanıcı ödeme bilgileri, Kredi notu, Diğer finansal bilgiler, E-postalar, SMS veya MMS, Videolar, Uygulama içi arama geçmişi, Yüklü uygulamalar, Kilitlenme günlükleri ve Diğer uygulama performans verileri de işaretlenmez.

**İleri**'ye tıklayın.

### Adım 4 — Veri kullanımı ve işleme
Her türün yanındaki **→ (Başlat)** okuna tıklayın. Her tür için sırasıyla dört soru gelir:

- **Toplanma / paylaşılma:** "Bu veriler toplanıyor mu, paylaşılıyor mu?" İki kutudan uygun olanları işaretleyin.
- **Geçici işleme:** "Bu veriler geçici olarak mı işleniyor?"
- **Zorunlu / isteğe bağlı:** "Bu veri toplama zorunlu mu, yoksa kullanıcılar bu verilerin toplanıp toplanmayacağını seçebilir mi?"
- **Amaçlar:** "Bu kullanıcı verileri neden toplanıyor?" Paylaşılan türlerde ayrıca "Neden paylaşılıyor?" sorusu gelir.

| Tür | Toplanıyor | Paylaşılıyor | Geçici | Zorunlu / İsteğe bağlı | Toplama amaçları | Paylaşma amaçları |
|---|---|---|---|---|---|---|
| Yaklaşık konum | ✔ | ✔ | Hayır | Zorunlu | Analitik; Reklam veya pazarlama; Dolandırıcılığı önleme, güvenlik ve uyumluluk | Reklam veya pazarlama; Analitik; Dolandırıcılığı önleme, güvenlik ve uyumluluk |
| Ad | ✔ | — | Hayır | Zorunlu | Uygulama işlevselliği; Hesap yönetimi | — |
| E-posta adresi | ✔ | — | Hayır | Zorunlu | Uygulama işlevselliği; Hesap yönetimi | — |
| Kullanıcı kimlikleri | ✔ | ✔ (K-3) | Hayır | Zorunlu | Uygulama işlevselliği; Analitik; Dolandırıcılığı önleme, güvenlik ve uyumluluk; Hesap yönetimi | Uygulama işlevselliği; Dolandırıcılığı önleme, güvenlik ve uyumluluk |
| Diğer bilgiler | ✔ | — | Hayır | Zorunlu | Uygulama işlevselliği; Kişiselleştirme; Dolandırıcılığı önleme, güvenlik ve uyumluluk | — |
| Satın alma geçmişi | ✔ | — (K-6) | Hayır | Zorunlu (K-4) | Uygulama işlevselliği; Analitik | — |
| Diğer uygulama içi mesajlar | ✔ | — (K-2: doğrulanamazsa ✔, amaç Uygulama işlevselliği) | Hayır | Zorunlu (K-4) | Uygulama işlevselliği; Dolandırıcılığı önleme, güvenlik ve uyumluluk | — |
| Fotoğraflar | ✔ | — | Hayır | Zorunlu | Uygulama işlevselliği | — |
| Uygulama etkileşimleri | ✔ | ✔ | Hayır | Zorunlu | Analitik; Reklam veya pazarlama; Dolandırıcılığı önleme, güvenlik ve uyumluluk | Reklam veya pazarlama; Analitik; Dolandırıcılığı önleme, güvenlik ve uyumluluk |
| Diğer kullanıcı içeriği | ✔ | — | Hayır | Zorunlu | Uygulama işlevselliği; Kişiselleştirme | — |
| Diğer işlemler | ✔ | — | Hayır | Zorunlu | Uygulama işlevselliği; Analitik; Kişiselleştirme; Dolandırıcılığı önleme, güvenlik ve uyumluluk | — |
| Teşhis | ✔ | ✔ | Hayır | Zorunlu | Analitik; Reklam veya pazarlama; Dolandırıcılığı önleme, güvenlik ve uyumluluk | Reklam veya pazarlama; Analitik; Dolandırıcılığı önleme, güvenlik ve uyumluluk |
| Cihaz veya diğer kimlikler | ✔ | ✔ | Hayır | Zorunlu | Uygulama işlevselliği; Analitik; Reklam veya pazarlama; Dolandırıcılığı önleme, güvenlik ve uyumluluk | Reklam veya pazarlama; Analitik; Dolandırıcılığı önleme, güvenlik ve uyumluluk |

Her türü kaydettikten sonra **İleri**'ye tıklayın.

### Adım 5 — Mağaza girişi önizlemesi
1. Önizlemede "Paylaşılan veriler" başlığı altında şunlar görünmeli: Konum, Kişisel bilgiler (yalnızca K-3 "Evet" ise), Uygulama etkinliği, Uygulama bilgileri ve performans, Cihaz veya diğer kimlikler.
2. "Toplanan veriler" başlığı altında, Bölüm 2'deki "Evet" türlerinin hepsi görünmeli.
3. "Veriler aktarım sırasında şifrelenir" ve "Verilerin silinmesini isteyebilirsiniz" ifadeleri görünmeli.
4. Hata yoksa **Gönder**'e tıklayın. Henüz Ç-3 veya Ç-4 düzeltilmediyse **Taslak olarak kaydet**'e tıklayın.

### Adım 6 — Gönderim sonrası
- Politika düzeltmeleri (Ç-1, Ç-2, Ç-5, Ç-7, Ç-8) yayımlandıktan sonra Play Console'daki gizlilik politikası URL'sinin aynı kaldığını doğrulayın.
- İleride yapılacak SDK güncellemelerinde [ADMOB] ve [FIREBASE] sayfalarını yeniden okuyun. [ADMOB] yalnızca en son SDK sürümünü kapsar (şu an 25.5.0).

---

## 6. Doğrulama notları

- **"Hayır" cevaplarının kontrolü.** Her "Hayır" cevabı birleşik manifest, Gradle bağımlılıkları, istemcideki Firestore koleksiyon listesi (`chats`, `config`, `customers`, `interactions`, `matches`, `messages`, `read_receipts`, `reports`, `users`), Storage yolları ve Cloud Functions çıkışları ile yeniden karşılaştırıldı:
  - Konum, telefon, kişiler, takvim, ses, dosya, SMS, web geçmişi, sağlık ve çökme günlüğü için kodda veya izinlerde iz yok.
  - "Yüklü uygulamalar" için tek iz cihaz içi `queries` kontrolüdür.
- **"Evet" cevaplarının kontrolü.** Her "Evet" cevabının en az bir kanıtı tabloda var.
- **Yalnızca SDK belgesine dayanan cevaplar** (kodda doğrudan kanıt yok):
  1. Yaklaşık konum: [ADMOB] IP adresi, [GA] maskelenmiş IP.
  2. Teşhis: [ADMOB] tanılama bilgileri, [FIREBASE] user agent.
  3. Cihaz veya diğer kimlikler içindeki **App Set ID** ve **Firebase Installation ID**. Türün kendisi kodla da kanıtlı (ANDROID_ID özeti, FCM jetonu, `AD_ID` izni).
  4. Uygulama etkileşimleri türünün **paylaşılma** kısmı: [ADMOB]. Toplama kısmı kodla kanıtlı (özel analitik olayları).
  5. Satın alma geçmişinde GA'nın otomatik uygulama içi satın alma toplaması: [GA]. Tür, RevenueCat ve kendi olaylarımızla kodla da kanıtlı.
