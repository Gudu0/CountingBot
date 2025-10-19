const { SlashCommandBuilder, MessageFlags } = require('discord.js');
const counting = require('../../counting.js');
const logger = require('../../logger.js');
const ENV = require("../../config.json");

module.exports = {
	data: new SlashCommandBuilder()
    .setName('countstats')
    .setDescription('Replies with counting stats!')
    .addUserOption(option =>
        option
            .setName('user')
            .setDescription('The user to get stats for')
            .setRequired(true)),

	async execute(interaction) {
        await interaction.deferReply({ flags: MessageFlags.Ephemeral });
        const Shame = counting.mapOfShame;
        const Fame = counting.mapOfFame;
        const userId = interaction.options.getUser('user').id;
        const current = counting.currentStreak.get(userId) || 0;
        const best = counting.bestStreak.get(userId) || 0;

		await interaction.editReply(
            `${interaction.options.getUser('user').username} has ${Fame.get(userId) || 0} correct counts and ${Shame.get(userId) || 0} incorrect counts.\n` +
            `Current streak: ${current}\nBest streak: ${best}`
        );
        logger.log(`Someone checked the stats for ${interaction.options.getUser('user').username}.`, 'checked_countstats', ENV.bs_server_id);
	},
};