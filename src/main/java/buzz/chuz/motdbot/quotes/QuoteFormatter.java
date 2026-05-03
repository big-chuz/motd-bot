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
 *
 * Color treatment:
 *   - Quote text is bolded and either painted a single random palette color or
 *     rendered as per-character rainbow.
 *   - Attribution text (the trailing " -Author" segment) is always painted in
 *     {@code motd.attribution-color} regardless of whether the quote went
 *     rainbow or solid, so the speaker stays visually distinct.
 *
 * Note: in legacy MOTDs a color code resets all formatting, so bold (§l) must be
 * re-emitted after every color change — and line breaks reset formatting too,
 * so the start of line two gets its own prefix.
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
        String quotePart = "\"" + body + "\"";
        String attribPart = quote.attribution().map(a -> " -" + a).orElse("");
        String raw = quotePart + attribPart;

        String truncated = truncate(raw, config.motdMaxLength());
        // Where the attribution starts in the *truncated* string, clamped if
        // truncation cut the attribution off entirely.
        int attribStart = Math.min(quotePart.length(), truncated.length());

        // wrapTwoLines is a 1-char-for-1-char substitution (' ' → '\n'), so
        // indices in the wrapped string still line up with the truncated one.
        String wrapped = wrapTwoLines(truncated, Math.max(10, config.motdMaxLength() / 2));
        return paint(wrapped, attribStart);
    }

    public String fallback() {
        return translate(config.fallbackMotd());
    }

    private String paint(String text, int attribStart) {
        boolean rainbow = config.rainbowChance() > 0.0
                && ThreadLocalRandom.current().nextDouble() < config.rainbowChance();

        String palette = config.palette();
        if (palette.isEmpty()) palette = "f";

        // Pick the solid quote color up front so both lines share it in solid mode.
        String solidQuotePrefix;
        if (config.randomColor()) {
            solidQuotePrefix = SECTION + String.valueOf(palette.charAt(
                    ThreadLocalRandom.current().nextInt(palette.length())));
        } else {
            solidQuotePrefix = translate(config.quoteColor());
        }
        String attributionPrefix = translate(config.attributionColor());
        String bold = boldPrefix();

        int rainbowIdx = ThreadLocalRandom.current().nextInt(palette.length());
        StringBuilder out = new StringBuilder(text.length() * 4);
        boolean atLineStart = true;
        boolean inAttribution = false;

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\n') {
                out.append('\n');
                atLineStart = true;
                continue;
            }
            boolean nowInAttrib = i >= attribStart;
            boolean needsPrefix = atLineStart
                    || nowInAttrib != inAttribution
                    || (rainbow && !nowInAttrib);

            if (needsPrefix) {
                if (nowInAttrib) {
                    out.append(attributionPrefix).append(bold);
                } else if (rainbow) {
                    out.append(SECTION)
                       .append(palette.charAt(rainbowIdx % palette.length()))
                       .append(bold);
                    if (!Character.isWhitespace(ch)) rainbowIdx++;
                } else {
                    out.append(solidQuotePrefix).append(bold);
                }
            }

            out.append(ch);
            atLineStart = false;
            inAttribution = nowInAttrib;
        }
        return out.toString();
    }

    private String boldPrefix() {
        return config.bold() ? BOLD : "";
    }

    private static String truncate(String s, int max) {
        if (s.length() <= max) return s;
        if (max <= 1) return String.valueOf(ELLIPSIS);
        return s.substring(0, max - 1).stripTrailing() + ELLIPSIS;
    }

    /** Break {@code s} into two lines at a space near the middle. */
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
        return s;
    }

    private static String translate(String s) {
        return s == null ? "" : ChatColor.translateAlternateColorCodes('&', s);
    }
}
