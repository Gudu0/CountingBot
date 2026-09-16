# Counting Bot — Simplification Refactor Plan

Status: **planning only, no code written yet.** Every file, class, field, command and
ID named below was read out of the current tree (`src/main/java/org/gudu0/countingbot/`)
and the live data under `data/` on 2026-09-15, at commit `6e526fd`.

Goal in one line: **go from 56 classes across 12 packages to ~14 classes, drop
multi-server, and make "which file do I edit?" answerable without searching.**

---

## 1. Codebase audit (what is actually there)

### 1.1 Totals

56 Java files, 3,773 lines, 12 packages. Zero tests (`src/test/` does not exist).
Build: Gradle + shadow jar, JDA 6.0.0, Jackson 2.17.2, Java 21.
Deployment: `syncStuff/run-push.bat` / `run-sync.bat` drive WinSCP against a remote host.

### 1.2 Per-package inventory

| Package        | Files | Lines | What it actually does                                             |
|----------------|------:|------:|-------------------------------------------------------------------|
| `commands`     |    11 |   978 | One `ListenerAdapter` per slash command, plus `CommandGuards`     |
| `counting`     |     4 |   528 | The counting game — `CountingListener` is 429 of those lines      |
| `achievements` |    13 |   506 | Catalog + unlock engine + 5 tiny types + 2 enums                  |
| `goals`        |     4 |   332 | Pinned/edited goal embed, per-guild registry                      |
| `config`       |     5 |   194 | Two live config shapes + two store impls + one dead pair          |
| root           |     2 |   318 | `Main` (232) wires everything; `SafetyChecks` (86)                |
| `disconnects`  |     3 |   253 | Daily gateway-disconnect summary message                          |
| `suggestions`  |     4 |   196 | Suggestion persistence + DM/post (2 listeners live in `commands`) |
| `util`         |     3 |   173 | `JsonStore`, `ConsoleLog`, `BotPaths`                             |
| `guild`        |     3 |   165 | `GuildManager` / `GuildContext` / `GuildJoinListener`             |
| `stats`        |     3 |    76 | Global fame/shame per user                                        |
| `logging`      |     1 |    54 | `LogService` — Discord log thread + console passthrough           |

### 1.3 The fragmentation, measured

**Suggestions — 6 classes for 2 commands and one JSON file:**
`SuggestCommandListener` (51), `SuggestResponseListener` (48), `SuggestionsService` (141),
`SuggestionsStore` (23), `SuggestionsState` (10), `SuggestionEntry` (22).

**Achievements — 13 classes:** `AchievementsService` (166), `AchievementsCatalog` (112),
`Conditions` (87), `AchievementDef` (29), `AchievementContext` (24), `UserAchievements` (22),
`AchievementsStore` (19), `AchievementsState` (13), `StatKey` (9), `GlobalKey` (7),
`AchievementGrantResult` (7), `AchievementTrigger` (6), `Condition` (5).
Five of those files are 13 lines or fewer. Three are enums in their own file.

**Store wrappers — 6 near-identical classes, 139 lines, zero logic:**
`StateStore`, `GoalsStore`, `StatsStore`, `SuggestionsStore`, `AchievementsStore`,
`DisconnectStore` each wrap `JsonStore<T>` and re-export `state()`, `markDirty()`,
`startAutoFlush()`, `tryFlush()`, `flushNow()`. `DisconnectStore` differs only by
renaming `flushNow()` to `save()`.

### 1.4 Dead and unreachable code found during the audit

Confirmed by grep across `src/`:

- `config/BotConfig.java` (35) + `config/ConfigStore.java` (73) — **108 lines, zero live
  callers.** `BotConfig` is referenced only by `ConfigStore` and by a stale Javadoc line in
  `SafetyChecks`. Superseded by `GlobalConfig` + `GuildConfig` + `TypedConfigStore`.
- `AchievementsService` has a second "legacy" constructor taking a `StateStore`, plus the
  `legacyStateStore` field and the `guilds == null` branch of `stateStoreFor()`. `Main`
  only ever calls the `GuildManager` constructor. The legacy path is unreachable.
- `Conditions.globalAtLeast()`, `allOf()`, `anyOf()` and their backing records
  `GlobalAtLeast`, `AllOf`, `AnyOf` — never called. `AchievementsCatalog` uses only
  `globalEquals`, `userStatAtLeast` and `never`.
- `GlobalKey.GLOBAL_STREAK_CURRENT` / `GLOBAL_STREAK_BEST` — no catalog entry uses them,
  so `AchievementContext.CountingSnapshot`'s two streak fields are computed on every valid
  count and never read.
- `StatKey.CORRECT`, `INCORRECT`, `CURRENT_STREAK` — no catalog entry uses them.
- `CountingListener.EVE_ID` and `BEACON_ID` — declared, never referenced.
- `CountVerifier.isCorrectNumber` — commented out.
- `UserStats.negCounts` — written nowhere, read nowhere (it is still persisted, see §6).
- `StatsData.getOrDefault()`, `GuildManager.cachedCount()` — no callers.
- `SafetyChecks`'s class Javadoc advertises a `run(JDA, BotConfig, AtomicBoolean)` overload
  "kept so older code still compiles". That method no longer exists.

### 1.5 Multi-server surface (what has to come out)

