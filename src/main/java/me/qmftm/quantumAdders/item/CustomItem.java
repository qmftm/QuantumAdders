package me.qmftm.quantumAdders.item;

import net.kyori.adventure.key.Key;
import org.bukkit.Material;

import java.util.List;
import java.util.Locale;

/**
 * One custom item as declared in items/*.yml.
 *
 * <p>The same identity is expressed three ways, and all three must line up or the
 * item renders as its base material on one platform:
 * <ul>
 *   <li>{@link #modelKey()} — the Java {@code item_model} component and the resource
 *       pack entry at {@code assets/<namespace>/items/<id>.json}</li>
 *   <li>{@link #bedrockIcon()} — the key inside the Bedrock pack's item_texture.json</li>
 *   <li>{@link #id()} — what commands and the plugin API use</li>
 * </ul>
 */
public record CustomItem(
        String namespace,
        String id,
        Material base,
        String displayName,
        List<String> lore,
        String texture,
        String modelParent,
        Integer maxStackSize,
        BedrockOptions bedrock
) {

    /** Java {@code minecraft:item_model} value; also the Geyser definition's model. */
    public Key modelKey() {
        return Key.key(namespace, id);
    }

    /** Texture key inside the Bedrock resource pack. Bedrock has no namespaces. */
    public String bedrockIcon() {
        return namespace + "_" + id;
    }

    /** PNG basename under plugins/QuantumAdders/textures/, defaulting to the id. */
    public String textureName() {
        return texture == null || texture.isBlank() ? id : texture;
    }

    /**
     * Parent for the generated Java model. Tools and weapons need
     * {@code minecraft:item/handheld} or they are held flat, so that is inferred from
     * the base material unless the definition says otherwise.
     */
    public String resolvedModelParent() {
        if (modelParent != null && !modelParent.isBlank()) {
            return modelParent;
        }
        return isHandheld(base) ? "minecraft:item/handheld" : "minecraft:item/generated";
    }

    private static boolean isHandheld(Material material) {
        String name = material.name().toUpperCase(Locale.ROOT);
        return name.endsWith("_SWORD")
                || name.endsWith("_AXE")
                || name.endsWith("_PICKAXE")
                || name.endsWith("_SHOVEL")
                || name.endsWith("_HOE")
                || name.endsWith("_ROD")
                || name.equals("STICK")
                || name.equals("BLAZE_ROD")
                || name.equals("TRIDENT");
    }
}
