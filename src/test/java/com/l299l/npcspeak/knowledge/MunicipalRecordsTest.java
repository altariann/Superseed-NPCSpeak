package com.l299l.npcspeak.knowledge;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MunicipalRecordsTest {
    @TempDir Path root;
    private LoreEngine defaults() throws Exception {
        try (var input = getClass().getResourceAsStream("/knowledge.yml")) {
            Files.copy(Objects.requireNonNull(input), root.resolve("knowledge.yml"));
        }
        // Even an exact person wiki must not reintroduce unverified current relationships.
        Files.createDirectories(root.resolve("knowledge/wiki"));
        Files.writeString(root.resolve("knowledge/wiki/iladymoon.txt"), "Title: iladymoon\n\niladymoon, Hayali Kişi'ye bağlıdır.");
        return KnowledgeBase.load(root);
    }
    private Set<String> ids(LoreEngine.Result result) {
        Set<String> ids = new TreeSet<>();
        result.sourceData().lines().forEach(line -> ids.add(JsonParser.parseString(line).getAsJsonObject().get("title").getAsString()));
        assertTrue(result.sourceData().length() <= 3200);
        return ids;
    }
    @Test void officeOwnerIsExactAndDoesNotIncludeDeputyOrActingMayor() throws Exception {
        assertEquals(Set.of("mayor-tech"), ids(defaults().retrieve("Superseed Belediye Başkanı kim?")));
    }
    @Test void namedDeputyGetsOnlyOwnOffice() throws Exception {
        assertEquals(Set.of("deputy-dogan"), ids(defaults().retrieve("Doğan Tırpan hangi görevde?")));
    }
    @Test void reportingLineIsSeparateFromOfficeAndHasDirection() throws Exception {
        var engine = defaults();
        assertEquals(Set.of("dogan-reports-to-tech"), ids(engine.retrieve("Doğan Tırpan kime bağlı?")));
        assertEquals(Set.of("dogan-reports-to-tech"), ids(engine.retrieve("Tech'e kim bağlı?")));
        assertFalse(engine.retrieve("Tech kime bağlı?").hasSources());
        assertFalse(engine.retrieve("Tech kime bağlıdır?").hasSources());
    }
    @Test void frozenB03HasExactlyTwoOwnOfficeRecords() throws Exception {
        var result = defaults().retrieve("iladymoon belediyede hangi görevlerde bulunuyor?");
        assertEquals(Set.of("secretary-iladymoon", "acting-mayor-iladymoon"), ids(result));
        assertFalse(result.sourceData().contains("Tech"));
        assertFalse(result.sourceData().contains("Doğan"));
        assertEquals(0, result.wikiCount());
    }
    @Test void possessiveTurkishQuestionAlsoHasOnlyOwnOffices() throws Exception {
        assertEquals(Set.of("secretary-iladymoon", "acting-mayor-iladymoon"), ids(defaults().retrieve("iladymoon'un görevleri neler?")));
    }
    @Test void unverifiedReportingLineDoesNotUseExactWikiOrOtherPeoplesRelations() throws Exception {
        var result = defaults().retrieve("iladymoon kime bağlı?");
        assertFalse(result.hasSources());
        assertEquals("NO_VERIFIED_MUNICIPAL_RECORD", result.status());
        assertTrue(result.candidates().stream().anyMatch(candidate -> candidate.reasons().contains("REJECT_REQUIRES_VERIFIED_MUNICIPAL_RECORD")));
    }
    @Test void officeComparisonIncludesBothPeopleButNoReportingLine() throws Exception {
        assertEquals(Set.of("deputy-dogan", "secretary-iladymoon", "acting-mayor-iladymoon"),
                ids(defaults().retrieve("Doğan Tırpan ile iladymoon aynı görevi mi yürütüyor?")));
    }
    @Test void unsupportedPairCannotBorrowKnownRelation() throws Exception {
        var engine = defaults();
        for (String question : List.of("Kurgusal Alfa ile Kurgusal Beta arasındaki ilişki nedir?",
                "Doğan Tırpan ile Kurgusal Beta arasındaki ilişki nedir?",
                "Doğan Tırpan ile iladymoon arasındaki ilişki nedir?")) assertFalse(engine.retrieve(question).hasSources(), question);
        assertEquals(Set.of("dogan-reports-to-tech"), ids(engine.retrieve("Doğan Tırpan ile Tech arasındaki ilişki nedir?")));
    }
    @Test void newRecordsSuppressLegacyMunicipalityEvenWhenBothExist() throws Exception {
        defaults();
        Files.writeString(root.resolve("knowledge.yml"), "\nmunicipality: 'iladymoon, başka birine bağlıdır.'\n", StandardOpenOption.APPEND);
        var engine = KnowledgeBase.load(root);
        assertFalse(engine.retrieve("iladymoon kime bağlı?").hasSources());
        assertFalse(engine.retrieve("iladymoon kimdir?").sourceData().contains("başka birine"));
    }
    @Test void legacyOnlyMunicipalityAndFactsRemainAvailable() throws Exception {
        Files.writeString(root.resolve("knowledge.yml"), "municipality: 'Örnek kişi, örnek başkana bağlıdır.'\ncharacters:\n  example_npc: 'Kısa konuş.'\n");
        var engine = KnowledgeBase.load(root);
        assertTrue(engine.retrieve("Örnek kişi kime bağlı?").hasSources());
        assertEquals("Kısa konuş.", engine.persona("example_npc"));
    }
    @Test void historicalOfficeDoesNotBecomeCurrentAndCurrentDoesNotAnswerHistoricalQuestion() throws Exception {
        Files.writeString(root.resolve("knowledge.yml"), """
                municipal-records:
                  - {id: old, type: office, subjects: [Örnek], offices: [Belediye Başkanı], current: false, text: 'Örnek geçmişte başkandı.'}
                  - {id: now, type: office, subjects: [Diğer], offices: [Belediye Başkanı], current: true, text: 'Diğer güncel başkandır.'}
                """);
        var engine = KnowledgeBase.load(root);
        assertEquals(Set.of("now"), ids(engine.retrieve("Belediye Başkanı kim?")));
        var historical = engine.retrieve("Eskiden Belediye Başkanı kimdi?");
        assertEquals(Set.of("old"), ids(historical));
        assertTrue(historical.sourceData().contains("\"kind\":\"VERIFIED_RECORD\""));
    }
    @Test void malformedAtomicConfigurationFailsWithoutFallingBackToBroadText() throws Exception {
        for (String records : List.of("[]", "not-a-list", "[{id: x, type: office, subjects: [A], current: true, text: test}]",
                "[{id: x, type: relationship, subjects: [A], current: true, text: test}]",
                "[{id: x, type: office, subjects: [A], offices: [Mayor], current: maybe, text: test}]")) {
            Files.writeString(root.resolve("knowledge.yml"), "municipality: old\nmunicipal-records: " + records + "\n");
            assertThrows(IllegalArgumentException.class, () -> KnowledgeBase.load(root), records);
        }
    }
    @Test void personalitySeparatorFallbackIsExactFirstAndFailsClosedOnCollision() {
        var engine = new LoreEngine(LoreEngine.Settings.defaults(), List.of(), List.of(), List.of(),
                Map.of("example_npc", "legacy", "other-npc", "hyphen", "a_b-c", "one", "a-b_c", "two"));
        assertEquals("legacy", engine.persona("example-npc"));
        assertEquals("hyphen", engine.persona("other_npc"));
        assertEquals("one", engine.persona("a_b-c"));
        assertEquals("", engine.persona("a-b-c"));
        assertEquals("", engine.persona("313"));
    }
    @Test void configuredDefaultPersonalityLoadsForRealFileKeyWithoutEntityMutation() throws Exception {
        var engine = defaults();
        assertFalse(engine.persona("dogan-tirpan").isBlank());
        assertEquals(engine.persona("dogan_tirpan"), engine.persona("dogan-tirpan"));
        assertEquals("", engine.persona("313"));
    }
    @Test void verifiedNonMunicipalAliasAndProductionStillWork() throws Exception {
        var engine = defaults();
        assertTrue(engine.retrieve("Iron Plaza ile Plaza AVM ilişkisi nedir?").sourceData().contains("VERIFIED_NAME_RELATION"));
        assertTrue(engine.retrieve("Pursuing Evidence ile SPAT ilişkisi nedir?").sourceData().contains("Visual Node"));
    }
    @Test void longerUnrelatedWikiTitleCannotBeTreatedAsMunicipalPerson() throws Exception {
        defaults();
        Files.writeString(root.resolve("knowledge/wiki/tech-plaza.txt"), "Title: Tech Plaza\n\nTech Plaza bir binadır.");
        var result = KnowledgeBase.load(root).retrieve("Tech Plaza kimdir?");
        assertEquals(1, result.wikiCount());
        assertFalse(result.sourceData().contains("mayor-tech"));
    }
    @Test void missingCurrentOfficeDoesNotFallBackToHistoricalRecordOrWiki() throws Exception {
        Files.writeString(root.resolve("knowledge.yml"), """
                municipal-records:
                  - {id: old, type: office, subjects: [Örnek], offices: [Belediye Başkanı], current: false, text: 'Örnek eskiden başkandı.'}
                """);
        assertFalse(KnowledgeBase.load(root).retrieve("Belediye Başkanı kim?").hasSources());
    }
    @Test void municipalSubjectSupportsOnlyExplicitlyConfiguredAliases() throws Exception {
        Files.writeString(root.resolve("knowledge.yml"), """
                aliases:
                  Örnek Kişi:
                    names: [Eski Ad]
                municipal-records:
                  - {id: office, type: office, subjects: [Örnek Kişi], offices: [Başkan], current: true, text: 'Örnek Kişi başkandır.'}
                """);
        var engine = KnowledgeBase.load(root);
        assertTrue(ids(engine.retrieve("Eski Ad hangi görevde?")).contains("office"));
        assertFalse(engine.retrieve("Örnek hangi görevde?").hasSources());
    }
}
