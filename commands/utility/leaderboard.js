const { SlashCommandBuilder, MessageFlags } = require('discord.js');
const counting = require('../../counting.js');
const logger = require('../../logger.js');
const ENV = require("../../data/config.json");

module.exports = {
	cooldown: 5,
	data: new SlashCommandBuilder().setName('leaderboard').setDescription('Replies with the leaderboard!'),
	async execute(interaction) {
		await interaction.deferReply({ flags: MessageFlags.Ephemeral });
        
        // Get fame and shame entries, sort descending
        const fameEntries = [...counting.mapOfFame.entries()].sort((a, b) => b[1] - a[1]);
        const shameEntries = [...counting.mapOfShame.entries()].sort((a, b) => b[1] - a[1]);

        // Format top 5 for each
        const fameBoard = fameEntries.slice(0, 5).map(([id, score], i) => `${i + 1}. <@${id}>: ${score} correct`).join('\n') || 'No fame data!';
        const shameBoard = shameEntries.slice(0, 5).map(([id, score], i) => `${i + 1}. <@${id}>: ${score} incorrect`).join('\n') || 'No shame data!';

        const reply = `**Fame Leaderboard:**\n${fameBoard}\n\n**Shame Leaderboard:**\n${shameBoard}`;
        await interaction.editReply(reply);
        logger.log('Someone checked the leaderboard!', 'checked_leaderboard', ENV.bs_server_id);
	},
};