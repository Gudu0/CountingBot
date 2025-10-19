const { Events, MessageFlags, Collection } = require('discord.js');
if (!global.logLineSelections) global.logLineSelections = {};
if (!global.logLineTimeouts) global.logLineTimeouts = {};

module.exports = {
	name: Events.InteractionCreate,
	async execute(interaction) {
		 // Handle multiple select menus for log lines
        if (interaction.isStringSelectMenu() && interaction.customId.startsWith('loglines_select_')) {
            const fs = require('node:fs');
            const path = require('node:path');
            const settingsPath = path.join(__dirname, '../logSettings.json');
            const guildId = interaction.guildId;
            let settings = {};
			
            if (fs.existsSync(settingsPath)) {
                settings = JSON.parse(fs.readFileSync(settingsPath, 'utf8'));
            }
            settings[guildId] = settings[guildId] || { logs: {} };

            // Gather all select menu values from this interaction
            // Discord only sends one menu's values at a time, so we need to merge with previous selections
            // We'll store selections in a temporary object on the user for the session (in-memory)
            if (!global.logLineSelections) global.logLineSelections = {};
            const userKey = `${guildId}_${interaction.user.id}`;
            global.logLineSelections[userKey] = global.logLineSelections[userKey] || {};
			
			// Clear any previous timeout
			if (global.logLineTimeouts[userKey]) {
				clearTimeout(global.logLineTimeouts[userKey]);
			}

			// Set a new timeout to clear selections after 60 seconds
			global.logLineTimeouts[userKey] = setTimeout(() => {
				delete global.logLineSelections[userKey];
				delete global.logLineTimeouts[userKey];
			}, 60000);

            // Get all log keys for this guild
            const allKeys = Object.keys(settings[guildId].logs);

            // Store the current menu's selection
            const menuIndex = interaction.customId.split('_').pop();
            global.logLineSelections[userKey][menuIndex] = interaction.values;

			await interaction.update({
				content: 'Selections saved for this menu. Continue with the next menu or click Submit when ready.',
				components: interaction.message.components,
				flags: MessageFlags.Ephemeral
			});
			return;
        }


		if (interaction.isButton() && interaction.customId === 'loglines_submit') {
			const fs = require('node:fs');
			const path = require('node:path');
			const settingsPath = path.join(__dirname, '../logSettings.json');
			const guildId = interaction.guildId;
			let settings = {};
			if (fs.existsSync(settingsPath)) {
				settings = JSON.parse(fs.readFileSync(settingsPath, 'utf8'));
			}
			settings[guildId] = settings[guildId] || { logs: {} };

			const userKey = `${guildId}_${interaction.user.id}`;
			// Get all log keys for this guild
			const allKeys = Object.keys(settings[guildId].logs);

			// Merge all selected values from all menus
			const selected = Object.values(global.logLineSelections[userKey] || {}).flat();

			// Set selected keys to true, others to false
			for (const key of allKeys) {
				settings[guildId].logs[key] = selected.includes(key);
			}
			try {
				fs.writeFileSync(settingsPath, JSON.stringify(settings, null, 2));
			} catch (err) {
				console.error('Failed to write logSettings.json:', err);
				await interaction.update({
					content: `❌ Failed to update log settings: ${err.message}`,
					components: [],
					flags: MessageFlags.Ephemeral
				});
				return;
			}
			// Clean up the temporary selections and timeout
			clearTimeout(global.logLineTimeouts[userKey]);
			delete global.logLineTimeouts[userKey];
			delete global.logLineSelections[userKey];

			await interaction.update({
				content: `Log lines updated! Enabled: ${selected.join(', ')}`,
				components: [],
				flags: MessageFlags.Ephemeral
			});
			return;
		}

		if (!interaction.isChatInputCommand()) return;

		const command = interaction.client.commands.get(interaction.commandName);

		if (!command) {
			console.error(`No command matching ${interaction.commandName} was found.`);
			return;
		}

        const { cooldowns } = interaction.client;

        if (!cooldowns.has(command.data.name)) {
            cooldowns.set(command.data.name, new Collection());
        }

        const now = Date.now();
        const timestamps = cooldowns.get(command.data.name);
        const defaultCooldownDuration = 3;
        const cooldownAmount = (command.cooldown ?? defaultCooldownDuration) * 1_000;

        if (timestamps.has(interaction.user.id)) {
            const expirationTime = timestamps.get(interaction.user.id) + cooldownAmount;

            if (now < expirationTime) {
                const expiredTimestamp = Math.round(expirationTime / 1_000);
                return interaction.reply({
                    content: `Please wait, you are on a cooldown for \`${command.data.name}\`. You can use it again <t:${expiredTimestamp}:R>.`,
                    flags: MessageFlags.Ephemeral,
                });
            }
        }
        timestamps.set(interaction.user.id, now);
        setTimeout(() => timestamps.delete(interaction.user.id), cooldownAmount);

		try {
			await command.execute(interaction);
		} catch (error) {
			console.error(error);
			if (interaction.replied || interaction.deferred) {
				await interaction.followUp({
					content: 'There was an error while executing this command!',
					flags: MessageFlags.Ephemeral,
				});
			} else {
				await interaction.reply({
					content: 'There was an error while executing this command!',
					flags: MessageFlags.Ephemeral,
				});
			}
		}
	},
};