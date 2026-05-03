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

    public void remove(String id) {
        byId.remove(id);
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
