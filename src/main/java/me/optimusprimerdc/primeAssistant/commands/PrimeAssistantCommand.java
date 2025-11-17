package me.optimusprimerdc.primeAssistant.commands;

import me.optimusprimerdc.primeAssistant.PrimeAssistant;
import me.optimusprimerdc.primeAssistant.commands.subcommands.ReloadCommand;
import me.optimusprimerdc.primeAssistant.commands.subcommands.UnbanCommand;
import me.optimusprimerdc.primeAssistant.commands.subcommands.UnmuteCommand;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PrimeAssistantCommand implements CommandExecutor, TabCompleter {

    private final PrimeAssistant plugin;
    private final ReloadCommand reloadCommand;
    private final UnbanCommand unbanCommand;
    private final UnmuteCommand unmuteCommand;
    private final String prefix;

    public PrimeAssistantCommand(PrimeAssistant plugin) {
        this.plugin = plugin;
        this.reloadCommand = new ReloadCommand(plugin);
        this.unbanCommand = new UnbanCommand(plugin);
        this.unmuteCommand = new UnmuteCommand(plugin);
        this.prefix = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("prefix", "&8[&6PrimeAssistant&8] &r"));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(prefix + ChatColor.GOLD + "PrimeAssistant Commands:");
            sender.sendMessage(prefix + ChatColor.YELLOW + "/primeassistant reload " + ChatColor.GRAY + "- Reload the configuration");
            sender.sendMessage(prefix + ChatColor.YELLOW + "/primeassistant unban <player> " + ChatColor.GRAY + "- Unban a player");
            sender.sendMessage(prefix + ChatColor.YELLOW + "/primeassistant unmute <player> " + ChatColor.GRAY + "- Unmute a player");
            return true;
        }

        String subCommand = args[0].toLowerCase().trim();

        switch (subCommand) {
            case "reload":
                return reloadCommand.execute(sender, args);
            case "unban": {
                String[] subArgs = args.length > 1 ? Arrays.copyOfRange(args, 1, args.length) : new String[0];
                return unbanCommand.onCommand(sender, command, label, subArgs);
            }
            case "unmute": {
                String[] subArgs = args.length > 1 ? Arrays.copyOfRange(args, 1, args.length) : new String[0];
                return unmuteCommand.onCommand(sender, command, label, subArgs);
            }
            default:
                sender.sendMessage(prefix + ChatColor.RED + "Unknown subcommand: '" + args[0] + "'. Use /primeassistant for help.");
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String current = args[0].toLowerCase();
            if ("reload".startsWith(current)) completions.add("reload");
            if ("unban".startsWith(current)) completions.add("unban");
            if ("unmute".startsWith(current)) completions.add("unmute");
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            if ("unban".equals(subCommand) || "unmute".equals(subCommand)) {
                String currentPlayer = args[1].toLowerCase();
                for (org.bukkit.entity.Player p : plugin.getServer().getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(currentPlayer)) {
                        completions.add(p.getName());
                    }
                }
            }
        }

        return completions;
    }
}