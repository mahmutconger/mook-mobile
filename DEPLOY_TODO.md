# WalkMatch — Yayın Yapılacaklar Listesi

> Sıra önemli. Adımları yukarıdan aşağıya uygula; bir aşama bitmeden sonrakine geçme.
> Firestore kuralları (`firestore:rules`) EN SON deploy edilir — aşama 6'daki nedene bak.

---

## Aşama 0 — Ön kontrol

- [ ] `functions/` içinde testler yeşil: `npm run test:unit` ve `npm run test:rules`
- [ ] `./gradlew :shared:testAndroidHostTest :androidApp:compileDebugKotlin` yeşil
- [ ] Firebase projesinin doğru seçili olduğunu kontrol et: `firebase use`

## Aşama 1 — Secret'lar (fonksiyon deploy'undan ÖNCE)

Secret eksikse fonksiyon deploy'u başarısız olur.

- [ ] Yeni secret'ı tanımla (en az 16 karakter, rastgele):
      `firebase functions:secrets:set LIKED_ME_TOKEN_SECRET`
      → Bu olmadan "Beni Beğenenler" listesi ve kilit açma çalışmaz.
- [ ] Var olduğunu doğrula: `firebase functions:secrets:access REVENUECAT_API_KEY`
      → Webhook artık bunu da kullanıyor.
- [ ] Var olduğunu doğrula: `firebase functions:secrets:access RC_WEBHOOK_SIGNING_SECRET`

## Aşama 2 — Konsol ayarları (kod deploy'u gerektirmez, şimdi yapılabilir)

### RevenueCat
- [ ] "default" teklifin **Metadata** alanına ekle: `{"most_popular_tier":"standard"}`
      → Eklenmezse hiçbir pakette "En Popüler" rozeti görünmez.
      (Belirli bir paket için: `{"most_popular_package":"$rc_annual"}`)

### Google Play Console
- [x] `mook_standard` temel planına **7 günlük ücretsiz deneme** teklifi tanımlandı (teklif kimliği `try`)
      → Uygulama süreyi Play'den okur; limit ekranında "Standart'ı 7 gün ücretsiz dene" görünür.

### Firebase Remote Config (elle ekle — şablonu dosyadan deploy ETME, mevcut anahtarların üzerine yazar)
- [ ] `ads_enabled` = true
- [ ] `ads_interstitial_enabled` = true
- [ ] `ads_native_enabled` = true
- [ ] `ads_rewarded_enabled` = true
- [ ] `interstitial_min_interval_seconds` = 90
- [ ] `interstitial_session_cap` = 6
- [ ] `interstitial_daily_cap` = 20
- [ ] `interstitial_actions_between_ads` = 3
- [ ] `native_ad_card_interval` = 10
- [ ] `rewarded_ad_frequency_limit` = 4

### Google Analytics 4 (özel boyutlar)
> Mülk: `walktalk-1123f` — Mülk kimliği **557224613** (hesap 410481851, Firebase'e 2026-10-03'te yeniden bağlandı).
> `functions/.env.walktalk-1123f` içinde `GA4_PROPERTY_ID=557224613` tanımlı.
- [x] `198992547952-compute@developer.gserviceaccount.com` → Mülk erişim yönetimi → **Düzenleyici** (hesap silmede GA4 veri silme)
- [ ] Şu parametreleri özel boyut olarak kaydet:
      `placement`, `ad_type`, `feature`, `outcome`, `limit_reason`, `sku`, `target_tier`,
      `change_type`, `from_tier`, `to_tier`, `direction`, `reward_type`, `verified`, `step`

## Aşama 3 — Firestore dizinleri

Dizinlerin oluşması dakikalar sürebilir; fonksiyonlardan önce başlat.

- [ ] `firebase deploy --only firestore:indexes`
      → `interactions` (Beni Beğenenler listesi) ve `messages.senderUid` (hesap silmede herkese açık oda mesajları)
- [ ] Firebase Console → Firestore → Dizinler: hepsi **Etkin** olana kadar bekle

## Aşama 4 — Cloud Functions

- [x] Google Cloud'da **Cloud Scheduler API** etkin olmalı (yeni zamanlanmış görev `refreshPublicProfileAges` için)
- [x] `firebase deploy --only functions` (2026-10-03, 44 fonksiyon ACTIVE)
      → Kota hatası alınırsa: `BATCH_SIZE=2 PAUSE_SECONDS=180 ./scripts/deploy_functions_batched.sh`
      → Silme sorusunda eski WalkTalk fonksiyonları için her zaman **N**
      → `activateMookProfile` da dahil tüm fonksiyonlar (webhook, getLikedMe, deleteAccount, sendMessage,
        `onReportCreated`, `refreshPublicProfileAges` …)
