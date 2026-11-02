package com.l299l.npcspeak.knowledge;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class JarPlanPreparationTest {
    @TempDir Path temp;
    private Path root, recipe, artifact;
    private final Gson json = new Gson();
    private static final String COMMIT = "a".repeat(40);
    private void jar(Path path, boolean production) throws Exception {
        Path classes = Path.of(HybridReadiness.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        try (var zip = new ZipOutputStream(Files.newOutputStream(path))) {
            zip.putNextEntry(new ZipEntry("plugin.yml")); zip.write("name: NPCSpeak\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)); zip.closeEntry();
            if (production) try (var paths = Files.walk(classes.resolve("com/l299l/npcspeak"))) {
                for (Path file : paths.filter(p -> p.toString().endsWith(".class")).toList()) {
                    zip.putNextEntry(new ZipEntry(classes.relativize(file).toString().replace('\\', '/'))); zip.write(Files.readAllBytes(file)); zip.closeEntry();
                }
            }
        }
    }
    private void setup() throws Exception {
        root = Files.createDirectories(temp.resolve("server/plugins/NPCSpeak/npcs")).getParent().getParent().getParent();
        Files.createDirectories(root.resolve("plugins/NPCSpeak/knowledge/wiki"));
        Files.createDirectories(root.resolve("plugins/Citizens"));
        Files.writeString(root.resolve("START.bat"), "fixture launcher");
        Files.writeString(root.resolve("server.properties"), "#Minecraft server properties\n#Sun Nov 01 21:22:21 GMT+03:00 2026\nserver-port=1\n");
        Files.writeString(root.resolve("plugins/NPCSpeak/npcs/dogan-tirpan.yml"), """
                display-name: Doğan Tırpan
                citizens-id: 313
                knowledge-entity: Doğan Tırpan
                personality:
                  task: {type: freeform}
                """);
        String seed;
        try (var input = getClass().getResourceAsStream("/knowledge.yml")) { seed = new String(Objects.requireNonNull(input).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8); }
        Files.writeString(root.resolve("plugins/NPCSpeak/knowledge.yml"), seed.replace("  - id: dogan-reports-to-tech", "  - id: dogan-reports-to-tech\n    relation: reports_to\n    from: Doğan Tırpan\n    to: Tech"));
        Files.writeString(root.resolve("plugins/NPCSpeak/messages.yml"), "typing: fixture\n");
        Files.writeString(root.resolve("plugins/NPCSpeak/knowledge/wiki/A.txt"), "Title: Fixture\n");
        Files.writeString(root.resolve("plugins/Citizens/saves.yml"), "npc:\n  '313':\n    name: Fixture\n");
        Files.writeString(root.resolve("plugins/Other.jar"), "not a zip");
        jar(root.resolve("plugins/old.jar"), false);
        artifact = temp.resolve("candidate.jar"); jar(artifact, true);
        Files.writeString(temp.resolve("cases.json"), """
                [
                  {"id":"L01","query":"iladymoon belediyede hangi görevlerde bulunuyor?","expectedRoute":"LOCAL","expectedFragments":["Özel Kalem Müdürü ve Belediye Başkan Vekili"]},
                  {"id":"L02","query":"Sen hangi görevdesin?","expectedRoute":"LOCAL","expectedFragments":["görevlerim: Belediye Başkan Yardımcısı"]},
                  {"id":"L03","query":"Haklısınız, başkanımızın adı nedir?","expectedRoute":"LOCAL","expectedFragments":["Tech"]},
                  {"id":"L04","query":"iladymoon kime bağlı?","expectedRoute":"LOCAL","expectedFragments":["doğrulanmış güncel kaydım yok"]}
                ]
                """);
        recipe = temp.resolve("recipe.json");
        Files.writeString(recipe, json.toJson(new JarPlanPreparation.Recipe(root.toString(), artifact.toString(), temp.resolve("cases.json").toString(), "dogan-tirpan", "Doğan Tırpan", 313, COMMIT)));
    }
    private Map<Path, byte[]> snapshot() throws Exception {
        Map<Path, byte[]> files = new HashMap<>();
        try (var paths = Files.walk(root)) { for (Path p : paths.filter(Files::isRegularFile).toList()) files.put(p, Files.readAllBytes(p)); }
        return files;
    }
    @Test void plansOnlyTheJarAndLeavesTheServerUntouched() throws Exception {
        setup();
        var before = snapshot();
        Path output = temp.resolve("plan"); JarPlanPreparation.prepare(recipe, output);
        var after = snapshot();
        assertEquals(before.keySet(), after.keySet());
        before.forEach((path, bytes) -> assertArrayEquals(bytes, after.get(path), path.toString()));
        var plan = JsonParser.parseString(Files.readString(output.resolve("deployment-plan.json"))).getAsJsonObject();
        assertEquals("JarOnly", plan.get("PlanType").getAsString());
        assertFalse(plan.has("Files"), "A JAR plan cannot describe NPC or knowledge writes");
        assertEquals("plugins/old.jar", plan.getAsJsonObject("Jar").get("Target").getAsString());
        assertEquals(COMMIT, plan.get("SourceCommit").getAsString());
        assertFalse(Files.exists(output.resolve("files/npc.yml")));
        assertFalse(Files.exists(output.resolve("files/knowledge.yml")));
        var inventory = JsonParser.parseString(Files.readString(output.resolve("protected.json"))).getAsJsonObject();
        var paths = new ArrayList<String>();
        inventory.getAsJsonArray("Strict").forEach(e -> paths.add(e.getAsJsonObject().get("Path").getAsString()));
        assertTrue(paths.containsAll(List.of("plugins/NPCSpeak/knowledge.yml", "plugins/NPCSpeak/messages.yml", "plugins/Other.jar", "plugins/NPCSpeak/knowledge/wiki/A.txt")));
        assertFalse(paths.contains("plugins/old.jar"));
        assertEquals(1, inventory.getAsJsonArray("Npc").size());
        var validation = JsonParser.parseString(Files.readString(output.resolve("validation.json"))).getAsJsonObject();
        assertTrue(validation.get("MigratedSource").getAsBoolean());
        assertEquals(plan.get("Marker").getAsString(), validation.get("Marker").getAsString());
    }
    @Test void propertiesFingerprintIgnoresBootTimestampButNotSettings() throws Exception {
        setup();
        Path file = root.resolve("server.properties");
        String first = JarPlanPreparation.propertiesFingerprint(file);
        Files.writeString(file, Files.readString(file).replace("21:22:21", "22:01:29"));
        assertEquals(first, JarPlanPreparation.propertiesFingerprint(file));
        Files.writeString(file, Files.readString(file).replace("server-port=1", "server-port=2"));
        assertNotEquals(first, JarPlanPreparation.propertiesFingerprint(file));
    }
    @Test void unmigratedServerMustUseTheThreeFilePath() throws Exception {
        setup(); Path npc = root.resolve("plugins/NPCSpeak/npcs/dogan-tirpan.yml");
        Files.writeString(npc, Files.readString(npc).replace("knowledge-entity: Doğan Tırpan\n", ""));
        assertThrows(IllegalArgumentException.class, () -> JarPlanPreparation.prepare(recipe, temp.resolve("plan")));
        assertFalse(Files.exists(temp.resolve("plan")));
    }
    @Test void identityMismatchOrSameJarStopsBeforePlanCreation() throws Exception {
        setup(); Path npc = root.resolve("plugins/NPCSpeak/npcs/dogan-tirpan.yml");
        String original = Files.readString(npc);
        Files.writeString(npc, original.replace("citizens-id: 313", "citizens-id: 999"));
        assertThrows(IllegalArgumentException.class, () -> JarPlanPreparation.prepare(recipe, temp.resolve("plan")));
        Files.writeString(npc, original);
        Files.copy(artifact, root.resolve("plugins/old.jar"), StandardCopyOption.REPLACE_EXISTING);
        assertThrows(IllegalArgumentException.class, () -> JarPlanPreparation.prepare(recipe, temp.resolve("plan")));
        assertFalse(Files.exists(temp.resolve("plan")));
    }
    @Test void incompleteArtifactAndUnsafeOutputAreRefused() throws Exception {
        setup();
        assertThrows(IllegalArgumentException.class, () -> JarPlanPreparation.prepare(recipe, root.resolve("plan")));
        Path fake = temp.resolve("fake.jar"); jar(fake, false);
        Files.writeString(recipe, json.toJson(new JarPlanPreparation.Recipe(root.toString(), fake.toString(), temp.resolve("cases.json").toString(), "dogan-tirpan", "Doğan Tırpan", 313, COMMIT)));
        assertThrows(IllegalArgumentException.class, () -> JarPlanPreparation.prepare(recipe, temp.resolve("plan")));
        assertFalse(Files.exists(temp.resolve("plan")));
    }
}
