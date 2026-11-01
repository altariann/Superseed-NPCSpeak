package com.l299l.npcspeak.knowledge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic regressions only; no private wiki excerpts are published as fixtures. */
class LoreArchiveRegressionTest {
    private static LoreEngine.Article page(String title, String body) { return new LoreEngine.Article(title, "fixture://" + title, body); }
    private static LoreEngine index(LoreEngine.Article... pages) {
        return new LoreEngine(LoreEngine.Settings.defaults(), List.of(pages), List.of(), List.of(), Map.of());
    }
    @ParameterizedTest @ValueSource(strings = {"#REDIRECT Test Transit", "#YÖNLENDİRME Test Transit", "Şuraya yönlendir:\nTest Transit", "#REDIRECT [[Test Transit]]"})
    void explicitWikiRedirectProvidesReferencedFactsButNoVerifiedAlias(String directive) {
        var result = index(page("Test Metro", directive), page("Test Transit", "Test Metro uses electric rails."))
                .retrieve("Test Metro nedir?");
        assertTrue(result.candidates().stream().anyMatch(candidate -> candidate.selected() && candidate.title().equals("Test Transit")
                && candidate.reasons().contains("WIKI_REDIRECT")));
        assertTrue(result.sourceData().contains("viaWikiRedirect"));
        assertFalse(result.sourceData().contains("VERIFIED_NAME_RELATION"));
    }
    @Test void redirectsCannotPullInUnaskedEntitiesDuringLexicalFallback() {
        var result = index(page("Test Metro", "#REDIRECT Test Transit"), page("Test Transit", "Quantum crystals glow brightly."))
                .retrieve("quantum crystals nerede?");
        assertEquals(1, result.candidates().stream().filter(LoreEngine.Candidate::selected).count());
        assertTrue(result.candidates().stream().filter(LoreEngine.Candidate::selected).allMatch(candidate -> candidate.viaWikiRedirect().isBlank()));
    }
    @Test void missingAndCyclicTargetsNeverGuessAnotherPage() {
        var engine = index(page("Cycle A", "#REDIRECT Cycle B"), page("Cycle B", "#REDIRECT Cycle A"),
                page("Missing Page", "#REDIRECT Not Present"), page("Unrelated", "Other facts."));
        var result = engine.retrieve("Cycle A nedir?");
        assertTrue(result.candidates().stream().filter(LoreEngine.Candidate::selected).allMatch(candidate -> candidate.viaWikiRedirect().isBlank()));
        assertTrue(engine.retrieve("Missing Page nedir?").candidates().stream().filter(LoreEngine.Candidate::selected)
                .allMatch(candidate -> candidate.title().equals("Missing Page")));
    }
    @Test void predicateRanksEvidenceAheadOfRepeatedEntityName() {
        String repeated = "Test Drama is a drama. " + "An overview of Test Drama. ".repeat(45);
        var result = index(page("Test Drama", repeated + "\n\nproducer=Example Studio\n\n" + repeated))
                .retrieve("Test Drama producer nedir?");
        assertTrue(result.candidates().stream().filter(LoreEngine.Candidate::selected).anyMatch(candidate -> candidate.passedExcerpt().contains("producer=Example Studio")));
    }
    @Test void ambiguousRedirectTargetDoesNotPickAnArbitraryNormalizedTitle() {
        var result = index(page("Example Ref", "#REDIRECT IDEA PARK"), page("Idea Park", "First entity."),
                page("İdea Park", "Second entity.")).retrieve("Example Ref nedir?");
        assertTrue(result.candidates().stream().filter(LoreEngine.Candidate::selected).allMatch(candidate -> candidate.title().equals("Example Ref")));
    }
    @Test void longInfoboxPreservesCompleteFieldsInsteadOfLosingAllProperties() {
        String box = "{{Info|description=" + "long ".repeat(160) + "|producer=Example Studio|year=2010}}";
        var result = index(page("Test Drama", box)).retrieve("Test Drama producer nedir?");
        assertTrue(result.candidates().stream().filter(LoreEngine.Candidate::selected).anyMatch(candidate -> candidate.passedExcerpt().contains("producer=Example Studio")));
        assertFalse(result.sourceData().contains("long long"));
    }
    @Test void sameReferencedPassageIsNeverSentTwice() {
        var result = index(page("Alpha Ref", "#REDIRECT Test Transit"), page("Beta Ref", "#REDIRECT Test Transit"),
                page("Test Transit", "Test Transit operates trains."))
                .retrieve("Alpha Ref ve Beta Ref nedir?");
        assertEquals(1, result.candidates().stream().filter(LoreEngine.Candidate::selected).filter(candidate -> candidate.title().equals("Test Transit")).count());
    }
    @Test void explicitUnknownNameCannotBorrowARegisteredPrefix() {
        var result = index(page("Harbor", "Harbor contains docks."))
                .retrieve("\"Harbor Quartz\" adlı yer hakkında bilgi ver.");
        assertFalse(result.hasSources());
        assertEquals("UNRESOLVED_NAMED_ENTITY", result.status());
    }
    @Test void incidentalBodyMatchesDoNotResolveAnExplicitUnknownName() {
        var result = index(page("Example Drama", "Police officers visited a place named Cold House."))
                .retrieve("\"Police Icecream\" adlı yer hakkında bilgi ver.");
        assertFalse(result.hasSources());
    }
    @Test void knownQuotedNameStillRetrievesItsOwnArticle() {
        assertTrue(index(page("Test Harbor", "Test Harbor contains docks."))
                .retrieve("\"Test Harbor\" adlı yer hakkında bilgi ver.").hasSources());
    }
    @Test void genericWordCanNameItsOwnFullArticleButNeverAnotherEntity() {
        var engine = index(page("Plaza", "A generic-word article."), page("Iron Plaza", "A different entity."));
        var result = engine.retrieve("Plaza nedir?");
        assertTrue(result.candidates().stream().filter(LoreEngine.Candidate::selected).allMatch(candidate -> candidate.title().equals("Plaza")));
        assertTrue(result.hasSources());
        assertFalse(engine.retrieve("Plaza kuvars nedir?").hasSources());
    }
    @Test void genericScopeWordDoesNotMixIntoANamedPersonQuestion() {
        var engine = index(page("Merkez", "An indexed common-word title."), page("Test Person", "A test person."));
        var result = engine.retrieve("Merkez'de Test Person kimdir?");
        assertTrue(result.candidates().stream().filter(LoreEngine.Candidate::selected).allMatch(candidate -> candidate.title().equals("Test Person")));
        assertTrue(engine.retrieve("Merkez hakkında bilgi ver.").hasSources());
    }
    @Test void anAliasGroupCannotConsumeAllSlotsInAMultiEntityComparison() {
        String repeated = "Bu aynı eski kayıt ve diğer adı hakkında açıklamadır. ".repeat(25);
        var engine = new LoreEngine(LoreEngine.Settings.defaults(),
                List.of(page("Alias Tower", repeated), page("Modern Tower", repeated), page("Second Center", "Second Center ayrı bir binadır.")),
                List.of(new LoreEngine.Alias("Modern Tower", List.of("Alias Tower"), "Eski ve yeni ad aynı yapı.")), List.of(), Map.of());
        var result = engine.retrieve("Alias Tower ve Modern Tower aynı yer mi? Second Center onun diğer adı mı?");
        assertTrue(result.candidates().stream().anyMatch(candidate -> candidate.selected() && candidate.title().equals("Second Center")));
        assertTrue(result.candidates().stream().filter(LoreEngine.Candidate::selected).anyMatch(candidate -> candidate.title().equals("Alias Tower") || candidate.title().equals("Modern Tower")));
        assertTrue(result.wikiCount() <= 3);
    }
}
