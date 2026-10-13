# İyimser arayüz mimarisi

Android'deki beğenme işlemi, ağ yanıtını beklemeden kalbi doldurur. Keşfet ekranındaki dolu kalbe tekrar basmak beğeniyi geri çeker; Beğendiklerim ekranındaki dolu kalp satırı hemen gizler. Profil ayrıntısında beğenme düğmesi anında «Beğenildi» olur; eşleşme/gezinme ve reklamlar sunucu onayından sonra çalışır.

## Katmanlar

- `OptimisticStateController<State, Key>` sunum katmanındadır. Firebase ve iş kuralları içermez. ViewModel'in `MutableStateFlow` durumunu anında günceller, `viewModelScope` içinde senkronizasyonu başlatır, hatada yalnızca ilgili alanları geri alır.
- `SetLikeUseCase`, domain katmanında istenen yönü ve sunucunun sonucunu doğrular. Yanlış yön, hata veya çevrimdışı kuyruk sonucu başarı sayılmaz. On saniyelik zaman aşımı normal hata olarak geri alma akışına girer; üst kapsamın iptali kullanıcı hatasına çevrilmez.
- `LikeRepository`, domain sözleşmesidir. `FirebaseLikeRepository`, veri katmanında mevcut `swipe` ve yeni `unlike` callable'larını çağırır. Coroutine iptali ve ağ/yetki hataları üst katmana iletilir.
- Compose, `collectAsStateWithLifecycle` ile durumu okur. Kanal üzerinden gelen tek kullanımlık olaylar yalnızca ekran `STARTED` durumundayken tüketilir. Ekran arka plandayken oluşan hata geri dönüşte bir kez gösterilir; tüketilmiş hata yeniden yayınlanmaz.

## Eşzamanlılık ve geri alma

Yardımcı ve kullanıcı niyetleri ekranın ana iş parçacığında çalışmalıdır. Aynı anahtarın ikinci işlemi devam eden istek tamamlanana kadar reddedilir; bağımsız anahtarlar aynı anda ilerleyebilir. Keşfet ekranı mevcut kaydırma/kota davranışını korumak için işlemleri ekran genelinde sırayla kabul eder. Beğendiklerim ekranı farklı satırlardaki geri çekmeleri bağımsız yürütür.

`rollback(current, previous)`, önceki durumun tamamını geri yazmamalıdır. Yalnızca işleme ait alanları güncel duruma uygulamak, başka satırların başarısını ve yeni profil/görsel durumlarını korur. Beğendiklerim, gizlenen satırları onay gelene kadar kaynak listede tutar; geri alma bu nedenle ilk sıralamayı korur. İşlem sürümleri, eşzamanlı/geç gelen liste ve kota yanıtlarının daha yeni yerel işlemi ezmesini engeller.

ViewModel temizlendiğinde coroutine iptal edilir, yerel değişiklik geri alınır ve hata bildirimi üretilmez. Ağ onayından sonraki reklam veya gezinme işlemi, onaylanmış beğeniyi geri aldırmaz.

## Başka işlemlere uygulama

Takip veya kaydetme için domain repository sözleşmesi ve use case eklenir; ViewModel aynı yardımcıyı kullanır:

```kotlin
optimisticState.execute(
    key = itemId,
    optimistic = { it.copy(savedIds = it.savedIds + itemId) },
    rollback = { current, _ -> current.copy(savedIds = current.savedIds - itemId) },
    sync = { saveItemUseCase(itemId) },
    onFailure = {
        events.trySend(SavedEvent.ShowSnackbar("Öğe kaydedilemedi. Lütfen tekrar deneyin."))
    },
)
```

Örnek yeni bir kaydı eklemek içindir. Bir aç/kapat işlemi önceki ilgili değeri yakalamalı ve geri almada o değeri kullanmalıdır. Devam eden işlem göstergeleri gerekiyorsa `optimistic` içinde eklenip `settle` içinde temizlenebilir. Yan etkiler reducer içinde çalıştırılmamalıdır.

## Firebase ve ürün davranışı

`unlike`, kimliği doğrulanmış ve yasaklanmamış kullanıcının kendi beğenisini ve varsa karşılıklı eşleşmesini tek Firestore transaction içinde siler. Tekrar çağırmak güvenlidir; geçme kayıtları, karşı tarafın beğenisi ve sohbet geçmişi korunur. Günlük/toplam beğeni tüketimi iade edilmez; yeniden beğenme mevcut sunucu kota kurallarından geçer. Firestore istemci yazım kuralları gevşetilmez ve App Check açık kalır.

Yeni akış, beğeniyi çevrimdışı kuyruğa eklemez: başarısız isteği geri alır ve kullanıcıya tekrar deneme olanağı sunar. Eski oturumlardan kalan kaydırma kuyruğunun kurtarma akışı korunur. Zaman aşımı veya iptal, sunucuda tamamlanmış bir yazımı geri alamaz; sonraki yükleme sunucu durumuyla uzlaşır. Tekrar beğenme/geri çekme çağrıları sunucuda aynı duruma güvenle uygulanabilir. Bu yardımcı uygulama kapandıktan sonra çalışan kalıcı iş zamanlayıcısı değildir.

Yeni Android sürümünü kullanmadan önce `functions` derlenmeli ve `unlike` Firebase'e dağıtılmalıdır. Bu çalışma yerel kod ve emülatör doğrulamasını içerir; üretim dağıtımı içermez.

## Doğrulama

```sh
./gradlew :shared:testAndroidHostTest --tests 'com.mcclabs.mook.optimistic.*' --tests 'com.mcclabs.mook.billing.LikeInterstitialTransitionOrderTest' --tests 'com.mcclabs.mook.loading.ProgressiveViewModelTest'
npm --prefix functions run build
firebase emulators:exec --only firestore --project demo-walkmatch-optimistic "node --test functions/test/unlike.emulator.test.js"
```

Testler anlık durum, başarı, hata/geri alma, tek kullanımlık bildirim, yinelenen dokunuş, bağımsız işlemler, eski yanıtlar, yeniden deneme, zaman aşımı ve lifecycle iptalini kapsar. Firestore emülatörü ayrıca atomik silme, idempotent tekrar, yetkilendirme, yasaklı kullanıcı ve korunması gereken belgeleri doğrular. Callable taşıma katmanındaki App Check doğrulaması yerine gerçek handler ve Firestore transaction sınanır.
