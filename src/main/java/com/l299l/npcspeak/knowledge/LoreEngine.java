package com.l299l.npcspeak.knowledge;

import com.google.gson.Gson;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/** Immutable, deterministic lexical index. No Bukkit, I/O, network or model calls on retrieval. */
public final class LoreEngine {
    private static final Gson JSON = new Gson();
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern APOSTROPHE_SUFFIX = Pattern.compile("(?<=[\\p{L}0-9])['’][\\p{L}]+");
    private static final Pattern REDIRECT = Pattern.compile("(?iu)^\\s*(?:#(?:redirect|yönlendirme)\\s+|şuraya yönlendir:\\s*)(?:\\[\\[)?([^\\r\\n]+?)(?:\\]\\])?\\s*$");
    private static final Pattern NAMED_QUOTE = Pattern.compile("[\"“]([^\"”\\r\\n]{2,120})[\"”]\\s+(?:adli|isimli)\\b");
    private static final Set<String> STOP = Set.of("ve", "veya", "ile", "bir", "bu", "su", "o", "ne", "nedir",
            "kim", "kimdir", "kime", "hangi", "nasil", "niye", "neden", "nerede", "var", "mi", "mu", "ben", "sen",
            "biz", "siz", "de", "da", "ki", "icin", "hakkinda", "biraz", "bana", "soyle", "anlat", "misin", "musun",
            "lutfen", "bilgi", "ver", "nedirler", "zaman", "kadar", "olan", "olarak");
    private static final Set<String> GENERIC = Set.of("superseed", "plaza", "avm", "belediye", "sehir", "tarih",
            "tarihi", "bina", "binalar", "merkez", "yer", "kurum", "baskan", "baskani");
    private static final Set<String> SUFFIXES = Set.of("in", "nin", "un", "nun", "a", "e", "ya", "ye", "i", "yi",
            "u", "yu", "ni", "nu", "da", "de", "ta", "te", "dan", "den", "tan", "ten", "yla", "yle", "si", "su",
            "sinin", "sunun", "dir", "tir", "dur", "tur", "daki", "deki", "taki", "teki", "lar", "ler");

    public record Settings(boolean enabled, int maxSnippets, int maxChars, double minScore, double minCoverage) {
        public Settings {
            maxSnippets = Math.max(0, Math.min(8, maxSnippets));
            maxChars = Math.max(600, Math.min(7000, maxChars));
            if (!Double.isFinite(minScore) || !Double.isFinite(minCoverage)) throw new IllegalArgumentException("Non-finite retrieval threshold");
            minScore = Math.max(0, Math.min(1000, minScore));
            minCoverage = Math.max(0.5, Math.min(1, minCoverage));
        }
        public static Settings defaults() { return new Settings(true, 3, 2600, 6, 0.65); }
    }
    public record Article(String title, String source, String text) {}
    public record Alias(String canonical, List<String> names, String note) {
        public Alias { names = List.copyOf(names); }
    }
    public record VerifiedFact(String id, String text, List<String> subjects, List<String> keywords, boolean current) {
        public VerifiedFact { subjects = List.copyOf(subjects); keywords = List.copyOf(keywords); }
    }
    private record Passage(String key, String title, String source, String text, Set<String> terms, int number, String via) {}
    private record Name(String key, String label, List<String> words, boolean alias) {}
    private record Hit(String key, String label, int start, int end, boolean alias, boolean literal) {}
    public record Candidate(String title, String source, int passage, double score, double coverage,
                            List<String> reasons, boolean selected, String passedExcerpt, String viaWikiRedirect) {
        public Candidate { reasons = List.copyOf(reasons); }
    }
    public record Result(String status, String sourceData, List<Candidate> candidates, List<String> entities,
                         int verifiedCount, int wikiCount) {
        public Result { candidates = List.copyOf(candidates); entities = List.copyOf(entities); }
        public boolean hasSources() { return verifiedCount + wikiCount > 0; }
    }

    private final Settings settings;
    private final List<Passage> passages;
    private final List<Name> names;
    private final List<Alias> aliases;
    private final List<VerifiedFact> facts;
    private final Map<String, String> personas;
    private final Map<String, Set<String>> articleTerms;
    private final List<Article> articles;

