# Test Raporu

> Kural: yalnızca gerçekten çalıştırılan kontroller "geçti" olarak işaretlenir.
> Cihaz ölçümü yapılmadıysa "yapılmadı" yazılır.

## 1. Kapsam

- AetherLite kimliği ve paket adı
- AetherRender 3 (NG-GL4ES çekirdeği, GLES 3 bağlamı, 26.3 sürüm aralığı, mod bazlı
  EGL seçimi, yüzey politikası, gerçek çekirdek ayar ön ayarları, `eglSwapInterval`
  ile VSync, Mali modeline göre öneri, telemetri)
- AetherRender 3 çökme kurtarma: başlatma öncesi deneme kaydı, çıkış kodu 0 = başarı,
  OOM/gölge bellek hatasında `MEMORY`, raporlanmayan çıkışta `UNKNOWN`; **hiçbir
  durumda otomatik renderer değişimi yok**, yalnızca kullanıcı onaylı öneri ve sürüm
  başına "son çalışan renderer" kaydı
- 3.0.1 bellek koruması: `latest.log` kuyruğunda OOM sınıflandırma, mod sayısına göre
  heap önerisi ve yoğun mod paketlerinde başlatma öncesi uyarı
- AetherUI 3.0 ana ekran: kahraman kartı (hesap + seçili sürüm + son dünya + son
  oturum + gerçek Play koşulu), widget galerisi (sürüm/sunucu kartı ekleme)
- AetherUI 3.0 tema motoru (Titan Dark, Cyber Purple, Arctic Blue, Minimal + mevcut
  Aether paletleri, Midnight AMOLED)
- Canlı Performans Merkezi (CPU/RAM/GPU/ısı) ve ana ekran özet kartı
- AetherMetrics gerçek kare süresi ölçümü ve son oturum raporu
- Altı Aether paleti (Aether Blue, Purple Nebula, Emerald, Crimson, Midnight, AMOLED)
- GPU modeli ve sıcaklık okuma
- Mevcut performans/teşhis/mod bağımlılık mantığı (regresyon)

## 2. Birim Testleri

Komut (CI ile aynı):

```bash
./gradlew ZalithLauncher:testDebugUnitTest \
  --tests "com.movtery.zalithlauncher.game.performance.*" \
  --tests "com.movtery.zalithlauncher.game.diagnostics.*" \
  --tests "com.movtery.zalithlauncher.game.version.mod.dependency.*"
```

