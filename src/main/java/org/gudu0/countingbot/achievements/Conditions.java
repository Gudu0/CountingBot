package org.gudu0.countingbot.achievements;

import java.util.List;

@SuppressWarnings("unused")
public final class Conditions {
    private Conditions() {}

    public static Condition userStatAtLeast(StatKey key, long value) {
        return new UserStatAtLeast(key, value);
    }

    public static Condition globalAtLeast(GlobalKey key, long value) {
        return new GlobalAtLeast(key, value);
    }

    public static Condition globalEquals(GlobalKey key, long value) {
        return new GlobalEquals(key, value);
    }

    // For “manual-only” achievements (unlocked via code, not via triggers)
    public static Condition never() {
        return ctx -> false;
    }


    public static Condition allOf(Condition... all) {
        return new AllOf(List.of(all));
    }

    public static Condition anyOf(Condition... any) {
        return new AnyOf(List.of(any));
    }

    private record UserStatAtLeast(StatKey key, long value) implements Condition {
        @Override public boolean matches(AchievementContext ctx) {
            long v = switch (key) {
                case CORRECT -> ctx.stats.correct();
                case INCORRECT -> ctx.stats.incorrect();
                case CURRENT_STREAK -> ctx.stats.currentStreak();
                case BEST_STREAK -> ctx.stats.bestStreak();
                case POS_COUNTS -> ctx.stats.posCounts();
            };
            return v >= value;
        }
    }

    @SuppressWarnings("unused")
    private record GlobalAtLeast(GlobalKey key, long value) implements Condition {
        @Override public boolean matches(AchievementContext ctx) {
            long v = switch (key) {
                case LAST_NUMBER -> ctx.counting.lastNumber();
                case GLOBAL_STREAK_CURRENT -> ctx.counting.globalStreakCurrent();
                case GLOBAL_STREAK_BEST -> ctx.counting.globalStreakBest();
            };
            return v >= value;
        }
    }

    @SuppressWarnings("unused")
    private record AllOf(List<Condition> all) implements Condition {
        @Override public boolean matches(AchievementContext ctx) {
            for (Condition c : all) if (!c.matches(ctx)) return false;
            return true;
        }
    }

    @SuppressWarnings("unused")
    private record AnyOf(List<Condition> any) implements Condition {
        @Override public boolean matches(AchievementContext ctx) {
            for (Condition c : any) if (c.matches(ctx)) return true;
            return false;
        }
    }

    private record GlobalEquals(GlobalKey key, long value) implements Condition {
        @Override public boolean matches(AchievementContext ctx) {
            long v = switch (key) {
                case LAST_NUMBER -> ctx.counting.lastNumber();
                case GLOBAL_STREAK_CURRENT -> ctx.counting.globalStreakCurrent();
                case GLOBAL_STREAK_BEST -> ctx.counting.globalStreakBest();
            };
            return v == value;
        }
    }

}
