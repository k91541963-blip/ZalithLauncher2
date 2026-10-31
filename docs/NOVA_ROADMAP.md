# NovaLauncher — Fork Yol Haritası

> Zalith Launcher 2 tabanlı, düşük donanım odaklı Android Minecraft: Java Edition launcher fork'u.
> Bu belge **Aşama 1** (kod tabanını anlama) çıktısıdır. Kod yazılmadan önce gerçek durum tespiti.

## 1. Yasal Durum (kritik)

| Konu | Tespit | Sonuç |
|---|---|---|
| Lisans | **GPL-3.0** (`LICENSE`) | Fork GPL-3.0 olarak kalmalı, kaynak açık yayınlanmalı |
| Atıf | Orijinal: Zalith Launcher / Movtery | README + hakkında ekranında atıf korunmalı |
| Marka | "Zalith" adı ve logosu | Forkta **kullanılmamalı** (isim, ikon, paket adı değişmeli) |
| Değişiklik günlüğü | GPL-3.0 gereği | Değiştirilen dosyalar ve tarih belirtilmeli |

## 2. Mevcut Mimari

- Dil: Kotlin + Jetpack Compose, Gradle 9.5, AGP 9.x, KSP, Hilt
- Modüller: `ZalithLauncher` (uygulama), `LWJGL` (native köprüler), `Terracotta` (oturum/giriş), `InputMap` (girdi eşleme), `CardGrid`, `ColorPicker`, `LayerController`, `PetLib`, `Guide`
- Uygulama kodu: ~808 Kotlin dosyası
- `minSdk 26`, `targetSdk 34`, `compileSdk 37`
- Paket: `com.movtery.zalithlauncher` + `.v2` suffix
- Marka bilgileri tek yerde: `ZalithLauncher/gradle.properties`

## 3. İstenen Özelliklerin Mevcut Durumu

| İstenen | Durum | Kanıt |
|---|---|---|
| Çevrimdışı hesap | **VAR** | `game/account/offline/`, `OfflineYggdrasilServer.kt` |
| Microsoft girişi | **VAR** | `game/account/microsoft/` |
| Renderer seçenekleri | **VAR** | GL4ES, NG GL4ES, Kopper-Zink, VirGL, Freedreno, Panfrost + eklenti API v2 |
| Kontrol düzeni özelleştirme | **VAR** | `InputMap` modülü |
| Mod yükleyiciler | **VAR** | fabriclike (Fabric/Quilt), forgelike (Forge/NeoForge), OptiFine |
| Mod indirme | **VAR** | Modrinth + CurseForge istemcileri |
| Türkçe arayüz | **VAR** | `values-tr` |
| Çökme logu | **KISMİ** | `crashlogs/` (mclogs yükleme) + `game/diagnostics/` analiz motoru, `LogViewScreen` bandı |
| Donanım profili / RAM önerisi | **VAR** | `game/performance/HardwareProfile.kt`, `MemoryAdvisor.kt`, bellek ayarı altında öneri |
| Performans profilleri (kaydet/geri al) | **VAR** | `PerformancePresets` + Oyun ayarlarında "Performans profili" kartı |
| Renderer uyumluluk ön kontrolü | **VAR** | `RendererCompatibility` + renderer listesinde rozet |
| Mod bağımlılık kontrolü | **VAR (Fabric/Quilt)** | `game/version/mod/dependency/`, mod listesinde uyarı şeridi; Forge henüz yok |
| Log analizi (neden + kanıt + çözüm) | **VAR** | `LogAnalyzer` (15 kural, kanıt satırı, `Caused by` zinciri), log görüntüleyici bandı |
| Düşük donanım arayüz modu | **KISMİ** | Düşük donanım profili bellek/GC/çözünürlüğü düşürür; arayüz animasyonlarını azaltan mod henüz yok |

## 4. Aşamalar

- **Aşama 1 — Anlama:** ✅ tamamlandı (bu belge)
- **Aşama 2 — Fork altyapısı:** ✅ isim `Nova Launcher`, `applicationId com.nova.launcher`, adaptive + monochrome ikon, GPL atıf; derleme doğrulandı
- **Aşama 3 — Performans + kontrol:** ✅ donanım profili, RAM önerisi, performans profilleri, renderer ön kontrolü; düşük donanım arayüz modu kısmi
- **Aşama 4 — Mod + log:** ✅ log analiz motoru, Fabric/Quilt bağımlılık denetimi; Forge bağımlılıkları ve profil bazlı mod yönetimi açık
- **Aşama 5 — Test + dağıtım:** cihaz testleri, sürüm notları, APK yayını

### Doğrulanmış derlemeler

| Commit | İçerik | Sonuç |
|---|---|---|
| `9f50cde` | performans çekirdeği + bellek önerisi | ✅ |
| `ccac850` | renderer uyumluluk rozeti | ✅ |
| `22dc028` | log analiz motoru (çekirdek + 11 test) | ✅ |
| `05cec0c` | log görüntüleyici analiz bandı | ✅ |
| `ccf9bc5` | performans profili kartı | ✅ |
| `28c8f22` | performans profili testleri | ✅ |

## 5. Kısıtlar

- Geliştirme cihazında **JDK ve Android SDK yok** → yerel derleme mümkün değil.
  - Seçenek A: GitHub Actions ile derleme (repoda `build.yml` / `release_ci.yml` mevcut)
  - Seçenek B: cihaza JDK 21 + Android SDK kurulumu (yüzlerce MB)
- `compileSdk 37` çok yeni; CI'da SDK kurulumu doğrulanmalı.
- Native renderer kütüphaneleri (LWJGL/patches) derleme sırasında indiriliyor; ağ erişimi gerekir.

## 6. Doğrulama Kuralları

- Her aşama sonunda: derleme çıktısı + hangi dosyaların değiştiği + geri alma yöntemi.
- Doğrulanmamış özellik "tamamlandı" sayılmaz.
- Orijinal launcher'ın çalışan özellikleri bozulmaz.
