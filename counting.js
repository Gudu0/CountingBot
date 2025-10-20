const fs = require('node:fs');
const { get } = require('node:http');
const path = require('node:path');
const ENV = require("./data/config.json");
const logger = require('./logger.js');
const achievements = require('./achievement-check.js');

let lastNumber;
let lastUser;
let mapOfShame = new Map();
let mapOfFame = new Map();
let currentStreak = new Map();
let bestStreak = new Map();
let dailyCounts = new Map();
let lastCountTime = new Map();
let positiveCounts = new Map();
let negativeCounts = new Map();
const GOAL_UPDATE_PERCENT_THRESHOLD = 1; // percent change required to edit pinned embed


async function newMessage(d, ENV, client) {
    let author = d.author.id;
    let content = d.content.trim();
    

    if (d.guild_id !== ENV.bs_server_id) {
        return;
    }
    if (d.channel_id !== ENV.bs_counting_id) {
        return;
    }  
    
    achievements.checkAndAwardAchievements(d, mapOfFame, currentStreak, positiveCounts, negativeCounts);

    if (!/^-?\d+$/.test(content)) {
        // Mark as incorrect
        incorrectCount(d, author, NaN, ENV, client);
        logger.log(`${author} sent a non-numeric message!`, 'non_numeric', ENV.bs_server_id);
        return;
    }
    //in the counting channel in bsg
    let newNumber = parseInt(content); 

    const now = Date.now();
    if (lastCountTime.has(author) && now - lastCountTime.get(author) < 5000) {
        // Too soon, mark as incorrect
        incorrectCount(d, author, newNumber, ENV, client);
        logger.log(`${author} is counting too fast!`, 'counted_too_fast', ENV.bs_server_id);
        return;
    }
    if ((lastNumber + 1 === newNumber || lastNumber - 1 === newNumber) && lastUser !== author) {
        //correct

    await correctCount(author, newNumber, client);
    } else if (lastNumber === undefined) {
        //something went wrong
        logger.log('last number not loaded properly or broken', 'last_number_not_loaded', ENV.bs_server_id);
        lastNumber = newNumber;
        lastUser = author;
    } else {
        //incorrect
        incorrectCount(d, author, newNumber, ENV, client);
    }
}

