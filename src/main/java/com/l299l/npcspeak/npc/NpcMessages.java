package com.l299l.npcspeak.npc;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User-configurable chat strings, read from plugins/NPCSpeak/messages.yml.
 * Minecraft & codes and &#RRGGBB are supported; /npcspeak reload reloads the file.
 * The AI's generated responses are not parsed as formatting markup.
 */
public final class NpcMessages {
    private static final Pattern HEX = Pattern.compile("&#([a-fA-F0-9]{6})");
    private static File file;
    private static volatile FileConfiguration messages;

    private NpcMessages() {}

    public static void initialize(JavaPlugin plugin) {
        file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        reload();
    }

    public static void reload() {
        if (file != null) {
            messages = YamlConfiguration.loadConfiguration(file);
        }
    }

    public static Component format(String key, String fallback, String... replacements) {
        FileConfiguration cfg = messages;
        String value = cfg == null ? fallback : cfg.getString(key, fallback);
        if (value == null) value = fallback;
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            value = value.replace("{" + replacements[i] + "}", replacements[i + 1]);
        }
        return parseColors(value);
    }

    public static Component parseColors(String value) {
        Matcher matcher = HEX.matcher(value);
        StringBuffer expanded = new StringBuffer();
        while (matcher.find()) {
            StringBuilder rgb = new StringBuilder("&x");
            for (char digit : matcher.group(1).toCharArray()) {
                rgb.append('&').append(digit);
            }
            matcher.appendReplacement(expanded, Matcher.quoteReplacement(rgb.toString()));
        }
        matcher.appendTail(expanded);
        String colored = ChatColor.translateAlternateColorCodes('&', expanded.toString());
        return LegacyComponentSerializer.legacySection().deserialize(colored);
    }
}
