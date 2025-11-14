package me.optimusprimerdc.primeAssistant.items;

import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;

public class snowball implements Listener {
    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        double radius = 30.0;

        int count = 0;
        for (Entity nearby : projectile.getNearbyEntities(radius, radius, radius)) {
            if (nearby instanceof Projectile) {
                count++;
            }
        }

        if (count > 150) {
            for (Entity nearby : projectile.getNearbyEntities(radius, radius, radius)) {
                if (nearby instanceof Projectile) {
                    nearby.remove();
                }
            }
        }
    }

}