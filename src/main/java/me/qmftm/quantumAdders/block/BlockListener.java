package me.qmftm.quantumAdders.block;

import me.qmftm.quantumAdders.item.CustomItem;
import me.qmftm.quantumAdders.item.ItemFactory;
import me.qmftm.quantumAdders.item.ItemRegistry;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.NotePlayEvent;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Random;

/**
 * Keeps custom blocks looking and behaving like their own blocks rather than note blocks.
 *
 * <p>A note block's identity is fragile: right-clicking retunes it, and the server
 * rewrites its instrument from whatever sits underneath whenever a neighbour changes.
 * Either would silently turn one custom block into another, so both are suppressed —
 * but only for states this plugin owns, leaving ordinary note blocks fully playable.
 */
public final class BlockListener implements Listener {

    private final BlockRegistry blocks;
    private final ItemRegistry items;
    private final ItemFactory itemFactory;
    private final Random random = new Random();

    public BlockListener(BlockRegistry blocks, ItemRegistry items, ItemFactory itemFactory) {
        this.blocks = blocks;
        this.items = items;
        this.itemFactory = itemFactory;
    }

    /** Stamps the block's assigned state onto the note block that was just placed. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack inHand = event.getItemInHand();
        String id = itemFactory.idOf(inHand);
        if (id == null) {
            return;
        }
        CustomBlock block = blocks.get(id);
        if (block == null) {
            return; // A custom item that merely happens to be placeable.
        }
        String state = blocks.stateOf(id);
        if (state == null) {
            return;
        }
        try {
            BlockData data = Bukkit.createBlockData("minecraft:note_block[" + state + "]");
            // No physics: a neighbour update here would immediately rewrite the instrument.
            event.getBlockPlaced().setBlockData(data, false);
        } catch (IllegalArgumentException e) {
            // A state string that no longer parses, e.g. after a downgrade.
        }
    }

    /**
     * Replaces the note block's drops and experience with the custom block's own.
     *
     * <p>Silk touch yields the block itself and no experience, matching how vanilla ores
     * behave, unless the definition turns that off.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        CustomBlock block = customBlockAt(event.getBlock());
        if (block == null) {
            return;
        }
        event.setDropItems(false);
        event.setExpToDrop(0);

        if (event.getPlayer().getGameMode() == GameMode.CREATIVE) {
            return;
        }

        ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
        boolean silkTouch = tool.containsEnchantment(Enchantment.SILK_TOUCH);
        Location where = event.getBlock().getLocation().add(0.5, 0.5, 0.5);

        if (silkTouch && block.silkTouchSelf()) {
            dropSelf(block, where);
            return;
        }
        event.setExpToDrop(block.rollXp(random));

        if (block.dropsSelf()) {
            dropSelf(block, where);
            return;
        }
        int fortuneLevel = tool.getEnchantmentLevel(Enchantment.FORTUNE);
        for (BlockDrop drop : block.drops()) {
            int amount = drop.roll(random, fortuneLevel);
            if (amount > 0) {
                dropStack(drop.item(), amount, where);
            }
        }
    }

    private void dropSelf(CustomBlock block, Location where) {
        CustomItem item = items.get(block.id());
        if (item != null) {
            where.getWorld().dropItemNaturally(where, itemFactory.create(item, 1));
        }
    }

    /** A drop entry names either a custom item id or a vanilla material. */
    private void dropStack(String name, int amount, Location where) {
        CustomItem custom = items.get(name);
        if (custom != null) {
            where.getWorld().dropItemNaturally(where, itemFactory.create(custom, amount));
            return;
        }
        Material material = Material.matchMaterial(name);
        if (material != null && material.isItem()) {
            where.getWorld().dropItemNaturally(where, new ItemStack(material, amount));
        }
        // An unknown name was already reported when the block was loaded.
    }

    /**
     * Stops a right-click from retuning the block, without blocking the item in hand —
     * players must still be able to place blocks against it.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (customBlockAt(event.getClickedBlock()) != null) {
            event.setUseInteractedBlock(Event.Result.DENY);
        }
    }

    /** A custom block is not an instrument. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onNotePlay(NotePlayEvent event) {
        if (customBlockAt(event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    /**
     * Blocks the neighbour-driven state rewrite that would change the instrument, and with
     * it the block's identity.
     *
     * <p>This event fires constantly, so the cheap type test comes first and the state
     * lookup only happens for note blocks.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent event) {
        if (event.getChangedType() != Material.NOTE_BLOCK) {
            return;
        }
        if (customBlockAt(event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    private CustomBlock customBlockAt(Block block) {
        if (block == null || block.getType() != Material.NOTE_BLOCK) {
            return null;
        }
        return blocks.atState(BlockStateAllocator.propertiesOf(block.getBlockData().getAsString()));
    }
}
