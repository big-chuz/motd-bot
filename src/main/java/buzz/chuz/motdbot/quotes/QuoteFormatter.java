package buzz.chuz.motdbot.quotes;

import buzz.chuz.motdbot.config.PluginConfig;
import org.bukkit.ChatColor;

import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

/**
 * Renders a {@link Quote} into the standardized two-line MOTD form:
 *
 *   "quote text"
 *   -Author
 *
 * Quote line is bolded and either painted a random palette color or rendered as
 * per-character rainbow. Attribution line uses the configured attribution color
 * and matching boldness.
 *
 * Note: in legacy MOTDs a color code resets all formatting, so bold (§l) must
 * be re-emitted after every color change.
 */
public final class QuoteFormatter {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final char ELLIPSIS = '…';
    private static final char SECTION = '§';
    private static final String BOLD = SECTION + "l";

    private final PluginConfig config;

    public QuoteFormatter(PluginConfig config) {
        this.config = config;
    }

    public String format(Quote quote) {
        int perLineMax = Math.max(10, config.motdMaxLength() / 2);

        String body = WHITESPACE.matcher(quote.text()).replaceAll(" ").strip();
        String quoteText = "\"" + truncate(body, perLineMax - 2) + "\"";

        String quoteLine = paintQuote(quoteText);

        StringBuilder out = new StringBuilder(quoteLine);
        if (quote.attribution().isPresent()) {
            String name = truncate(quote.attribution().get(), perLineMax - 1);
            out.append('\n')
               .append(translate(config.attributionColor()))
               .append(boldPrefix())
               .append('-')
               .append(name);
        }
        return out.toString();
    }

    public String fallback() {
        return translate(config.fallbackMotd());
    }

    private String paintQuote(String text) {
        boolean rainbow = config.rainbowChance() > 0.0
                && ThreadLocalRandom.current().nextDouble() < config.rainbowChance();
        if (rainbow) {
            return rainbow(text);
        }
        if (config.randomColor()) {
            char c = pickFromPalette();
            return SECTION + String.valueOf(c) + boldPrefix() + text;
        }
        return translate(config.quoteColor()) + boldPrefix() + text;
    }

    private String rainbow(String text) {
        String palette = config.palette();
        if (palette.isEmpty()) palette = "f";
        StringBuilder sb = new StringBuilder(text.length() * 4);
        int idx = ThreadLocalRandom.current().nextInt(palette.length());
        String bold = boldPrefix();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            sb.append(SECTION).append(palette.charAt(idx % palette.length())).append(bold).append(ch);
            // Don't waste a color step on whitespace — keeps the rainbow visible across short words.
            if (!Character.isWhitespace(ch)) idx++;
        }
        return sb.toString();
    }

    private char pickFromPalette() {
        String palette = config.palette();
        if (palette.isEmpty()) return 'f';
        return palette.charAt(ThreadLocalRandom.current().nextInt(palette.length()));
    }

    private String boldPrefix() {
        return config.bold() ? BOLD : "";
    }

    private static String truncate(String s, int max) {
        if (s.length() <= max) return s;
        if (max <= 1) return String.valueOf(ELLIPSIS);
        return s.substring(0, max - 1).stripTrailing() + ELLIPSIS;
    }

    private static String translate(String s) {
        return s == null ? "" : ChatColor.translateAlternateColorCodes('&', s);
    }
}
