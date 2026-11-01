package com.l299l.npcspeak.knowledge;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/** Deliberately strict heuristic: a rejected opinion costs only a follow-up invitation. Not a truth check. */
final class CommentaryGuard {
    private static final int MAX_LENGTH = 180;
    private static final Pattern OPINION_START = Pattern.compile("^(?:bence|bana gore|bana kalirsa|kanimca|kisisel olarak)\\b.*");
    private static final Pattern FACT_CLAIM = Pattern.compile("\\b(?:bagli|bagla|amir|kayit|kayd|resmi|belge|atan|atam|atad|secil|secim|oy(?:lar|u|a)?\\b"
            + "|yetki|vekil|mudur|yardimci|makam|gorev|kurdu|kurul|acti|yapti|yapildi|kazandi|getirdi|imzala|karar|butce|proje|tarih|yil|eskiden|memnun|sikayet)");
    // The setting itself is not a claim about a person or institution.
    private static final String SETTING_NAME = "superseed";

    private CommentaryGuard() {}

    static boolean safe(String candidate, String clause, List<String> blockedTerms) {
        return rejection(candidate, clause, blockedTerms) == null;
    }

    /** The first failed rule, or null when the opinion may follow the fixed answer. */
    static String rejection(String candidate, String clause, List<String> blockedTerms) {
        if (candidate == null || candidate.isBlank()) return "EMPTY";
        String text = candidate.strip();
        if (text.length() > MAX_LENGTH) return "TOO_LONG";
        if (text.matches("(?s).*[\\d\\n\"“”].*")) return "DIGIT_QUOTE_OR_NEWLINE";
        String normalized = normalize(text);
        if (!OPINION_START.matcher(normalized).matches()) return "NO_OPINION_START";
        var claim = FACT_CLAIM.matcher(normalized);
        if (claim.find()) return "FACT_CLAIM:" + claim.group();
        if (Arrays.stream(text.split("[.!?]+\\s*")).filter(s -> !s.isBlank()).count() > 2) return "TOO_MANY_SENTENCES";
        for (String term : blockedTerms) if (normalized.contains(term)) return "BLOCKED_TERM:" + term;
        String name = introducedName(text, normalize(clause));
        return name == null ? null : "NEW_NAME:" + name;
    }

    private static String normalize(String text) { return LoreEngine.normalize(text).replace('’', '\''); }

    // A capitalized word the player did not write is treated as a new person, place or organization.
    // At a sentence start capitals prove nothing, so only a suffix or a following name marks one there.
    private static String introducedName(String text, String clause) {
        String[] words = text.split("\\s+");
        for (int i = 1; i < words.length; i++) {
            String word = cleanWord(words[i]);
            if (word.isEmpty() || !Character.isUpperCase(word.codePointAt(0))) continue;
            String stem = normalize(word).replaceFirst("'.*$", "");
            if (stem.equals(SETTING_NAME) || clause.contains(stem)) continue;
            boolean sentenceStart = words[i - 1].matches(".*[.!?]$");
            boolean nameShaped = word.matches(".*['’].*") || i + 1 < words.length && !cleanWord(words[i + 1]).isEmpty()
                    && Character.isUpperCase(cleanWord(words[i + 1]).codePointAt(0));
            if (!sentenceStart || nameShaped) return word;
        }
        return null;
    }

    private static String cleanWord(String word) { return word.replaceAll("[^\\p{L}'’]", ""); }
}
