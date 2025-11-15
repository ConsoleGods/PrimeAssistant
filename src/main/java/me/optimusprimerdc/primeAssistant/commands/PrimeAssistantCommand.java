package me.optimusprimerdc.primeAssistant.commands;

import me.optimusprimerdc.primeAssistant.PrimeAssistant;
import me.optimusprimerdc.primeAssistant.commands.subcommands.ReloadCommand;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;

public class PrimeAssistantCommand implements CommandExecutor, TabCompleter {

    private final PrimeAssistant plugin;
    private final ReloadCommand reloadCommand;
    private final String prefix;

    public PrimeAssistantCommand(PrimeAssistant plugin) {
        this.plugin = plugin;
        this.reloadCommand = new ReloadCommand(plugin);
        this.prefix = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("prefix", "&8[&6PrimeAssistant&8] &r"));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(prefix + ChatColor.GOLD + "PrimeAssistant Commands:");
            sender.sendMessage(prefix + ChatColor.YELLOW + "/primeassistant reload " + ChatColor.GRAY + "- Reload the configuration");
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "reload":
                return reloadCommand.execute(sender, args);
            default:
                sender.sendMessage(prefix + ChatColor.RED + "Unknown subcommand. Use /primeassistant for help.");
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            if ("reload".startsWith(args[0].toLowerCase())) {
                completions.add("reload");
            }
        }

        return completions;
    }
}
