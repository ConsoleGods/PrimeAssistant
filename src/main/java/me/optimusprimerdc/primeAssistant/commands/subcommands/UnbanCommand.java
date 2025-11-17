package me.optimusprimerdc.primeAssistant.commands.subcommands;

import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public class UnbanCommand implements CommandExecutor {
    private final JavaPlugin plugin;
    private final String prefix;

    public UnbanCommand(JavaPlugin plugin) {
        this.plugin = plugin;
        this.prefix = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("prefix", "&7[PrimeAssistant]&r "));
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("primeassistant.unban")) {
            sender.sendMessage(prefix + ChatColor.RED + "You do not have permission to run this command.");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(prefix + ChatColor.RED + "Usage: /" + label + " unban <player>");
            return true;
        }

        String target = args[0];
        BanList banList = Bukkit.getBanList(BanList.Type.NAME);

        if (!banList.isBanned(target)) {
            sender.sendMessage(prefix + ChatColor.YELLOW + target + " is not banned.");
            return true;
        }

        banList.pardon(target);
        sender.sendMessage(prefix + ChatColor.GREEN + "Unbanned " + target + ".");
        return true;
    }
}