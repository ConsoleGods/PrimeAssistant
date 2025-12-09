// java
package me.optimusprimerdc.primeAssistant.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class ConfigUpdater {

    private ConfigUpdater() {}

    /**
     * Merge missing top-level sections from the bundled resource file into the plugin's config file.
     * - Inserts full blocks (with preceding comments) for top-level keys that are missing entirely.
     * - Merges nested missing keys into existing sections (without their comments).
     * - Ensures `config-version` is present and placed last.
     * - Writes the resulting textual config back to disk so comment blocks are preserved for added sections,
     *   then calls plugin.reloadConfig() so the live FileConfiguration reflects the changes.
     */
    public static void updateConfig(Plugin plugin, String fileName) {
        // Ensure default resource exists in data folder (won't overwrite)
        plugin.saveResource(fileName, false);

        File dataFile = new File(plugin.getDataFolder(), fileName);
        if (!dataFile.exists()) {
            // nothing to do; saveResource should have created it, but guard anyway
            plugin.saveResource(fileName, false);
            return;
        }

        String defaultText = readResourceText(plugin, fileName);
        if (defaultText == null) {
            plugin.getLogger().severe("Default resource " + fileName + " not found in jar.");
            return;
        }

        String existingText = readFileText(dataFile);
        if (existingText == null) {
            plugin.getLogger().severe("Failed to read existing " + fileName + " in data folder.");
            return;
        }

        // Load YAML objects for value-level merges
        FileConfiguration existingCfg = plugin.getConfig();
        YamlConfiguration defaults;
        try (InputStream defStream = plugin.getResource(fileName)) {
            if (defStream == null) {
                plugin.getLogger().severe("Failed to open bundled " + fileName + " resource.");
                return;
            }
            defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(defStream, StandardCharsets.UTF_8));
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to load default config: " + e.getMessage());
            return;
        }

        boolean textChanged = false;
        boolean valuesChanged = false;

        // 1) For each top-level key in defaults, if missing in existingCfg, extract its textual block (with comments)
        //    from defaultText and insert before config-version (or at end).
        Set<String> topKeys = defaults.getKeys(false);
        List<String> missingTopLevelBlocks = new ArrayList<>();
        for (String key : topKeys) {
            if (!existingCfg.contains(key)) {
                String block = extractTopLevelBlock(defaultText, key);
                if (block != null && !block.trim().isEmpty()) {
                    missingTopLevelBlocks.add(block);
                    // also set values into the live config so nested merges are available immediately
                    existingCfg.set(key, defaults.get(key));
                    valuesChanged = true;
                }
            } else {
                // section exists at top level -> merge nested missing values (value-level merge)
                if (defaults.isConfigurationSection(key) && existingCfg.getConfigurationSection(key) != null) {
                    boolean merged = mergeDefaults(existingCfg.getConfigurationSection(key), defaults.getConfigurationSection(key));
                    if (merged) valuesChanged = true;
                }
            }
        }

        // 2) Insert missing textual blocks before config-version (preserving comments)
        if (!missingTopLevelBlocks.isEmpty()) {
            List<String> lines = splitLines(existingText);
            int insertIndex = findTopLevelKeyLineIndex(lines, "config-version");
            // if not found, insert at end (after a blank line)
            if (insertIndex == -1) insertIndex = lines.size();
            // ensure there's a blank line before inserts for readability
            if (insertIndex > 0 && !lines.get(insertIndex - 1).trim().isEmpty()) {
                missingTopLevelBlocks.add(0, "");
            }
            // perform insertion
            List<String> toInsert = new ArrayList<>();
            for (String block : missingTopLevelBlocks) {
                List<String> bLines = splitLines(block);
                toInsert.addAll(bLines);
                // ensure single blank line between inserted sections
                toInsert.add("");
            }
            lines.addAll(insertIndex, toInsert);
            existingText = joinLines(lines);
            textChanged = true;
            plugin.getLogger().info("Inserted " + missingTopLevelBlocks.size() + " missing top-level section(s) (with comments) from bundled config.");
        }

        // 3) Ensure config-version is present (prefer existing value if present) and is last top-level key.
        Object cfgVersionValue = existingCfg.contains("config-version") ? existingCfg.get("config-version") : defaults.get("config-version");
        if (cfgVersionValue != null) {
            // remove any existing textual occurrences of the config-version top-level entry
            List<String> lines = splitLines(existingText);
            int idx = findTopLevelKeyLineIndex(lines, "config-version");
            if (idx != -1) {
                // remove the config-version line (do not try to remove comments above it)
                lines.remove(idx);
                // remove trailing blank lines immediately after if present
                while (idx < lines.size() && lines.get(idx).trim().isEmpty()) {
                    lines.remove(idx);
                }
                existingText = joinLines(lines);
                textChanged = true;
            }
            // append config-version at the end, ensuring one blank line before it
            if (!existingText.endsWith("\n")) existingText = existingText + "\n";
            if (!existingText.endsWith("\n\n")) existingText = existingText + "\n";
            existingText = existingText + "config-version: " + String.valueOf(cfgVersionValue) + "\n";
            // Also ensure runtime config has the value
            existingCfg.set("config-version", cfgVersionValue);
            valuesChanged = true;
        }

        // 4) If we changed text on disk, write file (preserve comments for inserted blocks).
        if (textChanged) {
            boolean ok = writeFileText(dataFile, existingText);
            if (!ok) {
                plugin.getLogger().severe("Failed to write updated config file " + fileName);
            } else {
                plugin.getLogger().info("Wrote merged config file " + fileName + " (comments preserved for inserted sections).");
            }
        }

        // 5) If values changed (via set() calls), save and reload so plugin.getConfig() reflects them.
        if (valuesChanged) {
            // If we wrote textual file, reload from disk; otherwise saveConfig is fine.
            try {
                plugin.saveConfig();   // persist in-memory changes (safe)
            } catch (Exception ignored) {}
            plugin.reloadConfig();    // reload from disk so comments-preserved file is parsed
            plugin.getLogger().info("Reloaded plugin config after merging defaults.");
        }
    }

    // Read bundled resource text
    private static String readResourceText(Plugin plugin, String fileName) {
        try (InputStream is = plugin.getResource(fileName)) {
            if (is == null) return null;
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            return sb.toString();
        } catch (IOException e) {
            return null;
        }
    }

    // Read existing file text
    private static String readFileText(File f) {
        try {
            return Files.readString(f.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    // Write text back to file
    private static boolean writeFileText(File f, String text) {
        try (BufferedWriter w = Files.newBufferedWriter(f.toPath(), StandardCharsets.UTF_8)) {
            w.write(text);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    // Split into lines preserving empty lines
    private static List<String> splitLines(String s) {
        String[] arr = s.split("\\r?\\n", -1);
        List<String> out = new ArrayList<>(arr.length);
        for (String line : arr) out.add(line);
        return out;
    }

    private static String joinLines(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            sb.append(lines.get(i));
            if (i < lines.size() - 1) sb.append('\n');
        }
        // ensure trailing newline
        if (sb.length() == 0 || sb.charAt(sb.length() - 1) != '\n') sb.append('\n');
        return sb.toString();
    }

    // Find line index of a top-level key (line starting with non-whitespace and key:)
    private static int findTopLevelKeyLineIndex(List<String> lines, String key) {
        String prefix = key + ":";
        for (int i = 0; i < lines.size(); i++) {
            String trimmed = lines.get(i).trim();
            // only match top-level keys (line must not start with whitespace)
            if (!lines.get(i).startsWith(" ") && !lines.get(i).startsWith("\t") && trimmed.startsWith(prefix)) {
                return i;
            }
        }
        return -1;
    }

    // Extract a top-level block from the default text, including contiguous preceding comments and all indented child lines.
    private static String extractTopLevelBlock(String defaultText, String key) {
        List<String> lines = splitLines(defaultText);
        int start = -1;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (!line.startsWith(" ") && !line.startsWith("\t")) {
                String trimmed = line.trim();
                if (trimmed.startsWith(key + ":")) {
                    start = i;
                    break;
                }
            }
        }
        if (start == -1) return null;

        // Include contiguous comment lines immediately above the start
        int realStart = start;
        while (realStart - 1 >= 0) {
            String above = lines.get(realStart - 1);
            String t = above.trim();
            if (t.isEmpty() || t.startsWith("#")) {
                realStart--;
            } else {
                break;
            }
        }

        // Find end: include following lines that are indented (start with space or tab)
        int end = start + 1;
        while (end < lines.size()) {
            String l = lines.get(end);
            if (l.startsWith(" ") || l.startsWith("\t")) {
                end++;
            } else {
                break;
            }
        }

        // include one trailing blank line for readability
        if (end < lines.size() && !lines.get(end).trim().isEmpty()) {
            // leave as-is
        } else if (end < lines.size()) {
            end++; // include one blank line if present
        }

        List<String> blockLines = new ArrayList<>();
        for (int i = realStart; i < end; i++) blockLines.add(lines.get(i));
        return joinLines(blockLines);
    }

    // Recursively merge missing defaults into an existing ConfigurationSection (value-only; comments not preserved)
    private static boolean mergeDefaults(ConfigurationSection existing, ConfigurationSection defaults) {
        boolean changed = false;
        for (String key : defaults.getKeys(false)) {
            if (defaults.isConfigurationSection(key)) {
                ConfigurationSection defSec = defaults.getConfigurationSection(key);
                ConfigurationSection existSec = existing.getConfigurationSection(key);
                if (existSec == null) {
                    existing.createSection(key, defSec.getValues(true));
                    changed = true;
                } else {
                    if (mergeDefaults(existSec, defSec)) changed = true;
                }
            } else {
                if (!existing.contains(key)) {
                    existing.set(key, defaults.get(key));
                    changed = true;
                }
            }
        }
        return changed;
    }
}
