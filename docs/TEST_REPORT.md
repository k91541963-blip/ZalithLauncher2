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

| Paket | Test sayısı | Durum |
|---|---:|---|
| `game.performance` | 39 | ⏳ CI |
| `game.diagnostics` | 11 | ⏳ CI |
| `game.version.mod.dependency` | 22 | ⏳ CI |
| **Toplam** | **72** | ⏳ CI |

Yeni eklenenler:

- `GpuInfoTest` — Mali model ayrıştırma/normalizasyon, sınıflandırma, negatif durumlar
- `ThermalInfoTest` — millidegrees/ondalık ayrıştırma, aralık koruması
- `AetherRendererTest` — `opengles` öneki (native GL4ES yolu), env, kitaplık, kimlik çakışması
- `RendererCompatibilityTest` — AetherRender Mali/Adreno davranışı

## 3. Derleme

| İş | Ortam | Sonuç |
|---|---|---|
| Debug ARM64 APK | GitHub Actions (JDK 21, NDK, Rust) | ⏳ çalışıyor |
| Native `pojavexec` (AetherRender dahil) | NDK `ndkBuild` | ⏳ build ile doğrulanacak |

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

- Gerçek cihaz ölçümü yok.
- Otomatik çökme sonrası renderer geri dönüşü yok.
- Modpack dışa aktarma ve tam dosya doğrulama yok.
- NG-GL4ES kitaplığı paketlenmiyor (bilinçli).

Sonuçlar CI tamamlandığında bu dosyada güncellenecek.
