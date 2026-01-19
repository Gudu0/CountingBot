package org.gudu0.countingbot.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.gudu0.countingbot.counting.StateStore;
import org.gudu0.countingbot.counting.CountingState;
import org.gudu0.countingbot.stats.StatsStore;
import org.gudu0.countingbot.stats.UserStats;
import org.gudu0.countingbot.util.ConsoleLog;

import java.util.*;
import java.util.stream.Collectors;

public class LeaderboardListener extends ListenerAdapter {
    private final StatsStore stats;
    private final StateStore stateStore; // optional (for global streak later)

    public LeaderboardListener(StatsStore stats, StateStore stateStore) {
        this.stats = stats;
        this.stateStore = stateStore;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("leaderboard")) return;

        ConsoleLog.info("Command - " + this.getClass().getSimpleName(),
                "/" + event.getName()
                        + (event.getSubcommandName() != null ? " " + event.getSubcommandName() : "")
                        + " by userId=" + event.getUser().getId()
                        + " name=" + event.getUser().getName()
                        + " guildId=" + (event.getGuild() != null ? event.getGuild().getId() : "DM")
                        + " channelId=" + event.getChannel().getId());


        // Snapshot to avoid concurrent modification mid-sort
        Map<Long, UserStats> users = Map.copyOf(stats.data().users);

        var topFame = topN(users, true);
        var topShame = topN(users, false);

        StringBuilder sb = new StringBuilder();
        sb.append("** Fame (Correct) — Top 5**\n");
        append(sb, topFame, true);

        sb.append("\n** Shame (Incorrect) — Top 5**\n");
        append(sb, topShame, false);

        // Optional: once you add global streak fields, show them here
        CountingState state = stateStore.state();
        if (state.globalStreakBest > 0 || state.globalStreakCurrent > 0) {
            sb.append("\n** Global Streak**\n")
                    .append("Current: **").append(state.globalStreakCurrent).append("**\n")
                    .append("Best: **").append(state.globalStreakBest).append("**\n");
        }

        event.reply(sb.toString()).setEphemeral(true).queue();
    }

    private List<Map.Entry<Long, UserStats>> topN(Map<Long, UserStats> users, boolean fame) {
        return users.entrySet().stream()
                .filter(e -> (fame ? e.getValue().correct : e.getValue().incorrect) > 0)
                .sorted((a, b) -> {
                    long av = fame ? a.getValue().correct : a.getValue().incorrect;
                    long bv = fame ? b.getValue().correct : b.getValue().incorrect;
                    int cmp = Long.compare(bv, av);
                    if (cmp != 0) return cmp;
                    return Long.compare(a.getKey(), b.getKey()); // tie-break stable
                })
                .limit(5)
                .collect(Collectors.toList());
    }

    private void append(StringBuilder sb, List<Map.Entry<Long, UserStats>> list, boolean fame) {
        if (list.isEmpty()) {
            sb.append("_No data yet._\n");
            return;
        }

        int rank = 1;
        for (var e : list) {
            long userId = e.getKey();
            UserStats s = e.getValue();
            long value = fame ? s.correct : s.incorrect;

            sb.append(rank).append(". <@").append(userId).append("> — **").append(value).append("**\n");
            rank++;
        }
    }
}