async function correctCount(author, newNumber, client ) {

    // Store previous lastNumber for positive/negative check
    const prevLastNumber = lastNumber;

    //set most recent number and user ------------------------------
    lastNumber = newNumber;
    lastUser = author;

    //update last count time --------------------------------------
    lastCountTime.set(author, Date.now());

    //mapOfFame update ---------------------------------------------
    if (!mapOfFame.has(author)) {
        mapOfFame.set(author, 0);
    }
    mapOfFame.set(author, mapOfFame.get(author) + 1);
    saveStats();

    //positive/negative counts update -----------------------------
    if (newNumber === prevLastNumber + 1) {
        positiveCounts.set(author, (positiveCounts.get(author) || 0) + 1);
    } else if (newNumber === prevLastNumber - 1) {
        negativeCounts.set(author, (negativeCounts.get(author) || 0) + 1);
    }
    saveStats();

    //Streak update ------------------------------------------------
    currentStreak.set(author, (currentStreak.get(author) || 0) + 1);
    if ((bestStreak.get(author) || 0) < currentStreak.get(author)) {
        bestStreak.set(author, currentStreak.get(author));
    }

    //Logging ------------------------------------------------------
    logger.log(`${author} had a correct number, with ${newNumber}! They now have ${mapOfFame.get(author)} correct counts.`, 'correct_count', ENV.bs_server_id);

    //Daily counts update -------------------------------------------
    const today = new Date().toISOString().slice(0, 10); // "YYYY-MM-DD"
    dailyCounts.set(today, (dailyCounts.get(today) || 0) + 1);

    // Check for active goal completion
    try {
        const goalsPath = path.join(__dirname, 'data', 'goals.json');
        if (fs.existsSync(goalsPath)) {
            const goalsData = JSON.parse(fs.readFileSync(goalsPath, 'utf8'));
            const goal = goalsData.current;
            if (goal && !goal.completedAt && goal.target !== null && goal.target !== undefined) {
                const target = Number(goal.target);
                let reached = false;
                if (isNaN(target)) reached = false;
                else if (target >= 0) {
                    if (lastNumber >= target) reached = true;
                } else {
                    if (lastNumber <= target) reached = true;
                }

                // compute progress percent and possibly update pinned embed when percent increased
                const computePercent = (current, target) => {
                    const t = Number(target);
                    if (isNaN(t) || t === 0) return 0;
                    let percent = 0;
                    if (t > 0) percent = Math.round((current / t) * 100);
                    else {
                        const denom = (0 - t);
                        const numer = (0 - current);
                        percent = denom === 0 ? 0 : Math.round((numer / denom) * 100);
                    }
                    if (percent < 0) percent = 0;
                    if (percent > 100) percent = 100;
                    return percent;
                };

                const makeBar = (percent, size = 16) => {
                    const fill = Math.round((percent / 100) * size);
                    return '█'.repeat(fill) + '░'.repeat(size - fill);
                };

                const currentPercent = computePercent(lastNumber, goal.target);
                const lastReported = Number(goal.lastReportedPercent || 0);

                const shouldUpdatePin = (currentPercent - lastReported) >= GOAL_UPDATE_PERCENT_THRESHOLD;

                if (shouldUpdatePin && goal.pinnedMessageId && client) {
                    try {
                        const config = JSON.parse(fs.readFileSync(path.join(__dirname, 'data', 'config.json'), 'utf8'));
                        const countingChannelId = config.test_counting_id || config.bs_counting_id;
                        const channel = await client.channels.fetch(countingChannelId).catch(() => null);
                        if (channel) {
                            const msg = await channel.messages.fetch(goal.pinnedMessageId).catch(() => null);
                            if (msg) {
                                const { EmbedBuilder } = require('discord.js');
                                const displayDeadline = goal.deadline ? (isNaN(new Date(goal.deadline)) ? String(goal.deadline) : new Date(goal.deadline).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })) : 'None';
                                const pb = makeBar(currentPercent, 16);
                                const newEmbed = new EmbedBuilder()
                                    .setTitle('Counting Goal')
                                    .setDescription(goal.text)
                                    .setTimestamp(new Date(goal.createdAt || Date.now()))
                                    .setColor(0x00AE86)
                                    .addFields(
                                        { name: 'Set by', value: `<@${goal.setBy}>`, inline: true },
                                        { name: 'Target', value: goal.target ? String(goal.target) : 'None', inline: true },
                                        { name: 'Deadline', value: displayDeadline, inline: true },
                                        { name: 'Progress', value: `${pb} ${currentPercent}%\n${lastNumber} / ${goal.target}`, inline: false }
                                    );
                                await msg.edit({ embeds: [newEmbed] }).catch(() => null);
                                // persist lastReportedPercent
                                goal.lastReportedPercent = currentPercent;
                                fs.writeFileSync(goalsPath, JSON.stringify(goalsData, null, 2), 'utf8');
                            }
                        }
                    } catch (err) {
                        console.error('Error updating pinned goal message (progress):', err);
                    }
                }

                if (reached) {
                    // mark goal completed
                    goal.completedAt = new Date().toISOString();
                    goal.completedBy = author;
                    // Update pinned message if possible
                    const config = JSON.parse(fs.readFileSync(path.join(__dirname, 'data', 'config.json'), 'utf8'));
                    const countingChannelId = config.test_counting_id || config.bs_counting_id ;
                    if (goal.pinnedMessageId && countingChannelId && client) {
                        try {
                            const channel = await client.channels.fetch(countingChannelId).catch(() => null);
                            if (channel) {
                                const msg = await channel.messages.fetch(goal.pinnedMessageId).catch(() => null);
                                if (msg) {
                                    // Append completed info to embed
                                    const embeds = msg.embeds || [];
                                    let embed = embeds[0] ? embeds[0] : null;
                                    const { EmbedBuilder } = require('discord.js');
                                    const newEmbed = new EmbedBuilder()
                                        .setTitle('Counting Goal')
                                        .setDescription(goal.text)
                                        .setTimestamp(new Date().toISOString())
                                        .setColor(0x00AE86)
                                        .addFields(
                                            { name: 'Set by', value: `<@${goal.setBy}>`, inline: true },
                                            { name: 'Target', value: goal.target ? String(goal.target) : 'None', inline: true },
                                            { name: 'Deadline', value: goal.deadline ? String(goal.deadline) : 'None', inline: true },
                                            { name: 'Completed', value: `Yes — <@${author}> at ${goal.completedAt}`, inline: false }
                                        );
                                    await msg.edit({ embeds: [newEmbed] }).catch(() => null);
                                }
                            }
                        } catch (err) {
                            console.error('Error updating pinned goal message:', err);
                        }
                    }

                    // Save goal state
                    fs.writeFileSync(goalsPath, JSON.stringify(goalsData, null, 2), 'utf8');

                    // Announce using logger
                    try {
                        logger.log(`Goal reached: ${goal.text} — final number ${lastNumber} (by <@${author}>)`, 'goal_completed', ENV.bs_server_id);
                    } catch (err) {
                        console.error('Error logging goal completion:', err);
                    }

                    // Award achievement to final user (if available)
                    try {
                        achievements.awardAchievement(author, 'goal_winner');
                    } catch (err) {
                        console.error('Error awarding goal_winner achievement:', err);
                    }
                }
            }
        }
    } catch (err) {
        console.error('Error checking goal completion:', err);
    }

}

