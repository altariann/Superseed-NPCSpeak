# Ajan devri

- Son çalışan ajan: **Claude**. Son güncelleme: **1 Kasım 2026 23:27, Europe/Istanbul**.
- Dal: `dev/superseed-automation`; açık geliştirme PR'ı #2, kaynak tabanı PR #1.
- Doğrulanan uygulama commit'i: `352cbcb88c33c324054fa7b45360c5e1e1b8684e`.
  Belge commit'ini `git log -1 -- docs/ai/HANDOFF.md` ile, gerçek HEAD'i ayrıca doğrula.

## Son aşama

- **Gemma pilotu:** 4 tekil istek ([sonuçlar](../commentary-pilot-results.md)), retry yok.
  Bir gereksiz ret ve bir kural boşluğu `352cbcb` ile düzeltildi; kayıtlı yanıtlarla sınandı.
  Pilot kararları ve yeni filtre sonuçları ayrı raporlanır; yeni inference yapma.
- **Yalnız-JAR yolu:** `scripts/Jar-Deployment.ps1`, `JarPreview`/`JarDeploy`, `prepareJarUpdate`.
- **Gerçek log denemesi** iki gizli hatayı buldu: normal açılıştaki `All dimensions are saved`
  kapanış sayılıyordu; kimlik denetimi RCON dinlemesiyle yarışabilirdi. İkisi giderildi.
- Testler: **162 Java, 298 PowerShell**, 0 hata. Log: `.local-safety/build-jar-guard-26a89cd0….log`.
- Sahte sunucu testleri gerçek stop/açılış veya oyun içi kabul kanıtı değildir.

## Canlı durum

- **Düzeltmeler canlıda değil.** Canlı JAR `5bddbd93…de91a6`, kurulacak JAR `e5b8ddc6…0e7e`.
  Paper PID 8244 çalışıyor; stop/restart, JAR/config yazımı yapılmadı.
  RCON yalnız okuma için kullanıldı (`list`, `tps`). Oyuncu varken inference gönderilmedi.
- Önceki yedek: sunucu kökünde `deployment-backups/hybrid-9a9142c7bfe342c28aba9112a9df0fe9`.
  İlk geçişin fazı `RECOVERY_BLOCKED`; ayrıntı [STATE](STATE.md).

## Hazır özel plan

- **Geçerli:** `.local-safety/deployment-plans/jar-plan-20261101-232407` (Git dışı), kaynak `352cbcb`.
- Plan SHA256: `4BF7E5144B4BAE33AE0FF9466F213985F9E0F4E7293B8E07A4C519329981F336`.
- Marker `HYBRID_READY_V1 058c2efb…60a9`; 8 yerel kontrol; korunan 288 sıkı dosya + 1 NPC.
- **Kullanılmaz:** `plan-20261101-213104` ve `plan-20261101-214422` (`SUPERSEDED.txt`).
- Plan canlı hash'lere bağlıdır; değişiklikte reddedilir. Tek kullanımlıktır, üzerine yazma.

## Sonraki adım

1. Kullanıcı canlı güncelleme için **açık onay** versin. Notlar ve `-ApproveDeployment` onay değildir.
2. Onay gelirse önce oyuncu sayısı, Inspect ve JarPreview yinelenir, hash aynıysa çalıştır:

   ```powershell
   powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\Deploy-Superseed.ps1 -Mode JarDeploy -ServerRoot "<ServerRoot>" -JarPlan "<jar-plan>\deployment-plan.json" -ExpectedPlanSha256 4BF7E5144B4BAE33AE0FF9466F213985F9E0F4E7293B8E07A4C519329981F336 -ApproveDeployment -RconFirewallVerified
   ```

   İşlem 10 dakikayı aşabilir: araç zaman aşımı kesmesin diye arka planda bağımsız çalıştır.
3. Sonra oyun içi kabul: iki gerçek cümle, B03, Doğan'ın makamı/bağlılığı, Citizens 313,
   skin ve konum. Bu ajan oyun içi sonuç üretmedi; "kabul edildi" yazma.
4. Açık kalanlar için [STATE](STATE.md) bölümüne bak.

Özel plan, diff, yedek ve kanıtlar yalnız yerel özel alandadır. Gizli dosya
içeriklerini bu notlara/Git'e kopyalama.
