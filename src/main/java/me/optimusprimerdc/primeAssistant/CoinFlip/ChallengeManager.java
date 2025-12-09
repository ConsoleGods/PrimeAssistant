package me.optimusprimerdc.primeAssistant.CoinFlip;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChallengeManager {

    private final Plugin plugin;
    private final Map<UUID, PendingChallenge> pendingByTarget = new ConcurrentHashMap<>();

    public ChallengeManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public boolean hasPendingForTarget(UUID target) {
        return pendingByTarget.containsKey(target);
    }

    public PendingChallenge getPendingForTarget(UUID target) {
        return pendingByTarget.get(target);
    }

    public void create(PendingChallenge challenge, long timeoutTicks) {
        pendingByTarget.put(challenge.getTarget(), challenge);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            PendingChallenge current = pendingByTarget.get(challenge.getTarget());
            if (current != null && current.equals(challenge)) {
                pendingByTarget.remove(challenge.getTarget());
                if (challenge.getChallengerPlayer() != null && challenge.getChallengerPlayer().isOnline()) {
                    challenge.getChallengerPlayer().sendMessage("Your coinflip request to " + challenge.getTargetName() + " has expired.");
                }
                if (challenge.getTargetPlayer() != null && challenge.getTargetPlayer().isOnline()) {
                    challenge.getTargetPlayer().sendMessage("Coinflip request has expired.");
                }
            }
        }, timeoutTicks);
    }

    public PendingChallenge remove(UUID target) {
        return pendingByTarget.remove(target);
    }

    public void removeByChallenger(UUID challenger) {
        pendingByTarget.values().removeIf(c -> c.getChallenger().equals(challenger));
    }
}