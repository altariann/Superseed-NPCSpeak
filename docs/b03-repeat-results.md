# B03 — onaylı tek model tekrarı

1 Kasım 2026. **Tek tekrar başarısız.** Kullanıcı onayıyla yalnız dondurulmuş
B03 sorusu bir kez gönderildi. Otomatik retry, B04–B10 veya Aşama C yok.
Yeni kod düzeltmesi, canlı stop/başlatma, JAR veya gerçek ayar değişikliği yapılmadı.

## Koşullar

- Önceki gerçek Gemma modelinin etiketi ve digest'i doğrulandı. Modelin
  parameters/template/system çıktısı önceki özel metadata ile aynıydı.
  CPU, 4096 bağlam ve 160 çıktı token sınırı korundu; Modelfile değiştirilmedi.
- Gövdede yalnız `model`, `stream=false`, `messages`; ek inference seçeneği yok.
  Aynı dondurulmuş B03 sorusu, soru dosyası ve gönderilen request hash'i doğrulandı.
- Aynı özel arşiv kopyasındaki 221 wiki dosyasının hash'i ve gerçek NPC
  yapılandırmasının hash'i eşleşti. NPC dosyası yalnız salt okunur kullanıldı.
  İki atomik makam kaydı kullanıldı; kaynak JSON'u 455 karakter, wiki alıntısı 0.
- Ön kontrolde boş fiziksel RAM 5606 MB; üç CPU örneğinin ortalaması %3,33.
  Çağrının kendi ölçümünde RAM başlangıçta 5567 MB, sonrasında 1927 MB idi.
  Paper süreci korunuyordu; canlı TPS veya oyuncu gecikmesi ölçülmedi.
- Tek HTTP isteği `127.0.0.1` üzerinden, proxy/redirect olmadan, öncekiyle
  aynı 120 saniye timeout kullanılarak gönderildi.

## Yanıt incelemesi

| Sürüm/cümle | Kaynakla karşılaştırma |
| --- | --- |
| Eski, 1 | Sorulan kişinin iki makamını doğru aktarıyor. |
| Eski, 2 | Kaynakta başka kişi için doğrulanmış bağlılığı yanlış özne/yönle aktarıyor; desteklenmiyor. |
| Yeni, 1 | Sorulan kişinin makamını birinci şahıs anlatıcıya taşıyor; anlatıcı için makam ve katkı iddiası kaynakta yok. |
| Yeni, 2 | Diğer makamı da birinci şahısla üstlenip sorulan kişiyle destek/koordinasyon ilişkisi ekliyor; iki kaynakta bu ilişki yok. |

Önceki bağlılık cümlesi tekrarlanmadı. Yeni yanıtta açık bağlılık, üstlük veya
yöneticilik cümlesi bulunmaması yeterli değildir: **destek/koordinasyon şeklinde
başka bir kaynaksız ilişki üretildi.** Makam adlarının doğru geçmesi, doğru
kişiye atfedildikleri anlamına gelmez. NPC anlatıcısı ile sorulan kişinin
karışması gözlenen bir sorundur; modelin içsel niyeti kesinleştirilmedi.

Ham yanıtlar, birebir kaynaklar, prompt ve cümle bazında ayrıntılı karşılaştırma
yalnız özel yerel alandaki `comparison.md` / `comparison.json` içinde kalır;
GitHub'a gönderilmez. Bu değerlendirme asistana aittir; bağımsız insan
doğrulaması değildir.

## Süre ve sınırlar

| Ölçüm | Eski B03 | Tek yeni tekrar |
| --- | ---: | ---: |
| Uçtan uca süre | 8,28 sn | 24,26 sn |
| Model yükleme | 0,004 sn | 5,63 sn |
| Girdi token | 964 | 1213 |
| Çıktı token | 24 | 49 |
| Sonlanma | stop | stop |

Yeni prompt işleme 12,05 sn, üretim 6,55 sn. Timeout/çıktı kesilmesi yok;
girdi sayısı 4096 bağlam sınırının altında. Soğuk yükleme ve prompt farkları
nedeniyle bu iki gözlemden performans sonucu çıkarılmaz. Kaynak küçülürken
ek kişiliğin yüklenmesi ve kural değişikliği toplam girdi tokenını artırdı.

Kaynak yapısı, prompt kuralı ve kişilik yüklenmesi birlikte değiştiği için
sonuç tek bir nedene bağlanamaz. **Tek başarılı tekrar bile kalıcı doğruluk
kanıtı olmazdı; bu tekrar zaten başarısızdır.** B03 hatası açık kalır.
Bu tur yalnız ölçüm/raporlama yapıldı; Java/PowerShell kodu değişmedi ve
önceki çevrimdışı testler yeniden çalıştırılmış gibi sunulmadı.
Çalışma burada durduruldu; ek model isteği veya dağıtım başlatılmadı.
