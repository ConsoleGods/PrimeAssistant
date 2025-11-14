package me.optimusprimerdc.primeAssistant;

import me.optimusprimerdc.primeAssistant.items.snowball;
import me.optimusprimerdc.primeAssistant.listener.FastLeafDecay;
import org.bukkit.plugin.java.JavaPlugin;

public final class PrimeAssistant extends JavaPlugin {

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(new snowball(), this);

        getServer().getPluginManager().registerEvents(new FastLeafDecay(this), this);

    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
