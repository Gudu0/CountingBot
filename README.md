# Counting Bot (Java)

A **strict, deletion-only Discord counting bot** designed to run quietly in counting channels while enforcing rules automatically.

The bot supports **multiple servers at once**, stores **per-server counting state**, and keeps **global stats and achievements** across all servers it’s in.

There is **no bot chatter in the counting channel*** — invalid messages are simply deleted.
###### \* The bot does send a one-time message to pin the goal embed. This should only happen once, and the bot edits this one message when needed.

---

## What This Bot Does

- Enforces a **counting game** in a single channel per server
- Deletes invalid messages
- Tracks:
  - current number
  - streaks
  - user stats (globally)
  - achievements
- Supports **goals** with a single edited embed
- Can be fully configured **while running** via slash commands
- Automatically disables deletion if permissions are missing (fails safe)

---

## Counting Rules

In the configured counting channel:

### Message Format
- Must be **digits only**
- No spaces, no text
- No negative numbers
- No leading zeros 

### Validation Rules
- Number must be exactly **last number + 1**
- **Same user cannot count twice in a row**
- Optional cool-down between valid counts 

### Outcomes
- **Valid count**
  - Advances the number
  - Updates streaks
  - Updates global user stats
  - Triggers achievements
- **Invalid count**
  - Message is deleted (if enforcement is enabled)
  - Streak is reset
- **Cooldown violation**
  - Message is deleted
  - No shame / penalty applied

---

## Setup (Server Admins)

### 1. Invite the Bot
Invite the bot with these permissions:
- `View Channels`
- `Read Message History`
- `Manage Messages`
- `Use Slash Commands`

> If `Manage Messages` is missing, the bot will **automatically disable deletions**.

---

### 2. Configure the Server
All setup is done via `/setup` commands.

#### Set the counting channel
`/setup setcountingchannel`


#### Set the delay between valid counts (seconds)
`/setup setdelay <seconds>`


#### Enable or disable deletion enforcement
`/setup setenforcedelete <true|false>`


#### Enable or disable logging
`/setup setenablelogs <true|false>`


#### View current configuration
`/setup status`


Changes take effect immediately — **no restart required**.

---

## Slash Commands

All commands are **ephemeral** (only visible to the user).

### General
- `/ping` — check bot status
- `/stats [user]` — view counting stats
- `/leaderboard` — top counters (global)
- `/achievements [user]` — unlocked achievements

### Counting
- `/resync` — re-scan the counting channel and rebuild state

### Goals (per server)
- `/goal set <number>` — set a counting goal
- `/goal clear` — remove the current goal
- `/goal view` — view the active goal

Goals are displayed using **one embed message**, which is edited instead of reposted.

### Suggestions
- `/suggest <text>` — submit a suggestion to me!

---

## Multi-Server Support

- Each server has its **own**:
  - counting channel
  - delay
  - enforcement settings
  - counting state
  - goal state
- Stats and achievements are **global across all servers**

The bot can be added to new servers **while running** and configured immediately.

---

## Safety & Permissions

On startup and after setup changes, the bot checks:
- counting channel exists
- bot can see the channel
- required permissions are present

If something is wrong:
- deletion enforcement is **disabled automatically**
- the bot becomes passive until fixed

This prevents accidental mass deletion.

---

## Status

- Multi-server ready
- Safe by default
- Designed for quiet, strict counting
- Actively maintained

---


> Written with [StackEdit](https://stackedit.io/).
