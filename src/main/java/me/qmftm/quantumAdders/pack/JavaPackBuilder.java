package me.qmftm.quantumAdders.pack;

import me.qmftm.quantumAdders.block.BlockRegistry;
import me.qmftm.quantumAdders.block.BlockTextures;
import me.qmftm.quantumAdders.block.CustomBlock;
import me.qmftm.quantumAdders.item.CustomItem;
import me.qmftm.quantumAdders.item.ItemRegistry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.StringJoiner;

/**
 * Builds the Java resource pack.
 *
 * <p>Layout follows the 1.21.4+ item model format: {@code assets/<ns>/items/<id>.json}
 * is the item definition the {@code item_model} component points at, and it in turn
 * references a normal block/item model.
 */
final class JavaPackBuilder {

    private final ItemRegistry registry;
    private final BlockRegistry blocks;
    private final Path texturesDirectory;
    private final String description;
    private final int packFormat;

    JavaPackBuilder(ItemRegistry registry, BlockRegistry blocks, Path texturesDirectory,
                    String description, int packFormat) {
        this.registry = registry;
        this.blocks = blocks;
        this.texturesDirectory = texturesDirectory;
        this.description = description;
        this.packFormat = packFormat;
    }

    Path build(Path workDirectory, Path target, List<String> missingTextures) throws IOException {
        Packs.clean(workDirectory);

        String namespace = registry.namespace();
        Packs.writeString(workDirectory.resolve("pack.mcmeta"), """
                {
                  "pack": {
                    "pack_format": %d,
                    "description": "%s"
                  }
                }
                """.formatted(packFormat, Packs.escape(description)));

        Path assets = workDirectory.resolve("assets").resolve(namespace);
        for (CustomItem item : registry.all()) {
            writeItemDefinition(assets, namespace, item);
            writeModel(assets, namespace, item);
            copyTexture(assets, item, missingTextures);
        }

        if (blocks != null && !blocks.isEmpty()) {
            for (CustomBlock block : blocks.all()) {
                writeBlockModel(assets, namespace, block);
                copyBlockTextures(assets, block, missingTextures);
            }
            writeNoteBlockStates(workDirectory, namespace);
        }

        Packs.zip(workDirectory, target);
        return target;
    }

    /** What {@code minecraft:item_model} resolves to. */
    private void writeItemDefinition(Path assets, String namespace, CustomItem item) throws IOException {
        Packs.writeString(assets.resolve("items").resolve(item.id() + ".json"), """
                {
                  "model": {
                    "type": "minecraft:model",
                    "model": "%s:item/%s"
                  }
                }
                """.formatted(namespace, item.id()));
    }

    private void writeModel(Path assets, String namespace, CustomItem item) throws IOException {
        String parent = item.resolvedModelParent();
        Path file = assets.resolve("models").resolve("item").resolve(item.id() + ".json");

        // A block's item inherits the block model whole — textures and all — so adding a
        // layer0 here would fight with the cube's own faces.
        if (parent.startsWith(namespace + ":block/")) {
            Packs.writeString(file, """
                    {
                      "parent": "%s"
                    }
                    """.formatted(Packs.escape(parent)));
            return;
        }
        Packs.writeString(file, """
                {
                  "parent": "%s",
                  "textures": {
                    "layer0": "%s:item/%s"
                  }
                }
                """.formatted(Packs.escape(parent), namespace, item.id()));
    }

    /**
     * Writes the block model.
     *
     * <p>A uniform block uses cube_all, one texture reference instead of six. Anything
     * else uses cube and names every face, which covers a pillar and a fully six-sided
     * block alike without needing a third case.
     */
    private void writeBlockModel(Path assets, String namespace, CustomBlock block) throws IOException {
        BlockTextures textures = block.textures();
        Path file = assets.resolve("models").resolve("block").resolve(block.id() + ".json");

        if (textures.uniform()) {
            Packs.writeString(file, """
                    {
                      "parent": "minecraft:block/cube_all",
                      "textures": {
                        "all": "%s:block/%s"
                      }
                    }
                    """.formatted(namespace, Packs.escape(textures.of(BlockTextures.Face.UP))));
            return;
        }
        Packs.writeString(file, """
                {
                  "parent": "minecraft:block/cube",
                  "textures": {
                    "particle": "%1$s:block/%2$s",
                    "up": "%1$s:block/%2$s",
                    "down": "%1$s:block/%3$s",
                    "north": "%1$s:block/%4$s",
                    "south": "%1$s:block/%5$s",
                    "east": "%1$s:block/%6$s",
                    "west": "%1$s:block/%7$s"
                  }
                }
                """.formatted(namespace,
                Packs.escape(textures.of(BlockTextures.Face.UP)),
                Packs.escape(textures.of(BlockTextures.Face.DOWN)),
                Packs.escape(textures.of(BlockTextures.Face.NORTH)),
                Packs.escape(textures.of(BlockTextures.Face.SOUTH)),
                Packs.escape(textures.of(BlockTextures.Face.EAST)),
                Packs.escape(textures.of(BlockTextures.Face.WEST))));
    }

    /** Copies every PNG the faces refer to, keyed by texture name so faces can share one. */
    private void copyBlockTextures(Path assets, CustomBlock block, List<String> missingTextures)
            throws IOException {
        Path directory = assets.resolve("textures").resolve("block");
        Files.createDirectories(directory);
        for (String name : block.textures().names()) {
            Path source = texturesDirectory.resolve(name + ".png");
            if (!Files.isRegularFile(source)) {
                if (!missingTextures.contains(name)) {
                    missingTextures.add(name);
                }
                continue;
            }
            Files.copy(source, directory.resolve(name + ".png"), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Rewrites vanilla's note_block blockstates file.
     *
     * <p>Every state must appear — one left out renders as the missing-model cube — so
     * the full list is emitted and only the assigned states point at custom models. This
     * file lives under assets/minecraft, so it collides with any other pack that also
     * redefines note blocks; that is inherent to the technique.
     */
    private void writeNoteBlockStates(Path workDirectory, String namespace) throws IOException {
        StringJoiner variants = new StringJoiner(",\n");
        for (String state : blocks.allocator().allStates()) {
            CustomBlock block = blocks.atState(state);
            String model = block == null
                    ? "minecraft:block/note_block"
                    : namespace + ":block/" + block.id();
            variants.add("    \"%s\": { \"model\": \"%s\" }"
                    .formatted(Packs.escape(state), Packs.escape(model)));
        }
        Packs.writeString(
                workDirectory.resolve("assets").resolve("minecraft")
                        .resolve("blockstates").resolve("note_block.json"),
                "{\n  \"variants\": {\n" + variants + "\n  }\n}\n");
    }

    private void copyTexture(Path assets, CustomItem item, List<String> missingTextures) throws IOException {
        Path source = texturesDirectory.resolve(item.textureName() + ".png");
        if (!Files.isRegularFile(source)) {
            missingTextures.add(item.id());
            return;
        }
        Path destination = assets.resolve("textures").resolve("item").resolve(item.id() + ".png");
        Files.createDirectories(destination.getParent());
        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
    }
}
