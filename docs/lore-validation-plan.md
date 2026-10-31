# Gerçek wiki ve Gemma 3 4B kontrollü doğrulama planı

Durum: **AI'sız Aşama A yürütüldü; Gemma B/C koşuları yapılmadı.**
Gerçek sonuçlar ve insan doğrulaması sınırı [Aşama A raporundadır](stage-a-results.md).
Bu plan kendi başına ölçülmüş başarı veya canlı dağıtım onayı değildir. Çalışma mevcut development
dalı ve PR #2 üzerinde devam eder; yeni PR veya otomatik merge gerektirmez.

## Sınırlar ve hazırlık

Testler Minecraft sürecini durdurmadan, JAR değiştirmeden, RCON/oyuncu sohbeti
kullanmadan yürütülecek. Üretim `knowledge.yml`, wiki, NPC ve Citizens dosyaları
değiştirilmeyecek. Model testi aynı makinede CPU/bellek tüketebileceği için gerçek
Ollama yükü ayrıca uygun bir test zamanında başlatılacak; bu hazırlık aşamasında
Ollama'ya istek gönderilmez. Aşama A aracı artık `evaluateLore` göreviyle
uygulanmıştır; normal derlemeye gerçek veri/model bağımlılığı eklenmemiştir.

1. Git commit'i, JAR SHA-256, JDK sürümü ve 48 Java testi/dağıtım denetimlerinin
   raporu kaydedilir. Önce son kaynakların temiz derlemesi doğrulanır.
2. Yalnız gerekli `knowledge.yml` ve wiki dosyaları salt okunur kaynaklardan
   özel yerel çalışma alanına kopyalanır. Örneğin Git'in yok saydığı
   `.local-safety/lore-eval/<run-id>/` kullanılabilir. Gerçek sunucu config'inin
   tamamı, RCON parolası, oyuncu hafızası ve chat logları kopyalanmaz.
3. Kaynak/kopya dosya adları, boyutları ve SHA-256 manifestleri karşılaştırılır;
   kopyalama sırasında değişmiş dosya varsa koşu durur. Beklenen **221 `.txt`**
   sayısı tutmazsa fark raporlanır; sayı sessizce değiştirilmez. Boş/bozuk UTF-8,
   160 KB üstü dosya, kullanılabilir pasajı olmayan metin, mükerrer/normalize
   başlık ve çakışan alias ayrı envanter satırı olur. Sınır dışı kayıtlar başarı
   oranını yükseltmek için sonuç kümesinden sessizce çıkarılmaz.
4. Canlı lore yapılandırması ile yeni örnek ayarlar arasındaki farklar gözden
   geçirilir. Yeni verified-facts kayıtları gerekiyorsa **yalnız test kopyasına**
   eklenir ve değişiklik manifestte tutulur. Güncel belediye bilgisi yönetici
   tarafından doğrulanır; wiki eski makam sahibini yazıyorsa o kayıt tarihsel
   etiketlenir. Güncellik, wiki metninden tahmin edilmez.
5. Yerel Ollama'nın model listesi/sürüm ve model bilgi çıktısından gerçek Gemma
   3 4B etiketi, digest, quantization, model parametreleri ve bağlam ayarları
   özel manifeste kaydedilir. Etiketin örneğin `gemma3:4b` olduğu varsayılmaz;
   kurulu model esas alınır. Global Ollama ayarı/Modelfile değiştirilmez,
   yeni model indirilmez. Tüm model istekleri `127.0.0.1` üzerinde kalır.

Manifest, sorular, beklenen yanıtlar, alıntılar, prompt'lar ve model yanıtları
özel yerel dosyalardır; GitHub/PR/CI çıktısına yüklenmez. Yayımlanabilecek rapor
yalnız toplu sayılar, süreler ve kullanıcının paylaşılmasını onayladığı örnekleri
içerir. Arşivden alınan metni public test fixture'ına dönüştürmeyin.

## Aşama A — AI olmadan arama doğruluğu

