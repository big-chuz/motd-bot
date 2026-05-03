package buzz.chuz.motdbot.discord;

import buzz.chuz.motdbot.config.PluginConfig;
import buzz.chuz.motdbot.quotes.ParsedQuote;
import buzz.chuz.motdbot.quotes.Quote;
import buzz.chuz.motdbot.quotes.QuoteCache;
import buzz.chuz.motdbot.quotes.QuoteParser;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class DiscordClient {

    private final PluginConfig config;
    private final QuoteCache cache;
    private final QuoteParser parser;
    private final Logger logger;
    private final Consumer<String> onReady;

    private JDA jda;

    public DiscordClient(PluginConfig config,
                         QuoteCache cache,
                         QuoteParser parser,
                         Logger logger,
                         Consumer<String> onReady) {
        this.config = config;
        this.cache = cache;
        this.parser = parser;
        this.logger = logger;
        this.onReady = onReady;
    }

    public void start() throws InterruptedException {
        EnumSet<GatewayIntent> intents = EnumSet.of(
                GatewayIntent.GUILD_MESSAGES,
                GatewayIntent.MESSAGE_CONTENT
        );

        jda = JDABuilder.create(config.discordToken(), intents)
                .setMemberCachePolicy(MemberCachePolicy.NONE)
                .setChunkingFilter(ChunkingFilter.NONE)
                .disableCache(EnumSet.of(
                        CacheFlag.ACTIVITY,
                        CacheFlag.VOICE_STATE,
                        CacheFlag.EMOJI,
                        CacheFlag.STICKER,
                        CacheFlag.CLIENT_STATUS,
                        CacheFlag.ONLINE_STATUS,
                        CacheFlag.SCHEDULED_EVENTS
                ))
                .addEventListeners(new Listener())
                .build();

        jda.awaitReady();
    }

    public void shutdown() {
        if (jda == null) return;
        try {
            jda.shutdown();
            if (!jda.awaitShutdown(5, TimeUnit.SECONDS)) {
                jda.shutdownNow();
                jda.awaitShutdown(2, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            jda.shutdownNow();
        }
        jda = null;
    }

    private void seedCache() {
        TextChannel channel = jda.getTextChannelById(config.channelId());
        if (channel == null) {
            logger.warning("Configured channel ID " + config.channelId()
                    + " is not visible to the bot. Check the bot is in the guild and has 'View Channel' permission.");
            onReady.accept(null);
            return;
        }

        channel.getHistory().retrievePast(config.initialFetchLimit()).queue(
                messages -> {
                    int added = 0;
                    int skipped = 0;
                    for (Message m : messages) {
                        Optional<Quote> q = tryBuildQuote(m);
                        if (q.isPresent()) {
                            cache.put(q.get());
                            added++;
                        } else {
                            skipped++;
                        }
                    }
                    logger.info("Cached " + added + " quotes from #" + channel.getName()
                            + " (skipped " + skipped + " non-quote messages)");
                    onReady.accept(channel.getName());
                },
                err -> {
                    logger.log(Level.WARNING, "Failed to fetch initial quote history", err);
                    onReady.accept(channel.getName());
                }
        );
    }

    private boolean basicAccept(Message m) {
        if (m.getChannel().getIdLong() != config.channelId()) return false;
        if (config.ignoreBots() && m.getAuthor().isBot()) return false;

        String content = m.getContentRaw();
        if (content == null) content = "";
        boolean hasAttachments = !m.getAttachments().isEmpty();
        boolean blank = content.isBlank();

        if (config.ignoreAttachmentsOnly() && blank && hasAttachments) return false;
        if (config.ignoreImageOnly() && blank && hasAttachments) return false;
        if (content.strip().length() < config.minLength()) return false;
        return true;
    }

    private Optional<Quote> tryBuildQuote(Message m) {
        if (!basicAccept(m)) return Optional.empty();
        ParsedQuote parsed = parser.parse(m.getContentDisplay());
        if (!parsed.isUsable()) return Optional.empty();
        return Optional.of(new Quote(m.getId(), parsed.quote(), parsed.attribution()));
    }

    private final class Listener extends ListenerAdapter {
        @Override
        public void onReady(@NotNull ReadyEvent event) {
            logger.info("Connected to Discord as " + event.getJDA().getSelfUser().getName());
            seedCache();
        }

        @Override
        public void onMessageReceived(@NotNull MessageReceivedEvent event) {
            tryBuildQuote(event.getMessage()).ifPresent(cache::put);
        }

        @Override
        public void onMessageUpdate(@NotNull MessageUpdateEvent event) {
            Message m = event.getMessage();
            if (m.getChannel().getIdLong() != config.channelId()) return;
            Optional<Quote> q = tryBuildQuote(m);
            if (q.isPresent()) {
                cache.put(q.get());
            } else {
                cache.remove(m.getId());
            }
        }

        @Override
        public void onMessageDelete(@NotNull MessageDeleteEvent event) {
            if (event.getChannel().getIdLong() != config.channelId()) return;
            cache.remove(event.getMessageId());
        }
    }
}
