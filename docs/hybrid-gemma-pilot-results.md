# Hibrit cevap sistemi — küçük gerçek Gemma pilotu

1 Kasım 2026. **6 yerel rota doğrulandı, 4 gerçek inference yapıldı.** Otomatik
retry, aynı sorunun ikinci inference'ı veya A/B deneyi yok. Pilot tamamlandı;
genel kalite için tam kabul verilmedi. Gerçek Minecraft dağıtımı yapılmadı.

Ham istek/yanıt, kaynaklar, model metadata'sı, inceleme ve geçiş adayları yalnız
Git'in yok saydığı özel yerel alandadır. Bu belge bunların toplu raporudur.
İncelemeler asistanın kaynak karşılaştırmasıdır; bağımsız insan onayı değildir.

## Koşullar ve üretim yolu

- Önceki üretim Gemma modelinin digest'i ve `parameters/template/system`
  değerleri başlangıçta ve sonda eşleşti. CPU (`num_gpu=0`), 4096 bağlam,
  160 çıktı tokenı ve diğer üretim varsayılanları korundu. Modelfile değiştirilmedi.
- İstek gövdelerinde yalnız `model`, `stream=false`, `messages` var.
  Ek seed, temperature, keep_alive veya başka inference seçeneği eklenmedi.
- NPC'nin gerçek dosyası salt okunur incelendi; önceki hash'le eşleşti.
  Daha önce doğrulanmış atomik özel profil ve 221 dosyalık aynı wiki kopyası
  kullanıldı. Yalnız yeni özel kopyaya açık kimlik ve yönlü ilişki alanları eklendi.
- `AnswerPlanner`, `HybridAnswerService`, `NpcConfig.buildSystemPrompt`,
  `GroundedPrompt` ve `OllamaBackend.encodeRequest` üretim kodu kullanıldı.
  Yeni `hybridPilot` aracı istek hazırlar veya kayıtlı yanıtı tekrar işler;
  kendisi HTTP göndermez. Yerel cevaplar için istek dosyası bile üretmez.
- HTTP yalnız `127.0.0.1` Ollama'ya, proxy/redirect kapalı ve mevcut 120 saniye
  timeout ile tek istek aracı üzerinden gönderildi. Her yanıt sonraki sorudan
  önce incelendi. Gerçek oyuncu hafızası/geçmişi ve PAPI genişletmesi kullanılmadı.
- Dört soru ve beklentileri ilk inference öncesinde donduruldu; girdi ve istek
  hash'leri sonda yeniden doğrulandı. Sonuçlara göre beklenti değiştirilmedi.
- Çalışan Minecraft süreci saptanmadı; sunucu başlatılmadı. Pilot aracına açık
  `-NoMinecraftProcess` modu eklendi: olası Java sunucusu varsa çağrıyı reddeder.
  Varsayılan modun açık/geçerli sunucu PID'si gereksinimi korunur. Gradle PID'si
  sunucu gibi gösterilmedi. Ön kontrolde boş RAM 9132 MB, üç CPU örneği ortalaması
  yaklaşık %3,33 idi. Canlı TPS veya oyuncu gecikmesi ölçülmedi.

İlk özel hazırlık, PowerShell 5.1'in BOM'suz UTF-8 script'i yanlış okuması
nedeniyle ilişki uçları ön kontrolünde durdu; inference yapılmadı. Başarısız
hazırlık korundu, yeni ayrı özel kopyada kodlama düzeltildi. Bu olay bir model
tekrarı değildir. Gerçek dosyalar bu düzeltmeden etkilenmedi.

## Yerel yönlendirme ve çağrı doğrulaması

| Vaka | Kontrol | Sonuç | Inference |
| --- | --- | --- | ---: |
| L01 / B03 | iladymoon belediyede hangi görevlerde bulunuyor? | İki makam iladymoon'a ait; anlatıcıya makam/ilişki eklenmedi | 0 |
| L02 | Sen hangi görevdesin? | Açık bağla Doğan'ın kendi makamı, birinci şahıs | 0 |
| L03 | Sen kime bağlısın? | Doğan → Tech; birinci şahıs | 0 |
| L04 | iladymoon kime bağlı? | Doğrulanmış kayıt yok; olumsuz gerçek veya başka ilişki üretilmedi | 0 |
| L05 | Superseed Belediye Başkanı kim? | Tech; yardımcı/vekil ile karışmadı | 0 |
| L06 | Tech'e kim bağlı? | Aynı kenarın ters sorgusu; bağlı kişi Doğan | 0 |

B03'ün nötr çıktısı:

> iladymoon için kayıtlardaki güncel görevler: Özel Kalem Müdürü ve Belediye Başkan Vekili.

