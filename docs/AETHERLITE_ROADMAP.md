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
| **AetherRender** | **VAR v1 (yeni)** | EGL config + `EGL_BUFFER_DESTROYED` + telemetri |
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
| `6f4df7d` | AetherLite kimliği + AetherRender v1 | CI kaydı: `docs/TEST_REPORT.md` |

## 6. Bilinen Kısıtlar

- Bu geliştirme ortamında Android SDK/NDK yok; derlemeler GitHub Actions'ta yapılır.
- **Henüz gerçek Mali cihazda oyun içi ölçüm yapılmadı.** AetherRender'ın EGL seçimi
  ve tampon politikası kod olarak doğrulanabilir, ancak FPS/frametime iddiası için
  cihaz testi zorunludur.
- NG-GL4ES ("Krypton Wrapper") `libng_gl4es.so` bu forkta paketlenmiyor; seçilirse
  başlatma başarısız olabilir. AetherRender bilinçli olarak paketli `libgl4es_114.so`
  kullanır.
- Vulkan/Zink yolu yalnızca gerçek Mesa sürücüsü olan cihazlarda anlamlıdır.

## 7. Sonraki Adımlar (öncelik sırası)

1. Mali cihaz test matrisini çalıştır, `TEST_REPORT.md`'yi ölçümlerle doldur.
2. AetherRender için otomatik güvenli geri dönüş: başlatma çökmesi sonrası GL4ES'e dön.
3. Modpack dışa aktarma ve oyun dosyası tam doğrulama.
4. Oyun içi sıcaklık/frametime telemetrisini launcher paneliyle birleştirme.
5. İmzalı release hattı ve sürüm notları.
