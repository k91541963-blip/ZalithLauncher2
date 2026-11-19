# AetherLite Launcher — Yol Haritası ve Durum

> Zalith Launcher 2 (GPL-3.0) tabanlı, düşük/orta segment Android cihazlarda
> Minecraft: Java Edition çalıştırmayı hedefleyen fork.
> Bu belge **kanıtlanmış** durumu anlatır; test edilmemiş hiçbir şey "tamamlandı" sayılmaz.

## 1. Yasal Durum

| Konu | Durum |
|---|---|
| Lisans | GPL-3.0 (`LICENSE` korunuyor) |
| Atıf | README, hakkında ekranı ve kaynak başlıkları MovTery + katkıda bulunanları anar |
| Marka | "Zalith" adı/logo kullanılmıyor; paket kimliği `com.aetherlite.launcher` |
| Değişiklik kaydı | Commit geçmişi + bu belge |
| Üçüncü taraf | `library/_Libraries.kt` ve `res/raw/*license*` dosyaları korunuyor |

## 2. Mimari

- Kotlin + Jetpack Compose, Gradle 9.5, AGP 9.x, Hilt/KSP
- Modüller: `ZalithLauncher`, `LWJGL`, `Terracotta`, `InputMap`, `CardGrid`,
  `ColorPicker`, `LayerController`, `PetLib`, `Guide`
- Başlatma çekirdeği: PojavLauncher tabanlı `jni/` + LWJGL
- Renderer yolu: `RendererInterface` → `GameLauncher.setRendererEnv` → `pojavexec`
  → `ctxbridges/gl_bridge.c` → GL4ES / Zink / VirGL
- Yeni: `jni/aetherrender/` → `gl_bridge.c` içinden çağrılan EGL/tile-GPU katmanı

## 3. Özellik Durumu

