package buzz.chuz.motdbot.quotes;

import java.util.Optional;

public record Quote(String id, String text, Optional<String> attribution) {
}
