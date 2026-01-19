package org.gudu0.countingbot.achievements;

import org.gudu0.countingbot.counting.CountingState;
import org.gudu0.countingbot.counting.StateStore;
import org.gudu0.countingbot.logging.LogService;
import org.gudu0.countingbot.stats.StatsStore;
import org.gudu0.countingbot.stats.UserStats;
import org.gudu0.countingbot.util.ConsoleLog;

import java.util.List;

public class AchievementsService {
    private final AchievementsStore store;
    private final StateStore stateStore;
    private final StatsStore statsStore;
    private final LogService logs;

    @SuppressWarnings("SpellCheckingInspection")
    private final List<AchievementDef> defs = AchievementsCatalog.all();

    public AchievementsService(AchievementsStore store, StateStore stateStore, StatsStore statsStore, LogService logs) {
        this.store = store;
        this.stateStore = stateStore;
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

    public void onTrigger(AchievementTrigger trigger, long guildId, long userId) {
        long now = System.currentTimeMillis();

        // Build snapshots under locks (short + safe)
        AchievementContext.CountingSnapshot cs;
        synchronized (stateStore.lock) {
            CountingState s = stateStore.state();
            cs = new AchievementContext.CountingSnapshot(
                    s.lastNumber,
                    s.globalStreakCurrent,
                    s.globalStreakBest
            );
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
                        logs.log("Achievement unlocked: " + def.title + ", by <@" + userId + ">!");
                        ConsoleLog.info("Achievements", "Unlocked id=" + def.id + " title=\"" + def.title + "\" userId=" + userId);
                    }
                }
            }

            if (unlockedAny) store.markDirty();
        }
    }

    public void unlockById(@SuppressWarnings("unused") long guildId, long userId, String achievementId) {
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
                logs.log("Achievement unlocked: " + def.title + ", by <@" + userId + ">!");
            }
        }
    }

}