| Mechanism                               | Where                                                                                                                                                                                                                                 |
|-----------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `ConcurrentHashMap<Long, GuildContext>` | `GuildManager.contexts`                                                                                                                                                                                                               |
| `ConcurrentHashMap<Long, GoalsService>` | `GuildGoalsServiceRegistry.map`                                                                                                                                                                                                       |
| Per-guild data dirs                     | `BotPaths.GUILDS_DIR`, `BotPaths.guildDir(long)`                                                                                                                                                                                      |
| Per-guild config                        | `config/GuildConfig` + one `TypedConfigStore` per guild                                                                                                                                                                               |
| `guildId` threaded through APIs         | `LogService.log(long, String)`, `AchievementsService.onTrigger/unlockById`, `CountingListener.resyncNow/accept/markIncorrect/delete/logDecision`, `SafetyChecks.runForGuild`, `AchievementContext.guildId`, `SuggestionEntry.guildId` |
| Runtime guild onboarding                | `GuildJoinListener` (51 lines)                                                                                                                                                                                                        |
| Boot-time disk scan for guilds          | `CountingListener.listGuildDirsOnDisk()` + the loop in `onReady`                                                                                                                                                                      |
| Per-guild command registration          | `Main.registerGuildCommandsAll/One`                                                                                                                                                                                                   |
| Per-guild setup commands                | `SetupListener` `setcountingchannel` / `setlogthread`                                                                                                                                                                                 |

**Reality check against the data directory:** `data/guilds/` contains exactly one folder,
`712304553931833385`. The bot is in 4 guilds; only this one has ever been configured.
All of the above exists to serve one server.

Note a side effect of the current design: `CountingListener.onMessageReceived` calls
`guilds.get(guildId)` **before** checking whether the guild has a counting channel. Any
message in any of the 4 guilds therefore constructs a `GuildContext`, which loads a config
and starts **two** `ScheduledExecutorService` threads (`stateStore.startAutoFlush(5)`,
`goalsStore.startAutoFlush(10)`) plus two JVM shutdown hooks — for guilds that will never
count. Today that is ~8 scheduler threads and ~8 shutdown hooks for one active server.

### 1.6 Live configuration (the values that become constants)

From `data/global/config.json` and `data/guilds/712304553931833385/config.json`:

```
guildId              712304553931833385
countingChannelId    1391997959557746800
logThreadId          1428562709498171472
disconnectThreadId   1428562709498171472   <- same channel as logThreadId
enableLogs           true
enforceDelete        true
countingDelaySeconds 10
suggestionsThreadId  "0"                   <- disabled; see Open Question 4
suggestionsNotifyUserId / hardcoded owner id  733113260496126053
```

`733113260496126053` is already hardcoded in two places in source:
`CountingListener.onMessageReceived` (the `!`-prefix bypass) and
`SuggestResponseListener` (`CommandGuards.requireUser(event, 733113260496126053L)`).
Hardcoding is already the de-facto pattern; this plan just makes it deliberate and central.

---

## 2. The consolidation rule

Applied uniformly, so the answer to "where does this live?" is mechanical.

> **A type gets its own file only if it meets at least one of these three tests:**
>
> 1. **Discord dispatches to it** — it is a `ListenerAdapter` registered in `Main`.
>    One listener per *feature area*, not per command.
> 2. **It owns a file on disk** — one class per JSON file under `data/`.
> 3. **Three or more unrelated callers depend on it** — genuine shared infrastructure.
>
> Everything else becomes a `static` nested type or a private method inside its owner:
> enums, records, DTOs, state POJOs, single-use helpers, condition types, and any
> "service" with exactly one caller.
>
> **Size guard:** if a merged file exceeds ~500 lines, split it along the same rule
> rather than by inventing a new abstraction layer.
>
> **Persistence rule:** persisted shapes live as `public static final class State` inside
> the class that owns them. They must be `static` and keep a no-arg constructor
> (Jackson requirement). Renaming the *class* is safe; renaming a *field* is not (§6).

Why this rule and not "one class per responsibility": with one maintainer and no tests,
the binding constraint is not testability-in-isolation, it is **recall**. A rule keyed to
things that are externally visible (a Discord command, a file on disk) means the answer to
"what do I touch?" is derivable from the symptom rather than from remembering the layering.

### 2.1 The rule applied — where else it bites

Not just suggestions and achievements:

- **`GuildGoalsServiceRegistry` (73)** — fails all three tests once there is one server.
  It exists purely to key `GoalsService` by guild id. Delete outright.
- **6 store wrappers (139)** — fail test 3 (each has one caller). Inline `JsonStore<T>`
  as a field on the owning class.
- **`goals/GoalState`, `counting/CountingState`, `disconnects/DisconnectDailyState`,
  `suggestions/SuggestionsState`, `achievements/AchievementsState`, `stats/StatsData`** —
  fail all three. Nest as `State` inside their owner.
- **`SafetyChecks` (86)** — one caller pattern, no disk, not a listener. Becomes a method.
- **`util/BotPaths` (29)** — two callers. Becomes constants in `BotConfig`.
- **`PingListener` (25), `CountDelayListener` (68), `ResyncListener` (47)** — each is one
  tiny command. Under "one listener per feature area", they merge into `AdminListener`
  alongside `/setup`.
- **`StatsListener` (59) + `LeaderboardListener` (100)** — two read-only views of the same
  `stats.json`. One feature area, one listener.
- **`ConsoleLog` (45) + `LogService` (54)** — passes test 3 (3+ callers each), but they are
  two halves of one concern ("emit a log line"), and `LogService.log()` already calls
  `ConsoleLog.info()` on every call. Merge into one `Log`.
- **Kept as-is:** `JsonStore` (6 callers, real logic), `CommandGuards` (used by 3 listeners),
  `Main`.

### 2.2 Target structure

Flat package `org.gudu0.countingbot` — with 14 files, sub-packages cost more navigation
than they save.

