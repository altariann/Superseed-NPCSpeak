# Ajan devri

- Son çalışan ajan: **Claude**. Son güncelleme: **1 Kasım 2026 22:35, Europe/Istanbul**.
- Dal: `dev/superseed-automation`; açık geliştirme PR'ı #2, kaynak tabanı PR #1.
- Doğrulanan uygulama commit'i: `94440c111772aca4fbbb0901b7ccf7fe32531055`.
  Belge commit'ini `git log -1 -- docs/ai/HANDOFF.md` ile, gerçek HEAD'i ayrıca doğrula.

## Son aşama: oyun içi hataların düzeltmesi

- Kullanıcının ilk oyun içi testinde üç sorgu doğruydu, iki olgusal hata bulundu.
- **Kanıtlı neden** (değişiklikten önce gerçek planlayıcıyla, geçiş verisi üzerinde):
  - `Haklısınız, başkanımızın adı nedir?` → `CHAT`, kaynak `NO_RELEVANT_SOURCE`, 0 kayıt.
  - `iladymoon kime bağlı? Sence Tech iyi bir belediye başkanı mı?` → `CHAT`, doğrulanmış
    kayıt 0. İlk cümlecik tek başına doğru `REPORTS_TO` yerel plana gidiyordu.
  - Uydurma ad wiki, NPC, `knowledge.yml` ve NPCSpeak diğer 4 dosyasında (memory dahil) yok.
  - **Çıkarım, kanıt değil:** ad modelin kaynaksız üretimi. Model yeniden çalıştırılmadı.
- `94440c1`: cümlecik planı, başkan dil bilgisi, `X Y'ye bağlı mı`, `CommentaryGuard`,
  yalnız yorum için `GroundedPrompt.commentary`, göstergenin model çağrısına bağlanması.
- İki eski test beklentisi bilerek değişti: iki olgulu `ve` turu ve `X Y'ye bağlı mı`
  artık modele gitmiyor. Tek başına yorum sorusu `MODEL` beklentisine eklendi.
- Testler: **154 Java (10 yeni), 245 PowerShell**, 0 hata. Geçiş verisiyle 6/6 sorgu.
- Regresyon testleri yalnız kurgusal veri kullanır; ekran görüntüsü/oyuncu verisi yok.

## Canlı durum

- **Düzeltme canlıda değil.** Canlı JAR `5bddbd93…de91a6`, depo JAR'ı `b7502123…c27a`.
  Paper PID 8244 çalışıyor; stop/restart, JAR/NPC/knowledge yazımı veya RCON yapılmadı.
- Yedek: sunucu kökünde `deployment-backups/hybrid-9a9142c7bfe342c28aba9112a9df0fe9`.
  İlk geçişin `transaction.json` fazı `RECOVERY_BLOCKED`; ayrıntı [STATE](STATE.md).

## Sonraki adım

1. Kullanıcı iki onayı ayrı verir; bunlar olmadan ikisini de yapma:
   - Küçük gerçek Gemma denemesi: yorum yolu ne sıklıkla geçerli `Bence…` üretiyor?
   - Yalnız JAR canlı dağıtımı.
2. **Araç boşluğu:** `DeploymentPlanPreparation` taşınmış kaynağı bilerek reddeder ve üç
   dosyalı plan NPC/knowledge değişimi bekler; bu dağıtım için kullanılamaz. Eski JAR-only
   `-Mode Deploy` yolu hibrit kanıt beklemez, makbuz denetimi ve üç dosya yedeği yoktur.
   Dağıtımdan önce uygun yol tasarlanmalı; `-WhatIf` dışında deneme yapma.
3. Canlı dağıtımdan sonra iki gerçek cümle, B03, Doğan'ın makamı/bağlılığı, Citizens 313,
   skin ve konum oyun içinde sınanır. Citizens için geçiş öncesi kopya yok.
4. Bu ajan oyun içi test sonucu üretmedi; "kabul edildi" yazma.

Özel plan, diff, yedek ve kanıtlar yalnız yerel özel alandadır. Gizli dosya
içeriklerini bu notlara/Git'e kopyalama.
