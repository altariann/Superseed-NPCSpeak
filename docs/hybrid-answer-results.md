# Hibrit cevap mimarisi — ilk sürüm ve çevrimdışı sonuçlar

1 Kasım 2026. İlk sürüm uygulandı ve çevrimdışı doğrulandı. **Canlıya kurulmadı;
gerçek Gemma ve oyun içi kabul testi yapılmadı.** Önceki iki B03 model denemesinin
başarısız sonuçları geçerlidir; bu sürüm modelin düzeldiği iddiasında bulunmaz.

## Uygulanan davranış

`SpeakerContext`, `QueryFrame`, `AnswerPlan` ve `AnswerOutcome` küçük Java
record'larıdır. `AnswerPlanner`, tam soru kalıplarını yapılandırılmış belediye
kayıtlarıyla çözer. Retrieval puanı veya serbest kaynak metni olgu üretimine
yetki vermez. `HybridAnswerService` yerel/model yolunu seçer; yeni doğrulama
modeli, retry, bağımlılık veya framework eklenmedi.

Yerel yol yalnız görevsiz veya task türü `freeform` olan NPC'lerde kullanılır.
Diğer görev türlerinde eski prompt ve model yolu devam eder; evaluator, ödül,
anlaşma, buton ve görev sonucu mantığı değiştirilmedi. Kabul/moderasyon,
cooldown, tek oyuncu kilidi, pending, oturum geçerliliği, timeout, formatter,
izinli log ve geçmişe yazım mevcut ortak akışta kalır. Hafıza özetleme aynı
gönderilmiş geçmişi kullanır. Yeni üslup sayacı geçmiş kırpıldığında da ilerler;
mevcut exchange sayacının anlamı değiştirilmedi.

Güncel makam listesi, makam sahibi ve açık yönlü `reports_to` sorguları
doğrudan alanlardan oluşturulur. İlişki yönü tersine çevrilmez, bir makamdan
bağlılık/koordinasyon/işlem yetkisi çıkarılmaz. Liste tamlığı veya tek makam
sahibi varsayılmadığından yanıtlar kayıt kapsamını belirtir. Bilgi yoksa
doğrulanmış kayıt olmadığı söylenir; “görevi yok” / “kimseye bağlı değil” denmez.

Birinci şahıs yalnız açık `knowledge-entity` bağıyla, ilgili kayıt kişinin
kendi kaydıysa kullanılır. Görünen addan, NPC dosya adından, Citizens ID'sinden
veya kişilikten makam tahmini yapılmaz. “Ben hangi görevdeyim?” oyuncuyu anlatır;
NPC'ye bağlanmaz. “O kime bağlı?” gibi belirsiz hitaplarda netleştirme kullanılır.

`GroundedPrompt` freeform model mesajlarında anlatıcı ile soru öznesini ayrı JSON
verisi olarak taşır. Görevli NPC'lerin eski prompt yolu korunur. Aynı mesajın
planı ve prompt'u aynı immutable lore snapshot'ını kullanır. Kişilik metinleri
otomatik ayıklanmadı veya yeniden yazılmadı.

“Hemen söyleyeyim:” ve “Kısaca:” gibi incelenmiş, yeni olgu içermeyen girişler
nötr seçenekle dönüşümlü kullanılır. Makamları kaynak alanları belirler; üslup
bir ilişki veya yetki cümlesi ekleyemez.

## Çağrı sayıları ve B03

| Soru/durum | İlk sürümün yolu | Cevap modeli çağrısı |
| --- | --- | ---: |
| iladymoon belediyede hangi görevlerde bulunuyor? | Yerel iki makam | 0 |
| iladymoon'un görevleri neler? | Yerel iki makam | 0 |
| Sen hangi görevdesin? | Açık bağla kendi makamları; bağ yoksa netleştirme | 0 |
| Superseed Belediye Başkanı kim? | Kayıtlı makam sahibi | 0 |
| Doğan Tırpan kime bağlı? / Tech'e kim bağlı? | Tipli kayıt varsa aynı yönlü kenar | 0 |
| iladymoon kime bağlı? / Tech kime bağlı? | Doğrulanmış kayıt eksikliği | 0 |
| Merhaba / Nasılsın? / Başkanım, nasılsın? | Mevcut serbest model yolu | 1 |
| iladymoon görevini iyi yapıyor mu? | Yorum; makam listesine indirgenmez | 1 |
| Görevimi yaptım, ödülümü ver | Freeform ise sohbet; diğer görev türlerinde mevcut görev yolu | Yeni katmanda en çok 1 |
| iladymoon'un görevleri neler, bugün nasılsın? | Sabit olgu + tamponlanan dar sohbet adayı | 1 |
| İki olgusal soru veya ayrıştırılamayan karmaşık niyet | Serbest model yolu; yerel doğruluk güvencesi verilmez | 1 |

B03 için testte beklenen ve üretilen tam nötr cevap:

