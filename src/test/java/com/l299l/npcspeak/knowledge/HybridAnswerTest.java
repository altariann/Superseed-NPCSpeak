package com.l299l.npcspeak.knowledge;

import com.l299l.npcspeak.ai.*;
import com.l299l.npcspeak.conversation.ConversationSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static com.l299l.npcspeak.knowledge.AnswerOutcome.Origin.*;

class HybridAnswerTest {
    @TempDir Path root;
    private final AnswerPlanner planner = new AnswerPlanner();
    private final HybridAnswerService service = new HybridAnswerService();
    private final SpeakerContext dogan = new SpeakerContext("dogan-tirpan", "Doğan Tırpan", "Doğan Tırpan");
    private LoreEngine lore() throws Exception {
        try (var input = getClass().getResourceAsStream("/knowledge.yml")) {
            Files.copy(Objects.requireNonNull(input), root.resolve("knowledge.yml"), StandardCopyOption.REPLACE_EXISTING);
        }
        // Only the test copy is migrated; production and bundled knowledge files are untouched.
        Files.writeString(root.resolve("knowledge.yml"), Files.readString(root.resolve("knowledge.yml"))
                .replace("  - id: dogan-reports-to-tech\n", "  - id: dogan-reports-to-tech\n    relation: reports_to\n    from: 'Doğan Tırpan'\n    to: 'Tech'\n"));
        return KnowledgeBase.load(root);
    }
    private AnswerPlan plan(String question) throws Exception { return planner.plan(question, dogan, lore(), 0); }
    private static final class FakeBackend implements AiBackend {
        int calls, streamCalls;
        String response = "Serbest sohbet cevabı.";
        CompletableFuture<String> pending;
        public CompletableFuture<String> complete(List<AiMessage> messages) {
            calls++;
            return pending != null ? pending : CompletableFuture.completedFuture(response);
        }
        public CompletableFuture<String> streamComplete(List<AiMessage> messages, Consumer<String> onToken) {
            streamCalls++;
            onToken.accept(response);
            return complete(messages);
        }
        public String getName() { return "fake"; }
    }
    private AnswerOutcome answer(AnswerPlan plan, FakeBackend backend, Consumer<String> preview) {
        return service.answer(plan, () -> List.of(new AiMessage("user", "fixture")), backend, true, preview).join();
    }
    @Test void frozenB03NeedsNoModelAndNeverUsesItsTemptingWrongAnswer() throws Exception {
        var plan = plan("iladymoon belediyede hangi görevlerde bulunuyor?");
        var backend = new FakeBackend();
        backend.response = "Başkan Vekiliyim, iladymoon ile birlikte çalışıyorum.";
        List<String> previews = new ArrayList<>();
        var result = service.answer(plan, () -> { fail("No prompt construction for a local answer"); return List.of(); }, backend, true, previews::add).join();
        assertEquals(SOURCE, result.origin());
        assertEquals("iladymoon için kayıtlardaki güncel görevler: Özel Kalem Müdürü ve Belediye Başkan Vekili.", result.text());
        assertEquals(Set.of("secretary-iladymoon", "acting-mayor-iladymoon"), new HashSet<>(plan.evidenceIds()));
        assertEquals(0, backend.calls);
        assertTrue(previews.isEmpty());
    }
    @Test void possessiveQuestionAndTurkishCharactersResolveSamePerson() throws Exception {
        for (String q : List.of("iladymoon'un görevleri neler?", "iladymoon’un görevleri neler?", "İLADYMOON belediyede hangi görevlerde bulunuyor?")) {
            assertEquals(plan("iladymoon belediyede hangi görevlerde bulunuyor?").sourceAnswer(), plan(q).sourceAnswer());
        }
    }
    @Test void firstPersonIsOnlyForExplicitNarratorEntity() throws Exception {
        for (String q : List.of("Sen hangi görevdesin?", "Senin görevlerin neler?", "Doğan Tırpan hangi görevde?")) {
            var p = plan(q);
            assertTrue(p.local(), q);
            assertTrue(p.query().self());
            assertTrue(p.sourceAnswer().contains("görevlerim: Belediye Başkan Yardımcısı"));
            assertFalse(p.sourceAnswer().contains("Vekili"));
        }
        var other = planner.plan("Doğan Tırpan hangi görevde?", new SpeakerContext("other", "Başka NPC", "Tech"), lore(), 0);
        assertFalse(other.query().self());
        assertTrue(other.sourceAnswer().contains("Doğan Tırpan için"));
    }
    @Test void unboundDisplayNameCannotBecomeFirstPersonFact() throws Exception {
        var speaker = new SpeakerContext("dogan-tirpan", "Doğan Tırpan", null);
        var p = planner.plan("Sen hangi görevdesin?", speaker, lore(), 0);
        assertEquals(QueryFrame.Intent.CLARIFY, p.query().intent());
        assertTrue(p.evidenceIds().isEmpty());
        var explicit = planner.plan("Doğan Tırpan hangi görevde?", speaker, lore(), 0);
        assertFalse(explicit.query().self());
    }
    @Test void playerAndAmbiguousPronounsAreNotNarrator() throws Exception {
        assertFalse(plan("Ben hangi görevdeyim?").local());
        assertFalse(plan("Benim görevlerim neler?").local());
        assertEquals(QueryFrame.Intent.CLARIFY, plan("O kime bağlı?").query().intent());
        assertFalse(plan("\"Sen\" hangi görevde?").local());
    }
    @Test void officeHolderDoesNotConfuseMayorDeputyAndActingMayor() throws Exception {
        var p = plan("Superseed Belediye Başkanı kim?");
        assertEquals(List.of("mayor-tech"), p.evidenceIds());
        assertTrue(p.sourceAnswer().contains("Tech"));
        assertFalse(p.sourceAnswer().contains("iladymoon"));
        assertFalse(p.sourceAnswer().contains("Doğan"));
    }
    @Test void directedReportingAndInverseQueriesUseSameEdge() throws Exception {
        for (String q : List.of("Doğan Tırpan kime bağlı?", "Sen kime bağlısın?", "Tech'e kim bağlı?")) {
            var p = plan(q);
            assertTrue(p.local(), q);
            assertEquals(List.of("dogan-reports-to-tech"), p.evidenceIds());
        }
        assertTrue(plan("Sen kime bağlısın?").sourceAnswer().contains("bağlı olduğum kişiler: Tech"));
        assertTrue(plan("Tech'e kim bağlı?").sourceAnswer().contains("Tech adlı kişiye bağlı olanlar: Doğan Tırpan"));
    }
    @Test void unknownReportingDoesNotBecomeNegativeFactOrBorrowAnotherEdge() throws Exception {
        for (String q : List.of("Tech kime bağlı?", "iladymoon kime bağlı?", "Kurgusal Alfa kime bağlı?")) {
            var p = plan(q);
            assertTrue(p.local(), q);
            assertTrue(p.evidenceIds().isEmpty());
            assertTrue(p.sourceAnswer().contains("kaydı") || p.sourceAnswer().contains("kaydım"));
            assertFalse(p.sourceAnswer().contains("bağlı değil"));
            var backend = new FakeBackend();
            answer(p, backend, token -> fail("No preview"));
            assertEquals(0, backend.calls);
        }
    }
    @Test void legacyRelationsAreNotAutomaticallyTypedForLocalAnswers() throws Exception {
        lore();
        String yaml = Files.readString(root.resolve("knowledge.yml"));
        Files.writeString(root.resolve("knowledge.yml"), yaml.replace("    relation: reports_to\n    from: 'Doğan Tırpan'\n    to: 'Tech'\n", ""));
        var p = planner.plan("Doğan Tırpan kime bağlı?", dogan, KnowledgeBase.load(root), 0);
        assertTrue(p.local());
        assertTrue(p.evidenceIds().isEmpty());
    }
    @Test void unknownOfficesUseExplicitUncertainty() throws Exception {
        for (String q : List.of("Kurgusal Alfa hangi görevde?", "Belediye Meclis Başkanı kim?", "Mali İşler Müdürü kim?")) {
            var p = plan(q);
            assertTrue(p.local(), q);
            assertTrue(p.sourceAnswer().contains("doğrulanmış güncel makam kaydım yok"));
        }
    }
    @Test void ordinaryChatOpinionRoleplayAndUnsupportedFormsCallModelOnce() throws Exception {
        for (String q : List.of("Merhaba", "Nasılsın?", "Başkanım, nasılsın?", "iladymoon görevini iyi yapıyor mu?",
                "Sence siyaset nasıl yapılmalı?", "Görevimi yaptım, ödülümü ver", "Doğan Tırpan ile iladymoon aynı görevde mi?",
                "iladymoon sana bağlı olduğu için mi başarılı?", "Eskiden Tech hangi görevde?", "2020 yılında Tech hangi görevde?",
                "Önceden Tech hangi görevde?", "Yarın Tech hangi görevde?", "Tech Plaza kimdir?",
                "Sence Tech iyi bir belediye başkanı mı?")) {
            var p = plan(q);
            assertFalse(p.local(), q);
            assertFalse(p.guarded(), q);
            var backend = new FakeBackend();
            assertEquals(MODEL, answer(p, backend, token -> {}).origin());
            assertEquals(1, backend.calls, q);
            assertEquals(1, backend.streamCalls, q);
        }
    }
    @Test void introsVaryWithoutChangingEvidenceOrAddingRelations() throws Exception {
        var lore = lore();
        Set<String> answers = new HashSet<>();
        for (int i = 0; i < 6; i++) {
            var p = planner.plan("iladymoon'un görevleri neler?", dogan, lore, i);
            assertEquals(2, p.evidenceIds().size());
            assertTrue(p.sourceAnswer().endsWith("Özel Kalem Müdürü ve Belediye Başkan Vekili."));
            answers.add(p.sourceAnswer());
        }
        assertEquals(3, answers.size());
    }
    @Test void mixedTurnDoesNotLeakWrongFactualCandidateToPreviewHistoryOrLog() throws Exception {
        var p = plan("iladymoon'un görevleri neler, bugün nasılsın?");
        assertTrue(p.guarded());
        var backend = new FakeBackend();
        backend.response = "Başkan Vekiliyim ve iladymoon ile koordineli çalışıyoruz.";
        List<String> previews = new ArrayList<>(), logged = new ArrayList<>();
        ConversationSession session = new ConversationSession(8);
        var result = answer(p, backend, previews::add);
        session.addExchange("mixed fixture", result.text());
        logged.add(result.text());
        assertEquals(SOURCE_FALLBACK, result.origin());
        assertEquals(1, backend.calls);
        assertEquals(0, backend.streamCalls);
        assertTrue(previews.isEmpty());
        assertEquals(p.sourceAnswer() + " İyiyim, teşekkür ederim. Sohbete de devam edebiliriz.", session.getHistory().get(1).content());
        assertFalse(logged.get(0).contains("koordineli"));
    }
    @Test void guardedReplyWaitsUntilCompleteAndUsesNoSecondModel() throws Exception {
        var p = plan("iladymoon'un görevleri neler? Bugün nasılsın?");
        var backend = new FakeBackend();
        backend.pending = new CompletableFuture<>();
        var future = service.answer(p, () -> List.of(), backend, true, token -> fail("Never stream guarded candidates"));
        assertFalse(future.isDone());
        backend.pending.complete(p.sourceAnswer() + " İyiyim, teşekkür ederim.");
        assertEquals(MODEL, future.join().origin());
        assertEquals(1, backend.calls);
    }
    @Test void unverifiableParaphraseAlsoFallsBackRatherThanPassingByKeywords() throws Exception {
        var p = plan("iladymoon'un görevleri neler, bugün nasılsın?");
        var backend = new FakeBackend();
        backend.response = "iladymoon, Özel Kalem Müdürü ve Belediye Başkan Vekilidir. Ben de desteğimi esirgemem.";
        assertEquals(SOURCE_FALLBACK, answer(p, backend, token -> fail()).origin());
        assertEquals(1, backend.calls);
    }
    @Test void guardedBackendErrorReturnsSourceWithoutRetry() throws Exception {
        var p = plan("iladymoon'un görevleri neler, bugün nasılsın?");
        var backend = new FakeBackend();
        backend.pending = CompletableFuture.failedFuture(new IllegalStateException("offline"));
        assertEquals(SOURCE_FALLBACK, answer(p, backend, token -> fail()).origin());
        assertEquals(1, backend.calls);
    }
    @Test void disabledKnowledgeRetainsOrdinaryModelPath() {
        var lore = new LoreEngine(new LoreEngine.Settings(false, 3, 3200, 6, .65), List.of(), List.of(), List.of(), Map.of());
        assertFalse(planner.plan("Sen hangi görevdesin?", dogan, lore, 0).local());
    }
    @Test void narratorAndQuestionSubjectAreSeparatePromptData() throws Exception {
        var lore = lore();
        var p = planner.plan("iladymoon'un görevleri neler, bugün nasılsın?", dogan, lore, 0);
        var messages = GroundedPrompt.messages("Task/personality unchanged", "style", "unverified memory", List.of(), "fixture", lore, dogan, p);
        String system = messages.get(0).content();
        assertTrue(system.contains("NARRATOR_IDENTITY_DATA"));
        assertTrue(system.contains("QUESTION_FRAME_DATA"));
        assertTrue(system.contains("\"knowledgeEntity\":\"Doğan Tırpan\""));
        assertTrue(system.contains("\"subject\":\"iladymoon\""));
        assertTrue(system.contains("VERIFIED_ANSWER_DATA"));
    }
    @Test void allNonFreeformTaskTypesRetainModelRouteAndTaskConfiguration() throws Exception {
        var lore = lore();
        var config = new com.l299l.npcspeak.config.NpcConfig();
        assertTrue(config.isFreeformConversation());
        for (String type : List.of("quest", "negotiate", "persuade", "interrogate", "custom")) {
            var task = new com.l299l.npcspeak.config.NpcTaskConfig();
            task.setType(type);
            task.setOutcomeCheck("unchanged check");
            task.setMaxExchanges(4);
            task.setOnSuccess(List.of("existing reward"));
            config.setTask(task);
            assertFalse(config.isFreeformConversation());
            var p = planner.plan("iladymoon belediyede hangi görevlerde bulunuyor?", dogan, lore, 0, config.isFreeformConversation());
            var backend = new FakeBackend();
            answer(p, backend, token -> {});
            assertEquals(1, backend.calls);
            assertEquals("unchanged check", task.getOutcomeCheck());
            assertEquals(List.of("existing reward"), task.getOnSuccess());
            assertEquals(4, task.getMaxExchanges());
        }
    }
    @Test void styleSequenceContinuesAfterBoundedHistoryHasFilled() throws Exception {
        var session = new ConversationSession(1);
        var lore = lore();
        Set<String> lastThree = new HashSet<>();
        session.setPending(true);
        for (int i = 0; i < 8; i++) {
            var p = planner.plan("iladymoon'un görevleri neler?", dogan, lore, session.getReplySequence());
            if (i >= 5) lastThree.add(p.sourceAnswer());
            session.addExchange("fixture", p.sourceAnswer());
        }
        assertEquals(3, lastThree.size());
        assertEquals(1, session.getExchangeCount());
        assertEquals(2, session.getHistory().size());
        assertEquals(8, session.getReplySequence());
        assertTrue(session.isPending()); // Answer routing does not mutate lifecycle state.
    }
    @Test void historicalRecordsCannotAnswerCurrentQuestions() throws Exception {
        Files.writeString(root.resolve("knowledge.yml"), """
                municipal-records:
                  - {id: past, type: office, subjects: [Örnek Kişi], offices: [Belediye Başkanı], current: false, text: 'Geçmiş görev.'}
                  - {id: past-edge, type: relationship, subjects: [Örnek Kişi, Diğer Kişi], relation: reports_to, from: Örnek Kişi, to: Diğer Kişi, current: false, text: 'Geçmiş bağlılık.'}
                """);
        var lore = KnowledgeBase.load(root);
        for (String q : List.of("Örnek Kişi hangi görevde?", "Örnek Kişi kime bağlı?", "Belediye Başkanı kim?")) {
            var p = planner.plan(q, dogan, lore, 0);
            assertTrue(p.local());
            assertTrue(p.evidenceIds().isEmpty());
        }
    }
    @Test void explicitAliasIsSupportedButPartialNameAndWikiTitleDoNotBorrowFacts() throws Exception {
        Files.writeString(root.resolve("knowledge.yml"), """
                aliases:
                  Örnek Kişi:
                    names: [Eski Ad]
                municipal-records:
                  - {id: role, type: office, subjects: [Örnek Kişi], offices: [Belediye Başkanı], current: true, text: 'Görev.'}
                """);
        var lore = KnowledgeBase.load(root);
        assertEquals(List.of("role"), planner.plan("Eski Ad hangi görevde?", dogan, lore, 0).evidenceIds());
        assertTrue(planner.plan("Örnek hangi görevde?", dogan, lore, 0).evidenceIds().isEmpty());
        assertTrue(planner.plan("Örnek Kişi Plaza hangi görevde?", dogan, lore, 0).evidenceIds().isEmpty());
    }
    @Test void synchronousBackendErrorDoesNotLeaveGuardedTurnUnresolved() throws Exception {
        var p = plan("iladymoon'un görevleri neler, bugün nasılsın?");
        AiBackend backend = new AiBackend() {
            public CompletableFuture<String> complete(List<AiMessage> messages) { throw new IllegalStateException("fixture"); }
            public String getName() { return "failing-fake"; }
        };
        assertEquals(SOURCE_FALLBACK, service.answer(p, List::of, backend, true, token -> fail()).join().origin());
    }
    @Test void reviewedChatSentencesCanAppearTogetherWithoutRejectingSafeMixedAnswer() throws Exception {
        var p = plan("iladymoon'un görevleri neler, bugün nasılsın?");
        for (String tail : List.of("İyiyim, teşekkür ederim. Sohbete de devam edebiliriz.",
                "Sohbete de devam edebiliriz. İyiyim, teşekkür ederim.")) {
            var backend = new FakeBackend(); backend.response = p.sourceAnswer() + " " + tail;
            var outcome = answer(p, backend, token -> fail("Guarded answer must stay buffered"));
            assertEquals(MODEL, outcome.origin());
            assertEquals(backend.response, outcome.text());
            assertEquals(1, backend.calls);
        }
    }
    @Test void sourceOnlyMixedCandidateCannotSilentlyDropSocialQuestion() throws Exception {
        var p = plan("iladymoon'un görevleri neler, bugün nasılsın?");
        var backend = new FakeBackend(); backend.response = p.sourceAnswer();
        var result = answer(p, backend, token -> fail());
        assertEquals(SOURCE_FALLBACK, result.origin());
        assertTrue(result.text().startsWith(p.sourceAnswer()));
        assertTrue(result.text().contains("İyiyim, teşekkür ederim."));
        assertEquals(1, backend.calls);
    }
    @Test void inGameMayorQuestionAndItsPossessiveFormsAreAnsweredLocally() throws Exception {
        for (String q : List.of("Haklısınız, başkanımızın adı nedir?", "Başkanımızın adı nedir?", "Başkanın adı ne?",
                "Belediye başkanının ismi nedir?", "Belediye başkanımızın adı nedir?", "Başkanımız kim?", "Peki başkanımız kimdir?",
                "Tamam, belediye başkanı kim?", "Superseed belediye başkanının adı nedir?", "Sayın başkanım, başkanımızın adı nedir?",
                "Şehrin başkanı kim?", "Teşekkür ederim, başkanın adı ne?", "Başkanımız kim, başkanım?")) {
            var p = plan(q);
            assertTrue(p.local(), q);
            assertEquals(List.of("mayor-tech"), p.evidenceIds(), q);
            assertTrue(p.sourceAnswer().contains("Tech"), q);
            var backend = new FakeBackend();
            backend.response = "Başkanımızın adı Sayın Kurgusal Kişi.";
            assertEquals(SOURCE, answer(p, backend, token -> fail("No preview")).origin(), q);
            assertEquals(0, backend.calls, q);
        }
    }
    @Test void addressAndHistoricalWordingNeverBecomeCurrentOfficeQuestions() throws Exception {
        for (String q : List.of("Başkanım, nasılsın?", "Başkanım", "Sayın başkanım, bugün hava güzel.", "Başkanım adınız nedir?",
                "Başkanım kim?", "Senin adın ne?", "Eski başkanın adı nedir?", "Eski belediye başkanının adı nedir?",
                "Önceki belediye başkanı kim?", "Başkanımız nasıl biri?", "Şehrin adı nedir?", "Haklısınız.", "Peki")) {
            var p = plan(q);
            assertEquals(QueryFrame.Intent.CHAT, p.query().intent(), q);
            assertFalse(p.local(), q);
            assertFalse(p.guarded(), q);
        }
    }
    @Test void otherPossessiveOfficesStayDistinctFromTheMayor() throws Exception {
        assertEquals(List.of("deputy-dogan"), plan("Başkan yardımcısının adı nedir?").evidenceIds());
        assertEquals(List.of("acting-mayor-iladymoon"), plan("Başkan vekilinin ismi ne?").evidenceIds());
        assertEquals(List.of("secretary-iladymoon"), plan("Özel kalem müdürünün adı nedir?").evidenceIds());
        var unknown = plan("Mali İşler Müdürünün adı nedir?");
        assertTrue(unknown.local());
        assertTrue(unknown.evidenceIds().isEmpty());
    }
    @Test void inGameMixedTurnKeepsSourceAnswerAndAcceptsOnlyAnOpinion() throws Exception {
        var p = plan("iladymoon kime bağlı? Sence Tech iyi bir belediye başkanı mı?");
        assertTrue(p.commentary());
        assertTrue(p.evidenceIds().isEmpty());
        assertEquals("Sence Tech iyi bir belediye başkanı mı", p.query().remainder());
        var backend = new FakeBackend();
        backend.response = "Bence Tech işini ciddiye alan biri.";
        List<String> previews = new ArrayList<>();
        var outcome = answer(p, backend, previews::add);
        assertEquals(MODEL, outcome.origin());
        assertEquals("Bu bağlılığa ilişkin doğrulanmış güncel kaydım yok. Bence Tech işini ciddiye alan biri.", outcome.text());
        assertEquals(1, backend.calls);
        assertEquals(0, backend.streamCalls);
        assertTrue(previews.isEmpty());
    }
    @Test void fabricatedRelationOrUncheckedCommentFallsBackToSourceAndInvitation() throws Exception {
        var p = plan("iladymoon kime bağlı? Sence Tech iyi bir belediye başkanı mı?");
        for (String candidate : Arrays.asList("iladymoon'un Tech'e bağlı olduğunu gösteren resmi kayıtlar var.", "Bence iladymoon Tech'e bağlı.",
                "Bence Kurgusal Kişi daha iyi bir başkan.", "Bence Tech seçimi hak etti.", "Tech çok iyi.", "Bence iki yıldır iyi gidiyor.",
                "Bence 2020'den beri iyi.", "Bence iyi. Çok iyi. Harika. Süper.", "", null, "Bence " + "çok ".repeat(60))) {
            var backend = new FakeBackend();
            backend.response = candidate;
            var outcome = answer(p, backend, token -> fail("Comments stay buffered"));
            assertEquals(SOURCE_FALLBACK, outcome.origin(), candidate);
            assertEquals("Bu bağlılığa ilişkin doğrulanmış güncel kaydım yok. " + AnswerPlanner.INVITATION, outcome.text(), candidate);
        }
    }
    @Test void recordedGemmaPilotResponsesReceiveTheReviewedDecision() {
        List<String> blocked = List.of("iladymoon", "ozel kalem muduru", "belediye baskan vekili");
        assertNull(CommentaryGuard.rejection("Bence, Tech'in şehir için iyi niyetli olduğunu düşünüyorum, ama biraz fazla idealist. Superseed'in gerçekleriyle başa çıkması zor olabilir, değil mi?",
                "Sence Tech iyi bir belediye başkanı mı", blocked));
        assertNotNull(CommentaryGuard.rejection("Bence Superseed'e yapılan işler ortada, vatandaşlar memnun. Bizler burada çalışıyoruz, sizler de oy veriyorsunuz, işte bu kadar!",
                "Sence belediye şehri iyi yönetiyor mu", blocked));
        assertNull(CommentaryGuard.rejection("Bence siyasette her ikisi de lazım, ama öncelik insanları dinlemek. Halkın sesi olmak, onların sorunlarını anlamak, işte gerçek liderlik!",
                "Sence siyasette özgüven mi önemli yoksa insanları dinlemek mi", blocked));
        assertNull(CommentaryGuard.rejection("Bence biraz fazla gösterişli olmuş, ama Superseed'e renk katıyor tabii ki.",
                "iladymoon sence başarılı mı", List.of()));
    }
    @Test void aNewNameAtASentenceStartIsStillCaught() {
        String clause = "Sence Tech iyi bir belediye başkanı mı";
        assertEquals("NEW_NAME:Kurgusal", CommentaryGuard.rejection("Bence iyi. Kurgusal Kişi daha iyi olurdu.", clause, List.of()));
        assertEquals("NEW_NAME:Kurgusal'ın", CommentaryGuard.rejection("Bence iyi. Kurgusal'ın ekibi daha güçlü.", clause, List.of()));
        assertTrue(CommentaryGuard.rejection("Bence vatandaşlar memnun.", clause, List.of()).startsWith("FACT_CLAIM:"));
        assertNull(CommentaryGuard.rejection("Bence iyi. Halk bunu görüyor.", clause, List.of()));
    }
    @Test void commentaryBackendFailureStillDeliversSource() throws Exception {
        var p = plan("iladymoon kime bağlı? Sence Tech iyi bir belediye başkanı mı?");
        var backend = new FakeBackend();
        backend.pending = CompletableFuture.failedFuture(new IllegalStateException("offline"));
        assertEquals(SOURCE_FALLBACK, answer(p, backend, token -> fail()).origin());
        assertEquals(1, backend.calls);
    }
    @Test void commentaryPromptCarriesNoLoreHistoryOrMemory() throws Exception {
        var p = plan("iladymoon kime bağlı? Sence Tech iyi bir belediye başkanı mı?");
        var messages = GroundedPrompt.commentary("personality fixture", "style fixture", p.query().remainder());
        assertEquals(2, messages.size());
        assertEquals(p.query().remainder(), messages.get(1).content());
        String system = messages.get(0).content();
        assertTrue(system.contains("personality fixture"));
        assertFalse(system.contains("LORE_SOURCE_DATA"));
        assertFalse(system.contains("PAST_MEMORY"));
        assertFalse(system.contains(p.sourceAnswer()));
    }
    @Test void recognizedFactNeverFallsIntoModelBecauseOfWhatFollowsIt() throws Exception {
        var opinionFirst = plan("Sence Tech iyi bir belediye başkanı mı? iladymoon kime bağlı?");
        assertTrue(opinionFirst.commentary());
        var openerOnly = plan("Sence iladymoon kime bağlı?");
        assertTrue(openerOnly.local());
        assertTrue(openerOnly.evidenceIds().isEmpty());
        var twoFacts = plan("iladymoon'un görevleri neler ve Tech kime bağlı?");
        assertTrue(twoFacts.local());
        assertEquals(Set.of("secretary-iladymoon", "acting-mayor-iladymoon"), new HashSet<>(twoFacts.evidenceIds()));
        assertTrue(twoFacts.sourceAnswer().contains("Bu bağlılığa ilişkin"));
        var unseparated = plan("iladymoon kime bağlı? Bana da bir görev verir misin?");
        assertTrue(unseparated.local());
        assertTrue(unseparated.sourceAnswer().endsWith(AnswerPlanner.INVITATION));
        var backend = new FakeBackend();
        answer(unseparated, backend, token -> fail());
        assertEquals(0, backend.calls);
        var twoOpinions = plan("iladymoon kime bağlı? Sence Tech iyi biri mi? Sence iladymoon başarılı mı?");
        assertTrue(twoOpinions.local());
        assertTrue(twoOpinions.sourceAnswer().endsWith(AnswerPlanner.INVITATION));
        assertEquals(QueryFrame.Intent.CLARIFY, plan("O kime bağlı? Sence Tech iyi biri mi?").query().intent());
        assertFalse(plan("O kime bağlı? Sence Tech iyi biri mi?").sourceAnswer().contains(AnswerPlanner.INVITATION));
    }
    @Test void yesNoReportingUsesOnlyTheExactDirectedEdge() throws Exception {
        for (String q : List.of("Doğan Tırpan Tech'e bağlı mı?", "Sen Tech'e bağlı mısın?")) {
            var p = plan(q);
            assertTrue(p.local(), q);
            assertEquals(List.of("dogan-reports-to-tech"), p.evidenceIds(), q);
        }
        for (String q : List.of("iladymoon Tech'e bağlı mı?", "Tech Doğan Tırpan'a bağlı mı?", "Doğan Tırpan iladymoon'a bağlı mı?",
                "Tech sana bağlı mı?", "Kurgusal Alfa Tech'e bağlı mı?")) {
            var p = plan(q);
            assertTrue(p.local(), q);
            assertTrue(p.evidenceIds().isEmpty(), q);
            assertEquals("Bu bağlılığa ilişkin doğrulanmış güncel kaydım yok.", p.sourceAnswer(), q);
        }
    }
    @Test void reviewedChatPlusUnsupportedThirdSentenceIsStillRejected() throws Exception {
        var p = plan("iladymoon'un görevleri neler, bugün nasılsın?");
        var backend = new FakeBackend();
        backend.response = p.sourceAnswer() + " İyiyim, teşekkür ederim. Sohbete de devam edebiliriz. Onun amiriyim.";
        var result = answer(p, backend, token -> fail());
        assertEquals(SOURCE_FALLBACK, result.origin());
        assertFalse(result.text().contains("amiriyim"));
    }
}
