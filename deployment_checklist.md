# WalkMatch (Mook) — Dağıtım & Yapılandırma Kontrol Listesi

> Kapsam: Zorla Güncelleme, `migrateExistingUsers` tek seferlik göçü, sunucu-taraflı
> Discover akışı, RevenueCat REST fallback doğrulaması ve webhook idempotency
> altyapı çalışmasının PRODUCTION'a çıkışı için gereken TÜM manuel adımlar.
> Bu belge kod DEĞİLDİR — dağıtımı yapan kişi tarafından, yukarıdan aşağı,
> her kutuyu işaretleyerek takip edilmelidir.

## 0) Ön Koşullar

- [ ] `firebase --version` >= 13.x, proje `firebase use <production-project-id>` ile
      doğru projeye bağlı.
- [ ] Play Console'da `com.mcclabs.mook` paketi için **Sahip (Owner)** veya
      **Sürümleri Yönet + Finansal Veriler** iznine sahip bir hesapla giriş yapılmış.
- [ ] RevenueCat dashboard'unda proje **production** app'ine erişim var.
- [ ] Google Cloud Console'da Firebase projesiyle AYNI GCP projesine erişim var
      (Pub/Sub, IAM, Secret Manager için gerekli).

---

## 1) Firebase Secrets (Secret Manager)

Bu fazda ikisi de KOD İÇİNE GÖMÜLMEDEN, yalnızca Secret Manager üzerinden okunan
iki secret tanımlandı/kullanıldı:

