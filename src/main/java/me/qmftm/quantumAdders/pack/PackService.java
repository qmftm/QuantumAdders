package me.qmftm.quantumAdders.pack;

import me.qmftm.quantumAdders.block.BlockRegistry;
import me.qmftm.quantumAdders.item.ItemRegistry;
import me.qmftm.quantumAdders.lang.Messages;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Generates both resource packs from the current item definitions.
 *
 * <p>Textures are read from plugins/QuantumAdders/textures/ and the finished packs go
 * wherever {@code pack.output} points, defaulting to plugins/QuantumAdders/output/.
 * Nothing is served to players automatically — the Java pack still has to be hosted or
 * set as the server pack; only the Bedrock pack can be dropped into Geyser directly.
 */
public final class PackService {

    private static final String JAVA_PACK_NAME = "QuantumAdders-java.zip";
    private static final String BEDROCK_PACK_NAME = "QuantumAdders-bedrock.mcpack";
    private static final String MAPPINGS_NAME = "quantumadders.json";
    private static final String BLOCK_MAPPINGS_NAME = "quantumadders-blocks.json";

    private final Plugin plugin;
    private final ItemRegistry registry;
    private final BlockRegistry blocks;
    private final Supplier<Path> geyserPackDirectory;
    private final Messages messages;

    /**
     * @param geyserPackDirectory resolves Geyser's pack folder, or {@code null} when
     *                            Geyser is not installed
     */
    public PackService(Plugin plugin, ItemRegistry registry, BlockRegistry blocks,
                       Supplier<Path> geyserPackDirectory, Messages messages) {
        this.plugin = plugin;
        this.registry = registry;
        this.blocks = blocks;
        this.geyserPackDirectory = geyserPackDirectory;
        this.messages = messages;
    }

    public PackResult build() {
        List<String> missingTextures = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        Path data = plugin.getDataFolder().toPath();
        Path textures = data.resolve("textures");

        // Staging always lives under the plugin folder. Only the finished archives go to
        // the configured output, so pointing that at an existing directory — a web root,
        // say — never causes anything there to be emptied.
        Path staging = data.resolve("cache");

        int format = plugin.getConfig().getInt("pack.java.format", 88);
        String javaDescription = plugin.getConfig()
                .getString("pack.java.description", "QuantumAdders custom items");
        String bedrockDescription = plugin.getConfig()
                .getString("pack.bedrock.description", "QuantumAdders custom items");

        Path javaTarget = resolveTarget("pack.java.output", JAVA_PACK_NAME);
        Path bedrockTarget = resolveTarget("pack.bedrock.output", BEDROCK_PACK_NAME);

        Path javaPack = null;
        Path bedrockPack = null;
        Path installedTo = null;

        try {
            Files.createDirectories(textures);
            javaPack = new JavaPackBuilder(registry, blocks, textures, javaDescription, format)
                    .build(staging.resolve("java"), javaTarget, missingTextures);
        } catch (IOException e) {
            errors.add(messages.plain("log.pack-failed", Messages.of("what", "Java"),
                    Messages.of("reason", String.valueOf(e.getMessage()))));
        }

        try {
            bedrockPack = new BedrockPackBuilder(registry, blocks, textures, bedrockDescription)
                    .build(staging.resolve("bedrock"), bedrockTarget, missingTextures);
        } catch (IOException e) {
            errors.add(messages.plain("log.pack-failed", Messages.of("what", "Bedrock"),
                    Messages.of("reason", String.valueOf(e.getMessage()))));
        }

        if (bedrockPack != null && plugin.getConfig().getBoolean("pack.bedrock.auto-install", true)) {
            installedTo = install(bedrockPack, errors);
        }

        Path mappings = null;
        if (plugin.getConfig().getBoolean("geyser.write-mappings", true)) {
            try {
                mappings = new GeyserMappingsWriter(registry).write(mappingsTarget(MAPPINGS_NAME));
            } catch (IOException e) {
                errors.add(messages.plain("log.pack-failed", Messages.of("what", "Geyser mappings"),
                        Messages.of("reason", String.valueOf(e.getMessage()))));
            }
        }

        Path blockMappings = null;
        if (blocks != null && !blocks.isEmpty()
                && plugin.getConfig().getBoolean("geyser.write-mappings", true)) {
            try {
                blockMappings = new GeyserBlockMappingsWriter(blocks)
                        .write(mappingsTarget(BLOCK_MAPPINGS_NAME));
            } catch (IOException e) {
                errors.add(messages.plain("log.pack-failed", Messages.of("what", "Geyser block mappings"),
                        Messages.of("reason", String.valueOf(e.getMessage()))));
            }
        }

        if (!missingTextures.isEmpty()) {
            plugin.getLogger().warning(messages.plain("log.missing-textures",
                    Messages.of("items", String.join(", ", missingTextures)),
                    Messages.of("path", textures.toString())));
        }
        return new PackResult(registry.size(), List.copyOf(missingTextures), javaPack, bedrockPack,
                installedTo, mappings, blockMappings, List.copyOf(errors));
    }