| File                        | Absorbs                                                                                                                                                                                                                                                      | Lines (est.) |
|-----------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------|
| `Main.java`                 | `Main` minus command registration                                                                                                                                                                                                                            | ~90          |
| `SlashCommands.java`        | the `updateCommands()` block from `Main`                                                                                                                                                                                                                     | ~80          |
| `BotConfig.java`            | `GlobalConfig`, `GuildConfig`, `TypedConfigStore`, `BotPaths`, `SafetyChecks`; deletes old `BotConfig`/`ConfigStore`                                                                                                                                         | ~150         |
| `Log.java`                  | `ConsoleLog` + `LogService`                                                                                                                                                                                                                                  | ~90          |
| `JsonStore.java`            | unchanged + §6 hardening                                                                                                                                                                                                                                     | ~105         |
| `CountingListener.java`     | `CountingListener`, `CountingState`→`State`, `CountVerifier`→private static + `Parsed` record, `StateStore`                                                                                                                                                  | ~470         |
| `GoalsService.java`         | `GoalsService`, `GoalState`→`State`, `GoalsStore`; deletes `GuildGoalsServiceRegistry`                                                                                                                                                                       | ~230         |
| `Achievements.java`         | `AchievementsService`, `AchievementsCatalog`, `Conditions`, `Condition`, `AchievementDef`, `AchievementContext`, `AchievementTrigger`, `AchievementGrantResult`, `StatKey`, `GlobalKey`, `UserAchievements`, `AchievementsState`, `AchievementsStore` (13→1) | ~330         |
| `AchievementsListener.java` | `AchievementsCommandListener`                                                                                                                                                                                                                                | ~110         |
| `SuggestionsListener.java`  | `SuggestCommandListener`, `SuggestResponseListener`, `SuggestionsService`, `SuggestionsStore`, `SuggestionsState`, `SuggestionEntry` (6→1)                                                                                                                   | ~230         |
| `DisconnectListener.java`   | `DisconnectDailyReporter`, `DisconnectDailyState`, `DisconnectStore`                                                                                                                                                                                         | ~200         |
| `StatsListener.java`        | `StatsListener`, `LeaderboardListener`, `StatsData`, `UserStats`, `StatsStore`                                                                                                                                                                               | ~200         |
| `AdminListener.java`        | `SetupListener`, `PingListener`, `CountDelayListener`, `ResyncListener`                                                                                                                                                                                      | ~220         |
| `CommandGuards.java`        | unchanged (minus `requireUser`, see Phase 5)                                                                                                                                                                                                                 | ~80          |

**56 files → 14. 12 packages → 1.** Estimated ~2,600 lines (down ~30%), almost all of the
reduction from deleted dead code, deleted per-guild plumbing, and deleted store wrappers —
not from compressing logic.

---

## 3. Removals and replacements, area by area

### 3.1 Multi-server infrastructure → two hardcoded server profiles

**Removed:** `guild/GuildManager`, `guild/GuildContext`, `guild/GuildJoinListener`,
`goals/GuildGoalsServiceRegistry`, `config/GuildConfig`, `config/GlobalConfig`,
`config/TypedConfigStore`, `SafetyChecks`, `util/BotPaths`.

**Replaced by:** one `BotConfig` holding two `static final` profiles:

```java
public final class BotConfig {
    public static final BotConfig PROD = new BotConfig(
        712304553931833385L,   // guild
        1391997959557746800L,  // counting channel
        1428562709498171472L,  // log thread
        1428562709498171472L,  // disconnect thread
        Path.of("data"));
    public static final BotConfig TEST = new BotConfig(
        <test guild id>, <test counting channel>, <test log thread>,
        <test disconnect thread>, Path.of("data-test"));

    public static BotConfig active();     // chosen by env var BOT_ENV, default PROD
    public Settings settings;             // the 3 values that stay runtime-tunable
    public void check(JDA jda);           // was SafetyChecks.runForGuild
}
```

`Settings` (nested, persisted) keeps only what genuinely changes at runtime:
`countingDelaySeconds`, `enforceDelete`, `enableLogs`. Channel and guild IDs become
constants — they have not changed since the file was created.

**Why this is better here:** the per-guild indirection buys the ability to onboard an
unknown server at runtime. That capability has been exercised zero times in the one
configured guild's lifetime, and it costs a `GuildManager` lookup at the top of every
handler, a `ctx.` prefix on every config read, a registry keyed by guild id for goals, and
a disk scan at boot. Removing it turns `ctx.cfg.enforceDelete` into `Cfg.enforceDelete`
everywhere and deletes 165 + 73 lines of routing whose only job was returning the same
object every time.

### 3.2 Suggestions: 6 classes → 1

**Removed:** `SuggestionsService`, `SuggestionsStore`, `SuggestionsState`,
`SuggestionEntry`, `SuggestCommandListener`, `SuggestResponseListener`.

**Replaced by:** `SuggestionsListener` — one `ListenerAdapter` handling both `suggest` and
`suggestion_response`, with `static final class Entry` and `static final class State`
nested, a `JsonStore<State>` field, and the existing `postToSuggestionsChannel` / `dmOwner`
/ `dmUserForSuggestionResponse` / `sendDm` as private methods.

**Why better:** today, changing the suggestion DM wording means locating
`SuggestionsService.sendDm` — which is not in the file for the command you invoked, and is
not obviously distinct from `dmUserForSuggestionResponse` sitting twenty lines above it.
After: one file, one `Ctrl+F`. `SuggestionsService.attach(JDA)` also disappears — the
listener already has the JDA instance from the event, so the two-phase
construct-then-attach dance is unnecessary.

### 3.3 Achievements: 13 classes → 2

**Removed:** all 13 files in `achievements/` plus the unreachable legacy constructor.

**Replaced by:**
- `Achievements.java` — the engine. Nested: `enum Trigger` (was `AchievementTrigger`),
  `enum GrantResult`, `enum StatKey`, `enum GlobalKey`, `record Def`, `record Ctx`,
  `interface Cond`, `static final class UserAchievements`, `static final class State`,
  the catalog as a private `static List<Def> CATALOG`, and the surviving condition
  factories (`globalEquals`, `userStatAtLeast`, `never`) as private statics.
- `AchievementsListener.java` — `/achievements view|grant` (Rule 1).

**Why better:** adding one achievement currently means editing `AchievementsCatalog`, and
possibly `StatKey` or `GlobalKey`, and possibly `Conditions` (to add a factory) and its
backing record. That is up to four files across four editors for one row of data. After, it
is one file, and the enums sit ten lines above the switch that consumes them — which is the
arrangement you asked for.

