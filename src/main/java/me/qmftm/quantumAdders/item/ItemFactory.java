package me.qmftm.quantumAdders.item;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * Turns a {@link CustomItem} definition into a real ItemStack.
 *
 * <p>The visual swap is entirely the {@code item_model} component: the stack stays a
 * vanilla item, so it stacks, burns and gets picked up like one. Bedrock clients get
 * the same swap through Geyser, which matches on that identical model key.
 */
public final class ItemFactory {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final NamespacedKey idKey;

    public ItemFactory(Plugin plugin) {
        this.idKey = new NamespacedKey(plugin, "item_id");
    }

    public NamespacedKey idKey() {
        return idKey;
    }

    public ItemStack create(CustomItem item, int amount) {
        ItemStack stack = new ItemStack(item.base(), amount);

        stack.setData(DataComponentTypes.ITEM_MODEL, item.modelKey());

        if (item.displayName() != null && !item.displayName().isBlank()) {
            stack.setData(DataComponentTypes.CUSTOM_NAME, render(item.displayName()));
        }
        if (!item.lore().isEmpty()) {
            List<Component> lines = item.lore().stream().map(ItemFactory::render).toList();
            stack.setData(DataComponentTypes.LORE, ItemLore.lore(lines));
        }
        if (item.maxStackSize() != null) {
            stack.setData(DataComponentTypes.MAX_STACK_SIZE, item.maxStackSize());
        }

        // Survives anvils, shulkers and /give round-trips, so the item stays identifiable.
        stack.editPersistentDataContainer(pdc -> pdc.set(idKey, PersistentDataType.STRING, item.id()));
        return stack;
    }

    /** The custom item id carried by this stack, or {@code null} if it is not one of ours. */
    public String idOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return stack.getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
    }

    /**
     * Italics are suppressed only when the definition did not ask for them, so
     * {@code <i>} in a display name still works.
     */
    private static Component render(String miniMessage) {
        return MINI_MESSAGE.deserialize(miniMessage)
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
