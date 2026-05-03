package buzz.chuz.motdbot.quotes;

import buzz.chuz.motdbot.config.PluginConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses raw Discord message text into {@link ParsedQuote}s with the structure
 *   "quote text"
 *   -Author
 *
 * Handles the common shapes seen in #quotes-with-no-proof:
 *   "quote" -name                                (single line, dash on same line)
 *   "quote" - @name                              (mention attribution)
 *   "quote"\n-name                               (attribution on next line)
 *   "quote 1"\n"quote 2"\n-name                  (multi-line dialog, one author)
 *   "quote" with no attribution                  (attribution becomes empty)
 *   plain text with no quote marks               (rejected when require-quote-marks is on)
 */
public final class QuoteParser {

    // Attribution-only line: "-name", "– name", "— @name", optionally with leading whitespace.
    private static final Pattern ATTRIBUTION_LINE =
            Pattern.compile("^\\s*[-–—]\\s*(.+?)\\s*$");

    // Inline trailing attribution: "...quote\" - name" near the end of the text.
    // We require the dash to be space-separated to avoid eating mid-quote dashes.
    private static final Pattern INLINE_ATTRIBUTION =
            Pattern.compile("\\s+[-–—]\\s+([^\\n]+?)\\s*$");

    private static final char[] OPEN_QUOTES  = {'"', '“', '‟', '«', '‘', '‹'};
    private static final char[] CLOSE_QUOTES = {'"', '”', '„', '»', '’', '›'};

    private static final Pattern QUOTED_SEGMENT = Pattern.compile(
            "[\"“‟«‘‹]([^\"“”‟«»‘’‹›]+)[\"”‟»’›]"
    );

    private final PluginConfig config;

    public QuoteParser(PluginConfig config) {
        this.config = config;
    }

    public ParsedQuote parse(String raw) {
        if (raw == null) return ParsedQuote.empty();
        String text = stripDiscordNoise(raw).strip();
        if (text.isEmpty()) return ParsedQuote.empty();

        List<String> lines = new ArrayList<>(List.of(text.split("\\R")));
        // Trim each line and drop empties so attribution detection works on dialog with blank lines.
        lines.replaceAll(String::strip);
        lines.removeIf(String::isEmpty);
        if (lines.isEmpty()) return ParsedQuote.empty();

        Optional<String> attribution = Optional.empty();

        // 1. Last line is an attribution-only line ("-name").
        String last = lines.get(lines.size() - 1);
        Matcher attrLine = ATTRIBUTION_LINE.matcher(last);
        if (attrLine.matches()) {
            attribution = Optional.of(attrLine.group(1));
            lines.remove(lines.size() - 1);
        } else {
            // 2. Last line ends with inline " - name" attribution.
            Matcher inline = INLINE_ATTRIBUTION.matcher(last);
            if (inline.find() && hasQuoteMarks(last.substring(0, inline.start()))) {
                attribution = Optional.of(inline.group(1));
                String trimmedLast = last.substring(0, inline.start()).stripTrailing();
                lines.set(lines.size() - 1, trimmedLast);
            }
        }

        String quoteBody = joinQuoteLines(lines);
        if (quoteBody.isBlank()) return ParsedQuote.empty();

        if (config.requireQuoteMarks() && !hasQuoteMarks(quoteBody)) {
            return ParsedQuote.empty();
        }

        Optional<String> cleanedAttribution = attribution
                .map(QuoteParser::cleanAttribution)
                .filter(s -> !s.isBlank())
                .map(name -> config.aliasFor(name).orElse(name));

        return new ParsedQuote(stripOuterQuotes(quoteBody), cleanedAttribution);
    }

    private static String joinQuoteLines(List<String> lines) {
        if (lines.isEmpty()) return "";
        // Prefer to use only the actually-quoted segments if any exist; otherwise join all lines.
        StringBuilder quoted = new StringBuilder();
        for (String line : lines) {
            Matcher m = QUOTED_SEGMENT.matcher(line);
            while (m.find()) {
                if (quoted.length() > 0) quoted.append(" / ");
                quoted.append(m.group(1).strip());
            }
        }
        if (quoted.length() > 0) {
            return quoted.toString();
        }
        return String.join(" ", lines).strip();
    }

    private static String cleanAttribution(String raw) {
        String s = raw.strip();
        // Drop leading dashes / @ that survived the regex
        while (!s.isEmpty() && (s.charAt(0) == '-' || s.charAt(0) == '–' || s.charAt(0) == '—' || s.charAt(0) == '@')) {
            s = s.substring(1).strip();
        }
        // Strip surrounding quotes if any
        s = stripOuterQuotes(s);
        // Truncate at any trailing punctuation/comments after the name
        int cut = indexOfAny(s, ',', ';', '\n');
        if (cut >= 0) s = s.substring(0, cut).strip();
        return s;
    }

    private static String stripOuterQuotes(String s) {
        if (s.length() < 2) return s;
        char first = s.charAt(0);
        char last = s.charAt(s.length() - 1);
        if (isOpenQuote(first) && isCloseQuote(last)) {
            return s.substring(1, s.length() - 1).strip();
        }
        return s;
    }

    private static boolean hasQuoteMarks(String s) {
        for (char c : s.toCharArray()) {
            if (isOpenQuote(c) || isCloseQuote(c)) return true;
        }
        return false;
    }

    private static boolean isOpenQuote(char c) {
        for (char q : OPEN_QUOTES) if (q == c) return true;
        return false;
    }

    private static boolean isCloseQuote(char c) {
        for (char q : CLOSE_QUOTES) if (q == c) return true;
        return false;
    }

    private static int indexOfAny(String s, char... chars) {
        for (int i = 0; i < s.length(); i++) {
            for (char c : chars) if (s.charAt(i) == c) return i;
        }
        return -1;
    }

    /** Strip Discord-specific syntax that shouldn't end up in the MOTD. */
    private static String stripDiscordNoise(String s) {
        // Remove custom emoji <:name:id> entirely (looks bad in plain text).
        s = s.replaceAll("<a?:[A-Za-z0-9_]+:\\d+>", "");
        // URLs become "[link]" since they won't fit in the MOTD anyway.
        s = s.replaceAll("https?://\\S+", "[link]");
        return s;
    }
}
