# B03 — kaynak incelemesi ve çevrimdışı düzeltme

1 Kasım 2026. Bu belge model tekrarı öncesindeki çevrimdışı çalışmayı kaydeder.
Sonrasında kullanıcı onayıyla yapılan **tek B03 tekrarı da başarısızdır**;
[karşılaştırma raporu](b03-repeat-results.md). B03 hatası açık kalır.
Aşama C ve canlı dağıtım başlamadı.
Ham yanıt, eski/yeni JSON kaynakları, prompt'lar ve manifestler yalnız Git'in
yok saydığı özel yerel değerlendirme alanında tutulur.

## Hatanın sınıflandırılması

Gerçek B03 yanıtının ilk cümlesindeki iki makam, eski `municipality-2`
kaydında iladymoon için açıkça doğrulanıyor. Bu cümle destekleniyor.
İkinci cümle, kaynakta farklı bir kişi için doğrulanmış bağlılığın öznesini/yönünü
doğru aktarmıyor; doğrulanmayan bir yönetim ilişkisi ekliyor. Soru öznesinin
örtük olarak kastedildiği varsayılsa bile onun için böyle bir bağlılık kaydı
yok. Ham cümle ve birebir kanıt karşılaştırması yalnız özel raporda tutuluyor.

Sonuç: **kaynak dışı ilişki; muhtemel özne/yön aktarımı hatası.** Salt belirsiz
bir zamirin doğru bir ilişkiyi aktardığı kabul edilemez. Kaynakta bulunmamak,
oyun dünyasında ilişkinin kesinlikle bulunmadığının kanıtı da değildir.
Modelin içsel hata nedeni kanıtlanmadı; denetlenen şey yanıttaki iddianın
kaynak tarafından desteklenmemesidir. Cümle bazında inceleme özel rapordadır;
bağımsız insan doğrulaması yapılmış sayılmaz.

Somut yazılım kusuru: eski belediye alanı 350 karakterlik parçalara ayrılıyor;
tek bir ortak sözcük bile parçanın tamamını seçtirebiliyor. B03 için Doğan'ın
görevi/yetenekleri, üç kişinin makamları ve Tech–Doğan ilişkisi birlikte
gönderilmişti. Bu gereksiz kapsam, modelin karıştırmasına olanak veriyor;
nedensel etkisi henüz bir model karşılaştırmasıyla ölçülmedi.

## Yeni kaynak paketi

| B03 | Eski | Yeni |
| --- | --- | --- |
| Doğrulanmış kayıt sayısı | 2 karma belediye parçası | 2 atomik makam kaydı |
| Kaynak JSON karakteri | 783 | 455 |
| Kapsanan kişiler | Tech, Doğan Tırpan, iladymoon | Yalnız iladymoon |
| Makamlar | Üç kişinin görevleri | Özel Kalem Müdürü ve Belediye Başkan Vekili |
| Tech–Doğan bağlılığı | Dahil | Hariç |
| Doğan'ın işlem/yetenek metni | Dahil | Hariç |
| Wiki | 0 | 0 |

Yeni JSON, `recordType`, `subjects` ve `offices` alanlarını da içerir.
Makam ve ilişki kayıtlarının farklı kanıtlar olduğu prompt kurallarında
açıklanır. Bu daraltma modelin artık yanlış yanıt veremeyeceğini kanıtlamaz.

`municipal-records` kullanıldığında eski `municipality` metni aramaya hiç
eklenmez; boş/bozuk yeni alan geniş eski metne sessizce dönmez. Yeni alan
yoksa eski municipality/verified-facts davranışı korunur. Geçiş otomatik
olarak canlı dosyalara yazılmaz; gerekli tüm eski yetenekler ayrıca taşınmalıdır.

Makam sorusu ilgili makam sahibini, kişi/makam sorusu o kişinin makamlarını,
ilişki sorusu yalnız doğrulanmış katılımcı/yön kaydını seçer. Eksik güncel
makam/bağlılık wiki üzerinden tamamlanmaz. Güncel kayıtlar ile açık tarihsel
sorular ayrı değerlendirilir; tarihsel etiket tek başına belirli bir yılın
kanıtı değildir. Belediye dışındaki doğrulanmış alias ve üretim kayıtları korunur.

## Kişilik eşleşmesi

NPCSpeak kimliği dosya adından geliyor; `dogan-tirpan` ile knowledge karakter
anahtarı `dogan_tirpan` uyuşmuyordu. Önce tam anahtar aranır; bulunamazsa yalnız
`-`/`_` farkı için tekil eşleşme kullanılır. Birden çok olası anahtar varsa
kişilik tahmin edilmez. Bu, varlık alias'ı veya Citizens ID dönüşümü değildir.
NPC/Citizens dosyası, sayısal ID, skin ve konum değiştirilmedi.