| Şartname maddesi | Durum | Kanıt / sınır |
|---|---|---|
| Gerçek Minecraft başlatma | **VAR** (miras) | Pojav çekirdeği, JRE/LWJGL bileşenleri |
| Microsoft + offline hesap | **VAR** (miras) | `game/account/` |
| Fabric/Forge/NeoForge/Quilt | **VAR** (miras) | `fabriclike`, `forgelike` |
| Mod/resource pack/shader/dünya yönetimi | **VAR** (miras) | ilgili ekranlar |
| Mod çakışma/bağımlılık denetimi | **VAR** | Fabric/Quilt/Forge/NeoForge ayrıştırıcıları + testler |
| Modpack içe aktarma | **VAR** (miras) | `ModpackImporter`; dışa aktarma yok |
| RAM/JVM/Java sürüm yönetimi | **VAR** (miras) | ayarlar + `multirt` |
| Log + çökme raporu + analiz | **VAR** | `LogAnalyzer`, çökme ekranı bandı |
| FPS/CPU/RAM göstergesi | **VAR** (miras) | oyun içi FPS grafiği; CPU/RAM launcher panelinde |
| Cihaz/GPU bilgisi | **VAR (yeni)** | `GpuInfo.kt` gerçek EGL pbuffer sorgusu |
| Sıcaklık | **KISMİ (yeni)** | batarya + okunabilen thermal zone; her cihazda yok |
| Performans profilleri | **VAR** | Lite/Balanced/Quality, RAM/JVM/çözünürlük |
| Renderer ön kontrolü | **VAR** | `RendererCompatibility` + rozet |
| **AetherRender** | **VAR 3.0 (yeni)** | NG-GL4ES çekirdeği (26.3'e kadar), beş EGL modu, gerçek çekirdek ön ayarları (batching/VBO/16-bit doku), `eglSwapInterval` VSync, Mali modeline göre öneri, telemetri |
| **Canlı Performans Merkezi** | **VAR (yeni)** | CPU/RAM/PSS/heap/GPU/ısı; 2 sn'de bir, yalnız görünürken |
| **Ana ekran performans kartı** | **VAR (yeni)** | 5 sn'de bir özet; dokununca Performans Merkezi |
| **Aether paletleri** | **VAR 3.0 (yeni)** | Aether Blue, Purple Nebula, Emerald, Crimson, Midnight, Midnight AMOLED + Titan Dark, Cyber Purple, Arctic Blue, Minimal |
| Oyun içi FPS/%1 low/frametime | **VAR 2.1 (yeni ölçüm)** | AetherMetrics takas zaman damgaları; oyun içi canlı overlay + oturum sonrası özet |
| Düşük donanım arayüz modu | **VAR** | animasyon azaltma anahtarı |
| Modpack dışa aktarma | **YOK** | planlı |
| Oyun dosyası doğrulama | **KISMİ** | indirme bütünlüğü var; tam tarama yok |
| Otomatik performans profili | **KISMİ** | öneri var; oyun içi otomatik geçiş yok |
| Vulkan backend | **YOK (bilinçli)** | yalnızca mevcut Zink/VirGL yolları; uydurma backend yok |

## 4. Aşamalar

- **Aşama 1 — Kod tabanı analizi:** ✅
- **Aşama 2 — Fork kimliği:** ✅ AetherLite adı, `com.aetherlite.launcher`, yeni adaptive +
  monochrome ikon, GPL atıf
- **Aşama 3 — Performans çekirdeği:** ✅ donanım profili, RAM önerisi, profiller,
  renderer uyumluluğu, düşük donanım UI
- **Aşama 4 — Teşhis + modlar:** ✅ log analizi, çökme nedeni, dört yükleyici için
  bağımlılık denetimi
- **Aşama 5 — AetherRender v1:** ✅ EGL katmanı, GPU/termal paneli, birim testleri
- **Aşama 5b — AetherLite 2.0 dilimi:** ✅ canlı Performans Merkezi, ana ekran
  performans kartı, altı Aether paleti, AetherRender 2.0 modları (80 birim testi)
- **Aşama 5c — AetherMetrics + oturum raporu:** ✅ gerçek kare süresi halkası,
  %1 low/P99/jank hesabı, son oturum kartı (yerel takas ölçümü)
- **Aşama 5d — AetherRender 2.1 (26.3 desteği):** ✅ NG-GL4ES çekirdeği, GLES 3
  bağlamı/uyumluluk denetimi, `26.3-snapshot-3` sürüm aralığı (96 birim testi,
  APK yayınlandı); cihazda 26.x başlatma doğrulaması hâlâ Aşama 6'da
- **Aşama 6 — Cihaz doğrulaması:** ⏳ Mali-G57/G68/G76/G78 için
  [`DEVICE_TEST_PLAN.md`](DEVICE_TEST_PLAN.md) uygulanacak
- **Aşama 7 — Dağıtım:** ⏳ imzalı release + sürüm notları

## 5. Doğrulanmış Derlemeler

| Commit | İçerik | Sonuç |
|---|---|---|
| `9f50cde` | performans çekirdeği | ✅ CI |
| `22dc028` | log analiz motoru | ✅ CI |
| `498acbe` | mod bağımlılık denetimi | ✅ CI |
| `cb6c8da` | hesap değiştirme, yedekleme, sağlık kontrolü | ✅ CI |
| `6f4df7d` | AetherLite kimliği + AetherRender v1 | ✅ CI |
| `da256b5` | AetherRender/derleme/cihaz testi belgeleri | ✅ CI |
| `61a49cf` | test beklentileri + `eglSurfaceAttrib_p` bağlayıcı düzeltmesi | ✅ CI, APK yayınlandı |
| `48d7919` | Performans Merkezi + Aether paletleri + AetherRender 2.0 modları | ✅ CI, 80/80 test, APK yayınlandı |
| `26c7bf3` | AetherMetrics kare süresi ölçümü + son oturum raporu | ✅ CI, 93/93 test, APK yayınlandı |
| `be17262` | AetherRender 2.1: NG-GL4ES çekirdeği, GLES 3 bağlamı, 26.3 aralığı | ✅ CI (44047256866) |
| `c17409f` | `LIBGL_ES` türetme düzeltmesi + belge doğrulaması | ✅ CI, 96/96 test, APK yayınlandı |
| `0dd1a45` | AetherRender 3 çekirdek ön ayarları + VSync + Mali profilleri + Titan temaları | ✅ CI (44261608248 kaynak hatası: yinelenen dize) |
| `de55df2` | Yinelenen dize düzeltmesi | ✅ CI 44262769558, 109/109 test, APK yayınlandı |
| `a96dfc1` | Aşama 3C: çökme kurtarma + sürüm başına son çalışan renderer | ✅ CI 44268954010, 120/120 test, APK yayınlandı |
| `92f7b60` | Aşama 3B: ana ekran kahraman kartı + son dünya/oturum + widget galerisi | ✅ CI 44271299158, 133/133 test, APK yayınlandı |
| `848eacf` + `dc03735` | Aşama 3D: oturum geçmişi, karşılaştırma, öneri danışmanı | ✅ CI 44274517130, 163/163 test |

Ayrıntılar, APK boyutu/SHA-256 ve test sayıları: [`TEST_REPORT.md`](TEST_REPORT.md).

## 6. Bilinen Kısıtlar

- Bu geliştirme ortamında Android SDK/NDK yok; derlemeler GitHub Actions'ta yapılır.
- `26.3-snapshot-3` aralığı **beyan edilmiştir, cihazda doğrulanmamıştır**: bu ortamda
  çalışan bir Mali cihaz/Minecraft kurulumu yok. NG-GL4ES çekirdeği ve GLES 3 bağlamı
  derleme, birim testi ve APK sembol kontrolüyle doğrulandı; gerçek 26.2/26.3 başlatma
  testi Aşama 6'ya bağlıdır.
- **Henüz gerçek Mali cihazda oyun içi ölçüm yapılmadı.** AetherRender'ın EGL seçimi
  ve tampon politikası kod olarak doğrulanabilir, ancak FPS/frametime iddiası için
  cihaz testi zorunludur.
- NG-GL4ES ("Krypton Wrapper") `libng_gl4es.so` `NG-GL4ES-release.aar` içinden
  APK'ya paketleniyor. AetherRender 2.1 artık aynı NG-GL4ES çekirdeğini kullanır ve
  `26.3-snapshot-3` sürümüne kadar destek beyan eder; eski `libgl4es_114.so` yolu
  yalnızca ayrı **GL4ES** renderer'ında (en fazla 1.21.4) kalır.
- AetherRender artık GLES 3 bağlamı ister; yalnızca GLES 2 destekleyen cihazlarda
  uyumluluk denetimi renderer'ı "desteklenmiyor" olarak işaretler.
- Chunk seviyesinde renderer değişikliği, asenkron chunk hazırlama ve draw call
  birleştirme yapılmadı; bunlar Minecraft/GL4ES içinde ve güvenli değil.
- AetherTouch markalaması ve tam Bedrock benzeri düzen henüz uygulanmadı; mevcut
  Zalith kontrol sistemi çalışıyor.
- Modüler Gradle ayrımı (`launcher-core`, `launcher-ui`, …) henüz yapılmadı;
  mevcut çalışan modül yapısı korunuyor.
- Vulkan/Zink yolu yalnızca gerçek Mesa sürücüsü olan cihazlarda anlamlıdır.

## 7. AetherLite 3.0 — TITAN Programı

3.0 spec'i tek seferde "yapıldı" ilan edilemeyecek kadar büyük (14 modül, yeni
ana ekran, overlay, plugin API). Program aşamalara bölündü; her aşama CI'da derlenip
APK olarak doğrulanır. **Yalnızca tamamlanan aşama ✅ işaretlenir.**

