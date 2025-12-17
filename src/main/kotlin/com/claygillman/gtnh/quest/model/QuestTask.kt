package com.claygillman.gtnh.quest.model

/**
 * Represents a task within a quest.
 */
data class QuestTask(
    val index: Int,
    val taskType: String,
    val consume: Boolean,
    val ignoreNbt: Boolean,
    val partialMatch: Boolean,
    val requiredItems: List<ItemStack>,
    val requiredEntities: List<EntityTarget>
)

/**
 * Represents a reward for completing a quest.
 */
data class QuestReward(
    val index: Int,
    val rewardType: String,
    val items: List<ItemStack>
)
