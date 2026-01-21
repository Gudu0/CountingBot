package org.gudu0.countingbot.commands;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.gudu0.countingbot.SafetyChecks;
import org.gudu0.countingbot.guild.GuildContext;
import org.gudu0.countingbot.guild.GuildManager;
import org.gudu0.countingbot.util.ConsoleLog;

import java.util.Objects;

/**
 * /setup ...
 * <p>
 * Multi-guild setup: edits data/guilds/<guildId>/config.json and immediately
 * re-runs SafetyChecks so enforceDeleteRuntime updates without a restart.
 */
public class SetupListener extends ListenerAdapter {

    private final GuildManager guilds;

    public SetupListener(GuildManager guilds) {
        this.guilds = guilds;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("setup")) return;

        logCommand(event);

        if (event.getGuild() == null) {
            event.reply("This command can only be used in a server.")
                    .setEphemeral(true).queue();
            return;
        }

        // Admin gate: keep it strict.
        boolean isAdmin =
                event.getMember() != null &&
                        (event.getMember().hasPermission(Permission.MANAGE_SERVER)
                                || event.getMember().hasPermission(Permission.BAN_MEMBERS));

        if (!isAdmin) {
            event.reply("You don't have permission to use /setup.")
                    .setEphemeral(true).queue();
            ConsoleLog.warn("Setup", "Denied (missing perms) userId=" + event.getUser().getId()
                    + " guildId=" + event.getGuild().getId());
            return;
        }

        long guildId = event.getGuild().getIdLong();
        GuildContext ctx = guilds.get(guildId); // ensures folder + loads config/stores

        String sub = event.getSubcommandName();
        if (sub == null) {
            event.reply("Missing subcommand. Use /setup status or /setup setcountingchannel ...")
                    .setEphemeral(true).queue();
            return;
        }

