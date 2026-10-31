package com.l299l.npcspeak;

import com.l299l.npcspeak.ai.AiBackend;
import com.l299l.npcspeak.ai.BackendFactory;
import com.l299l.npcspeak.command.NpcCommand;
import com.l299l.npcspeak.command.NpcCommandCompleter;
import com.l299l.npcspeak.config.ModerationConfig;
import com.l299l.npcspeak.config.PluginConfig;
import com.l299l.npcspeak.conversation.ConversationListener;
import com.l299l.npcspeak.conversation.ConversationManager;
import com.l299l.npcspeak.conversation.TaskManager;
import com.l299l.npcspeak.cooldown.CooldownManager;
import com.l299l.npcspeak.logging.ConversationLogger;
import com.l299l.npcspeak.npc.BuiltinNpcProvider;
import com.l299l.npcspeak.npc.CitizensNpcListener;
import com.l299l.npcspeak.npc.CitizensNpcProvider;
import com.l299l.npcspeak.npc.NPCSpeakTrait;
import com.l299l.npcspeak.npc.NpcListener;
import com.l299l.npcspeak.npc.NpcManager;
import com.l299l.npcspeak.npc.NpcMessages;
import com.l299l.npcspeak.knowledge.KnowledgeBase;
import com.l299l.npcspeak.npc.NpcProvider;
import com.l299l.npcspeak.memory.NpcMemoryManager;
import com.l299l.npcspeak.papi.NpcSpeakExpansion;
import com.l299l.npcspeak.papi.PapiExpander;
import com.l299l.npcspeak.vault.VaultHook;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.trait.TraitInfo;
import org.bukkit.plugin.java.JavaPlugin;

public final class NPCSpeak extends JavaPlugin {

    private PluginConfig pluginConfig;
    private NpcManager npcManager;
    private ConversationManager conversationManager;

    @Override
    public void onEnable() {
        pluginConfig = new PluginConfig(this);
        NpcMessages.initialize(this);
        KnowledgeBase.initialize(this);

        AiBackend backend;
        try {
            backend = BackendFactory.create(pluginConfig);
            getLogger().info("Using AI backend: " + backend.getName());
        } catch (IllegalArgumentException e) {
            getLogger().severe(e.getMessage() + " — disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        NpcMemoryManager memoryManager = new NpcMemoryManager(getDataFolder());
        boolean memorySummaryEnabled = pluginConfig.isMemoryEnabled();
        if (memorySummaryEnabled) {
            getLogger().info("NPC memory enabled (max-age: " + pluginConfig.getMemoryMaxAgeDays() + " days).");
        }

        boolean streamingEnabled = pluginConfig.isStreamingEnabled();
        if (streamingEnabled) getLogger().info("Streaming mode enabled.");

        ModerationConfig moderation = pluginConfig.getModerationConfig();
        if (moderation.enabled()) getLogger().info("Content moderation enabled.");

        ConversationLogger conversationLogger = new ConversationLogger(getDataFolder(), moderation.discordWebhook());

        VaultHook vault = VaultHook.init(this);

        conversationManager = new ConversationManager(
                this, backend, new CooldownManager(), new TaskManager(),
                memoryManager, memorySummaryEnabled, pluginConfig.getMemoryMaxAgeDays(),
                streamingEnabled, moderation, conversationLogger, vault,
                pluginConfig.getMaxConversationExchanges(),
                pluginConfig.getCooldownSeconds(),
                pluginConfig.getListenTimeoutSeconds()
        );

        NpcProvider provider = createProvider();
        npcManager = new NpcManager(this, provider);
        if (provider instanceof CitizensNpcProvider) {
            // Citizens reads saves.yml one tick AFTER plugin enable (CitizensLoadTask).
            // Loading NPCSpeak NPCs immediately here creates duplicate Steve-skin NPCs.
            getServer().getScheduler().runTaskLater(this, () -> {
                if (!isEnabled()) return;
                npcManager.loadAll();
                getLogger().info("Loaded " + npcManager.count() + " NPC(s) after Citizens initialized its registry.");
            }, 2L);
        } else {
            npcManager.loadAll();
        }

        getServer().getPluginManager().registerEvents(new NpcListener(npcManager, conversationManager), this);
        getServer().getPluginManager().registerEvents(new ConversationListener(conversationManager), this);

        if (provider instanceof CitizensNpcProvider) {
            getServer().getPluginManager().registerEvents(
                    new CitizensNpcListener(npcManager, conversationManager), this);
        }

        NpcCommand npcCommand = new NpcCommand(npcManager, memoryManager, conversationManager, conversationLogger);
        getCommand("npcspeak").setExecutor(npcCommand);
        getCommand("npcspeak").setTabCompleter(new NpcCommandCompleter(npcManager));

        PapiExpander.init(this);
        if (PapiExpander.isAvailable()) {
            new NpcSpeakExpansion(conversationManager, getPluginMeta().getVersion()).register();
        }

        getLogger().info("Enabled with " + npcManager.count() + " NPC(s) loaded (provider: " + provider.getName() + ").");
    }

    @Override
    public void onDisable() {
        if (npcManager != null) {
            npcManager.saveAll();
        }
    }

    private NpcProvider createProvider() {
        String configured = pluginConfig.getNpcProvider();
        if ("citizens".equalsIgnoreCase(configured)) {
            if (getServer().getPluginManager().isPluginEnabled("Citizens")) {
                CitizensAPI.getTraitFactory().registerTrait(
                        TraitInfo.create(NPCSpeakTrait.class).withName("npcspeak"));
                getLogger().info("Citizens2 detected — using Citizens NPC provider.");
                return new CitizensNpcProvider();
            }
            getLogger().warning("npc-provider is 'citizens' but Citizens2 is not installed — falling back to built-in.");
        }
        return new BuiltinNpcProvider(this);
    }

    public PluginConfig getPluginConfig() { return pluginConfig; }
    public NpcManager getNpcManager() { return npcManager; }
    public ConversationManager getConversationManager() { return conversationManager; }
}
