# Kaynak doğrulamalı hibrit cevap mimarisi — tasarım önerisi

Sonraki durum: temel yaklaşım onaylandı ve dar ilk sürüm uygulandı.
[Uygulama, test sonuçları ve kalan sınırlar](hybrid-answer-results.md).
Aşağıdaki metin, uygulama öncesinde sunulan tasarım kaydıdır.

1 Kasım 2026. Durum: **uygulanmadı, kullanıcı incelemesine sunulan tasarım**.
Bu çalışma kod, model ayarı, gerçek NPC/knowledge dosyası veya sunucu değişikliği
içermez. Model çağrısı yapılmadı. B03 hatası çözülmüş sayılmaz.

Öneri: Açık ve yapılandırılmış belediye sorularında doğrulanmış alanlardan
deterministik bir cevap planı oluşturmak; doğal sohbeti Gemma üzerinden sürdürmek.
Kimlik ayrımı prompt'a da yansıtılır, fakat doğruluk güvencesi prompt'a bırakılmaz.

## İnceleme ve mevcut sınırlar

İncelenenler: `NpcConfig.buildSystemPrompt`, `GroundedPrompt.messages`,
`KnowledgeBase.load`, `LoreEngine.municipalSelection`,
`ConversationManager.dispatchToBackend`, konuşma geçmişi ve görev değerlendirme
akışı, `MunicipalRecordsTest`, pilot hazırlama kodu ve gerçek ikinci B03 isteği.
Özel yerel alandaki gönderilen paket ve cümle karşılaştırması salt okunur
incelendi; ham prompt/yanıt ve özel dosyalar bu belgeye taşınmadı.

- `buildSystemPrompt` anlatıcıyı NPC'nin görünen adıyla tanıtıyor; topic, avoid,
  görev ve serbest system-prompt metnini aynı talimat metnine ekliyor.
- `GroundedPrompt` bu metne ek kişiliği, doğrulanmamış hafızayı ve kaynakları
  ekliyor. Açık bir `speakerEntityId` / `querySubjectIds` ayrımı yok.
- Gerçek B03 sorusu “iladymoon belediyede hangi görevlerde bulunuyor?” idi.
  İstekte yalnız bir system ve bir user mesajı vardı; geçmiş/hafıza boştu.
  Anlatıcı Doğan Tırpan, iki güncel makam kaydının öznesi yalnız iladymoon idi;
  wiki kaynağı ve ilişki kaydı yoktu. Buna rağmen yanıt makamları anlatıcıya
  taşıdı ve destek/koordinasyon ilişkisi ekledi. Bu vakada hatayı eski konuşma
  geçmişine bağlayan kanıt yok.
- Gerçek kişilik metninde kimlik ve makam iddiaları da bulunuyor. Ayrıca eski
  “başkanın adı verilmedi” ifadesi güncel kayıt önceliğiyle gerilim yaratıyor.
  Bu, gelecekte açıkça ele alınmalı; bu çalışma gerçek kişiliği değiştirmiyor.
- `municipal-records` makamları `subjects` / `offices` olarak taşıyor. İlişkilerde
  yalnız iki katılımcı, serbest `text` ve keywords var. Seçici bazı sorularda
  katılımcı sırasını yön gibi kullanıyor; yükleyici ilişki yüklemini ve yönünü
  tipli alanlarla doğrulamıyor. Genel ilişki üretimi için bu şema yeterli değil.
- Retrieval sonucu kaynak JSON metni taşıyor; ayrı bir cevap planı yok.
  Kaynak eşleşmesi, yanıtın desteklendiğini kanıtlamıyor.
- Model çıktısı bugün geçmişe/loga ve oyuncuya doğrulama yapılmadan gidiyor.
  Streaming önizlemesi de henüz tamamlanmamış metni actionbar'da gösteriyor.

B03, kaynak daraltma ve prompt sertliğinin yeterli olmadığını gösteriyor.
Modelin içsel hata nedeni veya yeni tasarımın ölçülmüş başarısı hakkında iddia yok.

