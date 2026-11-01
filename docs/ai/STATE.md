# Proje durumu

Son doğrulama: **1 Kasım 2026 22:04, Europe/Istanbul**. Bu dosya güncel durum özetidir;
son çalışma ve yarım iş için [HANDOFF](HANDOFF.md), kurallar için [AGENTS](../../AGENTS.md).

## Git ve kabul durumu

- Dal: `dev/superseed-automation`. Doğrulanan son uygulama commit'i: `e2fb567`.
  Bu belgelerin commit'i bunun üzerine gelir; gerçek HEAD'i Git'ten oku.
- [PR #2](https://github.com/altariann/Superseed-NPCSpeak/pull/2) açık;
  head `dev/superseed-automation`, base `dev/superseed-source`.
- [PR #1](https://github.com/altariann/Superseed-NPCSpeak/pull/1) açık;
  head `dev/superseed-source`, base `master`. Kaynak tabanı korunur.
- `9b55873` mimari, `aeccff0`/`cd60e32` ilk uygulama,
  `2589220` oturum/filtre güvenliği, `0e115f5` pilot aracı ve sonuç raporu,
  `c08cbf3` üç dosyalı güvenli dağıtım hazırlığı, `e73ba31` geçersiz plan koruması,
  `82c3218` beklenmedik Paper koruması, `e2fb567` paylaşımlı log okuma.
- İlk kontrollü canlı geçiş kullanıcı onayıyla 1 Kasım 22:01'de uygulandı. Canlı
  sürüm `82c3218` betiğiyle kuruldu; `e2fb567` düzeltmesi sonraki dağıtımlar içindir.
  Oyun içi kabul bekliyor.

## Uygulanan mimari

- `SpeakerContext` anlatıcıyı, `QueryFrame` soru öznesini/niyetini ayırır;
  `AnswerPlan` yolu/kanıtı, `AnswerOutcome` yalnız gönderilecek son metni taşır.
- Dar ve açık güncel makam, makam sahibi ve yönlü bağlılık soruları freeform
  akışında yerel yanıtlanır. B03'ün iki makamı iladymoon'a aittir; model çağrısı 0.
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
- **144 Java testi, 0 başarısızlık/hata/atlama; 245 PowerShell denetimi**
  (75 dağıtım + 144 üç dosyalı dağıtım + 11 snapshot + 15 pilot). Temiz
  `Deploy-Superseed.ps1 -Mode Build` başarılı; kanıt zamanı 1 Kasım 22:03 İstanbul.
  Java sayısı XML raporlarından okundu. JAR: test sınıfı/wiki yok, major 61,
  SHA256 `5bddbd93…de91a6`.
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
- **Canlı sürüm:** `plan-20261101-214422` kurulu; Paper PID 8244 çalışıyor (22:03).
  Aday JAR ve knowledge hash'leri aynı, NPC hash'i açılış makbuzuyla aynı.
  `HYBRID_READY_V1` plandaki marker ile birebir; Paper, Citizens, NPCSpeak açıldı.
  NPC YAML'da yalnız onaylı kişilik değişimi ve `knowledge-entity` farkı var.
- **Betik sonucu başarılı değil:** `transaction.json` fazı `RECOVERY_BLOCKED`.
  Betik açılışı beklerken kilitli `latest.log` okuyamadı, kurtarma açılan süreci
  görüp stop/yazma yapmadan durdu. Geri alma olmadı; yeni sürüm açılmaya devam etti.
  Son denetim betik dışında elle, salt okunur yapıldı. Bu fazı başarı sayma.
- Korunanlar 22:03'te doğrulandı: 221 wiki, 60 diğer plugin JAR'ı, BAT, spigot.yml,
  bukkit.yml, paper.jar, NPCSpeak config/messages aynı. `server.properties` yalnız
  Minecraft'ın açılış damgasıyla farklı (önceki damgayla plan hash'i elde edildi).
  Citizens `saves.yml` kapanışta yazıldı; önceki kopyası alınmadığından yalnız 313
  kaydı, adı ve skin alanlarının varlığı doğrulandı. Oyun içinde teyit edilmeli.
- Loglarda 2 ERROR var (ReviveMe sürümü, world_nether autoload); son iki eski açılışta
  da aynı. Bu işten kaynaklanmıyor, dokunulmadı.
- Plan artık tüketildi; canlı hash'ler değiştiği için yeniden kullanılamaz.
  Önceki plan `SUPERSEDED.txt` ile işaretli.
- Güncel kişi/makam bilgisi `municipal-records` içindedir; legacy municipality
  gölgelenir. NPC ve `characters` kişiliklerinde anlatıcının kendi makamını söyleyen
  ifade kaldı; kullanıcı kapsamı gereği değiştirilmedi. Tarihsel wiki ayrıca okunur.
- Sunucuda `spigot.yml` içinde `restart-on-crash: true` ve `restart-script: START.bat`
  **etkin**. Normal RCON stop'ta beklenmez; watchdog çökmesi veya `restart` komutunda
  devreye girebilir. Kullanıcı riski kabul etti, ayar değiştirilmedi. Başka otomatik
  yeniden başlatıcı bulunmadı.
- Gerçek geçişte Paper yeniden eşlemesi sorun çıkarmadı; eski JAR'ın stop kaydı NPC
  baytlarını değiştirmedi.
- **Oyun içi kabul yapılmadı.** Sunucu artık gizli pencerede çalışıyor; konsol görünmez.
  Sunucu kökünde boş `.npcspeak-deploy.lock` kaldı; işlem tutmuyor, zararsız.

Ayrıntılar: [mimari](../hybrid-answer-architecture.md),
[ilk sürüm](../hybrid-answer-results.md),
[gerçek pilot ve geçiş planı](../hybrid-gemma-pilot-results.md).
Özel kanıtlar Git dışında kalır; ortak notlara içerikleri taşınmaz.
