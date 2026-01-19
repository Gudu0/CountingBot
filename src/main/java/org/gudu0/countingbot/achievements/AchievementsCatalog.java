package org.gudu0.countingbot.achievements;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import static org.gudu0.countingbot.achievements.Conditions.*;

public final class AchievementsCatalog {
    private AchievementsCatalog() {}

    public static List<AchievementDef> all() {
        return List.of(
                // Count milestones (exact number sent)
                def("count_1", "Count to 1", "Counted 1 for the first time.",
                        tr(AchievementTrigger.VALID_COUNT),
                        globalEquals(GlobalKey.LAST_NUMBER, 1)
                ),

                def("count_10", "Count to 10", "Successfully counted to 10.",
                        tr(AchievementTrigger.VALID_COUNT),
                        globalEquals(GlobalKey.LAST_NUMBER, 10)
                ),

                def("count_100", "Count to 100", "Successfully counted to 100.",
                        tr(AchievementTrigger.VALID_COUNT),
                        globalEquals(GlobalKey.LAST_NUMBER, 100)
                ),

                def("count_1000", "Count to 1000", "Successfully counted to 1000.",
                        tr(AchievementTrigger.VALID_COUNT),
                        globalEquals(GlobalKey.LAST_NUMBER, 1000)
                ),

                def("count_10000", "Count to 10,000", "Successfully counted to 10,000.",
                        tr(AchievementTrigger.VALID_COUNT),
                        globalEquals(GlobalKey.LAST_NUMBER, 10_000)
                ),

                // Personal streak milestones
                def("streak_10", "10 Streak", "Reached a streak of 10 without failing.",
                        tr(AchievementTrigger.VALID_COUNT),
                        userStatAtLeast(StatKey.BEST_STREAK, 10)
                ),

                def("streak_50", "50 Streak", "Reached a streak of 50 without failing.",
                        tr(AchievementTrigger.VALID_COUNT),
                        userStatAtLeast(StatKey.BEST_STREAK, 50)
                ),

                def("streak_100", "100 Streak", "Reached a streak of 100 without failing.",
                        tr(AchievementTrigger.VALID_COUNT),
                        userStatAtLeast(StatKey.BEST_STREAK, 100)
                ),

                def("streak_500", "500 Streak", "Reached a streak of 500 without failing.",
                        tr(AchievementTrigger.VALID_COUNT),
                        userStatAtLeast(StatKey.BEST_STREAK, 500)
                ),

                def("streak_1000", "1000 Streak", "Reached a streak of 1000 without failing.",
                        tr(AchievementTrigger.VALID_COUNT),
                        userStatAtLeast(StatKey.BEST_STREAK, 1000)
                ),

                // “Increasing counter” == total valid counts sent (posCounts)
                def("increasing_count_1", "Increasing Counter 1", "Sent 1 increasing numbers in the count.",
                        tr(AchievementTrigger.VALID_COUNT),
                        userStatAtLeast(StatKey.POS_COUNTS, 1)
                ),

                def("increasing_count_10", "Increasing Counter 10", "Sent 10 increasing numbers in the count.",
                        tr(AchievementTrigger.VALID_COUNT),
                        userStatAtLeast(StatKey.POS_COUNTS, 10)
                ),

                def("increasing_count_100", "Increasing Counter 100", "Sent 100 increasing numbers in the count.",
                        tr(AchievementTrigger.VALID_COUNT),
                        userStatAtLeast(StatKey.POS_COUNTS, 100)
                ),

                def("increasing_count_1000", "Increasing Counter 1000", "Sent 1000 increasing numbers in the count.",
                        tr(AchievementTrigger.VALID_COUNT),
                        userStatAtLeast(StatKey.POS_COUNTS, 1000)
                ),

                // Manual-only (unlocked from CountingListener via unlockById)
                def("cause_fail", "Saboteur", "Another user repeated a number you sent, causing them to incorrectly count.",
                        EnumSet.noneOf(AchievementTrigger.class),
                        never()
                ),

                def("goal_winner", "Goal Winner", "Was the final counter when a goal was reached.",
                        EnumSet.noneOf(AchievementTrigger.class),
                        never()
                )
        );

    }

    private static EnumSet<AchievementTrigger> tr(@SuppressWarnings("SameParameterValue") AchievementTrigger... t) {
        EnumSet<AchievementTrigger> s = EnumSet.noneOf(AchievementTrigger.class);
        s.addAll(Arrays.asList(t));
        return s;
    }

    private static AchievementDef def(String id, String title, String desc,
                                      EnumSet<AchievementTrigger> triggers,
                                      Condition cond) {
        return new AchievementDef(id, title, desc, triggers, cond, false, true);
    }
}
