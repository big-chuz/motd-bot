# MotdBot — Briefing for Claude Cowork

You are being asked to install a Paper plugin called **MotdBot** on the user's
running Minecraft server. You already have context for the server's running
state (install path, how to start/stop it, the contents of `plugins/`, etc.).

This document tells you what the plugin is and the exact steps to install it.

---

## A. What this plugin does

**MotdBot** pulls quotes from a Discord channel and serves a random one as the
server's MOTD on every server-list ping.

- It is a single self-contained Paper/Spigot plugin (Java, JDK 21).
- It embeds the Discord client (JDA 5) — no external service, no RCON, no
  file-watching. One JVM, one process, one jar.
- On enable it connects to Discord, fetches the most recent ~200 messages from
  one configured channel, and stores them in memory.
- It listens for new / edited / deleted messages and updates the cache live.
- On every `PaperServerListPingEvent` it picks a random cached quote and calls
  `event.setMotd(...)` with a two-line formatted string.

### Quote parsing rules

The plugin parses raw Discord message text into the standardized form

```
"quote text"
-Author
```

regardless of how it was written in Discord. The parser handles:

- `"quote" -name`
- `"quote" - @name` (Discord mention attribution)
- `"quote"\n-name`
- `"quote 1"\n"quote 2"\n-name` (multi-line dialog → joined with ` / `)
- Smart quotes (`“ ”`), em/en dashes (`— –`)
- Custom emoji `<:name:id>` are stripped
- URLs are replaced with `[link]`

Messages without quote marks (e.g. `What`) are skipped when
`filtering.require-quote-marks: true` (default), as are image-only messages.

### Author aliases

`config.yml` has an `aliases:` section mapping Discord display names
(case-insensitive) to friendly names used in the attribution line. Anything not
listed falls back to the raw Discord name.

### Files inside the jar

The jar is a fat-jar built with Shadow. JDA and its transitive deps
(jackson, okhttp, slf4j, etc.) are relocated under
`buzz.chuz.motdbot.shaded.*` to avoid clashes with other plugins.

---

## B. How to install it on the server

### Inputs you need from the user

Ask for these if not already known:

1. The compiled jar — at `C:\Users\cadair\Documents\motd-bot\build\libs\motd-bot-0.1.0.jar`
   on the user's dev machine. They will hand it to you (or you'll fetch it from
   wherever they uploaded it).
2. The Discord bot token (regenerated after the previous one was leaked — they
   should already have a fresh one).
3. The channel ID for `quotes-with-no-proof`.

### Pre-flight checks

- Confirm the server is **Paper** (not vanilla, not Forge/Fabric). Look at
  `version.json` or run `version` in the console — the brand should say "Paper".
  If it isn't Paper, stop and tell the user — the plugin uses
  `PaperServerListPingEvent` and won't work on plain Bukkit/Spigot.
- Confirm Paper is on **API 1.21+**. The plugin's `plugin.yml` declares
  `api-version: '1.21'`. Older Paper will refuse to load it.
- Confirm the server is running on Java 21 or newer (the plugin is compiled
  for `--release 21`).

### Steps

1. **Stop the Minecraft server cleanly.** Use the `stop` console command (or
   whatever wrapper the user uses — systemd, screen, tmux, Docker, etc.).
   Do NOT kill -9; that can corrupt region files.

2. **Drop the jar into `plugins/`.** Path is `<server-root>/plugins/motd-bot-0.1.0.jar`.
   Verify the file landed and is not zero bytes.

3. **First start — generate the config.** Start the server. MotdBot will load,
   notice the empty token, log a warning like:

   > Discord token or channel ID is missing. Edit plugins/MotdBot/config.yml and reload.

   This is expected. Confirm the file `plugins/MotdBot/config.yml` now exists.

4. **Stop the server again.**

5. **Fill in the config.** Edit `plugins/MotdBot/config.yml`. Set:
   - `discord.token` — the bot token. Quote it.
   - `discord.channel-id` — the channel ID as a string. Quote it (it's a
     17–19 digit number; YAML sometimes mangles unquoted longs).

   Leave the rest at defaults unless the user asks otherwise. The relevant
   tunables are `motd.max-length`, `motd.fallback`, `motd.quote-color`,
   `motd.attribution-color`, the `filtering.*` flags, and the `aliases:` map.

6. **Start the server.** Watch the log for these lines from `[MotdBot]`:

   - `Connected to Discord as <bot-name>` — the JDA gateway came up.
   - `Cached N quotes from #quotes-with-no-proof (skipped M non-quote messages)`
     — the initial fetch worked. N should be > 0; if N is 0 either the channel
     is empty or the bot can't see it (check guild membership + View Channel
     permission).
   - `MotdBot ready: serving N quotes from #quotes-with-no-proof`.

   If instead you see `Configured channel ID ... is not visible to the bot`,
   the bot isn't in the guild or lacks `View Channel` on that channel. Direct
   the user to fix the invite / permissions.

7. **Smoke test.** Have the user open the Minecraft Multiplayer screen and
   refresh the server entry several times. The MOTD should change to a
   different quote on most refreshes. You won't be able to verify this
   yourself — ask the user to confirm.

### Updating the plugin later

When a new build is delivered:

1. Stop the server.
2. Replace `plugins/motd-bot-<old>.jar` with `motd-bot-<new>.jar`. Do NOT keep
   both — Paper will load both and you'll get duplicate listeners.
3. `plugins/MotdBot/config.yml` is preserved across updates. Only delete it if
   the changelog says config keys changed.
4. Start the server.

### Uninstalling

1. Stop the server.
2. Delete `plugins/motd-bot-<version>.jar` and the `plugins/MotdBot/` folder.
3. Start the server. The MOTD will revert to whatever is in `server.properties`.

---

## What you may NOT do without asking

- Do not commit, push, or upload the bot token anywhere. It belongs only in
  `plugins/MotdBot/config.yml` on the server.
- Do not restart the server during a session where players are online without
  warning them via `/say` or the user's preferred broadcast mechanism.
- Do not modify `server.properties` or other plugins' configs while installing
  MotdBot — it doesn't need them touched.
