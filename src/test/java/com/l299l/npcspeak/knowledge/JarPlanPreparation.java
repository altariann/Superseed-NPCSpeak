package com.l299l.npcspeak.knowledge;

import com.google.gson.*;
import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Opt-in preparation for a migrated server: reads it, writes a separate private plan, never starts it. */
public final class JarPlanPreparation {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    // Every path here must be left byte-identical by a JAR update. Others are checked by their own rule.
    private static final List<String> STRICT = List.of("spigot.yml", "bukkit.yml", "paper.jar", "plugins/NPCSpeak/knowledge.yml",
            "plugins/NPCSpeak/config.yml", "plugins/NPCSpeak/messages.yml", "plugins/Citizens/config.yml");
    record Recipe(String root, String jar, String cases, String npcId, String entity, int citizensId, String sourceCommit) {}
    public static void main(String[] args) throws Exception {
        Map<String, String> flags = new HashMap<>();
        for (int i = 0; i + 1 < args.length; i += 2) flags.put(args[i], args[i + 1]);
        prepare(Path.of(flags.get("--recipe")), Path.of(flags.get("--output")));
    }
    static void prepare(Path recipeFile, Path output) throws Exception {
        Recipe recipe = JSON.fromJson(Files.readString(recipeFile), Recipe.class);
        Path root = Path.of(recipe.root()).toRealPath(), jar = Path.of(recipe.jar()).toRealPath();
        output = output.toAbsolutePath().normalize();
        if (output.startsWith(root) || root.startsWith(output) || !recipe.npcId().matches("[a-zA-Z0-9_-]+")
                || recipe.sourceCommit() == null || !recipe.sourceCommit().matches("[a-f0-9]{40}"))
            throw new IllegalArgumentException("Separate private output, NPC id and source commit required");
        Path npc = root.resolve("plugins/NPCSpeak/npcs/" + recipe.npcId() + ".yml");
        Path knowledge = root.resolve("plugins/NPCSpeak/knowledge.yml");
        Path saves = root.resolve("plugins/Citizens/saves.yml");
        List<Path> pluginJars;
        try (var files = Files.list(root.resolve("plugins"))) {
            pluginJars = files.filter(p -> p.getFileName().toString().endsWith(".jar")).sorted().toList();
        }
        var installed = pluginJars.stream().filter(DeploymentPlanPreparation::isNpcSpeak).toList();
        if (installed.size() != 1) throw new IllegalArgumentException("Exactly one original NPCSpeak JAR required");
        Path oldJar = installed.get(0);
        DeploymentPlanPreparation.verifyArtifact(jar);
        String oldJarHash = HybridReadiness.sha256(oldJar), newJarHash = HybridReadiness.sha256(jar);
        if (oldJarHash.equals(newJarHash)) throw new IllegalArgumentException("Candidate is already installed");
        var npcYaml = DeploymentPlanPreparation.yaml(npc);
        var knowledgeYaml = DeploymentPlanPreparation.yaml(knowledge);
        // This path can never perform or repeat the migration, so it requires a finished one.
        if (!npcYaml.contains("knowledge-entity") || !knowledgeYaml.contains("municipal-records"))
            throw new IllegalArgumentException("Unmigrated source requires the three-file migration");
        if (!recipe.entity().equals(npcYaml.getString("knowledge-entity"))) throw new IllegalArgumentException("Knowledge entity mismatch");
        if (npcYaml.getInt("citizens-id", -1) != recipe.citizensId()) throw new IllegalArgumentException("Citizens identity mismatch");
        Files.createDirectory(output); Files.createDirectory(output.resolve("files"));
        Files.copy(jar, output.resolve("files/new.jar"));
        Path checkData = Files.createDirectory(output.resolve("check-data"));
        Files.copy(knowledge, checkData.resolve("knowledge.yml"));
        LoreEngine lore = KnowledgeBase.load(checkData);
        var speaker = new SpeakerContext(recipe.npcId(), npcYaml.getString("display-name", recipe.npcId()), recipe.entity());
        var cases = JSON.fromJson(Files.readString(Path.of(recipe.cases())), HybridPilot.Case[].class);
        if (cases == null || cases.length < 4) throw new IllegalArgumentException("Frozen local checks required");
        for (var item : cases) {
            var plan = new AnswerPlanner().plan(item.query(), speaker, lore, 0);
            if (!"LOCAL".equals(item.expectedRoute()) || !plan.local()
                    || item.expectedFragments().stream().anyMatch(f -> !plan.sourceAnswer().contains(f)))
                throw new IllegalArgumentException("Local deployment check failed: " + item.id());
        }
        boolean freeform = "freeform".equalsIgnoreCase(npcYaml.getString("personality.task.type", "freeform"));
        String copied = HybridReadiness.sha256(output.resolve("files/new.jar"));
        String knowledgeHash = HybridReadiness.sha256(knowledge);
        String marker = HybridReadiness.marker(recipe.npcId(), recipe.entity(), recipe.citizensId(), freeform, lore, knowledgeHash, copied);
        List<Map<String, String>> strict = new ArrayList<>(), npcs = new ArrayList<>();
        for (String path : STRICT) if (Files.isRegularFile(root.resolve(path))) strict.add(protectedFile(root, root.resolve(path)));
        for (Path plugin : pluginJars) if (!plugin.equals(oldJar)) strict.add(protectedFile(root, plugin));
        Path wiki = root.resolve("plugins/NPCSpeak/knowledge/wiki");
        if (Files.isDirectory(wiki)) try (var files = Files.walk(wiki)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) strict.add(protectedFile(root, file));
        }
        try (var files = Files.list(npc.getParent())) {
            for (Path file : files.filter(p -> p.toString().endsWith(".yml")).sorted().toList()) npcs.add(protectedFile(root, file));
        }
        if (!Files.isRegularFile(saves)) throw new IllegalArgumentException("Citizens saves required");
        var inventory = new LinkedHashMap<String, Object>();
        inventory.put("Strict", strict); inventory.put("Npc", npcs); inventory.put("CitizensSaves", "plugins/Citizens/saves.yml");
        write(output.resolve("protected.json"), inventory);
        write(output.resolve("validation.json"), Map.of("Valid", true, "LocalChecks", cases.length, "JarSha256", copied,
                "Marker", marker, "ArtifactClassesVerified", true, "MigratedSource", true));
        var jarEntry = new LinkedHashMap<String, Object>();
        jarEntry.put("Target", root.relativize(oldJar).toString().replace('\\', '/')); jarEntry.put("OriginalSha256", oldJarHash);
        jarEntry.put("Candidate", "files/new.jar"); jarEntry.put("CandidateSha256", copied);
        var manifest = new LinkedHashMap<String, Object>();
        manifest.put("FormatVersion", 1); manifest.put("PlanType", "JarOnly"); manifest.put("ServerRoot", root.toString());
        manifest.put("StartBat", "START.bat"); manifest.put("BatSha256", HybridReadiness.sha256(root.resolve("START.bat")));
        manifest.put("PropertiesFingerprint", propertiesFingerprint(root.resolve("server.properties")));
        manifest.put("CitizensId", recipe.citizensId()); manifest.put("SourceCommit", recipe.sourceCommit());
        manifest.put("Jar", jarEntry); manifest.put("Marker", marker);
        manifest.put("ValidationSha256", HybridReadiness.sha256(output.resolve("validation.json")));
        manifest.put("ProtectedSha256", HybridReadiness.sha256(output.resolve("protected.json")));
        write(output.resolve("deployment-plan.json"), manifest);
        System.out.println("Private JAR-only plan ready; " + cases.length + " local checks, " + (strict.size() + npcs.size())
                + " protected files; no server mutation or model call.");
    }
    private static Map<String, String> protectedFile(Path root, Path file) throws Exception {
        return Map.of("Path", root.relativize(file).toString().replace('\\', '/'), "Sha256", HybridReadiness.sha256(file));
    }
    static String propertiesFingerprint(Path file) throws Exception {
        var lines = Arrays.stream(Files.readString(file).split("\\R")).filter(line -> !line.startsWith("#")).toList();
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
    private static void write(Path file, Object value) throws Exception {
        Files.writeString(file, JSON.toJson(value), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }
}