## 1. Anlatıcı, bilgi öznesi ve cevap planı

Önerilen küçük, değişmez veri nesneleri:

| Nesne | Taşıdığı bilgi |
| --- | --- |
| `SpeakerContext` | NPC dosya kimliği, görünen ad, doğrulanmış bilgi varlığına açık bağ; bağ yoksa bilinmiyor |
| `QueryFrame` | Niyet, soru öznesi/özneleri, ilişki yönü, zaman kapsamı, belirsizlik ve çözümlenmemiş bölüm |
| `AnswerPlan` | Cevap yolu, kanıt kimlikleri, desteklenen olgular, kapsam/tamlık durumu, güvenli cevap ve üslup seçimi |
| `AnswerOutcome` | Oyuncuya gönderilecek son metin, üretim yolu, doğrulama sonucu ve geri dönüş nedeni |

NPC kimliğini bilgi varlığına bağlamak için gelecekte isteğe bağlı açık bir alan
önerilir; örneğin NPC dosya kimliği `dogan-tirpan` → bilgi varlığı Doğan Tırpan.
Görünen ad, skin, sayısal Citizens ID veya kişilik metninden makam çıkarılmaz.
Mevcut `-`/`_` kişilik anahtarı eşleştirmesi varlık kimliği sayılmaz. Citizens
verilerine dokunulmaz. Açık bağ bulunmuyorsa birinci şahısla makam üretilmez.

Özne çözümleme kuralları:

- “Sen hangi görevdesin?” doğrudan hitap edilen NPC'ye bağlanabilir; açık kimlik
  eşlemesi gerekir. “Ben hangi görevdeyim?” oyuncudur, NPC'ye çevrilmez.
- Adı açıkça geçen kişi, anlatıcıdan bağımsız çözülür. Doğan kendisi hakkında
  sorulan açık bir soruya birinci şahısla cevap verebilir.
- “O kime bağlı?”, alıntı içindeki “sen”, birden fazla aday veya eksik isim için
  ilk sürümde geçmişten tahmin yürütülmez; kısa netleştirme kullanılır.
- Önce en uzun tam ad ve yalnız açıkça tanımlanmış alias değerlendirilir.
  “Tech Plaza”, “Tech” kişisine dönüştürülmez. Türkçe ek normalizasyonu sınırlı
  ve test edilmiş olur; benzer yazım otomatik aynı kişi sayılmaz.

`NpcConfig` görev/üslup talimatlarını korur. `GroundedPrompt`, ayrıca açık
anlatıcı kimliği ve soru çerçevesini veri olarak alır; NPC'nin olgusal kendilik
bilgisi kendi doğrulanmış kayıtlarından, üslubu kişilikten gelir. “Ben” yalnız
anlatıcıyı, üçüncü kişi adı yalnız o varlığı gösterir. Bu ayrım bir prompt
yardımıdır; deterministik üretim kurallarının yerine geçmez.

Eski serbest kişilik metninden otomatik olgu ayıklama yapılmaz. Olgu içeren
kişiliklerin açık geçişi ayrıca gözden geçirilir; yapılandırılmış kimlik ve
güncel kayıtlar önceliklidir, çelişki tanılanır. Görev talimatı bir makam veya
kurumsal yetki kanıtı sayılmaz.

## 2. Doğrulanmış kayıtlar ve deterministik üretim

İlk kapsam: güncel makam listesi, açık makamın sahibi ve açık yönlü bağlılık.
Karşılaştırma ancak her iki kişinin istenen kapsamı yeterince doğrulanmışsa
desteklenir. Genel `fact` metinleri otomatik makam/ilişki nesnesine çevrilmez.

Makam kayıtlarının mevcut yapılandırılmış alanları kullanılabilir. Kurum kapsamı
ileride açık alanla belirtilmeli; başka kurumdaki bir makama otomatik
“Superseed Belediyesi” eklenmemeli. İlk sürüm bu belediye kayıtlarının açıkça
onaylanmış kapsamıyla sınırlı tutulur.