Sonuç: **208 test, 0 başarısız** (CI çalışması
[44695468421](https://github.com/k91541963-blip/Zlc/actions/runs/44695468421),
kaynak commit `3ff8059`; `:ZalithLauncher:testDebugUnitTest` BUILD SUCCESSFUL).
Test dosyalarındaki `@Test` sayımı 208'dir ve CI aynı filtrelerle yeşildir.

| Paket | Test sayısı | Durum |
|---|---:|---|
| `game.performance` | 175 | ✅ geçti |
| `game.diagnostics` | 11 | ✅ geçti |
| `game.version.mod.dependency` | 22 | ✅ geçti |
| **Toplam** | **208** | ✅ **geçti** |

Aşama 3C'de CI iki gerçek hata yakaladı ve ikisi de düzeltildi:
`44266581823` — TR dizelerinde kaçırılmamış kesme işareti (`aapt2`
`mergeDebugResources` hatası), `44267725095` — kurtarma testinde kalan
`storeFileRoot` referansı ve "son çalışan" kaydını yanlış sürümde tutan beklenti.
Düzeltmeler `f7ced04`, `b727757`, `a96dfc1`.

3.0.1 hata düzeltme paketinde CI yine gerçek bir hata yakaladı:
`44476561826` — `186 testten 1'i` kaldı çünkü Minecraft'ın kendi ekran metni
"has **run** out of memory"; sınıflandırıcıda yalnızca "ran out of memory"
marker'ı vardı. Her iki metin de eklendi (`175908d`) ve `44477628757` yeşil
kapandı.

3.0.2 (bellek teşhisi + 3E dünya geri yükleme) `44695468421` ile yeşil kapandı:
208/208 test, APK yayınlandı. Bu turda CI yeni bir hata yakalamadı; ancak
düzeltmenin cihazda oyunu gerçekten açtığı **gösterilmedi**.

İlk AetherLite CI çalışması (`43861719985`) iki hatalı test beklentisini ortaya çıkardı:
`parseMaliModel("Mali-T860 MP4")` çekirdek sayısını koruyor ve `classifyMali`
G68'i MID sayıyor. Beklentiler gerçek davranışa göre düzeltildi (`a12fcad`).
İkinci çalışma (`43862571445`) birim testleri geçti ancak bağlayıcı
`eglSurfaceAttrib_p` tanımının eksik olduğunu gösterdi; tanım eklendi (`61a49cf`).

Yeni eklenenler:

- `CpuUsageTest` — `/proc/stat` ayrıştırma, `iowait`/`guest` kuralı, delta, sıfırlanma
  ve tutarsız sayaç reddi (8 test)
- `GpuInfoTest` — Mali model ayrıştırma/normalizasyon, sınıflandırma, negatif durumlar
- `ThermalInfoTest` — millidegrees/ondalık ayrıştırma, aralık koruması
- `AetherRendererTest` — `opengles` öneki (native GL4ES yolu), NG-GL4ES ortamı
  (`LIBGL_GL=31`, `LIBGL_ES=3`, `LIBGL_USE_MC_COLOR`), `AETHER_RENDER_MODE`
  değerinin geçerliliği, `libng_gl4es.so`, `26.3-snapshot-3` aralığı, kimlik çakışması
- `RendererCompatibilityTest` — AetherRender Mali/Adreno davranışı, GLES 2 cihazda
  red, `26.4` sürümünde aralık dışı
- `FrameTimeStatsTest` — harmonik ortalama FPS, %1 low (en yavaş %1 kare), P99,
  jank oranı, geçersiz/1 sn üstü örnek reddi (7 test)
- `GameSessionAccumulatorTest` — oturum toplama, eksik cihaz verisinin `null`
  kalması, kare tamponu sınırı ve işaretleme, 10 sn FPS kovaları (6 test)
- `AetherTuningTest` — çekirdek varsayılanının hiç değişken yazmaması, ön ayarların
  yalnızca `LIBGL_*` anahtarları ve 0/1 değerleri kullanması, ön ayarların aynı
  değişkeni paylaşmaması (5 test)
- `AetherGpuProfilesTest` — G57/G68 bellek, G76/G78/G710/G715 verim, bilinmeyen GPU
  çekirdek varsayılanı, EGL modunun asla 16-bit'e zorlanmaması (5 test)
- `AetherThemeSeedTest` — her Aether paletinin benzersiz tohumu, Aether olmayan
  türlerde `null`, Titan tohumlarının opak olması (3 test)
- `HomeHeroTest` — Play koşulu (hesap/sürüm/ikisi), eksik olanın doğru sebep
  olarak dönmesi, kare örneği olmayan oturumda FPS'in gizlenmesi, negatif sürenin
  sıfıra çekilmesi, `level.dat` olan en yeni dünyanın seçilmesi, `level.dat`
  olmayan klasörün yok sayılması, sıralama/limit, eksik `saves` klasörü, sunucu
  adresi doğrulama (host/port kabul; boşluk, şema, baştaki/sondaki iki nokta ve
  aşırı uzunluk reddi), ad boşken adresin kullanılması (13 test)
- `RendererRecoveryTest` — öneri zinciri (AetherRender→Krypton Wrapper, GLES 2'de
  AetherRender→GL4ES, zincirin GL4ES'te bitmesi), kimlik eşlemesi, denemenin
  çözülene kadar okunması, çıkış kodu 0'da denemenin silinmesi ve son çalışanın
  yazılması, `RENDERER` olayında öneri + sayaç artışı/sıfırlanması, olayın
  kapatılması, başarısız sürümün "son çalışan" kaydını kaybetmesi, kayıt yokken
  hiçbir şey üretilmemesi, **`MEMORY` olayının renderer önermemesi ve sayaç/son
  çalışanı bozmaması**, **`UNKNOWN` (kalan deneme) olayının öneri üretmemesi**,
  **baskı türü ve cihaz sınırının yeniden açılışta korunması** (17 test)
- `MemoryRecommendationTest` — mod sayısına göre heap tabanı (1/10/30 eşikleri),
  50 modda 2048→3072 önerisi, yeterli heap'te sessiz kalma, küçük artışın
  önerilmemesi, cihaz üst sınırına kırpma, kullanılamaz üst sınırda öneri yok,
  64 MB adımına yuvarlama, `mods/` sayımının büyük/küçük harf duyarsız ve yalnız
  `.jar` olması, eksik klasörde 0, **heap'te yer varken yükseltme**, **cihaz
  sınırında "grafik düşür"**, **vanilla OOM'unda tek güvenli artış**, **GL
  baskısında asla heap önermeme** (14 test)
- `MemoryReliefTest` — render/simulation mesafesi, fabulous→fancy, gölge kapatma,
  asla yükseltmeme, ilgisiz ayarları koruma, eksik dosya/dizin ve sayısal olmayan
  değerde dosyayı bozmama (6 test)
- `LaunchLogClassifierTest` — `OutOfMemoryError`/`Java heap space`, Minecraft'ın
  "has run out of memory" ve eski "ran out of memory" metni, `GL_OUT_OF_MEMORY`,
  **heap ile GL baskısının ayrı sınıflandırılması**, sıradan hataların OOM
  sayılmaması, yalnızca son 256 KB'ın okunması (eski çalıştırmanın izinin yok
  sayılması), kısa logun tamamının okunması, eksik/dizin dosyada istisna atmama,
  sıfır pencere reddi (12 test)
- `WorldArchiveTest` — çok dünyalı yedek→geri yükleme turu, mevcut dünyayı
  silmeden `.before-restore-*` olarak saklama, zip-slip reddi, `level.dat`
  içermeyen arşivin reddi, kök `level.dat` ve `saves/` önekli arşivler, bozuk
  yedeklerin listeyi bozmaması, eksik arşiv (7 test)

## 3. Derleme

| İş | Ortam | Sonuç |
|---|---|---|
| Debug ARM64 APK | GitHub Actions (JDK 21, NDK 27, Rust) | ✅ başarılı |
| Native `pojavexec` (AetherRender 2.1 + AetherMetrics dahil) | NDK `ndkBuild` | ✅ başarılı |

APK: `AetherLite-arm64-debug.apk`

| Alan | Değer |
|---|---|
| Boyut | 216.630.464 bayt (≈206,6 MiB) |
| SHA-256 | `2a60a219a7678ca4c271602d25884bd4e4967a3680143e75458226f5e0c3b9f2` |
| Paket | `com.aetherlite.launcher.debug` |
| Uygulama adı | `AetherLite Launcher` |
| İndirme | https://github.com/k91541963-blip/Zlc/releases/download/apk-latest/AetherLite-arm64-debug.apk |

APK içeriği doğrulandı:

- `lib/arm64-v8a/libpojavexec.so` içinde `aether_render_choose_config`,
  `aether_render_on_surface_created`, `aether_render_on_context_created`,
  `AETHER_RENDER`, `AETHER_RENDER_MODE`, `AetherRender`, `EGL_BUFFER_DESTROYED`,
  `EGL config: mode=%s rgb=%d/%d/%d alpha=%d depth=%d stencil=%d ...`,
  `No stencil config for mode=%s, retrying without stencil` ve
  `No config passed validation for mode=%s, using stock attributes` dizeleri mevcut.
  `RGB565` dizesi **yok** → 16-bit yolu derlemeden kaldırılmış.
- Aynı kitaplıkta `aether_metrics_on_frame`, `aether_metrics_copy`,
  `aether_metrics_reset`, `Java_org_lwjgl_glfw_CallbackBridge_getFrameTimeSamples`
  ve `..._resetFrameTimeSamples` sembolleri mevcut (gerçek kare süresi ölçümü).
- DEX içinde `PerformanceCenterScreen`, `PerformanceSummaryCard`,
  `AetherRenderModeCard`, `AetherRenderMode`, `CpuUsage`, `FrameTimeMetrics`,
  `GameSessionAccumulator` ve `GameSessionRecorder` sınıfları paketli; oturum
  kaydı yolu (`aethermetrics`) ve yeni `aether_perf_session_*` dizeleri mevcut.
- `lib/arm64-v8a/libng_gl4es.so` paketli; içinde `LIBGL_GL` ve
  `LIBGL_USE_MC_COLOR` değişken adları bulunuyor (2.1 çekirdeği).
- `lib/arm64-v8a/libgl4es_114.so` yalnızca ayrı **GL4ES** renderer'ı için paketli
  (regresyon değil).
- DEX içinde `opengles3_aether` ve `26.3-snapshot-3` dizeleri var; eski
  `opengles2_aether` kimliği hiç geçmiyor.
- DEX içinde `AETHER_SWAP_INTERVAL`, `aetherRenderTuning`, `aetherRenderVsync`,
  `AetherGpuProfiles` ve ön ayarların gerçekten yazdığı `LIBGL_BATCH`,
  `LIBGL_USEVBO`, `LIBGL_RECYCLEFBO`, `LIBGL_FORCE16BITS`, `LIBGL_NOVAOCACHE`
  dizeleri var.
- Native `libpojavexec.so` içinde `AETHER_SWAP_INTERVAL` ve
  `Swap interval: %d` günlük dizesi var → VSync yolu gerçekten derlenmiş.
- DEX içinde `TITAN_DARK`, `CYBER_PURPLE`, `ARCTIC_BLUE` tema kimlikleri var.
- DEX içinde `HomeHeroCard`, `HomeWidgetGallery`, `HomeWorlds`, `HomeWidgetInput`,
  `home_hero_last_session_fps` ve `home_widget_add_title` var → 3B kodu paketlenmiş.
- DEX içinde `RendererRecovery`, `RendererRecoveryStore`, `RendererRecoveryStartup`,
  `renderer_recovery` ve kurtarma dosya adları (`pending.properties`,
  `last_good.properties`) var → kurtarma kodu gerçekten paketlenmiş.
- DEX içinde `RecoveryReason`, `MemoryRecommendation` ve `LaunchLogClassifier`
  sınıfları var → 3.0.1 bellek/OOM yolu paketlenmiş.
- DEX içinde `MemoryPressure` ve `MemoryRelief` sınıfları var → 3.0.2'nin heap/GL
  ayrımı ve `options.txt` hafifletmesi paketlenmiş.
- DEX içinde `WorldArchive` ve `before-restore-` dizesi var; `resources.arsc`
  içinde `aether_saves_restore_entry`/`aether_saves_restore_done` var → 3E dünya
  geri yükleme paketlenmiş.
- `resources.arsc` içinde `aether_launch_health_low_allocation`,
  `renderer_recovery_memory` ve `renderer_recovery_apply_memory` dizeleri var;
  kaldırılan `aether_render_mode_lite`, `aether_render_mode_performance` ve
  `renderer_recovery_switched` dizeleri **yok**.
- DEX içinde `SessionHistoryStore`, `PerformanceAdvisor` ve `SessionComparison`
  var → 3D kodu paketlenmiş.
- ZIP bütünlüğü bozuk giriş içermiyor (`unzip -t`: "No errors detected").
- SHA-256, GitHub release `digest` alanıyla birebir eşleşiyor.
- Manifest paket adı ve etiketi yukarıdakiyle aynı; APK v2/v3 imza bloğu mevcut
  (debug anahtarıyla imzalanmıştır; yayın/release imzası değildir).

> Not: `k91541963-blip/ZalithLauncher2` deposunda GitHub Actions şu an
> "disabled" döndüğü için derleme, projenin mevcut CI aynası
> `k91541963-blip/Zlc` üzerinden `aetherlite/phase3` dalına yönlendirilerek
> yapılıyor. Kaynak ve workflow aynıdır; yalnızca tetikleyici depo farklıdır.

## 4. Cihaz Testleri

| Cihaz | GPU | Durum |
|---|---|---|
| — | Mali-G57 | ❌ yapılmadı |
| — | Mali-G68 | ❌ yapılmadı |
| — | Mali-G76 | ❌ yapılmadı |
| — | Mali-G78 | ❌ yapılmadı |

Bu ortamda oyun çalıştırabilen bir Android cihaz/emülatör ve Minecraft hesabı
bulunmadığı için AetherMetrics'in ürettiği kare süreleri **gerçek oyunda
doğrulanmamıştır**. AetherMetrics kod yolu (halka tampon, JNI köprüsü, percentile
hesabı) birim testleri ve APK sembol kontrolü ile doğrulanmıştır; ölçümün kendisi
cihaz testi bekler. AetherRender için FPS iddiası yoktur. Uygulanacak adımlar:
[`DEVICE_TEST_PLAN.md`](DEVICE_TEST_PLAN.md).

## 5. Statik / Mantıksal Kontroller

- `AetherRenderer.getRendererId()` (`opengles3_aether`) `opengles` ile başlar →
  `pojavSetWindowHint` GL4ES yolunu seçer (birim testi).
- `aether_render_choose_config()` `EGL_RENDERABLE_TYPE` değerini `LIBGL_ES`'e göre
  seçer: GLES 3 bağlamı istendiğinde `EGL_OPENGL_ES3_BIT_KHR`. Sabit ES2 biti
  kalsaydı katı sürücülerde `eglCreateContext` `EGL_BAD_MATCH` verirdi (kod).
- `GameLauncher` AetherRender'ı GL4ES ailesinde sayar → Mesa/Zink ortam değişkenleri
  gönderilmez; `LIBGL_ES` AetherRender ortamından gelir, türetme yalnızca baştaki
  rakamı alır (`opengles3_aether` → `3`) (kod + birim testi).
- `AetherRenderer` ortamı `AETHER_RENDER_MODE` değerini her başlatmada yeniden
  üretir; mod değişikliği launcher'ı yeniden başlatmayı gerektirmez (kod + test).
- `aether_render_choose_config()` `AETHER_RENDER` yokken ilk uygun config'i
  döndürür → mevcut davranış korunur (kod incelemesi).
- `native` modu stok EGL'ye; `compatibility` modu gevşek (RGB888 zorunlu, depth ≥16,
  isteğe bağlı alpha/stencil) seçime düşer; moda özel istek boş dönerse stok
  attribute setine geçilir (kod).
