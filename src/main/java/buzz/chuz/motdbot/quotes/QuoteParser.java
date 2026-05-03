package buzz.chuz.motdbot.quotes;

import buzz.chuz.motdbot.config.PluginConfig;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;

import java.util.ArrayList;
import java.util.Arrays;
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

    // Inline trailing attribution: "...quote\" - name" or "...quote\" -@name"
    // near the end of the text. Whitespace is required BEFORE the dash so we
    // don't eat mid-word hyphens (e.g. "side-eye"), but the trailing space is
    // optional — Discord users often run the dash straight into an @mention.
    private static final Pattern INLINE_ATTRIBUTION =
            Pattern.compile("\\s+[-–—]\\s?([^\\n]+?)\\s*$");

    private static final char[] OPEN_QUOTES  = {'"', '“', '‟', '«', '‘', '‹'};
    private static final char[] CLOSE_QUOTES = {'"', '”', '„', '»', '’', '›'};

    private static final Pattern QUOTED_SEGMENT = Pattern.compile(
            "[\"“‟«‘‹]([^\"“”‟«»‘’‹›]+)[\"”‟»’›]"
    );

    private final PluginConfig config;

    public QuoteParser(PluginConfig config) {
        this.config = config;
    }

    /**
     * Returns the attribution name if {@code raw} is *only* an attribution line
     * (e.g. {@code "-bolb"}, {@code "— Harri"}). Used to pair the next message
     * after an unattributed quote with that quote's author.
     *
     * Aliases are applied to the result. Prefer {@link #parseAttributionOnly(Message)}
     * when a Message is available so alias keys can match server nicknames OR
     * usernames of any mentioned user.
     */
    public Optional<String> parseAttributionOnly(String raw) {
        return parseAttributionOnlyInternal(raw, null);
    }

    public Optional<String> parseAttributionOnly(Message message) {
        if (message == null) return Optional.empty();
        return parseAttributionOnlyInternal(message.getContentDisplay(), message);
    }

    private Optional<String> parseAttributionOnlyInternal(String raw, Message context) {
        if (raw == null) return Optional.empty();
        String text = stripDiscordNoise(raw).strip();
        if (text.isEmpty() || text.length() > 50) return Optional.empty();
        if (text.indexOf('\n') >= 0) return Optional.empty();
        Matcher m = ATTRIBUTION_LINE.matcher(text);
        if (!m.matches()) return Optional.empty();
        String cleaned = cleanAttribution(m.group(1));
        if (cleaned.isBlank() || cleaned.length() > 30) return Optional.empty();
        return Optional.of(applyAlias(cleaned, context));
    }

    public ParsedQuote parse(String raw) {
        return parseInternal(raw, null);
    }

    public ParsedQuote parse(Message message) {
        if (message == null) return ParsedQuote.empty();
        return parseInternal(message.getContentDisplay(), message);
    }

    private ParsedQuote parseInternal(String raw, Message context) {
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

        JoinedBody joined = joinQuoteLines(lines);
        String quoteBody = joined.body();
        if (quoteBody.isBlank()) return ParsedQuote.empty();

        // Multi-segment dialog (e.g. three back-to-back quoted lines joined with " / ")
        // tends to render as an unreadable mess in the MOTD — reject by default.
        if (!config.allowMultiSegment() && joined.segmentCount() > 1) {
            return ParsedQuote.empty();
        }

        // require-quote-marks must check the ORIGINAL input, not the body that
        // joinQuoteLines returns — because joinQuoteLines strips the quote
        // marks during extraction, so the body never has them. Use the flag
        // joinQuoteLines sets when it actually found and extracted quoted
        // segments.
        if (config.requireQuoteMarks() && !joined.foundQuoteMarks()) {
            return ParsedQuote.empty();
        }

        Optional<String> cleanedAttribution = attribution
                .map(QuoteParser::cleanAttribution)
                .filter(s -> !s.isBlank())
                .map(name -> applyAlias(name, context));

        return new ParsedQuote(stripOuterQuotes(quoteBody), cleanedAttribution);
    }

    /**
     * Looks up an alias for {@code captured}. If a Message context is provided,
     * also tries alias keys against the server nickname / global name / username
     * of every user mentioned in the message. This lets a single alias entry
     * keyed on any one of those forms match all of them.
     */
    private String applyAlias(String captured, Message context) {
        Optional<String> direct = config.aliasFor(captured);
        if (direct.isPresent()) return direct.get();
        if (context != null) {
            for (Member member : context.getMentions().getMembers()) {
                for (String candidate : alternativeNames(member)) {
                    Optional<String> hit = config.aliasFor(candidate);
                    if (hit.isPresent()) return hit.get();
                }
            }
        }
        return captured;
    }

    private static List<String> alternativeNames(Member member) {
        User user = member.getUser();
        return Arrays.asList(
                member.getEffectiveName(),    // server nickname, falls back to global name
                user.getEffectiveName(),      // global display name, falls back to username
                user.getName()                // raw Discord username
        );
    }

    /**
     * Returned from {@link #joinQuoteLines(List)} to tell {@link #parse(String)}
     * both the cleaned body and whether the body was extracted from inside
     * quote marks (so the require-quote-marks check has accurate info).
     */
    private record JoinedBody(String body, boolean foundQuoteMarks, int segmentCount) {}

    private static JoinedBody joinQuoteLines(List<String> lines) {
        if (lines.isEmpty()) return new JoinedBody("", false, 0);
        // Prefer to use only the actually-quoted segments if any exist; otherwise join all lines.
        StringBuilder quoted = new StringBuilder();
        int segments = 0;
        for (String line : lines) {
            Matcher m = QUOTED_SEGMENT.matcher(line);
            while (m.find()) {
                if (quoted.length() > 0) quoted.append(" / ");
                quoted.append(m.group(1).strip());
                segments++;
            }
        }
        if (quoted.length() > 0) {
            return new JoinedBody(quoted.toString(), true, segments);
        }
        // Fallback: no quoted segments matched. Surface whether the original
        // input had ANY quote-mark characters so requireQuoteMarks can still
        // reject obviously-unquoted plain text.
        boolean anyQuoteCharsInOriginal = false;
        for (String line : lines) {
            if (hasQuoteMarks(line)) {
                anyQuoteCharsInOriginal = true;
                break;
            }
        }
        return new JoinedBody(String.join(" ", lines).strip(), anyQuoteCharsInOriginal, 0);
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
