package me.qmftm.quantumAdders.pack;

import me.qmftm.quantumAdders.block.BlockRegistry;
import me.qmftm.quantumAdders.block.BlockTextures;
import me.qmftm.quantumAdders.block.CustomBlock;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.StringJoiner;

/**
 * Writes the Geyser {@code custom_mappings} file for custom blocks.
 *
 * <p>All custom blocks ride on {@code minecraft:note_block}, so they are one Bedrock
 * block whose appearance varies per state. {@code only_override_states} keeps every state
 * this plugin did not claim rendering as an ordinary note block.
 *
 * <p>Blocks are a separate file from items because Geyser reads each mapping type with
 * its own reader; a file mixing the two would be parsed for one and ignored for the other.
 */
final class GeyserBlockMappingsWriter {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final BlockRegistry blocks;

    GeyserBlockMappingsWriter(BlockRegistry blocks) {
        this.blocks = blocks;
    }

    Path write(Path target) throws IOException {
        StringJoiner overrides = new StringJoiner(",\n");
        for (CustomBlock block : blocks.all()) {
            String state = blocks.stateOf(block.id());
            if (state != null) {
                overrides.add(override(state, block));
            }
        }

        Packs.writeString(target, """
                {
                  "format_version": 1,
                  "blocks": {
                    "minecraft:note_block": {
                      "name": "%s_blocks",
                      "included_in_creative_inventory": false,
                      "only_override_states": true,
                      "state_overrides": {
                %s
                      }
                    }
                  }
                }
                """.formatted(Packs.escape(blocks.namespace()), overrides.toString()));
        return target;
    }

    private String override(String state, CustomBlock block) {
        StringJoiner fields = new StringJoiner(",\n");
        fields.add("            \"unit_cube\": true");
        fields.add(materialInstances(block));
        fields.add("            \"destructible_by_mining\": " + block.hardness());

        if (block.lightEmission() > 0) {
            fields.add("            \"light_emission\": " + block.lightEmission());
        }
        if (block.displayName() != null && !block.displayName().isBlank()) {
            String plain = PLAIN.serialize(MINI_MESSAGE.deserialize(block.displayName()));
            fields.add("            \"display_name\": \"" + Packs.escape(plain) + "\"");
        }
        String category = creativeCategory(block);
        if (!category.isBlank()) {
            fields.add("            \"creative_category\": \"" + Packs.escape(category) + "\"");
        }

        return "          \"" + Packs.escape(state) + "\": {\n" + fields + "\n          }";
    }

    /**
     * Bedrock's per-face textures.
     *
     * <p>A uniform block needs only the {@code *} instance. Otherwise every face is named
     * individually, because Bedrock has no equivalent of Java's {@code side} shorthand.
     */
    private String materialInstances(CustomBlock block) {
        BlockTextures textures = block.textures();
        StringJoiner instances = new StringJoiner(",\n");

        if (textures.uniform()) {
            instances.add(instance("*", block.bedrockTexture(textures.of(BlockTextures.Face.UP))));
        } else {
            for (BlockTextures.Face face : BlockTextures.Face.values()) {
                instances.add(instance(face.name().toLowerCase(Locale.ROOT),
                        block.bedrockTexture(textures.of(face))));
            }
        }
        return "            \"material_instances\": {\n" + instances + "\n            }";
    }

    private String instance(String face, String texture) {
        return """
                          "%s": {
                            "texture": "%s",
                            "render_method": "opaque",
                            "face_dimming": true,
                            "ambient_occlusion": true
                          }\
                """.formatted(Packs.escape(face), Packs.escape(texture));
    }

    private String creativeCategory(CustomBlock block) {
        String name = block.creativeCategory();
        if (name == null || name.isBlank()) {
            return "construction";
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "none", "construction", "nature", "equipment", "items" -> lower;
            default -> "construction";
        };
    }
}
