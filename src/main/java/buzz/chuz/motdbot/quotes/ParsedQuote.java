package buzz.chuz.motdbot.quotes;

import java.util.Optional;

public record ParsedQuote(String quote, Optional<String> attribution) {
    public static ParsedQuote empty() {
        return new ParsedQuote("", Optional.empty());
    }

    public boolean isUsable() {
        return !quote.isBlank();
    }
}
