package com.l299l.npcspeak.command;

import com.l299l.npcspeak.config.NpcConfig;
import com.l299l.npcspeak.config.NpcTaskConfig;
import com.l299l.npcspeak.conversation.ConversationManager;
import com.l299l.npcspeak.logging.ConversationLogger;
import com.l299l.npcspeak.memory.NpcMemoryManager;
import com.l299l.npcspeak.npc.NpcData;
import com.l299l.npcspeak.npc.NpcManager;
import com.l299l.npcspeak.knowledge.KnowledgeBase;
import com.l299l.npcspeak.knowledge.LoreDiagnostics;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class NpcCommand implements CommandExecutor {

    private final NpcManager npcManager;
    private final NpcMemoryManager memoryManager;
    private final ConversationManager conversationManager;
    private final ConversationLogger conversationLogger;

    public NpcCommand(NpcManager npcManager, NpcMemoryManager memoryManager,
                      ConversationManager conversationManager, ConversationLogger conversationLogger) {
        this.npcManager = npcManager;
        this.memoryManager = memoryManager;
        this.conversationManager = conversationManager;
        this.conversationLogger = conversationLogger;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            if (args[0].equalsIgnoreCase("accept"))  return handleQuickReply(sender, "I accept the offer.");
            if (args[0].equalsIgnoreCase("decline")) return handleQuickReply(sender, "I decline the offer.");
        }

        if (!sender.hasPermission("npcspeak.admin")) {
            sender.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender, label);
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "spawn" -> handleSpawn(sender, label, args);
            case "remove" -> handleRemove(sender, args);
            case "list" -> handleList(sender);
            case "info" -> handleInfo(sender, args);
            case "edit" -> handleEdit(sender, label, args);
            case "task" -> handleTask(sender, label, args);
            case "example" -> handleExample(sender, label, args);
            case "memory" -> handleMemory(sender, label, args);
            case "log" -> handleLog(sender, args);
            case "lore" -> handleLore(sender, args);
            case "reload" -> handleReload(sender);
            default -> { sendUsage(sender, label); yield true; }
        };
    }

    private boolean handleQuickReply(CommandSender sender, String message) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }
        if (!conversationManager.isInConversation(player.getUniqueId())) {
            player.sendMessage(Component.text("You are not currently in a conversation with an NPC.", NamedTextColor.RED));
            return true;
        }
        player.sendMessage(Component.text("You: " + message, NamedTextColor.GRAY));
        conversationManager.injectMessage(player, message);
        return true;
    }

    private boolean handleSpawn(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /" + label + " spawn <id> [display name]", NamedTextColor.YELLOW));
            return true;
        }

        String id = args[1].toLowerCase();
        if (npcManager.exists(id)) {
            player.sendMessage(Component.text("An NPC with id '" + id + "' already exists.", NamedTextColor.RED));
            return true;
        }

        String displayName = args.length > 2
                ? String.join(" ", Arrays.copyOfRange(args, 2, args.length))
                : id;

        NpcData data = new NpcData(id, displayName, null, player.getLocation(), new NpcConfig());
        try {
            npcManager.add(data);
            player.sendMessage(Component.text("Spawned NPC '", NamedTextColor.GREEN)
                    .append(Component.text(displayName, NamedTextColor.GOLD))
                    .append(Component.text("' (id: " + id + ") at your location.", NamedTextColor.GREEN)));
        } catch (IOException e) {
            player.sendMessage(Component.text("Failed to save NPC config: " + e.getMessage(), NamedTextColor.RED));
        }
        return true;
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /npcspeak remove <id>", NamedTextColor.YELLOW));
            return true;
        }
        String id = args[1].toLowerCase();
        if (npcManager.getById(id) == null) {
            sender.sendMessage(Component.text("No NPC with id '" + id + "' found.", NamedTextColor.RED));
            return true;
        }
        npcManager.remove(id);
        sender.sendMessage(Component.text("Removed NPC '" + id + "'.", NamedTextColor.GREEN));
        return true;
    }

    private boolean handleList(CommandSender sender) {
        if (npcManager.count() == 0) {
            sender.sendMessage(Component.text("No NPCs are currently loaded.", NamedTextColor.YELLOW));
            return true;
        }
        sender.sendMessage(Component.text("Loaded NPCs (" + npcManager.count() + "):", NamedTextColor.GOLD));
        for (NpcData npc : npcManager.getAll()) {
            sender.sendMessage(Component.text("  " + npc.getId() + " — " + npc.getDisplayName(), NamedTextColor.WHITE));
        }
        return true;
    }

    private boolean handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /npcspeak info <id>", NamedTextColor.YELLOW));
            return true;
        }
        NpcData npc = npcManager.getById(args[1].toLowerCase());
        if (npc == null) {
            sender.sendMessage(Component.text("No NPC with id '" + args[1] + "' found.", NamedTextColor.RED));
            return true;
        }

        sender.sendMessage(Component.text("=== " + npc.getDisplayName() + " ===", NamedTextColor.GOLD));
        sender.sendMessage(field("ID", npc.getId()));

        if (npc.getCitizensId() >= 0) {
            sender.sendMessage(field("Provider", "Citizens2"));
            sender.sendMessage(field("Citizens ID", String.valueOf(npc.getCitizensId())));
        } else if (npc.getEntityUUID() != null) {
            sender.sendMessage(field("Provider", "Builtin"));
            sender.sendMessage(field("Entity UUID", npc.getEntityUUID().toString()));
        } else {
            sender.sendMessage(field("Provider", "unassigned (not yet spawned)"));
        }

        if (npc.getSpawnLocation() != null && npc.getSpawnLocation().getWorld() != null) {
            sender.sendMessage(field("Location", String.format("%s (%.1f, %.1f, %.1f) yaw=%.1f pitch=%.1f",
                    npc.getSpawnLocation().getWorld().getName(),
                    npc.getSpawnLocation().getX(),
                    npc.getSpawnLocation().getY(),
                    npc.getSpawnLocation().getZ(),
                    npc.getSpawnLocation().getYaw(),
                    npc.getSpawnLocation().getPitch())));
        } else {
            sender.sendMessage(field("Location", "none / world not loaded"));
        }

        NpcConfig cfg = npc.getConfig();
        sender.sendMessage(field("Greeting", nonBlankOr(cfg.getGreeting(), "(default)")));
        sender.sendMessage(field("System prompt", nonBlankOr(cfg.getSystemPrompt(), "(none)")));
        sender.sendMessage(field("Topic", nonBlankOr(cfg.getTopic(), "(none)")));
        sender.sendMessage(field("Avoid", nonBlankOr(cfg.getAvoid(), "(none)")));
        sender.sendMessage(field("Memory enabled", cfg.getMemoryEnabled() == null ? "default" : String.valueOf(cfg.getMemoryEnabled())));
        sender.sendMessage(field("Max interactions", cfg.getMaxInteractions() > 0 ? String.valueOf(cfg.getMaxInteractions()) : "unlimited"));
        sender.sendMessage(field("Lock after task", String.valueOf(cfg.isLockAfterTask())));
        sender.sendMessage(field("Log conversations", String.valueOf(cfg.isLogConversations())));

        showTaskInfo(sender, npc);
        return true;
    }

    private static String nonBlankOr(String value, String fallback) {
        return (value != null && !value.isBlank()) ? value : fallback;
    }

    private static Component field(String label, String value) {
        return Component.text(label + ": ", NamedTextColor.GRAY).append(Component.text(value, NamedTextColor.WHITE));
    }

    private static final List<String> EDIT_FIELDS = List.of("name", "greeting", "prompt", "topic", "avoid", "memory", "maxinteractions", "lockaftertask", "logconversations");

    private boolean handleEdit(CommandSender sender, String label, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(Component.text("Usage: /" + label + " edit <id> <field> <value>", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("Fields: " + String.join(", ", EDIT_FIELDS), NamedTextColor.YELLOW));
            return true;
        }

        String id = args[1].toLowerCase();
        NpcData npc = npcManager.getById(id);
        if (npc == null) {
            sender.sendMessage(Component.text("No NPC with id '" + id + "' found.", NamedTextColor.RED));
            return true;
        }

        String field = args[2].toLowerCase();
        String value = String.join(" ", Arrays.copyOfRange(args, 3, args.length));

        switch (field) {
            case "name" -> {
                try {
                    npcManager.renameNpc(id, value);
                    sender.sendMessage(Component.text("Renamed NPC '" + id + "' to '", NamedTextColor.GREEN)
                            .append(Component.text(value, NamedTextColor.GOLD))
                            .append(Component.text("'.", NamedTextColor.GREEN)));
                } catch (IOException e) {
                    sender.sendMessage(Component.text("Failed to save: " + e.getMessage(), NamedTextColor.RED));
                }
            }
            case "greeting" -> {
                npc.getConfig().setGreeting(value);
                trySaveNpc(sender, npc);
                sender.sendMessage(Component.text("Updated greeting for '", NamedTextColor.GREEN)
                        .append(Component.text(id, NamedTextColor.GOLD))
                        .append(Component.text("'.", NamedTextColor.GREEN)));
            }
            case "prompt" -> {
                npc.getConfig().setSystemPrompt(value);
                trySaveNpc(sender, npc);
                sender.sendMessage(Component.text("Updated system prompt for '", NamedTextColor.GREEN)
                        .append(Component.text(id, NamedTextColor.GOLD))
                        .append(Component.text("'.", NamedTextColor.GREEN)));
            }
            case "topic" -> {
                npc.getConfig().setTopic(value);
                trySaveNpc(sender, npc);
                sender.sendMessage(Component.text("Updated topic for '", NamedTextColor.GREEN)
                        .append(Component.text(id, NamedTextColor.GOLD))
                        .append(Component.text("'.", NamedTextColor.GREEN)));
            }
            case "avoid" -> {
                npc.getConfig().setAvoid(value);
                trySaveNpc(sender, npc);
                sender.sendMessage(Component.text("Updated avoid for '", NamedTextColor.GREEN)
                        .append(Component.text(id, NamedTextColor.GOLD))
                        .append(Component.text("'.", NamedTextColor.GREEN)));
            }
            case "memory" -> {
                if (value.equalsIgnoreCase("default")) {
                    npc.getConfig().setMemoryEnabled(null);
                } else {
                    npc.getConfig().setMemoryEnabled(Boolean.parseBoolean(value));
                }
                trySaveNpc(sender, npc);
                sender.sendMessage(Component.text("Updated memory for '", NamedTextColor.GREEN)
                        .append(Component.text(id, NamedTextColor.GOLD))
                        .append(Component.text("' → " + (npc.getConfig().getMemoryEnabled() == null ? "default" : npc.getConfig().getMemoryEnabled()) + ".", NamedTextColor.GREEN)));
            }
            case "maxinteractions" -> {
                try {
                    int n = Integer.parseInt(value);
                    npc.getConfig().setMaxInteractions(n);
                    trySaveNpc(sender, npc);
                    sender.sendMessage(Component.text("Set max-interactions to " + n + " for '", NamedTextColor.GREEN)
                            .append(Component.text(id, NamedTextColor.GOLD))
                            .append(Component.text("' (-1 = unlimited).", NamedTextColor.GREEN)));
                } catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("'" + value + "' is not a valid number.", NamedTextColor.RED));
                }
            }
            case "lockaftertask" -> {
                npc.getConfig().setLockAfterTask(Boolean.parseBoolean(value));
                trySaveNpc(sender, npc);
                sender.sendMessage(Component.text("Set lock-after-task to " + npc.getConfig().isLockAfterTask() + " for '", NamedTextColor.GREEN)
                        .append(Component.text(id, NamedTextColor.GOLD))
                        .append(Component.text("'.", NamedTextColor.GREEN)));
            }
            case "logconversations" -> {
                npc.getConfig().setLogConversations(Boolean.parseBoolean(value));
                trySaveNpc(sender, npc);
                sender.sendMessage(Component.text("Set log-conversations to " + npc.getConfig().isLogConversations() + " for '", NamedTextColor.GREEN)
                        .append(Component.text(id, NamedTextColor.GOLD))
                        .append(Component.text("'. Use /npcspeak log " + id + " to read logs.", NamedTextColor.GREEN)));
            }
            default -> sender.sendMessage(Component.text(
                    "Unknown field '" + field + "'. Fields: " + String.join(", ", EDIT_FIELDS),
                    NamedTextColor.RED));
        }
        return true;
    }

    private static final List<String> TASK_SUBCOMMANDS = List.of("info", "clear", "type", "goal", "check", "maxexchanges", "difficulty", "intelligentquantity", "require", "onsuccess", "onfailure");
    private static final List<String> TASK_TYPES = List.of("negotiate", "persuade", "interrogate", "quest", "freeform");
    private static final List<String> ON_ACTIONS = List.of("add", "clear");

    private boolean handleTask(CommandSender sender, String label, String[] args) {
        if (args.length < 3) {
            sendTaskUsage(sender, label);
            return true;
        }
        String id = args[1].toLowerCase();
        NpcData npc = npcManager.getById(id);
        if (npc == null) {
            sender.sendMessage(Component.text("No NPC with id '" + id + "' found.", NamedTextColor.RED));
            return true;
        }
        String sub = args[2].toLowerCase();
        switch (sub) {
            case "info" -> showTaskInfo(sender, npc);
            case "clear" -> {
                npc.getConfig().setTask(null);
                trySaveNpc(sender, npc);
                sender.sendMessage(Component.text("Removed task from '", NamedTextColor.GREEN)
                        .append(Component.text(id, NamedTextColor.GOLD))
                        .append(Component.text("'.", NamedTextColor.GREEN)));
            }
            case "type" -> {
                if (args.length < 4) {
                    sender.sendMessage(Component.text("Usage: /" + label + " task <id> type <type>", NamedTextColor.YELLOW));
                    sender.sendMessage(Component.text("Types: " + String.join(", ", TASK_TYPES), NamedTextColor.YELLOW));
                    return true;
                }
                String type = args[3].toLowerCase();
                if (!TASK_TYPES.contains(type)) {
                    sender.sendMessage(Component.text("Unknown type. Valid: " + String.join(", ", TASK_TYPES), NamedTextColor.RED));
                    return true;
                }
                getOrCreateTask(npc).setType(type);
                trySaveNpc(sender, npc);
                sender.sendMessage(Component.text("Set task type to '", NamedTextColor.GREEN)
                        .append(Component.text(type, NamedTextColor.GOLD))
                        .append(Component.text("' for '" + id + "'.", NamedTextColor.GREEN)));
            }
            case "goal" -> {
                if (args.length < 4) {
                    sender.sendMessage(Component.text("Usage: /" + label + " task <id> goal <text...>", NamedTextColor.YELLOW));
                    return true;
                }
                getOrCreateTask(npc).setGoal(String.join(" ", Arrays.copyOfRange(args, 3, args.length)));
                trySaveNpc(sender, npc);
                sender.sendMessage(Component.text("Updated task goal for '", NamedTextColor.GREEN)
                        .append(Component.text(id, NamedTextColor.GOLD))
                        .append(Component.text("'.", NamedTextColor.GREEN)));
            }
            case "check" -> {
                if (args.length < 4) {
                    sender.sendMessage(Component.text("Usage: /" + label + " task <id> check <text...>", NamedTextColor.YELLOW));
                    return true;
                }
                getOrCreateTask(npc).setOutcomeCheck(String.join(" ", Arrays.copyOfRange(args, 3, args.length)));
                trySaveNpc(sender, npc);
                sender.sendMessage(Component.text("Updated task check for '", NamedTextColor.GREEN)
                        .append(Component.text(id, NamedTextColor.GOLD))
                        .append(Component.text("'.", NamedTextColor.GREEN)));
            }
            case "maxexchanges" -> {
                if (args.length < 4) {
                    sender.sendMessage(Component.text("Usage: /" + label + " task <id> maxexchanges <n|-1>", NamedTextColor.YELLOW));
                    return true;
                }
                try {
                    int n = Integer.parseInt(args[3]);
                    getOrCreateTask(npc).setMaxExchanges(n);
                    trySaveNpc(sender, npc);
                    sender.sendMessage(Component.text("Set max exchanges to " + n + " for '", NamedTextColor.GREEN)
                            .append(Component.text(id, NamedTextColor.GOLD))
                            .append(Component.text("'.", NamedTextColor.GREEN)));
                } catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("'" + args[3] + "' is not a valid number.", NamedTextColor.RED));
                }
            }
            case "difficulty" -> {
                if (args.length < 4) {
                    sender.sendMessage(Component.text("Usage: /" + label + " task <id> difficulty <1-5>", NamedTextColor.YELLOW));
                    sender.sendMessage(Component.text("1=very easy  2=easy  3=normal  4=hard  5=very hard", NamedTextColor.GRAY));
                    return true;
                }
                try {
                    int d = Integer.parseInt(args[3]);
                    if (d < 1 || d > 5) throw new NumberFormatException();
                    getOrCreateTask(npc).setDifficulty(d);
                    trySaveNpc(sender, npc);
                    sender.sendMessage(Component.text("Set difficulty to " + d + "/5 for '", NamedTextColor.GREEN)
                            .append(Component.text(id, NamedTextColor.GOLD))
                            .append(Component.text("'.", NamedTextColor.GREEN)));
                } catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("Difficulty must be 1–5.", NamedTextColor.RED));
                }
            }
            case "intelligentquantity" -> {
                if (args.length < 4) {
                    sender.sendMessage(Component.text("Usage: /" + label + " task <id> intelligentquantity <true|false>", NamedTextColor.YELLOW));
                    sender.sendMessage(Component.text("true = detect multi-unit deals (YES 2x80 for 2 items at 80 total); use %quantity% in", NamedTextColor.GRAY));
                    sender.sendMessage(Component.text("       on-success/require to scale item counts, %outcome% is always the TOTAL price", NamedTextColor.GRAY));
                    sender.sendMessage(Component.text("false = NPC refuses multi-item negotiations (default)", NamedTextColor.GRAY));
                    return true;
                }
                boolean iq = Boolean.parseBoolean(args[3]);
                getOrCreateTask(npc).setIntelligentQuantity(iq);
                trySaveNpc(sender, npc);
                sender.sendMessage(Component.text("Set intelligent-quantity to " + iq + " for '", NamedTextColor.GREEN)
                        .append(Component.text(id, NamedTextColor.GOLD))
                        .append(Component.text("'.", NamedTextColor.GREEN)));
            }
            case "require" -> handleOnList(sender, label, args, npc, null);
            case "onsuccess" -> handleOnList(sender, label, args, npc, true);
            case "onfailure" -> handleOnList(sender, label, args, npc, false);
            default -> sendTaskUsage(sender, label);
        }
        return true;
    }

    private void handleOnList(CommandSender sender, String label, String[] args, NpcData npc, Boolean isSuccess) {
        String field = isSuccess == null ? "require" : (isSuccess ? "onsuccess" : "onfailure");
        if (args.length < 4) {
            if (isSuccess == null) {
                sender.sendMessage(Component.text("Usage: /" + label + " task <id> require <add|clear> [entry...]", NamedTextColor.YELLOW));
                sender.sendMessage(Component.text("eco %outcome%              — player must have at least %outcome% Vault balance", NamedTextColor.GRAY));
                sender.sendMessage(Component.text("item <material> %outcome%  — player must hold at least %outcome% of that item", NamedTextColor.GRAY));
                sender.sendMessage(Component.text("item <material> <n>        — fixed item count, e.g. item minecraft:gold_ingot 5", NamedTextColor.GRAY));
                sender.sendMessage(Component.text("%outcome% is replaced at check-time with the negotiated value.", NamedTextColor.DARK_GRAY));
            } else {
                sender.sendMessage(Component.text("Usage: /" + label + " task <id> " + field + " <add|clear> [cmd...]", NamedTextColor.YELLOW));
            }
            return;
        }
        NpcTaskConfig task = getOrCreateTask(npc);
        List<String> list = isSuccess == null ? task.getRequire() : (isSuccess ? task.getOnSuccess() : task.getOnFailure());
        switch (args[3].toLowerCase()) {
            case "add" -> {
                if (args.length < 5) {
                    sender.sendMessage(Component.text("Usage: /" + label + " task <id> " + field + " add <command...>", NamedTextColor.YELLOW));
                    return;
                }
                String cmd = String.join(" ", Arrays.copyOfRange(args, 4, args.length));
                list.add(cmd);
                trySaveNpc(sender, npc);
                sender.sendMessage(Component.text("Added to " + field + " for '", NamedTextColor.GREEN)
                        .append(Component.text(npc.getId(), NamedTextColor.GOLD))
                        .append(Component.text("': " + cmd, NamedTextColor.GREEN)));
            }
            case "clear" -> {
                list.clear();
                trySaveNpc(sender, npc);
                sender.sendMessage(Component.text("Cleared " + field + " for '", NamedTextColor.GREEN)
                        .append(Component.text(npc.getId(), NamedTextColor.GOLD))
                        .append(Component.text("'.", NamedTextColor.GREEN)));
            }
            default -> sender.sendMessage(Component.text("Unknown action. Use 'add' or 'clear'.", NamedTextColor.RED));
        }
    }

    private void showTaskInfo(CommandSender sender, NpcData npc) {
        NpcTaskConfig task = npc.getConfig().getTask();
        sender.sendMessage(Component.text("=== Task: " + npc.getDisplayName() + " ===", NamedTextColor.GOLD));
        if (task == null) {
            sender.sendMessage(Component.text("No task configured.", NamedTextColor.GRAY));
            return;
        }
        sender.sendMessage(Component.text("Type: ", NamedTextColor.GRAY).append(Component.text(task.getType(), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("Goal: ", NamedTextColor.GRAY).append(Component.text(task.getGoal() != null ? task.getGoal() : "(none)", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("Check: ", NamedTextColor.GRAY).append(Component.text(task.getOutcomeCheck() != null ? task.getOutcomeCheck() : "(none)", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("Difficulty: ", NamedTextColor.GRAY).append(Component.text(task.getDifficulty() + "/5", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("Max exchanges: ", NamedTextColor.GRAY).append(Component.text(task.getMaxExchanges() > 0 ? String.valueOf(task.getMaxExchanges()) : "unlimited", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("Intelligent quantity: ", NamedTextColor.GRAY).append(Component.text(task.isIntelligentQuantity(), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("Require (" + task.getRequire().size() + "):", NamedTextColor.GRAY));
        for (String req : task.getRequire()) sender.sendMessage(Component.text("  " + req, NamedTextColor.WHITE));
        sender.sendMessage(Component.text("On success (" + task.getOnSuccess().size() + "):", NamedTextColor.GRAY));
        for (String cmd : task.getOnSuccess()) sender.sendMessage(Component.text("  " + cmd, NamedTextColor.WHITE));
        sender.sendMessage(Component.text("On failure (" + task.getOnFailure().size() + "):", NamedTextColor.GRAY));
        for (String cmd : task.getOnFailure()) sender.sendMessage(Component.text("  " + cmd, NamedTextColor.WHITE));
    }

    private NpcTaskConfig getOrCreateTask(NpcData npc) {
        NpcConfig config = npc.getConfig();
        if (config.getTask() == null) {
            config.setTask(new NpcTaskConfig());
        }
        return config.getTask();
    }

    private void sendTaskUsage(CommandSender sender, String label) {
        sender.sendMessage(Component.text("Task management:", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  /" + label + " task <id> info", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("  /" + label + " task <id> clear", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("  /" + label + " task <id> type <negotiate|persuade|interrogate|quest|freeform>", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("  /" + label + " task <id> goal <text...>", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("  /" + label + " task <id> check <text...>", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("  /" + label + " task <id> maxexchanges <n|-1>", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("  /" + label + " task <id> difficulty <1-5>  (1=easy 5=very hard)", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("  /" + label + " task <id> intelligentquantity <true|false>", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("  /" + label + " task <id> require <add|clear> [eco %outcome% | item <mat> %outcome%]", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("  /" + label + " task <id> onsuccess <add|clear> [cmd...]", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("  /" + label + " task <id> onfailure <add|clear> [cmd...]", NamedTextColor.WHITE));
    }

    private void trySaveNpc(CommandSender sender, NpcData npc) {
        try {
            npcManager.saveNpc(npc);
        } catch (IOException e) {
            sender.sendMessage(Component.text("Saved in memory but failed to write file: " + e.getMessage(), NamedTextColor.RED));
        }
    }

    private boolean handleLog(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /npcspeak log <id> [player]", NamedTextColor.YELLOW));
            return true;
        }
        String id = args[1].toLowerCase();
        if (npcManager.getById(id) == null) {
            sender.sendMessage(Component.text("No NPC with id '" + id + "' found.", NamedTextColor.RED));
            return true;
        }
        List<String> lines = args.length >= 3
                ? conversationLogger.readTodayFiltered(id, args[2])
                : conversationLogger.readToday(id);
        if (lines.isEmpty()) {
            sender.sendMessage(Component.text("No log entries for '" + id + "' today.", NamedTextColor.YELLOW));
            return true;
        }
        int start = Math.max(0, lines.size() - 30);
        sender.sendMessage(Component.text("=== Log: " + id + " (today, " + (lines.size() - start) + " entries) ===", NamedTextColor.GOLD));
        for (int i = start; i < lines.size(); i++) {
            sender.sendMessage(Component.text(lines.get(i), NamedTextColor.WHITE));
        }
        return true;
    }

    private boolean handleLore(CommandSender sender, String[] args) {
        if (!sender.hasPermission("npcspeak.lore.debug")) {
            sender.sendMessage(Component.text("Lore tanısı için yetkiniz yok.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(Component.text("Kullanım: /npcspeak lore <npc-id> <deneme sorusu>", NamedTextColor.YELLOW));
            return true;
        }
        if (npcManager.getById(args[1].toLowerCase(java.util.Locale.ROOT)) == null) {
            sender.sendMessage(Component.text("NPC bulunamadı.", NamedTextColor.RED));
            return true;
        }
        String question = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        // Same immutable index and retrieval method as an actual conversation; no AI request.
        for (String line : LoreDiagnostics.lines(KnowledgeBase.retrieve(question))) {
            sender.sendMessage(Component.text(line, NamedTextColor.GRAY));
        }
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        npcManager.reloadAll();
        if (!KnowledgeBase.wasLastReloadSuccessful()) {
            sender.sendMessage(Component.text("Lore yeniden yüklenemedi; önceki bilgi dizini korundu. knowledge.yml dosyasını kontrol edin.", NamedTextColor.YELLOW));
        }
        sender.sendMessage(Component.text("Reloaded " + npcManager.count() + " NPC(s).", NamedTextColor.GREEN));
        return true;
    }

    private static final List<String> EXAMPLE_TYPES = List.of("negotiator", "persuader", "interrogator", "quest", "free");

    private boolean handleExample(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 2 || !EXAMPLE_TYPES.contains(args[1].toLowerCase())) {
            sender.sendMessage(Component.text("Usage: /" + label + " example <type>", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("Types: " + String.join(", ", EXAMPLE_TYPES), NamedTextColor.YELLOW));
            return true;
        }
        String type = args[1].toLowerCase();
        String baseId = "ex_" + type;
        String id = baseId;
        int n = 2;
        while (npcManager.exists(id)) id = baseId + "_" + n++;

        NpcData data = buildExampleNpc(type, id, player.getLocation());
        try {
            npcManager.add(data);
            player.sendMessage(Component.text("Spawned example NPC '", NamedTextColor.GREEN)
                    .append(Component.text(data.getDisplayName(), NamedTextColor.GOLD))
                    .append(Component.text("' (id: " + id + ").", NamedTextColor.GREEN)));
        } catch (IOException e) {
            player.sendMessage(Component.text("Failed to save NPC: " + e.getMessage(), NamedTextColor.RED));
        }
        return true;
    }

    private NpcData buildExampleNpc(String type, String id, org.bukkit.Location location) {
        NpcConfig config = new NpcConfig();
        NpcTaskConfig task = null;
        String displayName;

        switch (type) {
            case "negotiator" -> {
                displayName = "Merchant Bob";
                config.setGreeting("Ah, a customer! I have fine iron swords for sale — 50 gold each!");
                config.setSystemPrompt("You are a shrewd merchant. Stay in character at all times.");
                config.setTopic("trading, weapons, commerce");
                task = new NpcTaskConfig();
                task.setType("negotiate");
                task.setIntelligentQuantity(true);
                task.setGoal("You are selling iron swords at 50 gold each; you can go as low as 30 gold per sword (never reveal this). You CAN sell multiple swords in one transaction. Counter-offer when haggled — do not refuse reasonable bulk offers. Only confirm a sale verbally (e.g. 'We have a deal!') when the player explicitly agrees to your price.");
                task.setOutcomeCheck("Did BOTH the player AND the NPC verbally confirm agreement on the SAME specific final price and quantity? Answer YES <quantity>x<total-price> for multi-item deals (e.g. YES 2x80 for 2 items sold for 80 gold total), YES <price> for single-item deals, or NO if no mutual agreement was reached.");
                task.setRequire(List.of("item minecraft:gold_ingot %outcome%"));
                task.setOnSuccess(List.of(
                        "clear %player% minecraft:gold_ingot %outcome%",
                        "give %player% minecraft:iron_sword %quantity%",
                        "xp add %player% 25 points"
                ));
                task.setOnFailure(List.of());
                task.setMaxExchanges(8);
                task.setDifficulty(3);
            }
            case "persuader" -> {
                displayName = "Village Elder";
                config.setGreeting("I don't trust outsiders. State your business.");
                config.setSystemPrompt("You are a suspicious but ultimately reasonable village elder. Be skeptical, but yield if the argument is genuinely compelling.");
                config.setTopic("village safety, local history");
                task = new NpcTaskConfig();
                task.setType("persuade");
                task.setGoal("You distrust the player. They must convince you to allow them into the village. Hold firm unless their reasoning is genuinely persuasive. Only agree explicitly when you are convinced.");
                task.setOutcomeCheck("Did the NPC explicitly and clearly state they agree to let the player into the village? Skepticism, partial softening, or asking questions does NOT count — only an unambiguous 'yes you may enter' from the NPC. Answer YES or NO.");
                task.setOnSuccess(List.of(
                        "lp user %player% permission set village.access true",
                        "give %player% minecraft:bread 5"
                ));
                task.setOnFailure(List.of(
                        "effect give %player% slowness 200 1"
                ));
                task.setMaxExchanges(6);
                task.setDifficulty(4);
            }
            case "interrogator" -> {
                displayName = "Guard Captain";
                config.setGreeting("Halt! You match the description of a suspect. You will answer my questions.");
                config.setSystemPrompt("You are a stern guard captain conducting an interrogation. Ask pointed questions and probe for inconsistencies.");
                config.setTopic("crime, justice, town security");
                task = new NpcTaskConfig();
                task.setType("interrogate");
                task.setGoal("You are interrogating the player about a stolen artifact. Ask where they were last night and probe for inconsistencies. Only conclude the interrogation when you have a clear outcome.");
                task.setOutcomeCheck("Did the player give a COMPLETE and satisfying alibi that the NPC accepted, or did the player explicitly admit guilt? Vague answers, deflections, or ongoing questions do NOT count. Answer YES <alibi/guilty> or NO.");
                task.setOnSuccess(List.of(
                        "give %player% minecraft:paper 1",
                        "xp add %player% 50 points"
                ));
                task.setOnFailure(List.of(
                        "effect give %player% mining_fatigue 300 1"
                ));
                task.setMaxExchanges(10);
                task.setDifficulty(3);
            }
            case "quest" -> {
                displayName = "Old Wizard";
                config.setGreeting("Ah, an adventurer! I have a task that requires your... unique talents.");
                config.setSystemPrompt("You are a mysterious wizard who needs help retrieving a lost tome. Be cryptic at first, then reveal the quest details.");
                config.setTopic("magic, ancient lore, quests");
                task = new NpcTaskConfig();
                task.setType("quest");
                task.setGoal("You need the player to agree to retrieve the Ancient Tome of Shadows from the old dungeon. First explain the quest fully. Only consider it accepted once they give a firm, standalone yes AFTER hearing the full details.");
                task.setOutcomeCheck("Did the player give a firm, standalone acceptance of the quest AFTER being told what it involves — not a question, not a 'yeah tell me more', but an unambiguous 'yes I will do it' or equivalent? Answer YES or NO.");
                task.setOnSuccess(List.of(
                        "give %player% minecraft:map 1",
                        "give %player% minecraft:iron_sword 1",
                        "xp add %player% 100 points"
                ));
                task.setOnFailure(List.of());
                task.setMaxExchanges(-1);
                task.setDifficulty(2);
            }
            default -> { // free
                displayName = "Friendly Villager";
                config.setGreeting("Hello there! Lovely day, isn't it?");
                config.setSystemPrompt("You are a friendly villager who loves to chat about daily life in the village.");
                config.setTopic("village life, weather, crops, local gossip");
            }
        }

        if (task != null) config.setTask(task);
        return new NpcData(id, displayName, null, location, config);
    }

    private boolean handleMemory(CommandSender sender, String label, String[] args) {
        if (memoryManager == null) {
            sender.sendMessage(Component.text("NPC memory is disabled in config.yml.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /" + label + " memory list <id>", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("       /" + label + " memory clear <id> <player|*>", NamedTextColor.YELLOW));
            return true;
        }
        String sub = args[1].toLowerCase();
        String npcId = args[2].toLowerCase();
        if (npcManager.getById(npcId) == null) {
            sender.sendMessage(Component.text("No NPC with id '" + npcId + "' found.", NamedTextColor.RED));
            return true;
        }
        switch (sub) {
            case "list" -> {
                List<UUID> players = memoryManager.listPlayers(npcId);
                if (players.isEmpty()) {
                    sender.sendMessage(Component.text("No memory entries for '" + npcId + "'.", NamedTextColor.YELLOW));
                    return true;
                }
                sender.sendMessage(Component.text("Memory entries for '" + npcId + "' (" + players.size() + "):", NamedTextColor.GOLD));
                for (UUID uuid : players) {
                    @SuppressWarnings("deprecation")
                    OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                    String name = op.getName() != null ? op.getName() : uuid.toString();
                    sender.sendMessage(Component.text("  " + name, NamedTextColor.WHITE));
                }
            }
            case "clear" -> {
                if (args.length < 4) {
                    sender.sendMessage(Component.text("Usage: /" + label + " memory clear <id> <player|*>", NamedTextColor.YELLOW));
                    return true;
                }
                String target = args[3];
                if (target.equals("*")) {
                    int count = memoryManager.clearAll(npcId);
                    sender.sendMessage(Component.text("Cleared " + count + " memory entr" + (count == 1 ? "y" : "ies") + " for '" + npcId + "'.", NamedTextColor.GREEN));
                } else {
                    @SuppressWarnings("deprecation")
                    OfflinePlayer op = Bukkit.getOfflinePlayer(target);
                    if (memoryManager.clear(npcId, op.getUniqueId())) {
                        sender.sendMessage(Component.text("Cleared memory for '" + target + "' with NPC '" + npcId + "'.", NamedTextColor.GREEN));
                    } else {
                        sender.sendMessage(Component.text("No memory found for '" + target + "' with NPC '" + npcId + "'.", NamedTextColor.YELLOW));
                    }
                }
            }
            default -> {
                sender.sendMessage(Component.text("Usage: /" + label + " memory list <id>", NamedTextColor.YELLOW));
                sender.sendMessage(Component.text("       /" + label + " memory clear <id> <player|*>", NamedTextColor.YELLOW));
            }
        }
        return true;
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(Component.text("NPCSpeak commands:", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  /" + label + " spawn <id> [display name]", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("  /" + label + " remove <id>", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("  /" + label + " list", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("  /" + label + " info <id>", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("  /" + label + " edit <id> <name|greeting|prompt|topic|avoid> <value>", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("  /" + label + " task <id> <info|clear|type|goal|check|maxexchanges|onsuccess|onfailure>", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("  /" + label + " example <negotiator|persuader|interrogator|quest|free>", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("  /" + label + " memory list <id>", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("  /" + label + " memory clear <id> <player|*>", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("  /" + label + " reload", NamedTextColor.WHITE));
        if (sender.hasPermission("npcspeak.lore.debug")) sender.sendMessage(Component.text("  /" + label + " lore <npc-id> <deneme sorusu>", NamedTextColor.WHITE));
    }
}