        switch (sub) {
            case "status" -> {
                String msg = buildStatus(event.getGuild(), ctx);
                event.reply(msg).setEphemeral(true).queue();
            }

            case "setcountingchannel" -> {
                Channel ch = Objects.requireNonNull(event.getOption("channel")).getAsChannel();

                // Only allow real guild message channels (TextChannel or other StandardGuildMessageChannel)
                if (!(ch instanceof StandardGuildMessageChannel)) {
                    event.reply("Please choose a normal text channel (not a voice/category).")
                            .setEphemeral(true).queue();
                    return;
                }

                ctx.cfg.countingChannelId = ch.getId();
                saveGuildConfig(ctx, "setcountingchannel", "countingChannelId=" + ctx.cfg.countingChannelId);

                // Safety checks now that we have a counting channel
                SafetyChecks.runForGuild(event.getJDA(), ctx);

                event.reply("Counting channel set to <#" + ctx.cfg.countingChannelId + ">.\n"
                                + "enforceDeleteRuntime=" + ctx.enforceDeleteRuntime.get())
                        .setEphemeral(true).queue();
            }

            case "setdelay" -> {
                long seconds = Objects.requireNonNull(event.getOption("seconds")).getAsLong();
                if (seconds < 0 || seconds > 3600) {
                    event.reply("Seconds must be between 0 and 3600.")
                            .setEphemeral(true).queue();
                    return;
                }

                ctx.cfg.countingDelaySeconds = (int) seconds;
                saveGuildConfig(ctx, "setdelay", "countingDelaySeconds=" + ctx.cfg.countingDelaySeconds);

                event.reply("Cooldown delay set to " + ctx.cfg.countingDelaySeconds + " seconds.")
                        .setEphemeral(true).queue();
            }

            case "setenforcedelete" -> {
                boolean enabled = Objects.requireNonNull(event.getOption("enabled")).getAsBoolean();

                ctx.cfg.enforceDelete = enabled;
                // If user turned it off, force runtime off immediately even before safety checks.
                if (!enabled) {
                    ctx.enforceDeleteRuntime.set(false);
                }

                saveGuildConfig(ctx, "setenforcedelete", "enforceDelete=" + ctx.cfg.enforceDelete);

                // Recompute runtime enforcement immediately based on perms
                SafetyChecks.runForGuild(event.getJDA(), ctx);

                event.reply("enforceDelete set to " + ctx.cfg.enforceDelete + ".\n"
                                + "enforceDeleteRuntime=" + ctx.enforceDeleteRuntime.get()
                                + " (auto-disabled if perms missing)")
                        .setEphemeral(true).queue();
            }

            case "setenablelogs" -> {
                boolean enabled = Objects.requireNonNull(event.getOption("enabled")).getAsBoolean();

                ctx.cfg.enableLogs = enabled;
                saveGuildConfig(ctx, "setenablelogs", "enableLogs=" + ctx.cfg.enableLogs);

                event.reply("enableLogs set to " + ctx.cfg.enableLogs + ".")
                        .setEphemeral(true).queue();
            }

            case "setlogthread" -> {
                Channel ch = Objects.requireNonNull(event.getOption("channel")).getAsChannel();

                // We accept ThreadChannel OR TextChannel OR any MessageChannel type JDA exposes.
                // Easiest safe check: can it be treated as a MessageChannel?
                if (!(ch instanceof MessageChannel)) {
                    event.reply("Please choose a thread or a text channel.")
                            .setEphemeral(true).queue();
                    return;
                }

                ctx.cfg.logThreadId = ch.getId();
                saveGuildConfig(ctx, "setlogthread", "logThreadId=" + ctx.cfg.logThreadId);

                event.reply("Log channel/thread set to <#" + ctx.cfg.logThreadId + ">.")
                        .setEphemeral(true).queue();
            }

            default -> event.reply("Unknown subcommand: " + sub).setEphemeral(true).queue();
        }
    }

    private static void saveGuildConfig(GuildContext ctx, String action, String detail) {
        try {
            ctx.configStore.save();
            ConsoleLog.info("Setup", "Saved guild config: guildId=" + ctx.guildId + " action=" + action + " " + detail);
        } catch (Exception e) {
            ConsoleLog.error("Setup", "Failed saving guild config guildId=" + ctx.guildId + ": " + e.getMessage(), e);
        }
    }

    private static String buildStatus(Guild g, GuildContext ctx) {
        String counting = (ctx.cfg.countingChannelId == null || ctx.cfg.countingChannelId.isBlank())
                ? "_not set_"
                : "<#" + ctx.cfg.countingChannelId + ">";

        String logCh = (ctx.cfg.logThreadId == null || ctx.cfg.logThreadId.isBlank())
                ? "_not set_"
                : "<#" + ctx.cfg.logThreadId + ">";

        return "**Setup Status — " + g.getName() + "**\n"
                + "- countingChannel: " + counting + "\n"
                + "- delaySeconds: " + ctx.cfg.countingDelaySeconds + "\n"
                + "- enforceDelete (config): " + ctx.cfg.enforceDelete + "\n"
                + "- enforceDeleteRuntime: " + ctx.enforceDeleteRuntime.get() + "\n"
                + "- enableLogs: " + ctx.cfg.enableLogs + "\n"
                + "- logChannel/thread: " + logCh + "\n"
                + "\n"
                + "_Tip: run `/setup setcountingchannel` first, then `/setup setenforcedelete true`._";
    }

    private static void logCommand(SlashCommandInteractionEvent event) {
        ConsoleLog.info("Command", "Setup Command Used");
        if (ConsoleLog.DEBUG) {
            ConsoleLog.debug("Command - SetupListener",
                    "/setup"
                            + (event.getSubcommandName() != null ? " " + event.getSubcommandName() : "")
                            + " by userId=" + event.getUser().getId()
                            + " name=" + event.getUser().getName()
                            + " guildId=" + (event.getGuild() != null ? event.getGuild().getId() : "DM")
                            + " channelId=" + event.getChannel().getId());
        }
    }
}
