package me.qmftm.quantumAdders.block;

import net.kyori.adventure.key.Key;

import java.util.List;
import java.util.Random;

/**
 * One custom block as declared in blocks/*.yml.
 *
 * <p>A custom block is a note block wearing a different face. The {@code instrument} and
 * {@code note} of its block state encode which block it is, so no per-coordinate
 * bookkeeping is needed — reading the state is enough to identify it. The state itself is
 * not stored here; {@link BlockStateAllocator} owns that mapping and persists it.
 */
public record CustomBlock(
        String namespace,
        String id,
        String displayName,
        List<String> lore,
        BlockTextures textures,
        float hardness,
        int lightEmission,
        List<BlockDrop> drops,
        boolean silkTouchSelf,
        int minXp,
        int maxXp,
        String creativeCategory,
        String creativeGroup
) {

    /** Java model key, shared by the block model and the item that places it. */
    public Key modelKey() {
        return Key.key(namespace, id);
    }

    /** Bedrock block identifier registered with Geyser. */
    public String bedrockIdentifier() {
        return namespace + ":" + id;
    }

    /** Texture key inside the Bedrock pack's terrain_texture.json, per PNG. */
    public String bedrockTexture(String textureName) {
        return namespace + "_" + textureName;
    }

    /** True when the block has no drop table and should simply drop itself. */
    public boolean dropsSelf() {
        return drops.isEmpty();
    }

    /** Experience to drop for this break. Silk touch suppresses it, as in vanilla. */
    public int rollXp(Random random) {
        if (maxXp <= 0) {
            return 0;
        }
        return minXp >= maxXp ? minXp : minXp + random.nextInt(maxXp - minXp + 1);
    }
}
