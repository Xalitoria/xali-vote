# XaliVote

**XaliVote** saves NuVotifier votes and dispatches reward commands. A vote for a player who is online runs the rewards at
once. A vote for a player who is offline is stored and runs when the player joins. Every so many votes it holds a
[vote party](#vote-party) for everyone who is online. `/vote` lists the places to vote, or opens a menu, and the
[top voters](#placeholders) are a command, a menu and a set of placeholders. There is a plugin for Paper and one for
Velocity, built from shared code; behind Velocity the proxy's plugin [shares its settings](#behind-velocity) with the servers.

## Contents

- [Installation](#installation): [Requirements](#requirements), [Modules](#modules),
  [Building and installing](#building-and-installing), [Removal](#removal)
- [Configuration](#configuration): [Files](#files), [config.yml](#configyml), [rewards.yml](#rewardsyml),
  [messages.yml](#messagesyml)
- [Usage](#usage): [Commands](#commands), [Vote links](#vote-links), [Vote party](#vote-party), [Placeholders](#placeholders),
  [Working with other plugins](#working-with-other-plugins), [Behind Velocity](#behind-velocity), [Webhook](#webhook)
- [Development](#development)
- [See also](#see-also)

## Installation

### Requirements

| Plugin | Needed | Purpose |
|---|---|---|
| NuVotifier | Yes | Receives the votes. Loads before XaliVote. |
| XaliMenu | No | The vote menus and the `xalivote:` [catalogs and action](#xalimenu). Turned on with `menu.enabled` (on by default). It needs PlaceholderAPI too, which it requires itself. |
| PlaceholderAPI | No | The [`%xalivote_...%` placeholders](#placeholders), which the menus and XaliRedeem's `placeholder` conditions read. |
| XaliRedeem | No | `vouchers` in `rewards.yml`, see [XaliRedeem](#xaliredeem). |

### Modules

| Module | Description |
|---|---|
| `vote/paper` | The Paper plugin, `xali-vote-<version>-paper.jar`: the vote and join listeners, the webhook (Ktor), `/vote`, the Server Links, and the hooks for XaliMenu, XaliRedeem and PlaceholderAPI. Supports Folia. |
| `vote/velocity` | The Velocity plugin, `xali-vote-<version>-velocity.jar`. The same vote rules on the proxy, and the [link with the servers](#behind-velocity); no webhook, no commands for players. |
| `vote/common` | What both share: the reward rules, the vote flow, the party counter, the database (Exposed), the configuration classes and the signed message format. No platform API. |

### Building and installing

```console
user $ ./gradlew :vote:paper:shadowJar :vote:velocity:shadowJar
```

The jars are written to `build/libs/vote/` as `xali-vote-<version>-paper.jar` and `xali-vote-<version>-velocity.jar`.
Copy the Paper jar into a server's `plugins` folder, or the Velocity jar into the proxy's. The libraries are downloaded
when the plugin loads, so the server or proxy needs internet access once.

For a dev server, `./gradlew :vote:paper:runServer` and `:vote:velocity:runProxy` start one in `vote/paper/run` and
`vote/velocity/run` with the plugin installed (`installPlugin` only installs).

The `paper { }` and `velocity { }` blocks in `vote/build.gradle.kts` come from the [Gradle plugin](../xali-gradle/README.md).
It writes the descriptors, and a loader for each platform that downloads the `api(...)` libraries when the plugin loads.
The Velocity plugin is `mc.xalitoria.vote.velocity.XaliVote`, created by the generated loader once its libraries are in.

### Removal

Delete the jar. `plugins/XaliVote/` (`plugins/xalivote/` on Velocity) and the tables `<prefix>votes`, `<prefix>vote_counts`
and `<prefix>vote_party` stay; delete them to forget the votes. The menus that were written into `plugins/XaliMenu/menus`
(`vote.conf`, `votetop.conf`) stay as well.

## Configuration

### Files

| File | Holds |
|---|---|
| `plugins/XaliVote/config.yml` | The database, the webhook, the links, the party, the menus and the proxy link. |
| `plugins/XaliVote/rewards.yml` | The rewards. |
| `plugins/XaliVote/messages.yml` | What the commands say (Paper). |
| `plugins/XaliVote/vote.db` | The data, with `database.type: sqlite`. |
| `plugins/XaliVote/proxy-settings.dat` | The settings the proxy shared last (Paper, behind Velocity). It is written by the plugin; delete it to forget them. |

On Velocity the same files, except the last two, are in `plugins/xalivote/`.

`config.yml` is written again after it is read, so a setting added by an update shows up in the file. Comments in it are not kept.

### config.yml

| Key | Default | Meaning |
|---|---|---|
| `database.type` | `sqlite` | `sqlite`, `mariadb` or `mysql`. Use `mariadb` for MariaDB: it has its own driver. |
| `database.table-prefix` | `xalivote_` | Prepended to every table name: letters, digits and underscores. |
| `database.sqlite.filename` | `vote.db` | The SQLite file. |
| `database.mysql` | | `host`, `port`, `database`, `username`, `password`, `use-ssl` and `pool-size`. |
| `webhook.enabled` | `false` | Accept votes over HTTP as well, see [Webhook](#webhook). |
| `webhook.port` | `8192` | The port the webhook listens on, on all interfaces. NuVotifier listens on 8192 too unless it was told otherwise, so choose another one; if the port is taken the webhook is switched off and the log says so. |
| `webhook.secret` | empty | Shared secret the sender must give. Empty accepts anything, and the plugin says so in its log when it starts. |
| `links` | none | The places to vote, a list of `name`, `url` and an optional `description`, see [Vote links](#vote-links). |
| `server-links.enabled` | `false` | Also put the links in the game's Server Links screen. |
| `server-links.prefix` | `Vote: ` | Put in front of each name there. |
| `party.enabled` | `false` | Hold a [vote party](#vote-party). |
| `party.votes` | `25` | How many votes make one. |
| `party.commands` | none | Console commands run for every player who is online. |
| `party.once` | none | Console commands run once. |
| `top.size` | `10` | How many top voters are kept for the command, the menu and the placeholders. |
| `top.refresh` | `1m` | How often they are read from the database again, which is how votes counted elsewhere show up. |
| `menu.enabled` | `true` | Use XaliMenu for `/vote` when it is installed. |
| `menu.name` | `vote` | The menu `/vote` opens. |
| `menu.top` | `votetop` | The menu of the top voters. |
| `menu.write-menus` | `true` | Write the two menus into `plugins/XaliMenu/menus` where they are missing. |
| `proxy.enabled` | `true` | Behind Velocity, use the proxy plugin's links and party; on the proxy, tell the servers. See [Behind Velocity](#behind-velocity). |

> [!NOTE]
> If the database cannot be reached, the plugin logs the error and disables itself.

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
| `vouchers` | none | Vouchers of [XaliRedeem](#xaliredeem) to give: the id of each, with how many. Paper only. |
| `exclude` | none | Names of other entries. The entry does not fire when one of them fires on the same vote. |

**File** `plugins/XaliVote/rewards.yml`

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

### messages.yml

What `/vote` says, one key each (`links-header`, `link-line`, `top-line`, `party-progress`, ...), written with their defaults on
the first start. Colors, hex and MiniMessage work. `{name}`, `{url}`, `{description}`, `{position}`, `{votes}`, `{progress}`,
`{remaining}` and `{parties}` are filled in where the default text has them. A name or a player's name is shown as it is, never
read as formatting.

## Usage

### Commands

`/xalivote`, also `/vote`, `/xvote`, `/votes` and `/xvotes`, on Paper.

| Command | Permission | Does |
|---|---|---|
| `/vote` | `xalivote.use` | Opens the vote menu, or lists the [links](#vote-links) in chat when there is no menu. |
| `/vote links` | `xalivote.use` | The links in chat; click one to open it. |
| `/vote menu` | `xalivote.use` | Opens the menu; lists the links and says so when there is none. |
| `/vote top` | `xalivote.use` | Opens the menu of the top voters, or lists them in chat. |
| `/vote party` | `xalivote.use` | How far the [party](#vote-party) is. |
| `/vote stats [player]` | `xalivote.use` | A player's votes in all; your own without a name. |
| `/vote reload` | `xalivote.admin` | Reads `config.yml`, `rewards.yml` and `messages.yml` again. A file that does not parse changes nothing and the error is shown. |

Everyone has `xalivote.use` unless a permission plugin takes it away; operators have `xalivote.admin`. On the proxy the only command is
`/xalivotev` (`xalivote.admin`): it says what is in force, and `/xalivotev reload` reads the files again and tells the servers.

### Vote links

`links` in `config.yml` lists the places to vote:

**File** `plugins/XaliVote/config.yml`

```yaml
links:
  - name: "Minecraft Servers"
    url: "https://example.com/vote"
    description: "Once a day"
  - name: "Top Servers"
    url: "https://top.example.org/vote"
server-links:
  enabled: true
```

Only an `http` or `https` address with a host counts, so a link cannot send a player to a `file:` or `javascript:` address; a link
without a name or with another address is left out, and the log says which. `/vote` shows them and a click opens one.

With `server-links.enabled` they are added to the game's own **Server Links** screen (the pause menu) as well, each named
`server-links.prefix` and its name. XaliVote removes only what it added, and a player sees a change the next time they join.

### Vote party

With `party.enabled`, every `party.votes` votes (from anyone, on any site) set off a party: `party.commands` run once for every
player who is online, and `party.once` run once.

**File** `plugins/XaliVote/config.yml`

```yaml
party:
  enabled: true
  votes: 50
  commands:
    - "give {player} diamond 2"
  once:
    - "say Vote party #{party}! Everyone gets a gift."
```

`{player}` is the player (in `commands`), `{party}` the number of the party and `{votes}` how many votes make one. Every vote counts,
also one for a player who is offline. The count is kept in the table `<prefix>vote_party` and raised in the database, so servers
that share the database count together, and of several that count at the same time exactly one starts each party. Lowering
`party.votes` below what is already counted starts the parties that were due, one for each.

> [!NOTE]
> A party that is started by a vote counted on this server is run on this server. Behind Velocity it is [run on every server](#behind-velocity),
> which needs the servers to share one MariaDB or MySQL database: with a SQLite file each plugin counts for itself.

### Placeholders

PlaceholderAPI answers from memory (the votes of the players who are online, and the top voters, read every `top.refresh`), never
from the database.

| Placeholder | Is |
|---|---|
| `%xalivote_votes%` | The player's votes in all. 0 for a player who is not online. |
| `%xalivote_top_name_<n>%`, `%xalivote_top_votes_<n>%` | The player in place *n* (from 1) of the top voters, and their votes. Empty past the last. |
| `%xalivote_party_progress%` | Votes counted toward the next party. |
| `%xalivote_party_needed%` | `party.votes`. |
| `%xalivote_party_remaining%` | Votes still missing. |
| `%xalivote_parties%` | How many parties there have been. |
| `%xalivote_party_enabled%` | `true` or `false`. |
| `%xalivote_links%` | How many links there are. |

### Working with other plugins

#### XaliMenu

With XaliMenu installed and `menu.enabled`, `/vote` opens a menu and `/vote top` another; XaliVote writes `vote.conf` and
`votetop.conf` into `plugins/XaliMenu/menus` where they are missing (`menu.write-menus`) and they are plain XaliMenu menus from
then on. `/xmenu reload` reads them if XaliMenu has already started. XaliVote adds three things to XaliMenu, so any menu can use them:

| What | Name | Does |
|---|---|---|
| Catalog | `xalivote:links` | The links, one element each, with `{index}`, `{name}`, `{url}`, `{host}` and `{description}`. |
| Catalog | `xalivote:top` | The top voters, one element each, with `{position}`, `{name}`, `{votes}` and `{uuid}`. |
| Action | `xalivote:link`, `url = "{url}"` | Tells the player the link in chat, where it can be clicked: a menu cannot open a web page itself. Anything but an `http(s)` address is dropped. |

XaliMenu shows an element's values as plain text, **without colors**, so the menus color them in the item template.

#### XaliRedeem

XaliRedeem has no API to compile against; what it offers is `/xaliredeem admin give`, which the console may run. A reward's
`vouchers` use that so you need not write the command:

**File** `plugins/XaliVote/rewards.yml`

```yaml
milestone:
  after: 10
  every: 5
  vouchers:
    vote-crate: 1
```

gives one `vote-crate` voucher, for the same vote numbers `commands` would run on. XaliRedeem has to be installed on the server (the
log warns at start when `rewards.yml` names vouchers and it is not), and a voucher id is only accepted if it is a name XaliRedeem can
have (lower case letters, digits, `_` and `-`). **The proxy cannot give vouchers**, since XaliRedeem is a Paper plugin: on Velocity
the key is ignored and the log says so, so give them from the server's own XaliVote or a `commands` line that a server runs.

The other way round, a voucher can ask for votes through PlaceholderAPI, with XaliRedeem's `placeholder` condition:

```yaml
conditions:
  - type: placeholder
    value: "%xalivote_votes%"
    operator: ">="
    expected: "10"
```

#### PlaceholderAPI

The [placeholders](#placeholders) are registered when PlaceholderAPI is installed.

### Behind Velocity

A server detects that it is behind Velocity the way Paper itself knows: modern forwarding is on in `config/paper-global.yml`
(`proxies.velocity.enabled`, with its `secret`), which is read once at startup. Nothing needs to be set, and the log says
`Velocity forwarding found` when it is. With `proxy.enabled: false` the server ignores the proxy.

Install the Velocity jar on the proxy too, and the proxy's `config.yml` is the **master** of what the servers share:

- the `links` and `server-links` (so every server answers `/vote` with the same list), and the `party`.
  The proxy sends them to a server whenever a player connects to it (at most every five seconds per server) and after
  `/xalivotev reload`; the server keeps them in `proxy-settings.dat`, so a restart does not lose them. Until the first arrives, and
  on a server without the proxy plugin, the server's own `links` and `party` are used. The `rewards`, the `database`, the `menu`
  and the rest stay what each one's own file says.
- the **party**: whichever of the proxy and the servers counts the vote that completes it, `party.once` runs there, and every other
  server and the proxy tell all of them, so each runs `party.commands` for its own players. With `proxy.enabled: false` on the proxy
  it runs `commands` for all its players on its own console instead, which only reaches commands the proxy knows.

Velocity's own console cannot run a server's commands, so the rewards for a vote that the proxy counts are commands the proxy
knows, as before. To reward on a server, have NuVotifier count the votes there.

The messages are signed with the forwarding secret, which the plugin reads like Velocity does (`VELOCITY_FORWARDING_SECRET`, else the
file `forwarding-secret-file` names in `velocity.toml`, `forwarding.secret` by default); a message that is not signed, is a minute
old, or comes from a player is ignored. A plugin message travels on the connection of a player, so a server with nobody on neither
sends nor hears anything until someone joins; the database is right either way.

| Frame | Layout |
|---|---|
| Channel | `xalivote:main`, one frame per message, at most 30 000 bytes. |
| Frame | 32-byte HMAC-SHA256 of the rest, then `byte version (1)`, `long milliseconds`, `byte type`, the body. |
| `0` Settings | `int n`, `n` links (`UTF name`, `UTF url`, `UTF description`), `boolean enabled`, `UTF prefix` (server links), `boolean enabled`, `int votes`, commands and once (each `int count`, then `UTF` strings). At most 40 links. Proxy to server. |
| `1` Party | `long number`: a party started where the vote was counted. Either way; the proxy passes a server's on to the others. |

Types are only ever added to the end.

### Webhook

With `webhook.enabled`, the Paper plugin listens for `POST /vote` with a JSON body, for services that cannot reach
NuVotifier:

```json
{ "username": "Steve", "serviceName": "example.com", "address": "203.0.113.5", "secret": "..." }
```

`address` and `secret` are optional. A body that does not parse gets 400, a wrong `secret` gets 401 and an accepted vote
gets 200.

```console
user $ curl -i -X POST http://127.0.0.1:8192/vote -H 'Content-Type: application/json' \
          -d '{"username":"Steve","serviceName":"example.com","secret":"..."}'
```

> [!WARNING]
> The webhook listens on all interfaces. Set `webhook.secret`, and firewall the port so that only the sender can reach it.

## Development

| Directory | Package | Holds |
|---|---|---|
| `vote/common` | `com.xalitoria.vote` | The shared code: `RewardEngine.kt` (`resolveCommands`), `VoteFlow.kt` (what a vote does, and the `VoteHost` a platform fills in), `Links.kt`, `config/` (`VoteConfig`, `RewardEntry`), `db/` (`DatabaseManager`, `tables/`, `repository/`), `protocol/` (`Protocol`, the signed messages, and `ForwardingSecret`). No platform API; its libraries are `compileOnly`. |
| `vote/paper` | `mc.xalitoria.vote.paper` | The Paper plugin (`XaliVote`, which is the `VoteHost`), `listener/`, `http/VoteWebhook.kt`, `command/VoteCommand.kt`, `board/Board.kt` (what is answered from memory), `link/ServerLinkSync.kt`, `proxy/ProxyLink.kt` and `external/` (`MenuHook`, `RedeemHook` and `VoteExpansion`: the only classes that touch those plugins). |
| `vote/velocity` | `mc.xalitoria.vote.velocity` | The Velocity plugin (`XaliVote`, created by the generated loader), `listener/` and `relay/Relay.kt`. |

The platform differences (who is online, running a console command, who a party's commands run for) are the `VoteHost` that each
platform's `XaliVote` implements, so the rules in `VoteFlow` run without a server. Both platforms declare their libraries with
`api(...)`, so each gets a loader and nothing but `common` is shaded.

```console
user $ ./gradlew :vote:common:test
```

| Test | Covers |
|---|---|
| `Rewards` (`Votes.kt`) | `resolveCommands`: `after`, `every` (below 1 counts as 1), `exclude`, and the defaults that give nothing. |
| `Storage` (same file) | The database on SQLite: each player counts their own votes from 1, votes that arrive at once get a number each, and pending votes are claimed once, oldest first. |
| `PartyCount` (`Party.kt`) | The party counter: a party every so many votes, exactly one start for votes that arrive at once, catching up when the setting is lowered, and the top voters' order. |
| `Flow` (same file) | `VoteFlow` on a host that only records: online and offline votes, vouchers, a party's `once` and `commands`, a party another server started, and no counting while it is off. |
| `Links` (`Sharing.kt`) | Which addresses a link may have, and that a bad link is left out and reported. |
| `Sharing` (same file) | The signed messages: they survive the trip, and a wrong secret, a changed byte, an old time or a cut frame is turned away; too many links are cut; the forwarding secret is found in `paper-global.yml` and `velocity.toml`. |
| `Vouchers` (same file) | `vouchers` become commands only when something can give them. |

The reward rules are a pure function, so a new rule is cheap to cover with a test before the plugins are touched.

What the unit tests do not cover is a server: the menus, the commands, the placeholders and the Server Links were tried on a Folia
server with a bot (see [`menu/live`](../menu/live/README.md)), with NuVotifier's jar and a vote sent to the webhook. The proxy
link was not run on a proxy; its messages and the secret are tested, and the rest is the same plugin-message code as the other Xali plugins.

**Adding a reward key**

1. Add the field to `RewardEntry` (with a default, so an old `rewards.yml` still loads).
2. Apply it in `resolveCommands` and test it in `Rewards`.
3. Document it in the table under [rewards.yml](#rewardsyml).

**Adding a message to the proxy link**

1. Add a case to `Message` and a number at the **end** of `Protocol` (`encode` and `decode`); test it in `Sharing`.
2. Handle it in `ProxyLink` (Paper) and `Relay` (Velocity), and add a row to the table under [Behind Velocity](#behind-velocity).

## See also

- [Gradle plugin](../xali-gradle/README.md) — descriptors and loaders
- [util](../util/README.md) — `DatabaseConfig` and YAML files
- [XaliMenu](../menu/README.md) and [XaliRedeem](../redeem/README.md) — the menus and the vouchers the hooks work with
- [XaliChat](../chat/README.md#proxy-sync) — the same proxy scheme, signed with the forwarding secret
- [Root README](../README.md) — the workspace

## External resources

- [NuVotifier](https://github.com/NuVotifier/NuVotifier) — the vote receiver
