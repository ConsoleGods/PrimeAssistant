package me.optimusprimerdc.primeAssistant.commands.subcommands;

import me.optimusprimerdc.primeAssistant.PrimeAssistant;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

public class ReloadCommand {

    private final PrimeAssistant plugin;
    private final String prefix;

    public ReloadCommand(PrimeAssistant plugin) {
        this.plugin = plugin;
        this.prefix = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("prefix", "&8[&6PrimeAssistant&8] &r"));
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("primeassistant.reload")) {
            sender.sendMessage(prefix + ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        try {
            plugin.reloadConfig();

            if (plugin.getChatFiltering() != null) {
                plugin.getChatFiltering().reload();
            }

            sender.sendMessage(prefix + ChatColor.GREEN + "Configuration reloaded successfully!");
            plugin.getLogger().info(sender.getName() + " reloaded the configuration.");
            return true;
        } catch (Exception e) {
            sender.sendMessage(prefix + ChatColor.RED + "Failed to reload configuration. Check console for errors.");
            plugin.getLogger().severe("Error reloading config: " + e.getMessage());
            return true;
        }
    }
}
