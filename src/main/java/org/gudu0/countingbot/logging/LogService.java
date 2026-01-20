package org.gudu0.countingbot.logging;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.gudu0.countingbot.guild.GuildContext;
import org.gudu0.countingbot.guild.GuildManager;
import org.gudu0.countingbot.util.ConsoleLog;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class LogService {

    public static final ZoneId ZONE = ZoneId.of("America/Los_Angeles");
    public static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final GuildManager guilds;
    private volatile JDA jda;

    public LogService(GuildManager guilds) {
        this.guilds = guilds;
    }

    public void attach(JDA jda) {
        this.jda = jda;
    }

    public void logGlobal(String message) {
        ConsoleLog.info("LogService", message);
    }

    public void log(long guildId, String message) {
        // Always console-log
        ConsoleLog.info("LogService", "guildId=" + guildId + " | " + message);

        JDA j = this.jda;
        if (j == null) return;

        GuildContext ctx = guilds.get(guildId);
        if (!ctx.cfg.enableLogs) return;
        if (ctx.cfg.logThreadId == null || ctx.cfg.logThreadId.isBlank()) return;

        MessageChannel ch = j.getChannelById(MessageChannel.class, ctx.cfg.logThreadId);
        if (ch == null) {
            ConsoleLog.warn("LogService", "guildId=" + guildId + " logThreadId not found: " + ctx.cfg.logThreadId);
            return;
        }

        ch.sendMessage(message).queue(
                ok -> {},
                err -> ConsoleLog.error("LogService", "guildId=" + guildId + " failed sending log: " + err.getMessage(), err)
        );
    }
}
