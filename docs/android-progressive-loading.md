# Android kademeli yükleme

Keşfet, Beğendiklerim, Beni Beğenenler, Sohbetler ve kendi/ziyaret edilen profil ekranlarında ilk içerik yüklemesi yerleşime uygun iskeletlerle gösterilir. Başlıklar, geri düğmeleri ve mevcut gezinme yapısı kullanılabilir kalır. Çekerek yenileme sırasında eldeki içerik korunur.

Ekranlar ortak Compose kaynaklarında bulunduğundan bileşenler burada tutulur. `progressiveLoadingEnabled` yalnızca Android'de etkindir; iOS mevcut görsel ve ilk yükleme gösterimini kullanır. iOS kaynakları ve bağımlılıkları değiştirilmez.

## Durum akışı

Repository sözleşmeleri Domain modellerini ve görsel URL'lerini döndürür; görsel baytlarını indirmez. ViewModel veriyi yayımlar, Compose görsel yüklemesini başlatır ve Coil sonucu `onImageLoadChanged` olayıyla ViewModel'e iletilir. `loadingPhase` mevcut ekran durumundan türetilir:

1. `Loading`: İlk metin verisi beklenir, ekran iskeleti gösterilir.
2. `ImagesLoading`: Metin görünür; ilgili görsellerde bağımsız shimmer sürer.
3. `Success`: Bekleyen görsel kalmaz. Hatalı görseller `imageLoading.failedUrls` içinde ayrıca tutulur ve yedek görünüm gösterir.
4. `Error`: İlk içerik yüklemesi başarısızdır; ekranın hata görünümü kullanılır.

Kota, öğretici ve ilişki sorguları metinlerin görünmesini geciktirmez. Profil eylemleri, ilişki/kota bilgileri hazır olduğunda açılır. Sohbet mesajları kişi bilgilerinden önce görünür; her kişinin adı ve fotoğraf URL'si kendi sorgusu tamamlanınca eklenir. Yeni sohbet bildirimi önceki listeye ait tamamlanmamış profil sorgularını iptal eder.

## Bileşenler ve önbellek

`LoadingSkeleton` ekran türüne göre iki sütunlu profil kartları, dairesel avatarlarla liste satırları veya profil fotoğrafı/ayrıntı düzenini oluşturur. İskeletteki parçalar tek animasyon saatini paylaşır. `Modifier.shimmer` animasyon değerini çizim aşamasında okur; her karede kompozisyonu yeniden çalıştırmaz.

`ProgressiveAsyncImage` mevcut Coil yükleyicisini ve bellek/disk önbelleğini kullanır. İstek başarılı olduğunda yalnızca o görselin shimmer'ı kalkar. Eksik veya hatalı URL için animasyon sürdürülmez. Model değişince önceki görselin yerel durumu yeni modele taşınmaz. Kilitli beğenen fotoğraflarının küçük çözümleme boyutu ve bulanıklığı korunur.

Galeri sayfaları ve liste satırları görünür oldukça yüklenir. Ekranın `imagesLoading` alanı görünmeyen görsel URL'lerini de içerebilir; bu alan metinleri veya kullanıcı gezinmesini engellemez. Aynı URL'nin başka bir bileşende yüklenmesi, tamamlanmış başarı durumunu geriye düşürmez. Liste değişikliklerinde eski URL'ler çıkarılır, kalan URL'lerin sonuçları korunur.

## Doğrulama

Android JVM testleri:

```sh
./gradlew :shared:testAndroidHostTest
```

Yalnızca yükleme testleri:

```sh
./gradlew :shared:testAndroidHostTest --tests '*Progressive*Test'
```

APK derlemesi:

```sh
./gradlew :androidApp:assembleDebug
```

`ProgressiveViewModelTest`, tüm hedef ViewModel'lerde ilk yükleme → görünür metin/bekleyen görseller → tamamlanmış görseller geçişlerini, yavaş ek sorguları, boş sonuçları, iptal edilen/eski istekleri ve hataları doğrular. `ProgressiveLoadingUiTest`, Robolectric ile gerçek Compose ekranlarında iskeletin kaldırılmasını, metnin bağımsız görünmesini, görsel shimmer'ının sona ermesini, hata görünümünü, model değişimini ve Coil bellek önbelleğinin tekrar indirmeyi önlemesini doğrular. Arayüz testleri emülatör veya canlı sunucu gerektirmez.
