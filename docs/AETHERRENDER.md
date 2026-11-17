# AetherRender 2.0 — Tasarım, Entegrasyon ve Sınırlar

> Bu belge AetherRender'ın **ne yaptığını**, **ne yapmadığını** ve nasıl
> doğrulanacağını anlatır. Pazarlama metni değildir.

## 1. Amaç

AetherRender, launcher'ın kontrol edebildiği katmanda Mali/tile tabanlı GPU'lar için
gereksiz bant genişliği ve yapılandırma maliyetini azaltmayı hedefler:

- çok örneklemeli (MSAA) EGL config seçmemek,
- 24-bit derinlikli, 8-bit renkli pencere config'i tercih etmek,
- takas sonrası arka tamponun korunmasını istememek (`EGL_BUFFER_DESTROYED`),
- gerçek sürücü dizelerini loglayarak destek raporlarını kanıta dayandırmak.

## 2. Bilinçli Olarak Yapılmayanlar

Aşağıdakiler bu sürümde **yoktur** ve varmış gibi gösterilmez:

| İstenen | Neden yok |
|---|---|
| Minecraft chunk renderer'ını değiştirme | Oyun içi kod; launcher tarafından güvenli biçimde yapılamaz |
| Draw call azaltma / VBO birleştirme | GL4ES'in içinde; kaynak kodu bu repoda değil |
| Shader derleme ikilisi önbelleği (Mali) | Mali sürücüsü `glGetProgramBinary` garantisi vermiyor |
| Asenkron chunk hazırlama | Minecraft istemcisinin işi; Sodium benzeri mod gerekir |
| Oyun içi dinamik ısı yönetimi | Oyun thread'lerine müdahale gerektirir; güvenli değil |
| Vulkan backend | Gerçek backend yoksa sunulmaz |
| FPS artış garantisi | Hiçbir ölçüm yapılmadan iddia edilmez |

Zink yolu (Mesa) kullanılıyorsa `MESA_GLSL_CACHE_DIR` zaten ayarlanır; bu, sürücünün
kendi shader önbelleğidir. Mali'nin yerel GLES sürücüsünde eşdeğeri yoktur.

## 3. Mimari

```
Minecraft (LWJGL)
      │  OpenGL çağrıları
      ▼
libgl4es_114.so  (GL → GLES çevirisi)
      │
      ▼
pojavexec / ctxbridges/gl_bridge.c
      │  aether_render_choose_config()
      │  aether_render_on_surface_created()
      │  aether_render_on_context_created()
      ▼
jni/aetherrender/aether_render.c
      │
      ▼
Vendor EGL/GLES sürücüsü (Mali, Adreno, …)
```

### 3.1 Renderer kaydı

`game/renderer/renderers/AetherRenderer.kt`

- Kimlik: `opengles2_aether`
  - **Kritik:** `egl_bridge.c` içindeki `pojavSetWindowHint`, yalnızca `opengles`
    önekiyle başlayan kimlikleri `RENDERER_GL4ES` yoluna bağlar. Bu önek değişirse
    swap/make-current sessizce bozulur.
- Kitaplık: paketli `libgl4es_114.so` (kanıtlanmış çekirdek)
- Ortam: `AETHER_RENDER=1`, `AETHER_RENDER_MODE=<mod>`, `LIBGL_ES=2`, GL4ES güvenli ayarları
- Sürüm aralığı: en fazla Minecraft 1.21.4 (GL4ES çekirdeğinin sınırı)

### 3.2 EGL config seçimi

`aether_render_choose_config()`:

1. `EGL_WINDOW_BIT | EGL_PBUFFER_BIT`, `EGL_OPENGL_ES2_BIT`, RGB888, alpha 8,
   depth 24 ile adayları ister.
2. AetherRender kapalıysa eskisi gibi ilk adayı döndürür (davranış değişmez).
3. Açıksa adayları puanlar:
   - `EGL_SAMPLES` / `EGL_SAMPLE_BUFFERS` → ağır ceza,
   - depth ≥ 24 → artı, depth < 16 → büyük ceza,
   - `EGL_CONFIG_CAVEAT != EGL_NONE` → ceza,
   - pencere desteği ve ES2 renderable zorunlu.
4. Seçilen config'in renk/alpha/depth/samples/visual değerlerini loglar.

### 3.3 Renderer modları (2.0)

Mod, `AllSettings.aetherRenderMode` üzerinden seçilir ve **bir sonraki oyun
başlatmasında** `AETHER_RENDER_MODE` ortam değişkeniyle yerel katmana iletilir.
Yalnızca launcher'ın gerçekten kontrol ettiği EGL kararlarını değiştirir.

| Mod | EGL davranışı | Not |
|---|---|---|
| Aether Native | Stok EGL seçimi, yüzey ipucu yok | Sürücü sorunlarını izole etmek için |
| Aether Balanced (varsayılan) | MSAA yok, RGB888 + alpha 8, depth 24, `EGL_BUFFER_DESTROYED` | Önceki v1 davranışı |
| Aether Lite | Ek olarak alpha ve stencil içermeyen config tercih edilir | Daha az piksel durumu/bellek |
| Aether Performance | RGB565 pencere config'i tercih edilir (opt-in) | Bant genişliği kazancı; renk bantlanması olabilir |
| Aether Compatibility | Stok EGL seçimi, yüzey ipucu yok | Destek raporları için Native ile aynı |

