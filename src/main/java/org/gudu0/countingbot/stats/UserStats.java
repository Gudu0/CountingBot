package org.gudu0.countingbot.stats;

@SuppressWarnings("unused")
public class UserStats {
    public long correct = 0;
    public long incorrect = 0;

    // streak = consecutive correct counts (not total chain streak, just their own)
    public long currentStreak = 0;
    public long bestStreak = 0;

    public long lastCorrectAtMs = 0;
    public long lastIncorrectAtMs = 0;

    public long posCounts = 0;
    public long negCounts = 0;

    public void onCorrect(long nowMs) {
        correct++;
        currentStreak++;
        if (currentStreak > bestStreak) bestStreak = currentStreak;
        lastCorrectAtMs = nowMs;
    }

    public void onIncorrect(long nowMs) {
        incorrect++;
        currentStreak = 0;
        lastIncorrectAtMs = nowMs;
    }
}
