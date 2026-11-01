package com.l299l.npcspeak.knowledge;

/** Only an explicit configuration binding establishes the narrator's factual identity. */
public record SpeakerContext(String npcId, String displayName, String knowledgeEntity) {
    public boolean isSelf(String entity) {
        return knowledgeEntity != null && !knowledgeEntity.isBlank()
                && LoreEngine.normalize(knowledgeEntity).equals(LoreEngine.normalize(entity));
    }
}