Bu altı vaka için backend ve prompt hazırlama çağrılmadı. Model token sayısı
yoktur. Özel hazırlama aracının dosya yazımı, kaynak tanısı ve JIT dahil
süreleri sırasıyla 125,73 / 22,45 / 15,02 / 20,83 / 13,69 / 13,90 ms idi.
Bunlar oyun içi cevap gecikmesi veya yalnız planner maliyeti ölçümü değildir.

## Gerçek model yanıtlarının incelemesi

| Vaka | Konu | Uçtan uca | Girdi / çıktı token | Olgusal değerlendirme | Kişilik / dil |
| --- | --- | ---: | ---: | --- | --- |
| H01 | Günlük selamlaşma ve keyif sorma | 20,11 sn | 1209 / 28 | Yeni somut makam, ilişki veya belediye eylemi saptanmadı | Samimi; gösteriş zayıf, küçük Türkçe ifade kusuru var |
| H02 | Özgüven ile insanları dinleme arasında siyasi yorum | 6,45 sn | 1216 / 46 | Kendi makamı doğrulanmış kayda uyuyor; fakat bu soruda seçilen kaynak paketinde kendi makamı yok | Kendini önemseyen, özgüvenli siyasi ton daha belirgin |
| H03 | Arşivden bir yapının tamamlanma zamanı | 12,06 sn | 1422 / 30 | Zaman doğru; ek belediye eylemi cümlesi belirsiz, tam desteklenmiş sayılmadı | Tanıdık hitap var; ek cümlede bozuk Türkçe ve gereksiz genişleme |
| H04 | iladymoon'un makamları + günlük sohbet | 10,59 sn | 1430 / 42 | İki makam doğru kişide; ek ilişki yok. Model sohbet kısmını da cevapladı | Önceden incelenmiş kısa sohbet cümleleri; üslup sınırlı |

Hepsi HTTP 200 ve `done_reason=stop` ile tamamlandı. Timeout, 160 token sınırında
kesilme veya bağlam taşması görülmedi. H01'in 6,67 saniyesi soğuk model yüklemesi;
tek küçük koşudan performans üstünlüğü veya genellenebilir başarı oranı çıkarılmaz.
Yanıt sonrası boş RAM yaklaşık 5420–5625 MB aralığındaydı.

H02'deki makam yanlış değildir. Açık kalan sorun, modelin doğru kendilik bilgisini
soruya seçilmiş kaynaklar yerine olgu da içeren kişilikten söylemesidir. Sonraki
iyileştirme seçeneği, anlatıcının doğrulanmış kendi makamını ayrı bir kanıt bölümü
olarak taşımaktır; üçüncü kişi kaynaklarıyla karıştırılmamalıdır. Bu pilot içinde
prompt ayarı veya yeni model deneyi yapılmadı.

H03'te arşivdeki kurucu/yaptıran bilgisi gereksiz, dilbilgisel olarak da bozuk bir
eylem cümlesine genişletildi. Bu ek otomatik olarak kaynakla desteklenmiş kabul
edilmedi. Aynı soruya retry veya prompt tuning yapılmadı; yalnız önceden dondurulmuş
son karışık vaka tamamlandı ve gerçek inference burada durdu.

Karakter metni korunmuştur. Buna rağmen bu örnekler, gösterişli ve zaman zaman
iğneleyici tarzın kalıcı biçimde başarıldığını kanıtlamaz; günlük yanıtta ton
zayıf, siyasi yorumda daha belirgindir. Serbest model çıktısı için genel olgusal
doğrulama garantisi de verilmez.

## Tamponlama, gereksiz ret ve sohbet kaybı

İlk filtreyle **1 tamponlanan adayın 1'i reddedildi**. Bu yüzdeyi genel ret
oranı gibi kullanmak doğru değildir; örneklem yalnız bir adaydır.

H04'ün ham adayı kaynak cevabını aynen koruyup iki incelenmiş sohbet cümlesini
birlikte ekledi. İlk filtre yalnız bir cümleye izin verdiği için güvenli adayı
gereksiz yere reddetti. Oyuncuya gidecek ilk sonuç kaynak cevabı ve sohbet daveti
olacaktı; doğrudan hal-hatır cevabı kayboluyordu. Ayrıca kod incelemesinde yalnız
kaynak cümlesi içeren bir adayın kabul edilebildiği görüldü: bu da sohbet isteğini
sessizce düşürebilirdi.

Dar düzeltme uygulandı:

- Aynı iki incelenmiş cümlenin tek başına veya birlikte kullanılması kabul edilir.
- Yalnız olgusal cümleden oluşan karışık aday artık kabul edilmez.
- Güvenli geri dönüş hem kısa hal-hatır cevabı hem devam daveti taşır.
- Ek makam/ilişki veya üçüncü, serbest cümle hâlâ reddedilir; keyfî Türkçe
  paraphrase doğrulanmış sayılmaz.

