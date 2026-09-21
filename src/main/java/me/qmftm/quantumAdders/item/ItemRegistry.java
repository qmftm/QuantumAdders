package me.qmftm.quantumAdders.item;

import me.qmftm.quantumAdders.lang.Messages;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Loads and holds every custom item declared under plugins/QuantumAdders/items/. */
public final class ItemRegistry {

    /**
     * Bedrock identifiers accept neither uppercase nor hyphens, so ids are held to the
     * stricter of the two platforms' rules rather than Java's.
     */
    private static final Pattern VALID_ID = Pattern.compile("[a-z0-9_]+");
    private static final Pattern VALID_NAMESPACE = Pattern.compile("[a-z0-9_]+");

    private final Plugin plugin;
    private final Messages messages;
    private final Map<String, CustomItem> items = new LinkedHashMap<>();
    /** Which file each id came from, so a clash can name both sides. */
    private final Map<String, String> sourceFiles = new LinkedHashMap<>();
    private String namespace = "quantumadders";

    public ItemRegistry(Plugin plugin, Messages messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    public String namespace() {
        return namespace;
    }

    public Collection<CustomItem> all() {
        return items.values();
    }

    public CustomItem get(String id) {
        return items.get(id);
    }

    /**
     * Adds an item that did not come from items/*.yml — currently the item that places a
     * custom block. Registered after {@link #reload()}, so a reload drops these and the
     * block registry puts them back.
     */
    public void register(CustomItem item) {
        items.put(item.id(), item);
        sourceFiles.put(item.id(), "blocks");
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public int size() {
        return items.size();
    }

    /** Clears and re-reads every definition. Already-issued ItemStacks are unaffected. */
    public void reload() {
        items.clear();
        sourceFiles.clear();

        String configured = plugin.getConfig().getString("namespace", "quantumadders");
        if (!VALID_NAMESPACE.matcher(configured).matches()) {
            plugin.getLogger().warning(messages.plain("item.invalid-namespace",
                    Messages.of("value", configured)));
            configured = "quantumadders";
        }
        namespace = configured;

        File dir = new File(plugin.getDataFolder(), "items");
        if (!dir.isDirectory()) {
            if (!dir.mkdirs()) {
                plugin.getLogger().severe(messages.plain("item.directory-failed",
                        Messages.of("path", dir.getPath())));
                return;
            }
            writeExample(new File(dir, "example.yml"));
        }
        new File(plugin.getDataFolder(), "textures").mkdirs();

        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".yml"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            loadFile(file);
        }
        plugin.getLogger().info(messages.plain("log.items-loaded", Messages.of("count", items.size())));
    }

    private void writeExample(File target) {
        try (InputStream in = plugin.getResource("defaults/example.yml")) {
            if (in != null) {
                Files.copy(in, target.toPath());
            }
        } catch (IOException e) {
            plugin.getLogger().warning(messages.plain("item.example-failed",
                    Messages.of("reason", String.valueOf(e.getMessage()))));
        }
    }

    private void loadFile(File file) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("items");
        if (root == null) {
            plugin.getLogger().warning(messages.plain("item.no-items-section",
                    Messages.of("file", file.getName())));
            return;
        }
        warnAboutRepeatedKeys(file);

        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            CustomItem item = parse(file.getName(), id, section);
            if (item == null) {
                continue;
            }
            CustomItem previous = items.put(id, item);
            String previousFile = sourceFiles.put(id, file.getName());
            if (previous != null) {
                // Files load in name order, so which definition wins is not obvious.
                plugin.getLogger().warning(messages.plain("item.duplicate-id",
                        Messages.of("item", id),
                        Messages.of("file", file.getName()),
                        Messages.of("other", String.valueOf(previousFile))));
            }
        }
    }

    /**
     * Reports ids written twice in one file.
     *
     * <p>YAML merges repeated keys silently — the parser has already thrown the earlier
     * definition away by the time it is read — so this re-reads the raw text and looks
     * for item ids at the same indentation appearing more than once.
     */
    private void warnAboutRepeatedKeys(File file) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return;
        }

        Set<String> seen = new HashSet<>();
        boolean insideItems = false;
        int idIndent = -1;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int indent = line.length() - line.stripLeading().length();

            if (!insideItems) {
                insideItems = trimmed.startsWith("items:");
                continue;
            }
            if (indent == 0) {
                break; // A top-level key ends the items section.
            }
            if (idIndent == -1) {
                idIndent = indent;
            }
            // Item ids are the only bare "name:" entries at this depth.
            if (indent == idIndent && trimmed.endsWith(":")) {
                String id = trimmed.substring(0, trimmed.length() - 1).trim();
                if (!seen.add(id)) {
                    plugin.getLogger().warning(messages.plain("item.repeated-key",
                            Messages.of("item", id), Messages.of("file", file.getName())));
                }
            }
        }
    }

    private CustomItem parse(String fileName, String id, ConfigurationSection section) {
        String where = fileName + " -> items." + id;

        if (!VALID_ID.matcher(id).matches()) {
            plugin.getLogger().warning(messages.plain("item.invalid-id", Messages.of("where", where)));
            return null;
        }

        String baseName = section.getString("base");
        if (baseName == null) {
            plugin.getLogger().warning(messages.plain("item.missing-base", Messages.of("where", where)));
            return null;
        }
        Material base = Material.matchMaterial(baseName);
        if (base == null || !base.isItem()) {
            plugin.getLogger().warning(messages.plain("item.invalid-base",
                    Messages.of("where", where), Messages.of("value", baseName)));
            return null;
        }

        Integer maxStack = section.contains("max-stack-size")
                ? section.getInt("max-stack-size")
                : null;
        if (maxStack != null && (maxStack < 1 || maxStack > 99)) {
            plugin.getLogger().warning(messages.plain("item.invalid-stack-size",
                    Messages.of("where", where)));
            maxStack = null;
        }

        List<String> lore = section.getStringList("lore");
        return new CustomItem(
                namespace,
                id,
                base,
                section.getString("display-name"),
                List.copyOf(lore),
                section.getString("texture"),
                section.getString("model-parent"),
                maxStack,
                BedrockOptions.from(section.getConfigurationSection("bedrock"))
        );
    }
}