Küçük bir yerel test aracı `KnowledgeBase.load`/`LoreEngine.retrieve` ile aynı
üretim kodunu çalıştırmalı; arama algoritmasının ayrı bir Python/PowerShell
kopyası yazılmamalıdır. Paket erişimi gerekiyorsa değerlendirme aracı test kaynak
kümesinde tutulabilir. Gerçek arşiv yolu açık parametreyle verilmeli; olağan
`gradlew build` gerçek wikiye veya modele bağımlı hale getirilmemelidir.

Beklentiler motor çıktısını görmeden insan tarafından hazırlanır. Her soruda
kanonik varlık, kabul edilen kaynak kimlikleri, gerekli kayıt/kanıt cümlesi,
yasaklanan karışık varlıklar ve varsa doğru belirsizlik cevabı belirtilir.

| Grup | Adet | Beklenen davranış |
| --- | ---: | --- |
| Tam başlık | 221 | Her makaleyi kendi tam adıyla sor; doğru makale/alias grubu seçilsin. |
| Türkçe/ad varyantı | 221 | Her başlığa bir büyük/küçük harf, ASCII veya uygun durum eki varyantı; yalnız belgelenen destek beklenir. |
| Kanıtlı ayrıntı | 221 | Her makaleden insanın seçtiği bir olguya soru; ilgili olguyu içeren pasaj gönderilsin. |
| Negatif/karışan adlar | 60 | 20 yakın ama farklı ad, 20 parçalı/ortak sözcüklü alias, 20 arşiv ve verified kayıtlarda bulunmayan konu. |
| Güncel/tarihsel/ilişki | 15 | Belediye görevleri, eski ad, yapımcı ilişkisi ve tarihsel çelişkiler. |
| Talimat/veri ayrımı | 12 | Deneme kopyasındaki sentetik kaynak talimatları, yanıltıcı stil/hafıza, bilinmeyen ayrıntı ekletme girişimi. |
| **Toplam** | **750** | Tam arşivi kapsayan, önceden etiketlenmiş sabit sorgu kümesi. |

Olmayan konu örnekleri mevcut 221 dosya ve verified kayıtlarla kontrol edilerek
seçilir; bir ad sırf ilk bakışta tanınmadığı için negatif kabul edilmez.
Sentetik saldırı metinleri yalnız ayrı test kopyasına eklenir; gerçek arşive
veya belediye kayıtlarına yazılmaz. 12 talimat örneğinin kaynak/prompt ayrımı
denetlenir; AI'sız koşu modelin talimata uyduğunu kanıtlamaz.

Her soru için seçilen ve elenen kaynaklar, puanlar, eşleşen sözcükler,
gerekçeler, birebir JSON alıntıları ve arama süresi özel raporda tutulur. Aynı
sorgular dosya sırası ters çevrilerek tekrar çalıştırılır; sonuçlar aynı olmalı.
Karakter bütçesi ve JSON geçerliliği her sonuçta denetlenir. Sonuçsuz arama,
belirsiz varlık, pasaj sayısı ve bütçe nedeniyle elenme ayrı sayılır.

İlk kabul hedefleri (sonuç değildir):

- Desteklenen ve belirsiz olmayan tam adlarda doğru kaynak/alias grubu: %100.
  Envanterdeki sorunlu dosyalar ayrıca raporlanır; 221 dosyanın tamamının kapsanma
  durumu görünür kalır.
- Kanıtlı ayrıntı sorularında gerekli kanıtın seçilen pasajlarda bulunması: en az
  %95. Yanlış negatiflerin nedeni ayrı incelenir; başarısız sorular silinmez.
- Kritik alias/kişi ayrımı, belediye önceliği ve olmayan varlık gruplarında yanlış
  varlık seçimi: 0. Negatif sorular hem wiki hem verified kayıt için denetlenir.
- Tüm kaynak verisi yapılandırılmış bütçeye sığmalı; kurallar kesilmemeli,
  JSON bozulmamalı ve farklı yükleme sırası sonuçları değiştirmemeli.
- Isınmış arama için başlangıç p95 hedefi 50 ms; donanım ve ölçüm koşullarıyla
  birlikte raporlanır. Ölçülmüş hedef aşımı kaynak yükleme/arama/inference ayrılarak
  incelenir; yavaş arama model gecikmesi gibi gösterilmez.

