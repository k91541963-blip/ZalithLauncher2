# AetherLite Performance Center — Ölçülen Veriler ve Sınırlar

> Bu ekran yalnızca bu cihazda **gerçekten okunabilen** değerleri gösterir. Bilinmeyen
> değer "Bilinmiyor" olarak kalır; hiçbir metrik tahminle veya animasyonla doldurulmaz.

## 1. Ne Ölçülüyor?

| Gösterge | Kaynak | Not |
|---|---|---|
| CPU yükü | `/proc/stat` toplam `cpu` satırı, iki örnek arasındaki fark | `iowait` boşta sayılır; `guest` zaten `user` içinde olduğu için eklenmez |
| Çekirdek sayısı | `Runtime.availableProcessors()` | Android'in gördüğü değer |
| Sistem RAM'i | `ActivityManager.MemoryInfo` (`totalMem`, `availMem`, `lowMemory`) | Kullanılan = toplam − kullanılabilir |
| Launcher PSS | `Debug.MemoryInfo.totalPss` | Süreç için gerçek oransal bellek |
| Java heap | `Runtime.totalMemory()` / `freeMemory()` / `maxMemory()` | Yalnızca launcher süreci |
| GPU modeli | EGL pbuffer EGL/GLES probe (`GpuInfoReader`) + Mali `sysfs` yedeği | Mali-G57/G68/G76/G78/G710/G715 isimden sınıflanır |
| GPU sürücüsü | `GL_VENDOR` + `GL_VERSION` | Sürücü sürümü garanti edilmez |
| SoC / batarya sıcaklığı | `/sys/class/thermal/thermal_zone*/temp`, batarya broadcast'i | Okunamıyorsa `null` |
| Isıl durum | `PowerManager.currentThermalStatus` (Android 10+) | 0–6 arası platform değeri |
| AetherRender modu | Launcher ayarı | Yalnızca bilgi; oyun içi FPS değil |
| Oyun içi kare süresi | AetherMetrics (`jni/aetherrender/aether_metrics.c`), her takas (swap) zaman damgası | Oyun çalışırken gerçek delta; 1 sn'den uzun boşluklar (duraklatma/yükleme) atılır |
| Ortalama FPS, %1 low, P99 | Aynı kare sürelerinden hesaplanır | Harmonik ortalama; %1 low = en yavaş %1 karenin ortalaması |

## 2. FPS Nereden Geliyor?

FPS değerleri **yalnızca Minecraft çalışırken** üretilir, ama artık launcher bunları
kendisi ölçer:

- `aether_metrics_on_frame()` her kare takasında `CLOCK_MONOTONIC` zaman damgası alır
  ve iki kare arasındaki farkı 2048 girişlik halka tampona yazar
  (`jni/aetherrender/aether_metrics.c`). Ek GL çağrısı yoktur, render durumu değişmez.
- Oyun ekranı açıkken `GameSessionRecorder` saniyede bir bu tamponu boşaltır, aynı
  anda CPU/RAM/sıcaklık örneği alır. Oyun ekranı kapanınca oturum özeti
  `filesDir/aethermetrics/last_session.json` dosyasına yazılır.
- Performance Center açıldığında bu dosya okunur: ortalama FPS, %1 low, P99 kare
  süresi, 30 FPS altı kare oranı, ortalama CPU, tepe RAM, tepe SoC sıcaklığı ve 10
  saniyelik pencerelerden oluşan FPS grafiği gösterilir.
- Oyun sırasında canlı FPS oyun içi katmanın işidir (mevcut `FpsChart`); launcher
  paneli oyun kapalıyken sayı üretmez, yalnızca ölçülmüş son oturumu gösterir.
- Chunk yükleme ve render thread durumu launcher tarafından okunamaz; bu yüzden
  ekranda böyle bir değer yoktur.

## 3. Yenileme Davranışı

- Performance Center görünürken **2 saniyede** bir örnek alır.
- Ana ekrandaki performans kartı görünürken **5 saniyede** bir örnek alır.
- Ekran görünmez olduğunda `LaunchedEffect` iptal edilir; arka planda ölçüm döngüsü
  çalışmaz.
- İlk örnekte CPU yüzdesi yoktur (delta için ikinci örnek gerekir) ve "Ölçülüyor…"
  yazar. Bu bir hata değildir.
- Oturum kaydı yalnızca oyun ekranı açıkken **1 saniyede** bir örnek alır; oyun
  kapalıyken hiçbir ölçüm döngüsü çalışmaz.
- Kayıt sırasında en fazla 240.000 kare süresi saklanır (60 FPS'te ~66 dakika).
  Sınır aşılırsa özet bunu açıkça belirtir; sessizce kırpılmaz.

## 4. Profiller

`PerformancePresetCard`, donanım profiline göre Ultra Lite / Lite / Balanced /
High Performance benzeri hazır değerleri uygular. Uygulanan preset **yalnızca**
RAM, JVM argümanları, çözünürlük ölçeği ve (varsa) renderer kimliğini yazar.
Renderer'ı kullanıcı seçmedikçe değiştirmez ve FPS vaat etmez.

## 5. Bilinçli Sınırlar

- Android 8+ bazı çekirdeklerde `/proc/stat` erişimini kısıtlar; bu durumda CPU
  yüzdesi "Ölçülüyor…" olarak kalır, tahmin edilmez.
- `Debug.getMemoryInfo` bazı cihazlarda PSS döndürmez; o satır "Bildirilmiyor" olur.
- Sıcaklık sensörleri OEM'e göre gizlenebilir; alternatif sensör uydurulmaz.
- GPU modeli sürücü dizesinden okunur; isim tabanlı Mali sınıfı bir **heuristik**
  başlangıç noktasıdır, benchmark değildir.
- Gerçek Mali cihazda oyun içi FPS/frametime karşılaştırması henüz yapılmadı;
  ayrıntı: [`TEST_REPORT.md`](TEST_REPORT.md) ve [`DEVICE_TEST_PLAN.md`](DEVICE_TEST_PLAN.md).
- Kare süresi ölçümü takas (swap) anını ölçer; Minecraft'ın kendi iç render
  süresini ayrıştırmaz. Bu yüzden "render thread süresi" gibi bir değer gösterilmez.

## 6. Dosyalar

| Dosya | Görev |
|---|---|
| `game/performance/CpuUsage.kt` | Saf `/proc/stat` ayrıştırma ve delta (birim testli) |
| `game/performance/PerformanceMonitor.kt` | CPU/RAM/PSS/heap/GPU/ısı anlık örneği |
| `game/performance/FrameTimeStats.kt` | Kare sürelerinden ortalama FPS/%1 low/P99/jank hesabı (birim testli) |
| `game/performance/GameSessionSummary.kt` | Oturum toplayıcı, özet modeli ve JSON deposu (toplayıcı birim testli) |
| `game/performance/GameSessionRecorder.kt` | Oyun sırasında 1 sn'lik örnekleme ve oturum kaydı |
| `jni/aetherrender/aether_metrics.c` | Takas zaman damgalarından gerçek kare süresi halkası |
| `ui/.../performance/PerformanceCenterScreen.kt` | Canlı ekran + son oturum kartı |
| `ui/.../home/PerformanceSummaryCard.kt` | Ana ekran özet kartı |
| `ui/.../settings/PerformancePresetCard.kt` | Profil uygulama |
| `test/.../CpuUsageTest.kt` | Ayrıştırma/delta testleri |
| `test/.../FrameTimeStatsTest.kt` | Percentile ve %1 low testleri |
| `test/.../GameSessionAccumulatorTest.kt` | Oturum toplama/sınır testleri |
