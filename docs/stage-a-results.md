# AI'sız Aşama A — gerçek arşiv doğrulama raporu

1 Kasım 2026. Yalnız gerçek wiki araması çalıştırıldı. Ollama/Gemma, RCON,
oyuncu sohbetleri veya canlı Minecraft/JAR değişimi kullanılmadı. Mevcut
development dalı ve PR #2 korunur. Bu rapor model testine veya dağıtıma onay değildir.

## Veri ve bağımsız beklentiler

221 `.txt` ve yalnız `knowledge.yml` özel alana kopyalandı. Kopyalar salt okunur;
222 dosyanın kaynak/kopya SHA-256 değerleri işlem sonunda da aynı. Gerçek sunucu
config'i, parolalar, oyuncu/NPC/Citizens verileri veya loglar kopyalanmadı.

- 221/221 geçerli UTF-8; boş gövde, 160 KB üstü dosya, U+FFFD veya kullanılan
  mojibake işaretleri saptanmadı. Bu, metinlerin olgusal olarak güncel olduğunu kanıtlamaz.
- Tüm dosyalarda bir başlık satırı ve kullanılabilir pasaj var.
- Bir normalize başlık çifti ve bir aynı-gövde çifti bulundu. Kayıtlar silinmedi,
  birleştirilmedi; aynı-gövde çifti yönlendirme içeriği taşıyor.
- 16 açık wiki yönlendirmesinin yerel hedefi bulunuyor. Bir yönetici alias grubu
  yükleniyor; mevcut yapılandırmada alias çakışması yok. Aynı normalize başlık
  ile yönetici alias çakışması birbirinden ayrı sayıldı.

İlk 30 sorunun kaynak/kanıt beklentileri motor ilk kez çalıştırılmadan önce
wiki metinlerinden hazırlandı; dondurulan vaka dosyası değiştirilmedi.
Bu beklentileri asistan inceledi; insan onayı yoktur.

750 vakada planın dağılımı kullanıldı: 221 tam başlık, 221 varyant, 221 ayrıntı,
60 negatif, 15 güncel/tarihsel/ilişki ve 12 talimat-yapısı kontrolü.
**735 vaka otomatik ve insan incelemesiz; 15 vaka asistanın kaynak/önceki kullanıcı
doğrulamasına dayalı etiketi; HUMAN_VERIFIED=0.** Ayrıntı grubunun otomatik soruları
ilk kaynak ifadesine dayalı genel tanım sorularıdır; insanın yazdığı özgül ayrıntı
sorularının yerini tutmaz. 12 yapısal kontrol modelin saldırılara dirençli olduğunu
kanıtlamaz: bu koşudaki 12 sorguya talimat eklendi; kaynak/persona/hafıza
saldırılarının bütün matrisi veya model etkisi ölçülmedi. Negatifler sentetik
ad mutasyonu/birleşimi/yokluk kontrolleridir ve bu koşuda açık alıntılı ad
biçimindedir; günlük serbest soruların dağılımını temsil etmez. Kaynakta
bulunmayan negatif adlar önce metinlerde tarandı; kaynakta
çıkan bir aday motor çalışmadan reddedilip değiştirildi.

Arama kodu kopyalanmadı: test-kaynak kümesindeki `LoreEvaluation` doğrudan
üretim `KnowledgeBase.load` ve `LoreEngine.retrieve` kodunu çağırır. Normal
`build` gerçek arşiv veya modele bağımlı değildir. Ham veri/raporlar yalnız
Git'in yok saydığı özel çalışma alanındadır; bu belgede toplu sonuçlar vardır.

## Ölçülen beklenti eşleşmeleri

Bu sayılar **kesin doğruluk veya model başarı oranı değil**, dondurulan
beklentilerle otomatik eşleşmedir. Kontrol edilmemiş etiketler altın veri sayılmaz.

| Koşu | Eşleşme | Oran |
| --- | ---: | ---: |
| İlk 30 soru, ilk motor | 27/30 | %90,00 |
| Aynı 30 soru, düzeltmeler sonrası | 30/30 | %100,00 |
| 750 vaka, ön test düzeltmeleri sonrası | 736/750 | %98,13 |
| Aynı 750 vaka, son motor + canlı config kopyası | 743/750 | %99,07 |
| Aynı 750 vaka, son motor + yalnız özel alanda güncel kayıtlarla tamamlanan config | 746/750 | %99,47 |

Son motor/canlı config kopyası alt grupları:

