package com.l299l.npcspeak.command;

import com.l299l.npcspeak.npc.NpcManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

public class NpcCommandCompleter implements TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("spawn", "remove", "list", "info", "edit", "task", "example", "memory", "log", "reload", "lore");
    private static final List<String> ID_SUBCOMMANDS = List.of("remove", "info", "edit", "task", "log", "lore");
    private static final List<String> MEMORY_SUBCOMMANDS = List.of("list", "clear");
    private static final List<String> EDIT_FIELDS = List.of("name", "greeting", "prompt", "topic", "avoid", "memory", "maxinteractions", "lockaftertask", "logconversations");
    private static final List<String> TASK_SUBCOMMANDS = List.of("info", "clear", "type", "goal", "check", "maxexchanges", "difficulty", "intelligentquantity", "require", "onsuccess", "onfailure");
    private static final List<String> DIFFICULTY_VALUES = List.of("1", "2", "3", "4", "5");
    private static final List<String> TASK_TYPES = List.of("negotiate", "persuade", "interrogate", "quest", "freeform");
    private static final List<String> ON_ACTIONS = List.of("add", "clear");
    private static final List<String> EXAMPLE_TYPES = List.of("negotiator", "persuader", "interrogator", "quest", "free");

    private final NpcManager npcManager;

    public NpcCommandCompleter(NpcManager npcManager) {
        this.npcManager = npcManager;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("npcspeak.admin")) return List.of();

        if (args.length == 1) {
            return SUBCOMMANDS.stream()
                    .filter(s -> !s.equals("lore") || sender.hasPermission("npcspeak.lore.debug"))
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }

        if (args.length == 2 && ID_SUBCOMMANDS.contains(args[0].toLowerCase())) {
            if (args[0].equalsIgnoreCase("lore") && !sender.hasPermission("npcspeak.lore.debug")) return List.of();
            return npcManager.getAllIds().stream()
                    .filter(id -> id.startsWith(args[1].toLowerCase()))
                    .toList();
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("edit")) {
            return EDIT_FIELDS.stream()
                    .filter(f -> f.startsWith(args[2].toLowerCase()))
                    .toList();
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("edit")) {
            String field = args[2].toLowerCase();
            if (field.equals("memory") || field.equals("lockaftertask") || field.equals("logconversations")) {
                return List.of("true", "false").stream().filter(v -> v.startsWith(args[3].toLowerCase())).toList();
            }
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("example")) {
            return EXAMPLE_TYPES.stream()
                    .filter(t -> t.startsWith(args[1].toLowerCase()))
                    .toList();
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("task")) {
            return TASK_SUBCOMMANDS.stream()
                    .filter(s -> s.startsWith(args[2].toLowerCase()))
                    .toList();
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("memory")) {
            return MEMORY_SUBCOMMANDS.stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .toList();
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("memory")) {
            return npcManager.getAllIds().stream()
                    .filter(id -> id.startsWith(args[2].toLowerCase()))
                    .toList();
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("memory") && args[1].equalsIgnoreCase("clear")) {
            List<String> suggestions = new java.util.ArrayList<>();
            suggestions.add("*");
            org.bukkit.Bukkit.getOnlinePlayers().stream()
                    .map(org.bukkit.entity.Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[3].toLowerCase()))
                    .forEach(suggestions::add);
            return suggestions;
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("task")) {
            String sub = args[2].toLowerCase();
            if (sub.equals("type")) {
                return TASK_TYPES.stream().filter(t -> t.startsWith(args[3].toLowerCase())).toList();
            }
            if (sub.equals("difficulty")) {
                return DIFFICULTY_VALUES.stream().filter(v -> v.startsWith(args[3])).toList();
            }
            if (sub.equals("intelligentquantity")) {
                return List.of("true", "false").stream().filter(v -> v.startsWith(args[3].toLowerCase())).toList();
            }
            if (sub.equals("require") || sub.equals("onsuccess") || sub.equals("onfailure")) {
                return ON_ACTIONS.stream().filter(a -> a.startsWith(args[3].toLowerCase())).toList();
            }
        }

        return List.of();
    }
}
