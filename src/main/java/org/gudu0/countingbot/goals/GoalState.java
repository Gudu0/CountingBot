package org.gudu0.countingbot.goals;

@SuppressWarnings("unused")
public class GoalState {
    // The bot's message in the counting channel that we edit.
    public long goalMessageId = 0;

    // If false -> we still keep the message, but show "No active goal".
    public boolean active = false;

    // Goal definition
    public long target = 0;              // e.g. 2000
    public long setByUserId = 0;         // admin who set it
    public long setAtMillis = 0;         // when it was set
    public Long deadlineAtMillis = null; // optional

    // Render throttling / de-dupe
    public long lastRenderedNumber = Long.MIN_VALUE; // lastNumber when we last edited
}
