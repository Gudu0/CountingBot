package org.gudu0.countingbot.disconnects;

public class DisconnectDailyState {
    public String date;          // yyyy-MM-dd
    public long messageId = 0;   // message we edit
    public int disconnects = 0;

    public long lastDisconnectMs = 0;
    public long lastReconnectMs = 0;
}
