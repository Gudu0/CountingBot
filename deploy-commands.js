const { REST, Routes } = require('discord.js');
const { clientId, guildId, token } = require('./config.json');
const fs = require('node:fs');
const path = require('node:path');

const commands = [];
// Grab all the command folders from the commands directory you created earlier
const foldersPath = path.join(__dirname, 'commands');
const commandFolders = fs.readdirSync(foldersPath);

for (const folder of commandFolders) {
    const commandsPath = path.join(foldersPath, folder);
    const commandFiles = fs.readdirSync(commandsPath).filter((file) => file.endsWith('.js'));
    for (const file of commandFiles) {
        const filePath = path.join(commandsPath, file);
        const command = require(filePath);
        // Only register commands that are not testonly
        if ('data' in command && 'execute' in command && !command.testonly) {
            commands.push(command.data.toJSON());
        } else if (command.testonly) {
            console.log(`[INFO] Skipping test-only command at ${filePath}`);
        } else {
            console.log(`[WARNING] The command at ${filePath} is missing a required "data" or "execute" property.`);
        }
    }
}

// Construct and prepare an instance of the REST module
const rest = new REST().setToken(token);

// and deploy your commands!
(async () => {
	try {
		console.log(`Started refreshing ${commands.length} application (/) commands.`);

        // For commands for only a specific server
        const data = await rest.put(Routes.applicationGuildCommands(clientId, guildId), { body: commands });

        // For commands global 
        // const data = await rest.put(Routes.applicationCommands(clientId), { body: commands });

        // To delete commands
        //const data = await rest.put(Routes.applicationCommands(clientId), { body: [] });
		//const data2 = await rest.put(Routes.applicationGuildCommands(clientId, guildId), { body: [] });

		console.log(`Successfully reloaded ${data.length} application (/) commands.`);
	} catch (error) {
		// And of course, make sure you catch and log any errors!
		console.error(error);
	}
})();