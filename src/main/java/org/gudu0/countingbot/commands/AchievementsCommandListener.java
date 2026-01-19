package org.gudu0.countingbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.gudu0.countingbot.achievements.AchievementDef;
import org.gudu0.countingbot.achievements.AchievementsService;
import org.gudu0.countingbot.achievements.UserAchievements;
import org.gudu0.countingbot.util.ConsoleLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class AchievementsCommandListener extends ListenerAdapter {
    private final AchievementsService achievements;

    public AchievementsCommandListener(AchievementsService achievements) {
        this.achievements = achievements;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("achievements")) return;

        ConsoleLog.info("Command - " + this.getClass().getSimpleName(),
                "/" + event.getName()
                        + (event.getSubcommandName() != null ? " " + event.getSubcommandName() : "")
                        + " by userId=" + event.getUser().getId()
                        + " name=" + event.getUser().getName()
                        + " guildId=" + (event.getGuild() != null ? event.getGuild().getId() : "DM")
                        + " channelId=" + event.getChannel().getId());


        long userId = event.getOption("user") != null
                ? Objects.requireNonNull(event.getOption("user")).getAsUser().getIdLong()
                : event.getUser().getIdLong();

        UserAchievements ua = achievements.userAchievements(userId);

        List<String> unlockedLines = new ArrayList<>();
        int unlockedCount = 0;

        for (AchievementDef def : achievements.defs()) {
            Long at = ua.unlockedAtMillis.get(def.id);
            if (at == null) continue;

            unlockedCount++;
            // Keep it compact
            unlockedLines.add("**" + def.title + "** — <t:" + (at / 1000) + ":d>");
        }

        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("Achievements");
        eb.setDescription("User: <@" + userId + ">\nUnlocked: **" + unlockedCount + "** / **" + achievements.defs().size() + "**");

        if (unlockedLines.isEmpty()) {
            eb.addField("Unlocked", "None yet.", false);
        } else {
            // prevent huge embeds
            int limit = Math.min(unlockedLines.size(), 20);
            String body = String.join("\n", unlockedLines.subList(0, limit));
            if (unlockedLines.size() > limit) body += "\n…and " + (unlockedLines.size() - limit) + " more.";
            eb.addField("Unlocked", body, false);
        }

//        eb.setFooter("Now: " + Instant.now());

        event.replyEmbeds(eb.build()).setEphemeral(true).queue();
    }
}
