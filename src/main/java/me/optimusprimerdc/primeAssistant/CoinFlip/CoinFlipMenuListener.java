package me.optimusprimerdc.primeAssistant.CoinFlip;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * Prevents players from taking items out of the coinflip menu.
 */
public class CoinFlipMenuListener implements Listener {
    private static final String TITLE = "CoinFlip Result";

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView() == null) return;
        if (TITLE.equals(event.getView().getTitle())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView() == null) return;
        if (TITLE.equals(event.getView().getTitle())) {
            event.setCancelled(true);
        }
    }
}