İlişkiler için önerilen ek yapı:

```yaml
# Tasarım örneği; mevcut dosyaya uygulanmadı.
id: dogan-reports-to-tech
type: relationship
relation: reports_to
from: 'Doğan Tırpan'
to: 'Tech'
current: true
```

`reports_to(Doğan, Tech)` yalnız Doğan → Tech bağlılığını doğrular. Ters sorgu
aynı kenarın hedefinden yapılır; Tech → Doğan bağlılığı yaratılmaz. Aynı makamda
bulunma, birlikte çalışma, destek, koordinasyon, dostluk veya geçişli bağlılık
çıkarılmaz. Başka yüklemler ayrı, açık kayıt ve ayrı şablon gerektirir.

Eski ilişki kayıtları mevcut retrieval yolunda okunabilir; yalnız katılımcı
sırası veya keywords üzerinden yeni tipli kenara otomatik yükseltilmez.
Geçiş tamamlanmamışsa güvenli alternatif, tam soruyla açıkça uyumlu ve incelenmiş
tek kaynak cümlesini değiştirmeden kullanmak ya da doğrulanamadığını söylemektir.
Kapsam ve yön uygunluğu belirlenemeyen eski kayıttan cevap üretilmez.

Yüklemede doğrulanacaklar: benzersiz kayıt kimliği, geçerli varlıklar, izinli
yüklem, yön, kurum/zaman kapsamı, çelişkili kayıtlar ve metin–yapı uyumu.
Serbest metnin anlamsal uyumu programla bütünüyle kanıtlanamaz; yapılandırılmış
alanlar cevaplamanın dayanağı olur, uyuşmazlıklar geçişte insan incelemesine
ayrılır. Hatalı yüklemede mevcut başarılı immutable snapshot korunur.

“Görevlerinin tümü” ayrıca kapsam sorunudur: birkaç pozitif kayıt diğer bütün
görevlerin bulunmadığını kanıtlamaz. Tamlık açıkça onaylanmamışsa “Kayıtlardaki
görevleri …” denir; eksik listelerden kesin eşitlik, eşitsizlik veya olumsuz
bağlılık sonucu çıkarılmaz. Aynı kişinin iki makamı hata sayılmaz; çelişki
kontrolü görevin tek kişilik olup olmadığına ilişkin açık kurala dayanmalıdır.

Deterministik çıktı, kaynak alanları + kodda tanımlı dilbilgisel şablon +
önceden incelenmiş üslup parçalarından oluşur:

- Üçüncü kişi: “iladymoon, Superseed Özel Kalem Müdürü ve Belediye Başkan
  Vekilidir.” Tamlık onaylanmamışsa başına “Kayıtlara göre” eklenir.
- Doğan kendisi: “Superseed Belediye Başkan Yardımcısıyım.”
- Doğan'ın bağlılığı: “Belediye Başkanı Tech'e bağlıyım.”
- Üslup seçeneği: “Hemen söyleyeyim: …” gibi makam, yetki, eylem veya ilişki
  iddiası taşımayan kısa, incelenmiş girişler. Nötr seçenek de bulunur.

Karakter, her cevaba slogan eklemekle korunmaz. Kısa ve kendinden emin anlatım,
NPC'nin görünen adı ve serbest sohbet kişiliği devam eder. Şablon dışı bir
“onunla koordineli çalışıyoruz” eki bu yolda üretilemez. Makam adı tek başına
oyun içi işlem yetkisi veya komut çalıştırma hakkı oluşturmaz.

## 3. Yönlendirme ve doğal konuşma

Yönlendirici retrieval skorunu cevaplama güveni olarak kullanmaz. Bütün mesajın
anlamı kapsanmış mı, özne tekil mi, soru türü destekleniyor mu ve kaynak yeterli
mi diye bakar. Bir makam sözcüğünün bulunması tek başına yeterli değildir.