- Pencere için EGL config **RGB888 + alpha 8 + depth 24 + stencil 8** olmak zorunda;
  RGB565, alfasız ve şablonsuz yollar kaldırıldı. Stencil sunmayan sürücüde bir kez
  şablonsuz denenir, o da yoksa stok yola dönülür. Seçilen config
  `EGL config: ... stencil=...` satırıyla loglanır (kod + `-Wall -Wextra` sözdizimi
  kontrolü; cihazda doğrulanmadı).
- `AETHER_RENDER_MODE=lite|performance` eski değerleri yerel katmanda `balanced`'a
  eşlenir; kayıtlı enum ayarı da bilinmeyen değerde `BALANCED`'a düşer (kod + test).
- OOM teşhisi iki ayrı havuza ayrılır: `GL_OUT_OF_MEMORY`/`EGL_BAD_ALLOC` →
  `GRAPHICS`, `OutOfMemoryError` → `HEAP`. GL baskısında heap önerilmez; heap zaten
  cihaz sınırındaysa `atLimit` olayı yazılır (kod + test).
- `MemoryRelief` yalnızca düşürür: `renderDistance` 8, `simulationDistance` 5,
  fabulous→fancy, `entityShadows:false`. Dosya yoksa veya zaten düşükse yazmaz
  (kod + test).
