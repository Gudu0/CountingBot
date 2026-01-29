package org.gudu0.countingbot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.gudu0.countingbot.guild.GuildContext;
import org.gudu0.countingbot.util.ConsoleLog;

/**
 * Startup safety checks.
 * <p>
 * Multi-guild refactor:
 * - runForGuild(JDA, GuildContext) should be used from Main.
 * - Legacy run(JDA, BotConfig, AtomicBoolean) is kept so older code still compiles.
 */
public final class SafetyChecks {

    private SafetyChecks() {}

    /**
     * Multi-guild: validate one guild context, disable enforceDelete if missing perms.
     */
    public static void runForGuild(JDA jda, GuildContext ctx) {
        long guildId = ctx.guildId;

        // If admin didn't enable deletion, nothing to enforce.
        if (!ctx.cfg.enforceDelete) return;

        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            autoDisable(ctx, "Guild missing in JDA cache (bot not in guild?)");
            return;
        }

        String channelId = ctx.cfg.countingChannelId;
        if (channelId == null || channelId.isBlank() || "0".equals(channelId)) {
            autoDisable(ctx, "countingChannelId not configured");
            return;
        }

        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) {
            autoDisable(ctx, "Counting channel not found / not visible (channelId=" + channelId + ")");
            return;
        }

        var self = guild.getSelfMember();

        boolean canView    = self.hasPermission(channel, Permission.VIEW_CHANNEL);
        boolean canHistory = self.hasPermission(channel, Permission.MESSAGE_HISTORY);
        boolean canManage  = self.hasPermission(channel, Permission.MESSAGE_MANAGE);

        if (!canView || !canHistory || !canManage) {
            String missing =
                    (!canView ? "VIEW_CHANNEL " : "") +
                            (!canHistory ? "MESSAGE_HISTORY " : "") +
                            (!canManage ? "MANAGE_MESSAGES " : "");
            autoDisable(ctx, "Missing permissions in #" + channel.getName() + ": " + missing.trim());
            return;
        }

        ConsoleLog.info("Safety",
                "Deletion enforcement OK: guildId=" + guildId +
                        " guild=" + guild.getName() +
                        " channel=#" + channel.getName() +
                        " delay=" + ctx.cfg.countingDelaySeconds + "s");
    }

    private static void autoDisable(GuildContext ctx, String reason) {
        synchronized (ctx) {
            if (!ctx.cfg.enforceDelete) return; // already disabled
            ctx.cfg.enforceDelete = false;
            try {
                ctx.configStore.save();
                ConsoleLog.warn("Safety",
                        "Auto-disabled enforceDelete for guildId=" + ctx.guildId + " reason=" + reason);
            } catch (Exception e) {
                ConsoleLog.error("Safety",
                        "Failed to save config after auto-disabling enforceDelete for guildId=" + ctx.guildId +
                                " reason=" + reason + " err=" + e.getMessage(), e);
            }
        }
    }

}
