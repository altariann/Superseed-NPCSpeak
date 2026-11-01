package com.l299l.npcspeak.knowledge;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Bukkit lifecycle adapter. Reload builds a complete immutable index before publishing it. */
public final class KnowledgeBase {
    private static JavaPlugin plugin;
    // Fail closed on the first malformed load: enabled grounding with no sources, not ungrounded chat.
    private static volatile LoreEngine engine = new LoreEngine(
            LoreEngine.Settings.defaults(), List.of(), List.of(), List.of(), Map.of());
    private static volatile boolean lastReloadSuccessful = true;

    private KnowledgeBase() {}

    public static synchronized void initialize(JavaPlugin owner) {
        plugin = owner;
        if (!Files.exists(owner.getDataFolder().toPath().resolve("knowledge.yml"))) owner.saveResource("knowledge.yml", false);
        try { Files.createDirectories(owner.getDataFolder().toPath().resolve("knowledge/wiki")); }
        catch (IOException error) { owner.getLogger().warning("Cannot create lore directory; existing files will be left unchanged."); }
        reload();
    }

    public static synchronized void reload() {
        if (plugin != null) reloadFrom(plugin.getDataFolder().toPath());
    }

    // Package-private entry point also exercises real file/YAML reload behavior in unit tests.
    static synchronized boolean reloadFrom(Path root) {
        try {
            LoreEngine loaded = load(root);
            engine = loaded;
            lastReloadSuccessful = true;
            if (plugin != null) plugin.getLogger().info("Lore index reloaded; enabled=" + loaded.settings().enabled());
            return true;
        } catch (Exception error) {
            lastReloadSuccessful = false;
            // Never echo YAML contents, reference text or player questions in a log.
            if (plugin != null) plugin.getLogger().warning("Lore reload failed (" + error.getClass().getSimpleName()
                    + "); previous lore index retained. Check knowledge.yml and wiki file access.");
            return false;
        }
    }

    public static boolean wasLastReloadSuccessful() { return lastReloadSuccessful; }
    public static LoreEngine snapshot() { return engine; }
    public static LoreEngine.Result retrieve(String question) { return engine.retrieve(question); }

    /** Compatibility entry point; snapshot() provides an atomic persona/retrieval pair. */
    public static String context(String npcId, String playerText) {
        LoreEngine state = engine;
        if (!state.settings().enabled()) return "";
        String persona = state.persona(npcId);
        return (persona.isBlank() ? "" : "\nNPC_STYLE (style only):\n" + persona + "\n")
                + GroundedPrompt.referenceSection(state.retrieve(playerText));
    }

