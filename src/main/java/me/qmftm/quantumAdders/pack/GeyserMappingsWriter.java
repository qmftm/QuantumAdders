package me.qmftm.quantumAdders.pack;

import me.qmftm.quantumAdders.item.BedrockOptions;
import me.qmftm.quantumAdders.item.CustomItem;
import me.qmftm.quantumAdders.item.ItemRegistry;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.NamespacedKey;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Writes a Geyser {@code custom_mappings} JSON file.
 *
 * <p>The API path in {@code GeyserBridge} only works when Geyser runs in this same JVM.
 * When Geyser is external — standalone, or on a proxy — nothing in this process can reach
 * it, and a mappings file dropped into its {@code custom_mappings} folder is the only way
 * to register the items. The file describes exactly what the API registration would.
 *
 * <p>Deliberately free of Geyser imports so it runs on a server without Geyser.
 */
final class GeyserMappingsWriter {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final ItemRegistry registry;

    GeyserMappingsWriter(ItemRegistry registry) {
        this.registry = registry;
    }

    Path write(Path target) throws IOException {
        // Geyser keys definitions by the vanilla item they ride on.
        Map<String, List<CustomItem>> byBaseItem = new LinkedHashMap<>();
        for (CustomItem item : registry.all()) {
            NamespacedKey key = item.base().getKey();
            byBaseItem.computeIfAbsent(key.getNamespace() + ":" + key.getKey(),
                    ignored -> new ArrayList<>()).add(item);
        }

        StringJoiner baseItems = new StringJoiner(",\n");
        byBaseItem.forEach((baseItem, items) -> {
            StringJoiner definitions = new StringJoiner(",\n");
            for (CustomItem item : items) {
                definitions.add(definition(item));
            }
            baseItems.add("    \"" + Packs.escape(baseItem) + "\": [\n"
                    + definitions
                    + "\n    ]");
        });

        Packs.writeString(target, "{\n"
                + "  \"format_version\": 2,\n"
                + "  \"items\": {\n"
                + baseItems
                + "\n  }\n"
                + "}\n");
        return target;
    }

    private String definition(CustomItem item) {
        String identity = item.namespace() + ":" + item.id();
        StringJoiner fields = new StringJoiner(",\n");
        fields.add("        \"type\": \"definition\"");
        fields.add("        \"bedrock_identifier\": \"" + Packs.escape(identity) + "\"");
        fields.add("        \"model\": \"" + Packs.escape(identity) + "\"");

        if (item.displayName() != null && !item.displayName().isBlank()) {
            String plain = LEGACY.serialize(MINI_MESSAGE.deserialize(item.displayName()));
            fields.add("        \"display_name\": \"" + Packs.escape(plain) + "\"");
        }
        if (item.maxStackSize() != null) {
            fields.add("        \"components\": {\n"
                    + "          \"minecraft:max_stack_size\": " + item.maxStackSize() + "\n"
                    + "        }");
        }
        fields.add(bedrockOptions(item));

        return "      {\n" + fields + "\n      }";
    }

    private String bedrockOptions(CustomItem item) {
        BedrockOptions options = item.bedrock();
        StringJoiner fields = new StringJoiner(",\n");
        fields.add("          \"icon\": \"" + Packs.escape(item.bedrockIcon()) + "\"");
        fields.add("          \"display_handheld\": " + options.displayHandheld());
        fields.add("          \"allow_offhand\": " + options.allowOffhand());
        fields.add("          \"protection_value\": " + options.protectionValue());
        // Geyser's JSON takes the lowercase form, unlike the enum the API uses.
        fields.add("          \"creative_category\": \""
                + Packs.escape(creativeCategory(options)) + "\"");

        if (options.creativeGroup() != null && !options.creativeGroup().isBlank()) {
            fields.add("          \"creative_group\": \""
                    + Packs.escape(options.creativeGroup()) + "\"");
        }
        return "        \"bedrock_options\": {\n" + fields + "\n        }";
    }

    private String creativeCategory(BedrockOptions options) {
        String name = options.creativeCategory();
        if (name == null || name.isBlank()) {
            return "items";
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "none", "construction", "nature", "equipment", "items" -> lower;
            // ALL and ITEM_COMMAND_ONLY exist in the API enum but not in the JSON schema.
            default -> "items";
        };
    }
}
