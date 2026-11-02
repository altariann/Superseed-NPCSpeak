# Aşama B — yerel Gemma pilotu

1 Kasım 2026. **Pilot üçüncü yanıtta durduruldu; geçmedi.** Aşama C'ye,
canlı dağıtıma veya otomatik tekrar koşusuna geçilmedi.

Sonraki çevrimdışı inceleme ve atomik kayıt düzeltmesi
[B03 raporundadır](b03-offline-review.md). Sonradan onaylanan
[tek B03 tekrarı da başarısızdır](b03-repeat-results.md); diğer sorular çalıştırılmadı.

## Deney koşulları

- Aşama A'nın yalnız özel alanda güncel kayıtlarla tamamlanan profili kullanıldı;
  canlı `knowledge.yml` güncellenmedi. 221 wiki dosyası aynı özel kopyadan okundu.
- Gerçek yerel Gemma 3 4B türevi modelin digest/sürüm/parametreleri özel manifeste
  alındı. CPU, 4096 bağlam, 160 çıktı token sınırı ve mevcut model varsayılanları
  korundu. Ham temel modele geçilmedi; Modelfile veya global ayar değiştirilmedi.
- Gerçek NPC YAML'ındaki yalnız üslup/görev alanları salt okunur okundu;
  NPC entity'si, Citizens verileri, oyuncu verileri ve sohbet geçmişi yüklenmedi.
  Ek knowledge kişilik anahtarı gerçek NPC kimliğiyle eşleşmedi; bu durum
  manifestte kaydedildi. NPC YAML üslubu kullanıldı; ID değiştirilmedi.
- İstekler gerçek `NpcConfig.buildSystemPrompt`, `GroundedPrompt.messages` ve
  backend ile ortak `OllamaBackend.encodeRequest` yolundan üretildi. PAPI
  genişletmesi/oyuncu hafızası kullanılmadı; çözümlenmemiş placeholder yoktu.
- Yalnız `model`, `stream=false`, `messages` gönderildi. Temperature, seed,
  num_ctx, keep_alive veya başka inference seçeneği gövdeye eklenmedi.
- HTTP yalnız `127.0.0.1` üzerinde; inference proxy/redirect kullanmaz. Her
  çağrıdan önce model digest'i ve boş fiziksel bellek kontrol edildi. Timeout
  mevcut backend ile aynı 120 saniye; otomatik retry yok.

Sorular ve beklenen olgular ilk inference'tan önce donduruldu. Tam prompt,
kaynak, yanıt, model metadata'sı ve süreç/bellek ölçümleri Git dışındaki özel
alanda tutulur. Bu belgede ham wiki veya model yanıtı yayımlanmamıştır.

## Hazırlıkta bulunan arama sorunu

Karşılaştırma sorusunda bir varlığın eski/yeni ad grubuna ait makaleler tüm
alıntı slotlarını doldurarak açıkça sorulan ikinci varlığı dışarıda bırakıyordu.
Bu soru modele gönderilmeden önce kaynak paylaşımı düzeltildi: açıkça sorulan
farklı varlık gruplarına, bütçe elverdiğince önce birer pasaj ayrılır; sonraki
slotlar puana göre doldurulur. Alias'lar ayrı varlık diye iki kez sayılmaz.
Tanıda `ENTITY_COVERAGE_FIRST` görünür; orijinal puan sırası korunur.

Sentetik regresyon eklendi. Aynı özel profille Aşama A'nın 750 vaka dosyası
yeniden çalıştırıldı: 746/750 kaldı; HUMAN_VERIFIED=0 ve önceki dört onaysız
alıntı beklentisi hâlâ açık. Kaynak kapsamı düzeltmesinden önce yapılan ilk
model isteğinin byte/hash değeri yeni bundle ile aynıydı; yanıt değiştirilmeden
yeniden kullanıldı. Bu soru için ikinci inference yapılmadı.

## Üç gerçek çağrının sonucu

| Vaka | Konu | Süre | Girdi/çıktı token | İnceleme |
| --- | --- | ---: | ---: | --- |
| B01 | Güncel belediye başkanı | 16,46 sn | 959 / 7 | Kaynakla uyumlu |
| B02 | Belediye başkan yardımcısının görevi | 10,28 sn | 964 / 35 | Kaynakla uyumlu |
| B03 | Özel Kalem / Başkan Vekili görevleri | 8,28 sn | 964 / 24 | Görevler doğru; ilave ilişki kaynak dışı/öznesi belirsiz |

**3/10 soru çalıştırıldı: 2 uygun, 1 başarısız, 7 çalıştırılmadı.** Bu küçük
örnekten genel başarı oranı çıkarılmaz. İncelemeyi asistan yaptı; bağımsız
insan doğrulaması değildir. Model kendi yanıtına not vermedi.

Üç çağrı da HTTP 200 ve `done_reason=stop` ile tamamlandı; timeout/çıktı
kesilmesi görülmedi. Girdi token sayıları 4096 sınırının oldukça altındaydı;
bu vakadaki hatayı bağlam taşması diye açıklayan bir bulgu yok. İlk çağrının
6,38 saniyesi soğuk model yüklemesiydi. Çağrılar sonrasında boş fiziksel bellek
sırasıyla yaklaşık 1873, 1796 ve 1802 MB idi. Sunucu PID'i izlendi ve korunuyordu;
canlı TPS veya oyuncuların AI yanıt gecikmesi doğrudan ölçülmedi.

## Hata değerlendirmesi ve sonraki kontrol

Üçüncü yanıtın ilk cümlesi iki görevi doğru verdi. Ardından kaynakların
doğrulamadığı veya öznesi yanlış/belirsiz okunabilen bir yönetim ilişkisi
ekledi. Olası neden, aynı belediye metin bloğunda birden fazla kişi ve bir
hiyerarşi cümlesinin bulunmasıdır. Bu yalnız bir hipotezdir; ayırıcı A/B
koşusu yapılmadı. Kısa/akıcı yanıt vermesi, kaynaklara bağlılığın kanıtı sayılmadı.

Planın hata halinde durma kuralı uygulandı: aynı soru tekrar sorulmadı,
başarısız yanıt daha iyi bir yanıtla değiştirilmedi ve B04–B10 gönderilmedi.
Eski hafıza/kişilik çatışması vakası da henüz çalışmadı.

Önerilen sonraki kontrollü değişiklik: kişi başına açık doğrulanmış kayıtlar ve
sorulan kişi/ilişkiyle sınırlandırılmış olgusal kaynak seçimi. Ardından aynı
dondurulmuş soru, aynı model/digest/parametrelerle yeniden değerlendirilmeli.
Yalnız prompt'u sertleştirmek veya rastgeleliği düşürmek hatanın çözüldüğünü
kanıtlamaz. Bu düzeltmenin model üzerindeki etkisi henüz test edilmedi.

Normal derleme ağ/model bağımsız kalır. Java/PowerShell araçları test edildi;
pilot çağrıları yalnız açıkça `Invoke-LorePilot.ps1` çağrısıyla yapılır.
Minecraft stop/başlatma, JAR veya gerçek yapılandırma değişimi yapılmadı.
