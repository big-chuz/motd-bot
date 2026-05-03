# MotdBot

A Paper plugin that pulls quote-style messages from a Discord channel and
displays a random one as the server MOTD on every server-list ping.

Designed for the kind of channel where friends collect funny things they've
said to each other — `"this is a quote" -name`. The plugin parses that shape
out of arbitrary Discord messages, formats it for the MOTD's two-line layout,
and rotates one onto the server list every time a client refreshes.

---

## Prerequisites

- **JDK 21** (or newer) for both building the plugin and running the Paper server.
  Any Adoptium / Microsoft / Zulu / GraalVM build works.
- **Gradle 8+**. The repo does not ship a Gradle wrapper; bootstrap one yourself
  if you want one (see Build below).
- A **Paper** Minecraft server on **API 1.21+**. Spigot/Bukkit will not work —
  this plugin uses `PaperServerListPingEvent`, which is Paper-specific.
- A **Discord bot** application with the Message Content intent enabled (steps
  below).

---

## Build

```sh
gradle shadowJar
```

The plugin jar lands at `build/libs/motd-bot-<version>.jar`. The build produces
a fat-jar; JDA and its transitive dependencies are relocated under
`buzz.chuz.motdbot.shaded.*` so they don't clash with other plugins.

If you prefer a Gradle wrapper, generate one once:

```sh
gradle wrapper
./gradlew shadowJar
```

---

## Discord bot setup

1. Visit <https://discord.com/developers/applications> and create a new
   application.
2. Under **Bot**, create a bot user and copy its token.
3. Under **Bot → Privileged Gateway Intents**, enable
   **Message Content Intent**.
4. Under **OAuth2 → URL Generator**, pick scope `bot` plus the permissions
   `View Channel` and `Read Message History`. Open the generated URL and invite
   the bot to your Discord server.
5. In Discord, enable Developer Mode (User Settings → Advanced), right-click
   the channel you want quotes pulled from, and choose **Copy Channel ID**.

The bot only needs read access. It never sends messages, never writes, never
reacts.

---

## Install

1. Drop `motd-bot-<version>.jar` into your Paper server's `plugins/` directory.
2. Start the server once. The plugin will create
   `plugins/MotdBot/config.yml` and log a warning that the token is missing —
   that's expected.
3. Stop the server, edit the config, and fill in `discord.token` and
   `discord.channel-id`. Quote both values; channel IDs are 17–19 digit numbers
   that YAML can mishandle without quotes.
4. Start the server again. Watch for log lines like:
   - `Connected to Discord as <bot-name>`
   - `Cached N quotes from #<channel> (skipped M non-quote messages)`
5. Open the Multiplayer screen in-game and refresh the server entry — the
   MOTD should change to a random quote on each refresh.

---

## Quote parsing

The parser tries to extract a `"quote text" -Author` shape from each message.
Supported input shapes:

| Discord message                                 | Result                          |
| ----------------------------------------------- | ------------------------------- |
| `"this is a quote" -name`                       | quote + attribution             |
| `"this is a quote" - name`                      | quote + attribution             |
| `"this is a quote" -@mention`                   | quote + attribution (resolved)  |
| `"this is a quote"`<br>`-name`                  | quote + attribution             |
| `"this is a quote"`                             | quote, no attribution           |
| `"this is a quote"`<br>(next message: `-name`)  | quote + attribution from next   |
| Plain text without `"…"`                        | rejected                        |
| Image-only message                              | rejected                        |
| Multi-line dialog (multiple `"…"` segments)     | rejected (configurable)         |

Quote characters can be straight (`"`) or smart (`“ ”`). Dashes can be hyphen,
en-dash, or em-dash. Attributions can be plain text or `@mentions` — for
mentions, alias keys can match the user's server nickname, global display name,
or raw username.

If a quote message has no attribution but the *very next* message in the
channel is just an attribution line (e.g. `-bolb`), the plugin pairs them
automatically.

---

## Config reference

`plugins/MotdBot/config.yml` (defaults shown):

```yaml
discord:
  token: ""                      # bot token
  channel-id: "0"                # channel ID
  initial-fetch-limit: 200       # how many recent messages to load on startup

motd:
  max-length: 90                 # truncate the rendered string to this many chars
  fallback: "&aA Minecraft Server"
  quote-color: "&f"              # used only when random-color is false
  attribution-color: "&7"        # color for the " -Author" portion (light grey)
  bold: true                     # bold the entire MOTD
  random-color: true             # pick a random palette color each ping
  rainbow-chance: 0.15           # 0.0–1.0; fraction of pings that go full rainbow
  palette: "c6eab9d5"            # legacy color code chars used by random + rainbow

filtering:
  ignore-bots: true
  ignore-attachments-only: true
  ignore-image-only: true
  require-quote-marks: true      # only cache messages that contain a "..." segment
  allow-multi-segment: false     # reject messages with multiple "..." segments
  min-length: 2

# Map a Discord display name OR username (case-insensitive) to a friendly alias
# used in the attribution. Anything not listed falls back to the raw name.
aliases:
  # "discord_username": "Friendly Name"
```

Color codes use the standard `&` prefix; see Bukkit's `ChatColor`.

---

## How it works

- On enable the plugin starts a JDA gateway connection, fetches the most recent
  messages from the configured channel (paged in 100-message chunks up to
  `initial-fetch-limit`), parses each into a quote, and caches them in memory.
- It listens for new, edited, and deleted messages and updates the cache live.
- On every `PaperServerListPingEvent` it picks a random cached quote, formats
  it as `"quote" -Author` wrapped across the two MOTD lines, applies bold +
  random color (or rainbow), and sets it on the ping response.
- If the cache is empty (e.g. Discord is unreachable at startup), the
  configured fallback MOTD is served instead.

There is no persistence — the cache rebuilds on every plugin start, since
Discord is the source of truth.

---

## Security notes

- `plugins/MotdBot/config.yml` contains your bot token. Treat it like any
  other credential — don't commit it, don't share screenshots of it.
- The `.gitignore` in this repo excludes `plugins/MotdBot/config.yml` and
  common secret-file patterns, but the in-source `src/main/resources/config.yml`
  is a *template* with no real values. Keep it that way.
- If a token leaks, regenerate it at
  <https://discord.com/developers/applications> → your app → Bot →
  **Reset Token**. The old token is invalidated immediately.

---

## License

MIT — see [LICENSE](LICENSE) if present, otherwise treat as MIT.
