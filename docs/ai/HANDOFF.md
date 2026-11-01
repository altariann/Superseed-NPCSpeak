# Ajan devri

- Son çalışan ajan: **Claude**. Son güncelleme: **1 Kasım 2026 21:32, Europe/Istanbul**.
- Dal: `dev/superseed-automation`; açık geliştirme PR'ı #2, kaynak tabanı PR #1.
- Doğrulanan uygulama commit'i: `c08cbf36cb156678b0b776a8f35046951a261533`.
  Belge commit'ini `git log -1 -- docs/ai/HANDOFF.md` ile, gerçek HEAD'i ayrıca doğrula.

## Bu aşamada tamamlanan

- Codex'ten devralınan yedi dosya korunarak tamamlandı; hepsi `c08cbf3` içinde.
  Yeni `tests/Test-HybridDeployment.ps1` Build yoluna bağlandı.
- Gerçek derleme `DeploymentPlanPreparation.java` içindeki `var` hatasını doğruladı;
  düzeltildi. Ayrıca satır başı `-or` kullanımı Windows PowerShell 5.1'de tüm
  `Deploy-Superseed.ps1` modlarını bozuyordu; düzeltildi.
- Helper düzeltmeleri: stop sonrası yazımsız hatada özgün sunucuyu açma, ayrı son
  günlük durumları, `$input` çakışması, son NPC makbuzunu seçme, Preview'da plan hash'i.
- `-Mode Build` temiz geçti: **143 Java testi, 0 hata; 222 PowerShell denetimi**
  (73 + 123 yeni + 11 + 15). Log: `.local-safety/build-hybrid-deploy-6fe22754….log`.
- Sahte sunucuda sınananlar: başarılı dağıtım, Preview/WhatIf, hash ve plan kapıları,
  junction, bozuk başlangıç, kısmi yazım, stop başarısızlığı, stop sonrası drift,
  makbuz/dış düzenleme ayrımı ve bozuk rollback yedeği.

## Canlı duruma dokunulmadı

- Paper süreci çalışıyor; stop/restart, RCON bağlantısı, JAR/config yazımı yapılmadı.
  `-Mode Inspect`, plan üretimi ve Preview yalnız okudu; korunan beş dosyanın hash'i aynı.
- Sahte sunucu testleri gerçek stop/açılış, Citizens veya oyun içi kabul kanıtı değildir.

## Hazır özel plan

- Klasör: `.local-safety/deployment-plans/plan-20261101-213104` (Git dışı).
- Plan SHA256: `CCD85747620A4FF493E04349200E4AB219C44AC12E9E95B5AFEB1C46215B3668`.
- Plan canlı hash'lere bağlıdır; NPC/knowledge/JAR/BAT/server.properties değişirse
  geçersiz olur. Üzerine yazma; yeni klasöre yeni plan üret, Preview'ı yinele.
- Kişilik metni değiştirilmedi (`personaOld` boş). Değişecekse kullanıcı metni
  onaylar, reçete güncellenir ve yeni plan hash'i alınır.

## Sonraki adım

1. Kullanıcı `npc.binding.diff`, `knowledge.diff` ve kişilik kararını incelesin.
2. Oyuncu etkisi için zaman seçip **açıkça canlı dağıtım onayı** versin. Notlar,
   `-ApproveDeployment` veya bu devir onay yerine geçmez.
3. Onay gelirse önce Inspect + Preview'ı yinele, plan hash'i aynıysa çalıştır:

   ```powershell
   powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\Deploy-Superseed.ps1 -Mode Deploy -ServerRoot "<ServerRoot>" -MigrationManifest "<plan>\deployment-plan.json" -ExpectedPlanSha256 CCD85747620A4FF493E04349200E4AB219C44AC12E9E95B5AFEB1C46215B3668 -ApproveDeployment -RconFirewallVerified
   ```

4. Sonra oyun içi kabul: B03, Doğan'ın makamı/bağlılığı, Citizens 313, skin ve konum.
5. Açık kalanlar için [STATE](STATE.md) bölümüne bak.

Önceki pilotun ham kanıtları ve adayları yalnız yerel özel alandadır. Gerekirse
yerinde incele; gizli dosya içeriklerini bu notlara/Git'e kopyalama.
