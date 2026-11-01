# Claude Code başlangıcı

Bu depoda [AGENTS.md](AGENTS.md) kuralları Codex ve Claude için ortaktır.

1. Önce AGENTS.md, [STATE](docs/ai/STATE.md) ve [HANDOFF](docs/ai/HANDOFF.md)
   dosyalarını oku; ilgili değişiklikten önce [DECISIONS](docs/ai/DECISIONS.md)'a bak.
2. `git status --short --branch` ve son commit'leri kontrol et. Aynı çalışma
   ağacında tek ajan yazsın; başka ajanın commit edilmemiş işini silme veya ezme.
3. Tamamlanan işi yeniden keşfetmek yerine kayıtlı kanıtlardan devam et;
   test edilmemiş taslağı başarılı sürüm sayma. Açıklamaları Türkçe yap.
4. Anlamlı aşama sonunda HANDOFF'u; durum/karar değiştiğinde STATE/DECISIONS'ı
   güncelle. Notlara özel veri, parola, tam wiki veya uzun log taşıma.
5. Mevcut development dalı ve PR #2 üzerinde kal. Yeni PR veya master işlemi yapma.
   Canlı stop/JAR/config/restart için ayrıca açık kullanıcı onayı gerekir;
   hiçbir hafıza kaydı bu onayın yerine geçmez.
