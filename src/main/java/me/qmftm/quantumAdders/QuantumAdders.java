package me.qmftm.quantumAdders;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.qmftm.quantumAdders.block.BlockListener;
import me.qmftm.quantumAdders.block.BlockRegistry;
import me.qmftm.quantumAdders.block.BlockStateAllocator;
import me.qmftm.quantumAdders.command.QuantumAddersCommand;
import me.qmftm.quantumAdders.geyser.GeyserBridge;
import me.qmftm.quantumAdders.item.ItemFactory;
import me.qmftm.quantumAdders.item.ItemRegistry;
import me.qmftm.quantumAdders.lang.Messages;
import me.qmftm.quantumAdders.pack.PackResult;
import me.qmftm.quantumAdders.pack.PackService;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

/**
 * Custom items that render on Java and Bedrock alike.
 *
 * <p>An item is one definition in items/*.yml expressed on three sides: the Java
 * {@code item_model} component on the ItemStack, a Geyser custom item definition keyed
 * to that same model, and a texture in each platform's resource pack. All three are
 * generated from the single YAML entry.
 */
public final class QuantumAdders extends JavaPlugin {

    private Messages messages;
    private ItemRegistry registry;
    private ItemFactory itemFactory;
    private BlockRegistry blockRegistry;
    private PackService packService;

    /**
     * Whether Geyser runs in this JVM. When it does not — it is external, or absent —
     * the API registration is impossible and the generated mappings file is the only
     * route to Bedrock.
     */
    private boolean geyserApiAvailable;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        messages = new Messages(this);
        messages.reload();

        registry = new ItemRegistry(this, messages);
        registry.reload();
        itemFactory = new ItemFactory(this);

        // Blocks load second: each one registers the item that places it.
        blockRegistry = new BlockRegistry(this, messages, registry,
                new BlockStateAllocator(this, messages));
        blockRegistry.reload();

        geyserApiAvailable = getServer().getPluginManager().getPlugin("Geyser-Spigot") != null;
        packService = new PackService(this, registry, blockRegistry,
                geyserApiAvailable ? geyserPackDirectory() : null, messages);

        if (geyserApiAvailable) {
            hookGeyser();
        } else {
            getLogger().info(messages.plain("log.geyser-absent"));
        }

        getServer().getPluginManager().registerEvents(
                new BlockListener(blockRegistry, registry, itemFactory), this);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(
                        new QuantumAddersCommand(this).build(),
                        "QuantumAdders custom item commands",
                        List.of("qa")));

        if (getConfig().getBoolean("auto-build-packs", true)) {
            // Synchronous on purpose: loadbefore puts this ahead of Geyser, so a pack
            // installed here is picked up during the same boot. Going async would race it.
            logPackBuild(packService.build());
        }
    }

    public void logPackBuild(PackResult result) {
        for (String failure : result.errors()) {
            getLogger().warning(failure);
        }
        if (result.installedTo() != null) {
            getLogger().info(messages.plain("log.pack-installed",
                    Messages.of("path", String.valueOf(result.installedTo().getParent()))));
        }
        if (result.mappings() != null && !geyserApiAvailable) {
            getLogger().info(messages.plain("log.mappings-note",
                    Messages.of("path", result.mappings().toString())));
        }
    }

    /**
     * Kept behind a guard because {@link GeyserBridge} links against Geyser classes that
     * are absent on a plain Paper server.
     */
    private void hookGeyser() {
        try {
            new GeyserBridge(this, registry, messages).subscribe();
        } catch (Throwable t) {
            geyserApiAvailable = false;
            getLogger().warning(messages.plain("log.geyser-hook-failed",
                    Messages.of("reason", String.valueOf(t))));
        }
    }

    /**
     * Geyser's pack folder. The API only answers once Geyser has initialised, which is
     * after this plugin enables, so the conventional path is used as a fallback — that is
     * the common case for the startup build.
     */
    private Supplier<Path> geyserPackDirectory() {
        return () -> {
            try {
                Path fromApi = GeyserBridge.packDirectory();
                if (fromApi != null) {
                    return fromApi;
                }
            } catch (Throwable ignored) {
                // Geyser is present but not up yet; fall through.
            }
            Path geyserData = getDataFolder().toPath().getParent().resolve("Geyser-Spigot");
            if (!Files.isDirectory(geyserData)) {
                return null;
            }
            try {
                // Geyser creates this itself, but not before its first start.
                return Files.createDirectories(geyserData.resolve("packs"));
            } catch (IOException e) {
                return null;
            }
        };
    }

    public Messages messages() {
        return messages;
    }

    public BlockRegistry blockRegistry() {
        return blockRegistry;
    }

    public ItemRegistry registry() {
        return registry;
    }

    public ItemFactory itemFactory() {
        return itemFactory;
    }

    public PackService packService() {
        return packService;
    }
}
