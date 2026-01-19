package org.gudu0.countingbot.achievements;

import java.util.HashMap;
import java.util.Map;

public class UserAchievements {
    @SuppressWarnings("CanBeFinal")
    public Map<String, Long> unlockedAtMillis = new HashMap<>();

    public boolean isUnlocked(String id) {
        return unlockedAtMillis.containsKey(id);
    }

    public void unlock(String id, long atMillis) {
        unlockedAtMillis.putIfAbsent(id, atMillis);
    }
}
