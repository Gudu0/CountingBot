package org.gudu0.countingbot.config;

/**
 * Per-guild config.
 * <p>
 * Stored at: data/guilds/<guildId>/config.json
 */
public class GuildConfig {
    /** Counting channel ID. */
    public String countingChannelId = "";

    /** Minimum seconds between VALID counts from the same user. */
    public int countingDelaySeconds = 2;

    /** Whether the bot should delete invalid counting messages. */
    public boolean enforceDelete = false;

    /** Per-guild log thread. */
    public String logThreadId = "";

    /** Whether the bot should send logs for this guild. */
    public boolean enableLogs = false;
}
