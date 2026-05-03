package buzz.chuz.motdbot.listener;

import buzz.chuz.motdbot.quotes.Quote;
import buzz.chuz.motdbot.quotes.QuoteCache;
import buzz.chuz.motdbot.quotes.QuoteFormatter;
import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.Optional;

public final class PingListener implements Listener {

    private final QuoteCache cache;
    private final QuoteFormatter formatter;

    public PingListener(QuoteCache cache, QuoteFormatter formatter) {
        this.cache = cache;
        this.formatter = formatter;
    }

    @EventHandler
    public void onPing(PaperServerListPingEvent event) {
        Optional<Quote> quote = cache.randomQuote();
        if (quote.isPresent()) {
            event.setMotd(formatter.format(quote.get()));
        } else {
            event.setMotd(formatter.fallback());
        }
    }
}
