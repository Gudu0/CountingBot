package org.gudu0.countingbot.logging;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.gudu0.countingbot.config.BotConfig;
import org.gudu0.countingbot.util.ConsoleLog;

import java.time.format.FormatStyle;
import java.util.concurrent.atomic.AtomicBoolean;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;


public class LogService {
    private final BotConfig cfg;
    private final AtomicBoolean ready = new AtomicBoolean(false);
    private volatile JDA jda;

    public static final DateTimeFormatter TS = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM);
    public static final ZoneId ZONE = ZoneId.of("America/Los_Angeles");


    public LogService(BotConfig cfg) {
        this.cfg = cfg;
    }

    public void attach(JDA jda) {
        this.jda = jda;
        this.ready.set(true);
    }

    public void log(String message) {
        // Always print to console:
        ConsoleLog.info("LogService", message);

        // Discord thread logging is optional:
        if (!cfg.enableLogs) {
            ConsoleLog.debug("LogService", "Discord logging disabled (enableLogs=false)");
            return;
        }
        if (cfg.logThreadId == null || cfg.logThreadId.isBlank()) {
            ConsoleLog.debug("LogService", "Discord logging disabled (logThreadId missing)");
            return;
        }
        if (!ready.get() || jda == null) {
            ConsoleLog.debug("LogService", "Discord logging skipped (JDA not ready yet)");
            return;
        }

        MessageChannel ch = jda.getChannelById(MessageChannel.class, cfg.logThreadId);
        if (ch == null) {
            ConsoleLog.warn("LogService", "logThreadId not found: " + cfg.logThreadId);
            return;
        }

        String out = "[" + ZonedDateTime.now(ZONE).format(TS) + "] " + message;

        ch.sendMessage(out).queue(
                ok -> ConsoleLog.debug("LogService", "Sent discord log"),
                err -> ConsoleLog.error("LogService", "Log send failed: " + err.getMessage(), err)
        );
    }

}