- [ ] `RC_WEBHOOK_SIGNING_SECRET` — `revenuecatWebhook.ts`'in HMAC imza doğrulaması
      için. RevenueCat dashboard → Project Settings → Webhooks → (bu webhook'un)
      **Signing Secret** değeriyle BİREBİR aynı olmalı.
  ```
  firebase functions:secrets:set RC_WEBHOOK_SIGNING_SECRET
  ```
- [ ] `REVENUECAT_API_KEY` — `subscriptionVerification.ts` (`verifyEntitlementNow`
      callable) için RevenueCat'in **Secret API Key**'i (public/SDK anahtarı DEĞİL —
      REST API'nin `/v1/subscribers/{app_user_id}` uç noktasına erişim gerektirir).
      RevenueCat dashboard → Project Settings → API Keys → **Secret key**.
  ```
  firebase functions:secrets:set REVENUECAT_API_KEY
  ```
- [ ] Her iki secret için de `firebase functions:secrets:access <NAME>` ile
      staging/production ortamında doğru değerin okunduğu doğrulanmış.
- [ ] `functions/src/index.ts` içindeki `onRevenueCatEvent` ve `verifyEntitlementNow`
      export'larının `runWith`/`defineSecret` bağlamasında bu iki secret'ın listeli
      olduğu teyit edilmiş (deploy sırasında Firebase eksik secret için dağıtımı
      REDDEDER, ama önceden görmek daha güvenli).

---

## 2) Remote Config — Zorla Güncelleme (`min_required_version`)

`ForceUpdateUseCase`, istemci sürümünü Remote Config'teki `min_required_version`
parametresiyle karşılaştırır. Bu parametre YOKSA veya boşsa güvenli varsayılan
davranış "zorla güncelleme YOK" olacak şekilde kodlanmıştır — yani parametre
oluşturulmadan bu özellik SESSİZCE devre dışıdır.

- [ ] Firebase Console → Remote Config → yeni parametre: `min_required_version`
      (tip: **String**), değer: mevcut production `versionName` (ör. `"1.4.0"`).
- [ ] **Yayınla (Publish changes)** — taslak (draft) durumda kalan bir değer
      istemciler tarafından ASLA görülmez.
- [ ] Remote Config'in fetch aralığı/cache TTL'i kontrol edilmiş — acil bir zorla
      güncelleme senaryosunda (ör. kritik güvenlik açığı) TTL'in çok uzun olması
      yayılımı geciktirir.
- [ ] Bir sonraki sürüm yükseltmesinde bu parametrenin GÜNCELLENMESİ süreç
      olarak (ör. sürüm yükseltme PR şablonuna madde olarak) not edilmiş.

---

## 3) Google Play Developer API — Servis Hesabı Bağlama

RevenueCat'in Play Store satın alımlarını doğrulayabilmesi VE Play RTDN'in
çalışabilmesi için Play Console, bir GCP servis hesabına API erişimi vermelidir.

- [ ] Play Console → Kurulum (Setup) → API erişimi (API access) → GCP projesi
      BAĞLANMIŞ.
- [ ] O GCP projesinde bir servis hesabı oluşturulmuş (ör.
      `revenuecat-play-integration@<proje>.iam.gserviceaccount.com`), JSON anahtarı
      indirilmiş.
- [ ] Play Console → API erişimi → bu servis hesabına **Finansal veriler,
      sipariş yönetimi ve uygulama yönetimi izinleri** (Finance) rolü verilmiş
      — RevenueCat'in abonelik durumu/iade sorgulayabilmesi için ZORUNLU.
- [ ] JSON anahtarı RevenueCat dashboard → Play Store bağlantısı ayarına
      yüklenmiş ve "Bağlantıyı test et" YEŞİL.
- [ ] Servis hesabı anahtarının rotasyon takvimi (ör. 12 ay) bir hatırlatıcıya
      bağlanmış — süresi dolan anahtar tüm satın alma doğrulamasını SESSİZCE
      durdurabilir.

---

## 4) Play RTDN (Real-time Developer Notifications) — Pub/Sub

- [ ] GCP Console → Pub/Sub → yeni konu (topic) oluşturulmuş, ör.
      `play-rtdn-mook`.
- [ ] Bu konuya `google-play-developer-notifications@system.gserviceaccount.com`
      hesabına **Pub/Sub Publisher** rolü verilmiş (Play'in bildirim
      gönderebilmesi için ZORUNLU IAM izni).
- [ ] Play Console → Monetizasyon kurulumu → Real-time developer notifications →
      yukarıdaki konunun TAM kaynak adı (`projects/<proje>/topics/play-rtdn-mook`)
      girilmiş ve kaydedilmiş.
- [ ] Konuya bir Pub/Sub aboneliği (subscription) bağlanmış — ya doğrudan bir
      Cloud Function tetikleyicisi (`onMessagePublished`) ya da RevenueCat'in
      KENDİ dinleyicisi (RevenueCat, Play RTDN'i doğrudan da dinleyebilir;
      hangi yol seçildiyse SADECE O aktif olmalı, ikisi birden ÇİFT işlemeye
      yol açar).
- [ ] Play Console → "Test bildirimi gönder" ile uçtan uca doğrulanmış; Cloud
      Functions logunda (Turkçe log satırlarıyla) bildirimin ulaştığı görülmüş.

---

## 5) Google Payments / Play Faturalandırma Profili

- [ ] Play Console hesabına bağlı bir **Google Payments merchant profili**
      aktif ve onaylı (vergi bilgileri, banka hesabı dahil) — bu OLMADAN
      Play Console gerçek para ile satın almayı hiçbir şekilde işlemez.
- [ ] Uygulama içi ürünler/abonelikler (`mook_premium:monthly-v1` vb.) Play
      Console → Monetizasyon → Ürünler altında **Aktif** durumda VE RevenueCat
      dashboard'undaki paket kimlikleriyle (`productIdentifier`) BİREBİR
      eşleşiyor — `BillingConfig`'teki tier eşlemesiyle çapraz kontrol edilmiş.
- [ ] Test satın alımı (bir lisanslı test hesabıyla) uçtan uca yapılmış:
      satın alma → RevenueCat webhook → `customers/{uid}` güncellemesi →
      istemcide `EntitlementState.isResolved == true` gözlemlenmiş.

---

## 6) AdMob Onay Süreci

- [ ] `app-ads.txt` dosyası, uygulamanın Play Store listelemesindeki geliştirici
      web sitesinde YAYINDA (AdMob'un "Ödeme yayıncıları koruma" gereksinimi;
      eksikse doldurma oranı ciddi biçimde düşer).
- [ ] AdMob hesabı → Uygulamalar → `com.mcclabs.mook` → Play Console
      listelemesiyle EŞLEŞTİRİLMİŞ (App ads.txt durumu "Bulundu/Yetkili"
      gösteriyor).
- [ ] Reklam birimleri (rewarded, interstitial vb.) **canlı (live)** modda —
      test reklam birimi kimlikleri production build'e SIZMAMIŞ (`libs.versions.toml`
      / `AdMobConsentManager` yapılandırması kontrol edilmiş).
- [ ] Play Console → Uygulama içeriği → Reklamlar bildirimi "Evet, uygulama
      reklam gösteriyor" olarak işaretli (aksi halde Play Store incelemesi
      uygulamayı REDDEDEBİLİR).
