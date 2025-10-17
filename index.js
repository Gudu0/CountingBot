const fs = require('node:fs');
const path = require('node:path');
const { Client, Collection, Events, GatewayIntentBits, MessageFlags } = require('discord.js');
const { token } = require('./config.json');
const counting = require('./counting.js');
const logger = require('./logger.js');


// SETTING UP GATEWAY ============================================================================================================
const { WebSocket } = require('ws');
const ENV = require("./config.json");
const { log } = require('node:console');

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
        logger.botLog(e)
        console.log(e);
    });

    ws.on("close", function close() {
        if (wasReady) {
            logger.botLog("Gateway connection closed, trying to reconnect..");
            console.log("Gateway connection closed, trying to reconnect..");
            logger.botError("Gateway connection closed, trying to reconnect..");
        }
            
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
                logger.botLog("Gateway connection ready!");
                console.log("Gateway connection ready!");
                logger.botError("Gateway connection ready!");
                url = d.resume_gateway_url;
                session_id = d.session_id;
                break;
            case "RESUMED": 
                logger.botLog("Gateway connection resumed!");
                console.log("Gateway connection resumed!");
                logger.botError("Gateway connection resumed!");
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
			logger.botLog(`[WARNING] The command at ${filePath} is missing a required "data" or "execute" property.`);
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
    const logChannel = client.channels.cache.get(ENV.log_channel_id); // Add this line
    if (logChannel) {
        logger.setLogChannel(logChannel);
        logger.botLog('Logging initialized!');
        console.log('Logging initialized!');
        logger.botError('Error logging initialized!');
    } else {
        logger.botLog('Log channel not found!');
        console.log('Log channel not found!');
    }
    const logThread = await client.channels.fetch(ENV.log_thread_id);
    if (logThread && logThread.isThread()) {
        logger.setErrorThread(logThread);
        logger.botError('Error logging initialized in thread!');
    } else {
        logger.botLog('Log thread not found or is not a thread!');
    }
    if (!channel) {
        logger.botLog('Counting channel not found!');
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
                logger.botLog(`Initialized lastNumber to ${num} from last message.`);
                logger.botLog(`Initialized lastUser to ${id} from last message.`);
                logger.botError(`Initialized lastUser to ${id} from last message.`);
                logger.botError(`Initialized lastNumber to ${num} from last message.`);
            }
        }
    } catch (err) {
        logger.botLog(`Error fetching last message: ${err.message}`);
        console.error('Error fetching last message:', err);

    }
});