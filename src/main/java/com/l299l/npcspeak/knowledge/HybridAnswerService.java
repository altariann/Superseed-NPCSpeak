package com.l299l.npcspeak.knowledge;

import com.l299l.npcspeak.ai.AiBackend;
import com.l299l.npcspeak.ai.AiMessage;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import static com.l299l.npcspeak.knowledge.AnswerOutcome.Origin.*;

/** No retries and no verifier model. Guarded candidates never reach a token consumer. */
public final class HybridAnswerService {
    // Reviewed non-municipal replies for the limited mixed-turn option. Arbitrary prose is not certified.
    private static final List<String> SAFE_CHAT = List.of("İyiyim, teşekkür ederim.", "Sohbete de devam edebiliriz.");

    public CompletableFuture<AnswerOutcome> answer(AnswerPlan plan, Supplier<List<AiMessage>> messages,
                                                  AiBackend backend, boolean streaming, Consumer<String> preview) {
        if (plan.local()) return CompletableFuture.completedFuture(new AnswerOutcome(plan.sourceAnswer(), SOURCE));
        List<AiMessage> input;
        try { input = messages.get(); }
        catch (RuntimeException error) { return failedOrFallback(plan, error); }
        if (plan.guarded()) {
            CompletableFuture<String> candidateFuture;
            try { candidateFuture = backend.complete(input); }
            catch (RuntimeException error) { return failedOrFallback(plan, error); }
            return candidateFuture.handle((candidate, error) -> {
                if (error == null && supported(candidate, plan.sourceAnswer())) return new AnswerOutcome(candidate.strip(), MODEL);
                return fallback(plan);
            });
        }
        try {
            return (streaming ? backend.streamComplete(input, preview) : backend.complete(input))
                    .thenApply(text -> new AnswerOutcome(text, MODEL));
        } catch (RuntimeException error) { return CompletableFuture.failedFuture(error); }
    }

    private static CompletableFuture<AnswerOutcome> failedOrFallback(AnswerPlan plan, RuntimeException error) {
        return plan.guarded() ? CompletableFuture.completedFuture(fallback(plan)) : CompletableFuture.failedFuture(error);
    }
    private static AnswerOutcome fallback(AnswerPlan plan) {
        return new AnswerOutcome(plan.sourceAnswer() + " " + SAFE_CHAT.get(0) + " " + SAFE_CHAT.get(1), SOURCE_FALLBACK);
    }

    private static boolean supported(String candidate, String sourceAnswer) {
        if (candidate == null) return false;
        String text = candidate.strip();
        // A guarded turn includes a social request: a source-only answer silently loses it.
        // Accept exactly the reviewed sentences, singly or together, never arbitrary paraphrases.
        return SAFE_CHAT.stream().anyMatch(tail -> text.equals(sourceAnswer + " " + tail))
                || text.equals(sourceAnswer + " " + SAFE_CHAT.get(0) + " " + SAFE_CHAT.get(1))
                || text.equals(sourceAnswer + " " + SAFE_CHAT.get(1) + " " + SAFE_CHAT.get(0));
    }
}