| Grup | Eşleşme |
| --- | ---: |
| Tam başlık | 221/221 |
| Türkçe/ad varyantı | 221/221 |
| Otomatik ayrıntı beklentisi | 217/221 |
| Otomatik negatif | 60/60 |
| Diğer kaynak incelemeli/alias/mevcut güncel kayıt | 12/12 |
| Canlı config'te eksik güncel kayıt gereksinimi | 0/3 |
| Talimat-yapısı kontrolleri | 12/12 |

Son 750 sorgunun tamamı ters makale sırasıyla aynı sonucu verdi. Kaynak JSON'u,
karakter bütçesi ve grounding kurallarının kesilmemesi kontrolleri geçti.
Isınmış p95 arama süresi canlı config kopyasında 3,16 ms; tamamlanan özel
profilde 2,28 ms. Bu tek yerel koşunun ölçümüdür; karşılaştırmalı CPU/model
hızlanması iddiası değildir. Model süresi ölçülmedi.

## Bulunan nedenler ve düzeltmeler

1. Yönlendirme maddesi bulunuyor fakat hedefin kanıtı alınmıyordu. Açık yerel
   yönlendirmeler en fazla üç adımda takip edilir; döngü/eksik/belirsiz hedefte
   tahmin yapılmaz. `viaWikiRedirect` kaynağı görünürdür; wiki yönlendirmesi
   doğrulanmış yönetici alias'ı veya güncel kimlik eşitliği sayılmaz.
2. Uzun infobox parçaları tek cümle sayılıp atılabiliyordu. Tam `alan=değer`
   birimleri artık güvenli yapısal sınırlarından ayrılır.
3. Başlığın gövdede tekrarı, sorulan ayrıntının kanıtından yüksek puan alabiliyordu.
   Varlık eşleşmesi bonusuyla konu belirlendikten sonra gövde sıralaması sorunun
   kalan ayrıntı sözcüklerine dayanır. Aynı referans pasajı iki kez verilmez.
4. Açıkça `"…" adlı yer` diye sorulan bilinmeyen adlar bir kısa başlık veya
   ortak gövde sözcükleriyle eşleşebiliyordu. Kaynağı olmayan açık ad artık
   `UNRESOLVED_NAMED_ENTITY` ile boş döner; dört gözlenen yanlış seçim giderildi.
5. Genel sözcük filtresi, kendi tam makale adı tek konu olarak sorulduğunda bile
   kaynak reddediyordu. Gerçek tam kayıtlı başlık bu dar durumda kabul edilir;
   başka bir soruda geniş eşleme veya daha uzun ada benzerlik için kullanılmaz.

Bunlar özel arşiv metinleri yayımlanmadan sentetik regresyonlarla sınanmıştır.
JDK 26 ile temiz derleme, 67 Java testi, 73 dağıtım denetimi ve 11 snapshot
denetimi geçti. Normal `Build` moduna snapshot denetimleri de eklendi.
Doğrudan Windows PowerShell girişinde parametre varsayılanı sırasında boş
olabilen `PSScriptRoot` kullanımı, yerel ayar yolunu betik gövdesinde belirleyerek
düzeltildi ve regresyonla kontrol edildi.
Mevcut Citizens, NPC kimliği, konuşma kilidi, mesaj/echo, backend ve reload
akışlarına dağıtım veya veri müdahalesi yapılmadı.

## Kalan uyuşmazlıklar ve sonraki adım

Canlı config kopyasındaki 7 uyuşmazlığın **3'ü veri/yapılandırma eksikliği**:
kullanıcının doğruladığı güncel belediye/yapım ilişkisi kayıtları canlı
`knowledge.yml` içinde yok. Bunlar Java içine sabitlenmedi. Yalnız özel ikinci
profilde aynı alanlar tamamlandığında üç gereksinim de geçti. Bu test canlı
dosyanın güncellendiği anlamına gelmez. Canlıya aktarım için ayrı onay gerekir.

Kalan **4 vaka otomatik/onaysız ayrıntı beklentisi**: ham infobox ayraçlarına
ve birden fazla alanı kapsayan tek alıntıya bağımlılar. Beklenti dosyası motor
sonucuna göre değiştirilmedi; bu vakalar başarısızlık paydasında tutuldu.
İnsan, doğal ayrıntı sorusunu ve asgari kanıtı yeniden kaynak üzerinden
incelemeli. Her birinin gerçek kanıt kaybı mı, biçim/sınır karşılaştırması mı
olduğu onaylanmadan bunlar geçti sayılamaz.

Bu nedenle Aşama A koşusu tamamlanmış olsa da **insan onaylı kabul kapısı
tamamlanmadı**. Önce özel rapordaki dört beklentiyi ve doğrulanmış kayıt
önerisini birlikte değerlendirmek gerekir. Gemma B/C çağrıları başlatılmadı.