- `WorldArchive.restore` arşivdeki her dünyayı ayrı klasöre açar; var olan klasörü
  silmez, `<ad>.before-restore-<ts>` olarak saklar; `..`/mutlak yol girdilerini ve
  `level.dat` içermeyen arşivleri reddeder (kod + test).
- `eglSurfaceAttrib` başarısız olursa yalnızca log; başlatma durmaz (kod).
- Launcher tarafındaki EGL probe `eglTerminate` çağırmaz → Compose renderer'ı
  etkilenmez (kod incelemesi).
- GPU/termal bilinmeyen değerler `null`/“Bilinmiyor” olarak gösterilir; tahmin
  edilmiş sürücü/sıcaklık yoktur (UI kodu).
- Performans Merkezi ve ana ekran kartı yalnızca ekran görünürken örnek alır
  (kod + `CpuUsageTest`).
- Oturum kaydı yalnızca oyun ekranı açıkken çalışır (`DisposableEffect`); launcher
  ana ekranında veya oyun kapalıyken hiçbir kare süresi toplanmaz, sayı üretilmez.
- AetherMetrics 1 saniyeden uzun takas boşluklarını kaydetmez; duraklatma/yükleme
  ekranı yapay bir "1 FPS" örneği oluşturmaz (kod + `FrameTimeStatsTest`).
