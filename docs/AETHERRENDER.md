# AetherRender 3 — Tasarım, Entegrasyon ve Sınırlar

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
libng_gl4es.so  (GL 3.1 → GLES 3 çevirisi)
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

- Kimlik: `opengles3_aether`
  - **Kritik:** `egl_bridge.c` içindeki `pojavSetWindowHint`, yalnızca `opengles`
    önekiyle başlayan kimlikleri `RENDERER_GL4ES` yoluna bağlar. Bu önek değişirse
    swap/make-current sessizce bozulur.
- Kitaplık: paketli `libng_gl4es.so` (NG-GL4ES / "Krypton" çekirdeği)
  - Eski `libgl4es_114.so` çekirdeği yalnızca GL 2.1 sunduğu ve 1.21.4'te durduğu için
    AetherRender 2.1 modern çekirdeğe geçti. GLES 2 cihazlar ve eski sürümler için
    ayrı **GL4ES** renderer'ı korunuyor.
- Ortam: `AETHER_RENDER=1`, `AETHER_RENDER_MODE=<mod>`, `LIBGL_GL=31`, `LIBGL_ES=3`,
  `LIBGL_USE_MC_COLOR=1`, GL4ES güvenli ayarları
- Sürüm aralığı: en fazla `26.3-snapshot-3`; arayüzde `26.2` gösterilir

### 3.2 EGL config seçimi

`aether_render_choose_config()`:

1. `EGL_WINDOW_BIT | EGL_PBUFFER_BIT`, **RGB888 + alpha 8 + depth 24 + stencil 8**
   ile adayları ister. `EGL_RENDERABLE_TYPE` `LIBGL_ES` değerine göre seçilir: GLES 3
   bağlamı için `EGL_OPENGL_ES3_BIT_KHR`, GLES 2 için `EGL_OPENGL_ES2_BIT`.
2. AetherRender kapalıysa veya mod `native` ise stok attribute setiyle ilk adayı
   döndürür (davranış değişmez).
3. Açıksa adayları puanlar ve **geçersizleri eler**:
   - `EGL_SAMPLES` / `EGL_SAMPLE_BUFFERS` → ağır ceza,
   - pencere desteği ve gerekli renderable bit zorunlu,
   - RGB888 zorunlu (RGB565 **asla** kabul edilmez),
   - depth ≥ 24, alpha ≥ 8, stencil ≥ 8 zorunlu (stencil yoksa bir kez şablonsuz
     denenir),
   - `EGL_CONFIG_CAVEAT != EGL_NONE` → ceza.
4. Hiçbir aday kalmazsa stok attribute setine düşer; RGB565 bir config oyuna
   verilmez.
5. Seçilen config'in renk/alpha/depth/**stencil**/samples/visual değerlerini loglar.

> **Neden stencil zorunlu?** Minecraft'ın "Fabulous" grafik modu şeffaflık geçişinde
> stencil buffer kullanır. Stencil'siz (veya 16-bit) bir pencere yüzeyi bazı
> sürücilerde dünyayı tamamen beyaz çizdi. 3.0.1 bu yüzden 565/alfasız/şablonsuz
> yolları kaldırdı.

### 3.3 Renderer modları

Mod, `AllSettings.aetherRenderMode` üzerinden seçilir ve **bir sonraki oyun
başlatmasında** `AETHER_RENDER_MODE` ortam değişkeniyle yerel katmana iletilir.
Yalnızca launcher'ın gerçekten kontrol ettiği EGL kararlarını değiştirir.

| Mod | EGL davranışı | Not |
|---|---|---|
| Aether Native | Stok EGL seçimi, yüzey ipucu yok | Sürücü sorunlarını izole etmek için |
| Aether Balanced (varsayılan) | RGB888 + alpha 8 + depth 24 + stencil 8, MSAA yok, `EGL_BUFFER_DESTROYED` | Normal ve "Fabulous" grafikler için doğru yüzey |
| Aether Compatibility | Gevşek seçim: yine RGB888, ama depth 16 ve isteğe bağlı alpha/stencil; yüzey ipucu yok | 24-bit depth/şablon sunmayan sürücüler için son çare |

İstek hiçbir config bulamazsa stok attribute setine geri düşer; başlatma başarısız
edilmez. `native`/`compatibility` modlarında yüzey ipucu uygulanmaz.

