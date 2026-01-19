package org.gudu0.countingbot.achievements;

@SuppressWarnings({"ClassCanBeRecord", "unused"})
public class AchievementContext {
    public final long guildId;
    public final long userId;
    public final long nowMillis;

    public final CountingSnapshot counting;
    public final UserStatsSnapshot stats;

    public AchievementContext(long guildId, long userId, long nowMillis,
                              CountingSnapshot counting,
                              UserStatsSnapshot stats) {
        this.guildId = guildId;
        this.userId = userId;
        this.nowMillis = nowMillis;
        this.counting = counting;
        this.stats = stats;
    }

    public record CountingSnapshot(long lastNumber, long globalStreakCurrent, long globalStreakBest) {}
    public record UserStatsSnapshot(long correct, long incorrect, long currentStreak, long bestStreak, long posCounts) {}
}
