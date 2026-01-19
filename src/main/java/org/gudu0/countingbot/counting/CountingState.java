package org.gudu0.countingbot.counting;

import java.util.HashMap;
import java.util.Map;

public class CountingState {
    public long lastNumber = -1;
    public long lastUserId = 0;
    public long lastMessageId = 0;
    public long globalStreakCurrent = 0;
    public long globalStreakBest = 0;


    // userId -> last time (millis) they made a VALID count
    @SuppressWarnings("CanBeFinal")
    public Map<Long, Long> userLastValidCountAt = new HashMap<>();
}
