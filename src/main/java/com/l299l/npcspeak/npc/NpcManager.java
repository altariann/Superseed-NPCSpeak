package com.l299l.npcspeak.npc;

import com.l299l.npcspeak.config.NpcConfigLoader;
import com.l299l.npcspeak.knowledge.KnowledgeBase;
import com.l299l.npcspeak.knowledge.HybridReadiness;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class NpcManager {

    private final JavaPlugin plugin;
    private final NpcConfigLoader configLoader;
    private final NpcProvider provider;
    private final Map<String, NpcData> byId = new HashMap<>();
    private final Map<UUID, NpcData> byEntityUUID = new HashMap<>();

    public NpcManager(JavaPlugin plugin, NpcProvider provider) {
        this.plugin = plugin;
        this.provider = provider;
        this.configLoader = new NpcConfigLoader(new File(plugin.getDataFolder(), "npcs"));
    }

    public void loadAll() {
        for (NpcData data : configLoader.loadAll(plugin.getLogger())) {
            byId.put(data.getId(), data);
            provider.restoreOrSpawn(data);
            trackEntityUUID(data);
            trySave(data);
            if (data.getConfig().getKnowledgeEntity() != null && KnowledgeBase.wasLastReloadSuccessful()) {
                try {
                    var jar = java.nio.file.Path.of(HybridReadiness.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                    plugin.getLogger().info(HybridReadiness.marker(data.getId(), data.getConfig().getKnowledgeEntity(),
                            data.getCitizensId(), data.getConfig().isFreeformConversation(), KnowledgeBase.snapshot(),
                            HybridReadiness.sha256(plugin.getDataFolder().toPath().resolve("knowledge.yml")), HybridReadiness.sha256(jar))
                            + " NPC_SHA256 " + HybridReadiness.sha256(configLoader.getFile(data.getId()).toPath()));
                } catch (Exception error) {
                    // Never log YAML, entity labels, source text, paths or exception contents.
                    plugin.getLogger().warning("Hybrid readiness unavailable; check the private deployment validation.");
                }
            }
        }
    }

    public void saveAll() {
        for (NpcData data : byId.values()) {
            trySave(data);
        }
    }

    public void add(NpcData data) throws IOException {
        configLoader.save(data);
        byId.put(data.getId(), data);
        provider.restoreOrSpawn(data);
        trackEntityUUID(data);
        configLoader.save(data);
    }

    public void remove(String id) {
        NpcData data = byId.remove(id);
        if (data == null) return;
        if (data.getEntityUUID() != null) byEntityUUID.remove(data.getEntityUUID());
        provider.despawn(data);
        configLoader.delete(id);
    }

    public void reloadAll() {
        // Reload only live-adjustable configuration (e.g. single-player sessions).
        plugin.reloadConfig();
        NpcMessages.reload();
        KnowledgeBase.reload();
        // Never destroy Citizens NPCs on a configuration reload.
        // The existing Citizens identity, skin, sitting pose, and traits must survive.
        byId.clear();
        byEntityUUID.clear();
        loadAll();
    }

    public boolean exists(String id) {
        return byId.containsKey(id) || configLoader.exists(id);
    }

    public void renameNpc(String id, String newName) throws IOException {
        NpcData data = byId.get(id);
        if (data == null) throw new IllegalArgumentException("No NPC with id '" + id + "'");
        data.setDisplayName(newName);
        provider.rename(data, newName);
        configLoader.save(data);
    }

    public void saveNpc(NpcData data) throws IOException {
        configLoader.save(data);
    }

    private void trackEntityUUID(NpcData data) {
        if (data.getEntityUUID() != null) {
            byEntityUUID.put(data.getEntityUUID(), data);
        }
    }

    private void trySave(NpcData data) {
        try {
            configLoader.save(data);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save NPC '" + data.getId() + "': " + e.getMessage());
        }
    }

    public NpcData getByEntityUUID(UUID uuid) { return byEntityUUID.get(uuid); }
    public NpcData getById(String id) { return byId.get(id); }
    public int count() { return byId.size(); }
    public Collection<NpcData> getAll() { return Collections.unmodifiableCollection(byId.values()); }
    public Collection<String> getAllIds() { return Collections.unmodifiableSet(byId.keySet()); }
    public NpcConfigLoader getConfigLoader() { return configLoader; }
    public NpcProvider getProvider() { return provider; }
}
