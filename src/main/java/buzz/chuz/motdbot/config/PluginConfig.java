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
    private final boolean bold;
    private final boolean randomColor;
    private final double rainbowChance;
    private final String palette;

    private final boolean ignoreBots;
    private final boolean ignoreAttachmentsOnly;
    private final boolean ignoreImageOnly;
    private final boolean requireQuoteMarks;
    private final boolean allowMultiSegment;
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
        this.bold = b.bold;
        this.randomColor = b.randomColor;
        this.rainbowChance = b.rainbowChance;
        this.palette = b.palette;
        this.ignoreBots = b.ignoreBots;
        this.ignoreAttachmentsOnly = b.ignoreAttachmentsOnly;
        this.ignoreImageOnly = b.ignoreImageOnly;
        this.requireQuoteMarks = b.requireQuoteMarks;
        this.allowMultiSegment = b.allowMultiSegment;
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
        b.bold = cfg.getBoolean("motd.bold", true);
        b.randomColor = cfg.getBoolean("motd.random-color", true);
        b.rainbowChance = clamp01(cfg.getDouble("motd.rainbow-chance", 0.15));
        String pal = cfg.getString("motd.palette", "c6eab9d5");
        b.palette = pal == null || pal.isBlank() ? "f" : pal;

        b.ignoreBots = cfg.getBoolean("filtering.ignore-bots", true);
        b.ignoreAttachmentsOnly = cfg.getBoolean("filtering.ignore-attachments-only", true);
        b.ignoreImageOnly = cfg.getBoolean("filtering.ignore-image-only", true);
        b.requireQuoteMarks = cfg.getBoolean("filtering.require-quote-marks", true);
        b.allowMultiSegment = cfg.getBoolean("filtering.allow-multi-segment", false);
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
    public boolean bold()                   { return bold; }
    public boolean randomColor()            { return randomColor; }
    public double rainbowChance()           { return rainbowChance; }
    public String palette()                 { return palette; }
    public boolean ignoreBots()             { return ignoreBots; }
    public boolean ignoreAttachmentsOnly()  { return ignoreAttachmentsOnly; }
    public boolean ignoreImageOnly()        { return ignoreImageOnly; }
    public boolean requireQuoteMarks()      { return requireQuoteMarks; }
    public boolean allowMultiSegment()      { return allowMultiSegment; }
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

    private static double clamp01(double v) {
        if (Double.isNaN(v)) return 0.0;
        return Math.max(0.0, Math.min(1.0, v));
    }

    private static final class Builder {
        String discordToken;
        long channelId;
        int initialFetchLimit;
        int motdMaxLength;
        String fallbackMotd;
        String quoteColor;
        String attributionColor;
        boolean bold;
        boolean randomColor;
        double rainbowChance;
        String palette;
        boolean ignoreBots;
        boolean ignoreAttachmentsOnly;
        boolean ignoreImageOnly;
        boolean requireQuoteMarks;
        boolean allowMultiSegment;
        int minLength;
        Map<String, String> aliases = new TreeMap<>();
    }
}
