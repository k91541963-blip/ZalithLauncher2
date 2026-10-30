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
| Çökme logu | **KISMİ** | `crashlogs/` (mclogs yükleme), analiz yok |
| Donanım profili / RAM önerisi | **YOK** | — |
| Performans profilleri (kaydet/geri al) | **YOK** | — |
| Renderer uyumluluk ön kontrolü | **YOK** | — |
| Mod bağımlılık kontrolü | **YOK** | — |
| Log analizi (neden + kanıt + çözüm) | **YOK** | — |
| Düşük donanım arayüz modu | **YOK** | — |

## 4. Aşamalar

- **Aşama 1 — Anlama:** ✅ tamamlandı (bu belge)
- **Aşama 2 — Fork altyapısı:** isim, paket adı, ikon, renk, GPL atıf; mevcut işlevlerin korunduğunun doğrulanması
- **Aşama 3 — Performans + kontrol:** donanım profili, RAM önerisi, performans profilleri, renderer ön kontrolü, düşük donanım modu
- **Aşama 4 — Mod + log:** bağımlılık kontrolü, profil bazlı mod yönetimi, log analiz motoru
- **Aşama 5 — Test + dağıtım:** gerçek cihaz testleri, sürüm notları, APK yayını

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
