# Mali Cihaz Test Planı

> Amaç: AetherRender ve launcher'ın gerçek cihazlarda **çökmeden** çalıştığını
> kanıtlamak ve AetherRender'ın ölçülebilir bir fark yaratıp yaratmadığını
> önyargısız kaydetmek. Ölçüm yoksa iddia yok.

## 1. Cihaz Matrisi

| Cihaz / GPU | Durum | Not |
|---|---|---|
| Mali-G57 (ör. Dimensity 700/810) | ⏳ bekliyor | Öncelik: düşük segment |
| Mali-G68 (ör. Dimensity 900/920) | ⏳ bekliyor | |
| Mali-G76 (ör. Helio G95, Exynos 9611) | ⏳ bekliyor | |
| Mali-G78 (ör. Exynos 2100) | ⏳ bekliyor | |
| Diğer Mali (G52/G77/G710…) | ⏳ bekliyor | |

Her cihaz için kaydedilecekler:

- Üretici/model, Android sürümü, ROM, kernel
- GPU sürücüsü: `adb shell dumpsys SurfaceFlinger | grep -i gles` veya
  AetherLite > Renderer > Cihaz ve GPU paneli
- RAM, depolama boş alanı
- Ekran çözünürlüğü ve yenileme hızı

## 2. Kurulum

1. CI'dan `AetherLite-arm64-debug.apk` indir.
2. `adb install -r AetherLite-arm64-debug.apk`
3. Çevrimdışı hesap oluştur (Microsoft girişi gerekmez).
4. Aynı Minecraft sürümünü iki kez kur: `aether-test` (AetherRender) ve
   `gl4es-test` (GL4ES). Aynı seed, aynı ayarlar.

## 3. Senaryolar

Her senaryo en az **10 dakika**, cihaz şarjda değil, parlaklık sabit:

1. Boş dünya (superflat, render distance 8)
2. Köy / kalabalık entity alanı
3. Yüksek chunk yükleme (render distance 16, hızlı uçuş)
4. Modlu (Fabric + Sodium benzeri 5–10 mod)
5. Fabric modpack (60+ mod)
6. Düşük RAM baskısı (arka planda 2–3 uygulama)
7. Isınma sonrası (20 dk oyun + 5 dk bekleme, tekrar ölçüm)

## 4. Ölçüm Protokolü

### 4.1 Logcat doğrulaması (AetherRender)

```bash
adb logcat -c
# oyunu başlat
adb logcat -s AetherRender
```

Beklenen:

- `AetherRender enabled`
- `EGL config: depth=24 samples=0 sample_buffers=0 ...`
- `Surface swap behaviour: EGL_BUFFER_DESTROYED` **veya** sürücü reddiyse uyarı
- `GPU vendor=... renderer=Mali-... version=...`

Bu satırlar yoksa AetherRender etkin değildir; renderer ayarını kontrol et.

### 4.2 Frametime

Oyun içi FPS grafiğini kullan (AetherLite oyun menüsü) veya:

```bash
adb shell dumpsys gfxinfo com.aetherlite.launcher reset
# 60 sn oyna
adb shell dumpsys gfxinfo com.aetherlite.launcher
```

Kaydet: **p50 / p95 / p99 frametime**, jank yüzdesi, ortalama FPS.
Tek bir "max FPS" değeri raporlanmaz.

### 4.3 CPU

```bash
adb shell top -H -b -n 1 -p $(adb shell pidof com.aetherlite.launcher)
```

Kaydet: toplam CPU %, en yoğun 3 thread, `RenderThread` / `Server thread`.

### 4.4 Bellek

```bash
adb shell dumpsys meminfo com.aetherlite.launcher
```

Kaydet: PSS, Java heap, native heap, GPU memory (varsa).

### 4.5 Sıcaklık / ısınma

```bash
adb shell dumpsys thermalservice
adb shell cat /sys/class/thermal/thermal_zone*/temp
```

AetherLite panelindeki batarya/SoC sıcaklığıyla karşılaştır. 20. dakikada
tekrar ölç; performans düşüşünü frametime ile birlikte raporla.

## 5. Karşılaştırma Kuralları

- Aynı cihaz, aynı sürüm, aynı seed, aynı render distance, aynı mod listesi.
- Her senaryo **3 kez**; medyan değerler raporlanır.
- AetherRender ve GL4ES oturumları arasında cihaz soğumalı (en az 10 dk).
- Fark %5'in altındaysa "ölçülebilir fark yok" yazılır.
- Çökme, siyah ekran, doku bozulması → **başarısız**; FPS'ten önce gelir.

## 6. Sonuç Şablonu

```text
Cihaz:
GPU / sürücü:
ROM / Android:
Minecraft + modlar:
Senaryo:
Renderer: AetherRender | GL4ES
Süre:
p50 / p95 / p99 frametime:
Ortalama FPS:
CPU %:
PSS:
Batarya / SoC °C:
Çökme / görsel hata:
Sonuç: geçti | kaldı | ölçülebilir fark yok
```

Sonuçlar `docs/TEST_REPORT.md` içine eklenir.
