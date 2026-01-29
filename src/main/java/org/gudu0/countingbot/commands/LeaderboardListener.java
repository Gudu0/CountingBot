package org.gudu0.countingbot.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.gudu0.countingbot.counting.CountingState;
import org.gudu0.countingbot.guild.GuildContext;
import org.gudu0.countingbot.guild.GuildManager;
import org.gudu0.countingbot.stats.StatsData;
import org.gudu0.countingbot.stats.StatsStore;
import org.gudu0.countingbot.stats.UserStats;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Global leaderboard for user stats (fame/shame), plus guild-scoped streak summary.
 */
public class LeaderboardListener extends ListenerAdapter {

    private final StatsStore stats;
    private final GuildManager guilds;

    public LeaderboardListener(StatsStore stats, GuildManager guilds) {
        this.stats = stats;
        this.guilds = guilds;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!"leaderboard".equals(event.getName())) return;

        if (event.getGuild() == null) {
            event.reply("This command can only be used in a server.").setEphemeral(true).queue();
            return;
        }

        long guildId = event.getGuild().getIdLong();

        // Guild streak snapshot (per guild)
        long last;
        long streak;
        long best;
        GuildContext ctx = guilds.get(guildId);
        synchronized (ctx.stateStore.lock) {
            CountingState s = ctx.stateStore.state();
            last = s.lastNumber;
            streak = s.globalStreakCurrent;
            best = s.globalStreakBest;
        }

        // Global leaderboards (across all guilds)
        List<Map.Entry<Long, UserStats>> entries;
        synchronized (stats.lock) {
            entries = List.copyOf(stats.data().users.entrySet());
        }

        // Top 10 fame (global)
        List<Map.Entry<Long, UserStats>> topFame = entries.stream()
                .sorted(Comparator.comparingLong((Map.Entry<Long, UserStats> e) -> e.getValue().correct).reversed())
                .limit(10)
                .toList();

        // Top 10 shame (global)
        List<Map.Entry<Long, UserStats>> topShame = entries.stream()
                .sorted(Comparator.comparingLong((Map.Entry<Long, UserStats> e) -> e.getValue().incorrect).reversed())
                .limit(10)
                .toList();


        StringBuilder sb = new StringBuilder();
        sb.append("**Guild Streak (this server)**\n");
        sb.append("Last valid: ").append(last).append("\n");
        sb.append("Current streak: ").append(streak).append("\n");
        sb.append("Best streak: ").append(best).append("\n\n");

        sb.append("**Top Fame (global)**\n");
        if (topFame.isEmpty()) {
            sb.append("(no data yet)\n");
        } else {
            for (int i = 0; i < topFame.size(); i++) {
                long userId = topFame.get(i).getKey();
                UserStats u = topFame.get(i).getValue();
                sb.append(i + 1).append(". <@").append(userId).append("> — ").append(u.correct).append("\n");
            }
        }

        sb.append("\n**Top Shame (global)**\n");
        if (topShame.isEmpty()) {
            sb.append("(no data yet)\n");
        } else {
            for (int i = 0; i < topShame.size(); i++) {
                long userId = topShame.get(i).getKey();
                UserStats u = topShame.get(i).getValue();
                sb.append(i + 1).append(". <@").append(userId).append("> — ").append(u.incorrect    ).append("\n");
            }
        }

        event.reply(sb.toString()).setEphemeral(true).queue();
    }
}
