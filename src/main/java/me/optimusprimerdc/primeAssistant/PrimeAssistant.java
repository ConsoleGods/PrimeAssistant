package me.optimusprimerdc.primeAssistant;

import me.optimusprimerdc.primeAssistant.commands.PrimeAssistantCommand;
import me.optimusprimerdc.primeAssistant.items.snowball;
import me.optimusprimerdc.primeAssistant.listener.*;
import me.optimusprimerdc.primeAssistant.updatechecker.UpdateChecker;
import me.optimusprimerdc.primeAssistant.clearlag.ClearLag;
import me.optimusprimerdc.primeAssistant.CoinFlip.CoinFlip;
import me.optimusprimerdc.primeAssistant.config.ConfigManager;
import me.optimusprimerdc.primeAssistant.config.ConfigUpdater;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class PrimeAssistant extends JavaPlugin {

    private ChatFiltering chatFiltering;
    private ClearLag clearLag;
    private Gunpowder gunpowder;
    private ConfigManager configManager;

    private static Economy econ = null;

    @Override
    public void onEnable() {
        // ensure default config is present (won't overwrite an existing file)
        saveDefaultConfig();

        // merge missing keys from bundled config into the existing config file
        ConfigUpdater.updateConfig(this, "config.yml");

        // reload to ensure in-memory config contains merged defaults
        reloadConfig();

        // initialize ConfigManager and perform migrations to bundled config version (use fractional target)
        this.configManager = new ConfigManager(this);
        try {
            // use fractional target to persist 5.1 when present in bundled config
            this.configManager.upgradeConfigIfNeeded(5.1);
            // reload to pick up any changes written by the migration (including config-version)
            reloadConfig();
            getLogger().info("Config upgrade check complete. Current config-version = " + this.configManager.getConfigVersionDouble());
        } catch (Exception ex) {
            getLogger().warning("Config upgrade failed: " + ex.getMessage());
        }

        if (!setupEconomy()) {
            getLogger().severe(String.format("[%s] - Disabled due to no Vault dependency found!", getDescription().getName()));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("Economy provider hooked: " + econ.getName());

        clearLag = new ClearLag(this);

        getServer().getPluginManager().registerEvents(new snowball(), this);
        getServer().getPluginManager().registerEvents(new FastLeafDecay(this), this);

        // keep a reference to Gunpowder so reload can update it
        gunpowder = new Gunpowder(this);
        getServer().getPluginManager().registerEvents(gunpowder, this);

        chatFiltering = new ChatFiltering(this);
        getServer().getPluginManager().registerEvents(chatFiltering, this);

        AntiStasisEnderPearl antiStasis = new AntiStasisEnderPearl(this);
        antiStasis.register();

        UpdateChecker updateChecker = new UpdateChecker(this);
        updateChecker.fetch();
        if (updateChecker.hasUpdateAvailable()) {
            getLogger().info("An update is available! You are running version " + getServer().getPluginManager().getPlugin("PrimeAssistant").getDescription().getVersion() + ", latest version is " + updateChecker.getLatestVersion());
        } else {
            getLogger().info("You are running the latest version of PrimeAssistant!");
        }

        // Use Redstone's single-arg constructor that reads its settings from ConfigManager
        Redstone redstoneProtector = new Redstone(this);
        getServer().getPluginManager().registerEvents(redstoneProtector, this);

        PrimeAssistantCommand mainCommand = new PrimeAssistantCommand(this);
        if (getCommand("primeassistant") != null) {
            getCommand("primeassistant").setExecutor(mainCommand);
            getCommand("primeassistant").setTabCompleter(mainCommand);
        }

        PluginCommand cfCmd = getCommand("cf");
        if (cfCmd != null) {
            // pass the plugin instance to CoinFlip's constructor
            cfCmd.setExecutor(new CoinFlip(this));
        } else {
            getLogger().severe("Command `cf` not found in plugin.yml; coinflip not registered.");
        }

        getServer().getPluginManager().registerEvents(new me.optimusprimerdc.primeAssistant.CoinFlip.CoinFlipMenuListener(), this);

        getLogger().info("  ____       _                   _            _     _              _   ");
        getLogger().info(" |  _ \\ _ __(_)_ __ ___   ___   / \\   ___ ___(_)___| |_ __ _ _ __ | |_ ");
        getLogger().info(" | |_) | '__| | '_ ` _ \\ / _ \\ / _ \\ / __/ __| / __| __/ _` | '_ \\| __|");
        getLogger().info(" |  __/| |  | | | | | | |  __// ___ \\\\__ \\__ \\ \\__ \\ || (_| | | | | |_ ");
        getLogger().info(" |_|   |_|  |_|_| |_| |_|\\___/_/   \\_\\___/___/_|___/\\__\\__,_|_| |_|\\__|");
        getLogger().info("                                                                       ");
        getLogger().info("PrimeAssistant is enabled");
    }

    @Override
    public void onDisable() {
        if (clearLag != null) {
            clearLag.stop();
        }
        if (gunpowder != null) {
            // disable to cancel tasks and clear tracking
            gunpowder.setEnabled(false);
        }
        getLogger().info("PrimeAssistant is disabled");
    }

    /**
     * Reload plugin config and apply relevant changes to subsystems.
     * Call this from your reload command handler.
     */
    public void reloadPluginConfig() {
        reloadConfig();

        // update gunpowder runtime state
        if (gunpowder != null) {
            gunpowder.loadConfig();
            boolean enabled = getConfig().getBoolean("gunpowder.enabled", true);
            gunpowder.setEnabled(enabled);
        }

        // other subsystems can be updated here if they expose reload/load methods
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public ChatFiltering getChatFiltering() {
        return this.chatFiltering;
    }

    public ClearLag getClearLag() {
        return this.clearLag;
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("test-economy")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Only players can use this command.");
                return true;
            }

            Player player = (Player) sender;

            double balance = econ.getBalance(player);
            sender.sendMessage("You have " + econ.format(balance));

            EconomyResponse r = econ.depositPlayer(player, 1.05);
            if (r.transactionSuccess()) {
                sender.sendMessage("You were given " + econ.format(r.amount) + " and now have " + econ.format(r.balance));
            } else {
                sender.sendMessage("An error occurred: " + r.errorMessage);
            }
            return true;
        }

        return false;
    }

    public static Economy getEconomy() {
        return econ;
    }

}