# Proje durumu

Son doğrulama: **1 Kasım 2026, Europe/Istanbul**. Bu dosya güncel durum özetidir;
son çalışma ve yarım iş için [HANDOFF](HANDOFF.md), kurallar için [AGENTS](../../AGENTS.md).

## Git ve kabul durumu

- Dal: `dev/superseed-automation`. Doğrulanan son uygulama commit'i: `0e115f5`.
  Ortak hafıza belgelerinin commit'i bunun üzerine gelir; gerçek HEAD'i Git'ten oku.
- [PR #2](https://github.com/altariann/Superseed-NPCSpeak/pull/2) açık;
  head `dev/superseed-automation`, base `dev/superseed-source`.
- [PR #1](https://github.com/altariann/Superseed-NPCSpeak/pull/1) açık;
  head `dev/superseed-source`, base `master`. Kaynak tabanı korunur.
- `9b55873` mimari, `aeccff0`/`cd60e32` ilk uygulama,
  `2589220` oturum/filtre güvenliği, `0e115f5` pilot aracı ve sonuç raporudur.
- **Güvenli üç dosyalı dağıtım hazırlığı yarım ve commit edilmemiştir.**
  Mevcut çalışma ağacı, doğrulanmış uygulama commit'inden farklıdır.

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
- **136 Java testi, 0 başarısızlık/hata; 99 PowerShell denetimi**
  (73 dağıtım + 11 snapshot + 15 pilot). XML raporları ve son başarılı Build logu
  bu belge turunda salt okunur doğrulandı; kanıt zamanı 1 Kasım 20:43 İstanbul.
- Bu sayılar **yeni dağıtım taslağını kapsamaz**. Bu belge turunda derleme/test
  yeniden çalıştırılmadı ve JAR üretilmedi.
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
- Üç dosyalı dağıtım: JAR + NPC YAML + knowledge.yml birlikte hash/yedek/geri alma
  kapsamına alınacak; ayrıntılı eksikler HANDOFF'tadır. Yeni Preview yolu hazır sayılmaz.
- **Canlı dağıtım ve oyun içi kabul yapılmadı; bunlara kullanıcı onayı yok.**
  Önceki pilotta sunucu süreci saptanmadı; bu, şimdiki süreç durumu iddiası değildir.
- Önceki son kontrolde korunan 9 dosya hash'i değişmedi; canlı/özel 221 wiki
  dosyası arşivle eşleşti. Bu belge turunda canlı dosyalar yeniden taranmadı.

Ayrıntılar: [mimari](../hybrid-answer-architecture.md),
[ilk sürüm](../hybrid-answer-results.md),
[gerçek pilot ve geçiş planı](../hybrid-gemma-pilot-results.md).
Özel kanıtlar Git dışında kalır; ortak notlara içerikleri taşınmaz.
