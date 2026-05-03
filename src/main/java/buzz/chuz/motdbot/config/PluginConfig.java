package buzz.chuz.motdbot.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public final class PluginConfig {

    private final String discordToken;
    private final long channelId;
    private final int initialFetchLimit;

    private final int motdMaxLength;
    private final String fallbackMotd;
    private final String quoteColor;
    private final String attributionColor;

    private final boolean ignoreBots;
    private final boolean ignoreAttachmentsOnly;
    private final boolean ignoreImageOnly;
    private final boolean requireQuoteMarks;
    private final int minLength;

    /** Lower-cased Discord display name → friendly alias. */
    private final Map<String, String> aliases;

    private PluginConfig(Builder b) {
        this.discordToken = b.discordToken;
        this.channelId = b.channelId;
        this.initialFetchLimit = b.initialFetchLimit;
        this.motdMaxLength = b.motdMaxLength;
        this.fallbackMotd = b.fallbackMotd;
        this.quoteColor = b.quoteColor;
        this.attributionColor = b.attributionColor;
        this.ignoreBots = b.ignoreBots;
        this.ignoreAttachmentsOnly = b.ignoreAttachmentsOnly;
        this.ignoreImageOnly = b.ignoreImageOnly;
        this.requireQuoteMarks = b.requireQuoteMarks;
        this.minLength = b.minLength;
        this.aliases = Map.copyOf(b.aliases);
    }

    public static PluginConfig from(FileConfiguration cfg) {
        Builder b = new Builder();
        b.discordToken = cfg.getString("discord.token", "");
        b.channelId = parseLong(cfg.getString("discord.channel-id", "0"));
        b.initialFetchLimit = clamp(cfg.getInt("discord.initial-fetch-limit", 200), 1, 500);

        b.motdMaxLength = clamp(cfg.getInt("motd.max-length", 90), 10, 500);
        b.fallbackMotd = cfg.getString("motd.fallback", "&aA Minecraft Server");
        b.quoteColor = cfg.getString("motd.quote-color", "&f");
        b.attributionColor = cfg.getString("motd.attribution-color", "&7");

        b.ignoreBots = cfg.getBoolean("filtering.ignore-bots", true);
        b.ignoreAttachmentsOnly = cfg.getBoolean("filtering.ignore-attachments-only", true);
        b.ignoreImageOnly = cfg.getBoolean("filtering.ignore-image-only", true);
        b.requireQuoteMarks = cfg.getBoolean("filtering.require-quote-marks", true);
        b.minLength = Math.max(0, cfg.getInt("filtering.min-length", 2));

        ConfigurationSection aliasSection = cfg.getConfigurationSection("aliases");
        if (aliasSection != null) {
            for (String key : aliasSection.getKeys(false)) {
                String value = aliasSection.getString(key);
                if (value != null && !value.isBlank()) {
                    b.aliases.put(key.toLowerCase(Locale.ROOT), value);
                }
            }
        }
        return new PluginConfig(b);
    }

    public boolean isValid() {
        return discordToken != null
                && !discordToken.isBlank()
                && !"PUT_YOUR_BOT_TOKEN_HERE".equals(discordToken)
                && channelId > 0L;
    }

    public Optional<String> aliasFor(String discordName) {
        if (discordName == null) return Optional.empty();
        return Optional.ofNullable(aliases.get(discordName.toLowerCase(Locale.ROOT)));
    }

    public String discordToken()            { return discordToken; }
    public long channelId()                 { return channelId; }
    public int initialFetchLimit()          { return initialFetchLimit; }
    public int motdMaxLength()              { return motdMaxLength; }
    public String fallbackMotd()            { return fallbackMotd; }
    public String quoteColor()              { return quoteColor; }
    public String attributionColor()        { return attributionColor; }
    public boolean ignoreBots()             { return ignoreBots; }
    public boolean ignoreAttachmentsOnly()  { return ignoreAttachmentsOnly; }
    public boolean ignoreImageOnly()        { return ignoreImageOnly; }
    public boolean requireQuoteMarks()      { return requireQuoteMarks; }
    public int minLength()                  { return minLength; }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static final class Builder {
        String discordToken;
        long channelId;
        int initialFetchLimit;
        int motdMaxLength;
        String fallbackMotd;
        String quoteColor;
        String attributionColor;
        boolean ignoreBots;
        boolean ignoreAttachmentsOnly;
        boolean ignoreImageOnly;
        boolean requireQuoteMarks;
        int minLength;
        Map<String, String> aliases = new TreeMap<>();
    }
}
