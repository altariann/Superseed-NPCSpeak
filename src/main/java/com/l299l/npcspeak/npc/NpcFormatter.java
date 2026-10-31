package com.l299l.npcspeak.npc;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

public final class NpcFormatter {

    private NpcFormatter() {}

    /** Show a private local copy of the player's typed message. Do not parse player text as color markup. */
    public static void sendPlayerEcho(Player player, String npcName, String message) {
        Component header = NpcMessages.format("player-echo-prefix", "&b[Sen â†’ {npc}] &f",
                "npc", npcName, "player", player.getName());
        player.sendMessage(header.append(Component.text(message, NamedTextColor.WHITE)));
    }
    public static void sendResponse(Player player, String npcName, String response) {
        // Keep AI-generated text literal: only admin-configured strings can contain formatting.
        player.sendMessage(prefix(npcName).append(Component.text(response, NamedTextColor.WHITE)));
    }

    public static void sendTypingIndicator(Player player, String npcName) {
        player.sendMessage(prefix(npcName).append(
                NpcMessages.format("typing", "&7✏ ...", "npc", npcName)));
    }

    public static void sendPendingNotice(Player player, String npcName) {
        player.sendMessage(prefix(npcName).append(
                NpcMessages.format("pending", "&7Biraz bekleyin, henüz düşünüyorum...", "npc", npcName)));
    }

    public static void sendCooldownNotice(Player player, String npcName, long secondsLeft) {
        player.sendMessage(prefix(npcName).append(
                NpcMessages.format("cooldown", "&eTekrar konuşmadan önce &6{seconds} saniye &ebekleyin.",
                        "seconds", Long.toString(secondsLeft), "npc", npcName)));
    }

    public static void sendGreeting(Player player, String npcName, String greeting) {
        // The greeting itself comes from the NPC's own YAML and may also use color codes.
        player.sendMessage(prefix(npcName).append(NpcMessages.parseColors(greeting)));
        player.sendMessage(NpcMessages.format("greeting-hint",
                "&8(Konuşmak için sohbetten yazın; ayrılmak için yeniden sağ tıklayın.)"));
    }

    public static void sendBusy(Player player, String npcName) {
        player.sendMessage(prefix(npcName).append(NpcMessages.format("busy",
                "&eŞu anda başka bir vatandaşla görüşüyorum. Lütfen sıranızı bekleyin.", "npc", npcName)));
    }

    public static void sendFarewell(Player player, String npcName) {
        player.sendMessage(prefix(npcName).append(NpcMessages.format("farewell",
                "&fGörüşmemiz sona erdi. İyi günler.", "npc", npcName)));
    }

    public static void sendTimeout(Player player, String npcName) {
        player.sendMessage(prefix(npcName).append(NpcMessages.format("timeout",
                "&7Görüşme süreniz doldu. Yeniden uğrayabilirsiniz.", "npc", npcName)));
    }

    public static void sendTaskSuccess(Player player, String npcName, String outcome) {
        if (outcome.isBlank()) {
            player.sendMessage(NpcMessages.format("task-success", "&a[ Görev tamamlandı ]"));
        } else {
            player.sendMessage(NpcMessages.format("task-success-with-outcome",
                    "&a[ Görev tamamlandı: &f{outcome}&a ]", "outcome", outcome));
        }
    }

    public static void sendTaskFailed(Player player, String npcName) {
        player.sendMessage(NpcMessages.format("task-failed", "&c[ Görev başarısız ]"));
    }

    public static void sendNegotiateButtons(Player player) {
        Component accept = NpcMessages.format("accept-button", "&a[✓ Kabul et]")
                .clickEvent(ClickEvent.runCommand("/npcspeak accept"))
                .hoverEvent(HoverEvent.showText(NpcMessages.format("accept-hover", "&aTeklifi kabul et")));
        Component decline = NpcMessages.format("decline-button", "&c[✗ Reddet]")
                .clickEvent(ClickEvent.runCommand("/npcspeak decline"))
                .hoverEvent(HoverEvent.showText(NpcMessages.format("decline-hover", "&cTeklifi reddet")));
        player.sendMessage(Component.text("  ").append(accept).append(Component.text("  ")).append(decline));
    }

    public static void sendRequirementsFailed(Player player, String npcName) {
        player.sendMessage(prefix(npcName).append(NpcMessages.format("requirements-failed",
                "&cGerekli eşya veya paraya sahip değilsiniz.")));
    }

    public static void sendInteractionBlocked(Player player, String npcName) {
        player.sendMessage(prefix(npcName).append(NpcMessages.format("interaction-blocked",
                "&7Sizinle şu anda daha fazla görüşemiyorum.", "npc", npcName)));
    }

    public static void sendError(Player player, String npcName, String backendName) {
        player.sendMessage(prefix(npcName).append(NpcMessages.format("error",
                "&cŞu anda yanıt veremiyorum. &8({backend} bağlantısı kurulamadı)",
                "backend", backendName, "npc", npcName)));
    }

    private static Component prefix(String npcName) {
        return NpcMessages.format("prefix", "&6[&e{npc}&6] &f", "npc", npcName);
    }
}
