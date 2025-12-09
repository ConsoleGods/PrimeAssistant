package me.optimusprimerdc.primeAssistant.CoinFlip;

import me.optimusprimerdc.primeAssistant.PrimeAssistant;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.BiConsumer;

public class CoinFlipMenu {

    // Keep the old simple result method for direct use
    public static void openResult(Player winner, Player loser, double amount) {
        Inventory inv = Bukkit.createInventory(null, 27, "CoinFlip Result");

        // Fill background with gray panes
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta paneMeta = pane.getItemMeta();
        paneMeta.setDisplayName(" ");
        pane.setItemMeta(paneMeta);
        for (int i = 0; i < 27; i++) inv.setItem(i, pane);

        Economy econ = PrimeAssistant.getEconomy();
        String formattedAmount = econ != null ? econ.format(amount) : String.valueOf(amount);

        // Winner head
        ItemStack winnerHead = makeHeadItem(winner, ChatColor.GREEN + "Winner: " + winner.getName(),
                ChatColor.YELLOW + "Won: " + ChatColor.WHITE + formattedAmount);

        inv.setItem(11, winnerHead);

        // Loser head
        ItemStack loserHead = makeHeadItem(loser, ChatColor.RED + "Loser: " + loser.getName(),
                ChatColor.GRAY + "Lost: " + ChatColor.WHITE + formattedAmount);

        inv.setItem(15, loserHead);

        // Center item showing result summary
        ItemStack center = new ItemStack(Material.PAPER);
        ItemMeta cMeta = center.getItemMeta();
        cMeta.setDisplayName(ChatColor.AQUA + "CoinFlip Result");
        cMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "Winner: " + ChatColor.WHITE + winner.getName(),
                ChatColor.GRAY + "Loser: " + ChatColor.WHITE + loser.getName(),
                ChatColor.GRAY + "Amount: " + ChatColor.WHITE + formattedAmount
        ));
        center.setItemMeta(cMeta);
        inv.setItem(13, center);

        winner.openInventory(inv);
        loser.openInventory(inv);
    }

    // Animated method with a completion callback invoked with (winner, loser)
    public static void openAnimatedResult(PrimeAssistant plugin, Player p1, Player p2, double amount, BiConsumer<Player, Player> onComplete) {
        Inventory inv = Bukkit.createInventory(null, 27, "CoinFlip Result");

        // Fill background with gray panes
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta paneMeta = pane.getItemMeta();
        paneMeta.setDisplayName(" ");
        pane.setItemMeta(paneMeta);
        for (int i = 0; i < 27; i++) inv.setItem(i, pane);

        Economy econ = PrimeAssistant.getEconomy();
        String formattedAmount = econ != null ? econ.format(amount) : String.valueOf(amount);

        // Neutral center while animating
        ItemStack neutral = new ItemStack(Material.PAPER);
        ItemMeta nMeta = neutral.getItemMeta();
        nMeta.setDisplayName(ChatColor.AQUA + "Flipping...");
        nMeta.setLore(Arrays.asList(ChatColor.GRAY + "Winner will be chosen soon"));
        neutral.setItemMeta(nMeta);
        inv.setItem(13, neutral);

        // Prepare the two head item variants (no winner/loser label yet)
        ItemStack head1 = makeHeadItem(p1, ChatColor.WHITE + p1.getName(),
                ChatColor.YELLOW + "Amount: " + ChatColor.WHITE + formattedAmount);
        ItemStack head2 = makeHeadItem(p2, ChatColor.WHITE + p2.getName(),
                ChatColor.YELLOW + "Amount: " + ChatColor.WHITE + formattedAmount);

        // Initialize the middle row (slots 9-17) with an alternating pattern
        List<ItemStack> row = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            row.add(i % 2 == 0 ? head1.clone() : head2.clone());
        }
        for (int i = 0; i < 9; i++) inv.setItem(9 + i, row.get(i));

        // Open for both players
        if (p1.isOnline()) p1.openInventory(inv);
        if (p2.isOnline()) p2.openInventory(inv);

        // Decide winner only at the end
        Random random = new Random();
        boolean pickFirstAsWinner = random.nextBoolean();

        // Animation parameters
        int totalShifts = 40 + random.nextInt(16); // total shifts before stopping
        long tickDelay = 3L; // ticks between shifts (3 ticks ~150ms)
        new BukkitRunnable() {
            int shifts = 0;
            int slowDownCounter = 0;

            @Override
            public void run() {
                // rotate right by one position
                ItemStack last = row.get(row.size() - 1);
                for (int i = row.size() - 1; i > 0; i--) {
                    row.set(i, row.get(i - 1));
                }
                row.set(0, last);

                // write row back to inventory (slots 9..17)
                for (int i = 0; i < 9; i++) {
                    inv.setItem(9 + i, row.get(i));
                }

                // Keep center neutral until finish
                inv.setItem(13, neutral);

                shifts++;

                // slow down near the end for better effect
                if (shifts > totalShifts - 8) {
                    slowDownCounter++;
                    if (slowDownCounter % 2 == 0) {
                        // perceptual slowdown handled via tick spacing
                    }
                }

                if (shifts >= totalShifts) {
                    // Stop animation and reveal winner in center
                    cancel();

                    Player winner = pickFirstAsWinner ? p1 : p2;
                    Player loser = pickFirstAsWinner ? p2 : p1;

                    // Winner head for center
                    ItemStack winnerHead = makeHeadItem(winner, ChatColor.GREEN + "Winner: " + winner.getName(),
                            ChatColor.YELLOW + "Won: " + ChatColor.WHITE + formattedAmount);

                    // Clear the entire middle row to gray panes and set only slot 13 to the winner head
                    for (int i = 0; i < 9; i++) {
                        inv.setItem(9 + i, pane.clone());
                    }
                    inv.setItem(13, winnerHead);

                    // Ensure both players see the final result
                    if (winner.isOnline()) winner.openInventory(inv);
                    if (loser.isOnline()) loser.openInventory(inv);

                    // Invoke completion callback on the main thread
                    if (onComplete != null) {
                        try {
                            onComplete.accept(winner, loser);
                        } catch (Exception ex) {
                            plugin.getLogger().severe("Error in coinflip completion callback: " + ex.getMessage());
                            ex.printStackTrace();
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, tickDelay);
    }

    // Helper to create a player head item with display name and lore
    private static ItemStack makeHeadItem(Player player, String displayName, String loreLine) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            OfflinePlayer off = Bukkit.getOfflinePlayer(player.getUniqueId());
            meta.setOwningPlayer(off);
            meta.setDisplayName(displayName);
            meta.setLore(Arrays.asList(loreLine));
            head.setItemMeta(meta);
        }
        return head;
    }
}