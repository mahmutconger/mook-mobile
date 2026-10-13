# Tema sistemi ve doğrulama

`ThemeRepositoryImpl`, cihazın tercihini `mook_theme/theme_preference` anahtarıyla yerelde saklar. Android'de SharedPreferences, iOS'ta NSUserDefaults kullanılır. Tercih oturumdan bağımsızdır; çevrimdışı ve giriş yapılmadan değiştirilebilir. Depo `StateFlow` yayınlar; `ThemeViewModel` kullanıcı olaylarını alır ve `ThemeUiState` sunar. Kök `MookTheme` bu durumu yaşam döngüsüne bağlı olarak gözlemler. İlk yerel okuma tamamlanmadan ekran ağacı çizilmez.

`System` ve `Ocean` cihazın açık/koyu görünümünü izler. `Light` ve `Dark` cihaz görünümünden bağımsızdır. Okyanus özel paleti, standart Material rollerini aynı bileşenlerde kullanarak genişletme örneği sağlar. Yeni bir tema eklemek için `ThemePreference` içine kararlı bir depolama anahtarı ve `themePalette` içine palet eklenir; ekranların değiştirilmesi gerekmez.

Tercih disk yazması beklenmeden yayınlanır. Yazmalar sıralanır, bekleyen eski seçimler atlanır. Yazma başarısız olursa yalnızca hâlâ etkin olan seçim son kaydedilmiş değere döner ve seçimi yapan ekranda Türkçe hata gösterilir. Yerel tercih bulunmayan eski kurulumlarda hesaptaki `isDarkMode` bir kez taşınır; ağ isteği sırasında yapılan yeni seçim bu geçiş tarafından ezilmez.

## Renk kuralları

Standart bileşenlerde `MaterialTheme.colorScheme` kullanılır. `primary/onPrimary`, `secondary/onSecondary`, `error/onError`, `inverseSurface/inverseOnSurface` gibi eşleşmeler birlikte uygulanır. Tipografi palet rengi içermez; metin bulunduğu yüzeyin içerik rengini devralır.

Degrade, Premium rozeti, fotoğraf perdesi ve parıltı gibi özel roller `LocalAppColors` üzerinden sağlanır. Degrade üzerinde `onGradient`, altın rozet üzerinde `onPremium`, fotoğraf perdesi üzerinde `onPhoto` kullanılır. Fotoğraf kontrolleri, ekranın açık/koyu paletinden bağımsız içerik eşleşmesine sahiptir. Ham palet renkleri yalnızca `ui/theme/Color.kt` içinde tanımlanır. Şeffaflık gibi çizim değerleri bileşenlerde kullanılabilir.

`ApplyPlatformTheme`, Android sistem çubuğu simgelerini ve pencere arka planını etkin palete göre günceller. Kenardan kenara görünümde çubuklar saydamdır; kök `AppScaffold` etkileşimli içeriği güvenli alana yerleştirir ve sistem alanlarında tema yüzeyini gösterir. Sistem boşluğu yönetimi `edge-to-edge.md` belgesinde açıklanır. iOS'ta açık/koyu zorlaması güncellenir; sistem ve Okyanus tercihlerinde zorlama kaldırılır. Yerel Android reklam görünümü de `AndroidView.update` üzerinden yeniden oluşturulmadan yeni Material paletine geçer.

## Otomatik testler

```sh
./gradlew :shared:testAndroidHostTest --tests 'com.mcclabs.mook.theme.*' --no-configuration-cache
./gradlew :shared:compileKotlinIosSimulatorArm64 :androidApp:assembleDebug --no-configuration-cache
```

Doğrulama komutlarında yapılandırma önbelleği kapatılır. Bu çalışma sırasında önbellek açıkken paylaşımlı sınıf JAR dosyasının boş üretildiği bir derleme görüldü; önbelleksiz yeniden üretim bu durumu giderdi.

`ThemeViewModelTest`, başlangıç yüklemesini, iki gözlemcinin aynı seçime geçmesini, yeniden oluşturulan depoda kalıcılığı, hızlı seçimleri, kayıt/okuma hatalarını, bilinmeyen değerleri ve eski tercih geçişini doğrular.

`ThemePaletteTest`, bütün tema ve sistem görünümü birleşimlerinde içerik eşleşmeleri için en az 4,5:1 kontrastı kontrol eder. Bu ölçüm düz, opak renk çiftlerine aittir; fotoğraf üzerindeki perde ve alfa bileşimleri cihaz kontrolünde ayrıca değerlendirilir.

`ThemeSwitchingUiTest`, Robolectric üzerinde gerçek Compose ağacında tema seçimine tıklar; ortak metin ve ana düğme renklerinin değiştiğini, yazılmış form içeriğinin korunduğunu, sistem görünümüne tepki verildiğini ve iç içe temaların kardeş bileşenleri etkilemediğini doğrular. Seçili oda kartı ve fotoğrafsız profil kartı üzerindeki degrade içerik renkleri de bütün paletlerde kontrol edilir. `SemanticColorUsageTest`, tema paketi dışındaki üretim kaynaklarına sabit renk eklenmesini engeller.

Yeni bir ekran için mevcut UI testindeki şu yaklaşım uygulanabilir:

```kotlin
// Seçimden sonra gerçek metin yerleşiminin rengi ve form içeriği kontrol edilir.
compose.onNodeWithTag("themeOptionDark").performClick().assertIsSelected()
compose.onNodeWithTag("draft").assertTextContains("Yürüyüş taslağı")
```

## Cihaz üzerinde kontrol

1. Android'de açık ve koyu sistem görünümünde bütün tema seçeneklerini seçin. Sistem varsayılanını seçtikten sonra uygulama açıkken cihaz görünümünü değiştirin. Okyanus paletinde de aynı geçişi doğrulayın; açık/koyu sabit seçimlerinin cihaz değişiminden etkilenmediğini kontrol edin.
2. Bir metin alanına taslak yazın, tema seçin ve geri dönün. Gezinme konumu, kaydırma ve taslak korunmalıdır. Uygulamayı kapatıp yeniden açın; son tercih ağ bağlantısı olmadan da yüklenmelidir.
3. Keşfet, beğeniler, sizi beğenenler, sohbet listesi, sohbet, profil, profil düzenleme, filtreler, oda seçimi, ayarlar, abonelik, giriş/kayıt ve izin ekranlarını kontrol edin. İletişim kutularını, alt sayfaları, yükleme ve hata durumlarını da açın.
4. Açık ve koyu fotoğraflar üzerinde kart metinlerini, kilit ve menü simgelerini; Premium rozeti, seçili sekmeleri, ana düğmeleri ve yıkıcı eylemleri kontrol edin. Yerel reklam yüklüyken tema değiştirin; reklamın yüzeyi, açıklamaları ve eylem düğmesi aynı palete geçmelidir.
5. Android API 24, 29 ve 35/36 üzerinde hareket ve üç düğmeli gezinmeyle durum/gezinme çubuklarının simgelerini kontrol edin. iOS cihaz veya simülatörde açık, koyu ve sistem varsayılanı arasında geçiş yaparak durum çubuğunu ve sistem görünümüne dönüşü doğrulayın.

Cihaz adımları bu belgedeki manuel doğrulama yönergeleridir; otomatik testlerin çalıştırılması bu kontrollerin yapıldığı anlamına gelmez.