    public LoreEngine(Settings settings, List<Article> articles, List<Alias> aliases,
                      List<VerifiedFact> facts, Map<String, String> personas) {
        this.settings = settings;
        this.aliases = List.copyOf(aliases);
        this.facts = List.copyOf(facts);
        this.personas = Map.copyOf(personas);
        Map<String, String> owners = new HashMap<>();
        List<Name> indexedNames = new ArrayList<>();
        for (Alias alias : aliases) {
            String key = normalize(alias.canonical());
            for (String label : aliasLabels(alias)) {
                String normalized = normalize(label);
                if (words(label).isEmpty()) throw new IllegalArgumentException("Empty alias");
                String previous = owners.putIfAbsent(normalized, key);
                if (previous != null && !previous.equals(key)) throw new IllegalArgumentException("Ambiguous alias");
                indexedNames.add(new Name(key, label, words(label), true));
            }
        }
        List<Passage> indexed = new ArrayList<>();
        Map<String, Set<String>> termsByArticle = new TreeMap<>();
        List<Article> ordered = articles.stream().sorted(Comparator.comparing(Article::title)
                .thenComparing(Article::source).thenComparing(Article::text)).toList();
        this.articles = List.copyOf(ordered);
        for (Article article : ordered) {
            String titleKey = normalize(article.title());
            String key = owners.getOrDefault(titleKey, titleKey);
            indexedNames.add(new Name(key, article.title(), words(article.title()), owners.containsKey(titleKey)));
            int number = 0;
            int chunkBudget = Math.min(700, Math.max(1, settings.maxChars()
                    - source("WIKI_POSSIBLY_HISTORICAL", article.title(), article.source(), "").length() - 32));
            for (String chunk : chunks(article.text(), chunkBudget)) {
                Set<String> terms = terms(chunk);
                indexed.add(new Passage(key, article.title(), article.source(), chunk, terms, ++number, ""));
                termsByArticle.computeIfAbsent(titleKey, unused -> new TreeSet<>()).addAll(terms);
            }
        }
        // Follow explicit local wiki references, not inferred identity. Never create admin aliases.
        List<Passage> originals = List.copyOf(indexed);
        for (Article origin : ordered) {
            Set<Article> visited = new HashSet<>(); visited.add(origin);
            Article current = origin;
            for (int hop = 0; hop < 3; hop++) {
                Optional<String> target = redirectTarget(current.text());
                if (target.isEmpty()) break;
                List<Article> literal = ordered.stream().filter(article -> article.title().equals(target.get())).toList();
                List<Article> matches = literal.isEmpty() ? ordered.stream()
                        .filter(article -> normalize(article.title()).equals(normalize(target.get()))).toList() : literal;
                if (matches.size() != 1 || !visited.add(matches.get(0))) break;
                current = matches.get(0);
                if (redirectTarget(current.text()).isPresent()) continue;
                String originKey = owners.getOrDefault(normalize(origin.title()), normalize(origin.title()));
                Article resolved = current;
                originals.stream().filter(passage -> passage.title().equals(resolved.title()) && passage.source().equals(resolved.source()))
                        .forEach(passage -> indexed.add(new Passage(originKey, passage.title(), passage.source(), passage.text(),
                                passage.terms(), passage.number(), origin.title())));
                break;
            }
        }
        this.names = List.copyOf(indexedNames);
        this.passages = List.copyOf(indexed);
        this.articleTerms = Collections.unmodifiableMap(termsByArticle);
    }

    public Settings settings() { return settings; }
    List<Article> articles() { return articles; }
    List<Alias> aliases() { return aliases; }
    LoreEngine reindex(List<Article> reordered) { return new LoreEngine(settings, reordered, aliases, facts, personas); }
    Map<String, Long> indexedPassageCounts() {
        Map<String, Long> counts = new TreeMap<>();
        passages.forEach(passage -> counts.merge(passage.title(), 1L, Long::sum));
        return Map.copyOf(counts);
    }
    public String persona(String id) { return settings.enabled() ? personas.getOrDefault(id, "") : ""; }

