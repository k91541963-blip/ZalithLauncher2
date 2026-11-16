# Derleme ve Test

## 1. Önkoşullar

| Araç | Sürüm |
|---|---|
| JDK | 21 (CI: Temurin 21) |
| Android SDK | `compileSdk 37` (minor API 2), `minSdk 26`, `targetSdk 34` |
| Android NDK | `25.2.9519653` |
| Rust | stabil, `cargo-ndk` (PetLib için) |
| Gradle | wrapper (`9.5.0`) |

> Bu proje geliştirilirken kullanılan Android cihazda JDK/SDK yoktu. Bu yüzden
> doğrulanmış derlemeler **GitHub Actions** üzerinden alınır. Aşağıdaki yerel
> komutlar Android Studio kurulu bir makine içindir.

## 2. Yerel derleme

```bash
./gradlew ZalithLauncher:assembleDebug -Darch=arm64
```

- `-Darch`: `all`, `arm`, `arm64`, `x86`, `x86_64`
- APK: `ZalithLauncher/build/outputs/apk/debug/*.apk`
- Yalnızca ARM64 hedefleniyorsa süre ve boyut ciddi biçimde düşer.

Release:

```bash
./gradlew ZalithLauncher:assembleRelease -Darch=arm64
```

İmza için `ZalithLauncher/zalith_launcher.jks` ve `STORE_PASSWORD` /
`KEY_PASSWORD` ortam değişkenleri gerekir. Anahtar yoksa release derlemesi
imzalanmaz; bu repoda anahtar **yoktur**.

## 3. Birim testleri

```bash
./gradlew ZalithLauncher:testDebugUnitTest \
  --tests "com.movtery.zalithlauncher.game.performance.*" \
  --tests "com.movtery.zalithlauncher.game.diagnostics.*" \
  --tests "com.movtery.zalithlauncher.game.version.mod.dependency.*"
```

Kapsam:

- `performance/`: donanım profili, RAM önerisi, profiller, renderer uyumluluğu,
  GPU modeli ayrıştırma, sıcaklık ayrıştırma, AetherRender sözleşmesi
- `diagnostics/`: log analiz kuralları
- `dependency/`: Fabric/Quilt/Forge/NeoForge bağımlılık ve sürüm aralığı

## 4. CI akışı

İki hat vardır:

1. **Kaynak deposu:** `.github/workflows/push_ci.yml` (bu repoda). GitHub bu
   depoda Actions'ı devre dışı bıraktığı dönemlerde çalışmayabilir.
2. **Ayna/tetikleyici:** `k91541963-blip/Zlc` → `.github/workflows/build-apk.yml`.
   `aetherlite/phase3` dalını checkout eder, birim testlerini çalıştırır,
   ARM64 debug APK üretir ve `apk-latest` release'ine yükler:
   `AetherLite-arm64-debug.apk`

Akış:

1. JDK 21 + NDK + Rust hazırlanır.
2. Performans/teşhis/bağımlılık birim testleri çalışır (hata build'i durdurur).
3. `assembleDebug -Darch=arm64` çalışır.
4. Başarılıysa APK yayınlanır.

APK indirme:

```text
https://github.com/k91541963-blip/Zlc/releases/download/apk-latest/AetherLite-arm64-debug.apk
```

Durum:

```bash
curl -s "https://api.github.com/repos/k91541963-blip/Zlc/actions/runs?per_page=5"
```

## 5. APK kurulumu

```bash
adb install -r AetherLite-arm64-debug.apk
```

- Uygulama kimliği: `com.aetherlite.launcher`
- Debug kimliği: `com.aetherlite.launcher.debug`
- Eski "Nova" veya Zalith kurulumuyla veri paylaşmaz (farklı paket).

## 6. Sık karşılaşılan hatalar

| Hata | Neden / çözüm |
|---|---|
| `compileSdk 37 not found` | SDK manager'da API 37 + build-tools kurulu olmalı |
| NDK bulunamadı | `ndkVersion 25.2.9519653` tam sürümü kurulmalı |
| `libng_gl4es.so` yok | Bilinen sınır; AetherRender/GL4ES kullanın |
| Release imzalanmadı | `STORE_PASSWORD`, `KEY_PASSWORD` ve keystore gerekli |
| `eglSurfaceAttrib` derleme hatası | `egl_loader.h/c` güncel mi kontrol edin |

## 7. Doğrulama kuralı

- Bir özellik ancak CI derlemesi **ve** ilgili birim testi geçtiyse "tamam" sayılır.
- Renderer/frametime iddiaları için ayrıca cihaz ölçümü gerekir:
  [`DEVICE_TEST_PLAN.md`](DEVICE_TEST_PLAN.md).
