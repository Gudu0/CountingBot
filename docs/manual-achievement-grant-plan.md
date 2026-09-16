# Plan: Admin Manual Achievement Grant

Status: planning only, no code written yet. All file/class names below are real,
taken from the current tree under `src/main/java/org/gudu0/countingbot/`.

## 1. Command spec

**Name / syntax:** extend the existing `/achievements` command with a subcommand,
rather than adding a new top-level command — this matches how `/goal` and `/setup`
already split behavior into subcommands (`GoalCommandListener`, `SetupListener`).

```
/achievements view [user]          <- current behavior (today: /achievements user:<optional>)
/achievements grant user:<user> achievement:<id>
```

- `user` (required, `OptionType.USER`): the target to grant the achievement to.
- `achievement` (required, `OptionType.STRING`): the achievement id as defined in
  `AchievementsCatalog.all()` (e.g. `streak_100`, `goal_winner`). No autocomplete
  exists anywhere else in this codebase (`SetupListener`, `GoalCommandListener`,
  `AchievementsCommandListener` all use plain required options, no
  `addChoice`/autocomplete), so this plan follows that precedent: plain string
  input, validated server-side against the catalog after the command fires. See
  Open Questions for whether that's acceptable given ids aren't shown anywhere
  in-product today.

**Permission/role check:** reuse `CommandGuards.requireAdmin` (already defined in
`commands/CommandGuards.java`, used implicitly by the same permission set as
`SetupListener`'s inline check: `MANAGE_SERVER` or `BAN_MEMBERS`). Unlike
`SetupListener` (which inlines the permission check instead of using
`CommandGuards`), the new subcommand should implement `CommandGuards` the way
`GoalCommandListener` does, and call `requireGuild(event)` + `requireAdmin(event)`
before doing anything else.

**This is a per-guild permission check on a global data store** — see Open
Questions #1.

## 2. New files

None are strictly required — this fits as a new subcommand branch inside the
existing `AchievementsCommandListener`, following the same pattern
`SetupListener` and `GoalCommandListener` use for their own subcommands (one
listener class, `switch (sub)`).

If you'd rather keep the grant path physically separate for clarity/testability,
the only new file worth adding is:

- **`src/main/java/org/gudu0/countingbot/achievements/AchievementGrantResult.java`**
  — purpose: a small enum so `AchievementsService` can report *why* a grant did
  or didn't happen, instead of the current silent no-op behavior in
  `unlockById` (see section 4). No existing type in the achievements package
  serves this purpose.
  ```java
  public enum AchievementGrantResult {
      GRANTED,
      ALREADY_UNLOCKED,
      UNKNOWN_ACHIEVEMENT_ID
  }
  ```

No new listener class, no new store, no new state class — `AchievementsStore`,
`AchievementsState`, and `UserAchievements` already model exactly what's needed
(a per-user map of unlocked ids → timestamps), and admin grants are just another
way to populate that same map.

## 3. Existing files to modify

- **`src/main/java/org/gudu0/countingbot/achievements/AchievementsService.java`**
  - Change `unlockById(long guildId, long userId, String achievementId)` to
    return `AchievementGrantResult` instead of `void`, so callers can
    distinguish "granted", "already had it", and "no such id" — today it
    silently returns in the last two cases (lines ~138 and ~142), which is fine
    for its current caller (`CountingListener`, which ignores the outcome) but
    is not fine for an admin command that must tell the admin what happened.
  - This is a signature change on a method already called from
    `CountingListener.java` (`cause_fail` and `goal_winner` grants) — those two
    call sites can keep ignoring the return value, so this is source-compatible
    for them.
  - No new method needed — the admin grant path calls the *same*
    `unlockById(guildId, userId, achievementId)` that `CountingListener`
    already uses for `cause_fail`/`goal_winner`. This is the "reuse, don't
    duplicate" path requested.

- **`src/main/java/org/gudu0/countingbot/commands/AchievementsCommandListener.java`**
  - Restructure `onSlashCommandInteraction` to branch on
    `event.getSubcommandName()` (`"view"` vs `"grant"`), matching the
    `switch (sub)` pattern in `SetupListener`/`GoalCommandListener`.
  - Move the current body (the embed-building logic, lines ~38–72) under the
    `"view"` case unchanged.
  - Add a `"grant"` case: `requireGuild`/`requireAdmin` gate, resolve the
    `user` and `achievement` options, call
    `achievements.unlockById(guildId, targetUserId, achievementId)`, and reply
    (ephemeral) based on the returned `AchievementGrantResult`.
  - Have `AchievementsCommandListener` implement `CommandGuards` (it currently
    doesn't — it's the only command listener with permission-sensitive
    potential that doesn't already implement it, though today it has no
    permission check at all since `/achievements view` is open to everyone).

- **`src/main/java/org/gudu0/countingbot/Main.java`**
  - In `registerGuildCommandsOne(Guild g)` (~line 156), change the
    `Commands.slash("achievements", ...)` registration from a flat command with
    a `user` option into a command with two subcommands via
    `.addSubcommands(...)`, mirroring how `/goal` and `/setup` are registered
    just above/below it in the same method:
    ```java
    Commands.slash("achievements", "View or grant achievements")
        .addSubcommands(
            new SubcommandData("view", "View achievements")
                .addOption(OptionType.USER, "user", "User to view (defaults to you)", false),
            new SubcommandData("grant", "Manually grant a user an achievement (admin only)")
                .addOption(OptionType.USER, "user", "User to grant the achievement to", true)
                .addOption(OptionType.STRING, "achievement", "Achievement id", true)
        )
    ```
  - This is a **breaking change** to the current `/achievements user:<x>` usage
    — see Open Questions #2.

- **`src/main/java/org/gudu0/countingbot/achievements/AchievementsCatalog.java`**
  - No code change required, but the grant command's validation depends on
    `AchievementsCatalog.all()` being the single source of truth for valid ids.
    Worth noting: this file currently contains catalog entries with no matching
    id anywhere else, and `data/global/achievements.json` on disk contains
    unlocked ids (`decreasing_count_1`, `decreasing_count_10`) with **no**
    matching `AchievementDef` in this file at all (found while documenting the
    achievement system earlier in this conversation). The grant command's
    validation will correctly reject those stale ids as unknown, which is
    arguably correct but worth being aware of.

## 4. Data flow

1. Admin runs `/achievements grant user:@Target achievement:streak_100`.
2. `AchievementsCommandListener` (subcommand `"grant"`) checks
   `requireGuild` + `requireAdmin` (via `CommandGuards`, same as
   `GoalCommandListener`'s admin gate).
3. Listener resolves `targetUserId` from the `user` option and `achievementId`
   from the `achievement` option (raw string, no server-side listing).
4. Listener calls `achievements.unlockById(guildId, targetUserId, achievementId)`
   — **the exact same method** `CountingListener` already calls for
   `cause_fail`/`goal_winner`. No parallel unlock path is created.
5. Inside `AchievementsService.unlockById` (`achievements/AchievementsService.java:131`),
   under the existing `store.lock`:
   - Looks up the `AchievementDef` by id in `defs` (from `AchievementsCatalog.all()`).
   - If not found → returns `UNKNOWN_ACHIEVEMENT_ID` (new behavior; currently
     silently returns).
   - Gets/creates the user's `UserAchievements` via `store.state().getOrCreate(userId)`.
   - If already unlocked (`ua.isUnlocked(def.id)`) → returns `ALREADY_UNLOCKED`
     (new; currently silently returns).
   - Otherwise: `ua.unlock(def.id, now)`, `store.markDirty()`, and — **exactly
     like an earned unlock** — if `def.logOnUnlock` and `logs != null`, calls
     `logs.log(guildId, "Achievement unlocked: " + def.title + ", by <@" + userId + ">!")`.
     This is the same `LogService.log(...)` call site used for earned
     unlocks, so a manually granted achievement produces an identical log-thread
     message (if the guild has one configured) and identical console log line —
     no separate "granted by admin" notification path is introduced, since
     `unlockById` doesn't currently distinguish *why* it was called (manual
     saboteur/goal-winner unlock vs. an admin grant would look identical in the
     log). See Open Questions #3 if you want that distinguished.
   - Returns `GRANTED`.
6. Storage persistence: identical to every other unlock — `store.markDirty()`
   flags the `JsonStore<AchievementsState>` dirty, and the existing
   `achievementsStore.startAutoFlush(10)` (started once in `Main.java:58`)
   flushes to `data/global/achievements.json` within 10s (or on clean shutdown
   via the `JsonStore` shutdown hook). No new persistence code needed.
7. Listener replies ephemerally to the admin based on the
   `AchievementGrantResult`:
   - `GRANTED` → "Granted **{title}** to {user}."
   - `ALREADY_UNLOCKED` → "{user} already has **{title}**."
   - `UNKNOWN_ACHIEVEMENT_ID` → "No achievement with id `{achievement}`."

## 5. Edge cases to handle

- **User already has the achievement** → `unlockById` returns
  `ALREADY_UNLOCKED`; command replies clearly instead of pretending success
  (today's silent-no-op behavior would look identical to success from the
  admin's point of view — that's the main bug this plan needs to avoid
  reintroducing).
- **Invalid/unknown achievement id** → `unlockById` returns
  `UNKNOWN_ACHIEVEMENT_ID`. Because the id is free-text (no autocomplete/choices
  anywhere in this codebase), typos are expected; the reply should probably
  echo back the input verbatim.
- **Invalid target user** — JDA's `OptionType.USER` resolves to a real Discord
  user/member at the API level, so "invalid user" mostly reduces to: user not
  in the guild (JDA still allows resolving a `User` who isn't a `Member`, since
  `AchievementsCommandListener` currently calls `.getAsUser()`, not
  `.getAsMember()`). Decide whether granting to a user who has left the guild
  should be allowed — the achievements store is global/bot-wide by user id
  (`data/global/achievements.json`), so nothing technically prevents it. See
  Open Questions #4.
- **Permission failure (non-admin)** — handled by `requireAdmin` before any
  option parsing happens, mirroring `GoalCommandListener`'s gate; replies
  ephemerally, no state touched, matches existing UX in `SetupListener`
  ("You don't have permission to use /setup.").
- **Command used outside a guild (DM)** — handled by `requireGuild`, same as
  every other guild-scoped command.
- **Manual-only achievements (`cause_fail`, `goal_winner`)** — these already
  exist in the catalog specifically as grant-only targets (`EnumSet.noneOf`
  triggers, `never()` condition, per the comment
  `// Manual-only (unlocked from CountingListener via unlockById)` in
  `AchievementsCatalog.java:87`). The new admin command can target these too,
  which is presumably desirable (e.g. an admin manually awarding "Saboteur"
  after a dispute) — but it can *also* target every trigger-based achievement
  (`count_1000`, `streak_100`, etc.), bypassing their conditions entirely. Confirm
  that's intended — see Open Questions #5.
- **Race with an in-flight organic unlock** — both paths take `store.lock`
  (`AchievementsService.onTrigger` and `unlockById` both synchronize on
  `store.lock`), so there's no data race; worst case is a harmless
  `ALREADY_UNLOCKED` reply if the user organically earns it in the same instant
  an admin grants it.

## 6. Open questions

1. **Achievements are global (bot-wide), but the grant command's admin check is
   per-guild.** `AchievementsStore` persists to a single
   `data/global/achievements.json`, not per-guild — yet whoever runs
   `/achievements grant` is only proven to be an admin *in the guild they ran it
   in* (same as every other admin-gated command here). An admin in Guild A
   could grant achievements to any Discord user id, and that grant is visible
   from every other guild the bot is in. Is that acceptable, or should grants
   be restricted/logged more prominently given the global blast radius?

2. **Restructuring `/achievements` into subcommands is a breaking change** for
   anyone currently using `/achievements user:<x>` — after this change it
   becomes `/achievements view user:<x>`. Do you want that breaking change, or
   would you prefer a separate top-level command (e.g. `/grantachievement`) to
   leave the existing `/achievements` command untouched?

3. **Should a manually-granted unlock be distinguishable from an earned one**
   in the log-thread message / console log / `/achievements view` output? Right
   now `unlockById` is shared by `cause_fail`, `goal_winner`, and (per this
   plan) admin grants — all three currently produce the identical
   `"Achievement unlocked: {title}, by <@{userId}>!"` message with no
   indication of *how* it was unlocked or *who* the admin was. If you want
   "granted by @Admin" in the log or stored alongside the unlock timestamp,
   that requires widening `UserAchievements.unlockedAtMillis` (currently
   `Map<String, Long>`) into something richer, which is a bigger, separate
   change worth deciding on up front rather than bolting on later.

4. **Should granting to a user who isn't currently a member of the guild be
   allowed?** The store is keyed by raw Discord user id with no guild
   membership check at grant time.

5. **Should the grant command be blocked from targeting the two manual-only
   achievements (`cause_fail`, `goal_winner`) or trigger-based achievements, or
   is "grant literally anything in the catalog" the intended scope?** The plan
   above assumes unrestricted — any id in `AchievementsCatalog.all()` is
   grantable — but that's an assumption, not a confirmed requirement.

6. **The stale `decreasing_count_1`/`decreasing_count_10`/etc. ids already
   sitting in `data/global/achievements.json`** (unlocked for real users, no
   matching `AchievementDef`) — out of scope for this feature, but flagging
   again since a "grant by id" command makes catalog/storage drift more
   visible (an admin might reasonably try to grant `decreasing_count_1` to
   someone, expecting it to exist, and get an "unknown id" error for an
   achievement other users clearly have).