| Durum | Yol | Yeni cevap modeli çağrısı |
| --- | --- | ---: |
| Açık özne + desteklenen olgusal soru + yeterli güncel yapı | Doğrudan kaynak alanlarından cevap | 0 |
| Açık olgusal soru, istenen bilgi doğrulanmıyor | Kısa kaynak eksikliği cevabı | 0 |
| Öznesi/yönü belirsiz olgusal soru | Kısa netleştirme | 0 |
| Selam, gündelik konuşma, karakter yorumu, açık uçlu sohbet | Gemma | 1 |
| Açıkça ayrılabilen olgusal ve sohbet bölümü | Sabit olgusal çekirdek + tek Gemma sohbet adayı; aşağıdaki kontrol | En çok 1 |
| Güvenle ayrıştırılamayan karışık mesaj | Kısmi makam eşleştirmesi yapılmaz; bağlama göre Gemma veya netleştirme | En çok 1 |

Örneğin “iladymoon'un görevleri neler?” kapsama girer. “iladymoon görevini iyi
yapıyor mu?” değerlendirme sorusudur; makam listesine indirgenmez. “Başkanım,
nasılsın?” hitaptır. “Görevimi yaptım, ödülümü ver” oyun görevidir. Belirsiz
soruların tamamını kaynak eksikliği cevabına çevirmek doğal sohbeti bozar.

Karışık mesajda yalnız ilk soruyu cevaplayıp kalan niyeti sessizce düşürmek yok.
“iladymoon'un görevleri neler, bugün nasılsın?” ayrılabiliyorsa olgusal çekirdek
yerel üretilir; Gemma yalnız sohbet kısmı için bir aday verir. Belediye olgusu
taşıyan ek güvenle doğrulanamıyorsa aday yayınlanmaz; sabit çekirdeğe kısa
incelenmiş bir sohbet geçişi eklenir. Güvenli serbest ek bulunmadığı durumda
kalan sohbet konusu için devam daveti kullanılır. Bu dar geri dönüş doğal
sohbetin tamamına uygulanmaz.

“iladymoon sana bağlı olduğu için mi başarılı?” gibi olgu varsayımıyla yorumun
iç içe geçtiği mesaj saf makam sorgusuna zorlanmaz. Bağlılığın doğrulanmadığı
belirtilir; gerekirse hangi konunun sorulduğu netleştirilir. Hiçbir durumda
varsayım soru içinde geçtiği için doğrulanmış ilişki sayılmaz.

## 4. Yerel doğrulama ve güvenli geri dönüş

En güvenilir ilk seçenek olgusal cümlenin modele yeniden yazdırılmamasıdır.
Gemma ile serbest ifade seçeneği daha sonra açılırsa aynı kayıt snapshot'ına
bağlı yerel denetim gerekir; ikinci bir doğrulama modeli çağrılmaz.

Denetim bir sözcük listesine indirgenemez. Doğru makam adlarının geçmesi,
doğru kişiye atıf kanıtı değildir. Denetlenecek birim:
**özne + yüklem + nesne/makam + yön + zaman + olumlama/olumsuzlama + kanıt**.
Birinci şahsın öznesi `SpeakerContext`, üçüncü şahsın öznesi çözülen varlıktır.

Üç sonuç önerilir:

- `SUPPORTED`: yalnız desteklenen dar dilbilgisi/şablonlar veya aynen kaynak
  aktarımı kapsamında bütün olgusal iddialar çözülebilmiş ve kanıta bağlanmış.
- `REJECTED`: yanlış özne, ters yön, yeni makam/ilişki, zaman veya olumsuzluk
  hatası gibi açık uyumsuzluk var.
- `UNVERIFIABLE`: metnin tüm iddiaları güvenilir biçimde çözülemiyor.

