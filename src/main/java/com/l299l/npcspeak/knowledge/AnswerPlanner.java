package com.l299l.npcspeak.knowledge;

import java.util.*;
import java.util.regex.Pattern;
import static com.l299l.npcspeak.knowledge.QueryFrame.Intent.*;

/** Whole-clause matching only. Retrieval scores never authorize an answer. */
public final class AnswerPlanner {
    private static final Pattern OFFICE_QUESTION = Pattern.compile("(.+?) (?:belediyede )?(?:hangi gorevde|hangi makamda|hangi gorevlerde bulunuyor|hangi gorevde bulunuyor|gorevleri neler|gorevleri nedir|makamlari neler)");
    private static final Pattern TO = Pattern.compile("(.+?) kime bagli(?:dir|sin|siniz)?");
    private static final Pattern FROM = Pattern.compile("(.+?) kim bagli(?:dir)?");
    private static final Pattern HOLDER = Pattern.compile("(.+?) kim(?:dir)?");
    private static final Pattern NAME_QUESTION = Pattern.compile("(.+?) (?:adi|ismi) (?:nedir|ne|kimdir|kim)");
    private static final Pattern REPORT_CHECK = Pattern.compile("(.+) bagli (?:mi|midir|misin|misiniz)");
    private static final Pattern CLAUSE = Pattern.compile("[?!.;,]+\\s*");
    private static final Pattern OPINION = Pattern.compile("(?:sence|sizce) .+|.*\\b(?:iyi|kotu|basarili|basarisiz|guvenilir|yeterli)\\b.*\\bmi\\b.*"
            + "|.*(?:ne dusunuyorsun|ne dusunuyorsunuz|ne dersin|ne dersiniz|yorumun ne|yorumunuz ne|begeniyor musun|seviyor musun).*");
    private static final Set<String> RESERVED = Set.of("ve", "ile", "ama", "neden", "nicin", "nasil", "sence", "gore", "bugun", "eskiden", "eski", "gecmiste", "tarihsel", "yilinda", "degil", "ben", "benim", "onceden", "yarin", "gelecekte", "gelecek", "once", "sonra", "onceki", "sonraki", "ilk", "yeni", "gecmis", "sabik");
    private static final Set<String> SOCIAL_QUESTIONS = Set.of("nasilsin", "bugun nasilsin", "sen nasilsin", "peki sen nasilsin", "merhaba", "selam");
    private static final Set<String> SELF_WORDS = Set.of("sen", "senin", "siz", "sizin", "sana", "size");
    // Short openers and forms of address carry no question of their own. "baskanim" addresses the narrator, never an office.
    private static final List<String> LEADS = List.of("peki o zaman", "sayin baskanim", "tesekkur ederim", "iyi gunler", "affedersiniz", "haklisiniz", "tesekkurler",
            "anliyorum", "baskanim", "haklisin", "o zaman", "sag olun", "efendim", "anladim", "merhaba", "pardon", "sagolun", "sag ol", "sagol", "tamam", "dogru", "selam", "peki", "evet");
    private static final Set<String> BARE_MAYOR = Set.of("baskan", "baskani", "baskanin", "baskaninin", "baskanimiz", "baskanimizin", "belediye baskanimiz", "belediye baskanimizin");
    private static final List<String> INTROS = List.of("", "Hemen söyleyeyim: ", "Kısaca: ");
    private static final String UNKNOWN_REPORTING = "Bu bağlılığa ilişkin doğrulanmış güncel kaydım yok.";
    /** Follows a fixed answer when the rest of the turn cannot be separated reliably. */
    static final String INVITATION = "Bunun yorum kısmını ayrı bir soru olarak sorarsan onu da konuşabiliriz.";

    private record Clause(String text, AnswerPlan fact) {}

    public AnswerPlan plan(String question, SpeakerContext speaker, LoreEngine lore, int styleVariant, boolean freeform) {
        return freeform ? plan(question, speaker, lore, styleVariant) : AnswerPlan.chat();
    }

