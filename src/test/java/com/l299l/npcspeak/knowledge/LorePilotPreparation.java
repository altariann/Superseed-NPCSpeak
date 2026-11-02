package com.l299l.npcspeak.knowledge;

import com.google.gson.*;
import com.l299l.npcspeak.ai.OllamaBackend;
import com.l299l.npcspeak.config.NpcConfig;
import com.l299l.npcspeak.config.NpcTaskConfig;
import org.bukkit.configuration.file.YamlConfiguration;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Exports local-only requests. Does not load NPC entities, player data or make model calls. */
public final class LorePilotPreparation {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    record PilotCase(String id, String query, String memory, String personaAppend, List<String> expectedFacts,
                     boolean expectAbstention, String basis) {}

    public static void main(String[] args) throws Exception {
        Map<String, String> flags = new HashMap<>();
        for (int i = 0; i + 1 < args.length; i += 2) flags.put(args[i], args[i + 1]);
        Path data = absolute(flags.get("--data")), cases = absolute(flags.get("--cases"));
        Path style = absolute(flags.get("--style")), output = absolute(flags.get("--output"));
        String npcId = flags.get("--npc"), model = flags.get("--model");
        if (npcId == null || npcId.isBlank() || model == null || model.isBlank()) throw new IllegalArgumentException("Explicit NPC and model required");
        if (output.startsWith(data) || data.startsWith(output)) throw new IllegalArgumentException("Separate private output required");
        LoreEngine engine = KnowledgeBase.load(data);
        YamlConfiguration yaml = new YamlConfiguration(); yaml.load(style.toFile());
        if (!"freeform".equals(yaml.getString("personality.task.type", "freeform"))) throw new IllegalArgumentException("Pilot supports freeform NPCs only");
        NpcConfig config = new NpcConfig();
        config.setSystemPrompt(yaml.getString("personality.system-prompt"));
        config.setTopic(yaml.getString("personality.topic")); config.setAvoid(yaml.getString("personality.avoid"));
        NpcTaskConfig task = new NpcTaskConfig(); task.setType("freeform");
        task.setGoal(yaml.getString("personality.task.goal"));
        task.setDifficulty(yaml.getInt("personality.task.difficulty", 3));
        config.setTask(task);
        String instructions = config.buildSystemPrompt(yaml.getString("display-name", npcId));
        if (instructions.matches("(?s).*%[a-zA-Z0-9_]+%.*")) throw new IllegalArgumentException("PAPI placeholders need an explicit offline test policy");
        PilotCase[] inputs = JSON.fromJson(LoreEvaluation.decode(Files.readAllBytes(cases)), PilotCase[].class);
        if (inputs == null || inputs.length != 10) throw new IllegalArgumentException("Exactly ten frozen pilot cases required");
        Set<String> ids = new HashSet<>();
        for (PilotCase item : inputs) if (item.id() == null || !item.id().matches("B[0-9]{2}") || !ids.add(item.id())
                || item.query() == null || item.query().isBlank() || item.expectedFacts() == null) throw new IllegalArgumentException("Invalid pilot case");
        Files.createDirectory(output);
        Files.createDirectory(output.resolve("responses"));
        List<Map<String, Object>> entries = new ArrayList<>();
        for (PilotCase item : inputs) {
            var result = engine.retrieve(item.query());
            String persona = engine.persona(npcId) + (item.personaAppend() == null ? "" : "\n" + item.personaAppend());
            var messages = GroundedPrompt.messages(instructions, persona, item.memory(), List.of(), item.query(), engine);
            String request = OllamaBackend.encodeRequest(model, messages, false);
            Files.writeString(output.resolve(item.id() + ".request.json"), request, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            Files.writeString(output.resolve(item.id() + ".sources.json"), JSON.toJson(result), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("case", item); entry.put("requestSha256", LoreEvaluation.sha(request.getBytes(StandardCharsets.UTF_8)));
            entry.put("systemChars", messages.get(0).content().length()); entry.put("sourceChars", result.sourceData().length());
            entry.put("wikiCount", result.wikiCount()); entry.put("verifiedCount", result.verifiedCount());
            entries.add(entry);
        }
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("model", model); manifest.put("npcId", npcId); manifest.put("casesSha256", LoreEvaluation.sha(Files.readAllBytes(cases)));
        manifest.put("knowledgeSha256", LoreEvaluation.sha(Files.readAllBytes(data.resolve("knowledge.yml"))));
        manifest.put("styleFileSha256", LoreEvaluation.sha(Files.readAllBytes(style))); manifest.put("additionalLorePersonaFound", !engine.persona(npcId).isBlank());
        manifest.put("playerMemoryUsed", false); manifest.put("papiExpansionUsed", false); manifest.put("cases", entries);
        Files.writeString(output.resolve("manifest.json"), JSON.toJson(manifest), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        System.out.println("10 özel pilot isteği hazırlandı; model çağrısı yapılmadı.");
    }
    private static Path absolute(String value) {
        if (value == null || value.isBlank() || !Path.of(value).isAbsolute()) throw new IllegalArgumentException("Explicit absolute paths required");
        return Path.of(value).normalize();
    }
}
