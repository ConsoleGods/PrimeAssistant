package me.optimusprimerdc.primeAssistant.CoinFlip;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.UUID;

public class PendingChallenge {
    private final UUID challenger;
    private final UUID target;
    private final double amount;

    public PendingChallenge(UUID challenger, UUID target, double amount) {
        this.challenger = challenger;
        this.target = target;
        this.amount = amount;
    }

    public UUID getChallenger() {
        return challenger;
    }

    public UUID getTarget() {
        return target;
    }

    public double getAmount() {
        return amount;
    }

    public Player getChallengerPlayer() {
        return Bukkit.getPlayer(challenger);
    }

    public Player getTargetPlayer() {
        return Bukkit.getPlayer(target);
    }

    public String getChallengerName() {
        Player p = getChallengerPlayer();
        return p != null ? p.getName() : challenger.toString();
    }

    public String getTargetName() {
        Player p = getTargetPlayer();
        return p != null ? p.getName() : target.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PendingChallenge)) return false;
        PendingChallenge that = (PendingChallenge) o;
        return Double.compare(that.amount, amount) == 0 &&
                Objects.equals(challenger, that.challenger) &&
                Objects.equals(target, that.target);
    }

    @Override
    public int hashCode() {
        return Objects.hash(challenger, target, amount);
    }
}