    public Result retrieve(String question) {
        if (!settings.enabled()) return empty("DISABLED");
        // Bound work on administrator commands as well as player queries.
        String bounded = question == null ? "" : question.substring(0, Math.min(1200, question.length()));
        var quoted = NAMED_QUOTE.matcher(normalize(bounded));
        while (quoted.find()) {
            List<String> named = words(quoted.group(1));
            boolean known = names.stream().anyMatch(name -> name.words().equals(named))
                    || facts.stream().anyMatch(fact -> fact.subjects().stream().anyMatch(subject -> words(subject).equals(named))
                            || !positions(words(fact.text()), named).isEmpty());
            // An explicit unknown name cannot be replaced by a known prefix or incidental body words.
            if (!known) return empty("UNRESOLVED_NAMED_ENTITY");
        }
        List<String> queryWords = words(bounded);
        Set<String> query = new TreeSet<>(queryWords);
        query.removeAll(STOP);
        if (query.isEmpty()) return empty("EMPTY_QUERY");
        List<Hit> hits = new ArrayList<>();
        for (Name name : names) {
            // A common word alone can name its own explicitly indexed article, but cannot
            // act as a broad anchor inside another topic or imply a longer entity name.
            boolean soleLiteralTopic = query.equals(new TreeSet<>(name.words()));
            if (!distinctiveName(name.words()) && !soleLiteralTopic) continue;
            for (int start : positions(queryWords, name.words())) {
                int end = start + name.words().size();
                hits.add(new Hit(name.key(), name.label(), start, end, name.alias(), queryWords.subList(start, end).equals(name.words())));
            }
        }
        // A nested short title (Tech) cannot steal a question about a longer title (Tech Plaza).
        List<Hit> longest = hits.stream().filter(hit -> hits.stream().noneMatch(other ->
                other.start() <= hit.start() && other.end() >= hit.end() &&
                        (other.end() - other.start() > hit.end() - hit.start()
                                || (other.start() == hit.start() && other.end() == hit.end() && other.literal() && !hit.literal())))).toList();
        Set<String> entities = new TreeSet<>();
        Set<String> ambiguous = new HashSet<>();
        for (Hit hit : longest) {
            if (longest.stream().anyMatch(other -> other.start() == hit.start() && other.end() == hit.end() && !other.key().equals(hit.key()))) {
                ambiguous.add(hit.key());
            } else entities.add(hit.key());
        }
        entities.removeAll(ambiguous);
        Set<String> informative = new TreeSet<>(query);
        informative.removeIf(word -> GENERIC.stream().anyMatch(generic -> sameTerm(word, generic)));
        // The entity bonus already establishes the subject. Rank its passages by the asked detail,
        // not by repeated occurrences of the subject's name in unrelated later paragraphs.
        for (Hit hit : longest) if (entities.contains(hit.key())) {
            informative.removeAll(queryWords.subList(hit.start(), hit.end()));
        }

        Map<String, Double> weights = new TreeMap<>();
        Set<String> rareTerms = new HashSet<>();
        for (String term : informative) {
            long frequency = articleTerms.values().stream().filter(terms -> contains(terms, term)).count();
            weights.put(term, 1 + Math.log(1 + (articleTerms.size() + 0.5) / (frequency + 0.5)));
            if (frequency <= Math.max(1, articleTerms.size() / 3)) rareTerms.add(term);
        }
        List<Candidate> ranked = new ArrayList<>();
        for (Passage passage : passages) {
            List<String> reasons = new ArrayList<>();
            boolean direct = entities.contains(passage.key());
            long matched = informative.stream().filter(term -> contains(passage.terms(), term)).count();
            double coverage = informative.isEmpty() ? 0 : (double) matched / informative.size();
            double score = weights.entrySet().stream().filter(entry -> contains(passage.terms(), entry.getKey()))
                    .mapToDouble(Map.Entry::getValue).sum() * 2;
            if (direct) {
                score += 100;
                reasons.add(longest.stream().anyMatch(hit -> hit.key().equals(passage.key()) && hit.alias()) ? "VERIFIED_ALIAS" : "EXACT_TITLE");
                if (!passage.via().isBlank()) reasons.add("WIKI_REDIRECT");
            } else if (!passage.via().isBlank()) {
                reasons.add("REJECT_REDIRECT_WITHOUT_ORIGIN_MATCH");
            } else if (!longest.isEmpty()) {
                reasons.add(entities.isEmpty() ? "REJECT_AMBIGUOUS_ENTITY" : "REJECT_OTHER_ENTITY");
            } else {
                boolean rare = rareTerms.stream().anyMatch(term -> contains(passage.terms(), term));
                if (matched < 2) reasons.add("REJECT_NEEDS_TWO_DISTINCT_TERMS");
                if (coverage < settings.minCoverage()) reasons.add("REJECT_LOW_COVERAGE");
                if (!rare) reasons.add("REJECT_COMMON_TERMS");
                if (reasons.isEmpty()) reasons.add("LEXICAL_COVERAGE_AND_RARITY");
            }
            reasons.add("BODY_MATCHES=" + String.join("/", informative.stream().filter(term -> contains(passage.terms(), term)).toList()));
            reasons.add(String.format(Locale.ROOT, "BODY_SCORE=%.2f", direct ? score - 100 : score));
            if (direct) reasons.add("ENTITY_BONUS=100");
            if (score < settings.minScore()) reasons.add("REJECT_LOW_SCORE");
            ranked.add(new Candidate(passage.title(), passage.source(), passage.number(), score, coverage, reasons, false, passage.text(), passage.via()));
        }
        ranked.sort(Comparator.comparingDouble(Candidate::score).reversed().thenComparing(Candidate::title)
                .thenComparing(Candidate::source).thenComparingInt(Candidate::passage));

        StringBuilder data = new StringBuilder();
        int verifiedCount = 0;
        // Explicitly verified records always receive the context budget before wiki text.
        for (VerifiedFact fact : facts) {
            boolean relevant = fact.subjects().stream().anyMatch(subject -> !positions(queryWords, words(subject)).isEmpty())
                    || fact.keywords().stream().anyMatch(keyword -> !positions(queryWords, words(keyword)).isEmpty())
                    || (fact.subjects().isEmpty() && fact.keywords().isEmpty() && query.stream().anyMatch(term ->
                            term.length() >= 3 && contains(terms(fact.text()), term)));
            if (relevant && append(data, source(fact.current() ? "CURRENT_VERIFIED_RECORD" : "VERIFIED_RECORD",
                    fact.id(), "knowledge.yml", fact.text()), settings.maxChars())) verifiedCount++;
        }
        for (Alias alias : aliases) {
            if (entities.contains(normalize(alias.canonical()))) {
                String text = alias.note().isBlank() ? "Same entity names: " + String.join(", ", aliasLabels(alias)) : alias.note();
                if (append(data, source("VERIFIED_NAME_RELATION", alias.canonical(), "knowledge.yml", text), settings.maxChars())) verifiedCount++;
            }
        }
        int wikiCount = 0;
        List<Candidate> trace = new ArrayList<>();
        Map<String, Integer> perArticle = new HashMap<>();
        Set<String> includedPassages = new HashSet<>();
        for (Candidate candidate : ranked) {
            List<String> reasons = new ArrayList<>(candidate.reasons());
            boolean eligible = reasons.stream().noneMatch(reason -> reason.startsWith("REJECT_"));
            boolean selected = false;
            String identity = candidate.title() + "\u0000" + candidate.source() + "\u0000" + candidate.passedExcerpt();
            if (eligible && includedPassages.contains(identity)) reasons.add("OMIT_DUPLICATE_PASSAGE");
            else if (eligible && wikiCount >= settings.maxSnippets()) reasons.add("OMIT_SNIPPET_LIMIT");
            else if (eligible && perArticle.getOrDefault(candidate.title(), 0) >= 2) reasons.add("OMIT_ARTICLE_LIMIT");
            else if (eligible) {
                selected = append(data, source("WIKI_POSSIBLY_HISTORICAL", candidate.title(), candidate.source(), candidate.passedExcerpt(), candidate.viaWikiRedirect()), settings.maxChars());
                if (selected) {
                    wikiCount++;
                    includedPassages.add(identity);
                    perArticle.merge(candidate.title(), 1, Integer::sum);
                } else reasons.add("OMIT_CONTEXT_BUDGET");
            }
            trace.add(new Candidate(candidate.title(), candidate.source(), candidate.passage(), candidate.score(),
                    candidate.coverage(), reasons, selected, selected ? candidate.passedExcerpt() : "", candidate.viaWikiRedirect()));
        }
        String status = verifiedCount + wikiCount > 0 ? "MATCHED" :
                ranked.stream().anyMatch(candidate -> candidate.reasons().stream().noneMatch(reason -> reason.startsWith("REJECT_")))
                        ? "NO_CONTEXT_ROOM_OR_WIKI_DISABLED" : "NO_RELEVANT_SOURCE";
        return new Result(status, data.toString(), trace, List.copyOf(entities), verifiedCount, wikiCount);
    }