Bu aşama geçmeden çok sayıda model çağrısı yapılmaz.

## Aşama B — küçük, üretim ayarlarına denk Gemma pilotu

Arşiv kopyasıyla aynı `GroundedPrompt.messages` yolu kullanılmalı. Modelden
kaynak seçmesi istenmez; seçilmiş kaynaklar motor tarafından verilir. Tam
prompt ve kaynaklar özel yerel rapora kaydedilir. Üretimdeki backend, bir
`/api/chat` isteğinde `model`, `stream`, `messages` gönderiyor; test aracı ilk
koşuda gizlice temperature/seed/num_ctx eklememelidir. Gerçek model varsayılanları
manifestte tutulur. Önce akışsız, tek seferde tek istek kullanılır.

Önce aşağıdakileri kapsayan **10 soruluk aday pilotu** çalışır:

- Tech'in güncel Belediye Başkanı olması.
- Doğan Tırpan'ın Belediye Başkan Yardımcısı olması.
- iladymoon'un Özel Kalem Müdürü/Belediye Başkan Vekili olması.
- Iron Plaza → Plaza AVM eski ad ilişkisi ve başka bir AVM ile karışmaması.
- Pursuing Evidence → Visual Node yapımı, SPAT tarafından oluşturulması;
  üç varlığın alias sayılmaması.
- Gerçek arşivden doğrulanmış bir tarihsel makam/görev sorusu.
- Arşivden doğrulanmış bir alışveriş/bina ayrıntısı.
- Arşivde bulunmadığı kontrol edilmiş bir kişi/işletme sorusu.
- Bilinen bir varlığın kaynaklarda belirtilmeyen tarihi/ilişkisi.
- Kişilik/hafıza içindeki eski bilgiye rağmen güncel kaydı kullanma.

Pilot hata, beklenmeyen uydurma, bağlam taşması veya sunucunun AI yanıt süresinde
bozulma gösterirse durur. Canlı serverı kapatmak ya da JAR değiştirmek çözüm
olarak otomatik uygulanmaz. Denemeler en fazla beşer istekli küçük gruplar
halinde, her grup sonrası süre/CPU/bellek kontrolüyle yürütülür. Gerekirse yük
durdurulur; üretim JVM'i veya Ollama zorla sonlandırılmaz.

## Aşama C — kontrollü A/B ve model yanıtları

Pilot geçerse 40 sabit soruluk katmanlı küme seçilir: 8 güncel/tarihsel belediye,
8 alias/varlık ayrımı, 8 farklı makaleden olgusal ayrıntı, 8 bilinmeyen ayrıntı,
4 yapımcı ilişkisi, 4 talimat/kişilik yanıltması. Makale ve pasaj uzunluğu
farklı örnekler seçilir; yalnız kolay sorularla başarı oranı yükseltilmez.

Her soru üç kez, her kez temiz mesaj geçmişiyle çalıştırılır. Ayrı bir çok turlu
alt koşuda aynı sorular, önceki hatalı model cümlesi içeren sabit test geçmişiyle
tekrarlanabilir; bu ek koşunun sayısı/sonuçları ana kümeden ayrı tutulur.

- A tabanı: `7bb3bfa` kaynak koruma sürümü, aynı özel veri kopyası/modelle ayrı
  yerel test çıktısı olarak hazırlanır; hiçbir sunucuya dağıtılmaz.
- B adayı: test edilen PR #2 commit'i. Dal değiştirilip çalışma ağacı bozulmaz;
  baseline ve aday için izole derleme çıktıları/adaptörleri kullanılır. Eski
  `KnowledgeBase.context` ve yeni prompt API'si farklı olduğundan adaptörler
  gerçek iki uygulamayı çağırmalı; davranışı yeniden taklit etmemelidir.
- İki kolun da ayarları aynı tutulur; yalnız kodun ürettiği arama/prompt değişir.
  Gerekli yeni verified kayıtlar iki kola da aynı veri olarak verilir. Kolun
  desteklemediği yeni alanlar açıkça raporlanır; kayıt kapsamı farkı arama
  başarısı gibi sunulmaz. Legacy-only karşılaştırma ayrıca ayrılabilir.