### 3.4 Goals: 4 classes → 1

**Removed:** `GuildGoalsServiceRegistry` (deleted, not merged), `GoalsStore`, `GoalState`.
**Replaced by:** `GoalsService` with nested `State` and a `JsonStore<State>` field, plus a
plain `Log` dependency. `Main` constructs exactly one instance.

**Why better:** the lazy-creation dance (`getOrCreate`, `markDirtyIfExists`,
`markDirtyOrCreate`) exists only so that a guild without a goal never spins up a scheduler.
With one server, the distinction between "exists" and "doesn't exist yet" is gone, and
`CountingListener` loses three different ways to say "the goal might have changed".

### 3.5 Commands: 11 classes → 4

**Removed:** `PingListener`, `CountDelayListener`, `ResyncListener`, `SetupListener` →
merged into `AdminListener`. `LeaderboardListener` → merged into `StatsListener`.
`SuggestCommandListener`/`SuggestResponseListener` → §3.2.
`AchievementsCommandListener` → renamed `AchievementsListener`.

**Kept:** `CommandGuards` (3 consumers).

**Also removed:** `/setup setcountingchannel` and `/setup setlogthread` — they mutate values
that are now constants. `/setup status` stays (it is the fastest way to confirm a deployment
took). See Open Question 3.

**Why better:** the current split means `/ping` costs a file, an import block and a JDA
registration line for four lines of behavior, while `/setup` and `/countdelay` — which write
the *same* `countingDelaySeconds` field — live apart and have drifted: `SetupListener` gates
on `MANAGE_SERVER || BAN_MEMBERS`, `CountDelayListener` gates on `BAN_MEMBERS` only. Merging
them makes that drift visible and forces one answer.

### 3.6 Logging + disconnects (the reason this refactor exists)

**Removed:** `util/ConsoleLog`, `logging/LogService`, `disconnects/DisconnectStore`,
`disconnects/DisconnectDailyState`.

**Replaced by:**
- `Log.java` — `Log.info/warn/error/debug` (console) and `Log.discord(String)` (the log
  thread). The `guildId` parameter is gone; the thread id comes from `BotConfig.active()`.
- `DisconnectListener.java` — the daily summary, with nested `State`.

**Why better, concretely:** you have wanted to rework disconnect logging for months and
could not find the entry point. Today it is spread across six places: the session events in
`DisconnectDailyReporter`, the message shape in its private `buildMessage`, the state in
`disconnects/DisconnectDailyState`, persistence in `DisconnectStore.save()` (which is
`markDirty()+flushNow()` under a different name), the channel id in `GlobalConfig`, and the
timezone in `logging/LogService.ZONE` / `TS` — which `DisconnectDailyReporter`
`static`-imports from a class it otherwise has nothing to do with. After Phase 6, the answer
is: **`DisconnectListener.java` for behavior, `Log.java` for where lines go.**

Two defects to fix while in there, both found in this audit:
- `DisconnectDailyReporter` mutates `store.state()` from both the gateway thread
  (`onSessionDisconnect`) and its own 30-second scheduler without ever taking
  `DisconnectStore.lock`, which every other store's callers do take. `ensureTodayMessage()`
  is `synchronized` on the reporter, but `recordDisconnect()` is not.
- `disconnectThreadId` and the guild's `logThreadId` are the *same channel*
  (`1428562709498171472`), reached by two independent code paths with two independent config
  fields. One channel, one path.

---

## 4. Phasing and sequencing

Each phase is a separate build, a separate deploy, and independently revertable. Phases run
in order; the dependency notes say why.

| # | Phase                                                      | Depends on | Risk     |
|---|------------------------------------------------------------|------------|----------|
| 0 | Safety net: Jackson hardening + backups + baseline capture | —          | Low      |
| 1 | Delete dead code                                           | 0          | Low      |
| 2 | Collapse multi-server to one server                        | 1          | **High** |
| 3 | Suggestions 6→1                                            | 2          | Low      |
| 4 | Achievements 13→2                                          | 2          | Medium   |
| 5 | Goals, stats, commands consolidation                       | 2          | Medium   |
| 6 | Logging + disconnect unification                           | 2, 5       | Medium   |
| 7 | Optional: flatten packages, tidy `data/`                   | 3–6        | Low      |

**Why Phase 2 before 3–6:** `guildId` threads through every feature's public API
(`LogService.log(guildId, …)`, `achievements.onTrigger(trigger, guildId, userId)`,
`goalsRegistry.markDirtyOrCreate(guildId)`). Consolidating a feature first and de-guilding it
second means touching each merged file twice, with the second pass changing method signatures
you just settled. De-guild first, then merge into a stable shape.

**Why Phase 6 after 5:** `Log` absorbs `ConsoleLog`, which every other class imports. Doing
it last means one mechanical import sweep instead of one per phase.

### Phase 0 — Safety net (no behavior change)

1. `JsonStore` and `TypedConfigStore`: add
   `om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)`. **This is the
   single most important line in the plan** — see §6.
2. `JsonStore.loadOrNew()`: on a parse *failure* (as opposed to a missing file), do not
   silently return defaults. See §6 for why the current behavior can erase a file.
3. Snapshot `data/` to a dated folder, verified, before anything else.
4. Capture a behavior baseline on prod: run `/setup status`, `/stats`, `/leaderboard`,
   `/achievements view`, `/goal view`, `/ping`; screenshot each. Record `sha256` of all six
   JSON files.

### Phase 1 — Delete dead code (no behavior change)

Delete everything in §1.4. Nothing in this list has a caller; the build either compiles or
tells you the audit was wrong about that item. Two need care:
- Removing `StatKey` / `GlobalKey` constants requires deleting the matching arms in the
  `switch` expressions inside `Conditions.UserStatAtLeast.matches` and `GlobalEquals.matches`.
