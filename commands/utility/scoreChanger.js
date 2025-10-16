const { SlashCommandBuilder, MessageFlags } = require('discord.js');

module.exports = {
	cooldown: 5,
    testonly: true,
	data: new SlashCommandBuilder()
        .setName('scorechanger')
        .setDescription('Changes the score!')
        .addIntegerOption(option => 
            option
                .setName('score')
                .setDescription('The score to set yourself to')
                .setRequired(true))
        .addStringOption(option =>
            option
                .setName('fameorshame')
                .setDescription('Fame or Shame')
                .setRequired(true)
                .addChoices(
                    { name: 'Fame', value: 'fame' },
                    { name: 'Shame', value: 'shame' },
                )),

	async execute(interaction) {
        await interaction.deferReply({ flags: MessageFlags.Ephemeral });
        const mapOfFame = interaction.client.mapOfFame;
        const mapOfShame = interaction.client.mapOfShame;
        if (interaction.options.getString('fameorshame') === 'shame') {
            mapOfShame.set(interaction.user.id, interaction.options.getInteger('score'));
            console.log(mapOfShame);
        } else {
            mapOfFame.set(interaction.user.id, interaction.options.getInteger('score'));
            console.log(mapOfFame); 
        }

		
        await interaction.editReply(`Your ${interaction.options.getString('fameorshame')} score has been changed to ${interaction.options.getInteger('score')}!`);
	},
};