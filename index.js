const fs = require('node:fs');
const path = require('node:path');
const { Client, Collection, Events, GatewayIntentBits, MessageFlags } = require('discord.js');
const { token } = require('./config.json');
const counting = require('./counting.js');

// SETTING UP GATEWAY ============================================================================================================
const { WebSocket } = require('ws');
const ENV = require("./config.json");

const initialurl = "wss://gateway.discord.gg";
let url = initialurl,
    session_id = ""; 
let ws;
let interval = 0,
    seq = -1;

let payload = {
    op: 2,
    d: {
        token: ENV.gatewayToken,
        intents: 33280,
        properties: {
            $os: "Windows",
            $browser: "Firefox",
            $device: "Firefox",
        }
    }
}

const heartbeat = (ms) => {
    return setInterval(() => {
        ws.send(JSON.stringify({ op: 1, d: null }));
    }, ms)
}

const initializeWebsocket = () => {
    if (ws && ws.readyState != 3) ws.close();
    
    let wasReady = false;

    ws = new WebSocket(url + "/?v=10&encoding=json");

    ws.on("open", function open() {
        if (url !== initialurl) {
            const resumePayload = {
                op: 6,
                d: { 
                    token: ENV.gatewayToken,
                    session_id,
                    seq,
                },
            };

            ws.send(JSON.stringify(resumePayload));
        }
    })

    ws.on("error", function error(e) {
        console.log(e)
    });

    ws.on("close", function close() {
        if (wasReady) console.log("Gateway connection closed, trying to reconnect..")

        setTimeout(() => {
            initializeWebsocket();
        }, 2500)
    })

    ws.on("message", function incoming(data) {
        let p = JSON.parse(data);
        const { t, op, d, s } = p; 

        switch (op) {
            case 10: 
                const { heartbeat_interval } = d;
                interval = heartbeat(heartbeat_interval);
                wasReady = true;

                if (url === initialurl) ws.send(JSON.stringify(payload));
                break;

            case 0: 
                seq = s;
                break;
        }

        switch (t) {
            case "READY": 
                console.log("Gateway connection ready!");
                url = d.resume_gateway_url;
                session_id = d.session_id;
                break;
            case "RESUMED": 
                console.log("Gateway connection resumed!");
                break;
            case "MESSAGE_CREATE": 
                counting.newMessage(d, ENV, client);
                break;
        }
    })
}


initializeWebsocket();

// SETTING UP CLIENT ============================================================================================================
const client = new Client({ intents: [
    GatewayIntentBits.Guilds,
    GatewayIntentBits.GuildMessages,
    GatewayIntentBits.MessageContent
] })


client.commands = new Collection(); 
client.cooldowns = new Collection();

//command finding
const foldersPath = path.join(__dirname, 'commands');
const commandFolders = fs.readdirSync(foldersPath);

for (const folder of commandFolders) {
	const commandsPath = path.join(foldersPath, folder);
	const commandFiles = fs.readdirSync(commandsPath).filter((file) => file.endsWith('.js'));
	for (const file of commandFiles) {
		const filePath = path.join(commandsPath, file);
		const command = require(filePath);
		// Set a new item in the Collection with the key as the command name and the value as the exported module
		if ('data' in command && 'execute' in command) {
			client.commands.set(command.data.name, command);
		} else {
			console.log(`[WARNING] The command at ${filePath} is missing a required "data" or "execute" property.`);
		}
	}
}

//event handling 
const eventsPath = path.join(__dirname, 'events');
const eventFiles = fs.readdirSync(eventsPath).filter((file) => file.endsWith('.js'));

for (const file of eventFiles) {
	const filePath = path.join(eventsPath, file);
	const event = require(filePath);
	if (event.once) {
		client.once(event.name, (...args) => event.execute(...args));
	} else {
		client.on(event.name, (...args) => event.execute(...args));
	}
}

counting.loadStats();
client.mapOfShame = counting.mapOfShame;
client.mapOfFame = counting.mapOfFame;
setInterval(counting.saveStats, 60 * 1000); // Save every 60 seconds

client.login(token);


client.once('clientReady', async () => {
    const channel = client.channels.cache.get(ENV.bs_counting_id);
    if (!channel) {
        console.log('Counting channel not found!');
        return;
    }
    try {
        // Fetch the last message in the channel
        const messages = await channel.messages.fetch({ limit: 1 });
        const lastMsg = messages.first();
        if (lastMsg) {
            const num = parseInt(lastMsg.content);
            const id = lastMsg.author.id;
            if (!isNaN(num)) {
                // Set lastNumber in counting.js
                counting.setLastNumber(num);
                counting.setLastUser(id);
                console.log(`Initialized lastNumber to ${num} from last message.`);
                console.log(`Initialized lastUser to ${id} from last message.`);
            }
        }
    } catch (err) {
        console.error('Error fetching last message:', err);
    }
});