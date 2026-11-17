# Test Raporu

> Kural: yalnızca gerçekten çalıştırılan kontroller "geçti" olarak işaretlenir.
> Cihaz ölçümü yapılmadıysa "yapılmadı" yazılır.

## 1. Kapsam

- AetherLite kimliği ve paket adı
- AetherRender 2.1 (NG-GL4ES çekirdeği, GLES 3 bağlamı, 26.3 sürüm aralığı, mod bazlı
  EGL seçimi, yüzey politikası, telemetri)
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

Sonuç: **93 test, 0 başarısız** (CI çalışması
[44044868982](https://github.com/k91541963-blip/Zlc/actions/runs/44044868982),
kaynak commit `26c7bf3`; `:ZalithLauncher:testDebugUnitTest` BUILD SUCCESSFUL.
Sayı, CI ile aynı paketlerdeki `@Test` sayımıdır).

| Paket | Test sayısı | Durum |
|---|---:|---|
| `game.performance` | 60 | ✅ geçti |
| `game.diagnostics` | 11 | ✅ geçti |
| `game.version.mod.dependency` | 22 | ✅ geçti |
| **Toplam** | **93** | ✅ **geçti** |

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
- `AetherRendererTest` — `opengles` öneki (native GL4ES yolu), env,
  `AETHER_RENDER_MODE` değerinin geçerliliği, kitaplık, kimlik çakışması
- `RendererCompatibilityTest` — AetherRender Mali/Adreno davranışı
- `FrameTimeStatsTest` — harmonik ortalama FPS, %1 low (en yavaş %1 kare), P99,
  jank oranı, geçersiz/1 sn üstü örnek reddi (7 test)
- `GameSessionAccumulatorTest` — oturum toplama, eksik cihaz verisinin `null`
  kalması, kare tamponu sınırı ve işaretleme, 10 sn FPS kovaları (6 test)

## 3. Derleme

| İş | Ortam | Sonuç |
|---|---|---|
| Debug ARM64 APK | GitHub Actions (JDK 21, NDK 27, Rust) | ✅ başarılı |
| Native `pojavexec` (AetherRender 2.1 + AetherMetrics dahil) | NDK `ndkBuild` | ✅ başarılı |

APK: `AetherLite-arm64-debug.apk`

| Alan | Değer |
|---|---|
| Boyut | 216.404.552 bayt (≈206,4 MiB) |
| SHA-256 | `5d12b52e4f22524dcb2901fe4f59ead350e2da80ac6f8c593fd6745661dbdae6` |
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
- `lib/arm64-v8a/libgl4es_114.so` ve `lib/arm64-v8a/libng_gl4es.so` paketli.
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

- `AetherRenderer.getRendererId()` `opengles` ile başlar → `pojavSetWindowHint`
  GL4ES yolunu seçer (birim testi).
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
- Otomatik çökme sonrası renderer geri dönüşü yok.
- Modpack dışa aktarma ve tam dosya doğrulama yok.
- AetherRender 2.1 NG-GL4ES çekirdeğini kullanır ve `26.3-snapshot-3` aralığını beyan
  eder; ancak bu aralık gerçek cihazda 26.3 ile denenmediği için "doğrulandı"
  sayılmaz. Eski `libgl4es_114.so` yolu ayrı GL4ES renderer'ında kalır.
- Chunk yükleme ve render thread süresi ölçülmez; bunlar Minecraft/GL4ES içinde
  kalır ve launcher tarafından güvenle okunamaz.
- Oyun içi overlay henüz %1 low/P99 satırlarını göstermiyor; aynı AetherMetrics
  örnekleri mevcut, arayüz işi yol haritasında.
- Oturum özeti tek dosyadır (`last_session.json`); geçmiş oturum listesi yok.

> Rapor son güncelleme: 2026-11-17, kaynak commit `26c7bf3`, CI çalışması
> [44044868982](https://github.com/k91541963-blip/Zlc/actions/runs/44044868982).
> APK bu commit ile aynı ağaçtan üretildi ve SHA-256/ZIP/sembol olarak doğrulandı;
> sonrasında yalnızca bu belge güncellendi.