| Aşama | İçerik | Durum |
|---|---|---|
| **3A** | AetherRender 3 çekirdek ön ayarları (batching/VBO/16-bit doku), gerçek VSync, Mali modeline göre öneri kartı; AetherUI 3.0 tema motoru (Titan Dark, Cyber Purple, Arctic Blue, Minimal) | ✅ CI 44262769558, 109/109 test |
| **3B** | Ana ekran 3.0: büyük karakter kartı, seçili sürüm/modpack/son dünya, Play, son oturumlar; widget ekle/kaldır/taşı/boyutlandır + düzen kaydı | ✅ CI 44271299158, 133/133 test |
| **3C** | AetherRender 3 derinlik: çökme sonrası otomatik geri dönüş, sürüm başına son çalışan renderer, seçilen EGL config dökümü | ✅ CI 44268954010, 120/120 test |
| **3D** | Performans AI: ölçüme dayalı bulgular (CPU/GPU darboğazı, bellek artışı, shader takılması) + kullanıcı onaylı uygulama; oturum geçmişi ve karşılaştırma | ✅ CI 44274517130, 163/163 test |
| **3E** | Mod Hub: güncelleme denetimi, uyumsuz mod uyarısı, `.mrpack` dışa aktarma; World Manager: toplu yedek/geri yükleme, içe/dışa aktarma | ⏳ |
| **3F** | Overlay 3.0 (taşı/boyutlandır/şeffaflık, panel seçimi) ve AetherTouch 3.0 (profil içe/dışa aktarma, tek elle düzen) | ⏳ |
| **3G** | Skin Studio, Java Manager yenilemesi, Download Center kuyruğu, Notification Center | ⏳ |
| **3H** | Plugin API (izin modeli, bilinmeyen eklenti güvenilmez) + modüler Gradle ayrımı | ⏳ |
| **3I** | Cihaz testi ve 2.0 ↔ 3.0 karşılaştırma raporu (aynı cihaz/dünya/ayarlar) | ⏳ |