> 3.0.1 öncesindeki **Lite** (alfasız/şablonsuz) ve **Performance** (RGB565) modları
> kaldırıldı: gerçek etkileri tam olarak beyaz dünyaya yol açan yüzeydi. Eski
> `AETHER_RENDER_MODE=lite|performance` değerleri `balanced` olarak okunur, kayıtlı
> ayarlar da `BALANCED`'a düşer.

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
EGL config: mode=balanced rgb=8/8/8 alpha=8 depth=24 stencil=8 samples=0 sample_buffers=0 visual=0x1
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

### 3.7 Çekirdek ayar ön ayarları (3.0)

`game/renderer/aether/AetherTuning.kt` + `AetherGpuProfiles.kt`

AetherRender 3, NG-GL4ES çekirdeğinin **gerçekten okuduğu** değişkenlerle üç ön
ayar sunar (değişken adları paketli `libng_gl4es.so` taranarak doğrulandı):

| Ön ayar | Yazdığı değişkenler | Ne yapar |
|---|---|---|
| Core default | — | Hiçbir şey yazmaz; 2.x davranışı birebir korunur |
| Throughput | `LIBGL_BATCH=1`, `LIBGL_USEVBO=1`, `LIBGL_RECYCLEFBO=1` | Toplu çizim + VBO kullanımı + FBO adı geri dönüşümü |
| Memory saver | `LIBGL_FORCE16BITS=1`, `LIBGL_NOVAOCACHE=1` | 16-bit doku (bellek yarıya iner, renk bantlanabilir) + VAO önbelleği kapalı |

- Varsayılan **Core default**'tur: 2.x'ten yükselen kullanıcıda davranış değişmez.
- Ön ayarlar birbirleriyle çakışmaz (birim testi) ve yalnızca `LIBGL_*` anahtarları
  kullanır (birim testi).
- Mali modeli `sysfs` üzerinden okunabilirse öneri kartı çıkar:
  G57/G68 → bellek tasarrufu, G76/G78/G710/G715 → verim; **bilinmeyen GPU'ya
  optimizasyon uydurulmaz**. Öneri ancak kullanıcı düğmeye basınca yazılır ve EGL
  modu asla 16-bit'e zorlanmaz.
- Bu değişkenler çeviricinin sürücüye verdiği biçimi değiştirir; Minecraft'ın chunk
  renderer'ını değiştirmez ve FPS garantisi yoktur.

### 3.8 VSync (3.0)

`aether_render.c` → `apply_swap_interval()`

- Launcher `AETHER_SWAP_INTERVAL=0|1` yazar (ayar: "VSync", varsayılan açık).
- Yüzey oluşturulduğunda ve bağlam current olduğunda `eglSwapInterval` çağrılır;
  katı sürücüler ikinci çağrıyı gerekli kılabilir.
- Değişken **yoksa** sürücü varsayılanına dokunulmaz (2.x ile aynı).
- Başarısızlık yalnızca loglanır (`eglSwapInterval(%d) rejected`), başlatma durmaz.
- VSync EGL modundan bağımsızdır: `native`/`compatibility` seçiliyken de uygulanır.

### 3.9 Çökme kurtarma (3C)

`game/renderer/recovery/RendererRecovery.kt`

- JVM başlamadan önce `filesDir/renderer_recovery/pending.properties` yazılır
  (renderer kimliği, adı, sürüm adı, zaman). Dosya `java.util.Properties`'tir;
  JVM testlerinde Android/JSON olmadan doğrulanabilir.
- Çıkış kodu **0** → deneme silinir, `last_good.properties` içine sürüm başına
  renderer yazılır, o renderer'ın hata sayacı sıfırlanır.
- Çıkış kodu **≠ 0** → `latest.log` kuyruğu OOM için taranır
  (`LaunchLogClassifier`). OOM varsa olay `MEMORY` olarak yazılır: renderer hatası
  sayılmaz, sayaç artmaz, "son çalışan" silinmez. Aksi halde olay `RENDERER` olur.
- Kod hiç raporlanmadıysa (native çökme/LMK) bir sonraki açılışta `ZLApplication`
  içinde `RendererRecoveryStartup` devreye girer ve olayı `UNKNOWN` olarak yazar.
- **Hiçbir durumda renderer ayarı otomatik yazılmaz.** Sıfırdan farklı çıkış veya
  kalan deneme, renderer'ın bozuk olduğunun kanıtı değildir; sessiz düşürme gerçek
  sorunu (bellek, sürücü) gizler.