    private static Result empty(String status) { return new Result(status, "", List.of(), List.of(), 0, 0); }
    private static boolean append(StringBuilder target, String text, int max) {
        if (target.length() + text.length() > max) return false;
        target.append(text);
        return true;
    }
    private static String source(String kind, String title, String origin, String text) {
        return source(kind, title, origin, text, "");
    }
    private static String source(String kind, String title, String origin, String text, String via) {
        Map<String, String> object = new LinkedHashMap<>();
        object.put("kind", kind); object.put("title", title); object.put("source", origin); object.put("text", text);
        if (!via.isBlank()) object.put("viaWikiRedirect", via);
        return JSON.toJson(object) + "\n";
    }
    private static List<String> aliasLabels(Alias alias) {
        List<String> labels = new ArrayList<>(); labels.add(alias.canonical()); labels.addAll(alias.names()); return labels;
    }
    static String normalize(String text) {
        return MARKS.matcher(Normalizer.normalize(text.toLowerCase(Locale.forLanguageTag("tr-TR")), Normalizer.Form.NFD))
                .replaceAll("").replace('ı', 'i').replace('_', ' ').strip();
    }
    private static List<String> words(String text) {
        return Arrays.stream(APOSTROPHE_SUFFIX.matcher(normalize(text)).replaceAll("").split("[^a-z0-9]+"))
                .filter(word -> !word.isEmpty()).toList();
    }
    private static Set<String> terms(String text) {
        Set<String> terms = new TreeSet<>(words(text)); terms.removeAll(STOP); return terms;
    }
    private static boolean distinctiveName(List<String> name) {
        return name.size() >= 2 || name.stream().anyMatch(word -> word.length() >= 2 && !GENERIC.contains(word) && !STOP.contains(word));
    }
    private static boolean sameTerm(String query, String source) {
        if (query.equals(source)) return true;
        String shorter = query.length() <= source.length() ? query : source;
        String longer = query.length() > source.length() ? query : source;
        return shorter.length() >= 3 && longer.startsWith(shorter) && SUFFIXES.contains(longer.substring(shorter.length()));
    }
    private static boolean contains(Set<String> haystack, String term) {
        return haystack.stream().anyMatch(word -> sameTerm(word, term));
    }
    private static List<Integer> positions(List<String> query, List<String> name) {
        List<Integer> found = new ArrayList<>();
        if (name.isEmpty()) return found;
        for (int i = 0; i + name.size() <= query.size(); i++) {
            boolean match = true;
            for (int j = 0; j < name.size(); j++) {
                // Only the final word may carry a Turkish case suffix.
                String asked = query.get(i + j);
                String named = name.get(j);
                boolean finalWord = j == name.size() - 1;
                boolean caseSuffix = finalWord && named.length() >= 3 && asked.startsWith(named)
                        && SUFFIXES.contains(asked.substring(named.length()));
                if (!asked.equals(named) && !caseSuffix) {
                    match = false; break;
                }
            }
            if (match) found.add(i);
        }
        return found;
    }

