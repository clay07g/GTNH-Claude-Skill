package com.claygillman.gtnh.quest.model

/**
 * Represents a quest parsed from a quest JSON file.
 */
data class Quest(
    val base64Id: String,
    val questIdHigh: Long,
    val questIdLow: Long,
    val name: String,
    val description: String?,
    val iconItemId: String?,
    val iconDamage: Int,
    val isMain: Boolean,
    val questLogic: String,
    val taskLogic: String,
    val visibility: String,
    val repeatTime: Int,
    val prerequisites: List<QuestPrerequisite>,
    val tasks: List<QuestTask>,
    val rewards: List<QuestReward>
)

/**
 * A prerequisite quest reference.
 */
data class QuestPrerequisite(
    val questIdHigh: Long,
    val questIdLow: Long
)

/**
 * An item reference used in tasks and rewards.
 */
data class ItemStack(
    val itemId: String,
    val count: Int,
    val damage: Int,
    val oreDict: String?,
    val nbt: String?
)

/**
 * An entity reference used in hunt tasks.
 */
data class EntityTarget(
    val entityId: String,
    val count: Int,
    val ignoreNbt: Boolean,
    val subtypes: Boolean
)
