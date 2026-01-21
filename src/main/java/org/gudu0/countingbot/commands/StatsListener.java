package org.gudu0.countingbot.commands;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.gudu0.countingbot.stats.StatsStore;
import org.gudu0.countingbot.stats.UserStats;
import org.gudu0.countingbot.util.ConsoleLog;

import java.util.Objects;

public class StatsListener extends ListenerAdapter {
    private final StatsStore stats;

    public StatsListener(StatsStore stats) {
        this.stats = stats;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("stats")) return;
        ConsoleLog.info("Command", "Stats Command Used");
        if (ConsoleLog.DEBUG) {
            ConsoleLog.debug("Command - " + this.getClass().getSimpleName(),
                    "/" + event.getName()
                            + (event.getSubcommandName() != null ? " " + event.getSubcommandName() : "")
                            + " by userId=" + event.getUser().getId()
                            + " name=" + event.getUser().getName()
                            + " guildId=" + (event.getGuild() != null ? event.getGuild().getId() : "DM")
                            + " channelId=" + event.getChannel().getId());
        }

        User target = event.getOption("user") != null
                ? Objects.requireNonNull(event.getOption("user")).getAsUser()
                : event.getUser();

        long id = target.getIdLong();

        // Read stats without creating a new entry
        UserStats s = stats.data().getOrDefault(id);

        String msg =
                "**Stats for " + target.getName() + "**\n" +
                        "Fame (correct): **" + s.correct + "**\n" +
                        "Shame (incorrect): **" + s.incorrect + "**\n" +
                        "Current streak: **" + s.currentStreak + "**\n" +
                        "Best streak: **" + s.bestStreak + "**";

        event.reply(msg).setEphemeral(true).queue();
    }
}
