package buzz.chuz.motdbot.quotes;

import buzz.chuz.motdbot.config.PluginConfig;
import org.bukkit.ChatColor;

import java.util.regex.Pattern;

/**
 * Renders a {@link Quote} into the standardized two-line MOTD form:
 *
 *   "quote text"
 *   -Author
 *
 * Both lines are independently truncated so neither overflows the visible MOTD width.
 */
public final class QuoteFormatter {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final char ELLIPSIS = '…';

    private final PluginConfig config;

    public QuoteFormatter(PluginConfig config) {
        this.config = config;
    }

    public String format(Quote quote) {
        int perLineMax = Math.max(10, config.motdMaxLength() / 2);

        String body = WHITESPACE.matcher(quote.text()).replaceAll(" ").strip();
        String quoteLine = "\"" + truncate(body, perLineMax - 2) + "\"";

        StringBuilder out = new StringBuilder();
        out.append(translate(config.quoteColor())).append(quoteLine);

        if (quote.attribution().isPresent()) {
            String name = truncate(quote.attribution().get(), perLineMax - 1);
            out.append('\n')
               .append(translate(config.attributionColor()))
               .append('-')
               .append(name);
        }
        return out.toString();
    }

    public String fallback() {
        return translate(config.fallbackMotd());
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
