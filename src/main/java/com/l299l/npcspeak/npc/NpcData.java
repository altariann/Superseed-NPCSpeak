package com.l299l.npcspeak.npc;

import com.l299l.npcspeak.config.NpcConfig;
import com.l299l.npcspeak.config.NpcTaskConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class NpcData {

    private final String id;
    private String displayName;
    private UUID entityUUID;
    private int citizensId = -1;
    private Location spawnLocation;
    private NpcConfig config;

    public NpcData(String id, String displayName, UUID entityUUID, Location spawnLocation, NpcConfig config) {
        this.id = id;
        this.displayName = displayName;
        this.entityUUID = entityUUID;
        this.spawnLocation = spawnLocation;
        this.config = config;
    }

    public static NpcData fromFile(String id, File file) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        String displayName = yaml.getString("display-name", id);

        UUID entityUUID = null;
        String uuidStr = yaml.getString("entity-uuid");
        if (uuidStr != null && !uuidStr.isEmpty()) {
            try {
                entityUUID = UUID.fromString(uuidStr);
            } catch (IllegalArgumentException ignored) {}
        }

        int citizensId = yaml.getInt("citizens-id", -1);

        Location location = null;
        if (yaml.isConfigurationSection("location")) {
            String worldName = yaml.getString("location.world");
            World world = worldName != null ? Bukkit.getWorld(worldName) : null;
            if (world != null) {
                location = new Location(world,
                        yaml.getDouble("location.x"),
                        yaml.getDouble("location.y"),
                        yaml.getDouble("location.z"),
                        (float) yaml.getDouble("location.yaw", 0.0),
                        (float) yaml.getDouble("location.pitch", 0.0));
            }
        }

        NpcConfig config = new NpcConfig();
        config.setKnowledgeEntity(yaml.getString("knowledge-entity"));
        if (yaml.isConfigurationSection("personality")) {
            config.setGreeting(yaml.getString("personality.greeting"));
            config.setSystemPrompt(yaml.getString("personality.system-prompt"));
            config.setTopic(yaml.getString("personality.topic"));
            config.setAvoid(yaml.getString("personality.avoid"));
            if (yaml.isSet("personality.memory-enabled"))
                config.setMemoryEnabled(yaml.getBoolean("personality.memory-enabled"));
            if (yaml.isSet("personality.single-player"))
                config.setSinglePlayer(yaml.getBoolean("personality.single-player"));
            config.setMaxInteractions(yaml.getInt("personality.max-interactions", -1));
            config.setLockAfterTask(yaml.getBoolean("personality.lock-after-task", false));
            config.setLogConversations(yaml.getBoolean("personality.log-conversations", false));
            if (yaml.isConfigurationSection("personality.task")) {
                NpcTaskConfig task = new NpcTaskConfig();
                task.setType(yaml.getString("personality.task.type", "freeform"));
                task.setGoal(yaml.getString("personality.task.goal"));
                task.setOutcomeCheck(yaml.getString("personality.task.outcome-check"));
                task.setRequire(yaml.getStringList("personality.task.require"));
                task.setOnSuccess(yaml.getStringList("personality.task.on-success"));
                task.setOnFailure(yaml.getStringList("personality.task.on-failure"));
                task.setMaxExchanges(yaml.getInt("personality.task.max-exchanges", -1));
                task.setDifficulty(yaml.getInt("personality.task.difficulty", 3));
                task.setIntelligentQuantity(yaml.getBoolean("personality.task.intelligent-quantity", false));
                task.setShowButtons(yaml.getBoolean("personality.task.show-buttons", true));
                config.setTask(task);
            }
        }

        NpcData result = new NpcData(id, displayName, entityUUID, location, config);
        result.citizensId = citizensId;
        return result;
    }

    public void saveToFile(File file) throws IOException {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        yaml.set("display-name", displayName);
        yaml.set("entity-uuid", entityUUID != null ? entityUUID.toString() : null);
        yaml.set("citizens-id", citizensId >= 0 ? citizensId : null);
        if (spawnLocation != null && spawnLocation.getWorld() != null) {
            yaml.set("location.world", spawnLocation.getWorld().getName());
            yaml.set("location.x", spawnLocation.getX());
            yaml.set("location.y", spawnLocation.getY());
            yaml.set("location.z", spawnLocation.getZ());
            yaml.set("location.yaw", (double) spawnLocation.getYaw());
            yaml.set("location.pitch", (double) spawnLocation.getPitch());
        }
        if (config != null) {
            yaml.set("knowledge-entity", config.getKnowledgeEntity());
            yaml.set("personality.greeting", config.getGreeting() != null ? config.getGreeting() : "");
            yaml.set("personality.system-prompt", config.getSystemPrompt() != null ? config.getSystemPrompt() : "");
            yaml.set("personality.topic", config.getTopic() != null ? config.getTopic() : "");
            yaml.set("personality.avoid", config.getAvoid() != null ? config.getAvoid() : "");
            yaml.set("personality.memory-enabled", config.getMemoryEnabled());
            yaml.set("personality.single-player", config.getSinglePlayer());
            yaml.set("personality.max-interactions", config.getMaxInteractions());
            yaml.set("personality.lock-after-task", config.isLockAfterTask());
            if (config.isLogConversations()) yaml.set("personality.log-conversations", true);
            NpcTaskConfig task = config.getTask();
            if (task != null) {
                yaml.set("personality.task.type", task.getType());
                yaml.set("personality.task.goal", task.getGoal());
                yaml.set("personality.task.outcome-check", task.getOutcomeCheck());
                yaml.set("personality.task.require", task.getRequire().isEmpty() ? null : task.getRequire());
                yaml.set("personality.task.on-success", task.getOnSuccess());
                yaml.set("personality.task.on-failure", task.getOnFailure());
                yaml.set("personality.task.max-exchanges", task.getMaxExchanges() > 0 ? task.getMaxExchanges() : null);
                yaml.set("personality.task.difficulty", task.getDifficulty());
                yaml.set("personality.task.intelligent-quantity", task.isIntelligentQuantity());
                yaml.set("personality.task.show-buttons", task.isShowButtons());
            } else {
                yaml.set("personality.task", null);
            }
        }
        yaml.save(file);
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public UUID getEntityUUID() { return entityUUID; }
    public Location getSpawnLocation() { return spawnLocation; }
    public NpcConfig getConfig() { return config != null ? config : new NpcConfig(); }

    public void setEntityUUID(UUID entityUUID) { this.entityUUID = entityUUID; }
    public void setCitizensId(int citizensId) { this.citizensId = citizensId; }
    public int getCitizensId() { return citizensId; }
    public void setSpawnLocation(Location spawnLocation) { this.spawnLocation = spawnLocation; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public void setConfig(NpcConfig config) { this.config = config; }
}
