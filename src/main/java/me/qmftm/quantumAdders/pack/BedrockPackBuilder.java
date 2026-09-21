package me.qmftm.quantumAdders.pack;

import me.qmftm.quantumAdders.block.BlockRegistry;
import me.qmftm.quantumAdders.block.CustomBlock;
import me.qmftm.quantumAdders.item.CustomItem;
import me.qmftm.quantumAdders.item.ItemRegistry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;

/**
 * Builds the Bedrock resource pack (.mcpack) that gives Geyser's registered items their
 * textures. Without it Bedrock clients see the items but render them as the base item.
 *
 * <p>Texture keys here must match {@link CustomItem#bedrockIcon()}, which is what the
 * Geyser definition passes as its icon.
 */
final class BedrockPackBuilder {

    private final ItemRegistry registry;
    private final BlockRegistry blocks;
    private final Path texturesDirectory;
    private final String description;

    BedrockPackBuilder(ItemRegistry registry, BlockRegistry blocks, Path texturesDirectory,
                       String description) {
        this.registry = registry;
        this.blocks = blocks;
        this.texturesDirectory = texturesDirectory;
        this.description = description;
    }

    Path build(Path workDirectory, Path target, List<String> missingTextures) throws IOException {
        Packs.clean(workDirectory);

        writeManifest(workDirectory);
        writeItemTextures(workDirectory);
        copyTextures(workDirectory, missingTextures);

        if (blocks != null && !blocks.isEmpty()) {
            writeTerrainTextures(workDirectory);
            copyBlockTextures(workDirectory, missingTextures);
        }

        Packs.zip(workDirectory, target);
        return target;
    }

    /**
     * UUIDs are derived from the namespace rather than random, so rebuilding produces the
     * same pack identity and Bedrock clients treat it as an update instead of a new pack.
     */
    private void writeManifest(Path workDirectory) throws IOException {
        String namespace = registry.namespace();
        UUID header = deterministicUuid("quantumadders:header:" + namespace);
        UUID module = deterministicUuid("quantumadders:module:" + namespace);

        Packs.writeString(workDirectory.resolve("manifest.json"), """
                {
                  "format_version": 2,
                  "header": {
                    "name": "%s",
                    "description": "%s",
                    "uuid": "%s",
                    "version": [1, 0, 0],
                    "min_engine_version": [1, 21, 0]
                  },
                  "modules": [
                    {
                      "type": "resources",
                      "uuid": "%s",
                      "version": [1, 0, 0]
                    }
                  ]
                }
                """.formatted(Packs.escape(namespace), Packs.escape(description), header, module));
    }

    private void writeItemTextures(Path workDirectory) throws IOException {
        StringJoiner entries = new StringJoiner(",\n");
        for (CustomItem item : registry.all()) {
            entries.add("""
                        "%s": {
                          "textures": "textures/items/%s"
                        }\
                    """.formatted(Packs.escape(item.bedrockIcon()), item.id()));
        }

        Packs.writeString(workDirectory.resolve("textures").resolve("item_texture.json"), """
                {
                  "resource_pack_name": "%s",
                  "texture_name": "atlas.items",
                  "texture_data": {
                %s
                  }
                }
                """.formatted(Packs.escape(registry.namespace()), entries.toString()));
    }

    /** Block textures live in a separate atlas from items on Bedrock. */
    private void writeTerrainTextures(Path workDirectory) throws IOException {
        StringJoiner entries = new StringJoiner(",\n");
        Set<String> written = new LinkedHashSet<>();
        for (CustomBlock block : blocks.all()) {
            for (String name : block.textures().names()) {
                if (!written.add(name)) {
                    continue; // Two blocks sharing a face texture need only one entry.
                }
                entries.add("""
                            "%s": {
                              "textures": "textures/blocks/%s"
                            }\
                        """.formatted(Packs.escape(block.bedrockTexture(name)), Packs.escape(name)));
            }
        }

        Packs.writeString(workDirectory.resolve("textures").resolve("terrain_texture.json"), """
                {
                  "resource_pack_name": "%s",
                  "texture_name": "atlas.terrain",
                  "padding": 8,
                  "num_mip_levels": 4,
                  "texture_data": {
                %s
                  }
                }
                """.formatted(Packs.escape(registry.namespace()), entries.toString()));
    }

    private void copyBlockTextures(Path workDirectory, List<String> missingTextures) throws IOException {
        Path target = workDirectory.resolve("textures").resolve("blocks");
        Files.createDirectories(target);
        for (CustomBlock block : blocks.all()) {
            for (String name : block.textures().names()) {
                Path source = texturesDirectory.resolve(name + ".png");
                if (!Files.isRegularFile(source)) {
                    if (!missingTextures.contains(name)) {
                        missingTextures.add(name);
                    }
                    continue;
                }
                Files.copy(source, target.resolve(name + ".png"), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private void copyTextures(Path workDirectory, List<String> missingTextures) throws IOException {
        Path items = workDirectory.resolve("textures").resolve("items");
        Files.createDirectories(items);
        for (CustomItem item : registry.all()) {
            Path source = texturesDirectory.resolve(item.textureName() + ".png");
            if (!Files.isRegularFile(source)) {
                if (!missingTextures.contains(item.id())) {
                    missingTextures.add(item.id());
                }
                continue;
            }
            Files.copy(source, items.resolve(item.id() + ".png"), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static UUID deterministicUuid(String seed) {
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }
}
