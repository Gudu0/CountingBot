package org.gudu0.countingbot.achievements;

public interface Condition {
    boolean matches(AchievementContext ctx);
}
