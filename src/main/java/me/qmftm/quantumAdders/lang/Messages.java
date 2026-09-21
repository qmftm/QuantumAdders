package me.qmftm.quantumAdders.lang;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;

/**
 * Every user-facing string, loaded from lang/&lt;language&gt;.yml.
 *
 * <p>Values are MiniMessage. Placeholders are passed as tags — {@code <item>},
 * {@code <count>} — so a translation may reorder or drop them freely.
 *
 * <p>The bundled file of the same name backs the one on disk, so a key added in a later
 * version resolves to its shipped text instead of failing after an upgrade.
 */
public final class Messages {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final List<String> BUNDLED = List.of("ko_kr", "en_us");
    private static final String FALLBACK = "en_us";

    private final Plugin plugin;
    private YamlConfiguration values = new YamlConfiguration();

    public Messages(Plugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        File directory = new File(plugin.getDataFolder(), "lang");
        if (!directory.isDirectory() && !directory.mkdirs()) {
            plugin.getLogger().severe("Could not create " + directory.getPath());
        }
        for (String bundled : BUNDLED) {
            copyIfAbsent(directory, bundled);
        }

        String language = normalise(plugin.getConfig().getString("language", "ko_kr"));
        File file = new File(directory, language + ".yml");
        if (!file.isFile()) {
            plugin.getLogger().warning("No lang/" + language + ".yml; using " + FALLBACK + ".");
            language = FALLBACK;
            file = new File(directory, language + ".yml");
        }

        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(file);
        YamlConfiguration defaults = bundled(language);
        if (defaults == null) {
            defaults = bundled(FALLBACK);
        }
        if (defaults != null) {
            loaded.setDefaults(defaults);
        }
        values = loaded;
    }

    /** A formatted message, prefixed, ready to send to a player or the console. */
    public Component get(String key, TagResolver... placeholders) {
        return MINI_MESSAGE.deserialize(raw(key), withPrefix(placeholders));
    }

    /** The same text without formatting, for {@code getLogger()} which takes a String. */
    public String plain(String key, TagResolver... placeholders) {
        Component rendered = MINI_MESSAGE.deserialize(raw(key), withPrefix(placeholders));
        return PlainTextComponentSerializer.plainText().serialize(rendered);
    }

    /** Shorthand for the common single-placeholder case. */
    public static TagResolver of(String name, String value) {
        return Placeholder.unparsed(name, value == null ? "" : value);
    }

    public static TagResolver of(String name, int value) {
        return Placeholder.unparsed(name, Integer.toString(value));
    }

    private String raw(String key) {
        String value = values.getString(key);
        // Showing the key beats showing nothing when a translation is incomplete.
        return value == null ? key : value;
    }

    private TagResolver withPrefix(TagResolver[] placeholders) {
        TagResolver.Builder builder = TagResolver.builder();
        builder.resolver(Placeholder.parsed("prefix", values.getString("prefix", "")));
        for (TagResolver placeholder : placeholders) {
            builder.resolver(placeholder);
        }
        return builder.build();
    }

    private void copyIfAbsent(File directory, String language) {
        File target = new File(directory, language + ".yml");
        if (target.isFile()) {
            return;
        }
        try (InputStream in = plugin.getResource("lang/" + language + ".yml")) {
            if (in != null) {
                Files.copy(in, target.toPath());
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Could not write lang/" + language + ".yml: " + e.getMessage());
        }
    }

    private YamlConfiguration bundled(String language) {
        InputStream in = plugin.getResource("lang/" + language + ".yml");
        if (in == null) {
            return null;
        }
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (IOException e) {
            return null;
        }
    }

    /** Accepts ko-KR, ko_KR and the like; the files are lowercase with an underscore. */
    private static String normalise(String language) {
        if (language == null || language.isBlank()) {
            return "ko_kr";
        }
        return language.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }
}