- [ ] Deploy'dan hemen sonra yaş göçünü elle tetikle (6 saat beklememek için):
      `gcloud scheduler jobs run firebase-schedule-refreshPublicProfileAges-us-central1 --location=us-central1`
      → Herkese açık profillerdeki `birthDateMillis` silinir, yerine `age` yazılır.
        Tekrar çalıştırmak güvenlidir; iş bitince göç sorgusu boş döner.
- [ ] `config/plans` belgesi Firestore'da TAM olmalı: `node scripts/seed_plan_catalog.js`
      (sunucu varsayılanlarıyla doldurur; App Check / admin talebi gerektirmez, tekrar çalıştırmak güvenli).
      → Uygulama artık plan sınırlarını bu belgeden okur.
- [ ] Eski uygulama sürümleri yeni sunucuyla sorunsuz çalışır; burada bekleme gerekmez.

## Aşama 5 — Uygulama sürümü

- [ ] Yeni Android sürümünü yayınla (Play Console)
- [ ] Sürüm kullanıcılara ulaşana kadar bekle (kademeli yayın kullanıyorsan %100'e çıkar)

> Bu aşamada okundu bilgisi ("Görüldü") henüz çalışmaz — kurallar aşama 6'da gelir. Hata vermez, sadece görünmez.

## Aşama 6 — Zorunlu güncelleme + Firestore kuralları

- [ ] Remote Config'te `min_required_version`'ı aşama 5'teki sürüme yükselt
      → **Neden:** Eski sürüm girişte `isMookActive` yazıyor ve bu yazma `try` bloğunun içinde.
        Yeni kurallar bu yazmayı reddeder; eski sürümde giriş tamamen başarısız olur.
- [ ] Ancak bundan SONRA: `firebase deploy --only firestore:rules`
      → `isMookActive` koruması, `read_receipts` (okundu bilgisi), `subscriptionTier` koruması,
        `abuse_flags` ve `private_profile_meta` (istemciye tamamen kapalı)

> ⚠️ Son konuşmada "functions,firestore:rules" birlikte deploy etme önerisi vardı — onu **uygulama**.
> Kurallar her zaman bu aşamada, zorunlu güncellemeden sonra gider.

## Aşama 7 — Yayın sonrası doğrulama

- [ ] Yeni sürümle giriş yap → Keşfet'te profil görünüyor (`activateMookProfile` çalışıyor)
- [ ] Test satın alma → `customers/{uid}.syncedTier` doğru, Paywall'da "En Popüler" rozeti görünüyor
- [ ] Limit sayfasında geri sayım ve (Ücretsiz kullanıcıda) deneme seçeneği görünüyor
- [ ] "Beni Beğenenler" ekranı açılıyor, kilit açma çalışıyor
- [ ] Premium hesapla sohbette "Görüldü" görünüyor
- [ ] Test hesabını sil → Firestore'da kullanıcıya ait belge kalmadığını kontrol et
- [ ] Firestore'da rastgele bir `public_profiles` belgesinde `birthDateMillis` OLMADIĞINI, `age` olduğunu kontrol et
- [ ] Bir test hesabını başka hesaplarla şikâyet et → `abuse_flags/{uid}` oluştu, puan arttı
- [ ] Konsoldan `config/plans` içindeki bir değeri değiştir → uygulamayı yeniden aç → yeni sınır geçerli
- [ ] Ertelenmiş düşürme (Standart → Ekonomik) yap → Ayarlar'da "… tarihinde Ekonomik pakete geçilecek" görünüyor
- [ ] Remote Config'te `ads_enabled=false` yapıp reklamların ~30 dk içinde kapandığını doğrula, sonra geri aç

## Bilinen durumlar / sonraki işler

- [ ] **Mevcut aboneler:** Kademe bilgisi profile ilk RevenueCat senkronizasyonunda yazılır. Mevcut Premium
      kullanıcılarda rozet ve Keşfet önceliği bir sonraki RevenueCat olayına kadar (en geç yenilemede)
      görünmeyebilir. İstenirse tek seferlik doldurma script'i yazılacak.
- [ ] **Çeviriler:** Diğer dillerde `needs-translation` blokları İngilizce yedek. Çevrildikçe kontrol:
      `python3 scripts/l10n/sync_missing_translations.py --check`
- [ ] **Beni Beğenenler gizliliği:** Kilitli profil fotoğrafının adresi istemciye geliyor (bulanıklık telefonda).
      Sunucuda bulanık küçük resim üretimi sonraki adım.
