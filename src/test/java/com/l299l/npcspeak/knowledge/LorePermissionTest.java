package com.l299l.npcspeak.knowledge;

import com.l299l.npcspeak.command.NpcCommand;
import com.l299l.npcspeak.command.NpcCommandCompleter;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LorePermissionTest {
    private static CommandSender sender(boolean admin, boolean debug, List<Object> sent) {
        return (CommandSender) Proxy.newProxyInstance(CommandSender.class.getClassLoader(), new Class<?>[]{CommandSender.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("hasPermission")) return "npcspeak.admin".equals(args[0]) ? admin : debug;
                    if (method.getName().equals("sendMessage")) { sent.add(args[0]); return null; }
                    if (method.getName().equals("toString")) return "test-admin";
                    if (method.getReturnType() == boolean.class) return false;
                    return null;
                });
    }

    @Test void nonAdminCannotReachLoreRetrievalOrReadPrivateSources() {
        List<Object> sent = new ArrayList<>();
        var command = new NpcCommand(null, null, null, null);
        assertTrue(command.onCommand(sender(false, false, sent), null, "npcspeak", new String[]{"lore", "npc", "private question"}));
        assertEquals(1, sent.size());
    }

    @Test void generalAdminStillNeedsLoreDebugPermission() {
        List<Object> sent = new ArrayList<>();
        var command = new NpcCommand(null, null, null, null);
        assertTrue(command.onCommand(sender(true, false, sent), null, "npcspeak", new String[]{"lore", "npc", "private question"}));
        assertEquals(1, sent.size());
        assertFalse(new NpcCommandCompleter(null).onTabComplete(sender(true, false, sent), null, "npcspeak", new String[]{""}).contains("lore"));
    }

    @Test void permittedAdminCanSeeUsageAndCompletionWithoutNpcOrChatAccess() {
        List<Object> sent = new ArrayList<>();
        assertTrue(new NpcCommand(null, null, null, null).onCommand(sender(true, true, sent), null, "npcspeak", new String[]{"lore"}));
        assertTrue(new NpcCommandCompleter(null).onTabComplete(sender(true, true, sent), null, "npcspeak", new String[]{"lo"}).contains("lore"));
    }
}