`UNVERIFIABLE`, başarı değildir. Sadece “bağlı” sözcüğünün geçmemesi başarı
değildir; örtük dayanışma, koordinasyon ve yetki iddiaları da sorun olabilir.
Regex veya dar bir Türkçe çözücü keyfî serbest metni eksiksiz doğrulayamaz.
İlk sürümde doğrulanmış sayılacak ifadeler bu nedenle sınırlı tutulur.

Olgusal yolda reddedilen veya doğrulanamayan aday tamamen bırakılır; planın kısa
deterministik cevabı kullanılır. Sadece şüpheli cümleyi silmek kalan zamirleri
ve anlamı bozabilir. Kanıt yoksa “Bu bağlılığa ilişkin doğrulanmış kaydım yok”
denir; “kimseye bağlı değil” denmez. Çelişkili kanıtta kesin sonuç verilmez.
Otomatik retry veya ikinci model çağrısı yoktur.

Saf sohbetin tümünü bu dar dilbilgisine uydurmak önerilmez. Bu yolda Gemma'nın
üslubu ve açık uçlu konuşması korunur; kaynak kuralları uygulanır, açıkça
saptanabilen belediye iddiaları yerel denetime alınır. Keyfî sohbet içinde
tespit edilemeyen bütün olgusal iddialar için kesin doğruluk garantisi verilemez.
Bu kalan risk, deterministik kapsamın güvencesinden ayrı raporlanır.

Makam/ilişki doğrulaması gereken model yanıtları tamamlanana kadar tamponlanır;
ham token'lar actionbar'a bile çıkmaz. Bekleme göstergesi sürer. Böylece geri
dönüş, yanlış bilginin oyuncuya gösterilmesinden önce gerçekleşir.

## 5. Mevcut akışa küçük ekleme noktaları

Önerilen akış:

```text
Mevcut konuşmaya kabul / kilit / moderasyon / cooldown
  → aynı oturum + aynı lore snapshot'ı + SpeakerContext
  → QueryFrame ve AnswerPlan
      → yerel cevap / netleştirme
      → veya mevcut Gemma backend'i → gerekiyorsa yerel doğrulama / geri dönüş
  → tek ortak sonlandırma: son metin, geçmiş, log, gönderim, timeout
```

- `LoreEngine` salt okunur tipli kayıt sorgusu sunar; cevap üretmek için
  `sourceData` JSON'u yeniden parse edilmez. Retrieval tanıları korunur.
- Önerilen `AnswerPlanner` / `VerifiedAnswerRenderer` / `AnswerValidator`
  Bukkit ve ağdan bağımsız küçük bileşenler olur. İsimler taslak öneridir.
- `GroundedPrompt` daha önce hesaplanmış plan/kaynak snapshot'ını alır;
  aynı soru farklı snapshot ile yeniden retrieve edilmez.
- `ConversationManager` yeni yolu mevcut kabul kontrollerinin arkasında seçer.
  Yerel cevaplar da pending durumunu temizler, cooldown/timeout ve oturum
  geçerliliği kontrollerinden geçer; ayrı bir gönderim kestirmesi oluşturulmaz.
- Geçmişe, izinli loga ve daha sonra hafıza özetine yalnız oyuncuya gönderilen
  son yanıt girer. Reddedilmiş model adayı hafızaya yerleşmez.
- Mevcut formatter, mesaj anahtarları, Citizens seçimi ve sahiplik/kilit
  davranışları korunur. Yeni eksik kayıt/netleştirme cümleleri mesaj sistemi
  üzerinden düzenlenebilir olur; mevcut dosyalar topluca yeniden yazılmaz.
- Görev evaluator'ı ve kapanış çağrıları bugün ayrı model çağrıları yapabiliyor;
  “tek çağrı” bütçesi yeni cevap/doğrulama katmanına aittir. Mevcut görev ve
  hafıza mekanizmaları bu tasarım gerekçesiyle sessizce kaldırılmaz.