3A'da bilinçli olarak yapılmayanlar: chunk renderer'a dokunulmadı, draw call
birleştirme/chunk mesh cache iddiası yok (bunlar NG-GL4ES içinde ve cihazda
doğrulanmadan beyan edilemez), 16-bit renk zorlanmadı.

3B'de eklenenler:

- **Kahraman kartı** (sistem kartı, ana ekranın en üstü): gerçek hesap adı + yüzü,
  seçili sürüm ve yükleyici özeti (`VersionInfo.getInfoString()`), o sürümün
  `saves/` klasöründeki **son dünya** (`level.dat` olan en yeni klasör) ve
  AetherMetrics'in yazdığı **son oturum** (sürüm, ölçülmüş FPS, süre).
- Play düğmesi yalnızca **hesap ve sürüm birlikte** varken etkin. Eksik olan şey
  yazıyla söylenir ("Hesap ekle" / "Sürüm seç") ve yazıya dokunmak ilgili ekranı
  açar. Sahte "hazır" durumu veya uydurma FPS yok; kare örneği yoksa FPS satırı
  hiç gösterilmez.
- Telefon ve tablet: kart 520 dp altında alt alta, üstünde yan yana düzen kullanır.
- **Widget galerisi**: sürüm kartı (yüklü ve geçerli sürümlerden) ve sunucu kartı
  (ad + adres) ekler. `VersionCardManager`/`ServerCardManager`'ın gerçek sonucu
  gösterilir; çift kayıt reddedilir, geçersiz adres kabul edilmez.
- Ekleme girişi her zaman görünen "+" düğmesi; düzenleme modundaki araç çubuğunda
  da ayrıca var. Taşıma/boyutlandırma/kaldırma ve düzen kaydı mevcut
  `cardgrid` + `HomeGridStore` yolunu kullanır (3B bunu değiştirmedi, doğruladı).

