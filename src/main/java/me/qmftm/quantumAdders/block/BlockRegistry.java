package me.qmftm.quantumAdders.block;

import me.qmftm.quantumAdders.item.BedrockOptions;
import me.qmftm.quantumAdders.item.CustomItem;
import me.qmftm.quantumAdders.item.ItemRegistry;
import me.qmftm.quantumAdders.lang.Messages;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Loads custom blocks from plugins/QuantumAdders/blocks/.
 *
 * <p>Each block also gets an item, so it can be held and placed. That item is a note
 * block carrying the block's model, and it is registered with {@link ItemRegistry} so the
 * existing pipeline — pack generation, Geyser registration, {@code /qa give} — covers it
 * without a parallel implementation.
 */
public final class BlockRegistry {

    private static final Pattern VALID_ID = Pattern.compile("[a-z0-9_]+");

    private final Plugin plugin;
    private final Messages messages;
    private final ItemRegistry items;
    private final BlockStateAllocator allocator;

    private final Map<String, CustomBlock> blocks = new LinkedHashMap<>();
    /** Note block state -> block id, for identifying a block in the world. */
    private final Map<String, String> statesToId = new LinkedHashMap<>();
    private final Map<String, String> idToState = new LinkedHashMap<>();

    public BlockRegistry(Plugin plugin, Messages messages, ItemRegistry items,
                         BlockStateAllocator allocator) {
        this.plugin = plugin;
        this.messages = messages;
        this.items = items;
        this.allocator = allocator;
    }

    public String namespace() {
        return items.namespace();
    }

    public Collection<CustomBlock> all() {
        return blocks.values();
    }

    public CustomBlock get(String id) {
        return blocks.get(id);
    }

    public boolean isEmpty() {
        return blocks.isEmpty();
    }

    public int size() {
        return blocks.size();
    }

    /** The note block state string this block occupies, e.g. {@code instrument=bass,note=3,powered=false}. */
    public String stateOf(String blockId) {
        return idToState.get(blockId);
    }

    /** The custom block standing at this state, or {@code null} if it is a plain note block. */
    public CustomBlock atState(String state) {
        String id = statesToId.get(state);
        return id == null ? null : blocks.get(id);
    }

    public BlockStateAllocator allocator() {
        return allocator;
    }

    /** Must run after {@link ItemRegistry#reload()}, since it adds items to it. */
    public void reload() {
        blocks.clear();
        statesToId.clear();
        idToState.clear();
        allocator.load();

        File dir = new File(plugin.getDataFolder(), "blocks");
        if (!dir.isDirectory()) {
            if (!dir.mkdirs()) {
                plugin.getLogger().severe(messages.plain("item.directory-failed",
                        Messages.of("path", dir.getPath())));
                return;
            }
            writeExample(new File(dir, "example.yml"));
        }

        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".yml"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            loadFile(file);
        }

        allocator.save();
        plugin.getLogger().info(messages.plain("block.blocks-loaded",
                Messages.of("count", blocks.size()),
                Messages.of("used", allocator.used()),
                Messages.of("capacity", allocator.capacity())));
    }

    private void writeExample(File target) {
        try (InputStream in = plugin.getResource("defaults/example-blocks.yml")) {
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
        ConfigurationSection root = yaml.getConfigurationSection("blocks");
        if (root == null) {
            plugin.getLogger().warning(messages.plain("block.no-blocks-section",
                    Messages.of("file", file.getName())));
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            CustomBlock block = parse(file.getName(), id, section);
            if (block != null) {
                add(file.getName(), block);
            }
        }
    }

    private void add(String fileName, CustomBlock block) {
        if (blocks.containsKey(block.id()) || items.get(block.id()) != null) {
            plugin.getLogger().warning(messages.plain("block.duplicate-id",
                    Messages.of("item", block.id()), Messages.of("file", fileName)));
            return;
        }
        String state = allocator.stateFor(block.id());
        if (state == null) {
            return; // Pool exhausted; the allocator has already complained.
        }

        blocks.put(block.id(), block);
        statesToId.put(state, block.id());
        idToState.put(block.id(), state);
        items.register(blockItem(block));
    }

    /**
     * The item that places this block: a note block whose model is the block's own, so it
     * looks right in the hand and in the inventory.
     */
    private CustomItem blockItem(CustomBlock block) {
        return new CustomItem(
                block.namespace(),
                block.id(),
                Material.NOTE_BLOCK,
                block.displayName(),
                block.lore(),
                block.textures().particle(),
                block.namespace() + ":block/" + block.id(),
                null,
                new BedrockOptions(block.creativeCategory(), block.creativeGroup(), false, true, 0)
        );
    }

    private CustomBlock parse(String fileName, String id, ConfigurationSection section) {
        String where = fileName + " -> blocks." + id;
        if (!VALID_ID.matcher(id).matches()) {
            plugin.getLogger().warning(messages.plain("item.invalid-id", Messages.of("where", where)));
            return null;
        }

        List<String> problems = new ArrayList<>();
        List<BlockDrop> drops = BlockDrop.listFrom(section, where, problems);
        for (String problem : problems) {
            plugin.getLogger().warning(problem);
        }

        int[] xp = parseXp(section.get("xp"), where);
        ConfigurationSection bedrock = section.getConfigurationSection("bedrock");

        return new CustomBlock(
                items.namespace(),
                id,
                section.getString("display-name"),
                List.copyOf(section.getStringList("lore")),
                BlockTextures.from(section, id),
                (float) section.getDouble("hardness", 3.0D),
                section.getInt("light-emission", 0),
                drops,
                section.getBoolean("silk-touch-self", true),
                xp[0],
                xp[1],
                bedrock == null ? "CONSTRUCTION" : bedrock.getString("creative-category", "CONSTRUCTION"),
                bedrock == null ? "" : bedrock.getString("creative-group", "")
        );
    }

    /** Accepts {@code xp: 3} or {@code xp: "2-5"}. */
    private int[] parseXp(Object value, String where) {
        if (value == null) {
            return new int[]{0, 0};
        }
        if (value instanceof Number number) {
            int amount = Math.max(0, number.intValue());
            return new int[]{amount, amount};
        }
        String text = value.toString().trim();
        int dash = text.indexOf('-');
        try {
            if (dash > 0) {
                int min = Integer.parseInt(text.substring(0, dash).trim());
                int max = Integer.parseInt(text.substring(dash + 1).trim());
                return new int[]{Math.max(0, Math.min(min, max)), Math.max(0, Math.max(min, max))};
            }
            int amount = Math.max(0, Integer.parseInt(text));
            return new int[]{amount, amount};
        } catch (NumberFormatException e) {
            plugin.getLogger().warning(messages.plain("block.invalid-xp",
                    Messages.of("where", where), Messages.of("value", text)));
            return new int[]{0, 0};
        }
    }
}