Moda özel istek hiçbir config bulamazsa dengeli attribute setine geri düşer;
başlatma başarısız edilmez. `native`/`compatibility` modlarında EGL seçimi ve
yüzey davranışı stok yola bırakılır.

### 3.4 Yüzey politikası

`aether_render_on_surface_created()` pencere ve pbuffer yüzeylerinde
`eglSurfaceAttrib(EGL_SWAP_BEHAVIOR, EGL_BUFFER_DESTROYED)` dener.

- Tile tabanlı GPU'larda arka tamponun kareler arasında korunması ekstra bellek
  trafiği demektir; `DESTROYED` bunu engeller.
- Sürücü reddederse yalnızca uyarı loglanır; **başlatma devam eder**.

### 3.5 Telemetri

`aether_render_on_context_created()` oyun bağlamı current olduktan sonra bir kez
`GL_VENDOR`, `GL_RENDERER`, `GL_VERSION`, `GL_SHADING_LANGUAGE_VERSION` yazar.

Logcat:

```bash
adb logcat -s AetherRender
```

Beklenen satırlar:

```
AetherRender enabled (mode=balanced)
EGL config: mode=balanced rgb=8/8/8 alpha=8 depth=24 samples=0 sample_buffers=0 visual=0x1
Surface swap behaviour: EGL_BUFFER_DESTROYED (mode=balanced)
GPU vendor=ARM renderer=Mali-G57 ... version=OpenGL ES 3.2 ... mode=balanced
```

### 3.6 AetherMetrics (kare süresi ölçümü)

AetherRender'ın EGL katmanıyla aynı pakette bulunan `aether_metrics.c` her kare
takasında (`calculateFPS()` içinden) bir `CLOCK_MONOTONIC` zaman damgası alır ve iki
kare arasındaki farkı 2048 girişlik halka tampona yazar.

- `CallbackBridge.getFrameTimeSamples(int[])` en yeni örnekleri kronolojik sırayla
  kopyalar; `resetFrameTimeSamples()` yeni oturum başında tamponu temizler.
- 1 saniyeden uzun farklar (duraklatma, boyut yükleme, uygulamanın arka plana
  gitmesi) kaydedilmez; aksi hâlde yapay bir "1 FPS" karesi tüm yüzdeleri bozardı.
- Ölçüm yalnızca `GameSessionRecorder` oyun ekranı açıkken çalışır; launcher ana
  ekranında veya oyun kapalıyken hiçbir örnek toplanmaz.
- Bu katman render durumunu değiştirmez, ek GL çağrısı yapmaz ve başlatmayı
  etkileyemez; yalnızca ölçer.

## 4. Geri Dönüş (Fallback)

- `AETHER_RENDER` yoksa/0 ise tüm yeni kod eski davranışı birebir izler.
- Moda özel config isteği boş dönerse dengeli attribute setine, o da boş dönerse
  stok davranışa düşülür; mod seçimi başlatmayı kilitlemez.
- Yüzey politikası reddedilirse sessizce devam edilir.
- Renderer seçimi kullanıcı ayarıdır; AetherRender seçili değilken hiçbir yeni
  ortam değişkeni gönderilmez.

> Otomatik "çökme sonrası GL4ES'e dön" henüz yok; yol haritasında.

## 5. Cihaz Doğrulama

`docs/DEVICE_TEST_PLAN.md` uygulanır. En az:

1. AetherRender ile temiz dünya başlat, logcat'te yukarıdaki 4 satırı doğrula.
2. Aynı sürüm/ayarlarla GL4ES'e dön; iki kez 10 dakikalık oturum karşılaştır.
3. Frametime dağılımı (p50/p95), CPU zamanı, RSS, batarya sıcaklığı kaydet.
4. Sonuç `TEST_REPORT.md`'ye cihaz/ROM/sürücü bilgisiyle yazılır.

**Ölçüm olmadan FPS iddiası yayınlanmaz.**

## 6. Dosyalar

| Dosya | Görev |
|---|---|
| `jni/aetherrender/aether_render.c` | Mod bazlı EGL seçimi, yüzey politikası, telemetri |
| `jni/aetherrender/aether_render.h` | C arayüzü |
| `jni/aetherrender/aether_metrics.c/h` | Takas zaman damgalarından gerçek kare süresi (AetherMetrics) |
| `jni/ctxbridges/gl_bridge.c` | Entegrasyon noktaları |
| `jni/ctxbridges/egl_loader.c/h` | `eglSurfaceAttrib` yüklemesi |
| `game/renderer/renderers/AetherRenderer.kt` | Renderer kaydı, mod ortamı |
| `setting/enums/AetherRenderMode.kt` | Beş mod ve `AETHER_RENDER_MODE` değerleri |
| `ui/.../settings/AetherRenderModeCard.kt` | Mod seçim arayüzü |
| `game/performance/GpuInfo.kt` | Launcher tarafı EGL probe + Mali modeli |
| `game/performance/ThermalInfo.kt` | Batarya/thermal zone okuma |
| `game/performance/PerformanceMonitor.kt` | Canlı CPU/RAM/GPU/ısı ölçümü |
| `ui/.../performance/PerformanceCenterScreen.kt` | Canlı Performans Merkezi |
| `ui/.../home/PerformanceSummaryCard.kt` | Ana ekran performans kartı |
| `ui/.../DeviceInfoCard.kt` | Cihaz ve GPU paneli |
| `test/.../CpuUsageTest.kt`, `GpuInfoTest.kt`, `ThermalInfoTest.kt`, `AetherRendererTest.kt` | Birim testleri |
