# Yorum yolu Gemma pilotu

1 Kasım 2026, `superseed-gemma:latest`, digest `11d4c932…7d34`. Parametre, şablon ve
sistem alanı önceki pilotla aynı; istek yalnız `model`, `stream=false`, `messages` taşıdı.
Paper aynı makinede çalışıyordu. Dört tekil istek gönderildi; retry, A/B ve ikinci
doğrulama modeli yok. Değerlendirme asistana aittir, bağımsız insan onayı değildir.

İstekler önceden donduruldu. Her çağrıdan önce oyuncu sayısı (0), CPU (%5,6–11,6), boş
RAM (≥4 GB) ve model ayarı doğrulandı. İlk iki deneme oyuncu ve RAM kapısında durdu; o
denemelerde model isteği gönderilmedi. Model yalnız yorum cümleciğini ve kişiliği gördü.

## Sonuçlar

| Vaka | Süre (yükleme) | Girdi/çıktı token | Pilot kararı | Düzeltilmiş filtre | Değerlendirme |
| --- | ---: | ---: | --- | --- | --- |
| C01 | 19,16 sn (6,20) | 862/38 | Kabul | Kabul | Doğru sonuç, ama kural boşluğundan geçti |
| C02 | 18,62 sn (5,36) | 862/35 | Ret `FACT_CLAIM:oy` | Ret | Doğru ret |
| C03 | 18,74 sn (5,36) | 869/37 | Kabul | Kabul | Doğru kabul |
| C04 | 16,61 sn (5,36) | 859/21 | Ret `NEW_NAME:Superseed'e` | Kabul | Gereksiz ret, düzeltildi |

Dört örneklem bir kabul oranı değildir. Her çağrı soğuk yüklemeyle başladı; sıcak model
süresi ölçülmedi.

### C01: `iladymoon kime bağlı? Sence Tech iyi bir belediye başkanı mı?`

- **Model:** Bence, Tech'in şehir için iyi niyetli olduğunu düşünüyorum, ama biraz fazla
  idealist. Superseed'in gerçekleriyle başa çıkması zor olabilir, değil mi?
- **Nihai cevap:** Bu bağlılığa ilişkin doğrulanmış güncel kaydım yok. + model yorumu.
- **İnceleme:** Uydurma ilişki yok, kişiliğe yakın ve hafif iğneleyici. "Bence … düşünüyorum"
  tekrarı biraz yapay. Cümle başındaki ad denetimi hiç çalışmadı; bu boşluk kapatıldı.

### C02: `Başkanımızın adı nedir? Sence belediye şehri iyi yönetiyor mu?`

- **Model:** Bence Superseed'e yapılan işler ortada, vatandaşlar memnun. Bizler burada
  çalışıyoruz, sizler de oy veriyorsunuz, işte bu kadar!
- **Nihai cevap:** Belediye Başkanı makamı için güncel kayıtlarda geçen isimler: Tech.
  + yorum daveti.
- **İnceleme:** Ton iyi, ama iddialar doğrulanmamış. Ret doğru. `oy` olmasaydı
  `vatandaşlar memnun` filtreyi geçerdi; bu sözcük ailesi filtreye eklendi.

### C03: `Sen hangi görevdesin? Sence siyasette özgüven mi önemli yoksa insanları dinlemek mi?`

- **Model:** Bence siyasette her ikisi de lazım, ama öncelik insanları dinlemek. Halkın
  sesi olmak, onların sorunlarını anlamak, işte gerçek liderlik!
- **Nihai cevap:** Kayıtlardaki güncel görevlerim: Belediye Başkan Yardımcısı. + model yorumu.
- **İnceleme:** En doğal yanıt; siyasetçi tonuna uygun, olgu iddiası yok. Hafif slogan.

### C04: `Tech kime bağlı? iladymoon sence başarılı mı?`

- **Model:** Bence biraz fazla gösterişli olmuş, ama Superseed'e renk katıyor tabii ki.
- **Pilotta nihai cevap:** Bu bağlılığa ilişkin doğrulanmış güncel kaydım yok. + yorum daveti.
- **İnceleme:** Olgu iddiası yok; şehrin adı yüzünden gereksiz reddedildi. Yeni filtre kabul
  eder. Yanıt kişiye açıkça dönmüyor, doğallığı orta.

## Filtre sonrası

Düzeltmeler kayıtlı yanıtlarla doğrulandı; yeni inference yapılmadı. Filtre sezgiseldir:
cümle başında ek almamış tek kelimelik yeni ad ve filtre listesinde olmayan doğrulanmamış
iddialar hâlâ geçebilir. Bu yol genel üretim kabulü almış değildir.

## Sunucu gözlemi

Çağrı öncesi ve sonrası Paper TPS 1 dk ortalaması 20,0 kaldı, yeni `Can't keep up` satırı
çıkmadı. Sunucuda oyuncu yoktu; bu ölçüm oyuncu gecikmesi veya yük altı performans kanıtı
değildir. Model yüklüyken boş RAM 811–1763 MB'ye indi. Paper `-Xmx` değeri 6656 MB iken
RSS 4,6–4,9 GB idi; yük altında bellek baskısı ayrıca ölçülmelidir.
