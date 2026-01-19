package org.gudu0.countingbot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.gudu0.countingbot.config.BotConfig;
import org.gudu0.countingbot.util.ConsoleLog;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One-time checks that prevent "silent failure" when the bot is missing access.
 * <p>
 * Important behavior: if deletion is enabled in config but the bot lacks
 * "Manage Messages" in the counting channel, we automatically disable deletion
 * (in memory) and log loudly.
 */
public final class SafetyChecks {
    private SafetyChecks() {}

    public static void run(JDA jda, BotConfig cfg, AtomicBoolean enforceDelete) {
        // Intent sanity (you still must enable Message Content in the Discord Dev Portal)
        if (!jda.getGatewayIntents().contains(GatewayIntent.MESSAGE_CONTENT)) {
            System.err.println("[SAFETY] MESSAGE_CONTENT intent is NOT enabled in code. Counting will not work.");
        }

        Guild guild = jda.getGuildById(cfg.guildId);
        if (guild == null) {
            System.err.println("[SAFETY] Guild not found: " + cfg.guildId + " (check data/config.json)");
            enforceDelete.set(false);
            return;
        }

        TextChannel ch = guild.getTextChannelById(cfg.countingChannelId);
        if (ch == null) {
            System.err.println("[SAFETY] Counting channel not found in guild: " + cfg.countingChannelId);
            enforceDelete.set(false);
            return;
        }

        Member self = guild.getSelfMember();

        boolean canView = self.hasPermission(ch, Permission.VIEW_CHANNEL);
        boolean canHistory = self.hasPermission(ch, Permission.MESSAGE_HISTORY);
        boolean canManage = self.hasPermission(ch, Permission.MESSAGE_MANAGE);

        if (!canView) {
            System.err.println("[SAFETY] Missing VIEW_CHANNEL in counting channel. Bot cannot function.");
            enforceDelete.set(false);
            return;
        }

        if (!canHistory) {
            System.err.println("[SAFETY] Missing MESSAGE_HISTORY. Startup resync may fail.");
        }

        if (enforceDelete.get() && !canManage) {
            System.err.println("[SAFETY] Config says enforceDelete=true but bot lacks MANAGE_MESSAGES. Disabling deletion.");
            enforceDelete.set(false);
        }

        System.out.println("[SAFETY] Guild OK: " + guild.getName() +
                " | Channel OK: #" + ch.getName() +
                " | enforceDelete=" + enforceDelete.get() +
                " | delay=" + cfg.countingDelaySeconds + "s");

        ConsoleLog.info("Safety",
                "Perms in #" + ch.getName() + ": VIEW=" + true +
                        " HISTORY=" + canHistory + " MANAGE_MESSAGES=" + canManage +
                        " | enforceDelete=" + enforceDelete.get());

    }
}