- `UserStats.negCounts` is a *persisted* field (it appears in `stats.json`). Removing it is
  only safe after Phase 0 step 1. Its value is always 0.

Also bump `build.gradle` `version` to `10.0-PHASE1-DEADCODE`. The version string is already
used as a deployment marker (`9.0-GRANTING_ACHIEVEMENTS`); keeping one version per phase makes
rollback unambiguous about which jar is running.

### Phase 2 — Single server

1. Add `BotConfig` with `PROD` and `TEST` profiles (§3.1). **Point `PROD`'s paths at the
   existing files** — `data/global/stats.json`, `data/guilds/712304553931833385/state.json`,
   etc. No file moves in this phase (§6).
2. Delete `GuildManager`, `GuildContext`, `GuildJoinListener`, `GuildGoalsServiceRegistry`,
   `GuildConfig`, `GlobalConfig`, `TypedConfigStore`, `SafetyChecks`, `BotPaths`.
3. Drop the `guildId` parameter from `LogService.log`, `AchievementsService.onTrigger`,
   `AchievementsService.unlockById`, `CountingListener.resyncNow/accept/markIncorrect/
   delete/logDecision`, and `AchievementContext`.
4. Replace the `onReady` disk-scan loop in `CountingListener` with a single resync of the
   configured channel.
5. Add a hard guard at the top of `onMessageReceived` / `onMessageDelete` /
   `onSlashCommandInteraction`: `if (guildId != Cfg.guildId) return;`. This also fixes the
   stray-`GuildContext`-per-guild thread leak from §1.5.
6. `Main.registerGuildCommandsAll` → register in the one active guild only.

**What breaks / needs cleanup as a result:**
- **Stale slash commands in the other 3 guilds.** They were registered by
  `registerGuildCommandsAll` and Discord keeps them until explicitly cleared. After this
  phase the bot ignores those guilds, so the commands would appear and then fail silently.
  Either kick the bot from them, or run one throwaway pass calling
  `g.updateCommands().queue()` with no commands for each before deleting the loop.
- **`SuggestionEntry.guildId`** stays in `suggestions.json` (existing rows have it) but stops
  being meaningful. Keep the field; do not delete it (§6).
- **`SetupListener.setcountingchannel` / `setlogthread`** become no-ops writing to constants —
  remove the subcommands *and* their `SubcommandData` entries in `Main`'s registration block,
  or they stay visible in Discord.
- **`data/guilds/` directory** becomes a path with one meaningful entry and no scanner. Leave
  it on disk; the constant points into it. Cosmetic move deferred to Phase 7.
- **`GoalsService` construction timing** changes: today it is created lazily on first goal
  activity and attached to JDA by the registry. With one server it is constructed in `Main`
  and attached after `awaitReady()`, like `LogService` already is. Verify the pinned goal
  message is edited, not re-created, on the first boot after this phase.
- **`DisconnectDailyReporter`** was already global — unaffected except for the config read.

### Phase 3 — Suggestions 6 → 1

Mechanical, per §3.2. No persisted field names change. `SuggestionsService.attach(JDA)` and
its `volatile JDA jda` field disappear.

### Phase 4 — Achievements 13 → 2

Per §3.3. The only real risk is the catalog: `AchievementsCatalog.all()` is the source of
truth for what `/achievements view` renders and for what ids `/achievements grant` accepts.
Transcribe it verbatim — id strings must match `achievements.json` exactly (§6).

### Phase 5 — Goals, stats, commands

Per §3.4 and §3.5. Reconcile the `/setup setdelay` vs `/countdelay` permission drift here
(Open Question 2). Rename `CommandGuards.requireUser` — it currently returns `true` when the
user does **not** match, which reads backwards at every call site; the one caller in
`SuggestResponseListener` compensates with `||`. Rename to `isNotUser`, same behavior.

### Phase 6 — Logging + disconnects

Per §3.6. Merge `ConsoleLog` + `LogService` into `Log`; fold the disconnect classes into
`DisconnectListener`; move `ZONE`/`TS` into `Log` where the static import already points. Fix
the unsynchronized state mutation. **This is the phase that unblocks the disconnect rework you
have been putting off** — it should land as a pure restructure, with the actual behavior
rework as a separate change afterwards (see Non-Goals).

### Phase 7 — Optional cleanup

Flatten to one package. Optionally move `data/guilds/712304553931833385/*.json` up to `data/`.
Do this **only** with the bot stopped and a fresh snapshot in hand; it is the one phase that
touches file locations.

---

## 5. Service continuity

**Baseline fact: this bot has no hot reload.** Config edits apply live, code does not. Every
phase ends in stop-jar / swap-jar / start-jar. Realistic window: 10–30 seconds.

### What survives a restart, and what does not

| Thing                       | Survives?                  | Mechanism                                                          |
|-----------------------------|----------------------------|--------------------------------------------------------------------|
| Counting state              | Yes, if stopped gracefully | `state.json`, flushed every 5s + shutdown hook                     |
| Stats / achievements        | Yes, if stopped gracefully | `stats.json` / `achievements.json`, 10s + hook                     |
| Goal embed identity         | Yes                        | `goals.json.goalMessageId` — the message is *edited*, not reposted |
| Daily disconnect message    | Yes                        | `disconnects.json.messageId`                                       |
| Counts sent during downtime | **No**                     | Not received, not validated, not deleted                           |

**The graceful-shutdown dependency is load-bearing.** `JsonStore.startAutoFlush` registers a
JVM shutdown hook that calls `flushNow()`. A clean stop (SIGTERM / Ctrl-C / `systemctl stop`)
flushes everything. A `kill -9` skips the hook and loses up to the flush interval: 5s of
counting state, 10s of stats/achievements/goals/suggestions, 30s of disconnects. **Every
deploy in this plan must stop the bot gracefully, then verify the JSON mtimes advanced, before
the jar is swapped.** See Open Question 11.

