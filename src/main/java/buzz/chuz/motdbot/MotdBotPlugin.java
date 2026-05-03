package buzz.chuz.motdbot;

import buzz.chuz.motdbot.config.PluginConfig;
import buzz.chuz.motdbot.discord.DiscordClient;
import buzz.chuz.motdbot.listener.PingListener;
import buzz.chuz.motdbot.quotes.QuoteCache;
import buzz.chuz.motdbot.quotes.QuoteFormatter;
import buzz.chuz.motdbot.quotes.QuoteParser;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class MotdBotPlugin extends JavaPlugin {

    private DiscordClient discord;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        PluginConfig config = PluginConfig.from(getConfig());
        if (!config.isValid()) {
            getLogger().severe("Discord token or channel ID is missing. Edit plugins/MotdBot/config.yml and reload.");
            return;
        }

        QuoteCache cache = new QuoteCache();
        QuoteFormatter formatter = new QuoteFormatter(config);
        QuoteParser parser = new QuoteParser(config);

        Bukkit.getPluginManager().registerEvents(new PingListener(cache, formatter), this);

        discord = new DiscordClient(config, cache, parser, getLogger(), name -> {
            if (name == null) {
                getLogger().warning("Discord ready but channel " + config.channelId() + " was not found.");
            } else {
                getLogger().info("MotdBot ready: serving " + cache.size() + " quotes from #" + name);
            }
        });

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                discord.start();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                getLogger().warning("Discord startup interrupted.");
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Failed to start Discord client", e);
            }
        });
    }

    @Override
    public void onDisable() {
        if (discord != null) {
            getLogger().info("Shutting down JDA…");
            discord.shutdown();
            discord = null;
        }
    }
}