Özgün ham yanıt ve ilk ret sonucu değiştirilmedi. Aynı yanıt ayrı özel klasörde
yeni filtreyle üretim `HybridAnswerService` katmanından geçirildi ve kabul edildi;
olgusal çekirdek ile sohbet cevabı korundu. **Bu işlem sıfır yeni inference içeren
yerel replay'dir.** Düzeltmeden sonra yeni gerçek model denemesi yapılmadı.

CPU/deneyim alternatifi: bu kadar dar hal-hatır birleşimlerinde kaynak cevabı ile
incelenmiş sohbet parçasını doğrudan yerel birleştirmek bu pilotta harcanan tek
10,59 saniyelik model çağrısını gereksiz kılardı. Genel sohbet yine Gemma'da kalabilir.
Bu alternatif uygulanmadı; daha serbest modele yalnız sohbet eki yazdırmak ise
keyfî ekte yeni olgular bulunmadığını güvenilir biçimde kanıtlama sorununu çözmez.

## İş parçacığı ve oturum güvenliği

İncelemede bulunan ortak akış sorunları küçük bir düzeltmeyle giderildi:

- Async chat kabulü/PAPI genişletmesi ana iş parçacığına taşındı. Ağ isteği
  asenkron kalır; ana iş parçacığında HTTP sonucu beklenmez.
- Yerel tamamlanmış future dahil bütün cevapların geçmiş/log/oyuncu teslimi
  ana iş parçacığında, güncel ziyaret yeniden doğrulandıktan sonra yapılır.
  `pending` bu teslim tamamlanmadan bırakılmaz.
- Ziyaret oturumu açılışta oluşturulur. Async giriş yeni oturum yaratmaz;
  yakaladığı ziyaret kapanmışsa kuyruktaki giriş atılır. Aynı NPC'ye yeniden
  girmek eski ziyaretin mesajını veya cevabını devralmaz.
- Eski streaming önizlemesi ziyaret ve cevap sırasıyla sınırlandırıldı.
- İptal edilmiş bir timeout'un zaten kuyruğa girmiş callback'i, hem ziyaret
  hem güncel timeout kimliğiyle denetlenir; yeni ziyaret/mesajı kapatamaz.

Gerçek `ConversationManager`, sahte Bukkit sunucu/oyuncu/kuyruğu üzerinden
13 Java testiyle sınandı: iki oyuncu için tekli kilit ve çoklu izin, hızlı mesaj,
yerel cevabın pending süresi, kapanış/yeniden açılış, teslimden önce kapanış,
quit/geç hata, bağımsız geçmişler, cooldown, eski preview, eski ve geçerli timeout,
reddedilmiş adayın gerçek manager log/oyuncu çıktısına sızmaması.
Testlerde oyuncu çıktısı ve lifecycle işlemleri yanlış thread'de hata verir.
Test loader'ı yalnız test kaynağıdır; Minecraft başlatılmaz, JAR'a paketlenmez.

Bu ortak sınırdaki düzeltme görev kabul/teslim zamanlamasını da etkiler;
ödül/evaluator kararı ve komut mantığı değiştirilmedi. Görev evaluator/kapanış
çağrılarının kendi eski asenkron callback'leri bu freeform pilotun kapsamı dışındadır.
Görevli NPC'ler için tam oyun içi kabul yapılmış sayılmaz.

Ana iş parçacığında prompt/retrieval hazırlama ve etkinse log dosyası yazımı da
bulunur. Gerçek tick etkisi ölçülmedi. Sahte kuyruk testleri canlı yük, PAPI
eklentilerinin tüm davranışları veya sunucu kapanışı için performans/güvenlik
garantisi değildir.

## Canlı dosyalara dokunmadan geçiş hazırlığı

Özel alanda gerçek dosyalardan iki aday üretildi: NPC'ye yalnız `knowledge-entity`,
knowledge'a incelenmiş `municipal-records` bloğu eklendi. İki özgün dosyanın
**bütün baytları adayların başlangıcında aynen korunuyor**; dosya adı, görünen ad,
Citizens 313, skin/konum ve mevcut personality/task ayarları yeniden yazılmadı.
Pilot profilinin ek kişilik metni veya belediye dışı `verified-facts` alanları
gerçek dosya adayına topluca kopyalanmadı. Adaylarla altı yerel rota 6/6 geçti;
bu kontrol de model çağrısı yapmadı.

Baytların korunması tek başına davranış eşitliği değildir: yeni alan, korunmuş
legacy `municipality` metnini retrieval'da gölgeler. Geçiş eşlemesi şu kapsamda
incelenmelidir:

