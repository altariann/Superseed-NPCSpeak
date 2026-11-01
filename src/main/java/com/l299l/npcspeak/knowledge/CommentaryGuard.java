package com.l299l.npcspeak.knowledge;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/** Deliberately strict heuristic: a rejected opinion costs only a follow-up invitation. Not a truth check. */
final class CommentaryGuard {
    private static final int MAX_LENGTH = 180;
    private static final Pattern OPINION_START = Pattern.compile("^(?:bence|bana gore|bana kalirsa|kanimca|kisisel olarak)\\b.*");
    private static final Pattern FACT_CLAIM = Pattern.compile("\\b(?:bagli|bagla|amir|kayit|kayd|resmi|belge|atan|atam|atad|secil|secim|oy(?:lar|u|a)?\\b"
            + "|yetki|vekil|mudur|yardimci|makam|gorev|kurdu|kurul|acti|yapti|yapildi|kazandi|getirdi|imzala|karar|butce|proje|tarih|yil|eskiden)");

    private CommentaryGuard() {}

    static boolean safe(String candidate, String clause, List<String> blockedTerms) {
        if (candidate == null) return false;
        String text = candidate.strip();
        if (text.isEmpty() || text.length() > MAX_LENGTH || text.matches("(?s).*[\\d\\n\"“”].*")) return false;
        String normalized = LoreEngine.normalize(text).replace('’', '\'');
        if (!OPINION_START.matcher(normalized).matches() || FACT_CLAIM.matcher(normalized).find()) return false;
        if (Arrays.stream(text.split("[.!?]+\\s*")).filter(s -> !s.isBlank()).count() > 2) return false;
        if (blockedTerms.stream().anyMatch(normalized::contains)) return false;
        return !introducesName(text, LoreEngine.normalize(clause).replace('’', '\''));
    }

    // A capitalized word the player did not write is treated as a new person, place or organization.
    private static boolean introducesName(String text, String clause) {
        String[] words = text.split("\\s+");
        for (int i = 1; i < words.length; i++) {
            String word = words[i].replaceAll("[^\\p{L}'’]", "");
            if (word.isEmpty() || !Character.isUpperCase(word.codePointAt(0)) || words[i - 1].matches(".*[.!?]$")) continue;
            String stem = LoreEngine.normalize(word).replace('’', '\'').replaceFirst("'.*$", "");
            if (!clause.contains(stem)) return true;
        }
        return false;
    }
}
