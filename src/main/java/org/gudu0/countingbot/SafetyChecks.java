package org.gudu0.countingbot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.gudu0.countingbot.config.BotConfig;
import org.gudu0.countingbot.guild.GuildContext;
import org.gudu0.countingbot.util.ConsoleLog;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Startup safety checks.
 *
 * Multi-guild refactor:
 * - runForGuild(JDA, GuildContext) should be used from Main.
 * - Legacy run(JDA, BotConfig, AtomicBoolean) is kept so older code still compiles.
 */
public final class SafetyChecks {

    private SafetyChecks() {}

    /**
     * Multi-guild: validate one guild context, disable enforceDeleteRuntime if missing perms.
     */
    public static void runForGuild(JDA jda, GuildContext ctx) {
        long guildId = ctx.guildId;

        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            ctx.enforceDeleteRuntime.set(false);
            ConsoleLog.warn("Safety", "Guild missing in JDA cache: guildId=" + guildId + " (enforceDelete disabled)");
            return;
        }

        String channelId = ctx.cfg.countingChannelId;
        if (channelId == null || channelId.isBlank() || "0".equals(channelId)) {
            ctx.enforceDeleteRuntime.set(false);
            ConsoleLog.warn("Safety", "countingChannelId not configured: guildId=" + guildId + " (enforceDelete disabled)");
            return;
        }

        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) {
            ctx.enforceDeleteRuntime.set(false);
            ConsoleLog.warn("Safety", "Counting channel not found: guildId=" + guildId + " channelId=" + channelId + " (enforceDelete disabled)");
            return;
        }

        // Print a human-friendly status line (matches your logs style)
        System.out.println("[SAFETY] Guild OK: " + guild.getName()
                + " | Channel OK: #" + channel.getName()
                + " | enforceDelete=" + ctx.enforceDeleteRuntime.get()
                + " | delay=" + ctx.cfg.countingDelaySeconds + "s");

        boolean canView = channel.getGuild().getSelfMember().hasPermission(channel, Permission.VIEW_CHANNEL);
        boolean canHistory = channel.getGuild().getSelfMember().hasPermission(channel, Permission.MESSAGE_HISTORY);
        boolean canManage = channel.getGuild().getSelfMember().hasPermission(channel, Permission.MESSAGE_MANAGE);

        if (ctx.enforceDeleteRuntime.get() && !canManage) {
            ctx.enforceDeleteRuntime.set(false);
            ConsoleLog.warn("Safety", "Missing MANAGE_MESSAGES in counting channel; enforcement disabled guildId=" + guildId);
        }

        boolean runtime = ctx.cfg.enforceDelete && canView && canHistory && canManage;

        if (ctx.cfg.enforceDelete && !runtime) {
            ConsoleLog.warn("Safety", "guildId=" + ctx.guildId + " enforceDelete requested but perms missing -> enforceDeleteRuntime=false");
        }

        ctx.enforceDeleteRuntime.set(runtime);

        ConsoleLog.info("Safety", "guildId=" + ctx.guildId
                + " cfg.enforceDelete=" + ctx.cfg.enforceDelete
                + " -> enforceDeleteRuntime=" + ctx.enforceDeleteRuntime.get());

    }
}