    static LoreEngine load(Path root) throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        // Entity labels may contain dots. Bukkit's default dot path separator would split them.
        config.options().pathSeparator('\u001f');
        config.load(root.resolve("knowledge.yml").toFile());
        for (String section : List.of("retrieval", "characters", "aliases")) {
            if (config.contains(section) && !config.isConfigurationSection(section)) throw new IllegalArgumentException("Invalid configuration section");
        }
        if (config.contains("municipality") && !config.isString("municipality")) throw new IllegalArgumentException("Invalid municipality text");
        if (config.contains("enabled") && !config.isBoolean("enabled")) throw new IllegalArgumentException("Invalid boolean setting");
        for (String path : List.of("retrieval.max-snippets", "retrieval.max-context-chars")) {
            if (config.contains(configPath(path)) && !config.isInt(configPath(path))) throw new IllegalArgumentException("Invalid integer setting");
        }
        for (String path : List.of("retrieval.min-score", "retrieval.min-query-coverage")) {
            if (config.contains(configPath(path)) && !(config.get(configPath(path)) instanceof Number)) throw new IllegalArgumentException("Invalid threshold setting");
        }
        LoreEngine.Settings settings = new LoreEngine.Settings(config.getBoolean("enabled", true),
                config.getInt(configPath("retrieval.max-snippets"), 3), config.getInt(configPath("retrieval.max-context-chars"), 2600),
                config.getDouble(configPath("retrieval.min-score"), 6), config.getDouble(configPath("retrieval.min-query-coverage"), 0.65));
        Map<String, String> personas = new TreeMap<>();
        ConfigurationSection characters = config.getConfigurationSection("characters");
        if (characters != null) characters.getValues(false).forEach((key, value) -> {
            if (!(value instanceof String text)) throw new IllegalArgumentException("Invalid character text");
            personas.put(key, text);
        });
        List<LoreEngine.Alias> aliases = new ArrayList<>();
        ConfigurationSection aliasSection = config.getConfigurationSection("aliases");
        if (aliasSection != null) for (var entry : new TreeMap<>(aliasSection.getValues(false)).entrySet()) {
            if (!(entry.getValue() instanceof ConfigurationSection section)) throw new IllegalArgumentException("Invalid alias section");
            aliases.add(new LoreEngine.Alias(entry.getKey(), stringList(section.get("names")), section.getString("note", "")));
        }
        List<LoreEngine.VerifiedFact> facts = new ArrayList<>();
        Object municipalRecords = config.get("municipal-records");
        if (municipalRecords != null) {
            if (!(municipalRecords instanceof List<?> list) || list.isEmpty()) throw new IllegalArgumentException("Nonempty municipal-records required");
            Set<String> ids = new HashSet<>();
            for (Object raw : list) {
                if (!(raw instanceof Map<?, ?> record) || !(record.get("id") instanceof String id) || id.isBlank() || !ids.add(id)
                        || !(record.get("text") instanceof String text) || text.isBlank()
                        || !(record.get("type") instanceof String type) || !Set.of("office", "relationship", "fact").contains(type)
                        || !(record.get("current") instanceof Boolean current)) throw new IllegalArgumentException("Invalid municipal record");
                List<String> subjects = stringList(record.get("subjects"));
                List<String> offices = stringList(record.get("offices"));
                if (subjects.isEmpty() || subjects.stream().anyMatch(String::isBlank)
                        || subjects.stream().map(LoreEngine::normalize).distinct().count() != subjects.size()
                        || !type.equals("relationship") && subjects.size() != 1
                        || type.equals("office") && offices.isEmpty()
                        || type.equals("relationship") && subjects.size() != 2
                        || !type.equals("office") && !offices.isEmpty() || offices.stream().anyMatch(String::isBlank)) {
                    throw new IllegalArgumentException("Invalid municipal participants or offices");
                }
                String relation = "", from = "", to = "";
                if (record.containsKey("relation") || record.containsKey("from") || record.containsKey("to")) {
                    if (!type.equals("relationship") || !"reports_to".equals(record.get("relation"))
                            || !(record.get("from") instanceof String source) || source.isBlank()
                            || !(record.get("to") instanceof String target) || target.isBlank()
                            || LoreEngine.normalize(source).equals(LoreEngine.normalize(target))) {
                        throw new IllegalArgumentException("Invalid directed municipal relationship");
                    }
                    relation = "reports_to"; from = source.strip(); to = target.strip();
                    Set<String> participants = new HashSet<>();
                    subjects.forEach(subject -> participants.add(LoreEngine.normalize(subject)));
                    if (!participants.equals(Set.of(LoreEngine.normalize(from), LoreEngine.normalize(to)))) {
                        throw new IllegalArgumentException("Relationship endpoints must match subjects");
                    }
                }
                facts.add(new LoreEngine.VerifiedFact(id, text.strip(), subjects, stringList(record.get("keywords")),
                        current, type, offices, true, relation, from, to));
            }
        }
        String municipality = config.getString("municipality", "");
        int municipalityPart = 0;
        // Explicit migration: keep the legacy field readable, but never mix its broad text into atomic records.
        for (String chunk : LoreEngine.chunks(municipalRecords == null ? municipality : "", 350)) {
            facts.add(new LoreEngine.VerifiedFact("municipality-" + ++municipalityPart, chunk, List.of(), List.of(), true));
        }
        Object rawFacts = config.get("verified-facts");
        if (rawFacts != null) {
            if (!(rawFacts instanceof List<?> list)) throw new IllegalArgumentException("Invalid verified-facts list");
            int index = 0;
            for (Object raw : list) {
                if (!(raw instanceof Map<?, ?> fact) || !(fact.get("text") instanceof String text) || text.isBlank()) {
                    throw new IllegalArgumentException("Invalid verified fact");
                }
                if (fact.containsKey("current") && !(fact.get("current") instanceof Boolean)) throw new IllegalArgumentException("Invalid current flag");
                Object id = fact.get("id");
                facts.add(new LoreEngine.VerifiedFact(id == null ? "record-" + ++index : id.toString(), text.strip(),
                        stringList(fact.get("subjects")), stringList(fact.get("keywords")), !Boolean.FALSE.equals(fact.get("current"))));
            }
        }
        return new LoreEngine(settings, readArticles(root.resolve("knowledge/wiki")), aliases, facts, personas);
    }

    static List<LoreEngine.Article> readArticles(Path wiki) throws IOException {
        List<LoreEngine.Article> articles = new ArrayList<>();
        if (Files.exists(wiki)) try (var files = Files.list(wiki)) {
            for (Path file : files.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".txt")).sorted().toList()) {
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > 160_000) {
                    if (plugin != null) plugin.getLogger().warning("Skipped non-regular or oversized wiki file.");
                    continue;
                }
                String raw = Files.readString(file, StandardCharsets.UTF_8).replaceFirst("^\\uFEFF", "");
                String title = file.getFileName().toString().replace('_', ' ').replaceFirst("(?i)\\.txt$", "");
                String source = "";
                List<String> body = new ArrayList<>();
                boolean header = true;
                for (String line : raw.split("\\R", -1)) {
                    if (header && line.startsWith("Title: ")) title = line.substring(7).strip();
                    else if (header && line.startsWith("Source: ")) source = line.substring(8).strip();
                    else {
                        body.add(line);
                        if (!line.isBlank()) header = false;
                    }
                }
                String text = String.join("\n", body).strip();
                if (!text.isBlank()) articles.add(new LoreEngine.Article(title, source, text));
            }
        }
        return List.copyOf(articles);
    }

    private static List<String> stringList(Object value) {
        if (value == null) return List.of();
        if (!(value instanceof List<?> list) || list.stream().anyMatch(item -> !(item instanceof String))) {
            throw new IllegalArgumentException("Expected list of strings");
        }
        return list.stream().map(Object::toString).toList();
    }

    private static String configPath(String path) { return path.replace('.', '\u001f'); }
}
