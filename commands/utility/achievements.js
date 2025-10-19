const { SlashCommandBuilder, MessageFlags } = require('discord.js');
const fs = require('fs');
const path = require('path');
const achievementCheck = require('../../achievement-check.js');

module.exports = {
    cooldown: 5,
    data: new SlashCommandBuilder()
        .setName('achievements')
        .setDescription('View your achievements!')
        .addUserOption(option =>
            option.setName('user')
                .setDescription('User to view achievements for (optional)')
                .setRequired(false)
        ),
    async execute(interaction) {
        await interaction.deferReply({ flags: MessageFlags.Ephemeral });
        const user = interaction.options.getUser('user') || interaction.user;
        const userId = user.id;
        console.log(`[ACHIEVEMENTS CMD] Looking up achievements for user: ${user.username} (${userId})`);
        const userAchievements = achievementCheck.loadUserAchievements();
        console.log('[ACHIEVEMENTS CMD] Loaded userAchievements:', userAchievements);
        const definitions = achievementCheck.loadDefinitions();
        //console.log('[ACHIEVEMENTS CMD] Loaded definitions:', definitions);
        const achievements = userAchievements[userId] || [];
        console.log(`[ACHIEVEMENTS CMD] Achievements for user ${userId}:`, achievements);
        if (achievements.length === 0) {
            console.log(`[ACHIEVEMENTS CMD] No achievements found for user ${userId}`);
            await interaction.editReply(`${user.username} has no achievements yet.`);
            return;
        }
        // Build a simple string list
        const achievementList = achievements.map(id => {
            const def = definitions[id];
            return def ? `• ${def.name}: ${def.description}` : `• ${id}`;
        }).join('\n');
        //console.log(`[ACHIEVEMENTS CMD] Output string for user ${userId}:\n${achievementList}`);
        await interaction.editReply(`Achievements for ${user.username}:\n${achievementList}`);
    },
};
