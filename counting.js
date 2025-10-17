const fs = require('node:fs');
const { get } = require('node:http');
const path = require('node:path');
const ENV = require("./config.json");
const logger = require('./logger.js');

let lastNumber;
let mapOfShame = new Map();
let mapOfFame = new Map();
let lastUser;


function newMessage(d, ENV, client) {
    let author = d.author.id;
    let content = d.content;
    let messageId = d.last_message_id; 
     
    
    if (d.guild_id !== ENV.bs_server_id) {
        return;
    }
    if (d.channel_id !== ENV.bs_counting_id) {
        return;
    }   

    //in the counting channel in bsg
    let newNumber = parseInt(d.content); 
    if ((lastNumber + 1 === newNumber || lastNumber - 1 === newNumber) && lastUser !== author) {
        //correct

        lastNumber = newNumber;
        lastUser = author;
        if (!mapOfFame.has(author)) {
            mapOfFame.set(author, 0);
        } 
        mapOfFame.set(author, mapOfFame.get(author) + 1);
        saveStats()
        logger.botLog(`${author} had a correct number, with ${newNumber}! They now have ${mapOfFame.get(author)} correct counts.`);
    } else if (lastNumber === undefined) {
        //don't know 

        logger.botLog('first number or broken');
        logger.botError('first number or broken');
        //first number, or broken.
        lastNumber = newNumber;
        lastUser = author;
    } else {
        //incorrect
        
        const channel = client.channels.cache.get(ENV.bs_counting_id);
        if (channel) {
            logger.botLog(`Deleting message ${d.id} in channel ${d.channel_id}`);
            logger.botError(`Deleting message ${d.id} in channel ${d.channel_id}`);
            channel.messages.fetch(d.id)
                .then(message => message.delete())
                .catch(err => {
            if (err.code === 10008) {
                logger.botLog('Tried to delete a message that does not exist.');
                logger.botError('Tried to delete a message that does not exist.');
            } else {
                console.error(err);
            }
        });
        }

        if (!mapOfShame.has(author)) {
            mapOfShame.set(author, 0);
        } 
        mapOfShame.set(author, mapOfShame.get(author) + 1); 
        saveStats()
        if (lastUser == author) {
            logger.botLog(`${author} Counted twice in a row! They now have ${mapOfShame.get(author)} incorrect counts.`)
            logger.botError(`${author} Counted twice in a row! They now have ${mapOfShame.get(author)} incorrect counts.`)
        } else {
          logger.botLog(`${author} had a incorrect number, with ${newNumber}! They now have ${mapOfShame.get(author)} incorrect counts.`)
          logger.botError(`${author} had a incorrect number, with ${newNumber}! They now have ${mapOfShame.get(author)} incorrect counts.`)
        }
        ;
    }
}

function saveStats() {
    logger.botLog('Saving stats to countingStats.json');
    fs.writeFileSync(
        path.join(__dirname, 'countingStats.json'),
        JSON.stringify({
            shame: Array.from(mapOfShame.entries()),
            fame: Array.from(mapOfFame.entries())
        }, null, 2)
    );
}

function loadStats() {
    try {
        const data = fs.readFileSync(path.join(__dirname, 'countingStats.json'), 'utf8');
        const stats = JSON.parse(data);

        mapOfShame.clear();
        for (const [key, value] of stats.shame) {
            mapOfShame.set(key, value);
        }

        mapOfFame.clear();
        for (const [key, value] of stats.fame) {
            mapOfFame.set(key, value);
        }
    } catch (err) {
        logger.botLog('No stats file found, starting fresh.');
        logger.botError('No stats file found, starting fresh.');
    }
}

function setLastNumber(num) {
    lastNumber = num;
}
function getLastNumber() {
    return lastNumber;
}
function setLastUser(id) {
    lastUser = id;
}

module.exports = {
    mapOfShame,
    mapOfFame,
    newMessage,
    saveStats,
    loadStats,
    lastNumber,
    setLastNumber,
    getLastNumber,
    setLastUser
};