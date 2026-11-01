# Kalıcı teknik kararlar

Yalnız kalıcı kararlar tutulur; durum ve yarım iş STATE/HANDOFF'tadır.
Güncel kullanıcı talimatı bu notlardan önceliklidir; notlar onay belgesi değildir.

| Karar | Gerekçe / tekrar yapılmayacak hata |
| --- | --- |
| Anlatıcı kimliği ile bilgi öznesini ayır | B03'te doğru makam adları yanlış kişiye taşındı. Makam adının geçmesi doğruluk kanıtı değildir; özne, yön ve kanıt birlikte gerekir. |
| Kimlik bağı açık `knowledge-entity` alanından gelir | Görünen ad, Citizens ID, skin, kişilik veya `-`/`_` kişilik anahtarı eşleştirmesinden varlık/makam çıkarma. |
| Açık belediye olguları dar deterministik yolda, serbest sohbet Gemma'da | B03'e gereksiz model çağrısı gönderme; bütün sohbeti sabit cevaplara veya belirsiz mesajı zoraki makam eşleşmesine dönüştürme. |
| Makam ve ilişki farklı kanıtlardır | `reports_to/from/to` yönünü koru; makamdan koordinasyon, yetki, bağlılık veya geçişli ilişki üretme. Eski katılımcı sırasını yeni yönlü kayıt sayma. |
| Eksik kanıt bilinmeyendir | “Kaydım yok” ifadesini “görevi yok/kimseye bağlı değil” sonucuna çevirme; tarihsel wiki'yi güncel atama kanıtı sayma. |
| Dar karışık cevapta sabit olgu + incelenmiş sohbet ekleri | Adayı teslimden önce tamponla. Güvenli iki eki birlikte gereksiz reddetme; yalnız olguyu verip sohbeti sessizce düşürme. Keyfî paraphrase'i doğrulanmış sayma. |
| İkinci doğrulama modeli veya otomatik retry yok | CPU maliyeti ve ikinci modelin de yanılma riski var. Eski küçük pilot onayı sınırsız yeni inference izni değildir. |
| Son teslim güncel ziyaret ve ana thread ile bağlıdır | Yerel tamamlanmış future de aynı teslim kuyruğunu kullanır. Pending erken bırakılmaz; eski giriş/yanıt/preview/timeout yeni görüşmeye sızmaz. |
| Eski YAML ve çalışan sistem korunur | Yeni şema varsa geniş legacy municipality retrieval'da gölgelenir. Yetki/yetenek sınırlamalarını geçişte kaybetme; bozuk reload'da son iyi snapshot'ı koru. |
| Dağıtım üç dosyalı ve kapalı sunucuda doğrulanmış olmalıdır | JAR/NPC/knowledge birlikte hash ve yedek kapsamına girer. Hash drift'ini sessizce kabul etme, zorla süreç sonlandırma. |
| Stop sonrası hata dosya durumuna göre ayrılır | Dosya yazılmadıysa veya geri yazım doğrulandıysa özgün sunucu aynı BAT ile açılır; sunucu sebepsiz kapalı bırakılmaz. Kapanış doğrulanamadıysa hiçbir şey başlatılmaz. |
| Rollback yalnız onaylı dosyaları kapsar | Citizens, wiki, mesaj, oyuncu hafızası ve dünyayı topluca geri sarma. Güvenli kapanış/geri alma doğrulanamıyorsa riskli yazmaya devam etme. Makbuzsuz NPC değişikliği dış düzenleme sayılır ve ezilmez. |
| Plan kişilik metnini kendiliğinden değiştirmez | Eski kişilik cümlesi ayrı içerik kararıdır; değişecekse reçetede açık eski/yeni metinle yeni plan üretilir. |
| Eski plan silinmez, zorunlu olarak geçersiz kılınır | `SUPERSEDED.txt` taşıyan plan Preview/Deploy'da reddedilir. Canlı hash'ler aynı olduğundan yalnız not bırakmak eski planın kurulmasını engellemez. |
| Beklenmedik Paper süreci işlemi durdurur | Yazımdan, geri yazımdan ve her başlatmadan önce süreç denetlenir; görülen sürece stop gönderilmez, dosya yazılmaz. Açılmakta olan sunucu da bu nedenle kapatılmaz; geri alma için önce kullanıcı değerlendirir. |
| Canlı log paylaşımlı okunur | Log4j `latest.log`'u açık tutar; `ReadAllText` ilk canlı geçişte bu yüzden düştü. Sahte testler sunucunun açık tutamacını da taklit etmeli. |
| Güncel kişi ve makamlar `municipal-records` içinde yönetilir | Kişilik metinleri kayıtlara yönlendirir; başkan veya görevli adı kişiliğe yazılmaz. |
| JDK 26, ana hedef Java 17; gerçek test kanıtı | Yeni taslağı önceki 136/99 sonucuyla başarılı gösterme. Sahte Bukkit/sunucu testleri oyun içi kabul veya TPS ölçümü değildir. |
| Tek çalışma ağacında tek yazan ajan | Önce Git durumu, STATE ve HANDOFF okunur. Önceki ajanın yarım işini resetleme/ezme; doğrulanmış işi gerekmedikçe tekrarlama. |
| Mevcut dal ve PR ilişkisi korunur | PR #2, PR #1'in `dev/superseed-source` tabanındadır. Küçük düzeltmeler için yeni PR açma; master'a otomatik push/merge yapma. |
| Ortak hafıza kısa, doğrulanabilir ve gizli verisizdir | Ham sohbet/log, parola, tam özel wiki veya yerel gizli dosyaları notlara taşıma; ajan notlarını canlı müdahaleye otomatik onay sayma. |

Kararların bağlamı: [mimari tasarım](../hybrid-answer-architecture.md),
[ilk sürüm](../hybrid-answer-results.md),
[Gemma pilotu ve geçiş hazırlığı](../hybrid-gemma-pilot-results.md).
