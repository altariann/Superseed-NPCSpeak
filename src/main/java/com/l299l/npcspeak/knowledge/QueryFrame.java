package com.l299l.npcspeak.knowledge;

/** A deliberately small grammar; an unmatched message remains ordinary conversation. */
public record QueryFrame(Intent intent, String subject, boolean self, String remainder) {
    public enum Intent { CHAT, OFFICES, OFFICE_HOLDER, REPORTS_TO, REPORTS_FROM, CLARIFY }
    public static QueryFrame chat() { return new QueryFrame(Intent.CHAT, "", false, ""); }
}
