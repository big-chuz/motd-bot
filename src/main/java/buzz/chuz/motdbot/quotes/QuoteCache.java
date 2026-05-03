package buzz.chuz.motdbot.quotes;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class QuoteCache {

    private final ConcurrentHashMap<String, Quote> byId = new ConcurrentHashMap<>();

    public void put(Quote quote) {
        byId.put(quote.id(), quote);
    }

    /** Re-write the attribution on an existing cached quote, if it's still present. */
    public void setAttribution(String id, java.util.Optional<String> attribution) {
        byId.computeIfPresent(id, (k, existing) ->
                new Quote(existing.id(), existing.text(), attribution));
    }

    public void remove(String id) {
        byId.remove(id);
    }

    public Optional<Quote> get(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public void clear() {
        byId.clear();
    }

    public int size() {
        return byId.size();
    }

    public Optional<Quote> randomQuote() {
        if (byId.isEmpty()) {
            return Optional.empty();
        }
        List<Quote> snapshot = List.copyOf(byId.values());
        if (snapshot.isEmpty()) {
            return Optional.empty();
        }
        int idx = ThreadLocalRandom.current().nextInt(snapshot.size());
        return Optional.of(snapshot.get(idx));
    }
}
