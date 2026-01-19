package org.gudu0.countingbot.achievements;

import java.util.EnumSet;

@SuppressWarnings({"ClassCanBeRecord", "unused"})
public class AchievementDef {
    public final String id;
    public final String title;
    public final String description;
    public final EnumSet<AchievementTrigger> triggers;
    public final Condition condition;

    public final boolean hidden;
    public final boolean logOnUnlock;

    public AchievementDef(String id, String title, String description,
                          EnumSet<AchievementTrigger> triggers,
                          Condition condition,
                          boolean hidden,
                          boolean logOnUnlock) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.triggers = triggers;
        this.condition = condition;
        this.hidden = hidden;
        this.logOnUnlock = logOnUnlock;
    }
}
