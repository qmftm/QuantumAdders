package me.qmftm.quantumAdders.block;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * One entry in a block's drop table.
 *
 * <p>{@code item} is either a custom item id or a vanilla material name; the listener
 * resolves it at break time so a drop may point at an item defined in another file.
 */
public record BlockDrop(String item, int min, int max, double chance, boolean fortune) {

    /** Parses the {@code drops:} list. Malformed entries are reported and skipped. */
    public static List<BlockDrop> listFrom(ConfigurationSection section, String where,
                                           List<String> problems) {
        List<BlockDrop> drops = new ArrayList<>();
        List<?> raw = section.getList("drops");
        if (raw == null) {
            return drops;
        }
        for (Object element : raw) {
            if (!(element instanceof Map<?, ?> map)) {
                problems.add(where + ": each drop must be a mapping with an 'item'.");
                continue;
            }
            Object item = map.get("item");
            if (item == null || item.toString().isBlank()) {
                problems.add(where + ": a drop is missing 'item'.");
                continue;
            }

            int[] range = parseAmount(map.get("amount"), where, problems);
            double chance = parseChance(map.get("chance"), where, problems);
            boolean fortune = Boolean.TRUE.equals(map.get("fortune"));
            drops.add(new BlockDrop(item.toString(), range[0], range[1], chance, fortune));
        }
        return drops;
    }

    /** How many to drop this time, counting the tool's Fortune level when the entry opts in. */
    public int roll(Random random, int fortuneLevel) {
        if (chance < 1.0D && random.nextDouble() >= chance) {
            return 0;
        }
        int amount = min >= max ? min : min + random.nextInt(max - min + 1);
        if (fortune && fortuneLevel > 0) {
            // Vanilla ore behaviour: a uniform bonus multiplier of 1..level+1.
            amount *= 1 + random.nextInt(fortuneLevel + 1);
        }
        return amount;
    }

    /** Accepts {@code 3} or {@code "2-5"}. */
    private static int[] parseAmount(Object value, String where, List<String> problems) {
        if (value == null) {
            return new int[]{1, 1};
        }
        if (value instanceof Number number) {
            int amount = Math.max(0, number.intValue());
            return new int[]{amount, amount};
        }
        String text = value.toString().trim();
        int dash = text.indexOf('-');
        try {
            if (dash > 0) {
                int min = Integer.parseInt(text.substring(0, dash).trim());
                int max = Integer.parseInt(text.substring(dash + 1).trim());
                return new int[]{Math.max(0, Math.min(min, max)), Math.max(0, Math.max(min, max))};
            }
            int amount = Math.max(0, Integer.parseInt(text));
            return new int[]{amount, amount};
        } catch (NumberFormatException e) {
            problems.add(where + ": amount '" + text + "' is not a number or range; using 1.");
            return new int[]{1, 1};
        }
    }

    private static double parseChance(Object value, String where, List<String> problems) {
        if (value == null) {
            return 1.0D;
        }
        if (value instanceof Number number) {
            return clamp(number.doubleValue());
        }
        try {
            return clamp(Double.parseDouble(value.toString().trim()));
        } catch (NumberFormatException e) {
            problems.add(where + ": chance '" + value + "' is not a number; using 1.0.");
            return 1.0D;
        }
    }

    private static double clamp(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