- İlk uygulamada yerel cevap kestirmesi yalnız `freeform` NPC'lerde açılması
  önerilir. Evaluatable görevlerde mevcut akış korunur; görev sayacı, anlaşma,
  ödül ve sonuç değerlendirmesi etkilenmez. Bu NPC'lere genişleme ayrı
  entegrasyon testleriyle yapılır. Yerel bilgi cevabı görev başarısı sayılmaz.

Reload mevcut snapshot yayımlama davranışını korur. Eski YAML biçimleri
okunabilir kalır; yeni deterministik yetenekler yalnız yeterli yapılandırılmış
kanıtla açılır. Wiki/lore araması belediye dışındaki konuşmalarda devam eder.
Gerçek dosyaların geçişi ve canlı etkinleştirme bu tasarımın kapsamında değildir.

## 6. Seçenekler ve önerilen sıra

| Seçenek | Kazanç | Sınır | Öneri |
| --- | --- | --- | --- |
| Yalnız kimlik ayrımı ve güçlü prompt | Küçük değişiklik, serbest üslup | Olgusal güvence yok; B03 tekrarını engelleme garantisi yok | Yardımcı katman |
| Doğrudan doğrulanmış cevap + incelenmiş üslup | Özne/yön kodda korunur, açık soruda 0 çağrı | Dar niyet kapsamı ve kayıt geçişi gerekir | İlk uygulama |
| Gemma'nın olguyu yeniden ifade etmesi + yerel kontrol | Daha fazla üslup çeşitliliği | Serbest Türkçede doğrulama eksik; sık güvenli geri dönüş gerekir | Varsayılan kapalı, sonraki deney |
| Modele yalnız izinli şablon kimliği seçtirmek | Olguyu model yazmaz | Basit soru için gereksiz çağrı ve gecikme | İlk aşamada gereksiz |
| Her yanıta ikinci doğrulama modeli | Ek anlamsal denetim adayı | CPU maliyeti; ikinci model de yanılabilir | Önerilmiyor |

İlk aşama onayından sonra önerilen uygulama sırası: kimlik ve tipli plan;
dar soru çözümleme; makam şablonları; yönlü ilişki şeması ve uyumluluk;
freeform konuşma entegrasyonu ve geri dönüş; çevrimdışı doğrulama.
Model ile yeniden ifade etme bu çekirdeğin ön şartı değildir.

## 7. Planlanan testler ve kabul koşulları

Aşağıdakiler yeni test planıdır; bu tur çalıştırılmış test sonucu değildir.
Mevcut kaynak seçimi testleri korunur; son cevabın doğruluğu ayrıca denetlenir.

