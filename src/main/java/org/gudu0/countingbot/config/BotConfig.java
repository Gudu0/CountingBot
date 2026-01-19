package org.gudu0.countingbot.config;

/**
 * Persistent config stored in data/config.json.
 * <p>
 * Keep DISCORD_TOKEN in an environment variable.
 */
public class BotConfig {
    /** Guild/server ID that this bot is intended to run in. */
    public String guildId;

    /** Counting channel ID. */
    public String countingChannelId;

    /** Minimum seconds between VALID counts from the same user. */
    public int countingDelaySeconds = 2;

    /** Whether the bot should delete invalid counting messages. */
    public boolean enforceDelete = false;

    /** Log channel ID for where logs go. */
    public String logThreadId = "";

    /** Whether the bot should send logs*/
    public boolean enableLogs = false;

    /** Disconnect Thread ID for logging disconnects*/
    public String disconnectThreadId = "";

    public String suggestionsThreadId = "0";

    public String suggestionsNotifyUserId = "733113260496126053";

}
