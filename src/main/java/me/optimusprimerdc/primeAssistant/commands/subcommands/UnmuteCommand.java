package me.optimusprimerdc.primeAssistant.commands.subcommands;

import me.optimusprimerdc.primeAssistant.PrimeAssistant;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class UnmuteCommand implements CommandExecutor {
    private final PrimeAssistant plugin;
    private final String prefix;

    public UnmuteCommand(PrimeAssistant plugin) {
        this.plugin = plugin;
        this.prefix = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("prefix", "&7[PrimeAssistant]&r "));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("primeassistant.unmute")) {
            sender.sendMessage(prefix + ChatColor.RED + "You do not have permission to run this command.");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(prefix + ChatColor.RED + "Usage: /primeassistant unmute <player>");
            return true;
        }

        String targetName = args[0].trim();


        OfflinePlayer targetPlayer = null;
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            if (op.getName() != null && op.getName().equalsIgnoreCase(targetName)) {
                targetPlayer = op;
                break;
            }
        }

        if (targetPlayer == null) {
            sender.sendMessage(prefix + ChatColor.RED + "Player " + targetName + " not found. They may never have joined.");
            return true;
        }

        UUID targetUUID = targetPlayer.getUniqueId();
        boolean wasRemoved = plugin.getChatFiltering().unmutePlayer(targetUUID);

        if (!wasRemoved) {
            sender.sendMessage(prefix + ChatColor.YELLOW + targetName + " is not muted.");
            return true;
        }

        sender.sendMessage(prefix + ChatColor.GREEN + "Unmuted " + targetName + ".");

        Player onlinePlayer = targetPlayer.getPlayer();
        if (onlinePlayer != null && onlinePlayer.isOnline()) {
            onlinePlayer.sendMessage(prefix + ChatColor.GREEN + "You have been unmuted.");
        }

        return true;
    }
}