| Vaka | Beklenen davranış |
| --- | --- |
| B03'ün dondurulmuş sorusu ve “iladymoon'un görevleri neler?” | İki makam iladymoon'a ait; Doğan adına makam veya koordinasyon eklenmez; 0 cevap çağrısı |
| Doğan'a “Sen hangi görevdesin?” / “Doğan Tırpan hangi görevde?” | Aynı doğrulanmış Doğan kaydı; güvenli birinci şahıs mümkün |
| “Ben hangi görevdeyim?” | Oyuncu NPC'ye eşitlenmez; kayıt yoksa uydurulmaz |
| Başka NPC'ye Doğan'ın makamını sormak | Doğan üçüncü şahıs; anlatıcının makamı değişmez |
| Kimlik bağı yok / aynı görünen ad / alias çakışması | Birinci şahısla makam üretmez; tahmin yapmaz |
| “Belediye Başkanı kim?” | Tech; yardımcı ve vekil başkanlıkla birleştirilmez |
| “Sen kime bağlısın?” / “Tech'e kim bağlı?” | Açık kimlik/yönle Doğan → Tech; ters kenar yaratılmaz |
| “Tech kime bağlı?” / “iladymoon kime bağlı?” | Doğrulanmış kayıt yok; “bağlı değil” sonucuna varılmaz |
| “Doğan iladymoon'a bağlı mı?” ve bilinmeyen kişi çifti | Başka kişinin ilişkisinden cevap üretmez; kanıt yokluğu olumsuz gerçek sayılmaz |
| “Doğan ile iladymoon aynı görevde mi?” | İki kişinin doğrulanmış makamlarını ayırır; tamlık yoksa yalnız kayıt kapsamını karşılaştırır |
| Tarihsel görev / belirli yıl | Güncel kayıtla cevaplamaz; `current: false` tek başına yıl kanıtı değildir |
| Eksik/çelişkili kayıt veya bütçe yüzünden eksilen kaynak | Kesin/tam liste iddiası yok; uygun güvenli geri dönüş |
| “Nasılsın?”, siyasi karakter yorumu, “görevini iyi yapıyor mu?” | Gemma yolu; anahtar sözcük yüzünden makam listesine dönmez |
| “Başkanım nasılsın?” / oyun görevi veya ödül isteği | Hitap ve oyun görevi makam sorgusu sayılmaz |
| “Görevleri neler, bugün nasılsın?” | İki niyet görünür kalır; en fazla 1 cevap çağrısı, olgusal çekirdek değişmez |
| Belirsiz “o”, alıntılı hitap ve iç içe yanlış bağlılık varsayımı | Güvensiz özne çözümlemez; varsayımı gerçek kabul etmez |
| B03'teki iki başarısız adayın yerel yeniden oynatımı | Yanlış birinci şahıs ve kaynaksız ilişki reddedilir; yeni inference yok |
| Doğru makam adı + yanlış kişi / ters yön / olumsuzluk / örtük destek | Yalnız adlar doğru diye doğrulanmış sayılmaz |
| Anlaşılamayan serbest ifade | `UNVERIFIABLE`; otomatik kabul veya retry yok |
| Eski hafıza/kişilik/geçmişte yanlış makam ve oyuncu enjeksiyonu | Kanıt kaynağı sayılmaz; son cevapta yanlış iddia korunmaz |
| Streaming ve reddedilen aday | Yanlış token oyuncuya/önizlemeye/geçmişe çıkmaz |
| Pending, cooldown, oturum değişimi, oyuncunun ayrılması, timeout | Mevcut kilit ve geç yanıt korumaları yerel cevapta da korunur |
| Görev, buton, ödül, max-exchanges, lock-after-task | İlk kapsamda mevcut evaluatable yol değişmez; bilgi yanıtı ödül tetiklemez |
| Eski YAML, reload hatası, alias ve belediye dışı lore | Mevcut uyumluluk ve son iyi snapshot korunur |

Çevrimdışı Java testlerinde sahte backend ile çağrı sayısı, yayınlanan tam
metin ve reddedilen adayın hiçbir çıktıya sızmaması denetlenir. Yönlendirme
için hem olumlu hem olumsuz örnekler kullanılır; beklentiler sonuç görülmeden
dondurulur. Özel B03 örnekleri özel alanda kalır; Git'teki regresyonlar sentetik
eşdeğer kullanır. Eski 750 sorgunun etiket geçişleri ayrı değerlendirilir;
önceki eşleşme sayıları yanıt doğruluğu diye sunulmaz.

Java uygulaması yapılırsa JDK 26 ile gerçek Java testleri ve izinli
`Deploy-Superseed.ps1 -Mode Build` derlemesi gerekir; ana kaynak hedefi 17
korunur. İlk kabul: açık bilgi sorularında doğru özne/yön, olgu eklememe,
bilinmeyeni olumsuz gerçeğe çevirmeme, sohbeti zorla eşleştirmeme ve yeni
doğrulama modeli çağrısı eklememe. Performans ölçülmeden hızlanma oranı verilmez.

Bu belge yalnız mimari rapordur. Model denemesi, gerçek yapılandırma geçişi ve
canlı dağıtım için mevcut kullanıcı onayı sınırları geçerlidir. Çalışma
`dev/superseed-automation` / mevcut PR #2 kapsamında kalır; yeni PR veya master
işlemi önerilmez.
