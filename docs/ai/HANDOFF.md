# Ajan devri

- Son çalışan ajan: **Claude**. Son güncelleme: **1 Kasım 2026 22:04, Europe/Istanbul**.
- Dal: `dev/superseed-automation`; açık geliştirme PR'ı #2, kaynak tabanı PR #1.
- Doğrulanan uygulama commit'i: `e2fb5676a1374fb76e906225cbcb3886d4071aeb`.
  Belge commit'ini `git log -1 -- docs/ai/HANDOFF.md` ile, gerçek HEAD'i ayrıca doğrula.

## İlk canlı geçişin sonucu

- Kullanıcının açık onayıyla 1 Kasım 22:01'de `plan-20261101-214422` uygulandı.
  Ön kontroller GO idi: 0 oyuncu, PID 15164, plan hash ve Inspect/Preview tamam.
- **Yeni sürüm canlı:** Paper PID 8244; `Done (46,111s)`, Citizens ve NPCSpeak açıldı,
  1 NPC yüklendi. `HYBRID_READY_V1` plandaki marker ile birebir aynı.
- **Dağıtım betiği başarılı bitmedi (çıkış 1):** açılış beklemesi `latest.log`'u kilit
  nedeniyle okuyamadı. Kurtarma açılan süreci görüp stop/yazma/başlatma yapmadan durdu;
  `transaction.json` fazı `RECOVERY_BLOCKED`. Geri alma uygulanmadı.
- Betiğin yapamadığı son denetim elle ve salt okunur yapıldı. Sonuçlar [STATE](STATE.md).
- Kök neden `e2fb567` ile düzeltildi, regresyon testi eklendi. Bu düzeltme canlı
  JAR'ı değiştirmez; yalnız sonraki dağıtım betiğini etkiler.
- Testler: **144 Java, 245 PowerShell**, 0 hata.

## Yedek ve geri dönüş

- Yedek: sunucu kökünde `deployment-backups/hybrid-9a9142c7bfe342c28aba9112a9df0fe9`.
  `before/` ve `final/` altındaki üç dosya özgün hash'lerle doğrulandı.
- Otomatik geri dönüş artık yok; plan tüketildi. Sorun çıkarsa kullanıcıya danış: geri
  dönüş yeni canlı müdahaledir, ayrı onay ister. Onaylanırsa güvenli stop, tam kapanış,
  yalnız bu üç dosyanın geri yazımı ve aynı BAT ile açılış gerekir.
- Geri dönüşte `knowledge-entity` ve yeni kişilik kalkar. Oyuncu verileri geri sarılmaz.

## Sonraki adım

1. Kullanıcıyla oyun içi kabul: B03, Doğan'ın makamı/bağlılığı, Tech'e bağlılık,
   Citizens 313, skin, konum. Citizens için önceki kopya olmadığından oyun içi teyit şart.
2. Bu ajan oyun içi test sonucu üretmedi; "kabul edildi" yazma.
3. Yeni canlı stop/JAR/config işlemi için yeni açık onay gerekir. Yeni plan gerekirse
   `.npcspeak-deploy.lock` boş dosyası sunucu kökünde durur; işlem tutmuyor.
4. Açık kalanlar için [STATE](STATE.md) bölümüne bak.

Özel plan, diff, yedek ve kanıtlar yalnız yerel özel alandadır. Gizli dosya
içeriklerini bu notlara/Git'e kopyalama.