- Öneri zinciri yalnızca gösterilir: **AetherRender → Krypton Wrapper
  (`opengles3`) → GL4ES (`opengles2`) → dur**. GLES ana sürümü
  `ActivityManager.deviceConfigurationInfo`'dan okunur; GLES 2 cihazda öneri
  doğrudan GL4ES olur.
- Renderer ayarlarındaki kurtarma kartı olayı gösterir: `MEMORY` için önerilen heap'i
  uygulama düğmesi, `RENDERER` için önerilen renderer'a geçme düğmesi, `UNKNOWN` için
  yalnızca bilgi. Kullanıcı onaylamadan hiçbir ayar değişmez.

## 4. Geri Dönüş (Fallback)

- `AETHER_RENDER` yoksa/0 ise tüm yeni kod eski davranışı birebir izler.
- AetherRender'ın config isteği boş dönerse stok attribute setine düşülür; mod
  seçimi başlatmayı kilitlemez.
- Yüzey politikası reddedilirse sessizce devam edilir.
- Renderer seçimi kullanıcı ayarıdır; AetherRender seçili değilken hiçbir yeni
  ortam değişkeni gönderilmez.

> Çökme sonrası kurtarma 3C ile geldi (bkz. 3.9) ve 3.0.1'de **yalnızca öneri**
> olacak şekilde daraltıldı. Cihazda gerçek bir native çökme üretilerek
> doğrulanmadı; birim testleri karar tablosunu ve dosya yaşam döngüsünü doğrular.

## 5. Cihaz Doğrulama

`docs/DEVICE_TEST_PLAN.md` uygulanır. En az:

1. AetherRender ile temiz dünya başlat, logcat'te yukarıdaki 4 satırı doğrula.
2. `26.2` (ve varsa `26.3-snapshot-3`) sürümünü NG-GL4ES çekirdeğiyle başlat;
   `LIBGL_ES=3`/`LIBGL_GL=31` ortamını ve GLES 3 bağlamını logdan doğrula.
3. Aynı sürüm/ayarlarla GL4ES'e dön; iki kez 10 dakikalık oturum karşılaştır.
4. Frametime dağılımı (p50/p95), CPU zamanı, RSS, batarya sıcaklığı kaydet.
5. Sonuç `TEST_REPORT.md`'ye cihaz/ROM/sürücü bilgisiyle yazılır.

**Ölçüm olmadan FPS iddiası yayınlanmaz.**

## 6. Dosyalar

| Dosya | Görev |
|---|---|
| `jni/aetherrender/aether_render.c` | Mod bazlı EGL seçimi (stencil zorunlu), yüzey politikası, telemetri |
| `jni/aetherrender/aether_render.h` | C arayüzü |
| `jni/aetherrender/aether_metrics.c/h` | Takas zaman damgalarından gerçek kare süresi (AetherMetrics) |
| `jni/ctxbridges/gl_bridge.c` | Entegrasyon noktaları |
| `jni/ctxbridges/egl_loader.c/h` | `eglSurfaceAttrib` yüklemesi |
| `game/renderer/renderers/AetherRenderer.kt` | Renderer kaydı, mod ortamı |
| `setting/enums/AetherRenderMode.kt` | Üç mod ve `AETHER_RENDER_MODE` değerleri |
| `ui/.../settings/AetherRenderModeCard.kt` | Mod seçim arayüzü |
| `game/performance/GpuInfo.kt` | Launcher tarafı EGL probe + Mali modeli |
| `game/performance/ThermalInfo.kt` | Batarya/thermal zone okuma |
| `game/performance/PerformanceMonitor.kt` | Canlı CPU/RAM/GPU/ısı ölçümü |
| `ui/.../performance/PerformanceCenterScreen.kt` | Canlı Performans Merkezi |
| `ui/.../home/PerformanceSummaryCard.kt` | Ana ekran performans kartı |
| `ui/.../DeviceInfoCard.kt` | Cihaz ve GPU paneli |
| `game/renderer/recovery/RendererRecovery.kt` | Çökme kurtarma, güvenli renderer zinciri, sürüm başına son çalışan |
| `ui/.../settings/RendererRecoveryCard.kt` | Kurtarma olayı + son çalışan renderer kartı |
| `test/.../CpuUsageTest.kt`, `GpuInfoTest.kt`, `ThermalInfoTest.kt`, `AetherRendererTest.kt`, `RendererRecoveryTest.kt` | Birim testleri |
