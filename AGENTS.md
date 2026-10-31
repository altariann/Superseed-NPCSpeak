# Superseed NPCSpeak çalışma kuralları

## Geliştirme ve GitHub

- Açıklamaları Türkçe yap.
- Mevcut geliştirmeleri mümkün olduğunca aynı development dalında sürdür.
  Şu anki geliştirme dalı `dev/superseed-automation`, açık geliştirme PR'ı #2'dir.
- Anlamlı değişiklikleri ayrı commit'lerle kaydet; küçük düzeltmeler için yeni PR açma.
- Yeni PR açmadan önce açık PR'ları kontrol et. Aynı çalışmaya ait PR varsa onu güncelle.
- Yeni PR yalnız büyük bir aşama tamamlandığında ve kullanıcı açıkça onay verdiğinde açılabilir.
- Mevcut PR veya dalları kullanıcı izni olmadan silme ya da kapatma.
- Master'a otomatik merge/push yapma. Mevcut commit edilmemiş değişiklikleri silme,
  sıfırlama veya üzerlerine yazma.
- Kaynak tabanı `dev/superseed-source` ve PR #1 korunur. #2 bu tabanın üzerine kuruludur.

## Derleme ve sunucu

- Java değişikliklerinden sonra Gradle derlemesini ve gerçek Java testlerini çalıştır.
  Desteklenen toolchain JDK 26, ana kaynakların Java hedefi 17'dir.
- Sunucuya dokunmayan tek komut: `Deploy-Superseed.ps1 -Mode Build`.
  Gerekirse kurulu JDK 26 yolunu `-JavaHome` ile seç.
- Canlı stop, JAR değiştirme ve yeniden başlatma için ayrıca kullanıcı onayı gerekir.
  Deploy onay bayrağı tek başına kullanıcı onayı yerine geçmez.
- Zorla işlem sonlandırma yapma. Mevcut BAT, Java/JVM parametreleri, Citizens
  ID/skin/konumları, NPC verileri, bilgi/mesaj dosyaları, wiki ve oyuncu verilerini koru.
- RCON yalnız `127.0.0.1` üzerinden kullanılmalı. Operatör dış erişimin kapalı
  olduğunu doğrulamıştır; geniş arayüzde dinlemeyi tek başına güvenlik açığı sayma.
  Ağ/güvenlik duvarı/port yönlendirmesini gereksiz yere değiştirme.
- Gerçek sunucu yapılandırması, RCON/API parolaları, oyuncu verileri, chat logları
  ve özel/full wiki arşivini Git'e veya dağıtılan JAR'a ekleme.
