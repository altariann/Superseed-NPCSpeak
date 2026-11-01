package com.l299l.npcspeak.knowledge;

import com.l299l.npcspeak.npc.NpcData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class DirectedRecordsTest {
    @TempDir Path root;
    private LoreEngine load(String fields) throws Exception {
        Files.writeString(root.resolve("knowledge.yml"), """
                municipal-records:
                  - id: reporting
                    type: relationship
                    subjects: [Doğan Tırpan, Tech]
                    current: true
                    text: 'Doğan Tırpan, Tech adlı kişiye bağlıdır.'
                """ + fields);
        return KnowledgeBase.load(root);
    }
    @Test void explicitDirectionSurvivesLoadingAndRetrieval() throws Exception {
        var engine = load("    relation: reports_to\n    from: Doğan Tırpan\n    to: Tech\n");
        var record = engine.municipalRecords().get(0);
        assertEquals("Doğan Tırpan", record.from());
        assertEquals("Tech", record.to());
        assertTrue(engine.retrieve("Doğan Tırpan kime bağlı?").sourceData().contains("\"relation\":\"reports_to\""));
        assertThrows(UnsupportedOperationException.class, () -> engine.municipalRecords().clear());
    }
    @Test void legacyRelationshipRemainsReadableWithoutInventingDirection() throws Exception {
        assertEquals("", load("").municipalRecords().get(0).relation());
    }
    @Test void malformedOrPartialEdgesAreRejected() {
        for (String fields : new String[]{"    relation: reports_to\n", "    from: Tech\n    to: Doğan Tırpan\n",
                "    relation: supports\n    from: Doğan Tırpan\n    to: Tech\n",
                "    relation: reports_to\n    from: Doğan Tırpan\n    to: Other\n",
                "    relation: reports_to\n    from: Tech\n    to: Tech\n"}) {
            assertThrows(IllegalArgumentException.class, () -> load(fields));
        }
    }
    @Test void explicitNpcBindingRoundTripsWithoutChangingCitizensOrCustomFields() throws Exception {
        Path file = root.resolve("npc.yml");
        Files.writeString(file, "display-name: Doğan Tırpan\ncitizens-id: 313\nknowledge-entity: Doğan Tırpan\ncustom-skin: untouched\n");
        var npc = NpcData.fromFile("dogan-tirpan", file.toFile());
        assertEquals("Doğan Tırpan", npc.getConfig().getKnowledgeEntity());
        npc.saveToFile(file.toFile());
        var restored = NpcData.fromFile("dogan-tirpan", file.toFile());
        assertEquals(313, restored.getCitizensId());
        assertEquals("Doğan Tırpan", restored.getConfig().getKnowledgeEntity());
        assertTrue(Files.readString(file).contains("custom-skin: untouched"));
    }
    @Test void displayNameAndFileNameDoNotEstablishKnowledgeIdentity() throws Exception {
        Path file = root.resolve("npc.yml");
        Files.writeString(file, "display-name: Doğan Tırpan\n");
        var config = NpcData.fromFile("dogan-tirpan", file.toFile()).getConfig();
        assertNull(config.getKnowledgeEntity());
        assertFalse(new SpeakerContext("dogan-tirpan", "Doğan Tırpan", null).isSelf("Doğan Tırpan"));
    }
}
