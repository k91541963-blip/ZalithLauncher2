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

## 2. Neden FPS Yok?

FPS, ortalama FPS, %1 low ve frametime **yalnızca Minecraft çalışırken** vardır ve
oyunun kendi kare döngüsünde ölçülür. Launcher kapalıyken bu değerleri üretmek
mümkün değildir; bu yüzden Performance Center uydurma bir grafik çizmez.

- Oyun içi FPS grafiği ve overlay mevcut Zalith altyapısında bulunur ve oyun
  çalışırken gerçek kareleri sayar.
- Chunk yükleme ve render thread durumu da oyun içi telemetridir; launcher bunları
  dışarıdan okuyamaz.
- Bu ekran, oyun kapalıyken CPU/RAM/GPU/ısı ölçer; FPS için "Oyun içi FPS ve
  frametime" kartında bunu açıkça söyler.

## 3. Yenileme Davranışı

- Performance Center görünürken **2 saniyede** bir örnek alır.
- Ana ekrandaki performans kartı görünürken **5 saniyede** bir örnek alır.
- Ekran görünmez olduğunda `LaunchedEffect` iptal edilir; arka planda ölçüm döngüsü
  çalışmaz.
- İlk örnekte CPU yüzdesi yoktur (delta için ikinci örnek gerekir) ve "Ölçülüyor…"
  yazar. Bu bir hata değildir.

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

## 6. Dosyalar

| Dosya | Görev |
|---|---|
| `game/performance/CpuUsage.kt` | Saf `/proc/stat` ayrıştırma ve delta (birim testli) |
| `game/performance/PerformanceMonitor.kt` | CPU/RAM/PSS/heap/GPU/ısı anlık örneği |
| `ui/.../performance/PerformanceCenterScreen.kt` | Canlı ekran |
| `ui/.../home/PerformanceSummaryCard.kt` | Ana ekran özet kartı |
| `ui/.../settings/PerformancePresetCard.kt` | Profil uygulama |
| `test/.../CpuUsageTest.kt` | Ayrıştırma/delta testleri |
