# Test Raporu

> Kural: yalnızca gerçekten çalıştırılan kontroller "geçti" olarak işaretlenir.
> Cihaz ölçümü yapılmadıysa "yapılmadı" yazılır.

## 1. Kapsam

- AetherLite kimliği ve paket adı
- AetherRender 3 (NG-GL4ES çekirdeği, GLES 3 bağlamı, 26.3 sürüm aralığı, mod bazlı
  EGL seçimi, yüzey politikası, gerçek çekirdek ayar ön ayarları, `eglSwapInterval`
  ile VSync, Mali modeline göre öneri, telemetri)
- AetherRender 3 çökme kurtarma: başlatma öncesi deneme kaydı, çıkış kodu 0 = başarı,
  başarısızlıkta güvenli renderer zinciri (AetherRender → Krypton Wrapper → GL4ES →
  dur) ve sürüm başına "son çalışan renderer" kaydı
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

Sonuç: **133 test, 0 başarısız** (CI çalışması
[44271299158](https://github.com/k91541963-blip/Zlc/actions/runs/44271299158),
kaynak commit `92f7b60`; `:ZalithLauncher:testDebugUnitTest` BUILD SUCCESSFUL).
Test dosyalarındaki `@Test` sayımı 133'tür ve CI aynı filtrelerle yeşildir.

| Paket | Test sayısı | Durum |
|---|---:|---|
| `game.performance` | 100 | ✅ geçti |
| `game.diagnostics` | 11 | ✅ geçti |
| `game.version.mod.dependency` | 22 | ✅ geçti |
| **Toplam** | **133** | ✅ **geçti** |

Aşama 3C'de CI iki gerçek hata yakaladı ve ikisi de düzeltildi:
`44266581823` — TR dizelerinde kaçırılmamış kesme işareti (`aapt2`
`mergeDebugResources` hatası), `44267725095` — kurtarma testinde kalan
`storeFileRoot` referansı ve "son çalışan" kaydını yanlış sürümde tutan beklenti.
Düzeltmeler `f7ced04`, `b727757`, `a96dfc1`.

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
- `RendererRecoveryTest` — GLES 3'te AetherRender→Krypton Wrapper, GLES 2'de
  AetherRender→GL4ES, zincirin GL4ES'te bitmesi, kimlik eşlemesi, denemenin
  çözülene kadar okunması, çıkış kodu 0'da denemenin silinmesi ve son çalışanın
  yazılması, başarısızlıkta olay + güvenli geri dönüş, sayaç artışı/sıfırlanması,
  olayın kapatılması, başarısız sürümün "son çalışan" kaydını kaybetmesi,
  kayıt yokken hiçbir şey üretilmemesi (11 test)

## 3. Derleme

| İş | Ortam | Sonuç |
|---|---|---|
| Debug ARM64 APK | GitHub Actions (JDK 21, NDK 27, Rust) | ✅ başarılı |
| Native `pojavexec` (AetherRender 2.1 + AetherMetrics dahil) | NDK `ndkBuild` | ✅ başarılı |

APK: `AetherLite-arm64-debug.apk`

| Alan | Değer |
|---|---|
| Boyut | 216.519.752 bayt (≈206,5 MiB) |
| SHA-256 | `787763e6c754f6c9e544ce028341e009d0339516b23eaeaff00d0a6d7026d779` |
| Paket | `com.aetherlite.launcher.debug` |
| Uygulama adı | `AetherLite Launcher` |
| İndirme | https://github.com/k91541963-blip/Zlc/releases/download/apk-latest/AetherLite-arm64-debug.apk |

APK içeriği doğrulandı:

- `lib/arm64-v8a/libpojavexec.so` içinde `aether_render_choose_config`,
  `aether_render_on_surface_created`, `aether_render_on_context_created`,
  `AETHER_RENDER`, `AETHER_RENDER_MODE`, `AetherRender`, `EGL_BUFFER_DESTROYED`,
  `performance` ve `compatibility` dizeleri mevcut.
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
- `native`/`compatibility` modları stok EGL'ye ve yüzey ipucu yokluğuna düşer;
  moda özel istek boş dönerse dengeli attribute setine geçilir (kod).
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
- Ana ekran "son oturumlar" **geçmişi** yok; yalnızca son ölçülen oturum gösterilir
  (`last_session.json`). Geçmiş ve karşılaştırma 3D'de.
- Widget galerisi sunucu kartı için adres doğrular, ancak erişilebilirliği burada
  ölçmez; kart kendi ping sonucunu gösterir.
- Dünya adı `level.dat` NBT'sinden değil klasör adından okunur.
- Otomatik çökme sonrası renderer geri dönüşü **kod olarak var** (3C) ve birim
  testleriyle doğrulandı, ancak gerçek bir native çökme/LMK senaryosunda cihazda
  denenmedi. Zincir AetherRender → Krypton Wrapper → GL4ES → dur; bilinmeyen
  renderer'lar da güvenli çekirdeğe düşer.
- Modpack dışa aktarma ve tam dosya doğrulama yok.
- AetherRender 2.1 NG-GL4ES çekirdeğini kullanır ve `26.3-snapshot-3` aralığını beyan
  eder; ancak bu aralık gerçek cihazda 26.3 ile denenmediği için "doğrulandı"
  sayılmaz. Eski `libgl4es_114.so` yolu ayrı GL4ES renderer'ında kalır.
- Chunk yükleme ve render thread süresi ölçülmez; bunlar Minecraft/GL4ES içinde
  kalır ve launcher tarafından güvenle okunamaz.
- Oyun içi overlay henüz %1 low/P99 satırlarını göstermiyor; aynı AetherMetrics
  örnekleri mevcut, arayüz işi yol haritasında.
- Oturum özeti tek dosyadır (`last_session.json`); geçmiş oturum listesi yok.
- **AetherLite 3.0 spec'inin bu dilimde olmayan bölümleri:** Ana ekran 3.0 yeniden
  tasarımı ve widget sistemi, oyun içi Overlay 3.0, Mod Hub güncelleme denetimi,
  World Manager eklemeleri, Skin Studio, Java Manager yenilemesi, Download Center
  kuyruğu, Notification Center, Plugin API ve modüler Gradle ayrımı. Bunlar
  `AETHERLITE_ROADMAP.md` içinde aşamalara bağlanmıştır; bu raporda "yapıldı"
  sayılmazlar.
- AetherRender 3 ön ayarlarının (batching/VBO/16-bit doku) gerçek etkisi cihazda
  ölçülmedi; yalnızca çekirdeğin bu değişkenleri okuduğu ve ortama yazıldığı
  doğrulandı. FPS/frametime iddiası yoktur.

> Rapor son güncelleme: 2026-11-18, kaynak commit `92f7b60`, CI çalışması
> [44271299158](https://github.com/k91541963-blip/Zlc/actions/runs/44271299158).
> APK bu commit ile aynı ağaçtan üretildi ve SHA-256/ZIP/sembol olarak doğrulandı;
> sonrasında yalnızca bu belge güncellendi.
