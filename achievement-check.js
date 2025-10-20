const fs = require('fs');
const path = require('path');
const counting = require('./counting.js');
const logger = require('./logger.js');
const definitions = require('./data/achievementDefinitions.json');
const ENV = require('./data/config.json');

const definitionsPath = path.join(__dirname, 'data', 'achievementDefinitions.json');
const userAchievementsPath = path.join(__dirname, 'data', 'userAchievements.json');

// Load achievement definitions
function loadDefinitions() {
	const defs = JSON.parse(fs.readFileSync(definitionsPath, 'utf8'));
	//console.log('[ACHIEVEMENT] Loaded definitions:', defs);
	return defs;
}

// Load user achievements (per userId)
function loadUserAchievements() {
	if (!fs.existsSync(userAchievementsPath)) {
		console.log('[ACHIEVEMENT] No userAchievements file found, returning empty object.');
		return {};
	}
	const data = JSON.parse(fs.readFileSync(userAchievementsPath, 'utf8'));
	//console.log('[ACHIEVEMENT] Loaded userAchievements:', data);
	return data;
}

// Save user achievements
function saveUserAchievements(data) {
	fs.writeFileSync(userAchievementsPath, JSON.stringify(data, null, 2), 'utf8');
	//console.log('[ACHIEVEMENT] Saved userAchievements:', data);
}



// Call this after awarding an achievement
function notifyAchievement(userId, achievementId) {
    const achievement = definitions[achievementId];
    if (!achievement) return;
    const message = `<@${userId}> earned the achievement: **${achievement.name}**!\n${achievement.description}`;
    logger.log(message, 'achievement', ENV.bs_server_id);
}

// Award an achievement to a user if not already earned
function awardAchievement(userId, achievementId) {
	const definitions = loadDefinitions();
	if (!definitions[achievementId]) {
		console.log(`[ACHIEVEMENT] Tried to award invalid achievement: ${achievementId} to user ${userId}`);
		return false;
	}
	const userAchievements = loadUserAchievements();
	if (!userAchievements[userId]) userAchievements[userId] = [];
	if (userAchievements[userId].includes(achievementId)) {
		//console.log(`[ACHIEVEMENT] User ${userId} already has achievement: ${achievementId}`);
		return false;
	}
	userAchievements[userId].push(achievementId);
	console.log(`[ACHIEVEMENT] Awarded achievement ${achievementId} to user ${userId}`);
	saveUserAchievements(userAchievements);
    notifyAchievement(userId, achievementId);
	return true;
}

// Check and award count milestone achievements
function checkCountMilestones(userId, totalCount) {
	const milestones = {
		1: 'count_1',
		10: 'count_10',
		100: 'count_100',
		1000: 'count_1000',
		10000: 'count_10000',
	};
	let awarded = false;
	for (const [min, id] of Object.entries(milestones)) {
		if (totalCount >= Number(min)) {
			//console.log(`[ACHIEVEMENT] Checking count milestone for user ${userId}: totalCount=${totalCount} (milestone: ${min})`);
			awarded = awardAchievement(userId, id) || awarded;
		}
	}
	return awarded;
}



// Check and award positive count achievements
function checkPositiveCountMilestones(userId, positiveCount) {
	const milestones = {
		1: 'increasing_count_1',
		10: 'increasing_count_10',
		100: 'increasing_count_100',
		1000: 'increasing_count_1000',
	};
	let awarded = false;
	for (const [min, id] of Object.entries(milestones)) {
		if (positiveCount >= Number(min)) {
			//console.log(`[ACHIEVEMENT] Checking positive count milestone for user ${userId}: positiveCount=${positiveCount} (milestone: ${min})`);
			awarded = awardAchievement(userId, id) || awarded;
		}
	}
	return awarded;
}

// Check and award negative count achievements
function checkNegativeCountMilestones(userId, negativeCount) {
	const milestones = {
		1: 'decreasing_count_1',
		10: 'decreasing_count_10',
		100: 'decreasing_count_100',
        1000: 'decreasing_count_1000',
	};
	let awarded = false;
	for (const [min, id] of Object.entries(milestones)) {
		if (negativeCount >= Number(min)) {
			//console.log(`[ACHIEVEMENT] Checking negative count milestone for user ${userId}: negativeCount=${negativeCount} (milestone: ${min})`);
			awarded = awardAchievement(userId, id) || awarded;
		}
	}
	return awarded;
}

// Check and award streak achievements
function checkStreakMilestones(userId, streak) {
	const streaks = {
		10: 'streak_10',
		50: 'streak_50',
		100: 'streak_100',
		500: 'streak_500',
		1000: 'streak_1000',
	};
	let awarded = false;
	for (const [min, id] of Object.entries(streaks)) {
		if (streak >= Number(min)) {
			//console.log(`[ACHIEVEMENT] Checking streak milestone for user ${userId}: streak=${streak} (milestone: ${min})`);
			awarded = awardAchievement(userId, id) || awarded;
		}
	}
	return awarded;
}


// Main function: pass in Discord message object and counting data
function checkAndAwardAchievements(d, fame, streak, positiveCounts, negativeCounts) {
	if (!d || !d.author || !d.author.id) return;
	const userId = d.author.id;
	const totalCount = fame.has(userId) ? fame.get(userId) : 0;
	const currentStreak = streak.has(userId) ? streak.get(userId) : 0;
	const positiveCount = positiveCounts.has(userId) ? positiveCounts.get(userId) : 0;
	const negativeCount = negativeCounts.has(userId) ? negativeCounts.get(userId) : 0;
	console.log(`[ACHIEVEMENT] Checking all achievements for user ${userId}: totalCount=${totalCount}, currentStreak=${currentStreak}, positiveCount=${positiveCount}, negativeCount=${negativeCount}`);
	checkCountMilestones(userId, totalCount);
	checkStreakMilestones(userId, currentStreak);
	checkPositiveCountMilestones(userId, positiveCount);
	checkNegativeCountMilestones(userId, negativeCount);
}



module.exports = {
	awardAchievement,
	checkCountMilestones,
	checkStreakMilestones,
	loadUserAchievements,
	loadDefinitions,
	checkAndAwardAchievements
};