| Eski kapsam | Yeni karşılığı / karar |
| --- | --- |
| Doğan'ın makamı | Doğan öznesine ait güncel office kaydı |
| Makamında görüşme, başvuru/şikâyet dinleme | Ayrı capabilities/fact kaydı |
| Dilekçe, ruhsat, para, ceza ve komut yeteneğinin tanımlı olmaması | Aynı capabilities kaydında açıkça korunur |
| Doğrulanmamış seçim usulü/oy sayısı/prosedür | Bilgi eklenmez; bilinmeyeni uydurmama kuralı sürer |
| Önceden kullanıcıca doğrulanmış diğer güncel makamlar | Ayrı kişi office kayıtları; kimlikler birleştirilmez |
| Doğan'ın Tech'e bağlılığı | Ayrı reports_to / from / to kenarı |

Özgün kişilikte başkan adının verilmediğini söyleyen eski ifade, yeni güncel
kayıtlarla anlamsal gerilim taşır. Minimal aday bunu sessizce değiştirmedi.
İleride ayrıca onaylanacak dar metin düzeltmesi, güncel makamların doğrulanmış
kayıtlardan alınacağını söylerken seçim/oy sayısının bilinmediğini korumalı;
gösterişli karakter ve iğneleyici üslup satırlarına dokunmamalıdır.

İlk canlı uygulama öncesi plan:

1. Ayrı kullanıcı onayı alınır; gerçek dosya/JAR hash'leri özel manifestle tekrar
   karşılaştırılır. Arada düzenleme olmuşsa aday üstüne yazılmaz, yeni diff hazırlanır.
2. Yalnız onaylanan JAR ve iki NPCSpeak dosya değişikliği kapsamı belirlenir;
   aynı BAT/Java parametreleri ve geri dönüş kopyaları hazır tutulur.
3. Citizens ID 313, skin/konum; wiki ve mesaj hash'leri, görev/kişilik alanları
   ile izinli yeni alanların diff'i yeniden gözden geçirilir.
4. Ancak bu sonraki onaylı aşamada kontrollü stop/JAR/config/başlatma uygulanabilir.
   Eski eklentiyle yeni şemayı ayrı zamanlarda etkinleştirmeye güvenilmez.
5. Oyunda önce B03, kendi görev/bağlılığı, bilinmeyen bağlılık ve iki oyunculu
   kapanış/geç yanıt senaryoları denenir; sonra serbest sohbet değerlendirilir.
6. Hata halinde onaylı kapsamda önceki NPCSpeak JAR'ı ve ilgili dosyalar geri
   alınır. Citizens, wiki, mesaj ve oyuncu verileri topluca geri sarılmaz/silinmez.

Bu adımların hiçbiri canlıda yürütülmedi. Son kontrolde gerçek BAT, sunucu
özellikleri, NPCSpeak ayar/NPC/knowledge/mesaj/JAR ve Citizens dosyalarından
izlenen **9 hash değişmedi**. Canlı 221 wiki dosyası önceki arşiv hash'leriyle,
özel 221 wiki dosyası da başlangıç kopyasıyla eşleşiyor.

## Derleme, Git ve kalan kabul

JDK 26.0.2 ile yalnız `Deploy-Superseed.ps1 -Mode Build` üzerinden aşama
derlemeleri yapıldı. Son temiz derlemede **136 Java testi, 99 PowerShell denetimi**
(73 dağıtım, 11 snapshot, 15 pilot) geçti. Java sınıf hedefi 17 / major 61.
Özel wiki, yerel test verisi, yedek ve test loader/pilot sınıfları için JAR
taramasında eşleşen girdi yok. Var olan `ChatColor` deprecation uyarısı sürüyor.
Test kurulumundaki ilk derleme/fixture sorunları giderildi; başarılı son koşu
raporlanıyor. Betiklerdeki stop/başlatma çıktıları geçici sahtelere aittir.

136 sayısı önceki 119 teste eklenen 13 manager, 3 kabul filtresi ve 1 pilot aracı
testini içerir. Normal Build bu gerçek pilotu yeniden çalıştırmaz; özel veri veya
model gerektirmez. 750 sorguluk eski retrieval değerlendirmesi bu tur yeniden
çalıştırılmadı; sonuçları yeni model başarısı olarak kullanılmadı.

`9b55873` mimari commit'i ve önceki uygulama commit'leri korunur; çalışma mevcut
`dev/superseed-automation` / PR #2 üzerindedir. Yeni PR, merge veya master push yok.
Gerçek model çağrıları burada bitti. **İlk kontrollü oyun içi dağıtım için ayrı
kullanıcı onayı bekleniyor.** Dil kalitesi ve anlatıcı kaynak kapsamı açık kalır;
bu küçük pilot genel üretim doğruluğu veya oyun içi kabul belgesi değildir.