- 40 × 3 × 2 = **240 çağrı üst sınırı**. 10 pilot çağrısı bundan ayrıdır.
  Aynı anda yalnız bir çağrı yapılır. Kol/soru sırası önceden belirlenmiş dengeli
  düzende değiştirilir; tek kolun hep soğuk model yükleme maliyetini taşıması
  önlenir. Isınma/yükleme süreleri ayrıca kaydedilir.
- Tek çağrı zaman aşımı mevcut backend ayarıyla aynı olmalı; timeout ayrı hata
  olarak sayılır. Otomatik sınırsız retry yapılmaz. Üç tekrardaki farklar saklanır;
  yalnız en iyi yanıt seçilmez.

Gerekirse ayrı bir teşhis koşusunda sabit seed/temperature kullanılabilir;
bu üretimle aynı ayar değildir ve üretim benzeri testin yerine geçmez. Ollama
sürümü/digest aynı olsa da model çıktılarının bit düzeyinde deterministik
olacağı varsayılmaz. Canlı Modelfile veya global ayarlar test için değiştirilmez.

## Ölçüm ve insan değerlendirmesi

Model kendi doğruluğunun hakemi olmaz. Beklenen kanıtları bilen bir insan,
mümkünse A/B kolunu görmeden, yanıtın her olgusal iddiasını şu etiketlerle
işaretler: destekleniyor, kaynaksız, kaynakla çelişiyor, tarihsel/güncel yanlış,
doğru belirsizlik, eksik ama yanlış olmayan yanıt. İstenmeyen ilave isim,
işletme, tarih, ilişki ve yetki iddiası da olgu sayılır.

Kaydedilecek toplu ölçüler: doğru kaynak/pasaj kapsaması, yanlış varlık oranı,
kaynaksız/çelişkili iddia oranı, doğru belirsizlik oranı, güncel kayıt önceliği,
zaman aşımı, uçtan uca p50/p95, arama ve model süreleri. Ollama yanıtında varsa
prompt/eval token sayıları, yükleme ve üretim süreleri ayrıca tutulur.
Kaynak karakter bütçesi **toplam token bütçesi değildir**: sistem talimatı,
kişilik, hafıza ve geçmiş de hesaba katılır. Token sayısı bağlam sınırına
dayanıyorsa veya model kaynak/kuralları unutuyorsa daha kısa geçmişli kontrollü
karşılaştırma yapılır; sessiz truncation yokmuş gibi başarı ilan edilmez.

Sürüm adayı için kabul kapısı:

- Kritik belediye/alias/yapımcı ve bilinmeyen bilgi örneklerinin hiçbir aday
  tekrarında kaynaksız veya çelişkili olgusal iddia bulunmamalı.
- Diğer model örneklerinde de kaynaksız iddia hedefi 0; tek hata dahi neden
  analizi ve düzeltme/yeniden test gerektirir. Baseline'ın yanlış olması adaya
  yanlış yapma hakkı vermez.
- Doğru belirsizlik, her bilinmeyen ayrıntıda korunmalı; bilinen kaynaklı
  soruların tamamına gereksizce “bilmiyorum” diyerek test geçilmemeli.
- Adayın en az %95 kanıtlı soru kapsamı, kritik ilişkilerde %100 doğruluk ve
  mevcut backend süresine uygun yanıt üretmesi beklenir. Latans farkı baseline,
  model yükleme ve prompt boyutuyla açıklanır; CPU çıkarımı hızlanmış varsayılmaz.

Son rapor başarısız örnek kimliklerini, nedenlerini ve sınırlamaları içerir.
Özel metinleri paylaşmadan toplu rapor PR #2'ye eklenebilir. Ancak bu planı
geçmek bile canlı dağıtım izni değildir: ilk canlı stop/JAR değişimi/başlatma
için kullanıcıdan ayrıca onay alınır. Onaylı dağıtımdan sonra Citizens
kimlik/skin/pozisyon, reload, tek oyuncu kilidi, renkli mesajlar ve özel echo
oyun içinde ayrıca kontrol edilir.