function incorrectCount(d, author, newNumber, ENV, client) {
    // If the bot sent the message, don't delete or penalize it
    if (client && client.user && author === client.user.id) {
        logger.log(`Skipping incorrect handling for bot message ${d.id}`, 'skip_bot_message', ENV.bs_server_id);
        return;
    }
    console.log(`Incorrect: ${d.author.username}: ${d.content}`);
    //delete the message ---------------------------------------
    const channel = client.channels.cache.get(ENV.bs_counting_id);
    if (channel) {
            logger.log(`Deleting message ${d.id} in channel ${d.channel_id}`, 'deleting_message', ENV.bs_server_id);
            channel.messages.fetch(d.id)
                .then(message => message.delete())
                .catch(err => {
            if (err.code === 10008) {
                logger.log('Tried to delete a message that does not exist.', 'message_not_found', ENV.bs_server_id);
            } else {
                console.error(err);
            }
        });
    }

    //set streak to 0
    currentStreak.set(author, 0);

    //update mapOfShame -------------------------------------------
    if (author !== client.user.id) {
       if (!mapOfShame.has(author)) {
            mapOfShame.set(author, 0);
       }
        mapOfShame.set(author, mapOfShame.get(author) + 1);
        saveStats(); 
    }
    

    //Determine type of incorrect count ---------------------------
    if (lastUser === author) {
        logger.log(`${author} Counted twice in a row! They now have ${mapOfShame.get(author)} incorrect counts.`, 'counted_twice_in_a_row', ENV.bs_server_id);
    } else {
      logger.log(`${author} had a incorrect number, with ${newNumber}! They now have ${mapOfShame.get(author)} incorrect counts.`, 'incorrect_count', ENV.bs_server_id);
    }
}

function saveStats() {
    logger.log('Saving stats to countingStats.json', 'saving_stats', ENV.bs_server_id);
    try {
        fs.writeFileSync(
            path.join(__dirname, 'data', 'countingStats.json'),
            JSON.stringify({
                shame: Array.from(mapOfShame.entries()),
                fame: Array.from(mapOfFame.entries()),
                currentStreak: Array.from(currentStreak.entries()),
                bestStreak: Array.from(bestStreak.entries()),
                dailyCounts: Array.from(dailyCounts.entries()),
                positiveCounts: Array.from(positiveCounts.entries()),
                negativeCounts: Array.from(negativeCounts.entries())
            }, null, 2)
        );
    } catch (err) {
        logger.log(`Error saving stats: ${err.message}`, 'error_saving_stats', ENV.bs_server_id);
    }
}

function loadStats() {
    try {
        const data = fs.readFileSync(path.join(__dirname, 'data', 'countingStats.json'), 'utf8');
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

        positiveCounts.clear();
        for (const [key, value] of stats.positiveCounts || []) {
            positiveCounts.set(key, value);
        }

        negativeCounts.clear();
        for (const [key, value] of stats.negativeCounts || []) {
            negativeCounts.set(key, value);
        }
    } catch (err) {
        logger.log('No stats file found, starting fresh.', 'no_stats_file', ENV.bs_server_id);
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
    dailyCounts, 
    positiveCounts,
    negativeCounts
};