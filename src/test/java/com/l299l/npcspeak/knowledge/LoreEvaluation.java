package com.l299l.npcspeak.knowledge;

import com.google.gson.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

/** Opt-in CLI over the real production loader/index. No HTTP, Bukkit server, or model calls. */
public final class LoreEvaluation {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Gson LINE = new Gson();
    static final Set<String> REVIEWS = Set.of("HUMAN_VERIFIED", "SOURCE_REVIEWED_BY_ASSISTANT", "GENERATED_UNREVIEWED");
    record Case(String id, String group, String query, String reviewStatus, List<String> allowedTitles,
                List<String> requiredTitles, boolean noSources, List<String> requiredEvidence,
                List<String> requiredKinds, List<String> forbiddenKinds, String evidenceFile, String evidenceQuote) {}

    public static void main(String[] args) throws Exception {
        Map<String, String> flags = new HashMap<>();
        for (int i = 0; i + 1 < args.length; i += 2) flags.put(args[i], args[i + 1]);
        Path data = absolute(flags.get("--data"));
        Path output = absolute(flags.get("--output"));
        if (output.startsWith(data) || data.startsWith(output)) throw new IllegalArgumentException("Data and reports must be separate directories");
        Files.createDirectory(output); // never overwrite an earlier run
        if ("inventory".equals(flags.get("--mode"))) inventory(data, output);
        else if ("run".equals(flags.get("--mode"))) evaluate(data, absolute(flags.get("--cases")), output);
        else throw new IllegalArgumentException("Expected inventory or run mode");
    }

    private static Path absolute(String value) {
        if (value == null || value.isBlank() || !Path.of(value).isAbsolute()) throw new IllegalArgumentException("Explicit absolute private paths required");
        return Path.of(value).normalize();
    }

