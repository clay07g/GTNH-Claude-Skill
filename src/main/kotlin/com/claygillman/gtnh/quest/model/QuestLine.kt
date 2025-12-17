package com.claygillman.gtnh.quest.model

/**
 * Represents a quest line (category) in the quest book.
 */
data class QuestLine(
    val base64Id: String,
    val displayOrder: Int,
    val name: String,
    val description: String?,
    val iconItemId: String?,
    val iconDamage: Int,
    val visibility: String
)

/**
 * Represents a quest's position within a quest line.
 */
data class QuestLineEntry(
    val questLineBase64Id: String,
    val questIdHigh: Long,
    val questIdLow: Long,
    val xPosition: Int,
    val yPosition: Int,
    val sizeX: Int,
    val sizeY: Int
)
