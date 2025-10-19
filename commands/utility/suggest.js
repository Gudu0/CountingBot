const { SlashCommandBuilder, MessageFlags } = require('discord.js');
const fs = require('fs');
const path = require('path');

const suggestionsPath = path.join(__dirname, '../../data/suggestions.json');

module.exports = {
	cooldown: 5,
	data: new SlashCommandBuilder()
		.setName('suggest')
		.setDescription('Suggest features or anything for the bot!')
		.addStringOption(option =>
			option.setName('text')
				.setDescription('Your suggestion')
				.setRequired(true)
		),
	async execute(interaction) {
		await interaction.deferReply({ flags: MessageFlags.Ephemeral });

		const suggestion = interaction.options.getString('text');
		const userId = interaction.user.id;
		const timestamp = new Date().toISOString();

		let suggestions = [];
		if (fs.existsSync(suggestionsPath)) {
			try {
				suggestions = JSON.parse(fs.readFileSync(suggestionsPath, 'utf8'));
			} catch (err) {
				// If file is corrupted, start fresh
				suggestions = [];
			}
		}

		suggestions.push({ userId, suggestion, timestamp });

		try {
			fs.writeFileSync(suggestionsPath, JSON.stringify(suggestions, null, 2), 'utf8');
			await interaction.editReply('Thank you for your suggestion! It has been recorded.');
		} catch (err) {
			await interaction.editReply('Sorry, there was an error saving your suggestion.');
		}
	},
};