    static String compact(String text) { return text.replaceAll("\\s+", " ").strip(); }
    static String sha(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    static String decode(byte[] bytes) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString().replaceFirst("^\\uFEFF", "");
    }
    private static void write(Path file, Object value) throws IOException {
        Files.writeString(file, JSON.toJson(value), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }

    static void inventory(Path data, Path output) throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, List<String>> contentHashes = new TreeMap<>();
        Map<String, List<String>> titles = new TreeMap<>();
        Map<String, List<String>> urls = new TreeMap<>();
        int utf8Errors = 0;
        try (var files = Files.list(data.resolve("knowledge/wiki"))) {
            for (Path file : files.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".txt")).sorted().toList()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("file", file.getFileName().toString());
                byte[] bytes = Files.readAllBytes(file);
                row.put("bytes", bytes.length); row.put("sha256", sha(bytes));
                row.put("oversized", bytes.length > 160_000);
                try {
                    String raw = decode(bytes);
                    String title = raw.lines().filter(line -> line.startsWith("Title: ")).findFirst().orElse("Title: " + file.getFileName()).substring(7).strip();
                    String url = raw.lines().filter(line -> line.startsWith("Source: ")).findFirst().orElse("Source: ").substring(8).strip();
                    row.put("validUtf8", true); row.put("replacementCharacter", raw.indexOf('\ufffd') >= 0);
                    row.put("suspiciousMojibake", raw.matches("(?s).*(?:Ã.|Ä±|ÅŸ|â€).*"));
                    row.put("title", title); row.put("source", url);
                    row.put("titleHeaderCount", raw.lines().filter(line -> line.startsWith("Title: ")).count());
                    row.put("empty", raw.isBlank());
                    titles.computeIfAbsent(LoreEngine.normalize(title), unused -> new ArrayList<>()).add(file.getFileName().toString());
                    if (!url.isBlank()) urls.computeIfAbsent(url, unused -> new ArrayList<>()).add(file.getFileName().toString());
                    String body = raw.replaceFirst("(?s)^(?:Title: [^\\r\\n]*\\R|Source: [^\\r\\n]*\\R|\\s*\\R)+", "").strip();
                    contentHashes.computeIfAbsent(sha(body.getBytes(StandardCharsets.UTF_8)), unused -> new ArrayList<>()).add(file.getFileName().toString());
                    row.put("bodyChars", body.length());
                } catch (CharacterCodingException error) { row.put("validUtf8", false); utf8Errors++; }
                rows.add(row);
            }
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("files", rows); report.put("fileCount", rows.size()); report.put("utf8Errors", utf8Errors);
        report.put("duplicateNormalizedTitles", duplicates(titles));
        report.put("duplicateBodies", duplicates(contentHashes)); report.put("duplicateSourceUrls", duplicates(urls));
        try {
            LoreEngine engine = KnowledgeBase.load(data);
            report.put("configurationLoaded", true); report.put("aliasGroups", engine.aliases().size());
            report.put("articleCount", engine.articles().size());
            report.put("passageCounts", engine.indexedPassageCounts());
            report.put("articlesWithoutPassages", engine.articles().stream().map(LoreEngine.Article::title)
                    .filter(title -> !engine.indexedPassageCounts().containsKey(title)).toList());
            List<Map<String, Object>> redirects = new ArrayList<>();
            for (var article : engine.articles()) LoreEngine.redirectTarget(article.text()).ifPresent(target -> {
                long exact = engine.articles().stream().filter(other -> other.title().equals(target)).count();
                long folded = engine.articles().stream().filter(other -> LoreEngine.normalize(other.title()).equals(LoreEngine.normalize(target))).count();
                redirects.add(Map.of("title", article.title(), "target", target, "literalMatches", exact, "normalizedMatches", folded));
            });
            report.put("redirects", redirects);
        } catch (Exception error) {
            report.put("configurationLoaded", false); report.put("loadError", error.getClass().getSimpleName());
        }
        write(output.resolve("inventory.json"), report);
        System.out.println("Private inventory written; files=" + rows.size() + ", invalid UTF-8=" + utf8Errors);
    }

    private static Map<String, List<String>> duplicates(Map<String, List<String>> values) {
        return values.entrySet().stream().filter(entry -> entry.getValue().size() > 1)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (left, right) -> left, TreeMap::new));
    }

    static List<Case> loadCases(Path file, Path data, Set<String> titles) throws Exception {
        Case[] array = LINE.fromJson(decode(Files.readAllBytes(file)), Case[].class);
        if (array == null || array.length == 0) throw new IllegalArgumentException("No cases");
        Set<String> ids = new HashSet<>();
        for (Case item : array) {
            if (item.id() == null || !ids.add(item.id()) || item.query() == null || item.query().isBlank()
                    || item.query().length() > 1200 || !REVIEWS.contains(item.reviewStatus())) throw new IllegalArgumentException("Invalid case metadata");
            if (item.allowedTitles() == null || item.requiredTitles() == null || item.requiredEvidence() == null
                    || item.requiredKinds() == null || item.forbiddenKinds() == null) throw new IllegalArgumentException("Explicit expectation lists required");
            if (!titles.containsAll(item.allowedTitles()) || !titles.containsAll(item.requiredTitles())) throw new IllegalArgumentException("Expected title missing from actual corpus");
            if (!item.evidenceFile().isBlank()) {
                Path evidence = data.resolve(item.evidenceFile()).normalize();
                if (!evidence.startsWith(data) || item.evidenceQuote().isBlank()
                        || !compact(decode(Files.readAllBytes(evidence))).contains(compact(item.evidenceQuote()))) {
                    throw new IllegalArgumentException("Source evidence does not match the frozen corpus");
                }
            }
        }
        return List.of(array);
    }

    static List<String> failures(Case item, LoreEngine.Result result, int maxChars) {
        List<String> failures = new ArrayList<>();
        Set<String> selected = result.candidates().stream().filter(LoreEngine.Candidate::selected).map(LoreEngine.Candidate::title).collect(Collectors.toSet());
        if (item.noSources() && result.hasSources()) failures.add("UNEXPECTED_SOURCE");
        if (!item.allowedTitles().isEmpty() && selected.isEmpty()) failures.add("MISSING_WIKI_SOURCE");
        if (!item.allowedTitles().isEmpty() && !new HashSet<>(item.allowedTitles()).containsAll(selected)) failures.add("WRONG_WIKI_ENTITY");
        if (!selected.containsAll(item.requiredTitles())) failures.add("REQUIRED_WIKI_MISSING");
        List<String> texts = new ArrayList<>();
        Set<String> kinds = new HashSet<>();
        try {
            result.sourceData().lines().filter(line -> !line.isBlank()).forEach(line -> {
                JsonObject object = JsonParser.parseString(line).getAsJsonObject();
                texts.add(compact(object.get("text").getAsString())); kinds.add(object.get("kind").getAsString());
            });
        } catch (Exception error) { failures.add("INVALID_SOURCE_JSON"); }
        for (String quote : item.requiredEvidence()) if (texts.stream().noneMatch(text -> text.contains(compact(quote)))) failures.add("REQUIRED_EVIDENCE_MISSING");
        if (!kinds.containsAll(item.requiredKinds())) failures.add("REQUIRED_KIND_MISSING");
        if (item.forbiddenKinds().stream().anyMatch(kinds::contains)) failures.add("FORBIDDEN_KIND");
        if (result.sourceData().length() > maxChars) failures.add("CONTEXT_OVER_BUDGET");
        if (!GroundedPrompt.referenceSection(result).contains(GroundedPrompt.RULES)) failures.add("GROUNDING_RULES_CUT");
        return List.copyOf(failures);
    }

    static void evaluate(Path data, Path caseFile, Path output) throws Exception {
        LoreEngine engine = KnowledgeBase.load(data);
        Set<String> titles = engine.articles().stream().map(LoreEngine.Article::title).collect(Collectors.toSet());
        List<Case> cases = loadCases(caseFile, data, titles); // expectations frozen before the first retrieve call
        List<LoreEngine.Article> reversed = new ArrayList<>(engine.articles()); Collections.reverse(reversed);
        LoreEngine alternative = engine.reindex(reversed);
        Map<String, int[]> groups = new TreeMap<>(), reviews = new TreeMap<>();
        List<Double> millis = new ArrayList<>();
        int passed = 0, deterministic = 0;
        for (Case item : cases.subList(0, Math.min(15, cases.size()))) engine.retrieve(item.query());
        try (var writer = Files.newBufferedWriter(output.resolve("results.jsonl"), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) {
            for (Case item : cases) {
                long start = System.nanoTime();
                LoreEngine.Result result = engine.retrieve(item.query());
                double elapsed = (System.nanoTime() - start) / 1_000_000d; millis.add(elapsed);
                List<String> issues = new ArrayList<>(failures(item, result, engine.settings().maxChars()));
                if (result.equals(alternative.retrieve(item.query()))) deterministic++;
                else issues.add("LOAD_ORDER_CHANGED_RESULT");
                boolean ok = issues.isEmpty(); if (ok) passed++;
                int[] group = groups.computeIfAbsent(item.group(), unused -> new int[2]); group[0]++; if (ok) group[1]++;
                int[] review = reviews.computeIfAbsent(item.reviewStatus(), unused -> new int[2]); review[0]++; if (ok) review[1]++;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("case", item); row.put("passed", ok); row.put("failures", issues); row.put("elapsedMillis", elapsed); row.put("result", result);
                writer.write(LINE.toJson(row)); writer.newLine();
            }
        }
        Collections.sort(millis);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("cases", cases.size()); summary.put("passed", passed); summary.put("failed", cases.size() - passed);
        summary.put("agreementPercent", 100d * passed / cases.size()); summary.put("humanVerified", reviews.getOrDefault("HUMAN_VERIFIED", new int[2])[0]);
        summary.put("reviewCountsAndPasses", reviews); summary.put("groupCountsAndPasses", groups);
        summary.put("deterministic", deterministic); summary.put("p50Millis", millis.get((millis.size() - 1) / 2));
        summary.put("p95Millis", millis.get(Math.min(millis.size() - 1, (int) Math.ceil(millis.size() * .95) - 1)));
        summary.put("casesSha256", sha(Files.readAllBytes(caseFile)));
        summary.put("settings", engine.settings());
        try (var stream = LoreEngine.class.getResourceAsStream("LoreEngine.class")) { summary.put("engineClassSha256", sha(Objects.requireNonNull(stream).readAllBytes())); }
        summary.put("meaning", "Agreement with labelled expectations, NOT human-verified accuracy unless explicitly HUMAN_VERIFIED.");
        write(output.resolve("summary.json"), summary);
        System.out.println("Private evaluation complete: " + passed + "/" + cases.size() + "; human-verified=" + summary.get("humanVerified"));
    }
}