    public AnswerPlan plan(String question, SpeakerContext speaker, LoreEngine lore, int styleVariant) {
        if (!lore.settings().enabled() || lore.municipalRecords().isEmpty() || question == null || question.length() > 500) return AnswerPlan.chat();
        String q = clean(question);
        if (q.contains("\"") || q.matches(".*\\b[12][0-9]{3}\\b.*")) return AnswerPlan.chat();
        // Whole clauses only: a turn is never reduced to its first matching keyword, and a
        // recognized fact is answered from records whatever else shares the message.
        List<Clause> facts = new ArrayList<>();
        List<String> social = new ArrayList<>(), opinions = new ArrayList<>(), other = new ArrayList<>();
        var clauses = clauses(q, speaker, lore, styleVariant);
        for (Clause clause : clauses) {
            if (clause.fact() != null) facts.add(clause);
            else if (SOCIAL_QUESTIONS.contains(clause.text())) social.add(clause.text());
            else if (OPINION.matcher(clause.text()).matches()) opinions.add(clause.text());
            else other.add(clause.text());
        }
        if (facts.isEmpty()) return AnswerPlan.chat();
        var first = facts.get(0).fact();
        if (clauses.size() == 1) return first;
        var frame = first.query();
        if (frame.intent() == CLARIFY && social.isEmpty()) return first;
        String source = String.join(" ", facts.stream().map(f -> f.fact().sourceAnswer()).distinct().toList());
        List<String> evidence = facts.stream().flatMap(f -> f.fact().evidenceIds().stream()).distinct().toList();
        if (facts.size() == clauses.size()) return new AnswerPlan(frame, source, evidence);
        if (other.isEmpty() && social.isEmpty() && opinions.size() == 1) {
            String clause = original(question, opinions.get(0));
            return new AnswerPlan(new QueryFrame(frame.intent(), frame.subject(), frame.self(), clause), source, evidence, blockedTerms(lore, clause));
        }
        if (other.isEmpty() && opinions.isEmpty()) {
            return new AnswerPlan(new QueryFrame(frame.intent(), frame.subject(), frame.self(), String.join(" ", social)), source, evidence);
        }
        return new AnswerPlan(frame, source + " " + INVITATION, evidence);
    }

    private List<Clause> clauses(String q, SpeakerContext speaker, LoreEngine lore, int styleVariant) {
        List<Clause> result = new ArrayList<>();
        for (String raw : CLAUSE.split(q)) {
            String clause = withoutLeads(raw.strip());
            if (clause.isEmpty() || LEADS.contains(clause)) continue;
            String[] parts = clause.split(" ve ");
            List<Clause> split = new ArrayList<>();
            for (String part : parts) split.add(classify(withoutLeads(part.strip()), speaker, lore, result.isEmpty() && split.isEmpty() ? styleVariant : 0));
            // "X ve Y hangi görevde" has one verb: joining words split only between complete clauses.
            boolean complete = split.stream().allMatch(p -> p.fact() != null || SOCIAL_QUESTIONS.contains(p.text()) || OPINION.matcher(p.text()).matches());
            if (parts.length > 1 && complete) result.addAll(split);
            else result.add(classify(clause, speaker, lore, result.isEmpty() ? styleVariant : 0));
        }
        return result;
    }

    private Clause classify(String clause, SpeakerContext speaker, LoreEngine lore, int styleVariant) {
        var fact = single(clause, speaker, lore, styleVariant);
        // "Sence Tech kime bağlı?" asks for a fact; the opener does not make it an opinion.
        if (fact == null && clause.matches("(?:sence|sizce|bence) .+")) fact = single(clause.substring(clause.indexOf(' ') + 1), speaker, lore, styleVariant);
        return new Clause(clause, fact);
    }

    private static String withoutLeads(String text) {
        String rest = text;
        for (boolean changed = true; changed; ) {
            changed = false;
            for (String lead : LEADS) if (rest.startsWith(lead + " ")) { rest = rest.substring(lead.length()).strip(); changed = true; break; }
        }
        return rest;
    }

    // The model should read the player's words, not their normalized matching form.
    private static String original(String question, String clause) {
        return Arrays.stream(CLAUSE.split(question)).map(String::strip).filter(part -> clean(part).endsWith(clause)).findFirst().orElse(clause);
    }

    private static List<String> blockedTerms(LoreEngine lore, String clause) {
        String asked = clean(clause);
        return lore.municipalRecords().stream().flatMap(r -> java.util.stream.Stream.concat(r.subjects().stream(), r.offices().stream()))
                .map(AnswerPlanner::clean).distinct().filter(term -> !asked.contains(term)).toList();
    }

