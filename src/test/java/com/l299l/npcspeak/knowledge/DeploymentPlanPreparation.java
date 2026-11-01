package com.l299l.npcspeak.knowledge;

import com.google.gson.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipFile;

/** Opt-in preparation only: reads the server, writes a separate private plan, never starts it. */
public final class DeploymentPlanPreparation {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    record Recipe(String root, String jar, String seed, String cases, String npcId, String entity,
                  int citizensId, String personaOld, String personaNew) {}
    public static void main(String[] args) throws Exception {
        Map<String, String> flags = new HashMap<>();
        for (int i = 0; i + 1 < args.length; i += 2) flags.put(args[i], args[i + 1]);
        prepare(Path.of(flags.get("--recipe")), Path.of(flags.get("--output")));
    }
    static void prepare(Path recipeFile, Path output) throws Exception {
        Recipe recipe = JSON.fromJson(Files.readString(recipeFile), Recipe.class);
        Path root = Path.of(recipe.root()).toRealPath(), jar = Path.of(recipe.jar()).toRealPath();
        output = output.toAbsolutePath().normalize();
        if (output.startsWith(root) || root.startsWith(output) || !recipe.npcId().matches("[a-zA-Z0-9_-]+"))
            throw new IllegalArgumentException("Separate private output and explicit NPC id required");
        Path npc = root.resolve("plugins/NPCSpeak/npcs/" + recipe.npcId() + ".yml");
        Path knowledge = root.resolve("plugins/NPCSpeak/knowledge.yml");
        Path oldJar;
        try (var files = Files.list(root.resolve("plugins"))) {
            var jars = files.filter(p -> p.getFileName().toString().endsWith(".jar")).filter(DeploymentPlanPreparation::isNpcSpeak).toList();
            if (jars.size() != 1) throw new IllegalArgumentException("Exactly one original NPCSpeak JAR required");
            oldJar = jars.get(0);
        }
        verifyArtifact(jar);
        String oldNpcHash = HybridReadiness.sha256(npc), oldKnowledgeHash = HybridReadiness.sha256(knowledge);
        String oldJarHash = HybridReadiness.sha256(oldJar), batHash = HybridReadiness.sha256(root.resolve("START.bat"));
        String propertiesHash = HybridReadiness.sha256(root.resolve("server.properties"));
        String npcText = Files.readString(npc), knowledgeText = Files.readString(knowledge);
        var originalNpc = yaml(npc);
        var originalKnowledge = yaml(knowledge);
        if (originalNpc.contains("knowledge-entity") || originalKnowledge.contains("municipal-records"))
            throw new IllegalArgumentException("Already migrated source requires a new reviewed recipe");
        if (originalNpc.getInt("citizens-id", -1) != recipe.citizensId()) throw new IllegalArgumentException("Citizens identity mismatch");
        String changedNpc = npcText;
        if (recipe.personaOld() != null && !recipe.personaOld().isEmpty()) {
            if (changedNpc.indexOf(recipe.personaOld()) < 0 || changedNpc.indexOf(recipe.personaOld()) != changedNpc.lastIndexOf(recipe.personaOld()))
                throw new IllegalArgumentException("Persona patch must match exactly once");
            changedNpc = changedNpc.replace(recipe.personaOld(), Objects.requireNonNull(recipe.personaNew()));
        }
        if (recipe.entity() == null || recipe.entity().contains("\n") || recipe.entity().isBlank()) throw new IllegalArgumentException("Explicit entity required");
        var seed = yaml(Path.of(recipe.seed()));
        Object records = seed.get("municipal-records");
        if (!(records instanceof List<?> list) || list.isEmpty()) throw new IllegalArgumentException("Reviewed atomic records required");
        var addition = new YamlConfiguration(); addition.set("municipal-records", records);
        String appendedKnowledge = "\n# Reviewed atomic migration; legacy text above is retained.\n" + addition.saveToString();
        String binding = "\nknowledge-entity: '" + recipe.entity().replace("'", "''") + "'\n";
        Files.createDirectory(output); Files.createDirectory(output.resolve("files")); Files.createDirectory(output.resolve("original"));
        Files.copy(jar, output.resolve("files/new.jar"));
        Files.copy(npc, output.resolve("original/npc.yml")); Files.copy(knowledge, output.resolve("original/knowledge.yml"));
        Files.writeString(output.resolve("files/npc.yml"), changedNpc + binding, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        Files.writeString(output.resolve("files/knowledge.yml"), knowledgeText + appendedKnowledge, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        var candidateNpc = yaml(output.resolve("files/npc.yml"));
        var candidateKnowledge = yaml(output.resolve("files/knowledge.yml"));
        Map<String, Object> oldNpcMap = plain(originalNpc), newNpcMap = plain(candidateNpc);
        newNpcMap.remove("knowledge-entity");
        if (recipe.personaOld() != null && !recipe.personaOld().isEmpty()) {
            ((Map<?, ?>) newNpcMap.get("personality")).remove("system-prompt");
            ((Map<?, ?>) oldNpcMap.get("personality")).remove("system-prompt");
        }
        if (!oldNpcMap.equals(newNpcMap)) throw new IllegalArgumentException("Unexpected NPC semantic changes");
        Map<String, Object> newKnowledgeMap = plain(candidateKnowledge); newKnowledgeMap.remove("municipal-records");
        if (!plain(originalKnowledge).equals(newKnowledgeMap)) throw new IllegalArgumentException("Original knowledge fields changed");
        Path candidateData = output.resolve("check-data"); Files.createDirectory(candidateData);
        Files.copy(output.resolve("files/knowledge.yml"), candidateData.resolve("knowledge.yml"));
        LoreEngine lore = KnowledgeBase.load(candidateData);
        var speaker = new SpeakerContext(recipe.npcId(), candidateNpc.getString("display-name", recipe.npcId()), candidateNpc.getString("knowledge-entity"));
        var cases = JSON.fromJson(Files.readString(Path.of(recipe.cases())), HybridPilot.Case[].class);
        if (cases == null || cases.length < 4) throw new IllegalArgumentException("Frozen local checks required");
        for (var item : cases) {
            var plan = new AnswerPlanner().plan(item.query(), speaker, lore, 0);
            if (!"LOCAL".equals(item.expectedRoute()) || !plan.local()
                    || item.expectedFragments().stream().anyMatch(f -> !plan.sourceAnswer().contains(f)))
                throw new IllegalArgumentException("Local deployment check failed: " + item.id());
        }
        boolean freeform = "freeform".equalsIgnoreCase(candidateNpc.getString("personality.task.type", "freeform"));
        String jarHash = HybridReadiness.sha256(output.resolve("files/new.jar"));
        String npcHash = HybridReadiness.sha256(output.resolve("files/npc.yml"));
        String knowledgeHash = HybridReadiness.sha256(output.resolve("files/knowledge.yml"));
        String marker = HybridReadiness.marker(recipe.npcId(), recipe.entity(), recipe.citizensId(), freeform, lore, knowledgeHash, jarHash);
        write(output.resolve("validation.json"), Map.of("Valid", true, "LocalChecks", cases.length, "JarSha256", jarHash,
                "NpcSha256", npcHash, "KnowledgeSha256", knowledgeHash, "Marker", marker, "ArtifactClassesVerified", true));
        Files.writeString(output.resolve("npc.binding.diff"), "+" + binding.strip() + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        Files.writeString(output.resolve("npc.personality.diff"), recipe.personaOld() == null ? "No personality change.\n"
                : "--- reviewed old fragment\n" + recipe.personaOld() + "\n+++ proposed fragment\n" + recipe.personaNew() + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        Files.writeString(output.resolve("knowledge.diff"), appendedKnowledge, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        if (!oldNpcHash.equals(HybridReadiness.sha256(npc)) || !oldKnowledgeHash.equals(HybridReadiness.sha256(knowledge))
                || !oldJarHash.equals(HybridReadiness.sha256(oldJar)) || !batHash.equals(HybridReadiness.sha256(root.resolve("START.bat")))
                || !propertiesHash.equals(HybridReadiness.sha256(root.resolve("server.properties")))) throw new IllegalArgumentException("Source changed during preparation");
        List<Object> files = List.of(
                entry("Jar", root.relativize(oldJar).toString().replace('\\', '/'), oldJarHash, "files/new.jar", jarHash),
                entry("Npc", "plugins/NPCSpeak/npcs/" + recipe.npcId() + ".yml", oldNpcHash, "files/npc.yml", npcHash),
                entry("Knowledge", "plugins/NPCSpeak/knowledge.yml", oldKnowledgeHash, "files/knowledge.yml", knowledgeHash));
        var manifest = new LinkedHashMap<String, Object>();
        manifest.put("FormatVersion", 1); manifest.put("ServerRoot", root.toString()); manifest.put("StartBat", "START.bat");
        manifest.put("BatSha256", batHash); manifest.put("PropertiesSha256", propertiesHash); manifest.put("Files", files);
        manifest.put("ValidationSha256", HybridReadiness.sha256(output.resolve("validation.json"))); manifest.put("Marker", marker);
        write(output.resolve("deployment-plan.json"), manifest);
        System.out.println("Private three-file plan ready; " + cases.length + " local checks passed; no server mutation or model call.");
    }
    private static Map<String, Object> entry(String kind, String target, String oldHash, String candidate, String hash) {
        return Map.of("Kind", kind, "Target", target, "OriginalSha256", oldHash, "Candidate", candidate, "CandidateSha256", hash);
    }
    static boolean isNpcSpeak(Path jar) {
        try (var zip = new ZipFile(jar.toFile())) {
            var entry = zip.getEntry("plugin.yml"); if (entry == null) return false;
            var yaml = new YamlConfiguration(); yaml.loadFromString(new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8));
            return "NPCSpeak".equals(yaml.getString("name"));
        } catch (Exception e) { return false; }
    }
    static void verifyArtifact(Path jar) throws Exception {
        if (!isNpcSpeak(jar)) throw new IllegalArgumentException("Candidate is not NPCSpeak");
        int count = 0;
        Path compiledRoot = Path.of(HybridReadiness.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        if (!Files.isDirectory(compiledRoot)) throw new IllegalArgumentException("Explicit compiled production classes required");
        long expected;
        try (var compiledFiles = Files.walk(compiledRoot.resolve("com/l299l/npcspeak"))) {
            expected = compiledFiles.filter(p -> p.toString().endsWith(".class")).count();
        }
        try (var zip = new ZipFile(jar.toFile())) {
            for (var entry : Collections.list(zip.entries())) if (entry.getName().startsWith("com/l299l/npcspeak/") && entry.getName().endsWith(".class")) {
                try (var compiled = HybridReadiness.class.getResourceAsStream("/" + entry.getName())) {
                    if (compiled == null || !Arrays.equals(compiled.readAllBytes(), zip.getInputStream(entry).readAllBytes()))
                        throw new IllegalArgumentException("Artifact differs from the checked production classes");
                }
                count++;
            }
            if (count != expected || zip.getEntry("com/l299l/npcspeak/knowledge/HybridReadiness.class") == null) throw new IllegalArgumentException("Missing production classes/readiness support");
        }
    }
    static YamlConfiguration yaml(Path path) throws Exception {
        var config = new YamlConfiguration(); config.load(path.toFile()); return config;
    }
    private static Map<String, Object> plain(ConfigurationSection section) {
        Map<String, Object> map = new TreeMap<>();
        section.getValues(false).forEach((key, value) -> map.put(key, value instanceof ConfigurationSection child ? plain(child) : value));
        return map;
    }
    private static void write(Path file, Object value) throws Exception {
        Files.writeString(file, JSON.toJson(value), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }
}
