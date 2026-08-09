package sh.variiuz.worldagent.command;

import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import sh.variiuz.worldagent.WorldAgentPlugin;

public final class WorldAgentCommand implements CommandExecutor, TabCompleter {

    private final WorldAgentPlugin plugin;

    public WorldAgentCommand(WorldAgentPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("worldagent.admin")) {
            sender.sendMessage("No permission.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("/worldagent <reload|status|token|undo|redo|tx>");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reloadAll();
                sender.sendMessage("World Agent reloaded.");
            }
            case "status" -> {
                boolean up = plugin.getApiServer() != null && plugin.getApiServer().isRunning();
                String host = plugin.getConfig().getString("http.host", "127.0.0.1");
                int port = plugin.getConfig().getInt("http.port", 8765);
                sender.sendMessage("API: " + (up ? "UP" : "DOWN") + " @ " + host + ":" + port);
            }
            case "undo" -> {
                try {
                    var result = plugin.getTransactions().undo();
                    sender.sendMessage("Undid " + result.get("label").getAsString() + " (" + result.get("blocks").getAsInt() + " blocks)");
                } catch (Exception e) {
                    sender.sendMessage("Undo failed: " + e.getMessage());
                }
            }
            case "redo" -> {
                try {
                    var result = plugin.getTransactions().redo();
                    sender.sendMessage("Redid " + result.get("label").getAsString() + " (" + result.get("blocks").getAsInt() + " blocks)");
                } catch (Exception e) {
                    sender.sendMessage("Redo failed: " + e.getMessage());
                }
            }
            case "tx" -> {
                var list = plugin.getTransactions().list();
                sender.sendMessage("Undo stack: " + list.get("undo_size") + " | Redo: " + list.get("redo_size"));
            }
            case "token" -> {
                String token = plugin.getConfig().getString("http.token", "");
                sender.sendMessage("Token length: " + token.length() + " (see config.yml)");
            }
            default -> sender.sendMessage("/worldagent <reload|status|token|undo|redo|tx>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("reload", "status", "token", "undo", "redo", "tx").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