    private AnswerPlan single(String question, SpeakerContext speaker, LoreEngine lore, int styleVariant) {
        String q = question.replaceFirst("[?.!]+$", "").strip();
        if (q.isEmpty() || q.contains("?") || q.contains(",") || q.contains("!") || q.contains("\"") || q.contains("\n")) return null;
        QueryFrame.Intent intent;
        String requested;
        var office = OFFICE_QUESTION.matcher(q);
        var to = TO.matcher(q);
        var from = FROM.matcher(q);
        var holder = HOLDER.matcher(q);
        var name = NAME_QUESTION.matcher(q);
        var check = REPORT_CHECK.matcher(q);
        if (Set.of("sen hangi gorevdesin", "siz hangi gorevdesiniz", "senin gorevin ne", "senin gorevlerin neler").contains(q)) {
            intent = OFFICES; requested = "sen";
        } else if (office.matches()) { intent = OFFICES; requested = office.group(1); }
        else if (to.matches()) { intent = REPORTS_TO; requested = to.group(1); }
        else if (from.matches()) { intent = REPORTS_FROM; requested = from.group(1); }
        else if (check.matches()) return reportCheck(check.group(1), speaker, lore, styleVariant);
        else if (name.matches()) { intent = OFFICE_HOLDER; requested = officeLabel(name.group(1), lore); }
        else if (holder.matches()) { intent = OFFICE_HOLDER; requested = officeLabel(holder.group(1), lore); }
        else return null;

        var records = lore.municipalRecords();
        boolean self = false;
        String subject;
        if (intent == OFFICE_HOLDER) {
            List<String> matches = records.stream().flatMap(r -> r.offices().stream())
                    .filter(o -> clean(o).equals(requested) || clean(o).equals(requested.replaceFirst("^superseed ", ""))).distinct().toList();
            if (matches.size() == 1) subject = matches.get(0);
            else if (matches.size() > 1) return clarification();
            else if (requested.matches("[a-z]+(?: [a-z]+){0,5}")
                    && requested.matches(".* (?:baskani|muduru|vekili|yardimcisi)")
                    && Arrays.stream(requested.split(" ")).noneMatch(RESERVED::contains)) subject = requested;
            else return null;
        } else {
            String label = requested.replaceFirst("'(?:in|un|nin|nun|e|a|ye|ya)$", "");
            if (SELF_WORDS.contains(label)) {
                if (speaker.knowledgeEntity() == null || speaker.knowledgeEntity().isBlank()) return clarification();
                label = clean(speaker.knowledgeEntity());
                self = true;
            }
            if (Set.of("o", "onun", "ona", "onlar", "onlara").contains(label)) return clarification();
            Set<String> people = people(label, lore);
            if (people.size() > 1) return clarification();
            if (people.isEmpty() && self) return clarification();
            if (people.isEmpty() && (!label.matches("[a-z0-9]+(?: [a-z0-9]+){0,2}")
                    || Arrays.stream(label.split(" ")).anyMatch(RESERVED::contains))) return null;
            subject = people.isEmpty() ? label : people.iterator().next();
            self = !people.isEmpty() && speaker.isSelf(subject);
        }

        String target = LoreEngine.normalize(subject);
        List<LoreEngine.VerifiedFact> evidence = records.stream().filter(LoreEngine.VerifiedFact::current).filter(r -> switch (intent) {
            case OFFICES -> r.type().equals("office") && r.subjects().stream().anyMatch(s -> LoreEngine.normalize(s).equals(target));
            case OFFICE_HOLDER -> r.type().equals("office") && r.offices().stream().anyMatch(o -> LoreEngine.normalize(o).equals(target));
            case REPORTS_TO -> r.relation().equals("reports_to") && LoreEngine.normalize(r.from()).equals(target);
            case REPORTS_FROM -> r.relation().equals("reports_to") && LoreEngine.normalize(r.to()).equals(target);
            default -> false;
        }).toList();
        String answer;
        if (evidence.isEmpty()) {
            answer = intent == OFFICES || intent == OFFICE_HOLDER
                    ? "Bu soruyu yanıtlayacak doğrulanmış güncel makam kaydım yok."
                    : UNKNOWN_REPORTING;
        } else {
            List<String> values = evidence.stream().flatMap(r -> switch (intent) {
                case OFFICES -> r.offices().stream();
                case OFFICE_HOLDER -> r.subjects().stream();
                case REPORTS_TO -> java.util.stream.Stream.of(r.to());
                case REPORTS_FROM -> java.util.stream.Stream.of(r.from());
                default -> java.util.stream.Stream.empty();
            }).distinct().toList();
            // No schema claim establishes a complete list, exclusive office holder or single superior.
            String joined = join(values);
            answer = switch (intent) {
                case OFFICES -> self ? "Kayıtlardaki güncel görevlerim: " + joined + "."
                        : subject + " için kayıtlardaki güncel görevler: " + joined + ".";
                case OFFICE_HOLDER -> subject + " makamı için güncel kayıtlarda geçen isimler: " + joined + ".";
                case REPORTS_TO -> self ? "Kayıtlara göre bağlı olduğum kişiler: " + joined + "."
                        : "Kayıtlara göre " + subject + " şu kişilere bağlı: " + joined + ".";
                case REPORTS_FROM -> "Kayıtlara göre " + subject + " adlı kişiye bağlı olanlar: " + joined + ".";
                default -> throw new IllegalStateException();
            };
        }
        return new AnswerPlan(new QueryFrame(intent, subject, self, ""),
                INTROS.get(Math.floorMod(styleVariant, INTROS.size())) + answer,
                evidence.stream().map(LoreEngine.VerifiedFact::id).toList());
    }

