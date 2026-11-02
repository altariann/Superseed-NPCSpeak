package com.l299l.npcspeak.knowledge;

/** Only this final text may be published, logged or remembered. */
public record AnswerOutcome(String text, Origin origin) {
    public enum Origin { SOURCE, MODEL, SOURCE_FALLBACK }
}
