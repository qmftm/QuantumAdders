package me.qmftm.quantumAdders.geyser;

import me.qmftm.quantumAdders.item.BedrockOptions;
import me.qmftm.quantumAdders.item.CustomItem;
import me.qmftm.quantumAdders.item.ItemRegistry;
import me.qmftm.quantumAdders.lang.Messages;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.event.EventRegistrar;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomItemsEvent;
import org.geysermc.geyser.api.item.custom.v2.CustomItemBedrockOptions;
import org.geysermc.geyser.api.item.custom.v2.CustomItemDefinition;
import org.geysermc.geyser.api.util.CreativeCategory;
import org.geysermc.geyser.api.util.Identifier;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Mirrors every registered item into Geyser so Bedrock clients see it too.
 *
 * <p>This class touches Geyser classes directly and must therefore never be loaded when
 * Geyser is absent — {@code me.qmftm.quantumAdders.QuantumAdders} guards the entry point.
 *
 * <p>Timing matters: Geyser fires {@link GeyserDefineCustomItemsEvent} once, while it
 * builds its item registry during startup. The plugin declares {@code loadbefore:
 * [Geyser-Spigot]} so this subscription is in place before that happens. A consequence is
 * that {@code /qa reload} cannot push new items to Bedrock — Geyser has to restart.
 */
public final class GeyserBridge {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final Plugin plugin;
    private final ItemRegistry registry;
    private final Messages messages;

    public GeyserBridge(Plugin plugin, ItemRegistry registry, Messages messages) {
        this.plugin = plugin;
        this.registry = registry;
        this.messages = messages;
    }

    /** Geyser's own pack folder, where a generated .mcpack can be dropped. */
    public static Path packDirectory() {
        GeyserApi api = GeyserApi.api();
        return api == null ? null : api.packDirectory();
    }

    public void subscribe() {
        GeyserApi api = GeyserApi.api();
        if (api == null) {
            plugin.getLogger().warning(messages.plain("log.geyser-not-initialised"));
            return;
        }
        api.eventBus().subscribe(EventRegistrar.of(plugin), GeyserDefineCustomItemsEvent.class, this::defineItems);
        plugin.getLogger().info(messages.plain("log.geyser-subscribed"));
    }

    private void defineItems(GeyserDefineCustomItemsEvent event) {
        int registered = 0;
        for (CustomItem item : registry.all()) {
            try {
                event.register(javaItemOf(item), definitionOf(item));
                registered++;
            } catch (Exception e) {
                plugin.getLogger().warning(messages.plain("log.geyser-item-rejected",
                        Messages.of("item", item.id()), Messages.of("reason", String.valueOf(e.getMessage()))));
            }
        }
        plugin.getLogger().info(messages.plain("log.geyser-registered", Messages.of("count", registered)));
    }

    /** The vanilla item the custom item rides on, e.g. {@code minecraft:paper}. */
    private Identifier javaItemOf(CustomItem item) {
        NamespacedKey key = item.base().getKey();
        return Identifier.of(key.getNamespace(), key.getKey());
    }

    private CustomItemDefinition definitionOf(CustomItem item) {
        // Both the Bedrock item id and the model key are <namespace>:<id>. The model must
        // equal the Java item_model component, which is what ties the two platforms together.
        Identifier identity = Identifier.of(item.namespace(), item.id());

        CustomItemDefinition.Builder builder = CustomItemDefinition
                .builder(identity, identity)
                .bedrockOptions(bedrockOptionsOf(item));

        if (item.displayName() != null && !item.displayName().isBlank()) {
            // Bedrock takes a plain string, so MiniMessage is flattened to legacy colour codes.
            builder.displayName(LEGACY.serialize(MINI_MESSAGE.deserialize(item.displayName())));
        }
        return builder.build();
    }

    private CustomItemBedrockOptions.Builder bedrockOptionsOf(CustomItem item) {
        BedrockOptions options = item.bedrock();
        CustomItemBedrockOptions.Builder builder = CustomItemBedrockOptions.builder()
                .icon(item.bedrockIcon())
                .displayHandheld(options.displayHandheld())
                .allowOffhand(options.allowOffhand())
                .protectionValue(options.protectionValue())
                .creativeCategory(creativeCategoryOf(item));

        if (options.creativeGroup() != null && !options.creativeGroup().isBlank()) {
            builder.creativeGroup(options.creativeGroup());
        }
        return builder;
    }

    private CreativeCategory creativeCategoryOf(CustomItem item) {
        String name = item.bedrock().creativeCategory();
        if (name == null || name.isBlank()) {
            return CreativeCategory.ITEMS;
        }
        try {
            return CreativeCategory.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning(messages.plain("log.geyser-unknown-category",
                    Messages.of("item", item.id()), Messages.of("value", name)));
            return CreativeCategory.ITEMS;
        }
    }
}
