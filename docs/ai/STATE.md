# Proje durumu

Son doğrulama: **1 Kasım 2026 21:32, Europe/Istanbul**. Bu dosya güncel durum özetidir;
son çalışma ve yarım iş için [HANDOFF](HANDOFF.md), kurallar için [AGENTS](../../AGENTS.md).

## Git ve kabul durumu

- Dal: `dev/superseed-automation`. Doğrulanan son uygulama commit'i: `c08cbf3`.
  Bu belgelerin commit'i bunun üzerine gelir; gerçek HEAD'i Git'ten oku.
- [PR #2](https://github.com/altariann/Superseed-NPCSpeak/pull/2) açık;
  head `dev/superseed-automation`, base `dev/superseed-source`.
- [PR #1](https://github.com/altariann/Superseed-NPCSpeak/pull/1) açık;
  head `dev/superseed-source`, base `master`. Kaynak tabanı korunur.
- `9b55873` mimari, `aeccff0`/`cd60e32` ilk uygulama,
  `2589220` oturum/filtre güvenliği, `0e115f5` pilot aracı ve sonuç raporu,
  `c08cbf3` üç dosyalı güvenli dağıtım hazırlığıdır.
- Devralınan yedi taslak dosya tamamlanıp commit'lendi. Çevrimdışı hazırlık bitti;
  canlı geçiş ayrı kullanıcı onayı bekliyor.

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
- **143 Java testi, 0 başarısızlık/hata/atlama; 222 PowerShell denetimi**
  (73 dağıtım + 123 üç dosyalı dağıtım + 11 snapshot + 15 pilot). Temiz
  `Deploy-Superseed.ps1 -Mode Build` başarılı; kanıt zamanı 1 Kasım 21:30 İstanbul.
  Java sayısı XML raporlarından okundu. JAR: test sınıfı/wiki yok, major 61,
  SHA256 `5bddbd93…de91a6`.
- Üç dosyalı senaryolar sahte sunucu ağacı ve mock yaşam döngüsüyle sınandı.
  Gerçek RCON/stop/başlatma, oyun içi kabul veya TPS ölçümü değildir.
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
- Gerçek dosyalar yalnız okunarak özel plan üretildi; 6 yerel kontrol ve Preview
  geçti. Kişilikteki eski "başkan adı verilmedi" ifadesi planda **değiştirilmedi**;
  ayrı içerik kararıdır. Legacy municipality alanı yeni kayıtlarla gölgelenir.
- Paper yeniden eşleme yaparsa açılış JAR hash'i eşleşmez. Mevcut `.paper-remapped`
  listesinde NPCSpeak görünmüyor, ama bu oyun içinde kanıtlanmadı; eşleşmezse
  dağıtım zaman aşımıyla geri alınır.
- Eski JAR'ın stop kaydı NPC baytlarını değiştirirse işlem yazmadan durur ve özgün
  sunucu açılır; bu davranış gerçek sunucuda gözlenmedi.
- **Canlı dağıtım ve oyun içi kabul yapılmadı; bunlara kullanıcı onayı yok.**
  1 Kasım 21:31'de Paper süreci **çalışıyordu**; durdurulmadı. `-Mode Inspect`
  süreç/BAT eşleşmesini doğruladı. RCON bağlantısı yapılmadı.
- Plan öncesi/sonrası korunan beş dosyanın (BAT, server.properties, JAR, NPC,
  knowledge) hash'i aynı kaldı; sunucuda yedek oluşmadı. Wiki yeniden taranmadı.

Ayrıntılar: [mimari](../hybrid-answer-architecture.md),
[ilk sürüm](../hybrid-answer-results.md),
[gerçek pilot ve geçiş planı](../hybrid-gemma-pilot-results.md).
Özel kanıtlar Git dışında kalır; ortak notlara içerikleri taşınmaz.
