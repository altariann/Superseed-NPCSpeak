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
    private static final Set<String> RESERVED = Set.of("ve", "ile", "ama", "neden", "nicin", "nasil", "sence", "gore", "bugun", "eskiden", "eski", "gecmiste", "tarihsel", "yilinda", "degil", "ben", "benim", "onceden", "yarin", "gelecekte", "gelecek", "once", "sonra");
    private static final Set<String> SOCIAL_QUESTIONS = Set.of("nasilsin", "bugun nasilsin", "sen nasilsin", "peki sen nasilsin", "merhaba", "selam");
    private static final List<String> INTROS = List.of("", "Hemen söyleyeyim: ", "Kısaca: ");

    public AnswerPlan plan(String question, SpeakerContext speaker, LoreEngine lore, int styleVariant, boolean freeform) {
        return freeform ? plan(question, speaker, lore, styleVariant) : AnswerPlan.chat();
    }

    public AnswerPlan plan(String question, SpeakerContext speaker, LoreEngine lore, int styleVariant) {
        if (!lore.settings().enabled() || lore.municipalRecords().isEmpty() || question == null || question.length() > 500) return AnswerPlan.chat();
        String q = clean(question);
        if (q.contains("\"") || q.matches(".*\\b[12][0-9]{3}\\b.*")) return AnswerPlan.chat();
        // A mixed turn is guarded only if its first complete clause independently matches.
        // Never reduce an arbitrary roleplay/opinion message to its first matching keyword.
        for (String separator : List.of("?", ",", " ve ")) {
            int split = q.indexOf(separator);
            if (split > 0 && split + separator.length() < q.length()) {
                var first = single(q.substring(0, split), speaker, lore, styleVariant);
                String rest = q.substring(split + separator.length()).strip();
                if (first != null && SOCIAL_QUESTIONS.contains(rest.replaceFirst("[?.!]+$", "").strip())) {
                    var frame = first.query();
                    return new AnswerPlan(new QueryFrame(frame.intent(), frame.subject(), frame.self(), rest), first.sourceAnswer(), first.evidenceIds());
                }
            }
        }
        var result = single(q, speaker, lore, styleVariant);
        return result == null ? AnswerPlan.chat() : result;
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
        if (Set.of("sen hangi gorevdesin", "siz hangi gorevdesiniz", "senin gorevin ne", "senin gorevlerin neler").contains(q)) {
            intent = OFFICES; requested = "sen";
        } else if (office.matches()) { intent = OFFICES; requested = office.group(1); }
        else if (to.matches()) { intent = REPORTS_TO; requested = to.group(1); }
        else if (from.matches()) { intent = REPORTS_FROM; requested = from.group(1); }
        else if (holder.matches()) { intent = OFFICE_HOLDER; requested = holder.group(1); }
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
            if (Set.of("sen", "senin", "siz", "sizin", "sana", "size").contains(label)) {
                if (speaker.knowledgeEntity() == null || speaker.knowledgeEntity().isBlank()) return clarification();
                label = clean(speaker.knowledgeEntity());
                self = true;
            }
            if (Set.of("o", "onun", "ona", "onlar", "onlara").contains(label)) return clarification();
            Set<String> people = new TreeSet<>();
            String asked = label;
            for (var r : records) for (String person : r.subjects()) {
                if (clean(person).equals(asked) || lore.aliases().stream().anyMatch(a ->
                        clean(a.canonical()).equals(clean(person)) && a.names().stream().anyMatch(n -> clean(n).equals(asked)))) people.add(person);
            }
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
                    : "Bu bağlılığa ilişkin doğrulanmış güncel kaydım yok.";
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
