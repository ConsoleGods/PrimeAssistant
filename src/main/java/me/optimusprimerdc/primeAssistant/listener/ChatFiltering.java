package me.optimusprimerdc.primeAssistant.listener;

import me.optimusprimerdc.primeAssistant.PrimeAssistant;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatFiltering implements Listener {

    private final PrimeAssistant plugin;
    private final Map<String, Pattern> patterns = new LinkedHashMap<>();
    private final Map<String, String> punishments = new HashMap<>();
    private final Set<UUID> mutedPlayers = new HashSet<>();
    private String webhookUrl;
    private boolean notifyStaff;
    private String staffPermission;
    private String defaultPunishment;
    private final int expectedConfigVersion = 1;

    public ChatFiltering(PrimeAssistant plugin) {
        this.plugin = plugin;
        checkAndUpdateConfigVersion();
        loadConfig();
    }

    private void checkAndUpdateConfigVersion() {
        plugin.reloadConfig();
        int currentConfigVersion = plugin.getConfig().getInt("config-version", 0);

        if (currentConfigVersion != expectedConfigVersion) {
            plugin.getLogger().info("Config version mismatch (config-version=" + currentConfigVersion + "). Backing up and updating config.yml.");

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
        // Map common letter substitutions
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
        sb.append("(?i)"); // Case insensitive flag

        for (int i = 0; i < word.length(); i++) {
            char c = Character.toLowerCase(word.charAt(i));

            // Use substitution pattern if available, otherwise just the letter
            if (substitutions.containsKey(c)) {
                sb.append(substitutions.get(c));
            } else {
                sb.append(Pattern.quote(String.valueOf(c)));
            }

            // Allow optional separators between characters (spaces, dots, dashes, underscores, asterisks, etc.)
            // but limit to prevent over-matching
            if (i < word.length() - 1) {
                sb.append("[\\s._*-]*");
            }
        }

        return sb.toString();
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        if (mutedPlayers.contains(player.getUniqueId())) {
            event.setCancelled(true);
            String tpl = plugin.getConfig().getString("chat-filter.messages.muted", "You are muted and cannot send messages.");
            player.sendMessage(replacePlaceholders(tpl, player.getName(), event.getMessage(), ""));
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

        // Always cancel the message first (this is safe in async)
        event.setCancelled(true);

        switch (punishment) {
            case "BLOCK":
                if (baseMessage != null) {
                    player.sendMessage(replacePlaceholders(baseMessage, user, message, word));
                }
                break;

            case "MUTE":
                mutedPlayers.add(player.getUniqueId());
                if (baseMessage != null) {
                    player.sendMessage(replacePlaceholders(baseMessage, user, message, word));
                }
                break;

            case "BAN":
                // Schedule ban and kick on main thread
                String reason = plugin.getConfig().getString("chat-filter.ban-reason", "Forbidden language");
                String kickMessage = replacePlaceholders(
                        plugin.getConfig().getString("chat-filter.messages.ban-kick", "You have been banned: %message%"),
                        user, message, word
                );

                Bukkit.getScheduler().runTask(plugin, () -> {
                    Bukkit.getBanList(BanList.Type.NAME).addBan(player.getName(), reason, null, plugin.getName());
                    player.kickPlayer(kickMessage);
                });
                break;

            default:
                if (baseMessage != null) {
                    player.sendMessage(replacePlaceholders(baseMessage, user, message, word));
                }
        }

        if (notifyStaff) {
            String notifyMsg = String.format("[ChatFilter] %s used forbidden word '%s' (punishment=%s)", user, word, punishment);
            // Schedule staff notification on main thread
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
        return template
                .replace("%user%", user)
                .replace("%message%", message)
                .replace("%word%", word);
    }

    private void notifyStaff(String text) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission(staffPermission)) {
                p.sendMessage(text);
            }
        }
        plugin.getLogger().info(text);
    }

    private void sendWebhook(String content) {
        new Thread(() -> {
            try {
                URL url = new URL(webhookUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
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