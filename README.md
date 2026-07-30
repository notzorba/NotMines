<div align="center">

# NotMines

Simple, server-side Mines for Paper with Vault economy support.

[![GitHub](https://img.shields.io/badge/GitHub-NotMines-181717?style=for-the-badge&logo=github)](https://github.com/notzorba/NotMines)
[![Paper](https://img.shields.io/badge/Paper-1.20.x--26.x-white?style=for-the-badge&logo=papermc&logoColor=black)](https://papermc.io/)
[![Java](https://img.shields.io/badge/Bytecode-Java%2017-orange?style=for-the-badge&logo=openjdk&logoColor=white)](https://adoptium.net/)
[![Vault](https://img.shields.io/badge/Vault-Required-4caf50?style=for-the-badge)](https://www.spigotmc.org/resources/vault.34315/)
[![License](https://img.shields.io/github/license/notzorba/NotMines?style=for-the-badge)](https://github.com/notzorba/NotMines/blob/main/LICENSE)

</div>

## About

This is a Paper plugin for a clean, casino-style Mines game.

The goal with this project is pretty simple: make Mines feel good in game. The board is handled server-side, payouts are based on real odds with a configurable house edge, bets can use formats like `1k` or `1.5m`, and admins can tweak limits without restarting the server.

It is meant to be easy to drop into a server, easy to configure, and not annoying to maintain.

It is compiled against Paper `1.20.1` with Java 17 bytecode and declares the `1.20` API baseline. That keeps the jar loadable across Paper `1.20.x` through the current year-based `26.x` releases without version-specific server internals.

## What It Does

- Opens a 5x5 Mines GUI for each round
- Uses Vault for taking bets and paying out winnings
- Supports compact number inputs like `1k`, `1.1k`, `1m`, `1.4m`, and `1b`
- Lets admins change live limits in game
- Reloads `config.yml`, `messages.yml`, and `gui.yml` with `/mines reload`
- Includes a `/minestop` leaderboard GUI with player-head entries, stat filters, and pagination
- Plays configurable layered sound cues for games and leaderboard navigation
- Announces big wins when they pass configurable multiplier and payout thresholds
- Saves player stats to SQLite
- Keeps a small pending stats journal so reloads and unloads are less likely to lose progress
- Versions and non-destructively updates all three YAML files without overwriting server-specific values
- Prints a concise startup banner with version, author, server, economy, storage, and integration status
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
| `nmines.use` | Start games, cash out, reopen, and view your own stats |
| `nmines.stats.others` | View another player's stats |
| `nmines.admin` | Change limits and reload the plugin |

### Upgrading public identifiers

Current builds use the shorter `nmines` namespace. When upgrading from an older build, update permission assignments from `notmines.*` to `nmines.*` and PlaceholderAPI entries from `%notmines_*%` to `%nmines_*%`. Player stats and customized YAML values are not renamed or reset.

## Setup

You will need:

- Paper `1.20.x` through `26.x`
- The Java version required by that Paper release (the plugin itself uses Java 17 bytecode)
- [Vault](https://www.spigotmc.org/resources/vault.34315/)
- Any Vault-compatible economy plugin

Optional:

- [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) for stats placeholders and leaderboard integrations

The project intentionally compiles against the oldest supported API (`1.20.1`). Paper servers older than the `api-version` declared by a plugin refuse to load it, so `plugin.yml` declares `1.20` instead of a newer point release.

Drop the jar into `plugins/`, start the server once, and the plugin will generate its files.

Main files:

- `plugins/NotMines/config.yml`
- `plugins/NotMines/messages.yml`
- `plugins/NotMines/gui.yml`

If Vault or an economy plugin is missing, the plugin disables itself on startup instead of half-working.

On startup and reload, NotMines compares each file's `config-version`, adds missing defaults and comments, and advances old versions without replacing customized values.

## bStats

NotMines registers with bStats using plugin ID `30856`.

Set `metrics.enabled: false` in `config.yml` to disable the NotMines metrics hook.

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

If PlaceholderAPI is installed, NotMines registers the shorter `%nmines_...%` placeholder set automatically.

Player stat placeholders:

- `%nmines_games%`
- `%nmines_wins%`
- `%nmines_losses%`
- `%nmines_tiles_cleared%`
- `%nmines_win_rate%`
- `%nmines_total_wagered%`
- `%nmines_total_paid%`
- `%nmines_profit%`
- `%nmines_best_cashout%`
- `%nmines_biggest_bet%`

For leaderboard plugins such as Topper, use the raw numeric variants for money values so sorting stays numeric instead of lexicographic:

- `%nmines_total_wagered_raw%`
- `%nmines_total_paid_raw%`
- `%nmines_profit_raw%`
- `%nmines_best_cashout_raw%`
- `%nmines_biggest_bet_raw%`

If you want integer minor-unit values instead, these are also available:

- `%nmines_total_wagered_minor%`
- `%nmines_total_paid_minor%`
- `%nmines_profit_minor%`
- `%nmines_best_cashout_minor%`
- `%nmines_biggest_bet_minor%`

The placeholder expansion uses the in-memory stats cache and only schedules background cache fills when needed, so it does not perform synchronous database queries on the server thread.

## Config Notes

The defaults are documented in place. Important options include:

- `metrics.enabled` controls the plugin's bStats hook
- `limits.min-bet` and `limits.max-bet` accept values like `100`, `1k`, `1m`, or `2b`
- `gameplay.house-edge` controls the edge applied to payouts
- `gameplay.safe-pick-messages` and `gameplay.board-close-messages` control chat feedback
- `announcements.min-multiplier` and `announcements.min-payout` jointly gate big-win broadcasts
- `stats.save-interval-seconds` controls how often pending stats are flushed
- `gui.yml` contains both the board layout and the sound theme used by the GUI
- `config-version` is maintained by the plugin and should not be edited manually

### GUI sound config

The `sounds` section in `plugins/NotMines/gui.yml` controls the feel of the board.

- `sounds.enabled` toggles GUI sounds on or off
- Board events plus `leaderboard-open`, `leaderboard-page`, `leaderboard-filter`, and `menu-close` accept layered sound entries
- Each sound entry supports a Bukkit-style name such as `UI_BUTTON_CLICK`, a namespaced key such as `minecraft:ui.button.click`, or a custom resource-pack sound key
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

The normal build uses the oldest supported API and Java 17 bytecode. Maintainers can also compile the same source against a newer endpoint, for example:

```powershell
.\gradlew.bat clean test "-PpaperApiVersion=26.2.build.84-stable" "-PtargetJavaVersion=25"
```

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