3B'de bilinçli olarak yapılmayanlar: modpack adı ayrı bir alan olarak gösterilmez
(sürüm özeti yükleyiciyi ve MC sürümünü içerir; modpack meta verisi güvenilir
biçimde okunamıyor), oturum **geçmişi** yoktur (tek `last_session.json` var,
geçmiş 3D'de), dünya adı NBT'den değil klasör adından gelir.

3C'de eklenenler:

- Başlatma öncesi `filesDir/renderer_recovery/pending.properties` yazılır; JVM
  çıkış kodu 0 ise deneme silinir ve sürüm için "son çalışan renderer" kaydedilir.
- Sıfırdan farklı çıkış kodu ya da hiç raporlanmayan çıkış (native çökme/LMK) bir
  sonraki açılışta işlenir: zincir **AetherRender → Krypton Wrapper (NG-GL4ES) →
  GL4ES → dur**. GLES 2 cihazda AetherRender doğrudan GL4ES'e düşer.
- Zincir sonunda hiçbir şey zorlanmaz; kullanıcıya "daha güvenli renderer kalmadı"
  bilgisi gösterilir ve aynı çökme iki kez işlenmez.
- Renderer ayarlarında kurtarma kartı yalnızca gerçek dosyalar varsa görünür; olay
  kapatılabilir ve kullanıcı tek dokunuşla AetherRender'a dönebilir.
- Seçilen EGL config'in tüm alanları (rgb/alpha/depth/samples/visual) zaten
  `EGL config: ...` satırıyla loglanıyor; 3C bunu değiştirmedi, yalnızca belgeledi.

Cihaz doğrulaması yapılmadı: gerçek bir native çökme üretilip zincirin cihazda
çalıştığı gösterilmedi. Birim testleri karar tablosunu ve dosya yaşam döngüsünü
doğrular; bu, cihaz kanıtı yerine geçmez.

3D'de eklenenler:

- **Oturum geçmişi** (`SessionHistoryStore`): `last_session.json` korunur, ayrıca en
  fazla 20 özet giriş tutulur. Girişler FPS kovaları içermez (dosya şişmesin diye);
  sürüm, süre, p50/p95/%1 low/jank, CPU/GPU/RAM ve renderer adı saklanır. Bozuk dosya
  okunamazsa geçmiş boş kabul edilir, başlatma durmaz.
- **Karşılaştırma** (`SessionComparison`): iki oturumu aynı metrikler üzerinden
  kıyaslar; ölçülmemiş alan "ölçülmedi" olarak kalır, uydurma değer üretilmez.
- **Danışman** (`PerformanceAdvisor`): yalnızca ölçülen sinyallerden (CPU baskın, GPU
  baskın, bellek artışı, shader derleme takılması) bulgu çıkarır ve yalnızca **var
  olan** ayarları önerir (`AetherTuning.THROUGHPUT`/`MEMORY_SAVER`, VSync kapatma).
  Hiçbir öneri otomatik uygulanmaz; kullanıcı onayı gerekir. FPS artışı iddia edilmez.
- Performans Merkezi geçmişi ve karşılaştırmayı gösterir. Cihazda oyun içi ölçümle
  doğrulanmadı; birim testleri (23 yeni test) mantığı doğrular.

### 3.0.1 — Bildirilen üç hatanın düzeltmesi

1. **"Yetersiz bellek" / oyuna girememe**: `LaunchLogClassifier` `latest.log`
   kuyruğunda (son 256 KB) OOM izlerini arar. OOM artık renderer hatası sayılmaz:
   `RecoveryReason.MEMORY` olayı yazılır, renderer değiştirilmez ve heap önerisi
   gösterilir. Başlatma öncesinde `LaunchHealthCheck`, mod sayısına göre taban uygular
   (1 mod → 2048 MB, ≥10 → 2560 MB, ≥30 → 3072 MB) ve cihazın güvenli üst sınırını
   aşmaz; kullanıcı "N MB yap ve başlat" veya "Yine de başlat" seçer.
2. **Renderer'ın kendiliğinden GL4ES'e geçmesi**: kurtarma akışı artık hiçbir koşulda
   renderer ayarını yazmaz. Sıfırdan farklı çıkışta yalnızca öneri üretilir; uygulama
   açılışında kalan deneme `UNKNOWN` olarak raporlanır. Geçiş, ayarlardaki kurtarma
   kartından kullanıcı onayıyla yapılır.
3. **AetherRender'da beyaz dünya**: pencere için EGL config artık **RGB888 + alpha 8 +
   depth 24 + stencil 8** olmak zorunda (Minecraft'ın "Fabulous" geçişi stencil ister).
   RGB565, alfasız ve şablonsuz istek yolları kaldırıldı; sürücü stencil sunmuyorsa bir
   kez şablonsuz denenir, o da yoksa stok yola dönülür. Bu nedenle gerçek etkisi yalnızca
   o hatalı yüzey olan **Lite** ve **Performance** modları kaldırıldı; eski
   `AETHER_RENDER_MODE=lite|performance` değerleri `balanced` olarak yorumlanır.

Bu üç düzeltme derleme ve birim testleriyle doğrulanabilir; **Mali cihazda yeniden
üretim testi yapılmadı**. Beyaz dünya sürerse `adb logcat -s AetherRender` çıktısındaki
`EGL config: ... stencil=...` satırı ve `latest.log` kuyruğu gerekir.

## 8. Sonraki Adımlar (öncelik sırası)

1. Mali cihaz test matrisini çalıştır, `TEST_REPORT.md`'yi ölçümlerle doldur.
2. 3.0.1 düzeltmelerini aynı cihazda doğrula: 50 modlu 26.2 kurulumunda OOM guard'ı,
   beyaz dünya için `EGL config` stencil satırı ve renderer'ın kendiliğinden
   değişmediği.
3. Oturum geçmişi + karşılaştırma (3D) ve kahraman kartındaki son oturum satırını
   geçmişle besle.
4. Oyun içi overlay'e `%1 low` ve P99 kare süresi satırlarını ekle (AetherMetrics
   aynı örnekleri sağlar; canlı grafik zaten var).
5. Modpack dışa aktarma ve oyun dosyası tam doğrulama.
6. AetherTouch markalaması ve Bedrock benzeri düzen seçeneği.
7. Modüler Gradle ayrımı (`launcher-core`, `launcher-performance`, `launcher-renderer`,
   …) — çalışan davranışı bozmadan kademeli.
8. İmzalı release hattı ve sürüm notları.
