package org.gudu0.countingbot.stats;

import java.util.HashMap;
import java.util.Map;

public class StatsData {
    // userId -> stats
    @SuppressWarnings("CanBeFinal")
    public Map<Long, UserStats> users = new HashMap<>();

    /** Creates a stats entry if missing. Use for mutation. */
    public UserStats getOrCreate(long userId) {
        return users.computeIfAbsent(userId, k -> new UserStats());
    }

    /** Returns an existing stats entry if present, otherwise returns a new zeroed stats object (not stored). */
    public UserStats getOrDefault(long userId) {
        UserStats s = users.get(userId);
        return s != null ? s : new UserStats();
    }

}
