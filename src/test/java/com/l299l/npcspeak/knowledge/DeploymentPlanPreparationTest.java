package com.l299l.npcspeak.knowledge;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class DeploymentPlanPreparationTest {
    @TempDir Path temp;
    private Path root, recipe, artifact;
    private final Gson json = new Gson();
    private void setup() throws Exception {
        root = Files.createDirectories(temp.resolve("server/plugins/NPCSpeak/npcs")).getParent().getParent().getParent();
        Files.writeString(root.resolve("START.bat"), "fixture launcher");
        Files.writeString(root.resolve("server.properties"), "fixture secret, never print");
        Files.writeString(root.resolve("plugins/NPCSpeak/npcs/dogan-tirpan.yml"), """
                # Preserve this comment.
                display-name: Doğan Tırpan
                citizens-id: 313
                location: {world: fixture, x: 1.5, y: 64, z: -3.25}
                custom-skin: untouched
                personality:
                  system-prompt: 'stale placeholder'
                  task: {type: freeform}
                """);
        Files.writeString(root.resolve("plugins/NPCSpeak/knowledge.yml"), "enabled: true\nmunicipality: 'Original capabilities and constraints.'\ncharacters:\n  dogan_tirpan: 'Showy and occasionally barbed.'\n");
        try (var zip = new ZipOutputStream(Files.newOutputStream(root.resolve("plugins/old.jar")))) {
            zip.putNextEntry(new ZipEntry("plugin.yml")); zip.write("name: NPCSpeak\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)); zip.closeEntry();
        }
        artifact = temp.resolve("candidate.jar");
        Path classes = Path.of(HybridReadiness.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        try (var zip = new ZipOutputStream(Files.newOutputStream(artifact)); var paths = Files.walk(classes.resolve("com/l299l/npcspeak"))) {
            zip.putNextEntry(new ZipEntry("plugin.yml")); zip.write("name: NPCSpeak\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)); zip.closeEntry();
            for (Path path : paths.filter(p -> p.toString().endsWith(".class")).toList()) {
                zip.putNextEntry(new ZipEntry(classes.relativize(path).toString().replace('\\', '/'))); zip.write(Files.readAllBytes(path)); zip.closeEntry();
            }
        }
        String seed;
        try (var input = getClass().getResourceAsStream("/knowledge.yml")) { seed = new String(Objects.requireNonNull(input).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8); }
        seed = seed.replace("  - id: dogan-reports-to-tech", "  - id: dogan-reports-to-tech\n    relation: reports_to\n    from: Doğan Tırpan\n    to: Tech");
        Files.writeString(temp.resolve("seed.yml"), seed);
        Files.writeString(temp.resolve("cases.json"), """
                [
                  {"id":"L01","query":"iladymoon belediyede hangi görevlerde bulunuyor?","expectedRoute":"LOCAL","expectedFragments":["Özel Kalem Müdürü ve Belediye Başkan Vekili"]},
                  {"id":"L02","query":"Sen hangi görevdesin?","expectedRoute":"LOCAL","expectedFragments":["görevlerim: Belediye Başkan Yardımcısı"]},
                  {"id":"L03","query":"Sen kime bağlısın?","expectedRoute":"LOCAL","expectedFragments":["Tech"]},
                  {"id":"L04","query":"iladymoon kime bağlı?","expectedRoute":"LOCAL","expectedFragments":["doğrulanmış güncel kaydım yok"]}
                ]
                """);
        recipe = temp.resolve("recipe.json");
        Files.writeString(recipe, json.toJson(new DeploymentPlanPreparation.Recipe(root.toString(), artifact.toString(), temp.resolve("seed.yml").toString(), temp.resolve("cases.json").toString(), "dogan-tirpan", "Doğan Tırpan", 313, "stale placeholder", "Use current verified records; keep election details unknown.")));
    }
    @Test void preparesCoupledPlanWithoutWritingServerAndPreservesUnchangedFields() throws Exception {
        setup();
        Path npc = root.resolve("plugins/NPCSpeak/npcs/dogan-tirpan.yml"), knowledge = root.resolve("plugins/NPCSpeak/knowledge.yml");
        byte[] before = Files.readAllBytes(npc), oldKnowledge = Files.readAllBytes(knowledge);
        Path output = temp.resolve("plan"); DeploymentPlanPreparation.prepare(recipe, output);
        assertArrayEquals(before, Files.readAllBytes(npc)); assertArrayEquals(oldKnowledge, Files.readAllBytes(knowledge));
        String candidate = Files.readString(output.resolve("files/npc.yml"));
        assertTrue(candidate.contains("# Preserve this comment.")); assertTrue(candidate.contains("custom-skin: untouched"));
        assertTrue(candidate.contains("citizens-id: 313")); assertTrue(candidate.contains("knowledge-entity: 'Doğan Tırpan'"));
        assertTrue(Files.readString(output.resolve("files/knowledge.yml")).startsWith(new String(oldKnowledge, java.nio.charset.StandardCharsets.UTF_8)));
        var validation = JsonParser.parseString(Files.readString(output.resolve("validation.json"))).getAsJsonObject();
        assertEquals(4, validation.get("LocalChecks").getAsInt()); assertTrue(validation.get("ArtifactClassesVerified").getAsBoolean());
        assertTrue(validation.get("Marker").getAsString().matches("HYBRID_READY_V1 [a-f0-9]{64}"));
        assertTrue(Files.readString(output.resolve("npc.personality.diff")).contains("stale placeholder"));
    }
    @Test void wrongCitizensIdentityOrMissingPatchStopsBeforePlanCreation() throws Exception {
        setup(); Files.writeString(root.resolve("plugins/NPCSpeak/npcs/dogan-tirpan.yml"), "citizens-id: 999\n");
        assertThrows(IllegalArgumentException.class, () -> DeploymentPlanPreparation.prepare(recipe, temp.resolve("plan")));
        assertFalse(Files.exists(temp.resolve("plan")));
    }
    @Test void changedOrIncompleteArtifactCannotBorrowValidationFromCurrentClasses() throws Exception {
        setup();
        Path fake = temp.resolve("fake.jar");
        try (var zip = new ZipOutputStream(Files.newOutputStream(fake))) {
            zip.putNextEntry(new ZipEntry("plugin.yml")); zip.write("name: NPCSpeak\n".getBytes()); zip.closeEntry();
        }
        assertThrows(IllegalArgumentException.class, () -> DeploymentPlanPreparation.verifyArtifact(fake));
    }
    @Test void readinessChangesWithArtifactKnowledgeIdentityOrDirection() throws Exception {
        setup(); Path out = temp.resolve("plan"); DeploymentPlanPreparation.prepare(recipe, out);
        var lore = KnowledgeBase.load(out.resolve("check-data"));
        String marker = HybridReadiness.marker("dogan-tirpan", "Doğan Tırpan", 313, true, lore, "a".repeat(64), "b".repeat(64));
        assertNotEquals(marker, HybridReadiness.marker("dogan-tirpan", "Doğan Tırpan", 314, true, lore, "a".repeat(64), "b".repeat(64)));
        assertNotEquals(marker, HybridReadiness.marker("dogan-tirpan", "Doğan Tırpan", 313, true, lore, "c".repeat(64), "b".repeat(64)));
        assertThrows(IllegalArgumentException.class, () -> HybridReadiness.marker("dogan-tirpan", "Doğan Tırpan", 313, false, lore, "a", "b"));
        assertFalse(marker.contains("Doğan"));
        assertEquals(marker, HybridReadiness.marker("dogan-tirpan", "Doğan Tırpan", 313, true, KnowledgeBase.load(out.resolve("check-data")), "a".repeat(64), "b".repeat(64)));
        assertNotEquals(marker, HybridReadiness.marker("dogan-tirpan", "Doğan Tırpan", 313, true, lore, "a".repeat(64), "d".repeat(64)));
        var reversed = lore.municipalRecords().stream().map(r -> "reports_to".equals(r.relation())
                ? new LoreEngine.VerifiedFact(r.id(), r.text(), r.subjects(), r.keywords(), r.current(), r.type(), r.offices(), true, r.relation(), r.to(), r.from()) : r).toList();
        assertNotEquals(marker, HybridReadiness.marker("dogan-tirpan", "Doğan Tırpan", 313, true,
                new LoreEngine(lore.settings(), List.of(), List.of(), reversed, Map.of()), "a".repeat(64), "b".repeat(64)));
    }
    @Test void multiLinePersonaPatchStaysInsideBlockAndKeepsOtherLines() throws Exception {
        setup(); Path npc = root.resolve("plugins/NPCSpeak/npcs/dogan-tirpan.yml");
        Files.writeString(npc, """
                display-name: Doğan Tırpan
                citizens-id: 313
                personality:
                  system-prompt: |
                    First retained line.

                    Stale first line,
                    stale second line.

                    Last retained line.
                  topic: untouched
                  task: {type: freeform}
                """);
        String old = "    Stale first line,\n    stale second line.", replacement = "    Use verified records: never invent.";
        Files.writeString(recipe, json.toJson(new DeploymentPlanPreparation.Recipe(root.toString(), artifact.toString(), temp.resolve("seed.yml").toString(), temp.resolve("cases.json").toString(), "dogan-tirpan", "Doğan Tırpan", 313, old, replacement)));
        Path output = temp.resolve("plan"); DeploymentPlanPreparation.prepare(recipe, output);
        String expected = Files.readString(npc).replace(old, replacement) + "\nknowledge-entity: 'Doğan Tırpan'\n";
        assertEquals(expected, Files.readString(output.resolve("files/npc.yml")));
        var candidate = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(output.resolve("files/npc.yml").toFile());
        assertEquals("First retained line.\n\nUse verified records: never invent.\n\nLast retained line.\n", candidate.getString("personality.system-prompt"));
        assertEquals("untouched", candidate.getString("personality.topic")); assertEquals(313, candidate.getInt("citizens-id"));
    }
    @Test void alreadyMigratedSourceRequiresNewRecipe() throws Exception {
        setup(); Path npc = root.resolve("plugins/NPCSpeak/npcs/dogan-tirpan.yml");
        Files.writeString(npc, Files.readString(npc) + "knowledge-entity: 'Doğan Tırpan'\n");
        assertThrows(IllegalArgumentException.class, () -> DeploymentPlanPreparation.prepare(recipe, temp.resolve("plan")));
        assertFalse(Files.exists(temp.resolve("plan")));
    }
    @Test void outputInsideServerOrExistingPlanIsRefused() throws Exception {
        setup();
        assertThrows(IllegalArgumentException.class, () -> DeploymentPlanPreparation.prepare(recipe, root.resolve("plan")));
        assertFalse(Files.exists(root.resolve("plan")));
        Path existing = Files.createDirectory(temp.resolve("plan"));
        assertThrows(FileAlreadyExistsException.class, () -> DeploymentPlanPreparation.prepare(recipe, existing));
    }
    @Test void failedLocalRouteLeavesNoApprovablePlan() throws Exception {
        setup(); Path cases = temp.resolve("cases.json");
        Files.writeString(cases, Files.readString(cases).replace("\"Tech\"]", "\"Başkası\"]"));
        Path output = temp.resolve("plan");
        assertThrows(IllegalArgumentException.class, () -> DeploymentPlanPreparation.prepare(recipe, output));
        assertFalse(Files.exists(output.resolve("deployment-plan.json"))); assertFalse(Files.exists(output.resolve("validation.json")));
    }
}
