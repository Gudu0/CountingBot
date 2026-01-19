package org.gudu0.countingbot.commands;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.gudu0.countingbot.counting.CountingListener;
import org.gudu0.countingbot.util.ConsoleLog;

public class ResyncListener extends ListenerAdapter {
    private final CountingListener counting;

    public ResyncListener(CountingListener counting) {
        this.counting = counting;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("resync")) return;
        ConsoleLog.info("Command - " + this.getClass().getSimpleName(),
                "/" + event.getName()
                        + (event.getSubcommandName() != null ? " " + event.getSubcommandName() : "")
                        + " by userId=" + event.getUser().getId()
                        + " name=" + event.getUser().getName()
                        + " guildId=" + (event.getGuild() != null ? event.getGuild().getId() : "DM")
                        + " channelId=" + event.getChannel().getId());

        if (event.getMember() == null || !event.getMember().hasPermission(Permission.BAN_MEMBERS)) {
            event.reply("You don't have permission to use this command.")
                    .setEphemeral(true).queue();
            ConsoleLog.warn("Command", "Denied (missing permission) userId=" + event.getUser().getId());
            return;
        }

        event.deferReply(true).queue(); // ephemeral

        counting.resyncNow(event.getJDA(), r -> {
            if (!r.found()) {
                event.getHook().editOriginal("Resync complete: **no valid count found** in recent history.").queue();
                return;
            }

            event.getHook().editOriginal(
                    "Resynced.\n" +
                            "Last number: **" + r.number() + "**\n" +
                            "Last user: <@" + r.userId() + ">\n" +
                            "Last messageId: `" + r.messageId() + "`"
            ).queue();
        });
    }
}
