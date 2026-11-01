package com.l299l.npcspeak.knowledge;

import com.google.gson.*;
import com.l299l.npcspeak.ai.*;
import com.l299l.npcspeak.config.*;
import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/** Opt-in export/replay of the production hybrid path. Never sends HTTP or loads a server. */
public final class HybridPilot {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    record Case(String id, String query, String expectedRoute, List<String> expectedFragments, String reviewBasis) {}
    public static void main(String[] args) throws Exception {
        Map<String, String> flags = new HashMap<>();
        for (int i = 0; i + 1 < args.length; i += 2) flags.put(args[i], args[i + 1]);
        if ("prepare".equals(flags.get("--mode"))) prepare(flags);
        else if ("replay".equals(flags.get("--mode"))) replay(flags);
        else throw new IllegalArgumentException("Expected prepare or replay; neither mode sends HTTP");
    }
    private static void prepare(Map<String, String> flags) throws Exception {
        Path data = absolute(flags.get("--data")), style = absolute(flags.get("--style"));
        Path casesFile = absolute(flags.get("--cases")), output = absolute(flags.get("--output"));
        if (output.startsWith(data) || data.startsWith(output) || style.startsWith(output) || casesFile.startsWith(output))
            throw new IllegalArgumentException("Separate, new private output required");
        LoreEngine lore = KnowledgeBase.load(data);
        YamlConfiguration yaml = new YamlConfiguration(); yaml.load(style.toFile());
        NpcConfig config = new NpcConfig();
        config.setSystemPrompt(yaml.getString("personality.system-prompt"));
        config.setTopic(yaml.getString("personality.topic")); config.setAvoid(yaml.getString("personality.avoid"));
        config.setKnowledgeEntity(yaml.getString("knowledge-entity"));
        NpcTaskConfig task = new NpcTaskConfig(); task.setType(yaml.getString("personality.task.type", "freeform"));
        task.setGoal(yaml.getString("personality.task.goal")); task.setDifficulty(yaml.getInt("personality.task.difficulty", 3));
        config.setTask(task);
        if (!config.isFreeformConversation()) throw new IllegalArgumentException("Freeform pilot only");
        String npcId = Objects.requireNonNull(flags.get("--npc")), model = Objects.requireNonNull(flags.get("--model"));
        var speaker = new SpeakerContext(npcId, yaml.getString("display-name", npcId), config.getKnowledgeEntity());
        String instructions = config.buildSystemPrompt(speaker.displayName());
        if (instructions.matches("(?s).*%[a-zA-Z0-9_]+%.*")) throw new IllegalArgumentException("PAPI needs an explicit offline policy");
        Case[] cases = JSON.fromJson(Files.readString(casesFile), Case[].class);
        if (cases == null || cases.length < 1 || cases.length > 16) throw new IllegalArgumentException("Small frozen pilot required");
        Set<String> ids = new HashSet<>();
        for (Case item : cases) if (item.id() == null || !item.id().matches("[A-Z][0-9]{2}") || !ids.add(item.id())
                || item.query() == null || item.query().isBlank() || item.expectedFragments() == null
                || !Set.of("LOCAL", "MODEL", "GUARDED").contains(item.expectedRoute())) throw new IllegalArgumentException("Invalid case");
        Files.createDirectory(output); Files.createDirectory(output.resolve("responses"));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Case item : cases) {
            long started = System.nanoTime();
            var plan = new AnswerPlanner().plan(item.query(), speaker, lore, 0, config.isFreeformConversation());
            String route = plan.local() ? "LOCAL" : plan.guarded() ? "GUARDED" : "MODEL";
            if (!route.equals(item.expectedRoute())) throw new IllegalStateException("Frozen route mismatch: " + item.id());
            write(output.resolve(item.id() + ".plan.json"), plan);
            write(output.resolve(item.id() + ".sources.json"), lore.retrieve(item.query()));
            Map<String, Object> row = new LinkedHashMap<>(); row.put("case", item); row.put("route", route);
            if (plan.local()) {
                var answer = new HybridAnswerService().answer(plan, () -> { throw new AssertionError("Local prompt must not be built"); },
                        backend(null, new AtomicInteger()), false, token -> { throw new AssertionError("No local preview"); }).join();
                if (item.expectedFragments().stream().anyMatch(fragment -> !answer.text().contains(fragment)))
                    throw new IllegalStateException("Frozen local facts mismatch: " + item.id());
                write(output.resolve(item.id() + ".outcome.json"), answer);
                row.put("inferenceCalls", 0); row.put("elapsedMs", (System.nanoTime() - started) / 1_000_000.0);
            } else {
                var messages = GroundedPrompt.messages(instructions, lore.persona(npcId), null, List.of(), item.query(), lore, speaker, plan);
                String request = OllamaBackend.encodeRequest(model, messages, false);
                Files.writeString(output.resolve(item.id() + ".request.json"), request, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
                row.put("requestSha256", LoreEvaluation.sha(request.getBytes(StandardCharsets.UTF_8)));
                row.put("wikiCount", lore.retrieve(item.query()).wikiCount());
            }
            rows.add(row);
        }
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("casesSha256", LoreEvaluation.sha(Files.readAllBytes(casesFile)));
        manifest.put("knowledgeSha256", LoreEvaluation.sha(Files.readAllBytes(data.resolve("knowledge.yml"))));
        manifest.put("styleSha256", LoreEvaluation.sha(Files.readAllBytes(style)));
        manifest.put("model", model); manifest.put("speaker", speaker); manifest.put("rows", rows);
        write(output.resolve("manifest.json"), manifest);
        System.out.println("Hibrit pilot hazırlandı; yalnız model/guarded rotalarda istek dosyası var, HTTP gönderilmedi.");
    }
    private static void replay(Map<String, String> flags) throws Exception {
        Path bundle = absolute(flags.get("--output"));
        String id = flags.get("--case");
        if (id == null || !id.matches("[A-Z][0-9]{2}")) throw new IllegalArgumentException("Explicit case required");
        var plan = JSON.fromJson(Files.readString(bundle.resolve(id + ".plan.json")), AnswerPlan.class);
        if (plan.local()) throw new IllegalArgumentException("Local answer must never have a model response");
        JsonObject transport = JsonParser.parseString(Files.readString(bundle.resolve("responses/" + id + ".response.json"))).getAsJsonObject();
        String candidate = transport.getAsJsonObject("Response").getAsJsonObject("message").get("content").getAsString();
        AtomicInteger calls = new AtomicInteger();
        var result = new HybridAnswerService().answer(plan, List::of, backend(candidate, calls), false,
                token -> { throw new AssertionError("Replay does not publish tokens"); }).join();
        if (calls.get() != 1) throw new AssertionError("Replay adapter must be invoked exactly once");
        write(bundle.resolve(id + ".outcome.json"), result);
        System.out.println(id + ": " + result.origin() + "; kayıtlı yanıt üretim katmanından geçirildi, yeni inference yok.");
    }
    private static AiBackend backend(String answer, AtomicInteger calls) {
        return new AiBackend() {
            public CompletableFuture<String> complete(List<AiMessage> messages) {
                calls.incrementAndGet();
                if (answer == null) throw new AssertionError("No backend call allowed");
                return CompletableFuture.completedFuture(answer);
            }
            public String getName() { return "private-recording-replay"; }
        };
    }
    private static Path absolute(String value) {
        if (value == null || !Path.of(value).isAbsolute()) throw new IllegalArgumentException("Explicit absolute private paths required");
        return Path.of(value).normalize();
    }
    private static void write(Path file, Object value) throws Exception {
        Files.writeString(file, JSON.toJson(value), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }
}
