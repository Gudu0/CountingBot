package org.gudu0.countingbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.gudu0.countingbot.achievements.AchievementDef;
import org.gudu0.countingbot.achievements.AchievementGrantResult;
import org.gudu0.countingbot.achievements.AchievementsService;
import org.gudu0.countingbot.achievements.UserAchievements;
import org.gudu0.countingbot.util.ConsoleLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class AchievementsCommandListener extends ListenerAdapter implements CommandGuards {
    private final AchievementsService achievements;

    public AchievementsCommandListener(AchievementsService achievements) {
        this.achievements = achievements;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("achievements")) return;
        ConsoleLog.info("Command", "Achievements Command Used");

        if (ConsoleLog.DEBUG) {
            ConsoleLog.debug("Command - " + this.getClass().getSimpleName(),
                    "/" + event.getName()
                            + (event.getSubcommandName() != null ? " " + event.getSubcommandName() : "")
                            + " by userId=" + event.getUser().getId()
                            + " name=" + event.getUser().getName()
                            + " guildId=" + (event.getGuild() != null ? event.getGuild().getId() : "DM")
                            + " channelId=" + event.getChannel().getId());
        }

        String sub = event.getSubcommandName();
        if (sub == null) {
            event.reply("Missing subcommand. Use /achievements view or /achievements grant.")
                    .setEphemeral(true).queue();
            return;
        }

        switch (sub) {
            case "view" -> handleView(event);
            case "grant" -> handleGrant(event);
            default -> event.reply("Unknown subcommand: " + sub).setEphemeral(true).queue();
        }
    }

    private void handleView(SlashCommandInteractionEvent event) {
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

        event.replyEmbeds(eb.build()).setEphemeral(true).queue();
    }

    private void handleGrant(SlashCommandInteractionEvent event) {
        if (requireGuild(event) == null) return;
        if (!requireAdmin(event)) return;

        long guildId = Objects.requireNonNull(event.getGuild()).getIdLong();
        long targetUserId = Objects.requireNonNull(event.getOption("user")).getAsUser().getIdLong();
        String achievementId = Objects.requireNonNull(event.getOption("achievement")).getAsString().trim();

        AchievementDef def = null;
        for (AchievementDef d : achievements.defs()) {
            if (d.id.equals(achievementId)) { def = d; break; }
        }
        String title = def != null ? def.title : achievementId;

        AchievementGrantResult result = achievements.unlockById(
                guildId, targetUserId, achievementId, event.getUser().getIdLong());

        switch (result) {
            case GRANTED -> {
                event.reply("Granted **" + title + "** to <@" + targetUserId + ">.")
                        .setEphemeral(true).queue();
                ConsoleLog.info("Achievements", "Admin grant: id=" + achievementId
                        + " to userId=" + targetUserId
                        + " by adminId=" + event.getUser().getId()
                        + " guildId=" + guildId);
            }
            case ALREADY_UNLOCKED -> event.reply("<@" + targetUserId + "> already has **" + title + "**.")
                    .setEphemeral(true).queue();
            case UNKNOWN_ACHIEVEMENT_ID -> event.reply("No achievement with id `" + achievementId + "`.")
                    .setEphemeral(true).queue();
        }
    }
}