    static List<String> chunks(String text, int max) {
        List<String> chunks = new ArrayList<>();
        // Prefer complete sentences/paragraphs. Never pass half a factual sentence.
        StringBuilder current = new StringBuilder();
        // Extracted infobox fields are complete key=value units. Keep them addressable instead
        // of dropping an entire oversized infobox as one unpunctuated sentence.
        String structured = text.replaceAll("\\|(?=[\\p{L}_][\\p{L}_0-9 ]{0,70}\\s*=)", "\n\n");
        for (String sentence : structured.strip().split("(?<=[.!?])\\s+|\\R\\s*\\R")) {
            String clean = sentence.strip();
            if (clean.isEmpty()) continue;
            if (current.length() + clean.length() + 1 > max && current.length() > 0) {
                chunks.add(current.toString()); current.setLength(0);
            }
            // Oversized unpunctuated statements are omitted, rather than changing their meaning by truncation.
            if (clean.length() > max) continue;
            if (current.length() > 0) current.append('\n');
            current.append(clean);
        }
        if (current.length() > 0) chunks.add(current.toString());
        return chunks;
    }

    static Optional<String> redirectTarget(String text) {
        var match = REDIRECT.matcher(text);
        if (!match.matches()) return Optional.empty();
        String target = match.group(1).split("#", 2)[0].strip();
        return target.isBlank() ? Optional.empty() : Optional.of(target);
    }
}
