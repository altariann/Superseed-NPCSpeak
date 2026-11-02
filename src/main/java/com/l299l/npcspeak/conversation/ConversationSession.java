package com.l299l.npcspeak.conversation;

import com.l299l.npcspeak.ai.AiMessage;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public class ConversationSession {

    private final ArrayDeque<AiMessage> history = new ArrayDeque<>();
    private final int maxExchanges;
    private volatile boolean pending = false;
    private int replySequence;

    public ConversationSession(int maxExchanges) {
        this.maxExchanges = maxExchanges;
    }

    public synchronized void addExchange(String userMessage, String assistantMessage) {
        replySequence++;
        history.addLast(new AiMessage("user", userMessage));
        history.addLast(new AiMessage("assistant", assistantMessage));
        while (history.size() > maxExchanges * 2) {
            history.pollFirst();
            history.pollFirst();
        }
    }

    public synchronized List<AiMessage> getHistory() {
        return new ArrayList<>(history);
    }

    public synchronized int getExchangeCount() {
        return history.size() / 2;
    }

    /** Independent of history trimming; stylistic variation must not freeze at maxExchanges. */
    public synchronized int getReplySequence() { return replySequence; }

    public boolean isPending() { return pending; }
    public void setPending(boolean pending) { this.pending = pending; }
}
