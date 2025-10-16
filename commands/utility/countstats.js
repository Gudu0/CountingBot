const { SlashCommandBuilder, MessageFlags } = require('discord.js');

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
        const mapOfShame = interaction.client.mapOfShame;
        const mapOfFame = interaction.client.mapOfFame;

        console.log(interaction.options.getUser('user').id);
        //await interaction.reply('Calculating...');
		await interaction.editReply(`You have ${mapOfFame.get(interaction.options.getUser('user').id) || 0} correct counts and ${mapOfShame.get(interaction.options.getUser('user').id) || 0} incorrect counts.`);
	},
};