**The real continuity risk is not the outage, it is the resync after it.**
`CountingListener.RESYNC_HISTORY = 3`. On boot, `onReady` reads the last **3** messages of the
counting channel and takes the newest parseable number as the current state. If more than 3
messages land during a 30-second window — entirely plausible in an active counting channel,
and more so because the bot is not deleting invalid ones while it is down — the resync can
latch onto the wrong number, or find nothing and reset `lastNumber` to `-1`, which puts the
channel into "init start" mode and accepts *any* number as the next count. Mitigations, in
order of preference:

1. Deploy during a quiet window and confirm the channel is idle first.
2. Run `/resync` immediately after every restart and check the reply.
3. Raise `RESYNC_HISTORY` (see Open Question 6 — the `/resync` reply already claims 10).

**Additional per-phase continuity notes:**

- **Phase 2 is the only phase with a user-visible Discord-side effect beyond the restart:**
  slash commands are re-registered, so they briefly re-sync in clients, and the removed
  `/setup` subcommands vanish. Command registration is asynchronous — `Main` queues it after
  `awaitReady()` — so the commands may lag the restart by a few seconds.
- **Phase 5** changes which listener owns `/ping`, `/stats`, `/leaderboard`, `/resync`,
  `/countdelay`, `/setup`. The registered *command shapes* do not change, so no re-register is
  strictly needed — but do it anyway to keep Discord's view and the code in sync.
- **Phase 6** touches the goal-embed-adjacent schedulers indirectly (`DisconnectListener` and
  `GoalsService` both own a `ScheduledExecutorService` with a shutdown hook). Confirm both
  schedulers still shut down cleanly, or the process will hang on stop and get `kill -9`'d,
  which loses writes.
- **No phase requires more than one restart** and none requires a data migration while running.
  Phase 7 requires the bot to be **stopped**, not merely restarted.

---

## 6. Data safety

Six live files. `data/global/achievements.json` (~140 users) and `data/global/stats.json` are
irreplaceable — they are the accumulated history of the server.

```
data/global/achievements.json   achievement unlocks, ~140 users
data/global/stats.json          fame/shame/streaks per user
data/global/suggestions.json    nextId + entries
data/global/config.json         disconnect thread, suggestions notify
data/global/disconnects.json    today's counters + messageId
data/guilds/712304553931833385/{config,state,goals}.json
```

### 6.1 The critical finding: Jackson will erase a file on a schema change

`JsonStore` and `TypedConfigStore` both build a bare `new ObjectMapper()`, which has
`FAIL_ON_UNKNOWN_PROPERTIES` **enabled** by default. Then `JsonStore.loadOrNew()` does:

```java
try { if (Files.exists(path)) return om.readValue(path.toFile(), type); }
catch (Exception e) { ConsoleLog.error(...); }
return defaultSupplier.get();          // <- silently starts empty
```

So if any persisted class loses a field — `UserStats.negCounts`, `SuggestionEntry.guildId`,
`GuildConfig.logThreadId` — the next boot throws on load, logs an ERROR, **starts with an
empty object in memory**, and then the 10-second auto-flush **overwrites the real file with
the empty one.** The error scrolls past in console output; the first symptom is an empty
leaderboard.

**Required, before any field is removed (Phase 0):**

1. `om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)` in both
   `JsonStore` and `TypedConfigStore`.
2. Distinguish "file missing" (fine, use defaults) from "file present but unparseable" (not
   fine). On a parse failure, either refuse to start, or set a `loadFailed` flag that makes
   `flushNow()` a no-op so a bad read can never overwrite a good file.

This is the one behavior change permitted under Non-Goals, because it exists specifically to
protect the refactor.

### 6.2 What is safe and what is not

**Safe — class and package renames do not touch JSON.** Jackson writes field names, never type
names. `SuggestionsState` → `SuggestionsListener.State`, `achievements.AchievementsState` →
`Achievements.State`, package flattening — all invisible to the data. Requirements: nested
classes must be `static`, and must keep a public no-arg constructor (`SuggestionEntry` already
has one alongside its 6-arg constructor — keep both).

**Safe — `Map<Long, X>` keys.** Jackson writes them as JSON strings (`"385943549331636226"`)
and reads them back as `Long`. Unchanged as long as the field name and declared type are
unchanged.

**Not safe — renaming a field.** `UserStats.correct`, `UserAchievements.unlockedAtMillis`,
`CountingState.lastNumber`, `GoalState.goalMessageId`, `SuggestionsState.nextId`, and every
other persisted field name must survive this refactor **byte-identical**. No phase in this plan
renames one. If a rename ever becomes desirable, it needs `@JsonAlias`, not courage.

**Not safe — the achievement id strings.** `achievements.json` keys unlocks by the id string
from `AchievementsCatalog` (`"streak_100"`, `"goal_winner"`, `"cause_fail"`). Phase 4
transcribes the catalog into a new file; a typo in an id silently un-awards that achievement
for everyone. **Verification step for Phase 4:** extract the id list from the old and new
catalogs and diff them before deploying.

