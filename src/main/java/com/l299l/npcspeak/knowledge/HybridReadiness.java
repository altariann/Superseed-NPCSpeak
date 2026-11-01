package com.l299l.npcspeak.knowledge;

import com.google.gson.Gson;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Content-free boot attestation of the loaded identity, directed schema and artifact. */
public final class HybridReadiness {
    private HybridReadiness() {}
    public static String sha256(Path file) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) >= 0) digest.update(buffer, 0, count);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
    public static String marker(String npcId, String entity, int citizensId, boolean freeform,
                                LoreEngine lore, String knowledgeHash, String jarHash) {
        if (!freeform || !lore.settings().enabled() || entity == null || entity.isBlank()
                || lore.municipalRecords().stream().noneMatch(r -> r.current() && r.type().equals("office") && r.subjects().contains(entity))
                || lore.municipalRecords().stream().noneMatch(r -> r.current() && r.relation().equals("reports_to"))) {
            throw new IllegalArgumentException("Hybrid readiness requirements not met");
        }
        var data = new TreeMap<String, Object>();
        data.put("npcId", npcId); data.put("entity", entity); data.put("citizensId", citizensId);
        data.put("knowledgeSha256", knowledgeHash.toLowerCase(Locale.ROOT));
        data.put("jarSha256", jarHash.toLowerCase(Locale.ROOT));
        data.put("records", lore.municipalRecords().stream().sorted(Comparator.comparing(LoreEngine.VerifiedFact::id)).toList());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(new Gson().toJson(data).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return "HYBRID_READY_V1 " + HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
}
