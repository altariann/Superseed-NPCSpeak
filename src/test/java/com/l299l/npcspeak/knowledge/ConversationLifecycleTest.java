package com.l299l.npcspeak.knowledge;

import com.l299l.npcspeak.ai.*;
import com.l299l.npcspeak.conversation.*;
import com.l299l.npcspeak.config.*;
import com.l299l.npcspeak.cooldown.CooldownManager;
import com.l299l.npcspeak.logging.ConversationLogger;
import com.l299l.npcspeak.memory.NpcMemoryManager;
import com.l299l.npcspeak.npc.NpcData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Server;
import org.bukkit.configuration.file.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.*;
import org.bukkit.scheduler.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

/** Production manager with a queued Bukkit facade, not a running Minecraft server. */
class ConversationLifecycleTest {
    @TempDir Path root;
    private boolean main = true;
    private final Deque<Runnable> queue = new ArrayDeque<>();
    private final List<Timer> timers = new ArrayList<>();
    private final List<String> logged = new ArrayList<>();
    private final FakeBackend backend = new FakeBackend();
    private TestPlugin plugin;
    private ConversationManager manager;
    private NpcData npc;

    private static final class Timer { Runnable action; boolean cancelled; }
    private final class FakeBackend implements AiBackend {
        final List<CompletableFuture<String>> replies = new ArrayList<>();
        Consumer<String> tokens;
        public CompletableFuture<String> complete(List<AiMessage> messages) {
            assertTrue(main, "Prompt/PAPI and dispatch must run on main thread");
            var future = new CompletableFuture<String>(); replies.add(future); return future;
        }
        public CompletableFuture<String> streamComplete(List<AiMessage> messages, Consumer<String> callback) {
            tokens = callback; return complete(messages);
        }
        public String getName() { return "fake"; }
    }
    @SuppressWarnings({"deprecation", "removal"})
    private static final class TestPlugin extends JavaPlugin {
        final FileConfiguration config = new YamlConfiguration();
        TestPlugin(Server server, File folder) {
            super(new JavaPluginLoader(server), new PluginDescriptionFile("Fixture", "1", "Fixture"), folder, new File(folder, "fixture.jar"));
        }
        @Override public FileConfiguration getConfig() { return config; }
    }
    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }
    private static Object zero(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        return 0;
    }
    private void setup(int cooldown) {
        BukkitScheduler scheduler = proxy(BukkitScheduler.class, (p, method, args) -> {
            assertTrue(main || method.getName().equals("runTask"), "Bukkit lifecycle must run on main");
            if (method.getName().equals("runTask")) { queue.addLast((Runnable) args[1]); return task(null); }
            if (method.getName().equals("runTaskLater")) {
                Timer timer = new Timer(); timer.action = (Runnable) args[1]; timers.add(timer); return task(timer);
            }
            return zero(method.getReturnType());
        });
        Server server = proxy(Server.class, (p, method, args) -> switch (method.getName()) {
            case "getScheduler" -> scheduler;
            case "isPrimaryThread" -> main;
            case "getLogger" -> Logger.getLogger("fixture");
            case "getName" -> "fixture";
            default -> zero(method.getReturnType());
        });
        plugin = new TestPlugin(server, root.toFile());
        plugin.config.set("conversation.single-player", true);
        plugin.config.set("conversation.echo-player-message", false);
        ConversationLogger logger = new ConversationLogger(root.toFile(), "") {
            @Override public void log(String id, String player, String input, String response) {
                assertTrue(main); logged.add(player + ":" + response);
            }
        };
        manager = new ConversationManager(plugin, backend, new CooldownManager(), new TaskManager(),
                new NpcMemoryManager(root.toFile()), false, 1, true, ModerationConfig.DISABLED, logger, null, 8, cooldown, 60);
        NpcConfig config = new NpcConfig(); config.setLogConversations(true);
        npc = new NpcData("fixture", "Fixture NPC", null, null, config);
    }
    private BukkitTask task(Timer timer) {
        return proxy(BukkitTask.class, (p, method, args) -> {
            if (method.getName().equals("cancel") && timer != null) { assertTrue(main); timer.cancelled = true; }
            return zero(method.getReturnType());
        });
    }
    private final class Visitor {
        final UUID id = UUID.randomUUID();
        boolean online = true;
        final List<String> messages = new ArrayList<>();
        final Player player;
        Visitor(String name) {
            player = proxy(Player.class, (p, method, args) -> switch (method.getName()) {
                case "getUniqueId" -> id;
                case "getName" -> name;
                case "isOnline" -> { assertTrue(main); yield online; }
                case "sendMessage", "sendActionBar" -> {
                    assertTrue(main, "No player output off main");
                    if (args != null) for (Object arg : args) if (arg instanceof Component c)
                        messages.add(PlainTextComponentSerializer.plainText().serialize(c));
                    yield null;
                }
                case "toString" -> name;
                case "hashCode" -> id.hashCode();
                case "equals" -> p == args[0];
                default -> zero(method.getReturnType());
            });
        }
    }
    private void async(Runnable action) { main = false; try { action.run(); } finally { main = true; } }
    private void drain() { assertTrue(main); while (!queue.isEmpty()) queue.removeFirst().run(); }

    @Test void asyncAdmissionAndRapidMessagesAreSerializedUntilDelivery() {
        setup(0); var a = new Visitor("a"); manager.startConversation(a.player, npc);
        async(() -> { manager.handleChatInput(a.player, "hello"); manager.handleChatInput(a.player, "second"); });
        assertEquals(0, backend.replies.size());
        drain(); assertEquals(1, backend.replies.size());
        async(() -> backend.replies.get(0).complete("first answer"));
        assertEquals(0, manager.getSessionExchangeCount(a.id, npc.getId()));
        manager.handleChatInput(a.player, "third before delivery");
        assertEquals(1, backend.replies.size());
        drain();
        assertEquals(1, manager.getSessionExchangeCount(a.id, npc.getId()));
        assertEquals(List.of("a:first answer"), logged);
    }
    @Test void secondPlayerCannotOccupyExclusiveNpcAndLateAnswerCannotLeakToNewVisitor() {
        setup(0); var a = new Visitor("a"); var b = new Visitor("b");
        manager.startConversation(a.player, npc); manager.startConversation(b.player, npc);
        assertFalse(manager.isInConversation(b.id));
        manager.handleChatInput(a.player, "hello");
        manager.startConversation(a.player, npc); // Toggle closes visit.
        manager.startConversation(b.player, npc);
        async(() -> backend.replies.get(0).complete("stale answer")); drain();
        assertTrue(manager.isInConversation(b.id));
        assertTrue(logged.isEmpty());
        assertFalse(a.messages.stream().anyMatch(s -> s.contains("stale answer")));
        assertFalse(b.messages.stream().anyMatch(s -> s.contains("stale answer")));
    }
    @Test void queuedInputFromClosedVisitDoesNotJoinReopenedVisit() {
        setup(0); var a = new Visitor("a"); manager.startConversation(a.player, npc);
        async(() -> manager.handleChatInput(a.player, "old input"));
        manager.startConversation(a.player, npc); manager.startConversation(a.player, npc);
        drain(); assertEquals(0, backend.replies.size());
    }
    @Test void responseCompletedBeforeClosingButQueuedForDeliveryIsStillDropped() {
        setup(0); var a = new Visitor("a"); manager.startConversation(a.player, npc);
        manager.handleChatInput(a.player, "hello");
        async(() -> backend.replies.get(0).complete("closed before delivery"));
        manager.startConversation(a.player, npc); drain();
        assertTrue(logged.isEmpty());
        assertEquals(0, manager.getSessionExchangeCount(a.id, npc.getId()));
    }
    @Test void quitAndLateFailureCannotCreateNewTimeoutOrErrorMessage() {
        setup(0); var a = new Visitor("a"); manager.startConversation(a.player, npc);
        manager.handleChatInput(a.player, "hello"); manager.clearPlayer(a.id); a.online = false;
        int count = a.messages.size(), timeoutCount = timers.size();
        async(() -> backend.replies.get(0).completeExceptionally(new IllegalStateException("late"))); drain();
        assertEquals(count, a.messages.size()); assertEquals(timeoutCount, timers.size()); assertTrue(logged.isEmpty());
    }
    @Test void twoNonExclusiveVisitorsHaveIndependentPendingAndHistory() {
        setup(0); npc.getConfig().setSinglePlayer(false);
        var a = new Visitor("a"); var b = new Visitor("b");
        manager.startConversation(a.player, npc); manager.startConversation(b.player, npc);
        manager.handleChatInput(a.player, "a"); manager.handleChatInput(b.player, "b");
        assertEquals(2, backend.replies.size());
        async(() -> { backend.replies.get(1).complete("b answer"); backend.replies.get(0).complete("a answer"); }); drain();
        assertEquals(1, manager.getSessionExchangeCount(a.id, npc.getId()));
        assertEquals(1, manager.getSessionExchangeCount(b.id, npc.getId()));
        assertFalse(a.messages.stream().anyMatch(s -> s.contains("b answer")));
    }
    @Test void cooldownStillRejectsNewMessageAfterDelivery() {
        setup(60_000); var a = new Visitor("a"); manager.startConversation(a.player, npc);
        manager.handleChatInput(a.player, "first"); backend.replies.get(0).complete("answer"); drain();
        manager.handleChatInput(a.player, "too soon"); assertEquals(1, backend.replies.size());
    }
    @Test void staleStreamingPreviewAfterCompletionDoesNotOverwriteNextTurn() {
        setup(0); var a = new Visitor("a"); manager.startConversation(a.player, npc);
        manager.handleChatInput(a.player, "first"); Consumer<String> oldTokens = backend.tokens;
        backend.replies.get(0).complete("answer"); drain();
        manager.handleChatInput(a.player, "next"); int before = a.messages.size();
        async(() -> oldTokens.accept("stale preview")); drain();
        assertEquals(before, a.messages.size());
    }
    @Test void completedLocalAnswerStillHoldsPendingUntilMainThreadDelivery() throws Exception {
        setup(0);
        Files.writeString(root.resolve("knowledge.yml"), """
                municipal-records:
                  - {id: secretary, type: office, subjects: [iladymoon], offices: [Özel Kalem Müdürü], current: true, text: 'Test kaydı.'}
                  - {id: acting, type: office, subjects: [iladymoon], offices: [Belediye Başkan Vekili], current: true, text: 'Test kaydı.'}
                """);
        assertTrue(KnowledgeBase.reloadFrom(root));
        try {
            var a = new Visitor("a"); manager.startConversation(a.player, npc);
            async(() -> {
                manager.handleChatInput(a.player, "iladymoon belediyede hangi görevlerde bulunuyor?");
                manager.handleChatInput(a.player, "iladymoon'un görevleri neler?");
            });
            drain();
            assertEquals(0, backend.replies.size());
            assertEquals(1, manager.getSessionExchangeCount(a.id, npc.getId()));
            assertEquals(1, logged.size());
            assertTrue(logged.get(0).contains("Özel Kalem Müdürü ve Belediye Başkan Vekili"));
        } finally {
            Files.writeString(root.resolve("knowledge.yml"), "enabled: true\n");
            assertTrue(KnowledgeBase.reloadFrom(root));
        }
    }
    @Test void cancelledTimeoutFromPreviousVisitCannotCloseReopenedVisit() {
        setup(0); var a = new Visitor("a"); manager.startConversation(a.player, npc);
        Timer old = timers.get(0);
        manager.startConversation(a.player, npc); manager.startConversation(a.player, npc);
        assertTrue(old.cancelled);
        int count = a.messages.size();
        old.action.run(); // Simulates a cancelled task already queued by the scheduler.
        assertTrue(manager.isInConversation(a.id));
        assertEquals(count, a.messages.size());
    }
    @Test void cancelledTimeoutCannotClosePendingOrLaterTurnOfSameVisit() {
        setup(0); var a = new Visitor("a"); manager.startConversation(a.player, npc);
        Timer old = timers.get(0);
        manager.handleChatInput(a.player, "hello");
        old.action.run(); assertTrue(manager.isInConversation(a.id));
        backend.replies.get(0).complete("answer"); drain();
        old.action.run(); assertTrue(manager.isInConversation(a.id));
    }
    @Test void currentTimeoutStillEndsTheVisit() {
        setup(0); var a = new Visitor("a"); manager.startConversation(a.player, npc);
        timers.get(0).action.run();
        assertFalse(manager.isInConversation(a.id));
        assertTrue(a.messages.stream().anyMatch(s -> s.contains("doldu")));
    }
    @Test void guardedRejectedCandidateNeverReachesManagerOutputOrLogger() throws Exception {
        setup(0);
        Files.writeString(root.resolve("knowledge.yml"), """
                municipal-records:
                  - {id: secretary, type: office, subjects: [iladymoon], offices: [Özel Kalem Müdürü], current: true, text: 'Test kaydı.'}
                  - {id: acting, type: office, subjects: [iladymoon], offices: [Belediye Başkan Vekili], current: true, text: 'Test kaydı.'}
                """);
        assertTrue(KnowledgeBase.reloadFrom(root));
        try {
            var a = new Visitor("a"); manager.startConversation(a.player, npc);
            manager.handleChatInput(a.player, "iladymoon'un görevleri neler, bugün nasılsın?");
            assertNull(backend.tokens, "Guarded reply must not open a streaming preview");
            async(() -> backend.replies.get(0).complete("Başkan Vekiliyim ve iladymoon'un amiriyim."));
            assertTrue(logged.isEmpty());
            drain();
            assertEquals(1, logged.size());
            assertTrue(logged.get(0).contains("İyiyim, teşekkür ederim."));
            assertFalse(logged.get(0).contains("amiriyim"));
            assertFalse(a.messages.stream().anyMatch(s -> s.contains("amiriyim")));
            assertEquals(1, manager.getSessionExchangeCount(a.id, npc.getId()));
        } finally {
            Files.writeString(root.resolve("knowledge.yml"), "enabled: true\n");
            assertTrue(KnowledgeBase.reloadFrom(root));
        }
    }
}
