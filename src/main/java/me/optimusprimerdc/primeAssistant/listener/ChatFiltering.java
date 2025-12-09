package me.optimusprimerdc.primeAssistant.listener;

import me.optimusprimerdc.primeAssistant.PrimeAssistant;
import me.optimusprimerdc.primeAssistant.config.ConfigManager;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.profile.PlayerProfile;

import java.net.HttpURLConnection;
import java.net.URI;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatFiltering implements Listener {

    private final PrimeAssistant plugin;
    private final ConfigManager cfg;
    private final Map<String, Pattern> patterns = new LinkedHashMap<>();
    private final Map<String, String> punishments = new HashMap<>();
    private final Map<UUID, Long> mutedPlayers = new HashMap<>();
    private String webhookUrl;
    private boolean notifyStaff;
    private String staffPermission;
    private String defaultPunishment;
    private String prefix;

    public ChatFiltering(PrimeAssistant plugin) {
        this.plugin = plugin;
        this.cfg = plugin.getConfigManager();
        loadConfig();
    }

    public void reload() {
        loadConfig();
        plugin.getLogger().info("ChatFiltering reloaded");
    }

    private void loadConfig() {
        // prefix & top-level
        this.prefix = ChatColor.translateAlternateColorCodes('&', cfg.getPrefix());

        // chat filter section via ConfigManager
        this.webhookUrl = cfg.getChatWebhookUrl();
        this.notifyStaff = cfg.isChatNotifyStaff();
        this.staffPermission = cfg.getChatStaffPermission();
        this.defaultPunishment = cfg.getChatDefaultPunishment().toUpperCase(Locale.ROOT);

        patterns.clear();
        List<String> basic = cfg.getChatBasicWords();
        if (basic != null) {
            for (String w : basic) {
                if (w == null || w.isBlank()) continue;
                String key = w.toLowerCase(Locale.ROOT);
                Pattern p = Pattern.compile("\\b" + Pattern.quote(key) + "\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);
                patterns.put(key, p);
            }
        }

        List<String> advanced = cfg.getChatAdvancedWords();
        if (advanced != null) {
            for (String w : advanced) {
                if (w == null || w.isBlank()) continue;
                String key = w.toLowerCase(Locale.ROOT);
                Pattern p = Pattern.compile(convertToFlexibleRegex(key), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);
                patterns.put(key, p);
            }
        }

        punishments.clear();
        Map<String, String> ps = cfg.getChatPunishments();
        if (ps != null) {
            for (Map.Entry<String, String> e : ps.entrySet()) {
                punishments.put(e.getKey().toLowerCase(Locale.ROOT), e.getValue().toUpperCase(Locale.ROOT));
            }
        }

        plugin.getLogger().info("ChatFiltering loaded: " + patterns.size() + " word patterns, webhook set=" + (!webhookUrl.isEmpty()));
    }

    private String convertToFlexibleRegex(String word) {
        Map<Character, String> substitutions = new HashMap<>();
        substitutions.put('a', "[a@4]");
        substitutions.put('e', "[e3]");
        substitutions.put('i', "[i1!|]");
        substitutions.put('o', "[o0]");
        substitutions.put('s', "[s5$]");
        substitutions.put('t', "[t7+]");
        substitutions.put('b', "[b8]");
        substitutions.put('g', "[g9]");
        substitutions.put('l', "[l1|]");
        substitutions.put('z', "[z2]");

        StringBuilder sb = new StringBuilder();
        sb.append("(?i)");

        for (int i = 0; i < word.length(); i++) {
            char c = Character.toLowerCase(word.charAt(i));

            if (substitutions.containsKey(c)) {
                sb.append(substitutions.get(c));
            } else {
                sb.append(Pattern.quote(String.valueOf(c)));
            }

            if (i < word.length() - 1) {
                sb.append("[\\s._*-]*");
            }
        }

        return sb.toString();
    }

    public void mutePlayer(UUID playerUUID, long durationSeconds) {
        long expiryTime = durationSeconds > 0 ? System.currentTimeMillis() + (durationSeconds * 1000) : 0;
        mutedPlayers.put(playerUUID, expiryTime);
    }

    public boolean isMuted(UUID playerUUID) {
        Long expiryTime = mutedPlayers.get(playerUUID);
        if (expiryTime == null) return false;

        if (expiryTime > 0 && System.currentTimeMillis() > expiryTime) {
            mutedPlayers.remove(playerUUID);
            return false;
        }
        return true;
    }

    public boolean unmutePlayer(UUID playerUUID) {
        return mutedPlayers.remove(playerUUID) != null;
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        String bypassPermission = cfg.getString("chat-filter.bypass-permission", "primeassistant.chat.bypass");
        if (player.isOp() || player.hasPermission(bypassPermission)) {
            return;
        }

        if (isMuted(player.getUniqueId())) {
            event.setCancelled(true);
            String tpl = cfg.getChatMessage("muted", "You are muted and cannot send messages.");
            player.sendMessage(prefix + replacePlaceholders(tpl, player.getName(), event.getMessage(), ""));
            return;
        }

        String message = event.getMessage();
        String lower = message.toLowerCase(Locale.ROOT);

        for (Map.Entry<String, Pattern> entry : patterns.entrySet()) {
            Matcher m = entry.getValue().matcher(lower);
            if (m.find()) {
                String matchedWord = entry.getKey();
                String punishment = punishments.getOrDefault(matchedWord, defaultPunishment);
                applyPunishment(event, player, matchedWord, punishment);
                return;
            }
        }
    }

    private void applyPunishment(AsyncPlayerChatEvent event, Player player, String word, String punishment) {
        punishment = punishment.toUpperCase(Locale.ROOT);
        plugin.getLogger().info("Chat filter triggered by " + player.getName() + " word=" + word + " punishment=" + punishment);

        String user = player.getName();
        String message = event.getMessage();
        String baseMessage = cfg.getChatMessage(punishment.toLowerCase(Locale.ROOT), null);

        event.setCancelled(true);

        switch (punishment) {
            case "BLOCK":
                if (baseMessage != null) {
                    player.sendMessage(prefix + replacePlaceholders(baseMessage, user, message, word));
                }
                break;

            case "MUTE":
                int muteDuration = cfg.getChatMuteDuration();
                mutePlayer(player.getUniqueId(), muteDuration);
                if (baseMessage != null) {
                    player.sendMessage(prefix + replacePlaceholders(baseMessage, user, message, word));
                }
                break;

            case "BAN":
                String reason = cfg.getChatBanReason();
                int banDuration = cfg.getChatBanDuration();
                String kickMessage = prefix + replacePlaceholders(
                        cfg.getChatMessage("ban-kick", "You have been banned: %message%"),
                        user, message, word
                );

                Bukkit.getScheduler().runTask(plugin, () -> {
                    BanList<PlayerProfile> banList = Bukkit.getBanList(BanList.Type.PROFILE);
                    Date expiry = banDuration > 0 ? new Date(System.currentTimeMillis() + (banDuration * 1000L)) : null;
                    banList.addBan(player.getPlayerProfile(), reason, expiry, plugin.getName());
                    player.kickPlayer(kickMessage);
                });
                break;
        }

        if (notifyStaff) {
            String notifyMsg = String.format("[ChatFilter] %s used forbidden word '%s' (punishment=%s)", user, word, punishment);
            Bukkit.getScheduler().runTask(plugin, () -> notifyStaff(notifyMsg));
        }

        if (!webhookUrl.isEmpty()) {
            String webhookMsg = cfg.getChatWebhookMessage();
            webhookMsg = webhookMsg.replace("%punishment%", punishment);
            sendWebhook(replacePlaceholders(webhookMsg, user, message, word));
        }
    }

    private String replacePlaceholders(String template, String user, String message, String word) {
        if (template == null) return "";
        return ChatColor.translateAlternateColorCodes('&', template
                .replace("%user%", user)
                .replace("%message%", message)
                .replace("%word%", word));
    }

    private void notifyStaff(String text) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission(staffPermission)) {
                p.sendMessage(prefix + text);
            }
        }
        plugin.getLogger().info(text);
    }

    private void sendWebhook(String content) {
        if (webhookUrl == null || webhookUrl.isEmpty()) return;
        new Thread(() -> {
            try {
                URI uri = URI.create(webhookUrl);
                HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setDoOutput(true);
                String json = "{\"content\":\"" + escapeJson(content) + "\"}";
                byte[] out = json.getBytes(StandardCharsets.UTF_8);
                conn.setFixedLengthStreamingMode(out.length);
                conn.connect();
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(out);
                }
                int code = conn.getResponseCode();
                if (code < 200 || code >= 300) {
                    plugin.getLogger().warning("Webhook returned HTTP " + code);
                }
                conn.disconnect();
            } catch (Exception ex) {
                plugin.getLogger().warning("Failed to send webhook: " + ex.getMessage());
            }
        }, "ChatFilter-Webhook").start();
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}