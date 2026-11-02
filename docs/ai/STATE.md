# Proje durumu

Son doğrulama: **1 Kasım 2026 23:46, Europe/Istanbul**. Bu dosya güncel durum özetidir;
son çalışma ve yarım iş için [HANDOFF](HANDOFF.md), kurallar için [AGENTS](../../AGENTS.md).

## Git ve kabul durumu

- Dal: `dev/superseed-automation`. Doğrulanan son uygulama commit'i: `352cbcb`.
  Bu belgelerin commit'i bunun üzerine gelir; gerçek HEAD'i Git'ten oku.
- [PR #2](https://github.com/altariann/Superseed-NPCSpeak/pull/2) açık;
  head `dev/superseed-automation`, base `dev/superseed-source`.
- [PR #1](https://github.com/altariann/Superseed-NPCSpeak/pull/1) açık;
  head `dev/superseed-source`, base `master`. Kaynak tabanı korunur.
- `9b55873` mimari, `aeccff0`/`cd60e32` ilk uygulama,
  `2589220` oturum/filtre güvenliği, `0e115f5` pilot aracı ve sonuç raporu,
  `c08cbf3` üç dosyalı güvenli dağıtım hazırlığı, `e73ba31` geçersiz plan koruması,
  `82c3218` beklenmedik Paper koruması, `e2fb567` paylaşımlı log okuma,
  `94440c1` çok niyetli tur ve başkan soruları düzeltmesi, `0a5e30b` yalnız-JAR yolu,
  `352cbcb` pilot bulgularıyla yorum filtresi düzeltmesi.
- İlk kontrollü canlı geçiş kullanıcı onayıyla 1 Kasım 22:01'de uygulandı. Yalnız-JAR
  güncellemesi 1 Kasım 23:43'te uygulandı: **`352cbcb` canlıda**, JAR `e5b8ddc6…0e7e`.
  Bu düzeltmelerin oyun içi kabulü henüz yapılmadı.

## Uygulanan mimari

- `SpeakerContext` anlatıcıyı, `QueryFrame` soru öznesini/niyetini ayırır;
  `AnswerPlan` yolu/kanıtı, `AnswerOutcome` yalnız gönderilecek son metni taşır.
- Dar ve açık güncel makam, makam sahibi ve yönlü bağlılık soruları freeform
  akışında yerel yanıtlanır. B03'ün iki makamı iladymoon'a aittir; model çağrısı 0.
  Başkan iyelik biçimleri, kısa girişler ve `X Y'ye bağlı mı` da bu yoldadır.
- Mesaj tam cümleciklere ayrılır; tanınan olgu her zaman kayıttan gelir. Olgu + tek
  yorum: model yalnız yorum cümleciğini görür (lore/geçmiş/hafıza yok), çıktı filtreden
  geçerse sabit cevabın ardına eklenir, geçmezse devam daveti kullanılır. Olgu + ayrıştırılamayan
  metin: model çağrılmaz, cevap + davet. Yalnız yorum veya sohbet Gemma'da kalır.
- "Yazıyor" göstergesi yalnız model çağrısında gösterilir; yerel cevapta gösterilmez.
- Birinci şahıs için isteğe bağlı açık `knowledge-entity` bağı gerekir.
  `reports_to`, `from`, `to` yönü belirtir; eski YAML okunmaya devam eder.
- Gündelik sohbet/karakter yorumları Gemma'da kalır. Dar karışık yolda olgusal
  çekirdek sabittir; incelenmiş sohbet ekleri dışında kalan aday yayınlanmaz.
  Yalnız olguyu verip sohbeti düşüren aday kabul edilmez. İkinci doğrulama modeli yok.
- Async giriş/PAPI ve son teslim ana iş parçacığında korunur; ağ sonucu beklenmez.
  Pending, ziyaret/cevap sırası ve timeout kimliği eski sonuçların sızmasını önler.
- Görev/ödül karar mantığı, Citizens kimliği, hafıza, mesajlar ve lore korunur;
  görev evaluator/kapanış callback'leri için tam oyun içi kabul yapılmış değildir.

## Doğrulanan son sonuçlar

- JDK 26.0.2; ana Java hedefi 17 / major 61.
- **162 Java testi, 0 başarısızlık/hata/atlama; 298 PowerShell denetimi**
  (80 dağıtım + 144 üç dosyalı + 148 yalnız-JAR + 11 snapshot + 15 pilot). Temiz
  `Deploy-Superseed.ps1 -Mode Build` başarılı; kanıt zamanı 1 Kasım 23:14 İstanbul.
  Java sayısı XML raporlarından okundu. Depo JAR'ı SHA256 `e5b8ddc6…0e7e`. Canlıya kurulmadı.
- Gerçek açılış logu çevrimdışı denetimden geçti: `Ready=True`, 2 eski hata uyarı sayıldı.
  Normal açılıştaki `All dimensions are saved` önce kapanış sayılıyordu; o hata olmasa ilk
  dağıtım sağlıklı sürümü geri almaya çalışırdı. Giderildi.
- Gemma yorum pilotu ([sonuçlar](../commentary-pilot-results.md)): 4 tekil istek, retry yok.
  Pilot filtresi 2 kabul, 2 ret; 1 gereksiz ret ve 1 kural boşluğu düzeltildi. Sonra kayıtlı
  yanıtlarla 3 kabul, 1 ret. Yeni inference yok. Genel üretim kabulü değil.
- Geçiş verisiyle (6 kayıt, 221 wiki) çevrimdışı rota: başkan sorusu `LOCAL`/`mayor-tech`,
  karışık tur `COMMENTARY`/kanıtsız, 6/6 deterministik sorgu, sohbet ve yorum `MODEL`.
- Üç dosyalı senaryolar sahte sunucu ağacı ve mock yaşam döngüsüyle sınandı;
  gerçek stop/kurulum/açılış ise tek canlı geçişle gözlendi (aşağıda). Oyun içi
  kabul veya TPS ölçümü yapılmadı.
- Küçük gerçek Gemma pilotu: 6 yerel rota, 4 tekil inference; aynı digest/üretim
  ayarları, CPU, 4096 bağlam, 160 çıktı tokenı. Retry veya A/B yok.
- Günlük/karakter/wiki/karışık çağrıları: 20,11 / 6,45 / 12,06 / 10,59 sn.
  İlk filtre bir güvenli karışık adayı gereksiz reddetti; dar düzeltme aynı
  kayıtlı yanıtla, **yeni inference olmadan** doğrulandı. Genel üretim kabulü değil.

## Açık sorunlar ve canlı durum

- Wiki yanıtında doğru tarihe eklenen belirsiz belediye eylemi ve Türkçe
  akıcılık sorunu var. Kendi makamı doğru olsa da serbest yorumda anlatıcının
  ayrı doğrulanmış kendilik kanıtı eksik. İğneleyici/gösterişli ton kalıcı doğrulanmadı.
- Karşılaştırma, tarih/yıl/gelecek, genel ilişkiler ve karmaşık karışık niyetler
  deterministik kapsamda değil. Serbest sohbetin tüm olguları doğrulanmış sayılmaz.
  Tek başına yorum sorusu ve varsayımlı cümleler ("sana bağlı olduğu için mi") Gemma'da kalır.
- Yorum filtresi sezgiseldir, doğruluk denetimi değildir. Cümle başı tek kelimelik yeni ad
  ve listede olmayan doğrulanmamış iddia geçebilir; gereksiz ret davet üretir.
- Model yüklüyken boş RAM 811–1763 MB'ye indi (oyuncusuz). Paper `-Xmx` 6656 MB, RSS
  4,6–4,9 GB; TPS 20,0 ama oyuncu/yük altı ölçüm yok. Modelin soğuk çağrısı 17–19 sn.
  Çok iyelikli serbest sohbette ("başkanımız çok iyi, adını unuttum") kaynak araması
  başkan kaydını yine bulamayabilir; kapsam dışı.
- Oyun içi test (1 Kasım): üç sorgu doğru, iki olgusal hata bulundu. İkisi de `CHAT`
  planına düşüyordu: başkan sorusunda kaynak `NO_RELEVANT_SOURCE`, karışık turda doğrulanmış
  kayıt 0. Uydurma ad wiki, NPC, knowledge ve memory dosyalarında yok.
- Sunucu 1 Kasım 23:48'de kullanıcı isteğiyle RCON stop ile kapatıldı; **2 Kasım 19:46'da
  kullanıcı tarafından yeniden açıldı** (Paper PID 528). 19:48 salt okunur denetimi: JAR `e5b8ddc6…`,
  `Ready=True`, marker `058c2efb…` aynı, yalnız eski iki başka-plugin hatası, 0 oyuncu.
  PID değişebilir; işlemden önce yeniden doğrula.
- **Kurulu sürüm:** `jar-plan-20261101-232407`; o açılışta Paper PID 3476 idi. Betik çıkışı 0,
  `transaction.json` fazı `READY`, Citizens farkı 0. `HYBRID_READY_V1 058c2efb…60a9` plandaki
  marker ile birebir; Paper, Citizens, NPCSpeak açıldı. Bağımsız denetimde 288 sıkı dosya,
  NPC YAML, sunucu ayarları ve BAT aynı; Citizens 313 bloğu stop öncesiyle aynı (52 satır).
- İlk geçişin fazı `RECOVERY_BLOCKED` kaldı: betik kilitli `latest.log` okuyamayıp durmuştu,
  geri alma olmamıştı ve son denetim elle yapılmıştı. Bu tarihsel kaydı başarı sayma.
- İlk geçiş sonrası korunanlar 22:03'te doğrulandı: 221 wiki, 60 diğer plugin JAR'ı, BAT, spigot.yml,
  bukkit.yml, paper.jar, NPCSpeak config/messages aynı. `server.properties` yalnız
  Minecraft'ın açılış damgasıyla farklı (önceki damgayla plan hash'i elde edildi).
  Citizens `saves.yml` kapanışta yazıldı; önceki kopyası alınmadığından yalnız 313
  kaydı, adı ve skin alanlarının varlığı doğrulandı. Oyun içinde teyit edilmeli.
- Loglarda 2 ERROR var (ReviveMe sürümü, world_nether autoload); son iki eski açılışta
  da aynı. Bu işten kaynaklanmıyor, dokunulmadı.
- Tüm mevcut planlar tüketildi veya `SUPERSEDED.txt` ile işaretli; yeniden kullanılamaz.
- Güncel kişi/makam bilgisi `municipal-records` içindedir; legacy municipality
  gölgelenir. NPC ve `characters` kişiliklerinde anlatıcının kendi makamını söyleyen
  ifade kaldı; kullanıcı kapsamı gereği değiştirilmedi. Tarihsel wiki ayrıca okunur.
- Sunucuda `spigot.yml` içinde `restart-on-crash: true` ve `restart-script: START.bat`
  **etkin**. Normal RCON stop'ta beklenmez; watchdog çökmesi veya `restart` komutunda
  devreye girebilir. Kullanıcı riski kabul etti, ayar değiştirilmedi. Başka otomatik
  yeniden başlatıcı bulunmadı.
- Gerçek geçişte Paper yeniden eşlemesi sorun çıkarmadı; eski JAR'ın stop kaydı NPC
  baytlarını değiştirmedi.
- **Oyun içi kabul tamamlanmadı.** Sunucu artık gizli pencerede çalışıyor; konsol görünmez.
  Sunucu kökünde boş `.npcspeak-deploy.lock` kaldı; işlem tutmuyor, zararsız.

Ayrıntılar: [mimari](../hybrid-answer-architecture.md),
[ilk sürüm](../hybrid-answer-results.md),
[gerçek pilot ve geçiş planı](../hybrid-gemma-pilot-results.md).
Özel kanıtlar Git dışında kalır; ortak notlara içerikleri taşınmaz.
