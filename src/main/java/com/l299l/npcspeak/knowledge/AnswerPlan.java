package com.l299l.npcspeak.knowledge;

import java.util.List;

/** commentBlockedTerms is non-null only when the model may add an opinion after the fixed answer. */
public record AnswerPlan(QueryFrame query, String sourceAnswer, List<String> evidenceIds, List<String> commentBlockedTerms) {
    public AnswerPlan {
        evidenceIds = List.copyOf(evidenceIds);
        commentBlockedTerms = commentBlockedTerms == null ? null : List.copyOf(commentBlockedTerms);
    }
    public AnswerPlan(QueryFrame query, String sourceAnswer, List<String> evidenceIds) { this(query, sourceAnswer, evidenceIds, null); }
    public boolean local() { return query.intent() != QueryFrame.Intent.CHAT && query.remainder().isEmpty(); }
    public boolean guarded() { return !query.remainder().isEmpty(); }
    public boolean commentary() { return commentBlockedTerms != null; }
    public static AnswerPlan chat() { return new AnswerPlan(QueryFrame.chat(), "", List.of()); }
}
