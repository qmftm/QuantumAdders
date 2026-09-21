package me.qmftm.quantumAdders.block;

import me.qmftm.quantumAdders.lang.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Instrument;
import org.bukkit.Material;
import org.bukkit.Note;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.NoteBlock;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hands each custom block one note block state, and remembers the choice forever.
 *
 * <p>Persistence is the whole point. The state is what identifies a placed block in the
 * world, so if ids were renumbered whenever a block was added or removed, every block
 * already built with would silently turn into a different one. Assignments therefore live
 * in block-states.yml and are only ever added to.
 *
 * <p>States are enumerated through the API rather than hardcoded, so a Minecraft version
 * that adds an instrument simply widens the pool.
 */
public final class BlockStateAllocator {

    private static final String FILE_NAME = "block-states.yml";

    private final Plugin plugin;
    private final Messages messages;

    /** Every usable state, in a fixed order. Index 0 is left to vanilla note blocks. */
    private final List<String> pool = new ArrayList<>();
    private final Map<String, String> byBlockId = new LinkedHashMap<>();
    private final Map<String, String> byState = new HashMap<>();

    public BlockStateAllocator(Plugin plugin, Messages messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    /**
     * Builds the state pool and reads previous assignments.
     *
     * <p>Only {@code powered=false} states are used. A redstone signal flips that
     * property, and a block whose identity changed under a redstone pulse would be worse
     * than one that simply ignores redstone.
     */
    public void load() {
        pool.clear();
        byBlockId.clear();
        byState.clear();

        BlockData data = Bukkit.createBlockData(Material.NOTE_BLOCK);
        if (!(data instanceof NoteBlock noteBlock)) {
            plugin.getLogger().severe("NOTE_BLOCK did not produce NoteBlock data; custom blocks are off.");
            return;
        }
        for (Instrument instrument : Instrument.values()) {
            for (int note = 0; note <= 24; note++) {
                try {
                    noteBlock.setInstrument(instrument);
                    noteBlock.setNote(new Note(note));
                    noteBlock.setPowered(false);
                    pool.add(propertiesOf(noteBlock.getAsString()));
                } catch (IllegalArgumentException e) {
                    // An instrument the server does not accept as a block state.
                    break;
                }
            }
        }

        File file = new File(plugin.getDataFolder(), FILE_NAME);
        if (file.isFile()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            var section = yaml.getConfigurationSection("assigned");
            if (section != null) {
                for (String blockId : section.getKeys(false)) {
                    String state = section.getString(blockId);
                    if (state != null && !state.isBlank()) {
                        byBlockId.put(blockId, state);
                        byState.put(state, blockId);
                    }
                }
            }
        }
    }

    /** Total states available to custom blocks, excluding the one vanilla keeps. */
    public int capacity() {
        return Math.max(0, pool.size() - 1);
    }

    public int used() {
        return byBlockId.size();
    }

    /**
     * The state for this block, assigning a free one on first sight.
     *
     * @return the state's property string, or {@code null} when the pool is exhausted
     */
    public String stateFor(String blockId) {
        String existing = byBlockId.get(blockId);
        if (existing != null) {
            return existing;
        }
        // Index 0 stays with vanilla so ordinary note blocks still look like note blocks.
        for (int i = 1; i < pool.size(); i++) {
            String candidate = pool.get(i);
            if (!byState.containsKey(candidate)) {
                byBlockId.put(blockId, candidate);
                byState.put(candidate, blockId);
                return candidate;
            }
        }
        plugin.getLogger().severe(messages.plain("block.pool-exhausted",
                Messages.of("item", blockId), Messages.of("count", capacity())));
        return null;
    }

    /** Which custom block this state belongs to, or {@code null} for a vanilla note block. */
    public String blockIdAt(String state) {
        return byState.get(state);
    }

    /** Every state in the pool, for generating the resource pack's full variant list. */
    public List<String> pool() {
        return List.copyOf(pool);
    }

    /**
     * Every note block state, powered ones included.
     *
     * <p>The resource pack's blockstates file has to name all of them: a state with no
     * variant renders as the missing-model cube. Only the unpowered ones are handed out
     * to custom blocks, so the rest simply map back to the vanilla model.
     */
    public List<String> allStates() {
        List<String> states = new ArrayList<>();
        BlockData data = Bukkit.createBlockData(Material.NOTE_BLOCK);
        if (!(data instanceof NoteBlock noteBlock)) {
            return states;
        }
        for (Instrument instrument : Instrument.values()) {
            for (int note = 0; note <= 24; note++) {
                for (boolean powered : new boolean[]{false, true}) {
                    try {
                        noteBlock.setInstrument(instrument);
                        noteBlock.setNote(new Note(note));
                        noteBlock.setPowered(powered);
                        states.add(propertiesOf(noteBlock.getAsString()));
                    } catch (IllegalArgumentException e) {
                        break;
                    }
                }
            }
        }
        return states;
    }

    /** The state vanilla note blocks keep. */
    public String vanillaState() {
        return pool.isEmpty() ? null : pool.get(0);
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of(
                "Which note block state each custom block uses.",
                "Generated automatically. Entries are never reused, so a block that is",
                "deleted keeps its state reserved and blocks already placed in the world",
                "are not reinterpreted. Editing this by hand will change what existing",
                "blocks look like."));
        for (Map.Entry<String, String> entry : byBlockId.entrySet()) {
            yaml.set("assigned." + entry.getKey(), entry.getValue());
        }
        try {
            yaml.save(new File(plugin.getDataFolder(), FILE_NAME));
        } catch (IOException e) {
            plugin.getLogger().warning(messages.plain("block.states-save-failed",
                    Messages.of("reason", String.valueOf(e.getMessage()))));
        }
    }

    /** Turns {@code minecraft:note_block[instrument=harp,note=1,...]} into the part inside the brackets. */
    public static String propertiesOf(String blockDataString) {
        int open = blockDataString.indexOf('[');
        int close = blockDataString.lastIndexOf(']');
        if (open < 0 || close < open) {
            return blockDataString;
        }
        return blockDataString.substring(open + 1, close);
    }
}
