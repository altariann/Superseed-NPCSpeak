package com.l299l.npcspeak.conversation;

import org.bukkit.event.Listener;
import org.bukkit.plugin.*;
import java.io.File;
import java.util.*;
import java.util.regex.Pattern;

/** Paper's legacy test constructor requires a service provider. Never loads a plugin/server. */
@SuppressWarnings("removal")
public final class FixturePluginLoader implements PluginLoader {
    public Plugin loadPlugin(File file) { throw new UnsupportedOperationException(); }
    public PluginDescriptionFile getPluginDescription(File file) { throw new UnsupportedOperationException(); }
    public Pattern[] getPluginFileFilters() { return new Pattern[0]; }
    public Map<Class<? extends org.bukkit.event.Event>, Set<RegisteredListener>> createRegisteredListeners(Listener listener, Plugin plugin) { return Map.of(); }
    public void enablePlugin(Plugin plugin) { throw new UnsupportedOperationException(); }
    public void disablePlugin(Plugin plugin) { throw new UnsupportedOperationException(); }
}