    /**
     * Where a finished pack is written.
     *
     * <p>{@code pack.output} sets the folder for both packs; {@code pack.java.output} and
     * {@code pack.bedrock.output} override it for one of them. Relative paths resolve
     * against the plugin folder and absolute paths are taken as-is. A value ending in
     * {@code .zip} or {@code .mcpack} names the file itself; anything else is a folder
     * and the default file name is appended.
     */
    private Path resolveTarget(String overrideKey, String defaultName) {
        String configured = plugin.getConfig().getString(overrideKey, "");
        if (configured == null || configured.isBlank()) {
            configured = plugin.getConfig().getString("pack.output", "output");
        }
        if (configured == null || configured.isBlank()) {
            configured = "output";
        }

        Path fallback = plugin.getDataFolder().toPath().resolve("output").resolve(defaultName);
        try {
            Path path = Path.of(configured);
            if (!path.isAbsolute()) {
                // Relative paths may reach sideways, e.g. ../ResourcePackManager/mixer.
                path = plugin.getDataFolder().toPath().resolve(path).normalize();
            }
            String name = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase();
            boolean namesTheFile = name.endsWith(".zip") || name.endsWith(".mcpack") || name.endsWith(".json");
            return namesTheFile && !Files.isDirectory(path) ? path : path.resolve(defaultName);
        } catch (InvalidPathException e) {
            plugin.getLogger().warning(messages.plain("log.invalid-path",
                    Messages.of("value", configured), Messages.of("path", fallback.getParent().toString())));
            return fallback;
        }
    }

    /**
     * Where the Geyser mappings JSON goes. With {@code geyser.mappings-output} empty it
     * lands in a co-installed Geyser's {@code custom_mappings} folder when there is one,
     * and otherwise beside the packs, ready to be copied to an external Geyser.
     */
    private Path mappingsTarget(String fileName) {
        String configured = plugin.getConfig().getString("geyser.mappings-output", "");
        if (configured != null && !configured.isBlank()) {
            return resolveTarget("geyser.mappings-output", fileName);
        }
        Path local = plugin.getDataFolder().toPath().getParent()
                .resolve("Geyser-Spigot").resolve("custom_mappings");
        if (Files.isDirectory(local)) {
            return local.resolve(fileName);
        }
        return resolveTarget("pack.output", "custom_mappings").resolve(fileName);
    }

    private Path install(Path bedrockPack, List<String> errors) {
        if (geyserPackDirectory == null) {
            return null;
        }
        try {
            Path directory = geyserPackDirectory.get();
            if (directory == null || !Files.isDirectory(directory)) {
                errors.add(messages.plain("log.geyser-folder-missing"));
                return null;
            }
            Path destination = directory.resolve(BEDROCK_PACK_NAME);
            Files.copy(bedrockPack, destination, StandardCopyOption.REPLACE_EXISTING);
            return destination;
        } catch (Exception e) {
            errors.add(messages.plain("log.install-failed",
                    Messages.of("reason", String.valueOf(e.getMessage()))));
            return null;
        }
    }
}