> iladymoon için kayıtlardaki güncel görevler: Özel Kalem Müdürü ve Belediye Başkan Vekili.

Sahte backend'e yanlış birinci şahıs/koordinasyon cevabı verilmesine rağmen
backend ve prompt hazırlama çağrılmadı, streaming callback'i çalışmadı.
Doğan'a makam atfedilmedi ve ek ilişki üretilmedi. Bu sonuç gerçek model
inference sonucu değil, üretim cevap yolunun gerçek Java testidir.

## Tamponlama ve güvenli geri dönüş

İlk sürümde karışık mesaj desteği bilinçli olarak çok dardır: ilk tam bölüm
yerel bilgi sorusu olmalı; ardından yalnız “nasılsın”, “bugün nasılsın”,
“sen nasılsın”, “peki sen nasılsın”, “merhaba” veya “selam” gelmelidir.
Ayırıcı `?`, virgül veya `ve` olabilir. İkinci bir olgusal soru bu yola zorlanmaz.

Bu dar yolda tek model isteği yapılır; `complete` ile bütün aday beklenir,
streaming token tüketicisine verilmez. Yalnız sabit kaynak cevabının aynısı
ve isteğe bağlı şu iki incelenmiş ekten biri kabul edilir:

- “İyiyim, teşekkür ederim.”
- “Sohbete de devam edebiliriz.”

Başka ifade, doğru makam sözcüklerini içerse bile doğrulanmış sayılmaz. Aday
yanlışsa, çözülemiyorsa veya backend hata verirse kaynak cevabı ve sohbeti
sürdürme daveti kullanılır. İkinci model çağrısı yapılmaz. Reddedilmiş aday
`ConversationManager` sonlandırmasına hiç ulaşmadığından önizlemeye,
konuşma geçmişine ve sohbet loguna gönderilmez.

Saf sohbet ve desteklenmeyen karmaşık mesajlar bu sınırlı kabul dilbilgisine
alınmaz; mevcut streaming ve Gemma üslubu sürer. Bu yoldaki keyfî olgusal
iddiaların tamamı için doğruluk garantisi yoktur. İlk sürüm genel amaçlı bir
Türkçe anlamsal doğrulayıcı içermez; güvenli sınırlı yolun kapsamı bununla
karıştırılmamalıdır.

## Yeni yapılandırma ve eski biçimler

**Aşağıdakiler örnektir; gerçek dosyalara uygulanmadı.** NPC dosyasında isteğe
bağlı üst düzey alan, kayıtlardaki tam özne adıyla belirtilir:

```yaml
knowledge-entity: 'Doğan Tırpan'
```

Bu alan yoksa isimle sorulan üçüncü kişi soruları çalışabilir; NPC'ye “sen”
diye sorulan olgusal soruda kimlik tahmin edilmez. Alan liste/sayı gibi yanlış
bir tipteyse NPC yükleme hatası oluşur; var olan geçerli dosyalar etkilenmez.

İlişki kaydının yönlü sorgularda kullanılabilmesi için mevcut alanların yanına
üç alan eklenir. `subjects` ve `text` eski okuyucu/yapı uyumluluğu için korunur:

```yaml
municipal-records:
  - id: dogan-reports-to-tech
    type: relationship
    subjects: ['Doğan Tırpan', 'Tech']
    relation: reports_to
    from: 'Doğan Tırpan'
    to: 'Tech'
    current: true
    text: 'Doğan Tırpan, Tech adlı kişiye bağlıdır.'
```

Yeni alanlar ya birlikte bulunur ya hiç bulunmaz. İlk yüklem yalnız
`reports_to` olabilir; uçlar iki katılımcıyla aynı olmalı ve kendine bağlılık
olmamalıdır. `subjects` sırasından bağımsız olarak `from` / `to` esas alınır.
Yeni yapı kaynak JSON'una da taşınır. Metin–alan anlamsal uyumunu otomatik
kanıtlayan bir sistem eklenmedi; yapılandırılmış alanlar yerel cevabın esasıdır.

Eski `municipality`, `verified-facts` ve yeni yön alanlarını taşımayan
`municipal-records` dosyaları okunmaya devam eder. Eski ilişki metni yönlü
yerel kanıta otomatik yükseltilmez. Böyle bir kayda yerel soru sorulursa açık
yönlü kanıtın bulunmadığı belirtilir. Yapılandırılmış belediye kaydı hiç yoksa
eski model/retrieval yolu korunur. Geçersiz knowledge reload'u önceki başarılı
snapshot'ı korur. Paket içindeki örnek knowledge dosyası da bu tur değiştirilmedi.

## Çevrimdışı doğrulama

