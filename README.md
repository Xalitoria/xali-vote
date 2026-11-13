# XaliVote

**XaliVote** saves NuVotifier votes and dispatches reward commands. A vote for a player who is online runs the rewards at
once. A vote for a player who is offline is stored and runs when the player joins. There is a plugin for Paper and one
for Velocity, built from shared code.

## Installation

### Requirements

| Plugin | Needed | Purpose |
|---|---|---|
| NuVotifier | Yes | Receives the votes. Loads before XaliVote. |

### Building and installing

```console
user $ ./gradlew :vote:paper:shadowJar :vote:velocity:shadowJar
```

The jars are written to `build/libs/vote/` as `xali-vote-<version>-paper.jar` and `xali-vote-<version>-velocity.jar`.
Copy the Paper jar into a server's `plugins` folder, or the Velocity jar into the proxy's.

`./gradlew :vote:installPaperPlugin` copies the Paper jar into `vote/run/plugins` for a dev server.

The `paper { }` and `velocity { }` blocks in `vote/build.gradle.kts` come from the [Gradle plugin](../xali-gradle/README.md).
It writes the descriptors, and a loader for each platform that downloads the `api(...)` libraries when the plugin loads.
The Velocity plugin is `mc.xalitoria.vote.velocity.XaliVote`, created by the generated loader once its libraries are in.

## Configuration

### Files

| File | Holds |
|---|---|
| `plugins/XaliVote/config.yml` | The database and the webhook. |
| `plugins/XaliVote/rewards.yml` | The rewards. |

### config.yml

| Key | Default | Meaning |
|---|---|---|
| `database.type` | `sqlite` | `sqlite`, `mariadb` or `mysql`. Use `mariadb` for MariaDB: it has its own driver. |
| `database.table-prefix` | `xalivote_` | Prepended to every table name: letters, digits and underscores. |
| `database.sqlite.filename` | `vote.db` | The SQLite file. |
| `database.mysql` | | `host`, `port`, `database`, `username`, `password`, `use-ssl` and `pool-size`. |
| `webhook.enabled` | `false` | Accept votes over HTTP as well, see [Webhook](#webhook). |
| `webhook.port` | `8192` | The port the webhook listens on, on all interfaces. |
| `webhook.secret` | empty | Shared secret the sender must give. Empty accepts anything, and the plugin says so in its log when it starts. |

> [!NOTE]
> If the database cannot be reached, the Paper plugin logs the error and disables itself.

> [!NOTE]
> The `database` section is the one the other Xali plugins use (`DatabaseConfig` in [util](../util/README.md)). Its keys
> were `tablePrefix`, `useSsl` and `poolSize` before; a `config.yml` written by an older version has to be changed to the
> names above, or its values are ignored and the defaults are used. `database.serverName` was never read and is gone.

### rewards.yml

Each entry has a name and says when it fires, for the player's all-time vote number *n* (1 for the first vote):

| Key | Default | Meaning |
|---|---|---|
| `after` | `1` | The first vote number the entry fires on. |
| `every` | `1` | Fires on every *every*-th vote from `after` on: when *n* >= `after` and (*n* - `after`) is a multiple of `every`. Below 1 counts as 1. |
| `commands` | none | Console commands. `{player}` is replaced with the player's name. |
| `exclude` | none | Names of other entries. The entry does not fire when one of them fires on the same vote. |

**File** `rewards.yml`

```yaml
basic-vote:
  every: 1
  exclude: [milestone]    # a milestone vote gets the milestone instead
  commands:
    - "give {player} diamond 1"
milestone:
  after: 10
  every: 5                # votes 10, 15, 20, ...
  commands:
    - "give {player} diamond 16"
```

The defaults written on the first start hold these two entries without commands.

## Usage

### Webhook

With `webhook.enabled`, the Paper plugin listens for `POST /vote` with a JSON body, for services that cannot reach
NuVotifier:

```json
{ "username": "Steve", "serviceName": "example.com", "address": "203.0.113.5", "secret": "..." }
```

`address` and `secret` are optional. A body that does not parse gets 400, a wrong `secret` gets 401 and an accepted vote
gets 200.

> [!WARNING]
> The webhook listens on all interfaces. Set `webhook.secret`, and firewall the port so that only the sender can reach it.

## Development

The module has no tests yet. Jars are built into `build/libs/vote/`.

| Directory | Package | Holds |
|---|---|---|
| `vote/common` | `com.xalitoria.vote` | The shared code: reward resolution, the database (Exposed) and the configuration classes. No platform API. |
| `vote/paper` | `mc.xalitoria.vote.paper` | The Paper plugin, its listeners and the webhook (Ktor). |
| `vote/velocity` | `mc.xalitoria.vote.velocity` | The Velocity plugin and its listeners. |

The platform differences (`isOnline`, `dispatchCommand`) are methods of each platform's `XaliVote` class.

The reward rules are in `RewardEngine.kt` (`resolveCommands`), a pure function that is cheap to cover with a test.

## See also

- [Gradle plugin](../xali-gradle/README.md) — descriptors and loaders
- [NuVotifier](https://github.com/NuVotifier/NuVotifier) — the vote receiver
