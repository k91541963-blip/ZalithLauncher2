# Test Raporu

> Kural: yalnızca gerçekten çalıştırılan kontroller "geçti" olarak işaretlenir.
> Cihaz ölçümü yapılmadıysa "yapılmadı" yazılır.

## 1. Kapsam

- AetherLite kimliği ve paket adı
- AetherRender 2.0 (mod bazlı EGL seçimi, yüzey politikası, telemetri)
- Canlı Performans Merkezi (CPU/RAM/GPU/ısı) ve ana ekran özet kartı
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

Sonuç: **80 test, 80 geçti, 0 başarısız** (CI çalışması
[43869003165](https://github.com/k91541963-blip/Zlc/actions/runs/43869003165),
kaynak commit `48d7919`).

| Paket | Test sayısı | Durum |
|---|---:|---|
| `game.performance` | 47 | ✅ geçti |
| `game.diagnostics` | 11 | ✅ geçti |
| `game.version.mod.dependency` | 22 | ✅ geçti |
| **Toplam** | **80** | ✅ **geçti** |

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

## 3. Derleme

| İş | Ortam | Sonuç |
|---|---|---|
| Debug ARM64 APK | GitHub Actions (JDK 21, NDK 27, Rust) | ✅ başarılı |
| Native `pojavexec` (AetherRender 2.0 dahil) | NDK `ndkBuild` | ✅ başarılı |

APK: `AetherLite-arm64-debug.apk`

| Alan | Değer |
|---|---|
| Boyut | 216.369.340 bayt (≈206,3 MiB) |
| SHA-256 | `368c3d2b5772e0e208447cc9be7aec0bc1b96847c6bbda000fb792a8547c0ded` |
| Paket | `com.aetherlite.launcher.debug` |
| Uygulama adı | `AetherLite Launcher` |
| İndirme | https://github.com/k91541963-blip/Zlc/releases/download/apk-latest/AetherLite-arm64-debug.apk |

APK içeriği doğrulandı:

- `lib/arm64-v8a/libpojavexec.so` içinde `aether_render_choose_config`,
  `aether_render_on_surface_created`, `aether_render_on_context_created`,
  `AETHER_RENDER`, `AETHER_RENDER_MODE`, `AetherRender`, `EGL_BUFFER_DESTROYED`,
  `performance` ve `compatibility` dizeleri mevcut.
- DEX içinde `PerformanceCenterScreen`, `PerformanceSummaryCard`,
  `AetherRenderModeCard`, `AetherRenderMode` ve `CpuUsage` sınıfları paketli.
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
bulunmadığı için oyun içi FPS/frametime ölçümü **yapılmamıştır**. AetherRender
için FPS iddiası yoktur. Uygulanacak adımlar: [`DEVICE_TEST_PLAN.md`](DEVICE_TEST_PLAN.md).

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
- Performans Merkezi ve ana ekran kartı yalnızca ekran görünürken örnek alır;
  FPS/frametime üretmez, oyun içi katmana yönlendirir (kod + `CpuUsageTest`).
- `strings.xml` ve `values-tr` XML olarak ayrıştırıldı; tüm `aether_*` anahtarları
  mevcut.

## 6. Bilinen Açıklar

- Gerçek cihaz ölçümü yok (Mali-G57/G68/G76/G78 testleri bekliyor).
- Otomatik çökme sonrası renderer geri dönüşü yok.
- Modpack dışa aktarma ve tam dosya doğrulama yok.
- NG-GL4ES APK'da paketli ve ayrı renderer olarak seçilebilir, ancak AetherRender
  2.0 hâlâ `libgl4es_114.so` çekirdeğini kullanır; iki yol birleştirilmedi.
- Oyun içi FPS/%1 low/frametime/chunk durumu Performans Merkezi'nde gösterilmez;
  bunlar oyun içi overlay'in işidir ve oyun kapalıyken ölçülemez.

> Rapor son güncelleme: 2026-11-17, kaynak commit `48d7919`, CI çalışması
> [43869003165](https://github.com/k91541963-blip/Zlc/actions/runs/43869003165).
> APK bu commit ile aynı ağaçtan üretildi ve SHA-256/ZIP olarak doğrulandı;
> sonrasında yalnızca bu belge güncellendi.
