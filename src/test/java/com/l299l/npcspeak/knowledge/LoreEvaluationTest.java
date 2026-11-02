package com.l299l.npcspeak.knowledge;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.charset.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LoreEvaluationTest {
    @TempDir Path root;
    @Test void utf8AuditRejectsMalformedBytesRatherThanReplacingThem() {
        assertThrows(CharacterCodingException.class, () -> LoreEvaluation.decode(new byte[]{(byte)0xc3, 0x28}));
    }
    @Test void evidenceUsesDecodedSourceDataRatherThanJsonEscapeSpelling() {
        var engine = new LoreEngine(LoreEngine.Settings.defaults(), List.of(new LoreEngine.Article("Example Film", "fixture://film", "producer=Example Studio")), List.of(), List.of(), Map.of());
        var item = new LoreEvaluation.Case("one", "source", "Example Film producer nedir?", "GENERATED_UNREVIEWED",
                List.of("Example Film"), List.of("Example Film"), false, List.of("producer=Example Studio"), List.of(), List.of(), "", "");
        assertTrue(LoreEvaluation.failures(item, engine.retrieve(item.query()), 2600).isEmpty());
    }
    @Test void negativeCasesCheckVerifiedSourcesAsWellAsWiki() {
        var engine = new LoreEngine(LoreEngine.Settings.defaults(), List.of(), List.of(),
                List.of(new LoreEngine.VerifiedFact("record", "Example Person leads a test group.", List.of("Example Person"), List.of(), true)), Map.of());
        var item = new LoreEvaluation.Case("one", "negative", "Example Person?", "GENERATED_UNREVIEWED", List.of(), List.of(), true, List.of(), List.of(), List.of(), "", "");
        assertTrue(LoreEvaluation.failures(item, engine.retrieve(item.query()), 2600).contains("UNEXPECTED_SOURCE"));
    }
    @Test void reportSeparatesUnreviewedCasesAndUsesRealEngine() throws Exception {
        Files.createDirectories(root.resolve("knowledge/wiki"));
        Files.writeString(root.resolve("knowledge.yml"), "enabled: true\n");
        Files.writeString(root.resolve("knowledge/wiki/example.txt"), "Title: Example Film\nSource: fixture://film\n\nExample Film is a test production.");
        String cases = """
                [{"id":"one","group":"title","query":"Example Film nedir?","reviewStatus":"GENERATED_UNREVIEWED",
                "allowedTitles":["Example Film"],"requiredTitles":[],"noSources":false,"requiredEvidence":[],"requiredKinds":[],"forbiddenKinds":[],
                "evidenceFile":"knowledge/wiki/example.txt","evidenceQuote":"Example Film is a test production."}]
                """;
        Path casesFile = root.resolve("cases.json"); Files.writeString(casesFile, cases);
        Path output = Files.createDirectory(root.resolve("reports"));
        LoreEvaluation.evaluate(root, casesFile, output);
        var summary = JsonParser.parseString(Files.readString(output.resolve("summary.json"))).getAsJsonObject();
        assertEquals(1, summary.get("passed").getAsInt());
        assertEquals(0, summary.get("humanVerified").getAsInt());
        assertTrue(summary.get("meaning").getAsString().contains("NOT human-verified"));
        assertThrows(FileAlreadyExistsException.class, () -> LoreEvaluation.evaluate(root, casesFile, output));
    }
    @Test void migratedProfileKeepsFrozenEvidenceAndReportsLiteralMismatchInsteadOfRewritingExpectation() throws Exception {
        Path original = Files.createDirectory(root.resolve("original"));
        Path migrated = Files.createDirectory(root.resolve("migrated"));
        Files.writeString(original.resolve("knowledge.yml"), "municipality: 'Example is mayor.'\n");
        Files.writeString(migrated.resolve("knowledge.yml"), "municipality: 'Mayor: Example.'\n");
        Path cases = root.resolve("cases.json");
        Files.writeString(cases, """
                [{"id":"one","group":"migration","query":"Example kim?","reviewStatus":"GENERATED_UNREVIEWED",
                "allowedTitles":[],"requiredTitles":[],"noSources":false,"requiredEvidence":["Example is mayor."],
                "requiredKinds":[],"forbiddenKinds":[],"evidenceFile":"knowledge.yml","evidenceQuote":"Example is mayor."}]
                """);
        String frozen = Files.readString(cases);
        assertThrows(IllegalArgumentException.class, () -> LoreEvaluation.evaluate(migrated, cases, Files.createDirectory(root.resolve("invalid"))));
        Path output = Files.createDirectory(root.resolve("valid"));
        LoreEvaluation.evaluate(migrated, cases, output, original);
        var summary = JsonParser.parseString(Files.readString(output.resolve("summary.json"))).getAsJsonObject();
        assertTrue(summary.get("separateEvidenceProfile").getAsBoolean());
        assertEquals(0, summary.get("passed").getAsInt());
        assertTrue(Files.readString(output.resolve("results.jsonl")).contains("REQUIRED_EVIDENCE_MISSING"));
        assertEquals(frozen, Files.readString(cases));
    }
}
