package buzz.chuz.motdbot.quotes;

import buzz.chuz.motdbot.config.PluginConfig;
import org.bukkit.ChatColor;

import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

/**
 * Renders a {@link Quote} as a single logical string in the form
 *
 *   "quote text" -Author
 *
 * which is then wrapped across the two MOTD lines if it's too long for one.
 * The whole rendered string is bolded and either painted a single random palette
 * color or rendered as per-character rainbow.
 *
 * Note: in legacy MOTDs a color code resets all formatting, so bold (§l) must be
 * re-emitted after every color change — and line breaks reset formatting too,
 * so the second line gets its own color+bold prefix in solid mode.
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
        String body = WHITESPACE.matcher(quote.text()).replaceAll(" ").strip();
        StringBuilder display = new StringBuilder().append('"').append(body).append('"');
        if (quote.attribution().isPresent()) {
            display.append(" -").append(quote.attribution().get());
        }

        String truncated = truncate(display.toString(), config.motdMaxLength());
        String wrapped = wrapTwoLines(truncated, Math.max(10, config.motdMaxLength() / 2));
        return paint(wrapped);
    }

    public String fallback() {
        return translate(config.fallbackMotd());
    }

    private String paint(String text) {
        boolean rainbow = config.rainbowChance() > 0.0
                && ThreadLocalRandom.current().nextDouble() < config.rainbowChance();
        if (rainbow) {
            return rainbow(text);
        }

        String prefix;
        if (config.randomColor()) {
            prefix = SECTION + String.valueOf(pickFromPalette()) + boldPrefix();
        } else {
            prefix = translate(config.quoteColor()) + boldPrefix();
        }

        // Color/format resets at line break, so re-prefix line two as well.
        int nl = text.indexOf('\n');
        if (nl < 0) {
            return prefix + text;
        }
        return prefix + text.substring(0, nl) + "\n" + prefix + text.substring(nl + 1);
    }

    private String rainbow(String text) {
        String palette = config.palette();
        if (palette.isEmpty()) palette = "f";
        StringBuilder sb = new StringBuilder(text.length() * 4);
        int idx = ThreadLocalRandom.current().nextInt(palette.length());
        String bold = boldPrefix();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\n') {
                sb.append('\n');
                continue;
            }
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

    /**
     * Break {@code s} into two lines at a space near the middle. If it already
     * fits inside {@code perLineSoftMax} characters we leave it as-is.
     */
    private static String wrapTwoLines(String s, int perLineSoftMax) {
        if (s.length() <= perLineSoftMax) return s;
        int target = s.length() / 2;
        int searchRadius = Math.min(s.length() - target, target);
        for (int delta = 0; delta <= searchRadius; delta++) {
            int leftIdx = target - delta;
            int rightIdx = target + delta;
            if (leftIdx > 0 && s.charAt(leftIdx) == ' ') {
                return s.substring(0, leftIdx) + "\n" + s.substring(leftIdx + 1);
            }
            if (rightIdx < s.length() && s.charAt(rightIdx) == ' ') {
                return s.substring(0, rightIdx) + "\n" + s.substring(rightIdx + 1);
            }
        }
        // No space found — leave as one line; client will truncate visually.
        return s;
    }

    private static String translate(String s) {
        return s == null ? "" : ChatColor.translateAlternateColorCodes('&', s);
    }
}
