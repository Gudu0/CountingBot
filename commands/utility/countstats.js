const { SlashCommandBuilder, MessageFlags } = require('discord.js');
const counting = require('../../counting.js');

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

        console.log(userId);
		await interaction.editReply(`${interaction.options.getUser('user').username} has ${Fame.get(userId) || 0} correct counts and ${Shame.get(userId) || 0} incorrect counts.`);
	},
};