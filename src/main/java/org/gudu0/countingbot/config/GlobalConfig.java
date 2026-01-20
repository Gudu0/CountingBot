package org.gudu0.countingbot.config;

/**
 * Global bot config (one per bot process).
 * Stored at: data/global/config.json
 */
public class GlobalConfig {
    /** Disconnect Thread ID for logging disconnects (one ops thread for the whole bot). */
    public String disconnectThreadId = "";

    /** Suggestions thread and DM target are global by your design. */
    public String suggestionsThreadId = "0";
    public String suggestionsNotifyUserId = "0";
}
