# Ajan devri

- Son çalışan ajan: **Claude**. Son güncelleme: **1 Kasım 2026 21:44, Europe/Istanbul**.
- Dal: `dev/superseed-automation`; açık geliştirme PR'ı #2, kaynak tabanı PR #1.
- Doğrulanan uygulama commit'i: `e73ba311a978a88aa282a8da9ea03fb3a0661ae9`.
  Belge commit'ini `git log -1 -- docs/ai/HANDOFF.md` ile, gerçek HEAD'i ayrıca doğrula.

## Bu aşamada tamamlanan

- Üç dosyalı dağıtım hazırlığı `c08cbf3` ile tamamlandı. `e73ba31`, `SUPERSEDED.txt`
  taşıyan planı reddeder ve çok satırlı kişilik yaması testini ekler.
- Kullanıcının verdiği kişilik metni, eski "başkan adı verilmedi" ifadesinin yerine
  **yalnız özel adayda** kondu. Canlı NPC değiştirilmedi.
- `-Mode Build` temiz geçti: **144 Java testi, 0 hata; 224 PowerShell denetimi**
  (73 + 125 + 11 + 15). Log: `.local-safety/build-hybrid-persona-358a33b1….log`.
- NPC adayı `özgün + tek kişilik değişimi + knowledge-entity` ile bayt düzeyinde
  eşleşti. `citizens-id`, konum ve diğer alan satırları aynı; knowledge adayı özgün
  dosyanın tüm içeriğiyle başlıyor. Skin bu YAML'da değil, plan kapsamı dışında.
- Altı deterministik belediye sorgusu ve üç diff yeniden doğrulandı.
- Sahte sunucu testleri gerçek stop/açılış, Citizens veya oyun içi kabul kanıtı değildir.

## Canlı duruma dokunulmadı

- Paper süreci çalışıyor; stop/restart, RCON bağlantısı, JAR/config yazımı yapılmadı.
  `-Mode Inspect`, plan üretimi ve Preview yalnız okudu; korunan beş dosyanın hash'i
  aynı, sunucuda yedek veya kilit dosyası oluşmadı.

## Otomatik yeniden başlatma denetimi

- **Bulundu:** `spigot.yml` → `restart-on-crash: true`, `restart-script: START.bat`.
  Normal RCON stop'ta beklenmez; watchdog çökmesi veya `restart` komutunda devreye
  girebilir. Başarısız açılış geri alınırken ek süreç açılması yarış yaratabilir.
  Bu davranış gerçek sunucuda sınanmadı. Ayar açılışta okunduğundan etkili değişiklik
  stop gerektirir; canlı dosya değiştirilmedi.
- **Yok:** Windows zamanlanmış görev/servis, başlangıç kaydı, BAT döngüsü, sarmalayıcı
  süreç. Java, Explorer → `cmd /c START.bat` ile elle açılmış. 1.941 plugin
  yapılandırması ve etkin 24 Skript'te zamanlı restart/stop kalıbı bulunmadı;
  bu desen taramasıdır, GUI veya JAR içi zamanlama kanıtı değildir.
- `start.cmd` AutoPlug çağırıyor, ama `AutoPlug-Client.jar` kökte yok ve çalışmıyor.
- Dağıtım başlatıcısı sunucuyu gizli pencerede açar; konsol penceresi görünmez.

## Hazır özel plan

- **Geçerli:** `.local-safety/deployment-plans/plan-20261101-214422` (Git dışı).
- Plan SHA256: `ADE54FFACF96D3BCCEAE17D97644FB0E2442C876C44DABB5616613710D9307B2`.
- **Kullanılmaz:** `plan-20261101-213104`; korundu, `SUPERSEDED.txt` ile reddediliyor.
- Plan canlı hash'lere bağlıdır; NPC/knowledge/JAR/BAT/server.properties değişirse
  geçersiz olur. Üzerine yazma; yeni klasöre yeni plan üret, Preview'ı yinele.

## Sonraki adım

1. Kullanıcı `restart-on-crash` riskine karar versin, oyuncu etkisi için zaman seçsin
   ve **açıkça canlı dağıtım onayı** versin. Notlar veya `-ApproveDeployment` onay değildir.
2. Onay gelirse önce Inspect + Preview'ı yinele, plan hash'i aynıysa çalıştır:

   ```powershell
   powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\Deploy-Superseed.ps1 -Mode Deploy -ServerRoot "<ServerRoot>" -MigrationManifest "<plan>\deployment-plan.json" -ExpectedPlanSha256 ADE54FFACF96D3BCCEAE17D97644FB0E2442C876C44DABB5616613710D9307B2 -ApproveDeployment -RconFirewallVerified
   ```

3. Sonra oyun içi kabul: B03, Doğan'ın makamı/bağlılığı, Citizens 313, skin ve konum.
4. Açık kalanlar için [STATE](STATE.md) bölümüne bak.

Önceki pilotun ham kanıtları ve adayları yalnız yerel özel alandadır. Gerekirse
yerinde incele; gizli dosya içeriklerini bu notlara/Git'e kopyalama.
