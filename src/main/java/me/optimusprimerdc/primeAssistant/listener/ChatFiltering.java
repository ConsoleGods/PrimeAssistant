package me.optimusprimerdc.primeAssistant.listener;

import me.optimusprimerdc.primeAssistant.PrimeAssistant;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.profile.PlayerProfile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatFiltering implements Listener {

    private final int expectedConfigVersion;
    private final PrimeAssistant plugin;
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
        this.expectedConfigVersion = loadBundledConfigVersion();
        checkAndUpdateConfigVersion();
        loadConfig();
    }

    public void reload() {
        loadConfig();
        plugin.getLogger().info("ChatFiltering reloaded");
    }

    private int loadBundledConfigVersion() {
        try (InputStream in = plugin.getResource("config.yml")) {
            if (in == null) return 0;
            YamlConfiguration def = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            return def.getInt("config-version", 0);
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to read bundled config-version: " + ex.getMessage());
            return 0;
        }
    }

    private void checkAndUpdateConfigVersion() {
        plugin.reloadConfig();
        int currentConfigVersion = plugin.getConfig().getInt("config-version", 0);

        if (currentConfigVersion != expectedConfigVersion) {
            plugin.getLogger().info("Config version mismatch (config-version=" + currentConfigVersion + "). Backing up and updating config.yml to bundled version " + expectedConfigVersion + ".");

            try {
                File configFile = new File(plugin.getDataFolder(), "config.yml");
                if (configFile.exists()) {
                    YamlConfiguration current = YamlConfiguration.loadConfiguration(configFile);
                    File backup = new File(plugin.getDataFolder(), "config.yml.bak." + Instant.now().toEpochMilli());
                    current.save(backup);
                    plugin.getLogger().info("Backed up config to " + backup.getName());
                }
                plugin.saveResource("config.yml", true);
                plugin.reloadConfig();
                plugin.getLogger().info("Replaced config.yml with bundled default.");
            } catch (IOException ex) {
                plugin.getLogger().severe("Failed to backup/update config: " + ex.getMessage());
            } catch (Exception ex) {
                plugin.getLogger().severe("Unexpected error while updating config: " + ex.getMessage());
            }
        }
    }

    private void loadConfig() {
        plugin.reloadConfig();

        prefix = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("prefix", "&8[&6PrimeAssistant&8] &r"));

        ConfigurationSection cf = plugin.getConfig().getConfigurationSection("chat-filter");
        if (cf == null) {
            plugin.getLogger().warning("chat-filter section missing from config.yml");
            return;
        }

        webhookUrl = cf.getString("webhook-url", "").trim();
        notifyStaff = cf.getBoolean("notify-staff", true);
        staffPermission = cf.getString("staff-permission", "primeassistant.chat.notify");
        defaultPunishment = cf.getString("default-punishment", "BLOCK").toUpperCase(Locale.ROOT);

        patterns.clear();
        List<String> basic = cf.getStringList("basic-words");
        for (String w : basic) {
            if (w == null || w.isBlank()) continue;
            String key = w.toLowerCase(Locale.ROOT);
            Pattern p = Pattern.compile("\\b" + Pattern.quote(key) + "\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);
            patterns.put(key, p);
        }

        List<String> advanced = cf.getStringList("advanced-words");
        for (String w : advanced) {
            if (w == null || w.isBlank()) continue;
            String key = w.toLowerCase(Locale.ROOT);
            Pattern p = Pattern.compile(convertToFlexibleRegex(key), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);
            patterns.put(key, p);
        }

        punishments.clear();
        ConfigurationSection ps = cf.getConfigurationSection("punishments");
        if (ps != null) {
            for (String word : ps.getKeys(false)) {
                String value = ps.getString(word, defaultPunishment).toUpperCase(Locale.ROOT);
                punishments.put(word.toLowerCase(Locale.ROOT), value);
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

        // Check for bypass permission or OP status
        String bypassPermission = plugin.getConfig().getString("chat-filter.bypass-permission", "primeassistant.chat.bypass");
        if (player.isOp() || player.hasPermission(bypassPermission)) {
            return; // Allow message without filtering
        }

        if (isMuted(player.getUniqueId())) {
            event.setCancelled(true);
            String tpl = plugin.getConfig().getString("chat-filter.messages.muted", "You are muted and cannot send messages.");
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
        String baseMessage = plugin.getConfig().getString("chat-filter.messages." + punishment.toLowerCase(Locale.ROOT), null);

        event.setCancelled(true);

        switch (punishment) {
            case "BLOCK":
                if (baseMessage != null) {
                    player.sendMessage(prefix + replacePlaceholders(baseMessage, user, message, word));
                }
                break;

            case "MUTE":
                int muteDuration = plugin.getConfig().getInt("chat-filter.mute-duration", 0);
                mutePlayer(player.getUniqueId(), muteDuration);
                if (baseMessage != null) {
                    player.sendMessage(prefix + replacePlaceholders(baseMessage, user, message, word));
                }
                break;

            case "BAN":
                String reason = plugin.getConfig().getString("chat-filter.ban-reason", "Forbidden language");
                int banDuration = plugin.getConfig().getInt("chat-filter.ban-duration", 0);
                String kickMessage = prefix + replacePlaceholders(
                        plugin.getConfig().getString("chat-filter.messages.ban-kick", "You have been banned: %message%"),
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
            String webhookMsg = plugin.getConfig().getString("chat-filter.webhook-message", "%user% used word '%word%' in message: %message% (punishment=%punishment%)");
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
