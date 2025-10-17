let logChannel = null;
let errorThread = null;
let errorLoggingEnabled = false;

function setLogChannel(channel) {
    logChannel = channel;
}
function setErrorThread(thread) {
    errorThread = thread;
}

function botLog(message) {
    if (logChannel) {
        logChannel.send(message).catch(console.error);
    }
    //console.log(message); // Optional: still log to console
}

function botError(message) {
    botLog(message);
    if (errorThread && errorLoggingEnabled) {
        errorThread.send(message).catch(console.error);
    }
}

module.exports = { setLogChannel, botLog, botError, setErrorThread };