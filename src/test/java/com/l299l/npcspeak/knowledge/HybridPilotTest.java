package com.l299l.npcspeak.knowledge;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class HybridPilotTest {
    @TempDir Path root;
    @Test void exportsOnlyNonLocalRequestsAndReplaysGuardedCandidateWithoutHttp() throws Exception {
        Path data = Files.createDirectory(root.resolve("data")), style = root.resolve("npc.yml"), cases = root.resolve("cases.json"), out = root.resolve("bundle");
        Files.writeString(data.resolve("knowledge.yml"), """
                municipal-records:
                  - {id: role, type: office, subjects: [iladymoon], offices: [Özel Kalem Müdürü], current: true, text: 'Fixture.'}
                """);
        Files.writeString(style, "display-name: Doğan Tırpan\nknowledge-entity: Doğan Tırpan\n");
        Files.writeString(cases, """
                [
                  {"id":"L01","query":"iladymoon'un görevleri neler?","expectedRoute":"LOCAL","expectedFragments":["Özel Kalem Müdürü"]},
                  {"id":"H01","query":"Merhaba","expectedRoute":"MODEL","expectedFragments":[]},
                  {"id":"H02","query":"iladymoon'un görevleri neler, bugün nasılsın?","expectedRoute":"GUARDED","expectedFragments":[]}
                ]
                """);
        HybridPilot.main(new String[]{"--mode","prepare","--data",data.toString(),"--style",style.toString(),"--cases",cases.toString(),"--output",out.toString(),"--npc","fixture","--model","fixture-model"});
        assertFalse(Files.exists(out.resolve("L01.request.json")));
        assertTrue(Files.exists(out.resolve("L01.outcome.json")));
        assertTrue(Files.exists(out.resolve("H01.request.json")));
        var request = JsonParser.parseString(Files.readString(out.resolve("H01.request.json"))).getAsJsonObject();
        assertEquals(3, request.size()); assertFalse(request.get("stream").getAsBoolean());
        Files.writeString(out.resolve("responses/H02.response.json"), """
                {"Response":{"message":{"content":"Ben bütün makamları üstleniyorum."}}}
                """);
        HybridPilot.main(new String[]{"--mode","replay","--output",out.toString(),"--case","H02"});
        var outcome = JsonParser.parseString(Files.readString(out.resolve("H02.outcome.json"))).getAsJsonObject();
        assertEquals("SOURCE_FALLBACK", outcome.get("origin").getAsString());
        assertFalse(outcome.get("text").getAsString().contains("üstleniyorum"));
        assertThrows(FileAlreadyExistsException.class, () -> HybridPilot.main(new String[]{"--mode","replay","--output",out.toString(),"--case","H02"}));
    }
    @Test void commentaryRequestCarriesOnlyOpinionClauseAndReplayRecordsGuardDecision() throws Exception {
        Path data = Files.createDirectory(root.resolve("data")), style = root.resolve("npc.yml"), cases = root.resolve("cases.json"), out = root.resolve("bundle");
        Files.writeString(data.resolve("knowledge.yml"), """
                municipal-records:
                  - {id: mayor, type: office, subjects: [Tech], offices: [Belediye Başkanı], current: true, text: 'Fixture.'}
                  - {id: role, type: office, subjects: [iladymoon], offices: [Özel Kalem Müdürü], current: true, text: 'Fixture.'}
                """);
        Files.writeString(style, "display-name: Doğan Tırpan\nknowledge-entity: Doğan Tırpan\n");
        Files.writeString(cases, """
                [{"id":"C01","query":"iladymoon kime bağlı? Sence Tech iyi bir belediye başkanı mı?","expectedRoute":"COMMENTARY","expectedFragments":[]}]
                """);
        HybridPilot.main(new String[]{"--mode","prepare","--data",data.toString(),"--style",style.toString(),"--cases",cases.toString(),"--output",out.toString(),"--npc","fixture","--model","fixture-model"});
        var messages = JsonParser.parseString(Files.readString(out.resolve("C01.request.json"))).getAsJsonObject().getAsJsonArray("messages");
        assertEquals(2, messages.size());
        assertEquals("Sence Tech iyi bir belediye başkanı mı", messages.get(1).getAsJsonObject().get("content").getAsString());
        assertFalse(messages.get(0).getAsJsonObject().get("content").getAsString().contains("LORE_SOURCE_DATA"));
        Files.writeString(out.resolve("responses/C01.response.json"), """
                {"Response":{"message":{"content":"Bence iladymoon görevinde başarılı."}}}
                """);
        HybridPilot.main(new String[]{"--mode","replay","--output",out.toString(),"--case","C01"});
        var guard = JsonParser.parseString(Files.readString(out.resolve("C01.guard.json"))).getAsJsonObject();
        assertTrue(guard.get("rejection").getAsString().startsWith("FACT_CLAIM:"));
    }
}
