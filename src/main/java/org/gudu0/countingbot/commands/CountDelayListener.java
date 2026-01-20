package org.gudu0.countingbot.commands;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.gudu0.countingbot.guild.GuildContext;
import org.gudu0.countingbot.guild.GuildManager;
import org.gudu0.countingbot.util.ConsoleLog;

/**
 * /countdelay [seconds] (per guild)
 * <p>
 * Multi-guild: updates data/guilds/[guildId]/config.json.
 */
public class CountDelayListener extends ListenerAdapter {

    private final GuildManager guilds;

    public CountDelayListener(GuildManager guilds) {
        this.guilds = guilds;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!"countdelay".equals(event.getName())) return;

        if (event.getGuild() == null) {
            event.reply("This command can only be used in a server.").setEphemeral(true).queue();
            return;
        }

        // Admin-only (same rule as before)
        if (event.getMember() == null || !event.getMember().hasPermission(Permission.BAN_MEMBERS)) {
            event.reply("You don't have permission to use this.").setEphemeral(true).queue();
            return;
        }

        Integer seconds = event.getOption("seconds", opt -> opt.getAsInt());
        if (seconds == null) {
            event.reply("Missing seconds.").setEphemeral(true).queue();
            return;
        }

        if (seconds < 0 || seconds > 3600) {
            event.reply("Seconds must be between 0 and 3600.").setEphemeral(true).queue();
            return;
        }

        long guildId = event.getGuild().getIdLong();
        GuildContext ctx = guilds.get(guildId);

        ctx.cfg.countingDelaySeconds = seconds;

        try {
            ctx.configStore.save();
            ConsoleLog.info("CountDelay", "guildId=" + guildId + " set delay=" + seconds);
            event.reply("Count delay set to **" + seconds + "s** for this server.").setEphemeral(true).queue();
        } catch (Exception e) {
            ConsoleLog.error("CountDelay", "guildId=" + guildId + " failed saving config: " + e.getMessage(), e);
            event.reply("Failed saving config: " + e.getMessage()).setEphemeral(true).queue();
        }
    }
}