    /** "X Y'ye bağlı mı": only an exact directed edge answers; absence stays unknown, never "no". */
    private AnswerPlan reportCheck(String body, SpeakerContext speaker, LoreEngine lore, int styleVariant) {
        String[] words = body.split(" ");
        if (words.length < 2 || Arrays.stream(words).anyMatch(RESERVED::contains)) return null;
        String last = words[words.length - 1];
        if (!last.matches("[a-z0-9]+'(?:e|a|ye|ya)") && !SELF_WORDS.contains(last)) return null;
        for (int split = 1; split < words.length; split++) {
            String from = person(String.join(" ", Arrays.copyOfRange(words, 0, split)), speaker, lore);
            String to = person(String.join(" ", Arrays.copyOfRange(words, split, words.length)).replaceFirst("'(?:e|a|ye|ya)$", ""), speaker, lore);
            if (from == null || to == null) continue;
            var edge = lore.municipalRecords().stream().filter(LoreEngine.VerifiedFact::current).filter(r -> r.relation().equals("reports_to")
                    && LoreEngine.normalize(r.from()).equals(LoreEngine.normalize(from)) && LoreEngine.normalize(r.to()).equals(LoreEngine.normalize(to))).toList();
            String answer = edge.isEmpty() ? UNKNOWN_REPORTING : "Kayıtlara göre " + from + " şu kişilere bağlı: " + to + ".";
            return new AnswerPlan(new QueryFrame(REPORTS_CHECK, from, speaker.isSelf(from), ""),
                    INTROS.get(Math.floorMod(styleVariant, INTROS.size())) + answer, edge.stream().map(LoreEngine.VerifiedFact::id).toList());
        }
        return new AnswerPlan(new QueryFrame(REPORTS_CHECK, "", false, ""), UNKNOWN_REPORTING, List.of());
    }

    private String person(String label, SpeakerContext speaker, LoreEngine lore) {
        if (SELF_WORDS.contains(label)) return speaker.knowledgeEntity() == null || speaker.knowledgeEntity().isBlank() ? null : speaker.knowledgeEntity();
        var people = people(label, lore);
        return people.size() == 1 ? people.iterator().next() : null;
    }

    private static Set<String> people(String asked, LoreEngine lore) {
        Set<String> people = new TreeSet<>();
        for (var r : lore.municipalRecords()) for (String person : r.subjects()) {
            if (clean(person).equals(asked) || lore.aliases().stream().anyMatch(a ->
                    clean(a.canonical()).equals(clean(person)) && a.names().stream().anyMatch(n -> clean(n).equals(asked)))) people.add(person);
        }
        return people;
    }

    // Possessive office forms map to the office itself. A bare "başkan" means the mayor to a municipal NPC.
    private static String officeLabel(String head, LoreEngine lore) {
        String label = head.replaceFirst("^(?:superseed'?in|sehrin|sehrimizin|belediyenin|belediyemizin) ", "");
        if (BARE_MAYOR.contains(label)) return "belediye baskani";
        if (label.matches(".*(?:baskaninin|mudurunun|vekilinin|yardimcisinin)")) label = label.replaceFirst("n[iu]n$", "");
        String stem = label;
        return lore.municipalRecords().stream().flatMap(r -> r.offices().stream()).map(AnswerPlanner::clean)
                .filter(office -> office.equals("belediye " + stem)).findFirst().orElse(stem);
    }

    private static AnswerPlan clarification() {
        return new AnswerPlan(new QueryFrame(CLARIFY, "", false, ""), "Kimi kastettiğini netleştirir misin?", List.of());
    }
    private static String clean(String text) {
        return LoreEngine.normalize(text).replace('’', '\'').replaceAll("\\s+", " ").strip();
    }
    private static String join(List<String> values) {
        return values.size() < 2 ? values.get(0) : String.join(", ", values.subList(0, values.size() - 1)) + " ve " + values.get(values.size() - 1);
    }
}