**Already-orphaned ids — leave them alone.** `achievements.json` contains `decreasing_count_1`,
`decreasing_count_10`, `decreasing_count_100` for real users, but `AchievementsCatalog.all()`
has no such definitions — they were removed at some point and the unlock records outlived them.
They render as nothing today (`/achievements view` iterates `defs()`, not the user's map). Do
**not** prune them from the JSON; if the defs are ever restored, the unlocks come back. See
Open Question 8.

### 6.3 Path handling — no file moves until Phase 7

Phases 0–6 change only which *code* resolves the paths. `BotConfig.PROD` points at the existing
locations verbatim, including `data/guilds/712304553931833385/`. The directory name looks
vestigial after Phase 2; that is fine. A refactor that both restructures code and moves data
cannot be bisected when something goes wrong.

### 6.4 The deployment scripts are a live hazard

`syncStuff/push-data.txt` runs:

```
synchronize remote "…\JavaCountingBot\data" "data" -mirror -delete
```

That pushes **local** `data/` over **prod** `data/`, mirroring and deleting. If it is run while
local `data/` holds a stale snapshot — which it will, constantly, during this refactor —
**prod's `achievements.json` and `stats.json` are overwritten with old data and any prod-only
file is deleted.** `run-sync.bat` is the safe direction (prod → local, also `-mirror -delete`,
so it clobbers local instead).

Rules for the duration of this refactor:
- Never run `run-push.bat` unless the intent is explicitly "overwrite prod data", and only
  immediately after a `run-sync.bat`.
- Take the dated snapshot **from the pulled copy**, not from whatever is in local `data/`.
- Deploy *code* by copying the shadow jar only. Data and code should never move in the same
  operation.

### 6.5 Per-phase data checklist

Before every deploy: `run-sync.bat` → copy `data/` to `backup/data-YYYY-MM-DD-phaseN/` →
`sha256` all six files → record them.
After every deploy: confirm all six files still parse, confirm `achievements.json` and
`stats.json` byte sizes did not shrink, confirm the six baseline commands from Phase 0 give
matching output.

---

## 7. Testing on the second server

The test server exists so that no phase is first exercised in production.

### 7.1 Setup required before Phase 2

- Hardcode the test guild id, counting channel, and log thread into `BotConfig.TEST`.
- **`BotConfig.TEST.dataDir = Path.of("data-test")`.** The test profile must never open a prod
  JSON file. This is not optional — a local run with a shared data dir will flush over prod
  state.
- Seed `data-test/` with a *copy* of prod data (real shapes, real volume) so the achievements
  and stats paths run against realistic content rather than an empty map.
- Selection via `BOT_ENV=test`, read in `Main` alongside `DISCORD_TOKEN`.

### 7.2 Per-phase test checklist

Run on the test server, with the test profile, before the same jar touches prod.

**Every phase (regression sweep — ~5 minutes):**
1. Boot; confirm no ERROR lines; confirm the boot resync reports a sensible last number.
2. Count three valid numbers with two different accounts → all accepted, number advances.
3. Send a wrong number → deleted, shame incremented, global streak reset.
4. Send the same number twice from one account → second deleted.
5. Send a non-number → deleted.
6. Count twice within `countingDelaySeconds` → deleted, **shame not incremented**.
7. `/stats`, `/leaderboard`, `/achievements view` → non-empty, plausible.
8. Stop gracefully; confirm every JSON mtime advanced; restart; confirm state carried over.

**Phase-specific additions:**
- *Phase 0:* corrupt a copy of `stats.json` deliberately → confirm the bot refuses to overwrite
  it. Add a junk field to a copy → confirm it loads.
- *Phase 1:* diff `/achievements view` output before and after — must be identical.
- *Phase 2:* invite the test bot to a *third* throwaway guild and confirm it is fully inert
  there (no counting, no context created, no thread started). Confirm `/setup status` reflects
  the constants. Confirm the goal embed is **edited**, not reposted.
- *Phase 3:* `/suggest` → check `nextId` increments, the entry appends, the owner DM lands.
  `/suggestion_response` on an existing id → DM lands; on a missing id → clean error. Confirm
  pre-existing entries in `suggestions.json` still load.
- *Phase 4:* diff the old and new catalog id lists (§6.2). Hit a real unlock — count to a
  `count_*` milestone on the test server. `/achievements grant` a known id, an already-held id,
  and a garbage id → three distinct replies.
- *Phase 5:* every command still responds: `/ping /stats /leaderboard /resync /countdelay
  /setup status /goal set|clear|view /suggest /achievements view|grant`. Confirm the permission
  gate on `/countdelay` matches whatever Open Question 2 decides.
- *Phase 6:* force a disconnect (kill the network for ~30s) → confirm one daily message is
  created and then *edited*, not duplicated. Confirm log lines still reach the log thread and
  the console. Let it cross local midnight if feasible, or fake the date key.

### 7.3 Promoting to prod

Same jar, `BOT_ENV` unset (defaults to `PROD`). Never rebuild between test and prod — ship the
exact artifact that passed.

---

## 8. Rollback

Rollback of **code** is cheap; rollback of **data** is not, because the new build starts
flushing within 5–30 seconds of boot. Rollback windows are therefore short by nature.

**Standing procedure, every phase:**
1. Keep the previous phase's shadow jar on the server as `countingbot-<version>.jar` (versions
   come from `build.gradle`, one per phase — `10.0-PHASE1-DEADCODE`,
   `11.0-PHASE2-SINGLESERVER`, …).
2. Keep `backup/data-YYYY-MM-DD-phaseN/` from §6.5.
3. Roll back: stop gracefully → restore the previous jar → **restore data only if the new build
   wrote something wrong** → start → `/resync` → verify against the Phase 0 baseline.

**Abort triggers — stop and roll back immediately if, within 2 minutes of boot:**
- any `JsonStore ... failed to load` or `starting fresh` line appears in the console;
- `/leaderboard` or `/achievements view` comes back empty or visibly shorter;
- any `data/` file shrinks materially versus the snapshot;
- a second goal embed or a second daily disconnect message is posted (means `goals.json` /
  `disconnects.json` lost its `messageId`);
- valid counts are being deleted, or invalid ones are not.

**Phase-specific notes:**
- *Phase 0/1:* pure code. Restore the jar; data is untouched by design.
- *Phase 2 — the one with a data-shaped rollback.* The old build reads
  `data/guilds/<id>/config.json` through `GuildConfig`; the new one reads the same file through
  `BotConfig.Settings`. If the field sets diverge, the old build may not read back what the new
  one wrote. **Mitigation: make Phase 2's `Settings` a field-for-field superset of
  `GuildConfig`** (`countingChannelId`, `countingDelaySeconds`, `enforceDelete`, `logThreadId`,
  `enableLogs`) even though channel ids are now constants — it costs two dead fields and makes
  the rollback purely a jar swap. Drop them in Phase 7 if ever.
  Also: stale slash commands in the other guilds do not come back on rollback if they were
  explicitly cleared. Clearing them is a one-way door — do it after Phase 2 has been stable for
  a few days, not during it.
