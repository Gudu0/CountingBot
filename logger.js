const fs = require('node:fs');
const path = require('node:path');

let logChannel = null;
let errorThread = null;
let errorLoggingEnabled = true;

function setLogChannel(channel) {
    logChannel = channel;
}
function setErrorThread(thread) {
    errorThread = thread;
}


// Helper to check if a log key is enabled for a guild
function isLogEnabled(key, guildId) {
    try {
        const settingsPath = path.join(__dirname, 'data', 'logSettings.json');
        if (!fs.existsSync(settingsPath)) return true; // Default: log if no settings
        const settings = JSON.parse(fs.readFileSync(settingsPath, 'utf8'));
        if (!settings[guildId] || !settings[guildId].logs) return true;
        if (settings[guildId].logs[key] === undefined) return true; // Default: log if not set
        
        return settings[guildId].logs[key];
    } catch {
        return true; // Fail-safe: always log
    }
}

function log(message, key = 'default', guildId = null) {
    const enabled = guildId ? isLogEnabled(key, guildId) : true;
    console.log(`[log] key: ${key}, guildId: ${guildId}, enabled: ${enabled}`);
    if (guildId && !enabled) return;

    if (logChannel) {
        logChannel.send(message).catch(console.error);
    }
    if (errorThread && errorLoggingEnabled) {
        errorThread.send(message).catch(console.error);
    }
}


module.exports = { setLogChannel, setErrorThread, log };