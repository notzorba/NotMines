<div align="center">

# Mines

Simple, server-side Mines for Paper with Vault economy support.

[![GitHub](https://img.shields.io/badge/GitHub-NotMines-181717?style=for-the-badge&logo=github)](https://github.com/notzorba/NotMines)
[![Paper API](https://img.shields.io/badge/Paper%20API-1.20.6-white?style=for-the-badge&logo=papermc&logoColor=black)](https://papermc.io/)
[![Java](https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=openjdk&logoColor=white)](https://adoptium.net/)
[![Vault](https://img.shields.io/badge/Vault-Required-4caf50?style=for-the-badge)](https://www.spigotmc.org/resources/vault.34315/)
[![License](https://img.shields.io/github/license/notzorba/NotMines?style=for-the-badge)](https://github.com/notzorba/NotMines/blob/main/LICENSE)

</div>

## About

This is a Paper plugin for a clean, casino-style Mines game.

The goal with this project is pretty simple: make Mines feel good in game. The board is handled server-side, payouts are based on real odds with a configurable house edge, bets can use formats like `1k` or `1.5m`, and admins can tweak limits without restarting the server.

It is meant to be easy to drop into a server, easy to configure, and not annoying to maintain.

It is built against the Paper `1.20.6` API for broad compatibility, and is intended to run on current stable Paper releases as well.

## What It Does

- Opens a 5x5 Mines GUI for each round
- Uses Vault for taking bets and paying out winnings
- Supports compact number inputs like `1k`, `1.1k`, `1m`, `1.4m`, and `1b`
- Lets admins change live limits in game
- Reloads `config.yml`, `messages.yml`, and `gui.yml` with `/mines reload`
- Includes a `/minestop` leaderboard GUI with player-head entries, stat filters, and pagination
- Plays configurable GUI sound cues for board open, safe picks, mine hits, cashouts, and full-board clears
- Announces big wins globally when they pass a configurable multiplier
- Saves player stats to SQLite
- Keeps a small pending stats journal so reloads and unloads are less likely to lose progress
- Merges new bundled YAML keys into existing files without overwriting server-specific values
- Reveals the mine locations after a loss or cashout
- Uses `SecureRandom` for mine placement
- Registers anonymous bStats usage metrics

## Commands

- `/mines <bet> <mines>` starts a new board
- `/mines cashout` cashes out your current board
- `/mines reopen` reopens your current board
- `/mines stats [player]` shows your stats, or another player's with permission
- `/minestop` opens the mines leaderboard GUI
- `/mines limits` shows the current live limits
- `/mines limits <min-bet|max-bet|min-mines|max-mines> <value>` changes limits in game
- `/mines reload` reloads config, GUI, and messages

Aliases: `/minegame`, `/mtop`

Examples:

- `/mines 1000 3`
- `/mines 1k 5`
- `/mines 1.5m 10`
- `/mines 2b 24`

## Permissions

| Permission | Use |
| --- | --- |
| `notmines.use` | Start games, cash out, reopen, and view your own stats |
| `notmines.stats.others` | View another player's stats |
| `notmines.admin` | Change limits and reload the plugin |

## Setup

You will need:

- Java 21
- Paper
- [Vault](https://www.spigotmc.org/resources/vault.34315/)
- Any Vault-compatible economy plugin

Optional:

- [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) for stats placeholders and leaderboard integrations

This repo is currently built against the Paper `1.20.6` API, so that is the baseline target to test against first rather than a claim that only one exact version works. In practice, the goal is compatibility with modern stable Paper versions, not just `1.20.6`.

Drop the jar into `plugins/`, start the server once, and the plugin will generate its files.

Main files:

- `plugins/NotMines/config.yml`
- `plugins/NotMines/messages.yml`
- `plugins/NotMines/gui.yml`

If Vault or an economy plugin is missing, the plugin disables itself on startup instead of half-working.

On startup and reload, NotMines can merge newly added default keys into those YAML files so updates can add fresh options without wiping server-specific settings.

## bStats

NotMines registers with bStats using plugin ID `30856`.

No plugin-specific NotMines config is required for that registration.

Server owners can still opt out globally through the shared `plugins/bStats/config.yml` file that bStats uses.

When enabled, NotMines reports standard bStats platform data plus a few plugin-specific charts:

- `economy_provider`
- `placeholderapi_enabled`
- `announcements_enabled`
- `house_edge_bucket`

The metrics hook is initialized during startup, refreshed on `/mines reload`, and shut down cleanly when the plugin disables.

The vendored bStats source and its MIT notice are documented in `THIRD_PARTY_NOTICES.md`.

## Leaderboard

`/minestop` opens a dedicated mines leaderboard GUI for players.

- It uses player-head entries with stat-aware lore for each ranked player
- A hopper filter cycles between tracked stats like profit, wagered, paid out, wins, win rate, and tiles cleared
- Pagination supports larger boards without cramming every result into one screen
- The menu includes a personal summary head so players can see their own rank for the active filter
- `/mtop` is available as a shorthand alias

## PlaceholderAPI

If PlaceholderAPI is installed, NotMines registers the `%notmines_...%` placeholder set automatically.

Player stat placeholders:

- `%notmines_games%`
- `%notmines_wins%`
- `%notmines_losses%`
- `%notmines_tiles_cleared%`
- `%notmines_win_rate%`
- `%notmines_total_wagered%`
- `%notmines_total_paid%`
- `%notmines_profit%`
- `%notmines_best_cashout%`
- `%notmines_biggest_bet%`

For leaderboard plugins such as Topper, use the raw numeric variants for money values so sorting stays numeric instead of lexicographic:

- `%notmines_total_wagered_raw%`
- `%notmines_total_paid_raw%`
- `%notmines_profit_raw%`
- `%notmines_best_cashout_raw%`
- `%notmines_biggest_bet_raw%`

If you want integer minor-unit values instead, these are also available:

- `%notmines_total_wagered_minor%`
- `%notmines_total_paid_minor%`
- `%notmines_profit_minor%`
- `%notmines_best_cashout_minor%`
- `%notmines_biggest_bet_minor%`

The placeholder expansion uses the in-memory stats cache and only schedules background cache fills when needed, so it does not perform synchronous database queries on the server thread.

## Config Notes

The default config is small on purpose.

- `limits.min-bet` and `limits.max-bet` accept values like `100`, `1k`, `1m`, or `2b`
- `gameplay.house-edge` controls the edge applied to payouts
- `announcements.min-multiplier` decides when a win gets broadcast globally
- `stats.save-interval-seconds` controls how often pending stats are flushed
- `gui.yml` contains both the board layout and the sound theme used by the GUI
- New default keys in `config.yml`, `messages.yml`, and `gui.yml` can be merged into existing files automatically during startup or reload

### GUI sound config

The `sounds` section in `plugins/NotMines/gui.yml` controls the feel of the board.

- `sounds.enabled` toggles GUI sounds on or off
- `board-open`, `safe-pick`, `mine-hit`, `cashout`, and `board-cleared` each accept one or more layered sound entries
- Each sound entry supports a `sound` field using a Bukkit/Paper `Sound` enum name
- Each sound entry supports `volume`, `pitch`, and optional `delay-ticks` values
- Safe picks slightly increase pitch as a streak grows, so repeated successful clicks feel more rewarding by default

If you want a different vibe, you can swap the sound names and retune the volume and pitch without rebuilding the plugin.

Player stats are stored in:

- `plugins/NotMines/data/stats.db`
- `plugins/NotMines/data/pending-stats.yml`

## Building

```powershell
.\gradlew.bat build
```

or

```bash
./gradlew build
```

The built jar ends up in `build/libs/`.

Local builds default to the version `dev-SNAPSHOT`. If you want a versioned local jar, pass the release version explicitly:

```bash
./gradlew build -PreleaseVersion=1.0.1
```

## Releasing

Releases are tag-driven, so you do not need to edit `build.gradle.kts` every time.

Push a tag like `v1.0.1`:

```bash
git tag v1.0.1
git push origin v1.0.1
```

GitHub Actions will:

- build the plugin with the matching Gradle version `1.0.1`
- create a GitHub Release for `v1.0.1`
- upload `NotMines-1.0.1.jar` from `build/libs/`

## Contributing

Bug reports, balance suggestions, and pull requests are all welcome.

If you are changing gameplay, it helps a lot to include what behavior changed from the player's point of view, not just the code side.

## License

This project is licensed under the GNU GPL v3. See [LICENSE](LICENSE).
