package me.optimusprimerdc.primeAssistant.updatechecker;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.optimusprimerdc.primeAssistant.PrimeAssistant;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.Arrays;

public class UpdateChecker implements Listener {

    private static final String GITHUB_API = "https://api.github.com/repos/ConsoleGods/PrimeAssistant/releases/latest";
    private static final String RELEASES_URL = "https://github.com/ConsoleGods/PrimeAssistant/releases";

    private final PrimeAssistant plugin;
    private final String pluginVersion;
    private String latestVersion;
    private boolean updateAvailable;

    public UpdateChecker(PrimeAssistant plugin) {
        this.plugin = plugin;
        this.pluginVersion = plugin.getDescription().getVersion();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public boolean hasUpdateAvailable() {
        return updateAvailable;
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public void fetch() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpsURLConnection con = (HttpsURLConnection) URI.create(GITHUB_API).toURL().openConnection();
                con.setRequestMethod("GET");
                con.setRequestProperty("Accept", "application/vnd.github.v3+json");
                con.setConnectTimeout(5000);
                con.setReadTimeout(5000);

                BufferedReader reader = new BufferedReader(new InputStreamReader(con.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                JsonObject json = JsonParser.parseString(response.toString()).getAsJsonObject();
                latestVersion = json.get("tag_name").getAsString().replace("v", "");

            } catch (Exception ex) {
                plugin.getLogger().info("Failed to check for updates on GitHub.");
                return;
            }

            if (latestVersion == null || latestVersion.isEmpty()) {
                return;
            }

            updateAvailable = isNewer();

            if (!updateAvailable) {
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getLogger().info("An update for PrimeAssistant (v" + getLatestVersion() + ") is available at:");
                plugin.getLogger().info(RELEASES_URL);
            });
        });
    }

    private boolean isNewer() {
        if (latestVersion == null || latestVersion.isEmpty()) {
            return false;
        }

        int[] current = toReadable(pluginVersion);
        int[] latest = toReadable(latestVersion);

        if (current[0] < latest[0]) {
            return true;
        } else if (current[1] < latest[1]) {
            return true;
        } else {
            return current[2] < latest[2];
        }
    }

    private int[] toReadable(String version) {
        if (version.contains("-DEV") || version.contains("-SNAPSHOT")) {
            version = version.split("-")[0];
        }

        return Arrays.stream(version.split("\\.")).mapToInt(Integer::parseInt).toArray();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        if (!updateAvailable) {
            return;
        }

        Player player = e.getPlayer();
        if (player.hasPermission("primeassistant.updatenotify")) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&bAn update for &fPrimeAssistant &e(&fv" + getLatestVersion() + "&e) is available at:"));
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e" + RELEASES_URL));
        }
    }
}