İki geliştirme aşamasında JDK 26.0.2 ile yalnız aşağıdaki Build komutu kullanıldı:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\Deploy-Superseed.ps1 -Mode Build -JavaHome 'C:\Program Files\Eclipse Adoptium\jdk-26.0.2.101-hotspot'
```

Başlangıçta PowerShell yürütme politikası ve Gradle önbelleği erişimi nedeniyle
koşu engellendi; süreç kapsamlı yürütme seçeneği ve gerekli dosya erişimiyle
aynı Build komutu tekrar çalıştırıldı. Global yürütme/Git/Java ayarı değiştirilmedi.

| Aşama | Java testleri | PowerShell denetimleri |
| --- | ---: | ---: |
| Kimlik ve yönlü kayıt şeması | 94 geçti | 95 geçti |
| Son hibrit cevap entegrasyonu | 119 geçti, 0 hata/başarısızlık | 95 geçti |

Yeni Java testleri: `DirectedRecordsTest` içinde 7, `HybridAnswerTest` içinde
23 test; mevcut 89 test de geçti. Son test grupları şunları kapsıyor:

- B03, Türkçe harfler ve apostrof ekleri; birinci/üçüncü şahıs, oyuncu–NPC
  ayrımı ve açık kimlik bağı yokluğu.
- Makam sahibi, vekil/yardımcı ayrımı, bilinmeyen makam/bağlılık, eski kayıt,
  ters yön, eski katılımcı sırasından bağımsız tipli yön ve tarihsel kayıt dışlama.
- Açık alias, kısmi isimden olgu ödünç almama ve bozuk/eksik ilişki şeması.
- Gerçek Java `NpcData` okuyup yazma testiyle yalnız geçici NPC dosyasında
  açık bağın, Citizens 313 değerinin ve bilinmeyen özel alanın korunması.
- Sahte backend ile 0/1 çağrı, dar karışık niyet, tamamlanmadan bekletme,
  reddedilen adayın token tüketicisine çıkmaması, yerel cevabın geçmiş/log
  tüketicilerine aktarımı, asenkron ve senkron hatada retry yapmama.
- Diğer görev türlerinin model yolunda kalması, görev yapılandırmasının
  değişmemesi ve sınırlı geçmiş dolduktan sonra da üslup çeşitlenmesi.

PowerShell: 73 dağıtım + 11 snapshot + 11 pilot denetimi; geçici sahte sunucu
ve dosyalar kullanılır. Çıktıdaki stop/başlatma senaryoları bu test sahtelerine
aittir; gerçek Minecraft sunucusuna RCON veya süreç işlemi yapılmadı.

Üretilen sınıfta major version **61 (Java 17)** doğrulandı. JAR'da taranan
özel wiki, `.local-safety`, server.properties, log ve yedek kalıplarıyla eşleşen
girdi sayısı **0**. Var olan `ChatColor` deprecation uyarısı sürüyor.

## Kalan sınırlar ve kabul durumu

- Genel Türkçe çözümleme yok: desteklenen kalıplar dışındaki ifade, bütün ek
  çeşitleri, iki kişi karşılaştırması, “X Y'ye bağlı mı?”, amir/üst eş anlamları,
  geçmiş/yıl ve geleceğe dönük sorular deterministik kapsamda değil.
- Genel ilişki türleri, koordinasyon, destek, dostluk, dolaylı/geçişli bağlılık
  ve kurumsal işlem yetkileri çıkarılmaz.
- Karmaşık karışık niyetler, gündelik sohbetin içindeki örtük belediye iddiaları
  ve modelin serbest olgusal yeniden ifadesi güvence altında değil.
- Yalnız bağlamdan “o” çözümleme ve typo/fuzzy isim eşleştirme yok. Desteklenen
  soru biçiminde bilinmeyen ad kaynak eksikliğiyle, zamir netleştirmeyle karşılanır.
- Üslup çeşitliliği üç girişle sınırlı. Dar karışık yoldaki model serbestliği
  özellikle sınırlıdır; doğal sohbetin tamamına aynı kısıt uygulanmaz.
- Gerçek kişilikteki eski olgu cümleleri ve gerçek knowledge şema geçişi
  uygulanmadı. Canlı bağ/ilişki alanları girilmeden ilgili yeni özelliklerin
  canlıda etkin olduğu söylenemez.
- Çevrimdışı testlerde regresyon görülmedi; gerçek Citizens etkileşimi, çok
  oyunculu zamanlama, canlı cooldown/kilit ve gerçek modelin üslup davranışı
  oyun içinde henüz doğrulanmadı. Bütün bu akışların oyun içi kabulü açık kalır.
- Özel 221 wiki / 750 sorguluk değerlendirme bu tur yeniden çalıştırılmadı;
  eski sayılar yeni yanıt katmanının başarısı olarak kullanılmaz. Performans
  ve CPU gecikmesi ölçülmedi; yalnız çağrı sayıları doğrulandı.

`9b55873` mimari commit'i korunur. Çalışma mevcut
`dev/superseed-automation` ve PR #2 üzerinden sürdürülür. Canlı NPC/Citizens,
skin/konum, gerçek knowledge, model ayarları ve sunucu JAR'ı değiştirilmedi.
Sonraki gerçek Gemma ve oyun içi test aşaması için kullanıcı onayı beklenir.