- `%1 low` en yavaş %1 karenin ortalama süresinden, P95/P99 nearest-rank ile
  hesaplanır; formüller `FrameTimeStatsTest` içinde sabit değerlerle doğrulanır.
- `strings.xml` ve `values-tr` XML olarak ayrıştırıldı; tüm `aether_*` anahtarları
  mevcut.

## 6. Bilinen Açıklar

- Gerçek cihaz ölçümü yok (Mali-G57/G68/G76/G78 testleri bekliyor).
- Ana ekran "son oturumlar" satırı yalnızca son ölçülen oturumu gösterir
  (`last_session.json`); geçmiş ve karşılaştırma Performans Merkezi'ndedir (3D).
- Widget galerisi sunucu kartı için adres doğrular, ancak erişilebilirliği burada
  ölçmez; kart kendi ping sonucunu gösterir.
- Dünya adı `level.dat` NBT'sinden değil klasör adından okunur.
- Çökme sonrası kurtarma **kod olarak var** (3C) ve birim testleriyle doğrulandı;
  3.0.1'de yalnızca öneri üretecek şekilde daraltıldı ve renderer ayarını hiçbir
  koşulda otomatik yazmıyor. Gerçek bir native çökme/LMK senaryosunda cihazda
  denenmedi. Öneri zinciri AetherRender → Krypton Wrapper → GL4ES → dur; bilinmeyen
  renderer'lar da güvenli çekirdeğe düşer.
