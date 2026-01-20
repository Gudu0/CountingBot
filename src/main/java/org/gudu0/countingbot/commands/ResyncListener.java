package org.gudu0.countingbot.commands;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.gudu0.countingbot.counting.CountingListener;

/**
 * /resync (per guild)
 */
public class ResyncListener extends ListenerAdapter {

    private final CountingListener counting;

    public ResyncListener(CountingListener counting) {
        this.counting = counting;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!"resync".equals(event.getName())) return;

        if (event.getGuild() == null) {
            event.reply("This command can only be used in a server.").setEphemeral(true).queue();
            return;
        }

        if (event.getMember() == null || !event.getMember().hasPermission(Permission.BAN_MEMBERS)) {
            event.reply("You don't have permission to use this.").setEphemeral(true).queue();
            return;
        }

        long guildId = event.getGuild().getIdLong();

        event.reply("Resyncing...").setEphemeral(true).queue();

        counting.resyncNow(event.getJDA(), guildId, r -> {
            if (!r.found()) {
                event.getHook().editOriginal("Resync complete: no valid count found in last 10 messages.").queue();
            } else {
                event.getHook().editOriginal(
                        "Resync complete: last valid = **" + r.number() + "** (user <@" + r.userId() + ">)."
                ).queue();
            }
        });
    }
}
