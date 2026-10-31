# İlk aşama inceleme ve PR taslağı

Önerilen PR başlığı: **Windows için güvenli NPCSpeak derleme ve dağıtım betiği**

Geliştirme dalı: `dev/superseed-automation`; kaynak tabanı `dev/superseed-source`.

Bu aşamanın kapsamı kullanıcının son talebine göre inceleme ve otomasyon
hazırlığıdır. Canlı sunucu durdurulmamış, RCON kimlik doğrulaması denenmemiş,
sunucu yapılandırması veya pluginleri değiştirilmemiştir. Canlı dağıtım ve
uçtan uca geri alma henüz denenmemiştir; ilk canlı deneme kullanıcı onayı gerektirir.

## Başlatma düzeni

`START.bat` doğrudan Paper'ı başlatıyor. Bellek sınırları `-Xms4096M -Xmx6656M`;
diğer parametreler `-XX:-OmitStackTraceInFastThrow`,
`-Dpaper.playerconnection.keepalive=2400`,
`-cp "paper.jar;plugins/Skulls/libs/sqlite-jdbc-3.44.1.0.jar"`,
`io.papermc.paperclip.Main -nogui`.

Çalışan işlem komutu BAT ile eşleşiyor ve Eclipse Adoptium Java 26.0.2.1
kullanıyor. `start.cmd` ayrı AutoPlug başlatıcısıdır; otomasyon tarafından
seçilmez. Java komutu ve port sahipliği işletim sistemi işlem bilgileriyle
okunmuştur; ilk sandbox denemesinde bu bilgilere erişim reddedilmiş,
yalnızca okuma için genişletilmiş izinle doğrulanmıştır.

RCON etkin ve Minecraft ile aynı Java işlemine aittir. Geniş ağ arayüzlerinde
dinlemektedir; operatör dışarıdan erişilemediğini ve yalnız yerel kullanımda
olduğunu doğrulamıştır. Bu durum doğrudan bir güvenlik açığı sayılmamıştır.
Betik yalnızca localhost'a bağlanır. Mevcut güvenlik duvarı, yönlendirici ve
sunucu ağ ayarları değiştirilmemiştir. RCON parolası depoya eklenmemiştir.

## Değişiklikler ve doğrulama

Betik varsayılan olarak salt okunur inceleme yapar. Build modu sunucuya erişmeden
temiz Gradle derlemesi ve çevrimdışı betik testlerini çalıştırır. Onay bayrağıyla
etkinleştirilen Deploy modu JAR/veri yedekleri, güvenli RCON stop, işlem çıkışı,
tek JAR değişimi, aynı BAT/Java ile başlatma ve yeni log doğrulamasını içerir.
Başlangıç hatasında güvenli kapanış kanıtlanırsa önceki JAR geri yüklenir.
Süreç bilgileri okunamıyorsa veya kapanış kanıtlanamıyorsa otomasyon durur.

JAR seçimi dosya adına göre değil `plugin.yml` kimliğine göre yapılır. Windows
dosya filtresi `.jar_original` dosyalarını da eşleştirebildiği için gerçek `.jar`
uzantısı ayrıca denetlenir. Bu sunucudaki eski JAR yedeğiyle doğrulanmış ve
regresyon testi eklenmiştir.

JDK 26 ile Gradle `build` ve `clean build` başarılıdır. Mevcut Java kaynaklarında
bir `ChatColor` deprecation uyarısı vardır. Gradle Java testleri `NO-SOURCE`
durumundadır. Çevrimdışı testler onay/WhatIf kontrolünü, RCON paket biçimini,
JAR kimliği/çoğaltma/eksiklik kontrollerini, hatalı derlemede sunucuya dokunmamayı,
kapanış zaman aşımında JAR'ı korumayı, başarı sırasını, yapılandırma korumasını ve
başlangıç hatasında geri almayı kapsar. Gerçek ağa veya Ollama'ya bağlanmaz.

## Lore için ön bulgular ve kapsam sınırı

`KnowledgeBase.java` ve `NpcMessages.java` ilk incelemede çalışma ağacında ve
index'te bulunuyordu; hiçbir commit'e ait değildi. `master`, `origin/master`
ve ilk otomasyon dalı aynı `e6cfb61` commit'ini gösteriyordu. Fetch sonrasında
master'ın bu durumda olduğu doğrulandı. Mevcut kaynaklar değiştirilmeden
`3c3cc09` commit'inde korundu; `7bb3bfa` commit'i özel wiki/yerel yedek
paketlenmesini ve eksik wiki örneğiyle başlangıç hatasını önledi. Kaynaklar
[PR #1](https://github.com/altariann/Superseed-NPCSpeak/pull/1) ile yayımlandı.
Master değiştirilmedi. Otomasyon ayrı commit ve PR olarak hazırlanmıştır.

İlk kod okumasında sonraki lore çalışmasında ele alınması gereken noktalar:

- Güncel belediye bilgisi yalnız `dogan_tirpan` NPC kimliğine ekleniyor.
- Türkçe ek benzerliği, sözcüğün başını ve uzunluk farkını kullanıyor;
  ilişkili olmayan adları aynı sözcük sayabilir.
- Başlık eşleşmesi yoksa iki ortak sözcük yeterli; yaygın sözcükler yeterince
  ayırt edilmediğinden ilgisiz metinler kabul edilebilir.
- Alias genişletmesi adların sözcüklerini aynı sorgu kümesine ekliyor;
  farklı adların parçaları birleşerek istenmeyen başlıkları eşleştirebilir.
- Kurallar, persona ve belediye metni aynı karakter bütçesinde yer alıyor;
  uzun girişler alıntılara alan bırakmayabilir veya metin kesilebilir.
- Kaynak seçiminin puanlarını/gerekçelerini gösteren yönetici tanısı henüz yok.

Bu maddeler ilk statik inceleme bulgularıdır; tam wiki arşiviyle ölçülmüş
doğruluk sonucu değildir. Bu aşamada lore algoritması değiştirilmemiş ve lore
doğruluğu testlerinin geçtiği iddia edilmemiştir. Sonraki PR için mevcut
Superseed değişikliklerinin yayımlanma durumu netleştirilmelidir.

## İlk canlı denemede operatör kontrolü

Önce RCON'un dış erişimini sınırlandırın ve Build/Inspect çıktılarını inceleyin.
İlk canlı dağıtım onayından sonra boş veya kontrollü bir oyuncu zamanında
Deploy çalıştırın. Yeni loglarda NPCSpeak/Citizens etkinleşmesini ve hata
olmadığını kontrol edin. Oyunda Citizens ID, skin/pozisyon, tek oyunculu konuşma
kilidi, Türkçe Ollama yanıtı, renkli mesajlar, oyuncuya özel echo ve güvenli
`/npcspeak reload` davranışlarını karşılaştırın. Logdaki etkinleşme, bu
davranışların tamamını kanıtlamaz.

Başlangıç hatası durumunda otomatik geri alma tamamlanamazsa canlı Java
işlemini zorla öldürmeyin; sunucu konsolundan `stop` verin, işlemin çıktığını
doğrulayın ve bildirilen yedekteki JAR'ı geri yükleyin. Yeni sürüm veri şeması
değiştirmişse yalnızca kapalı sunucuda ilgili veri yedeğini inceleyerek geri
yükleyin. Betik dünyaları veya veri klasörlerini otomatik geri sarmaz.