Yeni paket ek knowledge kişiliğini de doğru yüklüyor. Bu nedenle ilerideki
model karşılaştırmasında **kaynak yapısı, açıklayıcı kural ve ek kişilik
yüklenmesi birlikte değişmiş olacak**; tek başarılı tekrar kaynak daraltmanın
tek başına etkisini veya kalıcı başarıyı kanıtlamaz. Dondurulmuş soru ve
model/inference ayarları korunur; gerçek token sayısı yeni çağrıdan önce
ölçülmüş gibi sunulmaz.

## Çevrimdışı sonuçlar

JDK 26 temiz `build` başarılı: **89 Java testi**, **95 PowerShell denetimi**
(73 dağıtım, 11 snapshot, 11 pilot) geçti. Yeni 19 Java testi/denetimi; makam
seçimi, kişi karşılaştırması, ilişki yönü, bilinmeyen katılımcılar, tarihsel
ayrım, eski YAML, bozuk kayıt, alias, uzun başlık ve kişilik çakışmasını kapsar.

Gerçek 221 makaleyle, aynı dondurulmuş 750 sorgu/beklenti dosyası:

| Profil | Beklenti eşleşmesi | Açıklama |
| --- | ---: | --- |
| Canlı ayarların eski salt okunur kopyası | 743/750 | Önceki sonuç değişmedi |
| Eski tamamlanmış özel aday profili | 746/750 | Önceki sonuç değişmedi |
| Yeni atomik özel profil | 741/750 | Eski dört açık alıntı etiketi + beş eski cümle beklentisi |

Yeni profildeki beş fark `A723`, `A724`, `A725`, `C726`, `C727`:
doğru kişi/makam kayıtları seçiliyor; dondurulmuş denetim eski birleşik metnin
birebir cümlesini arıyor. Metin hem yeniden yazıldı hem bölündü; Doğan'ın
makam sorusunda makamda görüşme cümlesi artık özellikle gönderilmiyor.
Bu beş fark, yeni profilin kabulü için gözden geçirilecek etiket geçişleridir;
başarı sayısına eklenmedi ve beklentiler sonuçlara göre değiştirilmedi.
Önceki `D008`, `D017`, `D149`, `D200` alıntı beklentileri de açık kaldı.

Eski kanıtlar değişmeden doğrulanabilsin diye yalnız değerlendirme aracında
isteğe bağlı `loreEvidenceData` eklendi: kanıt eski profilde denetlenirken arama
yeni profilde çalışır; iki knowledge hash'i ve bu fark rapora yazılır. Varsayılan
koşuda kanıt kaybolmuşsa hata vermeye devam eder. Yanlış kaynağı geçirmek için
kanıt kontrolü kapatılmaz.

Üç profilin her birinde yükleme sırası karşılaştırması 750/750 aynı. JSON/bütçe
kontrolleri geçti. HUMAN_VERIFIED=0; bu sayılar **kesin doğruluk oranı değildir**.

İstenen yedi ek sorgu, gerçek arşiv ve yeni profille **7/7** beklenti eşleşmesi
verdi; beklentiler YAML'den önceden belirlendi, asistan kaynak incelemesi etiketli:

| Soru | Beklenen/seçilen kaynak |
| --- | --- |
| Superseed Belediye Başkanı kim? | Yalnız Tech'in makamı |
| Doğan Tırpan hangi görevde? | Yalnız Doğan'ın makamı |
| Doğan Tırpan kime bağlı? | Yalnız Doğan → Tech ilişkisi |
| iladymoon'un görevleri neler? | Yalnız iladymoon'un iki makamı |
| iladymoon kime bağlı? | Kaynak yok; ilişki tahmin edilmez |
| Doğan ile iladymoon aynı görevi mi yürütüyor? | İki kişinin ayrı makamları; bağlılık yok |
| Arşivde olmayan iki kişinin ilişkisi | Kaynak yok |

Türkçe niyet/ek desteği sınırlı ve deterministik kurallara dayanır; tam bir
dil çözümleyicisi değildir. Bu çevrimdışı testler modelin yanıt davranışını
ölçmez. Sonradan onaylanan tek model tekrarı ayrı rapordadır; tek başarılı
yanıt yeterli kanıt sayılmaz. Sunucu stop/başlatma, JAR ve gerçek ayar
değişikliği yapılmadı.
