package com.l299l.npcspeak.npc;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import org.bukkit.entity.EntityType;

public class CitizensNpcProvider implements NpcProvider {

    @Override
    public void restoreOrSpawn(NpcData data) {
        NPCRegistry registry = CitizensAPI.getNPCRegistry();

        NPC npc = null;
        if (data.getCitizensId() >= 0) {
            npc = registry.getById(data.getCitizensId());
        }

        if (npc == null && data.getCitizensId() >= 0) {
            // A saved Citizens ID must never be replaced silently with a fresh NPC.
            org.bukkit.Bukkit.getLogger().warning("[NPCSpeak] Citizens NPC #" + data.getCitizensId()
                    + " for '" + data.getId() + "' was not found. Check Citizens saves.yml; not creating a clone.");
            return;
        }

        if (npc == null) {
            npc = registry.createNPC(EntityType.PLAYER, data.getDisplayName());
            NPCSpeakTrait trait = new NPCSpeakTrait();
            trait.setNpcId(data.getId());
            npc.addTrait(trait);
            npc.spawn(data.getSpawnLocation());
            data.setCitizensId(npc.getId());
        } else {
            if (!npc.hasTrait(NPCSpeakTrait.class)) {
                NPCSpeakTrait trait = new NPCSpeakTrait();
                trait.setNpcId(data.getId());
                npc.addTrait(trait);
            }
            if (!npc.isSpawned()) {
                npc.spawn(data.getSpawnLocation());
            }
        }
    }

    @Override
    public void despawn(NpcData data) {
        if (data.getCitizensId() < 0) return;
        NPC npc = CitizensAPI.getNPCRegistry().getById(data.getCitizensId());
        if (npc != null) npc.destroy();
        data.setCitizensId(-1);
    }

    @Override
    public void rename(NpcData data, String newName) {
        if (data.getCitizensId() < 0) return;
        NPC npc = CitizensAPI.getNPCRegistry().getById(data.getCitizensId());
        if (npc != null) npc.setName(newName);
    }

    @Override
    public String getName() {
        return "citizens";
    }
}
