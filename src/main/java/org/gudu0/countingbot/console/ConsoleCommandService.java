package org.gudu0.countingbot.console;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import org.gudu0.countingbot.guild.GuildContext;
import org.gudu0.countingbot.guild.GuildManager;
import org.gudu0.countingbot.util.ConsoleLog;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class ConsoleCommandService {

    private final GuildManager guilds;
    private final JDA jda;

    private volatile boolean running = true;

    public ConsoleCommandService(GuildManager guilds, JDA jda) {
        this.guilds = guilds;
        this.jda = jda;
    }

    public void start() {
        Thread t = new Thread(this::runLoop, "ConsoleCommandService");
        t.setDaemon(true); // don't prevent JVM shutdown
        t.start();

        ConsoleLog.info("Console", "Console commands enabled. Type 'help' for commands.");
    }

    public void stop() {
        running = false;
    }

    private void runLoop() {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8))) {

            while (running) {
                String line = br.readLine(); // blocks waiting for input
                if (line == null) {
                    // STDIN closed (some hosts). Just stop gracefully.
                    ConsoleLog.warn("Console", "STDIN closed; console commands disabled.");
                    return;
                }

                line = line.trim();
                if (line.isEmpty()) continue;

                handle(line);
            }
        } catch (Exception e) {
            ConsoleLog.error("Console", "Console command loop crashed: " + e.getMessage(), e);
        }
    }

    private void handle(String raw) {
        String[] parts = raw.trim().split("\\s+");
        String cmd = parts[0].toLowerCase(Locale.ROOT);

        switch (cmd) {
            case "help" -> printHelp();

            case "listguilds", "guilds" -> listGuilds();

            case "guild" -> {
                if (parts.length < 2) {
                    ConsoleLog.warn("Console", "Usage: guild <guildId> [status]");
                    return;
                }
                long guildId;
                try {
                    guildId = Long.parseLong(parts[1]);
                } catch (NumberFormatException e) {
                    ConsoleLog.warn("Console", "Invalid guildId: " + parts[1]);
                    return;
                }

                String sub = (parts.length >= 3) ? parts[2].toLowerCase(Locale.ROOT) : "status";
                if (sub.equals("status")) {
                    guildStatus(guildId);
                } else {
                    ConsoleLog.warn("Console", "Unknown guild subcommand: " + sub + " (try: status)");
                }
            }

            case "shutdown", "exit" -> {
                ConsoleLog.warn("Console", "Shutdown requested from console.");
                // Let SparkedHost restart policy handle it if you want.
                System.exit(0);
            }

            default -> ConsoleLog.warn("Console", "Unknown command: " + cmd + " (type 'help')");
        }
    }

    private void printHelp() {
        ConsoleLog.info("Console", """
                Commands:
                  help                    - show this help
                  listguilds|guilds       - list guilds the bot is in
                  guild <id> [status]     - show per-guild config/runtime info
                  shutdown|exit           - terminate process

                Examples:
                  guilds
                  guild 712304553931833385 status
                """.trim());
    }

    private void listGuilds() {
        var gs = jda.getGuilds();
        ConsoleLog.info("Console", "Guilds (" + gs.size() + "):");
        for (Guild g : gs) {
            ConsoleLog.info("Console", " - " + g.getName() + " | " + g.getId());
        }
    }

    private void guildStatus(long guildId) {
        Guild g = jda.getGuildById(guildId);
        if (g == null) {
            ConsoleLog.warn("Console", "Bot is not in guildId=" + guildId);
            return;
        }

        GuildContext ctx = guilds.get(guildId);

        String counting = (ctx.cfg.countingChannelId == null || ctx.cfg.countingChannelId.isBlank())
                ? "(not set)"
                : ctx.cfg.countingChannelId;

        String logThread = (ctx.cfg.logThreadId == null || ctx.cfg.logThreadId.isBlank())
                ? "(not set)"
                : ctx.cfg.logThreadId;

        ConsoleLog.info("Console", "Guild status: " + g.getName() + " (" + guildId + ")");
        ConsoleLog.info("Console", "  countingChannelId=" + counting);
        ConsoleLog.info("Console", "  delaySeconds=" + ctx.cfg.countingDelaySeconds);
        ConsoleLog.info("Console", "  cfg.enforceDelete=" + ctx.cfg.enforceDelete);
        ConsoleLog.info("Console", "  enforceDeleteRuntime=" + ctx.enforceDeleteRuntime.get());
        ConsoleLog.info("Console", "  enableLogs=" + ctx.cfg.enableLogs);
        ConsoleLog.info("Console", "  logThreadId=" + logThread);
    }
}
