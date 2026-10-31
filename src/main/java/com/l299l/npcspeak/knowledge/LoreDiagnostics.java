package com.l299l.npcspeak.knowledge;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Administrator-supplied queries only. No stored player chats or automatic chat logging. */
public final class LoreDiagnostics {
    private LoreDiagnostics() {}

    public static List<String> lines(LoreEngine.Result result) {
        List<String> output = new ArrayList<>();
        output.add("Lore durumu: " + result.status() + "; doğrulanmış kayıt: " + result.verifiedCount() + "; wiki: " + result.wikiCount());
        output.add("Açık varlık eşleşmeleri: " + String.join(", ", result.entities()));
        if (result.wikiCount() == 0) output.add("AI'ya wiki alıntısı gönderilmedi. Ret/bütçe nedenlerini aşağıda inceleyin.");
        int rejected = 0;
        for (LoreEngine.Candidate candidate : result.candidates()) {
            if (!candidate.selected() && rejected++ >= 10) continue;
            output.add(String.format(Locale.ROOT, "%s | %s #%d | puan=%.2f kapsam=%.0f%% | %s",
                    candidate.selected() ? "SEÇİLDİ" : "ELENDİ", candidate.title(), candidate.passage(), candidate.score(),
                    candidate.coverage() * 100, String.join(", ", candidate.reasons())));
        }
        output.add("AI'ya gönderilen kaynak verisi (birebir JSON; kişilik/hafıza dahil değil):");
        String data = result.sourceData();
        if (data.isEmpty()) output.add("(kaynak yok)");
        for (int offset = 0; offset < data.length(); offset += 220) output.add(data.substring(offset, Math.min(offset + 220, data.length())));
        return List.copyOf(output);
    }
}
