package me.optimusprimerdc.primeAssistant;

import me.optimusprimerdc.primeAssistant.commands.PrimeAssistantCommand;
import me.optimusprimerdc.primeAssistant.items.snowball;
import me.optimusprimerdc.primeAssistant.listener.ChatFiltering;
import me.optimusprimerdc.primeAssistant.listener.FastLeafDecay;
import me.optimusprimerdc.primeAssistant.updatechecker.UpdateChecker;
import me.optimusprimerdc.primeAssistant.listener.Redstone;
import me.optimusprimerdc.primeAssistant.listener.AntiStasisEnderPearl;

import org.bukkit.plugin.java.JavaPlugin;

public final class PrimeAssistant extends JavaPlugin {

    private ChatFiltering chatFiltering;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        getServer().getPluginManager().registerEvents(new snowball(), this);
        getServer().getPluginManager().registerEvents(new FastLeafDecay(this), this);

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

        double tpsThreshold = getConfig().getDouble("redstone-protection.tps-threshold", 15.0);
        boolean enablePurge = getConfig().getBoolean("redstone-protection.enable-purge", true);
        Redstone redstoneProtector = new Redstone(this, tpsThreshold, enablePurge);
        getServer().getPluginManager().registerEvents(redstoneProtector, this);

        PrimeAssistantCommand mainCommand = new PrimeAssistantCommand(this);
        getCommand("primeassistant").setExecutor(mainCommand);
        getCommand("primeassistant").setTabCompleter(mainCommand);

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
        getLogger().info("PrimeAssistant is disabled");
    }

    public ChatFiltering getChatFiltering() {
        return this.chatFiltering;
    }
}