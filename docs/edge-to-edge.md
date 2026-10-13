# Sistem boşlukları ve kenardan kenara yerleşim

Uygulama kökü `MookTheme → AppScaffold → AppNavGraph` sırasıyla kurulur. Askıya alınmış hesap ve zorunlu güncelleme ekranları da aynı `AppScaffold` içinde çalışır. Tema renkleri bütün pencereyi doldurur; etkileşimli ekran ağacı güvenli alanın içine yerleştirilir.

`MainActivity.onCreate()` içindeki `enableEdgeToEdge()` modern Android sürümlerinde saydam sistem çubuklarını etkinleştirir. `ApplyPlatformTheme` açık/koyu tema için sistem simgelerinin görünümünü günceller. Manifestteki `adjustResize`, eski Android sürümlerinde de klavye boşluğunun iletilmesini sağlar.

## Tek boşluk sahibi

`AppScaffold`, Material 3 `Scaffold` üzerinde `contentWindowInsets = WindowInsets.safeDrawing` kullanır. Durum çubuğu, gezinme çubuğu, ekran kesikleri ve klavye bu güvenli alanın parçasıdır. Klavye ile alt gezinme boşluğu üst üste geldiğinde yükseklikleri toplanmaz; gereken büyük boşluk ayrılır.

Scaffold'ın verdiği `PaddingValues`, içerik kapsayıcısında `Modifier.scaffoldPadding(innerPadding)` ile uygulanır. Bu yardımcı hem `padding` hem de `consumeWindowInsets` kullanır. Böylece alt ekranlardaki `TopAppBar`, `Scaffold`, `statusBarsPadding`, `navigationBarsPadding` ve `imePadding` aynı sistem boşluğunu ikinci kez eklemez.

İç ekran Scaffold'larında da `Modifier.scaffoldPadding(padding)` kullanılır. Bu ikinci boşluk ekranın kendi üst/alt araç çubuklarına aittir; sistem boşlukları kökte zaten tüketilmiştir. Yeni ekranlarda sistem çubuğu için sabit yükseklik veya ek dış padding kullanılmamalıdır.

```kotlin
// Uygulama kökü güvenli alanı bütün ekranlara uygular.
MookTheme {
    AppScaffold {
        AppNavGraph()
    }
}

// Ekranın kendi Scaffold boşlukları uygulanır ve alt bileşenler için tüketilir.
Scaffold(topBar = { TopAppBar(title = { Text("Örnek ekran") }) }) { padding ->
    Box(Modifier.scaffoldPadding(padding).fillMaxSize()) {
        Text("Ekran içeriği")
    }
}
```

`BottomNavBar`, `WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)` üzerinden henüz tüketilmeyen boşluğu ayırır. Kök altında aynı boşluğu tekrarlamaz; tek başına kullanıldığında da gezinme çubuğunu, yan boşlukları ve klavyeyi dikkate alır. Sekmeler eşit genişlik kullanır; uzun etiketler tek satırda kısaltılır. Her sekme seçili durumunu erişilebilirlik sistemine bildirir.

Pencereyi ayrıca kaplayan iletişim kutuları ve modal alt sayfalar kendi pencere boşluklarını yönetmeye devam eder. Bu bileşenlerin inset işlemleri, normal ekran kökündeki tüketimle karıştırılmamalıdır.

## Otomatik doğrulama

```sh
./gradlew :shared:testAndroidHostTest --tests 'com.mcclabs.mook.layout.*' --tests 'com.mcclabs.mook.theme.*' --no-configuration-cache
./gradlew :shared:compileKotlinIosSimulatorArm64 :androidApp:assembleDebug --no-configuration-cache
```

`AppInsetsUiTest`, `ComposeTestRule` ve Robolectric üzerinde gerçek Material Scaffold ile gerçek `BottomNavBar` bileşenini çalıştırır. Altı test şu durumları doğrular:

- Hareketli ve üç düğmeli gezinme için farklı alt boşluklar; üst araç çubuğu, içerik ve sekmelerin güvenli sınırlar içinde kalması.
- Klavyenin açılıp kapanması, alt boşlukların toplanmaması ve önceki yerleşimin geri gelmesi.
- Yatay kesik veya yan gezinme boşlukları varken beş sekmenin de görünür ve tıklanabilir olması.
- Alt gezinmenin ortak kök dışında kullanıldığında kendi boşluklarını ayırması.
- Kökte tüketilen boşlukların eski inset modifier'ları tarafından tekrar uygulanmaması.
- Android API 35 üzerinde `ViewCompat.dispatchApplyWindowInsets` ile gerçek Android inset olaylarının, kökün varsayılan `WindowInsets.safeDrawing` yoluna ve klavye geçişlerine aktarılması.

Testler yalnızca öğelerin varlığını kontrol etmez; semantik ağaçtaki gerçek yerleşim sınırlarını ölçer, güvenli alanla karşılaştırır ve üst eylem, alt eylem ile bütün sekmelere tıklar. Tema regresyon testleri de aynı çalıştırmaya dahil edilir.

## Cihaz üzerinde kontrol

Android API 24, 29 ve 35/36 üzerinde hareketli ve üç düğmeli gezinmeyi kontrol edin. Portre, yatay görünüm, ekran kesikleri ve bölünmüş ekran boyutlarında üst araç çubuğu ile alt gezinme öğelerinin sistem alanına taşmadığını doğrulayın. Sohbet, kayıt, giriş ve profil düzenleme ekranlarında klavyeyi açıp kapatın; gönderme/kaydetme düğmelerinin klavyenin üstünde kaldığını ve kapanıştan sonra yerleşimin eski haline döndüğünü kontrol edin. Modal alt sayfalar ve iletişim kutularında da aynı görünürlük kontrolünü yapın.

Bu son adımlar manuel cihaz doğrulaması içindir; Robolectric testlerinin geçmesi fiziksel cihaz kontrolünün yapıldığı anlamına gelmez.
