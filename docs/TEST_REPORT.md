# Test Raporu

> Kural: yalnızca gerçekten çalıştırılan kontroller "geçti" olarak işaretlenir.
> Cihaz ölçümü yapılmadıysa "yapılmadı" yazılır.

## 1. Kapsam

- AetherLite kimliği ve paket adı
- AetherRender v1 (EGL config seçimi, yüzey politikası, telemetri)
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

Sonuç: **72 test, 72 geçti, 0 başarısız** (CI çalışması
[43863986149](https://github.com/k91541963-blip/Zlc/actions/runs/43863986149),
kaynak commit `61a49cf`).

| Paket | Test sayısı | Durum |
|---|---:|---|
| `game.performance` | 39 | ✅ geçti |
| `game.diagnostics` | 11 | ✅ geçti |
| `game.version.mod.dependency` | 22 | ✅ geçti |
| **Toplam** | **72** | ✅ **geçti** |

İlk CI çalışması (`43861719985`) iki hatalı test beklentisini ortaya çıkardı:
`parseMaliModel("Mali-T860 MP4")` çekirdek sayısını koruyor ve `classifyMali`
G68'i MID sayıyor. Beklentiler gerçek davranışa göre düzeltildi (`a12fcad`).
İkinci çalışma (`43862571445`) birim testleri geçti ancak bağlayıcı
`eglSurfaceAttrib_p` tanımının eksik olduğunu gösterdi; tanım eklendi (`61a49cf`).

Yeni eklenenler:

- `GpuInfoTest` — Mali model ayrıştırma/normalizasyon, sınıflandırma, negatif durumlar
- `ThermalInfoTest` — millidegrees/ondalık ayrıştırma, aralık koruması
- `AetherRendererTest` — `opengles` öneki (native GL4ES yolu), env, kitaplık, kimlik çakışması
- `RendererCompatibilityTest` — AetherRender Mali/Adreno davranışı

## 3. Derleme

| İş | Ortam | Sonuç |
|---|---|---|
| Debug ARM64 APK | GitHub Actions (JDK 21, NDK 27, Rust) | ✅ başarılı |
| Native `pojavexec` (AetherRender dahil) | NDK `ndkBuild` | ✅ başarılı |

APK: `AetherLite-arm64-debug.apk`

| Alan | Değer |
|---|---|
| Boyut | 216.290.874 bayt (≈206,3 MiB) |
| SHA-256 | `f5cde34782d8f7c5fb6ad070b57568d3f213a1e70196fc950ed242baff9c56ae` |
| Paket | `com.aetherlite.launcher.debug` |
| Uygulama adı | `AetherLite Launcher` |
| İndirme | https://github.com/k91541963-blip/Zlc/releases/download/apk-latest/AetherLite-arm64-debug.apk |

APK içeriği doğrulandı:

- `lib/arm64-v8a/libpojavexec.so` içinde `aether_render_choose_config`,
  `aether_render_on_surface_created`, `aether_render_on_context_created`,
  `AETHER_RENDER` ve `EGL_BUFFER_DESTROYED` dizeleri mevcut.
- `lib/arm64-v8a/libgl4es_114.so` ve `lib/arm64-v8a/libng_gl4es.so` paketli.
- Manifest paket adı ve etiketi yukarıdakiyle aynı.

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
- `aether_render_choose_config()` `AETHER_RENDER` yokken ilk uygun config'i
  döndürür → mevcut davranış korunur (kod incelemesi).
- `eglSurfaceAttrib` başarısız olursa yalnızca log; başlatma durmaz (kod).
- Launcher tarafındaki EGL probe `eglTerminate` çağırmaz → Compose renderer'ı
  etkilenmez (kod incelemesi).
- GPU/termal bilinmeyen değerler `null`/“Bilinmiyor” olarak gösterilir; tahmin
  edilmiş sürücü/sıcaklık yoktur (UI kodu).
- `strings.xml` ve `values-tr` XML olarak ayrıştırıldı; tüm `aether_*` anahtarları
  mevcut.

## 6. Bilinen Açıklar

- Gerçek cihaz ölçümü yok (Mali-G57/G68/G76/G78 testleri bekliyor).
- Otomatik çökme sonrası renderer geri dönüşü yok.
- Modpack dışa aktarma ve tam dosya doğrulama yok.
- NG-GL4ES APK'da paketli ve ayrı renderer olarak seçilebilir, ancak AetherRender
  v1 hâlâ `libgl4es_114.so` çekirdeğini kullanır; iki yol birleştirilmedi.

> Rapor son güncelleme: 2026-11-16, kaynak commit `61a49cf`, CI çalışması
> [43863986149](https://github.com/k91541963-blip/Zlc/actions/runs/43863986149).
> APK bu commit ile aynı ağaçtan üretildi; sonrasında yalnızca bu belge güncellendi.
