package org.gudu0.countingbot.achievements;

import org.gudu0.countingbot.counting.CountingState;
import org.gudu0.countingbot.counting.StateStore;
import org.gudu0.countingbot.guild.GuildContext;
import org.gudu0.countingbot.guild.GuildManager;
import org.gudu0.countingbot.logging.LogService;
import org.gudu0.countingbot.stats.StatsStore;
import org.gudu0.countingbot.stats.UserStats;
import org.gudu0.countingbot.util.ConsoleLog;

import java.util.List;

public class AchievementsService {
    private final AchievementsStore store;

    // Multi-guild: counting state is per guild.
    private final GuildManager guilds;

    // Legacy fallback (single guild) so older code can still construct this.
    private final StateStore legacyStateStore;

    private final StatsStore statsStore;
    private final LogService logs;

    @SuppressWarnings("SpellCheckingInspection")
    private final List<AchievementDef> defs = AchievementsCatalog.all();

    /**
     * Multi-guild constructor.
     * Achievements are global (stored under data/global/achievements.json), but
     * counting snapshots (streak/lastNumber) come from the triggering guild.
     */
    public AchievementsService(AchievementsStore store, GuildManager guilds, StatsStore statsStore, LogService logs) {
        this.store = store;
        this.guilds = guilds;
        this.legacyStateStore = null;
        this.statsStore = statsStore;
        this.logs = logs;
    }

    /**
     * Legacy constructor (single guild). Keep this around so older code doesn't explode.
     */
    public AchievementsService(AchievementsStore store, StateStore stateStore, StatsStore statsStore, LogService logs) {
        this.store = store;
        this.guilds = null;
        this.legacyStateStore = stateStore;
        this.statsStore = statsStore;
        this.logs = logs;
    }

    @SuppressWarnings("SpellCheckingInspection")
    public List<AchievementDef> defs() {
        return defs;
    }

    public UserAchievements userAchievements(long userId) {
        synchronized (store.lock) {
            return store.state().getOrCreate(userId);
        }
    }

    private StateStore stateStoreFor(long guildId) {
        if (guilds != null) {
            GuildContext ctx = guilds.get(guildId);
            return ctx.stateStore;
        }
        return legacyStateStore;
    }

    public void onTrigger(AchievementTrigger trigger, long guildId, long userId) {
        long now = System.currentTimeMillis();

        // Build snapshots under locks (short + safe)
        AchievementContext.CountingSnapshot cs;
        StateStore ss = stateStoreFor(guildId);
        if (ss == null) {
            // Should not happen unless constructed wrong.
            cs = new AchievementContext.CountingSnapshot(-1, 0, 0);
        } else {
            synchronized (ss.lock) {
                CountingState s = ss.state();
                cs = new AchievementContext.CountingSnapshot(
                        s.lastNumber,
                        s.globalStreakCurrent,
                        s.globalStreakBest
                );
            }
        }

        AchievementContext.UserStatsSnapshot us;
        synchronized (statsStore.lock) {
            UserStats u = statsStore.data().getOrCreate(userId);
            us = new AchievementContext.UserStatsSnapshot(
                    u.correct,
                    u.incorrect,
                    u.currentStreak,
                    u.bestStreak,
                    u.posCounts
            );
        }

        AchievementContext ctx = new AchievementContext(guildId, userId, now, cs, us);

        boolean unlockedAny = false;

        synchronized (store.lock) {
            UserAchievements ua = store.state().getOrCreate(userId);

            for (AchievementDef def : defs) {
                if (!def.triggers.contains(trigger)) continue;
                if (ua.isUnlocked(def.id)) continue;

                if (def.condition.matches(ctx)) {
                    ua.unlock(def.id, now);
                    unlockedAny = true;

                    if (def.logOnUnlock && logs != null) {
                        // No mention ping in logs; keep it plain.
                        logs.log(guildId, "Achievement unlocked: " + def.title + ", by <@" + userId + ">!");
                        ConsoleLog.info("Achievements", "Unlocked id=" + def.id + " title=\"" + def.title + "\" userId=" + userId + " guildId=" + guildId);
                    }
                }
            }

            if (unlockedAny) store.markDirty();
        }
    }

    public void unlockById(long guildId, long userId, String achievementId) {
        long now = System.currentTimeMillis();

        AchievementDef def = null;
        for (AchievementDef d : defs) {
            if (d.id.equals(achievementId)) { def = d; break; }
        }
        if (def == null) return;

        synchronized (store.lock) {
            UserAchievements ua = store.state().getOrCreate(userId);
            if (ua.isUnlocked(def.id)) return;

            ua.unlock(def.id, now);
            store.markDirty();

            if (def.logOnUnlock && logs != null) {
                logs.log(guildId, "Achievement unlocked: " + def.title + ", by <@" + userId + ">!");
            }
        }
    }
}
