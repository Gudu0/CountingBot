const fs = require('node:fs');
const { get } = require('node:http');
const path = require('node:path');
const ENV = require("./config.json");
const logger = require('./logger.js');

let lastNumber;
let lastUser;
let mapOfShame = new Map();
let mapOfFame = new Map();
let currentStreak = new Map();
let bestStreak = new Map();
let dailyCounts = new Map();


function newMessage(d, ENV, client) {
    let author = d.author.id;
    let content = d.content.trim();
    
    

    if (d.guild_id !== ENV.bs_server_id) {
        return;
    }
    if (d.channel_id !== ENV.bs_counting_id) {
        return;
    }  

    if (!/^-?\d+$/.test(content)) {
        // Mark as incorrect
        incorrectCount(d, author, NaN, ENV, client);
        return;
    }
    //in the counting channel in bsg
    let newNumber = parseInt(content); 
    if ((lastNumber + 1 === newNumber || lastNumber - 1 === newNumber) && lastUser !== author) {
        //correct

        correctCount(author, newNumber);
    } else if (lastNumber === undefined) {
        //something went wrong
        logger.botError('last number not loaded properly or broken');
        lastNumber = newNumber;
        lastUser = author;
    } else {
        //incorrect
        incorrectCount(d, author, newNumber, ENV, client);
    }
}


function correctCount(author, newNumber ) {
    //set most recent number and user ------------------------------
    lastNumber = newNumber;
    lastUser = author;

    //mapOfFame update ---------------------------------------------
    if (!mapOfFame.has(author)) {
            mapOfFame.set(author, 0);
    } 
    mapOfFame.set(author, mapOfFame.get(author) + 1);
    saveStats();

    //Streak update ------------------------------------------------
    currentStreak.set(author, (currentStreak.get(author) || 0) + 1);
    if ((bestStreak.get(author) || 0) < currentStreak.get(author)) {
        bestStreak.set(author, currentStreak.get(author));
    }

    //Logging ------------------------------------------------------
    logger.botLog(`${author} had a correct number, with ${newNumber}! They now have ${mapOfFame.get(author)} correct counts.`);

    //Daily counts update -------------------------------------------
    const today = new Date().toISOString().slice(0, 10); // "YYYY-MM-DD"
    dailyCounts.set(today, (dailyCounts.get(today) || 0) + 1);

}



function incorrectCount(d, author, newNumber, ENV, client) {
    console.log(`Incorrect: ${d.author.username}: ${d.content}`);
    //delete the message ---------------------------------------
    const channel = client.channels.cache.get(ENV.bs_counting_id);
    if (channel) {
            logger.botError(`Deleting message ${d.id} in channel ${d.channel_id}`);
            channel.messages.fetch(d.id)
                .then(message => message.delete())
                .catch(err => {
            if (err.code === 10008) {
                logger.botError('Tried to delete a message that does not exist.');
            } else {
                console.error(err);
            }
        });
    }

    //set streak to 0
    currentStreak.set(author, 0);

    //update mapOfShame -------------------------------------------
    if (!mapOfShame.has(author)) {
            mapOfShame.set(author, 0);
    }
    mapOfShame.set(author, mapOfShame.get(author) + 1);
    saveStats();

    //Determine type of incorrect count ---------------------------
    if (lastUser == author) {
        logger.botError(`${author} Counted twice in a row! They now have ${mapOfShame.get(author)} incorrect counts.`);
    } else {
      logger.botLog(`${author} had a incorrect number, with ${newNumber}! They now have ${mapOfShame.get(author)} incorrect counts.`);
    }
}



function saveStats() {
    logger.botLog('Saving stats to countingStats.json');
    fs.writeFileSync(
        path.join(__dirname, 'countingStats.json'),
        JSON.stringify({
            shame: Array.from(mapOfShame.entries()),
            fame: Array.from(mapOfFame.entries()),
            currentStreak: Array.from(currentStreak.entries()),
            bestStreak: Array.from(bestStreak.entries()),
            dailyCounts: Array.from(dailyCounts.entries())
        }, null, 2)
    );
}



function loadStats() {
    try {
        const data = fs.readFileSync(path.join(__dirname, 'countingStats.json'), 'utf8');
        const stats = JSON.parse(data);

        mapOfShame.clear();
        for (const [key, value] of stats.shame || []) {
            mapOfShame.set(key, value);
        }

        mapOfFame.clear();
        for (const [key, value] of stats.fame || []) {
            mapOfFame.set(key, value);
        }

        currentStreak.clear();
        for (const [key, value] of stats.currentStreak || []) {
            currentStreak.set(key, value);
        }

        bestStreak.clear();
        for (const [key, value] of stats.bestStreak || []) {
            bestStreak.set(key, value);
        }

        dailyCounts.clear();
        for (const [key, value] of stats.dailyCounts || []) {
            dailyCounts.set(key, value);
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
    setLastUser, 
    currentStreak,
    bestStreak,
    dailyCounts
};