const { REST, Routes } = require('discord.js');
const { clientId, guildId, token } = require('./config.json');
const fs = require('node:fs');
const path = require('node:path');
const readline = require('node:readline');

const commands = [];
const foldersPath = path.join(__dirname, 'commands');
const commandFolders = fs.readdirSync(foldersPath);

const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout
});

async function promptInclude(filePath) {
    return new Promise((resolve) => {
        rl.question(`Include command from ${filePath}? (y/n): `, (answer) => {
            resolve(answer.trim().toLowerCase() === 'y');
        });
    });
}


(async () => {
    for (const folder of commandFolders) {
        const commandsPath = path.join(foldersPath, folder);
        const commandFiles = fs.readdirSync(commandsPath).filter((file) => file.endsWith('.js'));
        for (const file of commandFiles) {
            const filePath = path.join(commandsPath, file);
            const command = require(filePath);
            if ('data' in command && 'execute' in command) {
                const include = await promptInclude(filePath);
                if (include) {
                    commands.push(command.data.toJSON());
                } else {
                    console.log(`[INFO] Skipping command at ${filePath}`);
                }
            } else {
                console.log(`[WARNING] The command at ${filePath} is missing a required "data" or "execute" property.`);
            }
        }
    }

    rl.close();

    const rest = new REST().setToken(token);
    	try {
		console.log(`Started refreshing ${commands.length} application (/) commands.`);

        // For commands for only a specific server
        // const data = await rest.put(Routes.applicationGuildCommands(clientId, guildId), { body: commands });

        // For commands global 
        // const data = await rest.put(Routes.applicationCommands(clientId), { body: commands });

        // To delete commands
        //const data = await rest.put(Routes.applicationCommands(clientId), { body: [] });
		const data = await rest.put(Routes.applicationGuildCommands(clientId, guildId), { body: [] });

		console.log(`Successfully reloaded ${data.length} application (/) commands.`);
	} catch (error) {
		// And of course, make sure you catch and log any errors!
		console.error(error);
	}
})();