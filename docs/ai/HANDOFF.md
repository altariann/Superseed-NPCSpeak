# Ajan devri

- Son çalışan ajan: **Codex**. Son güncelleme: **1 Kasım 2026, Europe/Istanbul**.
- Dal: `dev/superseed-automation`; açık geliştirme PR'ı #2, kaynak tabanı PR #1.
- Doğrulanan uygulama tabanı: `0e115f5692fb064ebe66a5a8b99d74ab6a15484b`.
  Bu belge aşaması bunun üzerine yalnız dokümantasyon commit'i ekler.
  Belge commit'ini `git log -1 -- docs/ai/HANDOFF.md` ile, gerçek HEAD'i ayrıca doğrula.

## Bu aşamada tamamlanan

- Codex/Claude ortak STATE, HANDOFF, DECISIONS ve kısa CLAUDE başlangıcı kuruldu;
  mevcut AGENTS kuralları korunarak ortak hafızaya yönlendirme eklendi.
- Git durumu, PR #1/#2 ilişkisi, 136 başarılı Java testi ve 99 PowerShell
  denetiminin eski başarılı kanıtları okundu. Yeni taslağın test edilmediği ayrıldı.
- Bu aşama yalnız belgelerdir: uygulama/taslak dosyaları, gerçek sunucu,
  yapılandırma ve JAR değiştirilmez; yeni inference veya Build çalıştırılmaz.

## Devralınacak yarım iş

Önceki görev: ilk canlı testten önce JAR ile iki yapılandırmanın birlikte güvenli
dağıtımını hazırlamak. **Yalnız geliştirme/çevrimdışı hazırlık onaylıdır; canlı
stop, JAR/config değiştirme ve yeniden başlatma onaylı değildir.**

Çalışma ağacında korunması gereken **commit edilmemiş** dosyalar:

| Dosya | Taslak amacı |
| --- | --- |
| `Deploy-Superseed.ps1` | MigrationManifest/ExpectedPlanSha256, Preview ve yeni açılış kanıtı bağlantısı |
| `build.gradle.kts` | Opt-in `prepareHybridDeployment` görevi |
| `src/main/java/com/l299l/npcspeak/npc/NpcManager.java` | Yüklenen kimlik/şema için içeriksiz açılış kanıtı |
| `scripts/Hybrid-Deployment.ps1` | Üç dosyalı hash, kilit, yedek, stop, uygulama ve rollback taslağı |
| `src/main/java/com/l299l/npcspeak/knowledge/HybridReadiness.java` | Artifact/knowledge/kimlik/yön fingerprint'i |
| `src/test/java/com/l299l/npcspeak/knowledge/DeploymentPlanPreparation.java` | Özel aday/diff/manifest hazırlama; üretim sınıflarıyla offline kontrol |
| `src/test/java/com/l299l/npcspeak/knowledge/DeploymentPlanPreparationTest.java` | Henüz çalıştırılmamış yeni hazırlama testleri |

Bu dosyalar bu makinenin çalışma ağacındadır; başka klonda/GitHub'da var olduklarını
varsayma. Silme, resetleme, otomatik yeniden üretip üzerine yazma veya belge commit'ine
katma. Başka ajan hâlâ yazıyorsa önce devri koordine et; eşzamanlı düzenleme yapma.

## Önemli bulgular ve eksikler

- Taslak **henüz derlenmedi/test edilmedi**; son 136/99 kanıtı eski tamamlanmış
  uygulamaya aittir. Test sayısını yeni dosyadaki testleri sayarak artırma.
- Statik okumada `DeploymentPlanPreparation.java` içinde `var originalNpc = …,
  originalKnowledge = …;` birleşik bildirimi görüldü. Java `var` ile bu biçim
  kullanılamaz; sonraki kod aşamasında düzelt ve gerçek derlemeyle doğrula.
- Yeni helper için başarılı dağıtım, bozuk başlangıç, hash farkı, kısmi yazım,
  güvenli kapanamama ve rollback bozulması PowerShell senaryoları henüz eklenmedi.
- NPC normal kapanış/açılışta yeniden kaydedilebilir. Stop sonrası hash drift'i
  sessizce yeni taban yapılmamalı. Açılışın ürettiği NPC hash makbuzu ile dış
  düzenlemeyi ayıran taslak ve rollback kontrolleri özellikle incelenmeli.
- Tam kapalı sunucu + üç hedefin özel kilidi; tüm yedeklerin ilk geri yazmadan
  önce doğrulanması; hata halinde daha fazla yazmama garantileri sınanmalı.
- Mevcut BAT/JVM, Citizens 313/skin/konum, wiki, mesaj, hafıza ve dünya korunmalı.
  Eski kişilikteki güncel bilgiyle çelişen cümle ayrı özel diff olarak gösterilmeli.

## Sonraki uygulama adımı

1. AGENTS/STATE/DECISIONS ve `git status --short --branch` oku; bu yedi dosyanın
   mevcut diff'ini devral. Güncel kullanıcı kapsamı yalnız belgeyse kod işine girme.
2. Kod aşaması sürdürüldüğünde taslağı derlenebilir hâle getir; sahte sunucu
   transaction/rollback testlerini tamamla. Eski dağıtım uyumluluğunu koru.
3. JDK 26 ile yalnız `Deploy-Superseed.ps1 -Mode Build` yolunda gerçek Java ve
   PowerShell testlerini çalıştır; yeni sonuçları açıkça kaydet. Inference gerekmez.
4. Gerçek dosyaları yalnız okuyarak yeni özel aday/manifest/diff üret; salt okunur
   önizlemeyi doğrula. Sonraki onay için gerçek hash'lere bağlı tam komutu ve kısa
   kontrol listesini raporla; henüz doğrulanmamış bir canlı komut uydurma.
5. Anlamlı aşamayı ayrı commit'le PR #2'ye taşı, HANDOFF/STATE'i güncelle ve
   gerçek dağıtım öncesinde açık kullanıcı onayını bekle.

Önceki pilotun ham kanıtları ve adayları yalnız yerel özel alandadır. Gerekirse
yerinde incele; gizli dosya içeriklerini bu notlara/Git'e kopyalama.
