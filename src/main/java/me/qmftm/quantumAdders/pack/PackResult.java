package me.qmftm.quantumAdders.pack;

import java.nio.file.Path;
import java.util.List;

/**
 * Outcome of a pack build.
 *
 * @param itemCount       items written into the packs
 * @param missingTextures item ids whose PNG was not found; they build but render as
 *                        the missing-texture checkerboard
 * @param javaPack        the generated Java resource pack zip, or {@code null} on failure
 * @param bedrockPack     the generated .mcpack, or {@code null} on failure
 * @param installedTo     where the .mcpack was copied for Geyser, or {@code null}
 * @param mappings        the Geyser custom_mappings JSON for items, or {@code null}
 * @param blockMappings   the Geyser custom_mappings JSON for blocks, or {@code null}
 * @param errors          failures that stopped part of the build
 */
public record PackResult(
        int itemCount,
        List<String> missingTextures,
        Path javaPack,
        Path bedrockPack,
        Path installedTo,
        Path mappings,
        Path blockMappings,
        List<String> errors
) {
    public boolean ok() {
        return errors.isEmpty();
    }
}