- [ ] UMP (User Messaging Platform) rıza formu AdMob → Gizlilik ve mesajlaşma
      altında AB/İngiltere/ABD (varsa) bölgeleri için yapılandırılmış ve test
      cihazında gerçek (canlı) formla doğrulanmış.
- [ ] AdMob hesap durumu "Onaylı/Aktif" — yeni hesaplarda ilk ödeme eşiğine
      kadar bir inceleme SÜRECİ olabilir, bu süre boyunca doldurma oranı
      düşük görünebilir; panik NEDENİ değildir.

---

## 7) Play Console — 14 Günlük Kapalı Test Zorunluluğu

Play Console, YENİ (daha önce production'a çıkmamış) uygulamalar için
production erişimine geçmeden önce ZORUNLU bir kapalı test (closed testing)
süreci ister. Bu proje için:

- [ ] Kapalı test kanalı (Closed testing → bir "track", ör. Alpha) oluşturulmuş.
- [ ] En AZ **12 test kullanıcısı** kanala eklenmiş ve **en az 14 GÜN kesintisiz**
      opt-in olarak katılmış (Play, bu süreyi ve kullanıcı sayısını OTOMATİK
      takip eder — manuel bir "tamamlandı" işareti YOKTUR).
- [ ] Test süresince uygulamanın kapalı kanalda EN AZ bir kez güncellendiği
      (yeni bir sürüm yüklendiği) teyit edilmiş — sürüm hiç değişmeden geçen
      14 gün bazı hesap türlerinde sayılmayabilir; Play Console'daki ilerleme
      göstergesi kontrol edilmiş.
- [ ] Play Console → Politika → Uygulama içeriği anketleri (Hedef kitle,
      Veri güvenliği formu, İçerik derecelendirmesi vb.) TAMAMLANMIŞ — bunlar
      olmadan production'a geçiş başvurusu Play tarafından REDDEDİLİR.
- [ ] 14 günlük süre TAMAMLANDIKTAN sonra Play Console "Production'a yükselt"
      seçeneğini gösteriyor mu diye kontrol edilmiş (bazen bir sonraki iş
      gününe kadar gecikebilir).
- [ ] **Not**: Bu şart hesap/uygulama bazlıdır — eğer bu Play Console hesabında
      `com.mcclabs.mook` daha önce HİÇ production'a çıkmadıysa geçerlidir; daha
      önce çıkmış ve sürüm güncelleniyorsa bu adım GEÇERSİZDİR, atlanabilir.

---

## 8) Dağıtım Sırası (Deploy Sequencing)

Bu faz birden fazla birbirine bağımlı parça içerdiğinden ÖNERİLEN sıra:

1. [ ] `firestore.rules` deploy edilmiş (`processed_revenuecat_events` için
       yeni deny-all kuralı DAHİL) — `firebase deploy --only firestore:rules`.
2. [ ] Cloud Functions deploy edilmiş — `firebase deploy --only functions`
       (Bölüm 1'deki secret'lar TANIMLI olmadan bu adım `onRevenueCatEvent` ve
       `verifyEntitlementNow` için BAŞARISIZ olur).
3. [ ] Remote Config `min_required_version` yayınlanmış (Bölüm 2) — Android
       build'i yayınlanmadan ÖNCE yapılırsa henüz kimseyi etkilemez, güvenli
       sıradır.
4. [ ] Android build (yeni `versionCode`/`versionName`, Play Core `app-update`
       bağımlılığı DAHİL) Play Console'a yüklenmiş.
5. [ ] `migrateExistingUsers` **YALNIZCA BİR KEZ**, admin özel talebi taşıyan
       bir hesaptan, `nextAfterUid` `null` DÖNENE kadar sayfa sayfa çağrılmış
       (tek bir çağrı TÜM kullanıcıları KAPSAMAZ — `PAGE_SIZE=250` sayfalı
       devam ettirilebilir bir tasarımdır). Sonuç logu (`scanned`,
       `publicProfilesWritten`, `timeZonesDefaulted`, `usageBackfilled`)
       her sayfa için KAYDEDİLMİŞ.
6. [ ] Migrasyon TAMAMLANDIKTAN sonra `getDiscoverFeed`'in `public_profiles`
       koleksiyonuna bağımlılığı doğrulanmış (migrasyon öncesi eski/eksik
       `public_profiles` belgeleri boş Discover sonuçlarına yol açabilir).

---

## 9) Dağıtım Sonrası Doğrulama (Smoke Test)

- [ ] **Zorla Güncelleme**: `min_required_version`i geçici olarak yüksek bir
      değere ayarlayıp (ör. `"99.0.0"`) eski bir build'de `ForceUpdateScreen`in
      göründüğü, `AppNavGraph`in HİÇ compose edilmediği doğrulanmış; SONRA
      değer gerçek sürüme geri alınmış.
- [ ] **Webhook idempotency**: RevenueCat dashboard'unun "Test webhook"
      özelliğiyle AYNI event ID iki kez gönderilmiş; `processed_revenuecat_events/{eventId}`
      belgesinin YALNIZCA BİR kez `"processed"`e geçtiği ve ikinci teslimatın
      200 ile ama entitlement'ı TEKRAR İŞLEMEDEN döndüğü Functions loglarından
      doğrulanmış.
- [ ] **RevenueCat REST fallback**: Bir test hesabında Auth token claim'i
      manuel olarak eski bırakılıp (ör. token yenilemeden önce satın alma
      yapılıp) `verifyEntitlementNow`in tetiklendiği ve doğru tier'i döndüğü
      loglardan izlenmiş.
- [ ] **Discover feed**: Free Roam kısıtlaması (PREMIUM olmayan bir hesapla oda
      dışına çıkma denemesi reddediliyor mu), Boost sıralaması (boost'lu bir
      profilin öne geldiği) ve Incognito filtrelemesi (incognito bir profilin
      hiç dönmediği) production'da MANUEL olarak test edilmiş.
- [ ] Crashlytics/Analytics panelinde dağıtımdan sonraki ilk 2 saatte anormal
      bir çökme/hata artışı YOK.

---

## 10) Geri Alma (Rollback) Planı

- [ ] Remote Config: `min_required_version`i ÖNCEKİ (zararsız) değere geri
      almak TEK BAŞINA yeterlidir — istemci tarafı ANINDA etkisiz hale gelir,
      yeni bir build gerekmez.
- [ ] Cloud Functions: `firebase functions:log` ile hatalı fonksiyon tespit
      edilip `firebase deploy --only functions:<fonksiyonAdı>` ile ÖNCEKİ
      commit'ten yeniden dağıtılabilir; `processed_revenuecat_events` ve
      `customers` koleksiyonlarındaki veri YAPISI bu fazda DEĞİŞMEDİĞİNDEN geri
      alma veri kaybına yol AÇMAZ.
- [ ] `migrateExistingUsers` geri alınamaz (yalnızca EKLEME/varsayılan atama
      yapar, mevcut veriyi SİLMEZ) — bu nedenle rollback planı GEREKTİRMEZ,
      ama tekrar çalıştırmak da (idempotent olduğundan) GÜVENLİDİR.
- [ ] Android: sorunlu sürüm Play Console'da "Sürümü durdur (Halt rollout)"
      ile durdurulup önceki APK/AAB'nin aşamalı yayılımı (staged rollout)
      devam ettirilebilir.

---

## 11) App Check — Play Integrity Zorunluluğu (Faz 6, Gereksinim 2)

Android istemcisi artık başlangıçta (`MookApplication.onCreate()`) bir Play Integrity
App Check sağlayıcısı kuruyor ve bir dizi `onCall` fonksiyonu artık
`enforceAppCheck: true` taşıyor (bkz. `monetization.ts`, `discoverFeed.ts`,
`moderation.ts`, `deleteAccount.ts`; `subscriptionVerification.ts`deki
`verifyEntitlementNow` ayrıca `consumeAppCheckToken: true` ile tek-kullanımlık jeton
tüketir). Bu adım YANLIŞ sırayla yapılırsa PRODUCTION kullanıcılarını KİLİTLEYEBİLİR.

- [ ] Firebase Console → App Check → `com.mcclabs.mook` uygulaması için **Play
      Integrity** sağlayıcısı kayıtlı (SHA-256 imza parmak izi production keystore'la
      EŞLEŞİYOR).
- [ ] **Dağıtım SIRASI kritik**: ÖNCE bu App Check istemci kurulumunu (bu faz) içeren
      Android build'i Play Console'da (aşamalı yayılım) YETERİNCE yaygınlaştır; App
      Check jetonu olmadan istek atan eski sürümler `enforceAppCheck: true` bir
      fonksiyona çarparsa REDDEDİLİR.
- [ ] Yukarıdaki madde doğrulandıktan SONRA Cloud Functions'ı bu faz koduyla deploy et
      (`enforceAppCheck: true` sunucu tarafında aktif olur).
- [ ] Firestore Console → bu veritabanı için App Check **Enforce** modu — `public_profiles`
      kuralındaki `request.app != null` koşulunun ANLAMLI olması için bu konsol
      ayarının da açılması GEREKİR (bkz. `firestore.rules`deki not). Enforce modu
      AÇILMADAN önce Metrics sekmesinde bir süre "monitor" modunda izlenip mevcut
      istemcilerin App Check jetonu taşıdığı DOĞRULANMALIDIR.
- [ ] `migrateExistingUsers` (bölüm 1'deki tek seferlik göç) App Check kapsamının
      KASITLI istisnasıdır — bkz. o dosyadaki not; bu, bu bölümdeki adımları ETKİLEMEZ.
- [ ] `admobRewardedSsv` webhook'u App Check kapsamının DIŞINDADIR (bkz. o dosyadaki
      not) — bütünlüğü zaten Google'ın ECDSA imza doğrulamasıyla sağlanır.
- [ ] Dağıtım sonrası: Firebase Console → App Check → Metrics'te "reddedilen istek"
      oranı beklenmedik şekilde YÜKSEK DEĞİL (yüksekse muhtemelen eski bir istemci
      sürümü ya da yanlış SHA-256 parmak izi kaydı anlamına gelir).

---

## 12) Veri İhracı & Toptan Silme (Faz 6, Gereksinim 5 — KVKK Madde 11)

- [ ] `firestore-send-email` (Trigger Email) uzantısı bu Firebase projesinde KURULU —
      `exportUserData` indirme bağlantısını `mail` koleksiyonuna yazarak gönderir; uzantı
      kurulu DEĞİLSE istek "başarılı" döner ama e-posta ASLA gitmez (sessiz başarısızlık).
- [ ] `GA4_PROPERTY_ID` ortam değişkeni `.env.<project-id>` dosyasına eklendi (ör.
      `GA4_PROPERTY_ID=properties/123456789`) — BOŞ bırakılırsa Analytics silme isteği
      sessizce ATLANIR (hesap silmenin GERİ KALANINI ENGELLEMEZ, ama KVKK Madde 11
      kapsamındaki Analytics silme yükümlülüğü fiilen YERİNE GETİRİLMEZ).
- [ ] Analytics Admin API (`analyticsadmin.googleapis.com`) bu GCP projesinde ETKİN VE
      Cloud Functions çalışma zamanı hizmet hesabının `analytics.edit` kapsamına ERİŞİMİ
      VAR (Application Default Credentials üzerinden — ayrı bir servis hesabı anahtarı
      GEREKMEZ).
- [ ] Cloud Storage bucket'ında `exports/` öneki (prefix) altındaki nesneler için ömür
      döngüsü (lifecycle) kuralı DEĞERLENDİRİLDİ — imzalı URL'ler 7 gün sonra süre DOLAR
      ama dosyanın KENDİSİ silinmez; isteğe bağlı bir otomatik temizlik kuralı önerilir.
- [ ] Android build'i, oturum açan HER kullanıcı için `Firebase.analytics.setUserId(uid)`
      çağıran koda (bkz. `App.kt`) sahip sürüme YÜKSELTİLDİ — bu YAPILMADAN GA4 silme
      isteği doğru kullanıcıyı HEDEFLEYEMEZ (bkz. `deleteAccount.ts`deki
      `requestAnalyticsDataDeletion` KDoc'u).

## 13) Destek Araçları — Promosyonel Hak Bağışı (Faz 6, Gereksinim 7)

- [ ] `grantPromotionalEntitlement` YALNIZCA güvenilir destek/yönetici hesaplarına
      `admin: true` özel Firebase Auth claim'i MANUEL olarak verildikten SONRA
      kullanılabilir (`admin.auth().setCustomUserClaims(uid, { admin: true })`) —
      `moderateUser`/`bootstrapMonetization` ile AYNI, bu fonksiyonun KAPSAMI DIŞINDAKİ
      manuel adım.
- [ ] `support_grants` koleksiyonu Firebase Console'da PERİYODİK olarak (ör. haftalık)
      gözden geçiriliyor — beklenmedik/açıklanamayan bağışlar SIZMIŞ bir `admin` claim'i
      İŞARET EDEBİLİR.
- [ ] Google Cloud Monitoring'de `grantPromotionalEntitlement` için log tabanlı bir
      hata-oranı metriği (bkz. fonksiyonun KDoc'undaki kavramsal %5 eşiği) OLUŞTURULDU.


## Ek: İlgili Dosyalar (Referans)

- `functions/src/revenuecatWebhook.ts` — idempotency + entitlement senkronizasyonu
- `functions/src/subscriptionVerification.ts` — `verifyEntitlementNow` (REST fallback)
- `functions/src/migrateExistingUsers.ts` — tek seferlik geriye dönük göç
- `functions/src/discoverFeed.ts` — sunucu taraflı Discover akışı
- `shared/.../domain/update/ForceUpdateUseCase.kt` — Zorla Güncelleme mantığı
- `shared/.../domain/billing/EntitlementFallbackVerifier.kt` — istemci fallback kararı
- `firestore.rules` — `processed_revenuecat_events`, `customers`, `public_profiles` erişim kuralları
- `shared/.../appcheck/AppCheckInstaller.kt` — Play Integrity App Check kurulumu (Android)
- `shared/.../data/appcheck/PlatformLimitedUseAppCheckCallableInvoker.android.kt` — tek kullanımlık jeton çağrı yolu
- `functions/src/exportUserData.ts` — KVKK Madde 11 veri ihracı (Faz 6, Gereksinim 5)
- `functions/src/customerSupport.ts` — promosyonel hak bağışı + denetim izi (Faz 6, Gereksinim 7)
- `shared/.../domain/privacy/ExportUserDataUseCase.kt` / `domain/support/CustomerSupportUseCase.kt`

## Analitik, Remote Config ve KVKK silme (2026-10-01)

- [ ] `firebase deploy --only functions,firestore:indexes` — `deleteAccount` yeni silme şemasıyla
      (`functions/src/deletionSchema.ts`) ve `messages.senderUid` koleksiyon-grubu dizini.
- [ ] Firebase Console → Remote Config'e reklam parametrelerini ekle (varsayılanlar uygulamadakiyle aynı):
      `ads_enabled`=true, `ads_interstitial_enabled`=true, `ads_native_enabled`=true,
      `ads_rewarded_enabled`=true, `interstitial_min_interval_seconds`=90, `interstitial_session_cap`=6,
      `interstitial_daily_cap`=20, `interstitial_actions_between_ads`=3, `native_ad_card_interval`=10,
      `rewarded_ad_frequency_limit`=4. Acil durumda `ads_enabled`=false → tüm reklamlar en geç ~30 dk içinde kapanır.
- [ ] GA4'te özel boyutları kaydet: `placement`, `ad_type`, `feature`, `outcome`, `limit_reason`, `sku`,
      `target_tier`, `change_type`, `from_tier`, `to_tier`, `direction`, `reward_type`, `verified`, `step`.
- [ ] Çeviri: `python3 scripts/l10n/sync_missing_translations.py --check` — `needs-translation` blokları
      profesyonel çeviriyle değiştirildikçe sayaç düşer.

## Premium ayrıcalıkları ve dinamik Paywall (2026-10-01)

- [ ] `firebase deploy --only functions,firestore:rules` — Keşfet önceliği (Boost → Premium → Standart),
      `public_profiles.subscriptionTier`, `read_receipts` kuralları (yalnızca Premium okur).
- [ ] RevenueCat panelinde "default" teklifin **Metadata** alanına rozet yapılandırmasını ekle; eklenmezse
      HİÇBİR pakette "En Popüler" rozeti görünmez:
      `{ "most_popular_tier": "standard" }` veya belirli bir paket için `{ "most_popular_package": "$rc_annual" }`.
- [ ] Standart ürüne Play Console'da 3 günlük ücretsiz deneme teklifi tanımlı olmalı (limit sayfasındaki
      "Standart'ı 3 gün ücretsiz dene" yalnızca mağaza + cihaz uygunluğu doğrulanınca görünür).
- [ ] Mevcut aboneler için `users.subscriptionTier` ilk RevenueCat senkronizasyonunda yazılır (bir sonraki
      webhook olayı veya `verifyEntitlementNow`); Premium rozeti/Keşfet önceliği o ana kadar görünmeyebilir.