- **Beyaz dünya düzeltmesi cihazda yeniden üretilmedi.** Kod, EGL config'i RGB888 +
  depth 24 + stencil 8 ile sınırlıyor ve 565/şablonsuz yolu kaldırıyor; ancak bunun
  Mali sürücüsünde beyaz dünyayı gerçekten bitirdiği bir cihaz testiyle
  gösterilmedi. Beyaz dünya sürerse `adb logcat -s AetherRender` çıktısındaki
  `EGL config: ... stencil=` satırı ve `latest.log` kuyruğu gerekir.
- **OOM teşhisi ve hafifletme cihazda denenmedi.** Sınıflandırma, heap önerisi ve
  `options.txt` düzenlemesi birim testleriyle doğrulandı; 50 modlu gerçek bir 26.2
  kurulumunda "grafikleri ve görüş mesafesini düşür" düğmesinin oyunu açtığı
  gösterilmedi.
- Modpack dışa aktarma ve tam dosya doğrulama yok.
- AetherRender 2.1 NG-GL4ES çekirdeğini kullanır ve `26.3-snapshot-3` aralığını beyan
  eder; ancak bu aralık gerçek cihazda 26.3 ile denenmediği için "doğrulandı"
  sayılmaz. Eski `libgl4es_114.so` yolu ayrı GL4ES renderer'ında kalır.
- Chunk yükleme ve render thread süresi ölçülmez; bunlar Minecraft/GL4ES içinde
  kalır ve launcher tarafından güvenle okunamaz.
- Oyun içi overlay henüz %1 low/P99 satırlarını göstermiyor; aynı AetherMetrics
  örnekleri mevcut, arayüz işi yol haritasında.
- Oturum geçmişi en fazla 20 özet giriş tutar ve FPS kovalarını saklamaz; tam
  kare verisi yalnızca `last_session.json` içindedir.
- **AetherLite 3.0 spec'inin bu dilimde olmayan bölümleri:** oyun içi Overlay 3.0,
  Mod Hub güncelleme denetimi, World Manager eklemeleri, Skin Studio, Java Manager
  yenilemesi, Download Center kuyruğu, Notification Center, Plugin API ve modüler
  Gradle ayrımı. Bunlar `AETHERLITE_ROADMAP.md` içinde aşamalara bağlanmıştır; bu
  raporda "yapıldı" sayılmazlar.
- AetherRender 3 ön ayarlarının (batching/VBO/16-bit doku) gerçek etkisi cihazda
  ölçülmedi; yalnızca çekirdeğin bu değişkenleri okuduğu ve ortama yazıldığı
  doğrulandı. FPS/frametime iddiası yoktur.

> Rapor son güncelleme: 2026-11-20, kaynak commit `3ff8059`, CI çalışması
> [44695468421](https://github.com/k91541963-blip/Zlc/actions/runs/44695468421).
> APK bu commit ile aynı ağaçtan üretildi (216.630.464 bayt, SHA-256
> `2a60a219…b9f2`); SHA-256 GitHub release `digest` alanıyla birebir eşleşiyor.
> Cihaz testleri hâlâ yapılmadı; 3.0.2 düzeltmeleri yalnızca derleme, birim testi ve
> APK sembol/dize kontrolüyle doğrulandı.
