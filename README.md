# MotdBot

Paper plugin that pulls messages from a Discord channel (e.g. `quotes`)
and shows a random one as the server MOTD on every server-list ping.

## Build

Requires JDK 21 and Gradle.

First time only — generate the Gradle wrapper:

```
gradle wrapper
```

Then build the plugin:

```
./gradlew shadowJar
```

The plugin jar lands at `build/libs/motd-bot-0.1.0.jar`.

## Discord bot setup

1. Go to <https://discord.com/developers/applications> and create a new application.
2. Under **Bot**, create a bot user and copy its token.
3. Under **Bot → Privileged Gateway Intents**, enable **Message Content Intent**.
4. Under **OAuth2 → URL Generator**, pick scopes `bot` and permissions
   `View Channel` + `Read Message History`. Open the generated URL and invite the
   bot to your Discord server.
5. In Discord, enable Developer Mode (Settings → Advanced), right-click the
   `quotes` channel, and choose **Copy Channel ID**.

## Install

1. Drop `motd-bot-0.1.0.jar` into your Paper server's `plugins/` directory.
2. Start the server once. It will create `plugins/MotdBot/config.yml`.
3. Edit the config, filling in `discord.token` and `discord.channel-id`.
4. Restart the server (or run `/reload confirm`).

## Config reference

```yaml
discord:
  token: "..."                 # bot token from the Developer Portal
  channel-id: "123..."         # channel ID for quotes-with-no-proof
  initial-fetch-limit: 200     # how many recent messages to load on startup

motd:
  max-length: 90               # truncate longer quotes
  include-author: true         # prefix line with "<name>:"
  fallback: "&aA Minecraft Server"
  color: "&f"                  # base color for quote text
  author-color: "&7"           # color for "<name>:" prefix

filtering:
  ignore-bots: true
  ignore-attachments-only: true
  min-length: 2
```

Color codes use the standard `&` prefix (see Bukkit's `ChatColor`).

## How it works

- On startup the plugin connects to Discord with JDA, fetches the most recent
  messages from the configured channel, and stores them in memory.
- It listens for new, edited, and deleted messages and updates the cache live.
- On every `PaperServerListPingEvent`, it picks a random cached quote, strips
  Discord markdown, truncates and wraps it across the two MOTD lines, and sets
  it on the response.
- If the cache is empty (e.g. Discord is unreachable on first start), it serves
  the configured fallback MOTD instead.
