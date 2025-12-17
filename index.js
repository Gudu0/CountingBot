const fs = require('node:fs');
const path = require('node:path');
const { Client, Collection, Events, GatewayIntentBits, MessageFlags } = require('discord.js');
const { token } = require('./data/config.json');
const counting = require('./counting.js');
const logger = require('./logger.js');
const os = require('os');


// SETTING UP GATEWAY ============================================================================================================
const { WebSocket } = require('ws');
const ENV = require("./data/config.json");

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
        logger.log(e, 'gateway_error', ENV.bs_server_id);
        logger.log("Gateway error occurred " + '@733113260496126053', ENV.bs_server_id);
        console.log(e);
    });

    ws.on("close", function close() {
        if (wasReady) {
            console.log("Gateway connection closed, trying to reconnect..");
            logger.log("Gateway connection closed, trying to reconnect..", 'gateway_connection_closed', ENV.bs_server_id);
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
                console.log("Gateway connection ready!");
                logger.log("Gateway connection ready!", "gateway_connection_ready", ENV.bs_server_id);
                url = d.resume_gateway_url;
                session_id = d.session_id;
                break;
            case "RESUMED": 
                console.log("Gateway connection resumed!");
                logger.log("Gateway connection resumed!", "gateway_connection_resumed", ENV.bs_server_id);
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
			logger.log(`[WARNING] The command at ${filePath} is missing a required "data" or "execute" property.`  + '@733113260496126053', 'command_missing_data_or_execute', ENV.bs_server_id);
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
client.currentStreak = counting.currentStreak;
client.bestStreak = counting.bestStreak;
setInterval(counting.saveStats, 60 * 1000 * 20); // Save every 20 minutes


client.login(token);


client.once('clientReady', async () => {
    
    const channel = client.channels.cache.get(ENV.bs_counting_id);
    const logChannel = client.channels.cache.get(ENV.log_channel_id); // Add this line
    if (logChannel) {
        logger.setLogChannel(logChannel);
        logger.log(`Bot started on host: ${os.hostname()} (${os.platform()})`, 'bot_started', ENV.bs_server_id);
        console.log('Logging initialized!');
        logger.log('Logging initialized!', 'logging_initialized', ENV.bs_server_id);
    } else {
        logger.log('Log channel not found!' + '@733113260496126053', 'log_channel_not_found', ENV.bs_server_id);
        console.log('Log channel not found!');
    }
    const logThread = await client.channels.fetch(ENV.log_thread_id);
    if (logThread && logThread.isThread()) {
        logger.setErrorThread(logThread);
        logger.log('Error logging initialized in thread!', 'error_logging_initialized', ENV.bs_server_id);
    } else {
        logger.log('Log thread not found or is not a thread!' + '@733113260496126053', 'log_thread_not_found', ENV.bs_server_id);
    }
    if (!channel) {
        logger.log('Counting channel not found!' + '@733113260496126053', 'counting_channel_not_found', ENV.bs_server_id);
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
                logger.log(`Initialized lastUser to ${id} from last message.`, 'last_user_initialized', ENV.bs_server_id);
                logger.log(`Initialized lastNumber to ${num} from last message.`, 'last_number_initialized', ENV.bs_server_id);
            }
        }
    } catch (err) {
        logger.log(`Error fetching last message: ${err.message}` + '@733113260496126053', 'error_fetching_last_message', ENV.bs_server_id);
        console.error('Error fetching last message:', err);

    }
});