- *Phase 3/4/5/6:* no persisted field changes, so rollback is a jar swap. The exception is
  Phase 4 shipping with a mistyped achievement id: that is *not* corruption — the JSON keeps the
  unlock, it just stops rendering — so fix forward rather than restoring data.
- *If a rollback happens more than ~60 seconds after boot,* prefer keeping the new data and
  fixing forward. Restoring an older `stats.json`/`achievements.json` discards every count that
  happened in between, which is real user-visible loss.

---

## 9. Non-goals

This is a **simplification pass**. Explicitly out of scope:

- **No new features.** No new commands, no new achievements, no new config options, no
  autocomplete for `/achievements grant`, no database.
- **No behavior changes to the counting rules.** Strict parsing, the comma rules, the cooldown,
  "same user twice", delete-on-invalid, "don't shame cooldown violations", the `!`-prefix
  bypass, the saboteur/`goal_winner` unlocks — all preserved exactly.
  `CountVerifier.parseStrictCount` moves; it does not change.
- **No message-text changes.** Embeds, DM wording, and log lines stay byte-identical, so a
  before/after diff is a valid correctness check.
- **The disconnect logging rework itself is not in this plan.** Phase 6 *unblocks* it by putting
  everything in one file. Actually changing what gets logged is the next project — keeping them
  separate is what makes Phase 6 safely revertable.
- **No re-adding multi-server support** "just in case". The test server is hardcoded, not
  generalized. If a third server ever matters, add a third constant.
- **No test framework.** Zero tests exist; introducing JUnit is a worthwhile separate project,
  not a precondition. §7 is the substitute.
- **No dependency upgrades**, no Gradle restructuring, no Java version change.
- **No data migration.** Same files, same field names, same locations (through Phase 6).
- **Two deliberate exceptions**, both included because the refactor is unsafe without them: the
  Jackson hardening in §6.1, and the `DisconnectListener` locking fix in §3.6.

---

## 10. Open questions

Answers needed before the phase in brackets can be planned in detail.

1. **Test bot: separate Discord application and token, or the same bot in both servers?**
   [blocks Phase 2 testing] If it is the same token, a local test run and the prod process are
   the *same bot user* — both would receive prod messages and both would try to delete them,
   double-counting stats. Strong recommendation: a second application with its own token.
   Confirm before §7 setup.

2. **`/countdelay` vs `/setup setdelay` — which one survives?** [Phase 5] They write the same
   `countingDelaySeconds` field with different permission gates (`BAN_MEMBERS` vs
   `MANAGE_SERVER || BAN_MEMBERS`). Recommendation: keep `/setup setdelay`, drop `/countdelay`.

3. **Should `/setup setcountingchannel` and `/setup setlogthread` be removed entirely?**
   [Phase 2] They configure values that become compile-time constants. Removing them means a
   channel change requires a rebuild — fine if channels never move, a real annoyance if they do.
   Recommendation: remove; keep `/setup status`.

4. **`suggestionsThreadId` is `"0"` in prod, so `postToSuggestionsChannel` has never run.**
   [Phase 3] Was posting suggestions to a thread ever wanted? Delete the method, or hardcode a
   real thread id?

5. **What should the disconnect rework actually do?** [shapes Phase 6, blocks the follow-up]
   Today it posts one message per day and edits it as disconnects accumulate, into the same
   channel the normal logs go to. What has been bothering you — the format, the once-a-day
   rollover, sharing a channel with counting logs, the 30-second polling, or missing detail
   (duration, reason, reconnect count)? Phase 6 only restructures; knowing the target shape
   means the restructure can put the seams where you'll need them.

6. **`RESYNC_HISTORY = 3`, but `/resync` replies "no valid count found in last 10 messages".**
   [Phase 2, affects §5] Which is right? 3 is thin for a post-downtime resync. Recommendation:
   raise to 10 and make the reply read from the constant.

7. **Keep the `!`-prefix bypass for user `733113260496126053` in `CountingListener`?** [Phase 2]
   Currently any message from you starting with `!` is ignored rather than deleted. Intentional
   and still wanted?

8. **`decreasing_count_1` / `_10` / `_100` exist in `achievements.json` but have no catalog
   definitions.** [Phase 4] Were they removed on purpose, or lost? Restore the defs (the unlocks
   reappear for those users), or leave them orphaned?

9. **Confirm these are safe to delete outright** [Phase 1]: `UserStats.negCounts`, the
   `globalStreakCurrent`/`globalStreakBest` snapshot fields in `AchievementContext`,
   `Conditions.allOf`/`anyOf`/`globalAtLeast`, and the unused `StatKey`/`GlobalKey` constants.
   All are currently unreachable — was anything half-built that should be finished instead of
   deleted?

10. **Do stats and achievements stay shared, or does the test server get its own?**
    [Phase 2 + §7] §7 assumes separate (`data-test/`), which means test unlocks and counts do not
    pollute prod history. Confirm that is what you want, and whether test should be seeded from a
    copy of prod data or start empty.

11. **How is the bot actually stopped on the server?** [§5, every phase] `systemctl stop`,
    `screen`/`tmux` + Ctrl-C, a `kill`, or the process just getting killed on reboot? A `kill -9`
    skips the shutdown hooks and loses up to 30 seconds of writes, which changes the deployment
    procedure in §5 from "stop and verify" to "flush first, then stop".

12. **Are you keeping the bot in the other 3 guilds?** [Phase 2] If yes, it needs the inert guard
    plus a one-time command clear. If no, kicking it is simpler and makes the guard a pure safety
    net.
