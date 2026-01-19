package org.gudu0.countingbot.achievements;

import java.util.HashMap;
import java.util.Map;

public class AchievementsState {
    @SuppressWarnings("CanBeFinal")
    public Map<Long, UserAchievements> users = new HashMap<>();

    public UserAchievements getOrCreate(long userId) {
        return users.computeIfAbsent(userId, k -> new UserAchievements());
    }
}
