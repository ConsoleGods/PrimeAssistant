package me.optimusprimerdc.primeAssistant.CoinFlip;

import me.optimusprimerdc.primeAssistant.PrimeAssistant;
import me.optimusprimerdc.primeAssistant.config.ConfigManager;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.function.BiConsumer;

public class CoinFlip implements CommandExecutor {

    private final PrimeAssistant plugin;
    private final ConfigManager cfg;
    private final StreakManager streakManager;
    private final ChallengeManager challengeManager;
    private final long timeoutTicks;

    public CoinFlip(PrimeAssistant plugin) {
        this.plugin = plugin;
        this.cfg = plugin.getConfigManager();
        this.streakManager = new StreakManager(plugin);
        this.challengeManager = new ChallengeManager(plugin);

        // use ConfigManager accessor for timeout (seconds -> ticks)
        int seconds = cfg.getCoinflipTimeoutSeconds();
        this.timeoutTicks = Math.max(1, seconds) * 20L;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player challenger)) {
            sender.sendMessage(colorErr("Only players can use this command."));
            return true;
        }

        // Respect the coinflip enabled/disabled toggle in config
        if (!cfg.isCoinflipEnabled()) {
            challenger.sendMessage(colorNote("CoinFlip feature is currently disabled."));
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("accept")) {
            return handleAccept(challenger, args);
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("deny")) {
            return handleDeny(challenger, args);
        }

        if (args.length != 2) {
            challenger.sendMessage(colorUsage("/cf <player> <amount>"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || !target.isOnline()) {
            challenger.sendMessage(colorErr("Player not found or not online."));
            return true;
        }

        if (target.equals(challenger)) {
            challenger.sendMessage(colorErr("You cannot coinflip yourself."));
            return true;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[1]);
            if (amount <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            challenger.sendMessage(colorErr("Invalid amount."));
            return true;
        }

        Economy econ = PrimeAssistant.getEconomy();
        if (econ == null) {
            challenger.sendMessage(colorErr("Economy provider not available."));
            return true;
        }

        // Enforce min/max from ConfigManager accessors
        int minAmount = cfg.getCoinflipMinAmount();
        int maxAmount = cfg.getCoinflipMaxAmount();

        if (amount < minAmount) {
            String minStr = econ.format(minAmount);
            challenger.sendMessage(colorErr("Amount must be at least " + minStr + "."));
            return true;
        }

        if (maxAmount > 0 && amount > maxAmount) {
            String maxStr = econ.format(maxAmount);
            challenger.sendMessage(colorErr("Amount must be at most " + maxStr + "."));
            return true;
        }

        if (econ.getBalance(challenger) < amount) {
            challenger.sendMessage(colorErr("You do not have enough money."));
            return true;
        }

        if (econ.getBalance(target) < amount) {
            challenger.sendMessage(colorErr("Target does not have enough money."));
            return true;
        }

        if (challengeManager.hasPendingForTarget(target.getUniqueId())) {
            challenger.sendMessage(colorNote("That player already has a pending coinflip."));
            return true;
        }

        PendingChallenge challenge = new PendingChallenge(challenger.getUniqueId(), target.getUniqueId(), amount);
        challengeManager.create(challenge, timeoutTicks);

        String secondsLabel = cfg.getCoinflipTimeoutSeconds() + "s";
        challenger.sendMessage(colorSuccess("Request sent") + " " + colorPlain("to " + target.getName() + ". They have ") + colorHighlight(secondsLabel) + colorPlain(" to respond."));
        target.sendMessage(colorAccent(challenger.getName()) + colorPlain(" has challenged you for ") + colorHighlight(econ.format(amount)) + ".");
        target.sendMessage(colorAction("/cf accept ") + colorPlain(" ") + colorPlain(ChatColor.GRAY + "or") + colorPlain(" ") + colorAction("/cf deny"));

        return true;
    }

    private boolean handleAccept(Player target, String[] args) {
        PendingChallenge pending = challengeManager.getPendingForTarget(target.getUniqueId());
        if (args.length >= 2) {
            Player challenger = Bukkit.getPlayerExact(args[1]);
            if (challenger == null || !challenger.isOnline()) {
                target.sendMessage(colorErr("Challenger not found or not online."));
                return true;
            }
            if (pending == null || !pending.getChallenger().equals(challenger.getUniqueId())) {
                target.sendMessage(colorNote("No matching coinflip request from that player."));
                return true;
            }
        } else {
            if (pending == null) {
                target.sendMessage(colorNote("You have no pending coinflip requests."));
                return true;
            }
        }

        Player challenger = Bukkit.getPlayer(pending.getChallenger());
        if (challenger == null || !challenger.isOnline()) {
            target.sendMessage(colorErr("Challenger not found or offline."));
            challengeManager.remove(target.getUniqueId());
            return true;
        }

        Economy e = PrimeAssistant.getEconomy();
        if (e == null) {
            target.sendMessage(colorErr("Economy provider not available."));
            return true;
        }
        double amount = pending.getAmount();
        if (e.getBalance(challenger) < amount) {
            target.sendMessage(colorErr("Challenger does not have enough money."));
            challenger.sendMessage(colorNote("You no longer have enough money to accept the coinflip."));
            challengeManager.remove(target.getUniqueId());
            return true;
        }
        if (e.getBalance(target) < amount) {
            target.sendMessage(colorErr("You do not have enough money."));
            challengeManager.remove(target.getUniqueId());
            return true;
        }

        challengeManager.remove(target.getUniqueId());

        BiConsumer<Player, Player> onComplete = createResultConsumer(amount);
        CoinFlipMenu.openAnimatedResult(plugin, challenger, target, amount, onComplete);
        return true;
    }

    private boolean handleDeny(Player target, String[] args) {
        PendingChallenge pending = challengeManager.getPendingForTarget(target.getUniqueId());
        if (args.length >= 2) {
            Player challenger = Bukkit.getPlayerExact(args[1]);
            if (challenger == null || !challenger.isOnline()) {
                target.sendMessage(colorErr("Challenger not found or not online."));
                return true;
            }
            if (pending == null || !pending.getChallenger().equals(challenger.getUniqueId())) {
                target.sendMessage(colorNote("No matching coinflip request from that player."));
                return true;
            }
        } else {
            if (pending == null) {
                target.sendMessage(colorNote("You have no pending coinflip requests."));
                return true;
            }
        }

        Player challenger = Bukkit.getPlayer(pending.getChallenger());
        if (challenger == null || !challenger.isOnline()) {
            target.sendMessage(colorErr("Challenger not found or offline."));
            challengeManager.remove(target.getUniqueId());
            return true;
        }

        challengeManager.remove(target.getUniqueId());
        target.sendMessage(colorDecline("Declined: ") + colorPlain("You denied the coinflip from ") + colorAccent(challenger.getName()) + ".");
        challenger.sendMessage(colorNote(target.getName() + " denied your coinflip request."));
        return true;
    }

    private BiConsumer<Player, Player> createResultConsumer(double amount) {
        return (winner, loser) -> {
            Economy e = PrimeAssistant.getEconomy();
            if (e == null) {
                if (winner.isOnline()) winner.sendMessage(colorErr("Economy provider not available."));
                if (loser.isOnline()) loser.sendMessage(colorErr("Economy provider not available."));
                return;
            }

            String formatted = e.format(amount);

            EconomyResponse withdraw = e.withdrawPlayer(loser, amount);
            if (!withdraw.transactionSuccess()) {
                String err = withdraw.errorMessage != null ? withdraw.errorMessage : "Unknown error";
                String failMsg = colorFail("CoinFlip failed: ") + colorPlain(err);
                if (winner.isOnline()) winner.sendMessage(failMsg);
                if (loser.isOnline()) loser.sendMessage(failMsg);
                plugin.getLogger().warning("CoinFlip withdraw failed: " + err);
                return;
            }

            EconomyResponse deposit = e.depositPlayer(winner, amount);
            if (!deposit.transactionSuccess()) {
                String err = deposit.errorMessage != null ? deposit.errorMessage : "Unknown error";
                e.depositPlayer(loser, amount);
                String failMsg = colorFail("CoinFlip failed: ") + colorPlain(err);
                if (winner.isOnline()) winner.sendMessage(failMsg);
                if (loser.isOnline()) loser.sendMessage(failMsg);
                plugin.getLogger().warning("CoinFlip deposit failed: " + err);
                return;
            }

            UUID winId = winner.getUniqueId();
            UUID loseId = loser.getUniqueId();

            int loserPrevStreak = streakManager.get(loseId);
            int newStreak = streakManager.increment(winId);

            if (loserPrevStreak > 0) {
                streakManager.reset(loseId);
            }

            String winnerTitle = ChatColor.GREEN + "" + ChatColor.BOLD + "CoinFlip » " + ChatColor.RESET + ChatColor.WHITE + "You won " + ChatColor.YELLOW + formatted + ChatColor.RESET + ChatColor.WHITE + "!";
            String winnerStreak = ChatColor.GRAY + "  Current streak: " + ChatColor.GOLD + newStreak;
            String msgWinner = winnerTitle + winnerStreak;

            String loserTitle = ChatColor.RED + "" + ChatColor.BOLD + "CoinFlip » " + ChatColor.RESET + ChatColor.WHITE + "You lost " + ChatColor.YELLOW + formatted + ChatColor.RESET + ChatColor.WHITE + ".";
            String msgLoser;
            if (loserPrevStreak > 0) {
                String loserStreak = ChatColor.GRAY + " Your streak has been reset.";
                msgLoser = loserTitle + loserStreak;
            } else {
                msgLoser = loserTitle;
            }

            if (winner.isOnline()) winner.sendMessage(msgWinner);
            if (loser.isOnline()) loser.sendMessage(msgLoser);
        };
    }

    // Helper styling methods that use ChatColor and keep messaging consistent with config prefix
    private String colorPlain(String s) {
        return ChatColor.WHITE + s;
    }

    private String colorErr(String s) {
        return ChatColor.RED + "" + ChatColor.BOLD + s;
    }

    private String colorNote(String s) {
        return ChatColor.GRAY + "" + ChatColor.ITALIC + s;
    }

    private String colorSuccess(String s) {
        return ChatColor.GREEN + "" + ChatColor.BOLD + s;
    }

    private String colorAction(String s) {
        return ChatColor.AQUA + "" + ChatColor.BOLD + s;
    }

    private String colorAccent(String s) {
        return ChatColor.GOLD + "" + ChatColor.BOLD + s;
    }

    private String colorHighlight(String s) {
        return ChatColor.AQUA + ChatColor.BOLD.toString() + s;
    }

    private String colorDecline(String s) {
        return ChatColor.RED + "" + ChatColor.BOLD + s;
    }

    private String colorFail(String s) {
        return ChatColor.RED + "" + ChatColor.BOLD + s;
    }

    private String colorUsage(String s) {
        String prefix = ChatColor.translateAlternateColorCodes('&', cfg.getPrefix());
        return prefix + ChatColor.YELLOW + s;
    }
}