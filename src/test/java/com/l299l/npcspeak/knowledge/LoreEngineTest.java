package com.l299l.npcspeak.knowledge;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class LoreEngineTest {
    private static LoreEngine.Article page(String name, String text) { return new LoreEngine.Article(name, "fixture://" + name, text); }
    private static LoreEngine engine(List<LoreEngine.Article> pages) {
        return new LoreEngine(LoreEngine.Settings.defaults(), pages,
                List.of(new LoreEngine.Alias("Plaza AVM", List.of("Iron Plaza"), "Iron Plaza, Plaza AVM'nin eski adıdır.")),
                List.of(), Map.of());
    }
    private static List<String> selected(LoreEngine.Result result) {
        return result.candidates().stream().filter(LoreEngine.Candidate::selected).map(LoreEngine.Candidate::title).distinct().toList();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Iron Plaza nedir?", "IRON PLAZA'nın eski adı?", "Plaza AVM hakkında anlat", "Plaza AVM'den bahset", "iron plazanin tarihi"})
    void verifiedFormerNameFindsOnlyTheSameEntity(String question) {
        var index = engine(List.of(page("Iron Plaza", "Bu test kaydı bir alışveriş yapısını anlatır."),
                page("Aymerkez AVM", "Plaza mağazaları ve bir alışveriş merkezi."), page("Plaza", "Genel bir plaza.")));
        var result = index.retrieve(question);
        assertEquals(List.of("Iron Plaza"), selected(result));
        assertTrue(result.sourceData().contains("VERIFIED_NAME_RELATION"));
        assertTrue(result.candidates().stream().filter(LoreEngine.Candidate::selected)
                .allMatch(candidate -> candidate.reasons().contains("VERIFIED_ALIAS")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Plaza nedir?", "Iron AVM kimindir?", "Superseed AVM belediye tarihi", "bilinmeyen zümrüt sarayı nedir?", "", "ne mi için lütfen?"})
    void rejectsUnrelatedGenericMixedAliasAndEmptyQueries(String question) {
        var result = engine(List.of(page("Iron Plaza", "Superseed belediye AVM tarihi kaydı."),
                page("Aymerkez AVM", "Superseed belediye AVM tarihi arşivi."))).retrieve(question);
        assertEquals(0, result.wikiCount());
        assertFalse(result.hasSources());
    }

    @Test void aliasExpansionNeverChainsOrBuildsHybridNames() {
        var index = new LoreEngine(LoreEngine.Settings.defaults(), List.of(page("Iron AVM", "Başka yapı."), page("Beta Park", "Başka park.")),
                List.of(new LoreEngine.Alias("Plaza AVM", List.of("Iron Plaza"), "Eski isim."),
                        new LoreEngine.Alias("Beta Park", List.of("Plaza AVM Merkezi"), "Farklı isim.")), List.of(), Map.of());
        assertEquals(0, index.retrieve("Iron Plaza nedir?").wikiCount());
    }

    @Test void rejectsAmbiguousAliasesInsteadOfMergingEntities() {
        assertThrows(IllegalArgumentException.class, () -> new LoreEngine(LoreEngine.Settings.defaults(), List.of(),
                List.of(new LoreEngine.Alias("A", List.of("Shared Name"), ""),
                        new LoreEngine.Alias("B", List.of("Shared Name"), "")), List.of(), Map.of()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Doğan Tırpan kimdir?", "DOGAN TIRPAN kimdir?", "doğan tırpan'ın görevi?", "Dogan Tirpanin gorevi?"})
    void turkishNormalizationAndCaseSuffixes(String question) {
        assertEquals(List.of("Doğan Tırpan"), selected(engine(List.of(page("Doğan Tırpan", "Test kişi kaydı."),
                page("Doğan Tırpancı", "İlgisiz test kişi."))).retrieve(question)));
    }

    @Test void namesCannotUseArbitraryPrefixesOrMatchTheInverseSuffix() {
        assertEquals(0, engine(List.of(page("Tech", "Test kişi."))).retrieve("Technology nedir?").wikiCount());
        assertEquals(0, engine(List.of(page("Techin", "Başka kişi."))).retrieve("Tech kim?").wikiCount());
    }

    @Test void longestNameWinsOverNestedTitle() {
        assertEquals(List.of("Tech Plaza"), selected(engine(List.of(page("Tech", "Bir test kişi."),
                page("Tech Plaza", "Bir test bina."))).retrieve("Tech Plaza nedir?")));
    }

    @Test void exactUsernameBeatsAnotherNamesPossibleTurkishSuffix() {
        assertEquals(List.of("Techin"), selected(engine(List.of(page("Tech", "Birinci kişi."), page("Techin", "İkinci kişi.")))
                .retrieve("Techin kimdir?")));
    }

    @Test void ambiguousPunctuationVariantsAreRejectedInsteadOfMerged() {
        var result = engine(List.of(page("A.B Kule", "Birinci kayıt."), page("A B Kule", "İkinci kayıt."))).retrieve("A B Kule nedir?");
        assertEquals(0, result.wikiCount());
        assertTrue(result.candidates().stream().allMatch(candidate -> candidate.reasons().contains("REJECT_AMBIGUOUS_ENTITY")));
    }

    @Test void anExactEntityNeverMixesIncidentalBodyMentions() {
        var result = engine(List.of(page("Plaza AVM", "Plaza AVM test kaydı."),
                page("Dış Haberler", "Plaza AVM ve Iron Plaza üzerine farklı haber."))).retrieve("Plaza AVM nedir?");
        assertEquals(List.of("Plaza AVM"), selected(result));
        assertTrue(result.candidates().stream().filter(candidate -> candidate.title().equals("Dış Haberler"))
                .allMatch(candidate -> candidate.reasons().contains("REJECT_OTHER_ENTITY")));
    }

    @Test void explicitMultiEntityQuestionMayRetrieveBoth() {
        var result = engine(List.of(page("Alpha Kule", "Birinci test kayıt."), page("Beta Park", "İkinci test kayıt.")))
                .retrieve("Alpha Kule ve Beta Park hakkında bilgi ver");
        assertEquals(Set.of("Alpha Kule", "Beta Park"), new HashSet<>(selected(result)));
    }

    @Test void lexicalFallbackNeedsRareTermsAndCoverageWithinOnePassage() {
        var result = engine(List.of(page("Deneme A", "Kuantum kristali burada sergilenir."),
                page("Deneme B", "Başka sıradan haber."), page("Deneme C", "Farklı sıradan haber.")))
                .retrieve("kuantum kristali nerede?");
        assertEquals(List.of("Deneme A"), selected(result));
        assertTrue(result.candidates().stream().filter(LoreEngine.Candidate::selected)
                .allMatch(candidate -> candidate.reasons().contains("LEXICAL_COVERAGE_AND_RARITY")));
        assertEquals(0, engine(List.of(page("Deneme A", "Kuantum kristali burada sergilenir.")))
                .retrieve("kuantum kristali zümrüt roket gökyüzü nerede?").wikiCount());
    }

    @Test void repeatedQueryWordIsNotTwoDistinctTerms() {
        assertEquals(0, engine(List.of(page("Deneme A", "Kuantum burada."))).retrieve("kuantum kuantum kuantum?").wikiCount());
    }

    @Test void fallbackDoesNotCountTitleWordsOrCombineSeparatePassages() {
        var titleOnly = engine(List.of(page("Kuantum Merkezi", "Kristali burada görürsün.")));
        assertEquals(0, titleOnly.retrieve("kuantum kristali nerede?").wikiCount());
        String first = "Kuantum " + "dolgu ".repeat(80) + ".";
        String second = "Kristali " + "başka ".repeat(80) + ".";
        assertEquals(0, engine(List.of(page("Deneme", first + "\n\n" + second))).retrieve("kuantum kristali nerede?").wikiCount());
    }

    @Test void commonInformativeWordsAreNotEnoughForFallback() {
        var result = engine(List.of(page("A Kayıt", "Kuantum kristali üzerine haber."),
                page("B Kayıt", "Kuantum kristali üzerine başka haber."), page("C Kayıt", "Kuantum kristali üzerine son haber.")))
                .retrieve("kuantum kristali nerede?");
        assertEquals(0, result.wikiCount());
        assertTrue(result.candidates().stream().allMatch(candidate -> candidate.reasons().contains("REJECT_COMMON_TERMS")));
    }

    @Test void scoreThresholdIsConfigurableEvenForAnExactEntity() {
        var strict = new LoreEngine(new LoreEngine.Settings(true, 3, 2600, 1000, .65),
                List.of(page("Test Kule", "Test bilgi.")), List.of(), List.of(), Map.of());
        var result = strict.retrieve("Test Kule nedir?");
        assertEquals(0, result.wikiCount());
        assertTrue(result.candidates().get(0).reasons().contains("REJECT_LOW_SCORE"));
    }

    @Test void zeroWikiLimitStillAllowsVerifiedRecords() {
        var index = new LoreEngine(new LoreEngine.Settings(true, 0, 2600, 6, .65),
                List.of(page("Tech", "Tarihsel kayıt.")), List.of(),
                List.of(new LoreEngine.VerifiedFact("current", "Belediye Başkanı: Tech.", List.of("Tech"), List.of(), true)), Map.of());
        assertEquals(1, index.retrieve("Tech kim?").verifiedCount());
        assertEquals(0, index.retrieve("Tech kim?").wikiCount());
    }

    @Test void disabledGroundingKeepsOriginalChatAndMemoryBehavior() {
        var index = new LoreEngine(new LoreEngine.Settings(false, 3, 2600, 6, .65), List.of(), List.of(), List.of(), Map.of());
        var messages = GroundedPrompt.messages("Original style/task", "", "Old memory", List.of(), "Merhaba", index);
        assertTrue(messages.get(0).content().startsWith("Original style/task"));
        assertTrue(messages.get(0).content().contains("Old memory"));
        assertFalse(messages.get(0).content().contains("LORE_SOURCE_DATA"));
        assertEquals("Merhaba", messages.get(1).content());
    }

    @Test void currentRecordsPrecedeWikiAndApplyToEveryNpc() {
        var fact = new LoreEngine.VerifiedFact("mayor", "Belediye Başkanı: Tech.", List.of("Tech"), List.of("belediye başkanı"), true);
        var index = new LoreEngine(LoreEngine.Settings.defaults(), List.of(page("Tech", "Eski tarihli bir makam kaydı.")),
                List.of(), List.of(fact), Map.of("dogan_tirpan", "Üslup"));
        var result = index.retrieve("Tech kimdir?");
        assertEquals(1, result.verifiedCount());
        assertTrue(result.sourceData().indexOf("CURRENT_VERIFIED_RECORD") < result.sourceData().indexOf("WIKI_POSSIBLY_HISTORICAL"));
        assertTrue(GroundedPrompt.messages("Stil", index.persona("other_npc"), null, List.of(), "Tech kimdir?", index)
                .get(0).content().contains("Belediye Başkanı: Tech."));
    }

    @Test void productionRelationshipDoesNotMakeProducerAnAlias() {
        var index = new LoreEngine(LoreEngine.Settings.defaults(), List.of(page("Visual Node", "Yapımcı test kaydı.")), List.of(),
                List.of(new LoreEngine.VerifiedFact("production", "Pursuing Evidence, SPAT tarafından oluşturulan bir Visual Node yapımıdır.",
                        List.of("Pursuing Evidence"), List.of(), true)), Map.of());
        var result = index.retrieve("Pursuing Evidence kim tarafından yapıldı?");
        assertEquals(1, result.verifiedCount());
        assertEquals(0, result.wikiCount());
        assertFalse(result.sourceData().contains("VERIFIED_NAME_RELATION"));
    }

    @Test void contextBudgetNeverCutsRulesOrSourcesAndDiagnosticsAreExact() {
        var index = new LoreEngine(new LoreEngine.Settings(true, 3, 600, 6, .65),
                List.of(page("Test Kule", "Uzun cümle " + "kelime ".repeat(95) + ". Kısa tam bilgi.")), List.of(), List.of(), Map.of());
        var result = index.retrieve("Test Kule nedir?");
        assertTrue(result.sourceData().length() <= 600);
        assertEquals(1, result.wikiCount());
        result.sourceData().lines().forEach(line -> assertDoesNotThrow(() -> JsonParser.parseString(line)));
        for (var candidate : result.candidates()) if (candidate.selected()) {
            assertTrue(result.sourceData().contains(candidate.passedExcerpt()));
        }
        assertTrue(GroundedPrompt.referenceSection(result).contains(GroundedPrompt.RULES));
        assertTrue(String.join("", LoreDiagnostics.lines(result)).contains(result.sourceData()));
    }

    @Test void rankingIsIndependentOfArticleLoadOrder() {
        var pages = List.of(page("Test Kule", "Bir test bilgi."), page("Test Park", "İkinci bilgi."));
        var reversed = new ArrayList<>(pages); Collections.reverse(reversed);
        assertEquals(engine(pages).retrieve("Test Kule ve Test Park?"), engine(reversed).retrieve("Test Kule ve Test Park?"));
    }

    @Test void disabledRetrievalAndZeroWikiLimitBehaveExplicitly() {
        var disabled = new LoreEngine(new LoreEngine.Settings(false, 3, 2600, 6, .65), List.of(), List.of(), List.of(), Map.of());
        assertEquals("DISABLED", disabled.retrieve("Tech?").status());
        var noWiki = new LoreEngine(new LoreEngine.Settings(true, 0, 2600, 6, .65),
                List.of(page("Test Kule", "Tam bilgi.")), List.of(), List.of(), Map.of());
        assertEquals(0, noWiki.retrieve("Test Kule nedir?").wikiCount());
        assertEquals("NO_CONTEXT_ROOM_OR_WIKI_DISABLED", noWiki.retrieve("Test Kule nedir?").status());
    }
}
