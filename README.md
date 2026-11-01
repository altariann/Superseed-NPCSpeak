<div align="center">
   
[![Modrinth](https://img.shields.io/badge/Modrinth-Download-1BD96A?style=for-the-badge&logo=modrinth&logoColor=white)](https://modrinth.com/plugin/npcspeak)

</div>

---
# NPCSpeak

### Superseed: Windows geliştirme ve güvenli dağıtım

`Deploy-Superseed.ps1`, Windows PowerShell 5.1 ve üzeri için hazırlanmıştır.
Varsayılan mod yalnızca okur; sunucuya RCON bağlantısı kurmaz, `stop` göndermez
ve sunucu dosyalarını değiştirmez. BAT, Java komutu, port sahipliği ve mevcut
NPCSpeak JAR kimliği denetlenir. İşlem bilgilerine erişilemiyorsa işlem durur.

```powershell
# Yalnızca inceleme (sunucu yolunu kendi mutlak yolunuzla değiştirin)
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\Deploy-Superseed.ps1 -ServerRoot "D:\Minecraft\Server"

# Sunucuya erişmeden temiz derleme + Gradle testleri + çevrimdışı betik testleri
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\Deploy-Superseed.ps1 -Mode Build -JavaHome "D:\Java\jdk-26"

# Sadece betik testleri; geçici dosyalar ve taklit sunucu kullanılır
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tests\Test-Deployment.ps1
```

Kaynak kodda varsayılan sunucu klasörü yoktur. İsterseniz örneği yerel ayara
kopyalayıp `ServerRoot` değerini kendi **mutlak** sunucu yolunuzla düzenleyin:

```powershell
Copy-Item .\Deploy-Superseed.settings.example.json .\Deploy-Superseed.local.json
# Yerel JSON içindeki örnek yolu düzenledikten sonra:
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\Deploy-Superseed.ps1
```

`Deploy-Superseed.local.json` Git tarafından yok sayılır; parola içermemelidir.
`-ServerRoot` verilirse yerel JSON okunmaz ve komut parametresi öncelik kazanır.
`-LocalSettingsPath` ile farklı bir JSON seçilebilir; özel dosyanızı Git'ten
ayrıca hariç tutun. Yol eksik, göreli, bozuk veya bulunamıyorsa Inspect/Deploy
sunucuya erişmeden durur. `Build` modu sunucu yolu ve yerel ayar gerektirmez.
Aşağıdaki prova/dağıtım örnekleri düzenlenmiş yerel JSON bulunduğunu varsayar;
alternatif olarak her komuta `-ServerRoot` ekleyebilirsiniz. Hiçbir ayar,
`-WhatIf` veya onay bayrağı canlı dağıtım için kullanıcı onayının yerini tutmaz.

`ExecutionPolicy Bypass` yalnızca başlatılan PowerShell sürecine uygulanır;
makinenin kalıcı yürütme ilkesi değiştirilmez. Projenin Gradle toolchain'i JDK 26,
Java derleme hedefi 17'dir. `-JavaHome` Gradle JVM'ini seçer; sunucu yeniden
başlatılırken kapanan sunucunun kullandığı Java korunur. İlk derleme Gradle ve
Maven bağımlılıklarını indirmek için internet gerektirebilir. Gradle `test`
görevi gerçek JUnit Java testlerini çalıştırır; sonuçlar
`build/reports/tests/test/index.html` dosyasındadır.

**Canlı dağıtım yalnızca kullanıcı ilk dağıtımı onayladıktan sonra yapılmalıdır.**
Aşağıdaki komutta `-ApproveDeployment` bu onayın verildiğini belirtir; betik ayrıca
interaktif onay istemez. Bir Codex oturumu bu bayrağı kullanıcı onayı yerine
kullanamaz. Sonraki dağıtımlar da onaylanmış betikle aynı komuttan yürütülebilir.

RCON yalnızca `127.0.0.1` üzerinden kullanılır. RCON tüm arayüzlerde dinliyorsa,
Windows Güvenlik Duvarı ve yönlendiricide RCON portuna dış erişimin engellendiği
ayrıca doğrulanmalıdır. Bu durumda `-RconFirewallVerified` operatörün bu kontrolü
yaptığını bildirir; bayrak güvenlik duvarını kontrol etmez veya değiştirmez.
RCON şifresi yalnızca yerel `server.properties` dosyasından belleğe okunur;
komut satırına veya çıktıya yazılmaz. Betik RCON'u etkinleştirmez, şifresini
değiştirmez. Minecraft'ın oyun portunu kapatmayın. Java properties dosyasındaki
çok satırlı veya ters eğik çizgiyle kaçış yapılmış RCON şifreleri desteklenmez;
kimlik doğrulama başarısızsa sunucu kapatılmaz.

Superseed operatörü RCON'un dışarıya kapalı olduğunu doğrulamıştır. Tüm
arayüzlerde dinleme tek başına internetten erişim olduğu anlamına gelmez;
mevcut ağ ayarlarını bu nedenle değiştirmek gerekmez. Aşağıdaki
`-RconFirewallVerified` bu mevcut operatör doğrulamasını belirtir.

```powershell
# Prova: derler ve ön kontrolleri yapar; yedek/stop/JAR değişimi/başlatma yapmaz
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\Deploy-Superseed.ps1 -Mode Deploy -ApproveDeployment -RconFirewallVerified -WhatIf

# YALNIZCA ilk canlı dağıtım onaylandıktan ve RCON erişimi sınırlandıktan sonra
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\Deploy-Superseed.ps1 -Mode Deploy -ApproveDeployment -RconFirewallVerified
```

`-ServerRoot`, `-LocalSettingsPath`, `-StartBat`, `-JavaHome`, `-StopTimeoutSeconds` (180) ve
`-StartTimeoutSeconds` (300) parametreleri kullanılabilir. Desteklenen BAT,
incelenen tek satırlı `java ... io.papermc.paperclip.Main -nogui` biçimidir.
BAT ve çalışan işlemin komutu eşleşmezse betik durur; `start.cmd`/AutoPlug
kendiliğinden seçilmez. Mevcut BAT içeriği, JVM parametreleri ve Java sürümü korunur.
Başka bir otomatik yeniden başlatıcı dağıtım sırasında devreye girmemelidir.

Dağıtım sırası:

1. `gradlew.bat clean build` ve çevrimdışı dağıtım testleri çalışır. Hata varsa
   sunucuya hiçbir yazma veya RCON komutu gönderilmez.
2. NPCSpeak kimliği `plugin.yml` üzerinden belirlenir. Sıfır veya birden fazla
   NPCSpeak JAR varsa dağıtım reddedilir. Sunucudaki kilit dosyası aynı anda iki
   dağıtım çalışmasını engeller.
3. Mevcut JAR, seçilen BAT, `server.properties`, `plugins/NPCSpeak` ve
   `plugins/Citizens` yerel `deployment-backups/<zaman>-<kimlik>/` altında yedeklenir.
   Bu klasör özel yapılandırma, RCON/API şifreleri, wiki ve oyuncu konuşmaları
   içerebilir; depoya eklemeyin veya paylaşmayın. Yeterli disk alanı bulundurun.
4. Kimliği doğrulanmış localhost RCON bağlantısından `stop` gönderilir. Java
   işleminin çıkması ve oyun/RCON portlarının kapanması beklenir. Zorla sonlandırma
   yapılmaz. Ardından NPCSpeak ve Citizens verilerinin tutarlı ikinci yedeği alınır.
5. Yeni JAR mevcut NPCSpeak JAR'ın **aynı dosya adına** yazılır ve SHA-256 ile
   doğrulanır; ikinci NPCSpeak JAR bırakılmaz. Diğer pluginler, Citizens verileri,
   NPC dosyaları, skin/ID/konum kayıtları, wiki, bilgi/mesaj dosyaları ve dünyalar
   dağıtım betiği tarafından değiştirilmez. Paper kendi plugin önbelleğini yönetir.
6. Aynı BAT gizli bir `cmd` sürecinde, aynı çalışma klasörü ve Java ile çalışır.
   Yeni `logs/latest.log` içinde NPCSpeak/Citizens etkinleşmesi, Paper `Done`
   satırı ve yeni süreç doğrulanır. Eski başlangıç satırları başarı sayılmaz.
   NPCSpeak/Citizens hataları veya kritik sunucu hataları varsa dağıtım başarısız
   bildirilir; oyuncu konuşmaları konsola basılmaz.

Başlangıç denetimi log olayını stack trace'iyle birlikte değerlendirir. Kurulu
JAR'ların `plugin.yml` kimlikleriyle doğrulanmış **başka** pluginlere ait hatalar
uyarı olarak sayılır; NPCSpeak ve Citizens etkinleşmesi, onların ardından gelen
Paper `Done` satırı ve yeni Java süreci doğrulanmışsa geri alma başlatmaz.
Bukkit'in açıkça başka bir pluginin etkinleşmesine atfettiği hata da bu kapsamdadır.
Hata ayrıntıları veya ham loglar çıktı olarak kopyalanmaz; yalnız adet bildirilir.

NPCSpeak/Citizens yükleme/etkinleşme hataları, hedef pluginin kapanması, JVM bellek
ve disk dolması hataları, port bağlama, sunucu başlangıcı/tick-loop/watchdog/oturum
kilidi hataları, `FATAL` ve sunucunun kapanması başarısızlık sayılır. Başka plugin
tetiklese bile JVM/disk hatası kritiktir. Sahibi belirlenemeyen sunucu `ERROR`/
exception kayıtları güvenli tarafta kalmak için başarısız sayılır; bilinmeyen
bir `[logger]` etiketi kendiliğinden plugin kabul edilmez. Eksik hedef etkinleşmesi
veya `Done` zaman aşımına yol açar. Bu denetim standart Paper log biçimini
destekler; özel log biçimleri için uyarlama gerekebilir. Günlükteki eski
başlangıç satırları ve tanınan oyuncu sohbet kayıtları sağlık sinyali sayılmaz.

Başlangıç hatasında yeni süreç güvenli RCON `stop` ile kapatılabiliyorsa önceki JAR
geri yüklenir ve aynı BAT ile başlatılır. Sürecin kapandığı kanıtlanamıyorsa veya
RCON çalışmıyorsa otomatik geri alma durur; zorla kapatma ve çalışan JAR'a yazma
yapılmaz. Önceki JAR'la başlatma da doğrulanamazsa yedek yolu bildirilir.
Dağıtım komutu geri alma başarılı olsa bile hata döndürür.
Veri klasörleri otomatik geri sarılmaz: yeni plugin verileri dönüştürdüyse kapalı
sunucuda yedekten elle geri yükleme gerekebilir. Dünya yedeği bu betiğin kapsamı
dışındadır; mevcut dünya yedekleme düzenini koruyun.

İlk onaylı denemede sunucuda giriş, NPC/Citizens ID'leri, skin/pozisyon, Türkçe
sohbet, tek oyunculu konuşma kilidi, mesaj renkleri, yalnız oyuncuya görünen echo,
yerel Ollama ve `/npcspeak reload` davranışını kontrol edin. Log kontrolü bu oyun
içi kontrollerin yerini tutmaz. Lore değişiklikleri aşağıda açıklanmıştır.

### Superseed v5: kaynaklara bağlı lore araması ve yönetici tanısı

**B03 durumu:** atomik belediye kayıtları ve kişi/ilişki seçimi çevrimdışı
doğrulandı. Yeni Gemma isteği gönderilmedi; yeniden test kullanıcı onayını
bekliyor. Eski/yeni kaynak farkları, 89 Java testi ve 750 sorgu karşılaştırması
[B03 inceleme raporunda](docs/b03-offline-review.md).

`knowledge.yml` içindeki yeni `municipal-records` listesi şu alanları kullanır:

```yaml
municipal-records:
  - id: mayor-tech
    type: office
    subjects: ['Tech']
    offices: ['Belediye Başkanı']
    current: true
    text: 'Tech, Superseed Belediye Başkanıdır.'
  - id: dogan-reports-to-tech
    type: relationship
    subjects: ['Doğan Tırpan', 'Tech']
    keywords: ['bağlı', 'ilişki', 'ilişkisi', 'amiri']
    current: true
    text: "Doğan Tırpan, Belediye Başkanı Tech'e bağlıdır."
```

Tam örnekte üç kişinin makamları ve Doğan'ın yetenekleri ayrı kayıtlardır.
`id`, `type`, `subjects`, `current`, `text` zorunlu; ID tekil, metin boş
olmamalı. `office` tek kişi ve en az bir tam `offices` başlığı ister;
`fact` tek kişinin ek bilgisidir. `relationship` iki açık katılımcı ister;
bağlılık kaydında sıra **bağlı kişi, bağlı olunan kişi** şeklindedir. Metinde
ilişkiyi de açık yazın. İlişki kayıtlarındaki `keywords` isteğe bağlı sorgu ifadeleridir; varsa en
az birinin soruda eşleşmesi gerekir. Makam adları kişilerin alias'ı değildir.

Bu liste mevcutsa eski geniş `municipality` metni kaynaklara eklenmez.
Boş/bozuk liste reload'u başarısız kılar ve önceki indeks korunur; eski metne
sessizce dönülmez. Liste yoksa eski YAML davranışı devam eder. Geçiş sırasında
hâlâ gereken bütün belediye bilgilerini atomik kayıtlara taşıyın; canlı dosya
otomatik dönüştürülmez. Belediye dışı `verified-facts` ve `aliases` desteklenir.
`current: false` tarihsel doğrulanmış kayıttır; açık eski/tarihsel sorularda
kullanılır, bugünkü makamın kanıtı sayılmaz. Tam Türkçe dil çözümlemesi yoktur.

Güncel kişi/makam ve doğrulanmış bağlılık sorularında wiki'den makam/ilişki
çıkarımı yapılmaz. Kayıt eksikse `NO_VERIFIED_MUNICIPAL_RECORD`; wiki adayında
`REJECT_REQUIRES_VERIFIED_MUNICIPAL_RECORD` tanısı görünür. Makam kıyaslaması
yalnız ilgili kişilerin makamlarını getirir, aralarında bağlılık üretmez.
Kişilik anahtarı önce tam eşleşir; yoksa yalnız `-`/`_` farkına göre tekil
eşleşme aranır. Çakışma varsa tahmin edilmez; NPC/Citizens ID'si değiştirilmez.

Özel değerlendirmede yapılandırma metni taşınmışsa isteğe bağlı
`-PloreEvidenceData=<eski-özel-veri-yolu>` eski dondurulmuş kanıtları doğrulamak
için kullanılabilir; arama hâlâ `loreData` üzerinde çalışır. Raporda her iki
knowledge hash'i ve ayrı kanıt profili kullanıldığı yazılır. Eski cümle
eşleşmemesi başarısızlık olarak kalır; bu seçenek beklentileri değiştirmez.

Lore motoru `knowledge.yml` ile `knowledge/wiki/*.txt` dosyalarını başlangıçta
ve `/npcspeak reload` sırasında belleğe alır. Sohbet sırasında disk/ağ erişimi,
embedding modeli veya ek AI çağrısı yoktur. Özel wiki arşivi sunucuda kalır;
wiki metinleri ve yerel `.bak` dosyaları derlenen JAR'a eklenmez.

Eski `municipality`, `characters`, `aliases` ve retrieval anahtarları desteklenir.
Mevcut sunucu dosyaları otomatik değiştirilmez. Yeni ayarlar eksikse varsayılanlar
kullanılır; aşağıdaki isteğe bağlı alanları kendi `knowledge.yml` dosyanıza elle
ekleyebilirsiniz. YAML/alan tipi hatası veya belirsiz alias varsa önceki lore dizini
korunur ve yalnız yöneticiye/konsola yükleme hatası bildirilir. NPC kimlikleri
ve Citizens verileri lore yeniden yüklemesinden etkilenmez.

| Ayar | Varsayılan ve sınır | Davranış |
| --- | --- | --- |
| `enabled` | `true` | `false` lore eklemesini kapatır, eski sohbet/görev/hafıza akışını korur. |
| `retrieval.max-snippets` | `3`, 0–8 | AI'ya en fazla bu kadar wiki pasajı; bir makaleden en fazla iki pasaj. `0` doğrulanmış kayıtları kapatmaz. |
| `retrieval.max-context-chars` | `2600`, 600–7000 | JSON kaynak verisinin toplam karakter bütçesi; dağıtılan örnek dosya `3200` kullanır. Kurallar bu bütçenin dışında kalır. |
| `retrieval.min-score` | `6.0`, 0–1000 | Wiki kabul puanı. Tam ad/alias eşleşmesinde 100 puan eklenir. |
| `retrieval.min-query-coverage` | `0.65`, 0.5–1 | Tam ad bulunmadığında ayırt edici sorgu sözcüklerinin aynı pasajda karşılanan oranı. |

Tam makale adı veya açıkça doğrulanmış alias, sözcük sırası korunarak aranır.
`Plaza` tek başına `Iron Plaza` sayılmaz. Tam `Plaza AVM` adı, doğrulanmış alias
grubundaki `Iron Plaza` makalesini bulabilir. Bir varlık açıkça eşleştiyse başka
varlıkların makaleleri eklenmez; soru açıkça iki varlığı adlandırıyorsa ikisi de
seçilebilir. İç içe adlarda uzun tam ad önceliklidir. Alias zinciri veya ayrı
adların sözcüklerini birleştirerek yeni bir ad üretme yapılmaz.

Türkçe büyük/küçük harf, aksan, `ı/i`, kesme işaretiyle yazılmış ekler ve sınırlı
bir Türkçe durum eki listesi desteklenir. Sınırsız önek benzerliği kullanılmaz.
Tam ad bulunmazsa en az iki **farklı**, ayırt edici sözcük, yeterli kapsam ve
arşivde seyrek bir sözcük gerekir. Başlıktaki sözcükler gövde eşleşmesi yerine
sayılmaz. Gövde puanı, eşleşen her sözcük için
`2 × (1 + ln(1 + (makaleSayısı + 0.5)/(sözcüğüİçerenMakaleSayısı + 0.5)))`
toplamıdır. Eşit puanlar başlık/kaynak/pasaj sırasıyla deterministik çözülür.

Bilgi önceliği:

1. `knowledge.yml` içindeki açıkça doğrulanmış güncel kayıtlar. `municipal-records` yoksa eski `municipality`
   alanı güncel kayıt sayılır ve artık tüm NPC'lerce kullanılabilir.
2. İlgili wiki alıntıları. Bunlar tarihsel olabileceği için güncel görev/atama
   veya sahiplik belgesi sayılmaz.
3. Kişilik ve üslup. Kişilik, oyuncu hafızası ve önceki AI yanıtları olgusal
   bilgi kaynağı sayılmaz; eksik isim, tarih veya ilişkiyi dolduramaz.

Kaynaklar JSON veri nesneleri olarak kurallardan ve kişilikten ayrılır.
Alıntılardaki talimatlara uyulmaması, kaynakta olmayan ayrıntı için kısaca
doğrulanmış kayıt bulunmadığının söylenmesi istenir. Kurallar karakter bütçesiyle
kesilmez. Sığmayan kaynak/pasaj bütünüyle elenir; çok uzun tek bir cümle
yarıya kesilerek modele verilmez. Güncel kayıtlara wiki'den önce bütçe ayrılır.

Farklı varlıkların ilişkilerini alias olarak tanımlamayın. Örneğin bu kaydı mevcut
`knowledge.yml` dosyanızdaki `verified-facts` listesine ekleyebilirsiniz:

```yaml
verified-facts:
  - id: pursuing-evidence-production
    subjects: ['Pursuing Evidence']
    keywords: ['Visual Node', 'SPAT']
    current: true
    text: 'Pursuing Evidence, SPAT tarafından oluşturulan bir Visual Node yapımıdır.'
```

`subjects` tam adları, `keywords` ise sözcük veya tam ifadeleri eşleştirir.
`current: false` doğrulanmış fakat güncel olduğu ileri sürülmeyen bir kaydı
belirtir. Alanlar verilmezse kayıt metnindeki sözcükler kullanılır. Dosyayı
düzenleyen yönetici kayıtların doğruluğundan ve güncelliğinden sorumludur.
Mevcut `aliases.<kanonik-ad>.names` ve `.note` biçimi korunur; yalnız aynı
varlığın doğrulanmış adları/eski adları için kullanılır. Aynı ad iki farklı
kanonik gruba verilirse yükleme reddedilir. Satır içi noktalı varlık adları desteklenir.

Tanı komutu:

```text
/npcspeak lore dogan-tirpan Plaza AVM'nin eski adı nedir?
/npcspeak lore dogan-tirpan Belediye başkanı kimdir?
/npcspeak lore dogan-tirpan Pursuing Evidence kim tarafından yapıldı?
/npcspeak lore dogan-tirpan Olmayan Zümrüt Sarayı'nın sahibi kim?
```

Komut `npcspeak.admin` **ve** `npcspeak.lore.debug` yetkilerini gerektirir;
ikincisi varsayılan olarak yalnız OP'lere verilir. Yalnız komutu çalıştırana
durum, açık varlık eşleşmeleri, seçilen/elenecek en fazla on ek aday, puan,
kapsam, eşleşen sözcükler ve AI'ya gerçekten verilen kaynak JSON'u gösterilir.
`EXACT_TITLE` tam başlık, `VERIFIED_ALIAS` doğrulanmış ad, `BODY_SCORE` gövde
puanı, `REJECT_*` ret, `OMIT_*` ise pasaj/karakter sınırı nedenidir. Wiki
alıntısı verilmemesi ayrıca belirtilir; doğrulanmış kayıt olması bundan ayrıdır.

Bu komut aynı anlık dizindeki sohbet aramasını tekrarlar; NPC'ye veya Ollama'ya
soru göndermez, oyuncu sohbetlerini tutmaz ve önceki konuşmaları okumaz.
NPC kimliği varlık kontrolü içindir; kaynak dizini tüm NPC'ler için ortaktır.
Kendi deneme sorularınızı kullanın: Paper veya başka yönetim araçları yönetici
komutlarını ayrıca loglayabilir. Lore tanısı kendiliğinden oyuncu mesajlarını
veya alıntıları sunucu loguna yazmaz. Mevcut isteğe bağlı konuşma logu özelliği
ayrı olarak korunur.

JUnit testleri sentetik metinlerle retrieval, alias, ilgisiz kaynak reddi, boş
sonuçlar, Türkçe, bütçe, kaynak prompt'u, yetki sınırı ve gerçek YAML yükleme/
hatalı reload davranışını sınar. Özel 221 makalelik arşivin tamamı veya gerçek
Gemma çıktısı testlere dahil değildir. Arama sözcükseldir; yazım hataları ve
listelenmemiş ekler yanlış negatif üretebilir. Küçük modellerin bu kurallara
uyması mutlak garanti değildir. İlk onaylı sunucu denemesinde aynı soruyu
farklı NPC'lerde sorup tanı kaynağıyla yanıtı karşılaştırın; bilinmeyen isim,
sahiplik ve tarih sorularında uydurma ayrıntı vermediğini kontrol edin.

Gerçek arşiv için AI'sız Aşama A yürütüldü; beklentilerin doğrulanma durumu ve
ölçümler [Aşama A raporunda](docs/stage-a-results.md) açıklanmıştır.
[Kontrollü test planındaki](docs/lore-validation-plan.md) B pilotu üçüncü
yanıtta durduruldu; [Aşama B raporu](docs/stage-b-results.md)
koşulları, sınırları ve sonraki kontrol önerisini açıklar. C aşaması çalıştırılmadı.

`prepareLorePilot` yalnız özel istek dosyaları üretir; HTTP çağrısı yapmaz.
Gerçek model çağrısı yalnız `scripts/Invoke-LorePilot.ps1` ile açıkça seçilmiş
**tek** istek için yapılır. Bu araç normal `build` tarafından çalıştırılmaz;
yalnız çevrimdışı `Test-LorePilot.ps1` denetimleri çalışır. Inference proxy ve
redirect kullanmadan `127.0.0.1` adresine gider, model digest'ini doğrular,
boş bellek eşiğini kontrol eder, mevcut yanıtı ezmez ve otomatik retry yapmaz.
Gövdeye üretimden farklı inference seçenekleri eklenemez. HTTP başarısı olgusal
başarı sayılmaz; her yanıttan sonra kaynak incelemesi gerekir. Özel prompt ve
yanıt dosyalarını Git'e eklemeyin. Bu pilot aracı RCON veya sunucu yönetimi yapmaz.

Gerçek arşivden gelen düzeltmeler: açık yerel `#REDIRECT`, `#YÖNLENDİRME` ve
`Şuraya yönlendir:` kayıtları en fazla üç adımda takip edilir. Döngü, eksik veya
belirsiz hedefte tahmin yapılmaz. Bunlar yönetici alias'ı sayılmaz; hedef
alıntının JSON'unda `viaWikiRedirect`, tanıda `WIKI_REDIRECT` görünür. Aynı
pasaj iki kez gönderilmez. Infobox `alan=değer` birimleri korunur; açık ad
eşleştikten sonra pasajlar sorulan ayrıntıya göre sıralanır. `"…" adlı/isimli`
biçimindeki bilinmeyen adlar, bilinen bir önek veya ortak gövde sözcükleriyle
değiştirilmez. Genel bir sözcük kendi tam kayıtlı makalesi olarak tek konu
halinde sorulabilir; başka bir soruda geniş eşleme anahtarı olamaz.

Özel arşivi kopyalamak ve üretim motorunu AI olmadan değerlendirmek için
araçlar isteğe bağlıdır; normal `build` özel veriye bağımlı değildir:

```powershell
# Yalnız gerekli knowledge.yml ve wiki; mevcut snapshot üzerine yazmaz
.\scripts\Copy-LoreSnapshot.ps1 -SourceData "D:\Minecraft\Server\plugins\NPCSpeak" -SnapshotRoot "D:\PrivateTests\run-001"

# Klasörler mutlak yol olmalı; rapor klasörü yeni ve veri klasöründen ayrı olmalı
.\gradlew.bat evaluateLore -PloreMode=inventory -PloreData="D:\PrivateTests\run-001\data" -PloreOutput="D:\PrivateTests\run-001\inventory"
.\gradlew.bat evaluateLore -PloreData="D:\PrivateTests\run-001\data" -PloreCases="D:\PrivateTests\run-001\cases.json" -PloreOutput="D:\PrivateTests\run-001\results"
```

Snapshot dosyaları hash ile doğrulanıp salt okunur yapılır; kaynak dosyaların
nitelikleri değiştirilmez. Gerçek config.yml/server.properties, NPC, Citizens,
oyuncu veya chat dosyaları kopyalanmaz. Arşiv, sorular, kanıtlar ve ham raporlar
Git'ten hariç özel klasörde kalmalıdır. Bu oturumun özel verileri `.local-safety`
altındadır; başka bir klasör kullanırsanız Git'ten ayrıca hariç tutun.

Vaka JSON'u bir nesne dizisidir; her nesnede `id`, `group`, `query`,
`reviewStatus`, `allowedTitles`, `requiredTitles`, `noSources`,
`requiredEvidence`, `requiredKinds`, `forbiddenKinds`, `evidenceFile`,
`evidenceQuote` bulunur. Liste alanları boş olsa da `[]` olmalıdır.
`evidenceFile` veri klasörüne göreli kanıt dosyasıdır; `evidenceQuote` o dosyada
bulunmalıdır. Sadece dondurulmuş kaynaklardan beklenti yazın; sorgu sonucundan
beklenti üretmeyin. `reviewStatus`: `HUMAN_VERIFIED`,
`SOURCE_REVIEWED_BY_ASSISTANT` veya `GENERATED_UNREVIEWED`. İnsan doğrulaması
olmadıkça ilk değeri kullanmayın. Kanıtlı güncel kayıt beklentisi üretim
config'inde eksikse bu fark ayrıca işaretlenmelidir.

Araç gerçek `KnowledgeBase.load`/`LoreEngine.retrieve` kodunu kullanır; ayrı
bir arama uygulaması içermez. Vaka hash'i, motor class hash'i, inceleme
durumuna/gruba göre eşleşmeler, p50/p95, tüm aday/gerekçeler ve birebir JSON
kaynaklar özel rapora yazılır. Ters makale sırasıyla sonuç eşitliği, JSON,
bütçe ve tam grounding kuralları da denetlenir. `evaluateLore` rapor üretir;
komutun başarılı tamamlanması tüm vakaların geçtiği anlamına gelmez.
`summary.json` ve `results.jsonl` okunmalıdır. Eski raporlar üzerine yazılmaz.

A Paper 1.20+ plugin that gives your NPCs real conversations, powered by a language model of your choice: a local Ollama install or any OpenAI-compatible API. Players walk up, right-click, and type. The NPC actually answers, in character, instead of cycling through three canned lines.

That alone is a nice trick. What makes it worth building a server around is the **task system**: NPCs that aren't just chatty, but actually want something from the player, and only reward them once they've genuinely gotten it.

---

## Why this exists

Most "AI NPC" plugins stop at the chat bubble. You talk, something vaguely on-topic comes back, and nothing changes in the world. NPCSpeak treats the conversation as a means to an end. A merchant isn't just roleplaying haggling, he's actually deciding, turn by turn, whether your offer is good enough, and he won't hand over the sword until a second, independent AI call confirms both of you agreed on the same price. A village guard won't wave you through the gate just because you said something plausible-sounding either; he'll only do it once your argument is genuinely convincing enough that *he*, in character, says so.

That second AI call, the **evaluator**, is really the core idea of the plugin. It watches the conversation from the outside and answers one question: did the task actually get completed? Only when it says yes do the `on-success` commands fire: give the item, take the gold, set the permission, whatever you've configured. If the player stalls, refuses, or wanders off, `on-failure` commands run instead. None of this is decided by pattern-matching keywords in the player's message. It's judged in context, the same way a human running the NPC would judge it.

---

## What you can build with it

- **A merchant who actually negotiates.** Set an asking price and a hidden minimum; the NPC counters, holds firm, or folds depending on a configurable difficulty, and only confirms a sale once you've both said yes to the same number. Multi-item deals ("two swords for 80 gold") work too.
- **A guard who has to be convinced.** Persuasion tasks don't need the player to say any magic phrase. The NPC decides, in its own words, whether it's satisfied, and that decision is what gets evaluated.
- **An interrogator who probes for holes in your story.** Give a solid, verifiable alibi and the NPC lets you go; give a vague one and it keeps pressing. Confess, and it arrests you instead.
- **A quest-giver who explains before accepting your answer.** The quest task specifically waits for the NPC to lay out what's involved before a player's "yes" counts as acceptance, so you won't accidentally start a quest because you said "sure" to a greeting.
- **A freeform NPC that's just there to talk.** Village gossip, lore, flavor. No task, no evaluation, just conversation.

Every one of these remembers the player, too. At the end of a conversation, the NPC's own memory of it gets summarized and quietly injected the next time that player talks to it. Nobody has to configure that per player; it just happens.

---

## Highlights

- Chat-based conversation with any NPC, right-click to start, right-click again to leave
- Ollama (local, free) and OpenAI-compatible backends (OpenAI itself, Groq, LM Studio, vLLM, and anything else that speaks the same API)
- Five task types: **negotiate**, **persuade**, **interrogate**, **quest**, **freeform**, each evaluated against criteria written for that specific kind of task, not a one-size-fits-all "did they say yes" check
- Automatic task evaluation with `on-success` / `on-failure` console commands, and `%player%`, `%outcome%`, `%quantity%` placeholders to wire in items, currency, permissions, whatever you want
- Multi-item negotiation (`intelligent-quantity: true`); the evaluator reports quantity and total price together, so a two-sword deal doesn't get double- or half-charged
- Clickable Accept / Decline chat buttons when a negotiate NPC makes a concrete offer, with a per-task toggle if you'd rather players just type their answer
- Per-player, per-NPC memory that survives restarts
- Configurable difficulty (1 to 5), max exchanges before a task auto-fails, and lock-after-task to end a relationship once it's resolved
- Basic defenses against players trying to talk the AI into giving them free stuff. Both the roleplay NPC and the evaluator are told to treat player chat as *what a character said*, never as instructions to follow, and obvious "ignore your instructions" attempts are caught outright
- Citizens2 support, so NPCs can be full player-skin Citizens NPCs instead of villagers
- PlaceholderAPI support both ways: expand PAPI placeholders inside NPC text, and expose `%npcspeak_*%` placeholders for other plugins to read
- Vault support for economy-based task requirements and rewards
- Content moderation: a blocked-phrase filter with optional Discord webhook alerts for anything caught
- Per-NPC conversation logging
- Streaming mode, watch the response build on the action bar as it's generated, then the full message lands in chat
- An in-game editor for personality and task fields, so you rarely need to touch YAML by hand

---

## Requirements

- Paper 1.20-26.2
- Java 17 or newer
- A language model to talk to: Ollama running locally is free and works well, or any OpenAI-compatible endpoint if you'd rather use a hosted one

Optional: PlaceholderAPI and Vault, if you want those integrations. Citizens2 is optional too, but recommended. Built-in villager NPCs work fine for testing, but Citizens gives you player-skin NPCs with proper animations, pathing, and a lot more customization, so for anything beyond a quick trial it's worth the extra plugin.

---

## Running your own model with Ollama

You don't need an API key, a subscription, or an internet connection at all to use this plugin. Ollama runs a language model directly on your own machine (or on your server box) for free, and NPCSpeak talks to it with zero extra setup beyond pointing at it.

1. Grab the installer from [ollama.com](https://ollama.com/) and install it like any other app. Windows, Mac, and Linux are all supported.
2. Pull a model. Open a terminal and run:
   ```
   ollama pull llama3
   ```
   This downloads the model once. `llama3` is a solid default for this plugin: fast enough for real-time chat and good at staying in character. Most 7 to 8 billion parameter models run comfortably on a normal gaming PC, and smaller quantized models work fine too if your hardware is more modest.
3. Ollama runs a local server automatically once it's installed, normally at `http://localhost:11434`. You can confirm it's up with `curl http://localhost:11434` in a terminal, it should respond with "Ollama is running".
4. Leave `backend: ollama` in `config.yml` as-is; the default URL already points at Ollama's default port. Start (or reload) your server and talk to an NPC.

If your Minecraft server runs on different hardware than the machine you're testing on, install Ollama on that box instead (or anywhere reachable from it) and change `ollama.url` accordingly.

That's the whole setup. No cost, no external API calls, and everything stays on hardware you control.

---

## Getting started

1. Drop the plugin JAR into your server's `plugins/` folder and start the server once; it'll write a default `config.yml`.
2. Point it at your AI backend: leave `backend: ollama` if you followed the steps above, or set `backend: openai` and fill in `openai.url` / `openai.api-key` for a hosted or self-hosted OpenAI-compatible server.
3. In-game, run `/npcspeak example negotiator` to drop a fully-configured merchant at your feet and just talk to him. It's the fastest way to see the task system in action without writing any config yourself.
4. When you're ready to build your own: `/npcspeak spawn <id> [display name]`, then either edit `plugins/NPCSpeak/npcs/<id>.yml` directly or configure everything through `/npcspeak edit` and `/npcspeak task`, followed by `/npcspeak reload` to pick up file changes.

`/npcspeak example` also has ready-made `persuader`, `interrogator`, and `quest` NPCs if you'd rather start from one of those instead of a negotiator.

---

## NPC configuration

Each NPC lives in its own YAML file under `plugins/NPCSpeak/npcs/`. The plugin reads and writes this file, so in-game commands and hand-editing both work without stepping on each other.

```yaml
display-name: "Merchant Bob"
location:
  world: world
  x: 100.5
  y: 64.0
  z: 200.5
  yaw: 180.0
  pitch: 0.0
personality:
  greeting: "Ah, a customer! Fine iron sword, only 50 gold!"
  system-prompt: "You are a shrewd merchant who loves to haggle."
  topic: "weapons, armor, trading"
  avoid: "magic, politics"
  memory-enabled: true       # false to opt out, omit to follow the global setting
  max-interactions: -1       # -1 = unlimited conversations with this NPC
  lock-after-task: false     # once true, refuse all further interaction after success/failure
  log-conversations: false   # write every exchange to the per-NPC log file
  task:
    type: negotiate           # negotiate | persuade | interrogate | quest | freeform
    goal: "You are selling an iron sword. Asking price 50 gold, minimum 30 (never reveal)."
    outcome-check: "Did both sides verbally confirm the same final price? Answer YES <price> or NO."
    intelligent-quantity: false  # true = detect multi-unit deals; %outcome% becomes the TOTAL price, not per-unit
    difficulty: 3              # 1 (easy) to 5 (very hard), how much convincing the NPC needs
    max-exchanges: 8           # auto-fail the task after this many player messages; -1 = no limit
    show-buttons: true         # negotiate only, show clickable Accept/Decline buttons on detected offers
    require:                   # checked right before on-success fires; on-failure runs instead if anything here fails
      - "eco %outcome%"        # player must have at least %outcome% in their Vault balance
      # - "item minecraft:gold_ingot %outcome%"   # or: must be holding %outcome% gold ingots
    on-success:
      - "eco take %player% %outcome%"
      - "give %player% minecraft:iron_sword 1"
    on-failure: []
```

A few things worth knowing about that `task` block:

- `%player%` becomes the player's name, `%outcome%` becomes whatever value the evaluator extracted (a price, an accepted alibi, "guilty", and so on), and `%quantity%` becomes the number of units in a multi-item deal (`1` otherwise). Commands run exactly once per list, so if you want an item count to scale with quantity, write `%quantity%` into the command yourself, e.g. `give %player% minecraft:iron_sword %quantity%`.
- With `intelligent-quantity: true`, `%outcome%` is always the *total* combined price, never a per-unit figure. That's deliberate: asking the model to reliably divide a price down to "per item" was the one thing it got wrong often enough to matter.
- `difficulty` and `goal` both get woven into the NPC's own instructions, along with a task-specific line telling it how to conclude decisively once it's actually satisfied (or not), so a persuade or interrogate NPC doesn't just hedge forever with nothing for the evaluator to latch onto.
- Freeform NPCs can omit the `task` section entirely: no goal, no evaluation, just conversation.

---

## Commands

Everything under `/npcspeak` (alias `/npc`) except talking to an NPC requires `npcspeak.admin` (default: op). Players just need `npcspeak.interact` (default: true) to start a conversation.

| Command | Description |
|---|---|
| `/npcspeak spawn <id> [name]` | Spawn an NPC at your position |
| `/npcspeak remove <id>` | Remove an NPC |
| `/npcspeak list` | List all loaded NPCs |
| `/npcspeak info <id>` | Full NPC details: provider, location, personality, memory settings, and task config all in one place |
| `/npcspeak edit <id> <field> <value>` | Edit personality fields in-game |
| `/npcspeak task <id> <subcommand>` | Manage task configuration |
| `/npcspeak example <type>` | Spawn a pre-configured example NPC (`negotiator`, `persuader`, `interrogator`, `quest`, `free`) |
| `/npcspeak memory list <id>` | List players with saved memory for an NPC |
| `/npcspeak memory clear <id> <player\|*>` | Clear saved memory |
| `/npcspeak log <id> [player]` | Show today's conversation log |
| `/npcspeak reload` | Reload all NPCs from disk |
| `/npcspeak accept` / `/npcspeak decline` | What the negotiate Accept/Decline buttons actually send; anyone mid-conversation can type these too |

Editable fields for `edit`: `name`, `greeting`, `prompt`, `topic`, `avoid`, `memory`, `maxinteractions`, `lockaftertask`, `logconversations`.

Task subcommands: `info`, `clear`, `type`, `goal`, `check`, `maxexchanges`, `difficulty`, `intelligentquantity`, `require`, `onsuccess`, `onfailure`.

---

## Config reference

```yaml
npc-provider: builtin   # builtin (villager entities) or citizens (Citizens2 NPCs)

backend: ollama   # ollama | openai

ollama:
  url: http://localhost:11434
  model: llama3
  timeout-seconds: 60

openai:
  # Base URL — change this for local OpenAI-compatible servers (LM Studio, Groq, vLLM, etc.)
  # Currently pointed at Groq's free OpenAI-compatible endpoint
  url: https://api.groq.com/openai/v1
  # API key — get one free at https://console.groq.com/keys, no credit card required
  api-key: ""
  model: llama-3.3-70b-versatile

streaming: false   # true = stream tokens to the action bar as they arrive

moderation:
  enabled: false
  blocked-phrases: []
  blocked-response: "I cannot discuss that topic."
  discord-webhook: ""

memory:
  enabled: true
  max-age-days: 30

conversation:
  max-exchanges: 10
  cooldown-seconds: 3
  listen-timeout-seconds: 60
```

---

## PlaceholderAPI

If PlaceholderAPI is installed, these become available to any other plugin:

| Placeholder | Value |
|---|---|
| `%npcspeak_active_npc%` | Display name of the NPC the player is currently talking to |
| `%npcspeak_exchange_count_<id>%` | Number of messages sent to NPC `<id>` this session |
| `%npcspeak_task_status_<id>%` | `active`, `success`, `failed`, or `none` |

The reverse also works: PAPI placeholders inside an NPC's `greeting` and `system-prompt` fields get expanded at runtime, so you can greet players by name, reference their rank, mention their balance, or pull in anything else another plugin exposes.

---

## Citizens2

We'd recommend running this alongside Citizens2 if you can. Built-in villager NPCs are fully functional, but they're still villagers: same model, same limited animation set. Set `npc-provider: citizens` in `config.yml` and NPCSpeak NPCs become full Citizens2 NPCs instead, player-skin entities with proper movement and all the customization Citizens offers on top. If Citizens2 isn't installed, the plugin quietly falls back to built-in villagers instead of failing to start, so it's safe to leave the setting as-is even if you haven't decided yet.

---

## Screenshots

Coming soon!

<!-- If you're maintaining this page and want to show the plugin off, these are the moments that sell it best:

1. **A conversation in progress.** The gold `[Merchant Bob]` prefix, a clearly contextual NPC response (not something generic), and the player's messages above it.
2. **Accept / Decline buttons.** Right after a negotiate NPC proposes a concrete price, with the two clickable buttons underneath its response.
3. **A task complete message.** The green `[ Task complete: 42 ]` line after a successful deal, followed by whatever items or XP it rewarded.
4. **A task failure.** The red `[ Task failed ]` line after a player gives up mid-negotiation or gets caught in a lie.
5. **`/npcspeak info <id>`.** Showing the full breakdown for one NPC: provider, location, personality, and task config together.
6. **A town scene.** Several different NPC types (merchant, guard captain, wizard) visible at once with their nametags, to show this living in a real server rather than a test world.
7. **Memory in action.** An NPC referencing something from a previous conversation ("Ah, you're back! Did you ever find a use for that sword?").  -->

---

## License

See LICENSE.txt

---

<div align="center">

**NPCSpeak v1.0** — Developed by **l299l** — Paper 1.20 – 26.2

</div>
