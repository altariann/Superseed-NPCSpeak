package com.l299l.npcspeak.knowledge;

import java.util.List;

public record AnswerPlan(QueryFrame query, String sourceAnswer, List<String> evidenceIds) {
    public AnswerPlan { evidenceIds = List.copyOf(evidenceIds); }
    public boolean local() { return query.intent() != QueryFrame.Intent.CHAT && query.remainder().isEmpty(); }
    public boolean guarded() { return !query.remainder().isEmpty(); }
    public static AnswerPlan chat() { return new AnswerPlan(QueryFrame.chat(), "", List.of()); }
}
