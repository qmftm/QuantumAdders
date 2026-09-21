package me.qmftm.quantumAdders.item;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Bedrock-side presentation for a custom item. These values are handed to Geyser
 * verbatim; they have no effect on Java clients.
 */
public record BedrockOptions(
        String creativeCategory,
        String creativeGroup,
        boolean displayHandheld,
        boolean allowOffhand,
        int protectionValue
) {

    public static final BedrockOptions DEFAULT = new BedrockOptions("ITEMS", "", false, true, 0);

    public static BedrockOptions from(ConfigurationSection section) {
        if (section == null) {
            return DEFAULT;
        }
        return new BedrockOptions(
                section.getString("creative-category", DEFAULT.creativeCategory()),
                section.getString("creative-group", DEFAULT.creativeGroup()),
                section.getBoolean("display-handheld", DEFAULT.displayHandheld()),
                section.getBoolean("allow-offhand", DEFAULT.allowOffhand()),
                section.getInt("protection-value", DEFAULT.protectionValue())
        );
    }
}
