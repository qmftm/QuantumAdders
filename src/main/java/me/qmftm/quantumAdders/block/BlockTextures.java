package me.qmftm.quantumAdders.block;

import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Which PNG goes on which face.
 *
 * <p>Three ways to say it, narrowest winning: a per-face name, then {@code side} for the
 * four walls, then {@code all}. So a plain block sets one value, a pillar sets three, and
 * a machine sets six.
 */
public record BlockTextures(
        String all,
        String top,
        String bottom,
        String side,
        String north,
        String south,
        String east,
        String west
) {

    public enum Face {
        UP, DOWN, NORTH, SOUTH, EAST, WEST
    }

    /**
     * Reads {@code texture:} (one name for everything) and the {@code textures:} section,
     * falling back to the block id so a block named like its PNG needs neither.
     */
    public static BlockTextures from(ConfigurationSection section, String fallback) {
        String single = section.getString("texture");
        ConfigurationSection faces = section.getConfigurationSection("textures");

        if (faces == null) {
            String all = single == null || single.isBlank() ? fallback : single;
            return new BlockTextures(all, null, null, null, null, null, null, null);
        }
        String all = faces.getString("all", single);
        if (all == null || all.isBlank()) {
            all = fallback;
        }
        return new BlockTextures(
                all,
                faces.getString("top"),
                faces.getString("bottom"),
                faces.getString("side"),
                faces.getString("north"),
                faces.getString("south"),
                faces.getString("east"),
                faces.getString("west"));
    }

    /** The texture name for one face, after applying the fallback chain. */
    public String of(Face face) {
        return switch (face) {
            case UP -> firstSet(top, all);
            case DOWN -> firstSet(bottom, all);
            case NORTH -> firstSet(north, side, all);
            case SOUTH -> firstSet(south, side, all);
            case EAST -> firstSet(east, side, all);
            case WEST -> firstSet(west, side, all);
        };
    }

    /** True when every face resolves to the same texture, which allows a simpler model. */
    public boolean uniform() {
        String first = of(Face.UP);
        for (Face face : Face.values()) {
            if (!first.equals(of(face))) {
                return false;
            }
        }
        return true;
    }

    /** Every distinct PNG this block needs. */
    public Set<String> names() {
        Set<String> names = new LinkedHashSet<>();
        for (Face face : Face.values()) {
            names.add(of(face));
        }
        return names;
    }

    /** The texture used for break particles — the top face reads best. */
    public String particle() {
        return of(Face.UP);
    }

    private static String firstSet(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return "";
    }
}
