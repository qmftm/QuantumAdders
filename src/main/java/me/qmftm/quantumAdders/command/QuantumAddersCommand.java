package me.qmftm.quantumAdders.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import me.qmftm.quantumAdders.QuantumAdders;
import me.qmftm.quantumAdders.block.BlockRegistry;
import me.qmftm.quantumAdders.block.BlockStateAllocator;
import me.qmftm.quantumAdders.block.CustomBlock;
import me.qmftm.quantumAdders.item.CustomItem;
import me.qmftm.quantumAdders.lang.Messages;
import me.qmftm.quantumAdders.pack.PackResult;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** {@code /quantumadders} (alias {@code /qa}), registered through Paper's Brigadier API. */
public final class QuantumAddersCommand {

    private final QuantumAdders plugin;

    public QuantumAddersCommand(QuantumAdders plugin) {
        this.plugin = plugin;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        SuggestionProvider<CommandSourceStack> itemIds = (context, builder) -> {
            String remaining = builder.getRemainingLowerCase();
            for (CustomItem item : plugin.registry().all()) {
                if (item.id().startsWith(remaining)) {
                    builder.suggest(item.id());
                }
            }
            return builder.buildFuture();
        };

        return Commands.literal("quantumadders")
                .requires(source -> source.getSender().hasPermission("quantumadders.admin"))
                .then(Commands.literal("give")
                        .then(Commands.argument("targets", ArgumentTypes.players())
                                .then(Commands.argument("item", StringArgumentType.word())
                                        .suggests(itemIds)
                                        .executes(context -> give(context, 1))
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 99))
                                                .executes(context -> give(context,
                                                        IntegerArgumentType.getInteger(context, "amount")))))))
                .then(Commands.literal("list").executes(this::list))
                .then(Commands.literal("reload").executes(this::reload))
                .then(Commands.literal("pack").executes(this::pack))
                .build();
    }

    private Messages messages() {
        return plugin.messages();
    }

    private int give(CommandContext<CommandSourceStack> context, int amount) throws CommandSyntaxException {
        CommandSender sender = context.getSource().getSender();
        String id = StringArgumentType.getString(context, "item");

        CustomItem item = plugin.registry().get(id);
        if (item == null) {
            sender.sendMessage(messages().get("command.unknown-item", Messages.of("item", id)));
            return 0;
        }

        List<Player> targets = context.getArgument("targets", PlayerSelectorArgumentResolver.class)
                .resolve(context.getSource());
        for (Player target : targets) {
            ItemStack stack = plugin.itemFactory().create(item, amount);
            Map<Integer, ItemStack> leftover = target.getInventory().addItem(stack);
            // A full inventory would silently eat the item otherwise.
            leftover.values().forEach(rest ->
                    target.getWorld().dropItemNaturally(target.getLocation(), rest));
        }

        sender.sendMessage(messages().get("command.give-success",
                Messages.of("item", id),
                Messages.of("amount", amount),
                Messages.of("count", targets.size())));
        return targets.size();
    }

    private int list(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (plugin.registry().isEmpty()) {
            sender.sendMessage(messages().get("command.list-empty"));
            return 0;
        }
        sender.sendMessage(messages().get("command.list-header",
                Messages.of("count", plugin.registry().size())));
        for (CustomItem item : plugin.registry().all()) {
            sender.sendMessage(messages().get("command.list-entry",
                    Messages.of("item", item.id()),
                    Messages.of("base", item.base().name().toLowerCase(Locale.ROOT))));
        }

        BlockRegistry blocks = plugin.blockRegistry();
        BlockStateAllocator allocator = blocks.allocator();
        // Shown even at zero blocks: the ceiling is the thing worth knowing up front.
        sender.sendMessage(messages().get("command.list-blocks-header",
                Messages.of("count", blocks.size()),
                Messages.of("used", allocator.used()),
                Messages.of("capacity", allocator.capacity()),
                Messages.of("free", allocator.capacity() - allocator.used())));
        for (CustomBlock block : blocks.all()) {
            sender.sendMessage(messages().get("command.list-block-entry",
                    Messages.of("item", block.id()),
                    Messages.of("state", String.valueOf(blocks.stateOf(block.id())))));
        }
        return plugin.registry().size();
    }

    private int reload(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        plugin.reloadConfig();
        plugin.messages().reload();
        plugin.registry().reload();
        plugin.blockRegistry().reload();

        sender.sendMessage(messages().get("command.reload-done",
                Messages.of("count", plugin.registry().size())));
        sender.sendMessage(messages().get("command.reload-geyser-note"));

        if (plugin.getConfig().getBoolean("auto-build-packs", true)) {
            buildPacks(sender);
        }
        return 1;
    }

    private int pack(CommandContext<CommandSourceStack> context) {
        buildPacks(context.getSource().getSender());
        return 1;
    }

    /** Pack building touches the disk, so it stays off the main thread. */
    private void buildPacks(CommandSender sender) {
        sender.sendMessage(messages().get("command.pack-building"));
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            PackResult result = plugin.packService().build();
            sender.sendMessage(messages().get("command.pack-done",
                    Messages.of("count", result.itemCount())));

            if (result.javaPack() != null) {
                sender.sendMessage(messages().get("command.pack-path-java",
                        Messages.of("path", result.javaPack().toString())));
            }
            if (result.bedrockPack() != null) {
                sender.sendMessage(messages().get("command.pack-path-bedrock",
                        Messages.of("path", result.bedrockPack().toString())));
            }
            if (result.mappings() != null) {
                sender.sendMessage(messages().get("command.pack-path-mappings",
                        Messages.of("path", result.mappings().toString())));
            }
            if (result.blockMappings() != null) {
                sender.sendMessage(messages().get("command.pack-path-mappings",
                        Messages.of("path", result.blockMappings().toString())));
            }
            if (!result.missingTextures().isEmpty()) {
                sender.sendMessage(messages().get("command.pack-missing-textures",
                        Messages.of("items", String.join(", ", result.missingTextures()))));
            }
            if (result.installedTo() != null) {
                sender.sendMessage(messages().get("command.pack-installed"));
            }
            for (String failure : result.errors()) {
                sender.sendMessage(messages().get("command.pack-error", Messages.of("reason", failure)));
            }
        });
    }
}
