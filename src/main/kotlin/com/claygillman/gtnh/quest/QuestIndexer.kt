package com.claygillman.gtnh.quest

import com.claygillman.gtnh.db.Database
import com.claygillman.gtnh.quest.model.*
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.sql.Connection
import java.sql.PreparedStatement

/**
 * Indexes quest data from a GTNH modpack installation into SQLite.
 */
class QuestIndexer(
    private val dbPath: String,
    private val questsBasePath: String,
    private val force: Boolean
) {
    private val questsDir = File(questsBasePath)

    // Lookup maps built during indexing
    private val questLineDbIds = mutableMapOf<String, Long>() // base64Id -> db id
    private val questDbIds = mutableMapOf<Pair<Long, Long>, Long>() // (high, low) -> db id
    private val questBase64ToDbId = mutableMapOf<String, Long>() // base64Id -> db id

    /**
     * Runs the indexing process with progress callbacks.
     * @param onProgress Called with (phase, current, total) during indexing
     */
    fun index(onProgress: (String, Int, Int) -> Unit): IndexResult {
        validatePaths()

        return Database.withConnection(dbPath) { conn ->
            conn.autoCommit = false
            try {
                Database.initializeSchema(conn)
                Database.initializeQuestSchema(conn)

                if (force) {
                    onProgress("Clearing existing data", 0, 1)
                    Database.clearQuestTables(conn)
                }

                // Phase 1: Parse and index quest lines
                val questLines = parseQuestLinesOrder()
                onProgress("Indexing quest lines", 0, questLines.size)
                indexQuestLines(conn, questLines, onProgress)

                // Phase 2: Index all quests (recursively scan subdirectories)
                val questFiles = collectQuestFiles(File(questsDir, "Quests"))
                onProgress("Indexing quests", 0, questFiles.size)
                val questCount = indexQuests(conn, questFiles.toTypedArray(), onProgress)

                // Phase 3: Link quest line entries (position data)
                onProgress("Linking quest positions", 0, questLines.size)
                linkQuestLineEntries(conn, questLines, onProgress)

                // Phase 4: Link prerequisites
                onProgress("Linking prerequisites", 0, questFiles.size)
                linkPrerequisites(conn, questFiles.toTypedArray(), onProgress)

                conn.commit()

                IndexResult(
                    questLineCount = questLines.size,
                    questCount = questCount,
                    success = true,
                    errors = emptyList()
                )
            } catch (e: Exception) {
                conn.rollback()
                throw e
            }
        }
    }

    private fun validatePaths() {
        require(questsDir.exists()) { "Quest directory not found: $questsBasePath" }
        require(File(questsDir, "QuestLinesOrder.txt").exists()) { "QuestLinesOrder.txt not found" }
        require(File(questsDir, "QuestLines").isDirectory) { "QuestLines directory not found" }
        require(File(questsDir, "Quests").isDirectory) { "Quests directory not found" }
    }

    /**
     * Recursively collects all quest JSON files from subdirectories.
     */
    private fun collectQuestFiles(dir: File): List<File> {
        val result = mutableListOf<File>()
        dir.listFiles()?.forEach { file ->
            when {
                file.isDirectory -> result.addAll(collectQuestFiles(file))
                file.extension == "json" -> result.add(file)
            }
        }
        return result
    }

    /**
     * Parses QuestLinesOrder.txt to get the ordered list of quest lines.
     * Format: [Base64ID]: [Display Name]
     */
    private fun parseQuestLinesOrder(): List<QuestLineOrderEntry> {
        val orderFile = File(questsDir, "QuestLinesOrder.txt")
        val entries = mutableListOf<QuestLineOrderEntry>()

        orderFile.readLines().forEachIndexed { index, line ->
            if (line.isBlank()) return@forEachIndexed

            // Format: "AAAAAAAAAAAAAAAAAAAAAA==: And So, It Begins"
            val colonIndex = line.indexOf(':')

            if (colonIndex > 0) {
                val base64Id = line.substring(0, colonIndex).trim()
                val displayName = line.substring(colonIndex + 1).trim()
                entries.add(QuestLineOrderEntry(index + 1, base64Id, displayName))
            }
        }

        return entries
    }

    private fun indexQuestLines(
        conn: Connection,
        questLines: List<QuestLineOrderEntry>,
        onProgress: (String, Int, Int) -> Unit
    ) {
        val insertStmt = conn.prepareStatement("""
            INSERT INTO quest_lines (base64_id, display_order, name, description, icon_item_id, icon_damage, visibility)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """, PreparedStatement.RETURN_GENERATED_KEYS)

        val questLinesDir = File(questsDir, "QuestLines")

        questLines.forEachIndexed { index, entry ->
            onProgress("Indexing quest lines", index + 1, questLines.size)

            // Find the quest line directory matching this base64 ID
            val questLineDir = questLinesDir.listFiles()?.find { dir ->
                dir.isDirectory && dir.name.endsWith("-${entry.base64Id}")
            }

            val questLine = if (questLineDir != null) {
                parseQuestLineMetadata(questLineDir, entry)
            } else {
                // Fallback if directory not found
                QuestLine(
                    base64Id = entry.base64Id,
                    displayOrder = entry.order,
                    name = entry.displayName,
                    description = null,
                    iconItemId = null,
                    iconDamage = 0,
                    visibility = "NORMAL"
                )
            }

            insertStmt.setString(1, questLine.base64Id)
            insertStmt.setInt(2, questLine.displayOrder)
            insertStmt.setString(3, questLine.name)
            insertStmt.setString(4, questLine.description)
            insertStmt.setString(5, questLine.iconItemId)
            insertStmt.setInt(6, questLine.iconDamage)
            insertStmt.setString(7, questLine.visibility)
            insertStmt.executeUpdate()

            val keys = insertStmt.generatedKeys
            if (keys.next()) {
                questLineDbIds[questLine.base64Id] = keys.getLong(1)
            }
        }

        insertStmt.close()
    }

    private fun parseQuestLineMetadata(dir: File, orderEntry: QuestLineOrderEntry): QuestLine {
        val metaFile = File(dir, "QuestLine.json")
        if (!metaFile.exists()) {
            return QuestLine(
                base64Id = orderEntry.base64Id,
                displayOrder = orderEntry.order,
                name = orderEntry.displayName,
                description = null,
                iconItemId = null,
                iconDamage = 0,
                visibility = "NORMAL"
            )
        }

        val bq = BetterQuestingJson.parseFile(metaFile)
        val props = bq.getCompound("properties")?.getCompound("betterquesting")

        return QuestLine(
            base64Id = orderEntry.base64Id,
            displayOrder = orderEntry.order,
            name = props?.getString("name") ?: orderEntry.displayName,
            description = props?.getString("desc"),
            iconItemId = props?.getCompound("icon")?.getString("id"),
            iconDamage = props?.getCompound("icon")?.getInt("Damage") ?: 0,
            visibility = props?.getString("visibility") ?: "NORMAL"
        )
    }

    private fun indexQuests(
        conn: Connection,
        questFiles: Array<File>,
        onProgress: (String, Int, Int) -> Unit
    ): Int {
        val insertQuestStmt = conn.prepareStatement("""
            INSERT INTO quests (base64_id, quest_id_high, quest_id_low, name, description,
                icon_item_id, icon_damage, is_main, quest_logic, task_logic, visibility, repeat_time)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, PreparedStatement.RETURN_GENERATED_KEYS)

        val insertTaskStmt = conn.prepareStatement("""
            INSERT INTO quest_tasks (quest_id, task_index, task_type, consume, ignore_nbt, partial_match)
            VALUES (?, ?, ?, ?, ?, ?)
        """, PreparedStatement.RETURN_GENERATED_KEYS)

        val insertTaskItemStmt = conn.prepareStatement("""
            INSERT INTO task_items (task_id, item_index, item_id, count, damage, ore_dict, nbt)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """)

        val insertTaskEntityStmt = conn.prepareStatement("""
            INSERT INTO task_entities (task_id, entity_index, entity_id, count, ignore_nbt, subtypes)
            VALUES (?, ?, ?, ?, ?, ?)
        """)

        val insertRewardStmt = conn.prepareStatement("""
            INSERT INTO quest_rewards (quest_id, reward_index, reward_type)
            VALUES (?, ?, ?)
        """, PreparedStatement.RETURN_GENERATED_KEYS)

        val insertRewardItemStmt = conn.prepareStatement("""
            INSERT INTO reward_items (reward_id, item_index, item_id, count, damage, ore_dict, nbt)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """)

        var count = 0
        questFiles.forEachIndexed { index, file ->
            if ((index + 1) % 100 == 0 || index == questFiles.size - 1) {
                onProgress("Indexing quests", index + 1, questFiles.size)
            }

            try {
                val quest = parseQuestFile(file)
                if (quest != null) {
                    insertQuest(insertQuestStmt, quest)
                    val questDbId = getGeneratedId(insertQuestStmt)

                    questDbIds[quest.questIdHigh to quest.questIdLow] = questDbId
                    questBase64ToDbId[quest.base64Id] = questDbId

                    // Insert tasks
                    quest.tasks.forEach { task ->
                        insertTask(insertTaskStmt, questDbId, task)
                        val taskDbId = getGeneratedId(insertTaskStmt)

                        task.requiredItems.forEachIndexed { itemIdx, item ->
                            insertTaskItem(insertTaskItemStmt, taskDbId, itemIdx, item)
                        }

                        task.requiredEntities.forEachIndexed { entityIdx, entity ->
                            insertTaskEntity(insertTaskEntityStmt, taskDbId, entityIdx, entity)
                        }
                    }

                    // Insert rewards
                    quest.rewards.forEach { reward ->
                        insertReward(insertRewardStmt, questDbId, reward)
                        val rewardDbId = getGeneratedId(insertRewardStmt)

                        reward.items.forEachIndexed { itemIdx, item ->
                            insertRewardItem(insertRewardItemStmt, rewardDbId, itemIdx, item)
                        }
                    }

                    count++
                }
            } catch (e: Exception) {
                System.err.println("Error parsing quest file ${file.name}: ${e.message}")
            }
        }

        insertQuestStmt.close()
        insertTaskStmt.close()
        insertTaskItemStmt.close()
        insertTaskEntityStmt.close()
        insertRewardStmt.close()
        insertRewardItemStmt.close()

        return count
    }

    private fun parseQuestFile(file: File): Quest? {
        // Extract base64 ID from filename: "QuestName-BASE64ID.json"
        val base64Id = file.nameWithoutExtension.substringAfterLast('-')

        val bq = BetterQuestingJson.parseFile(file)

        val questIdHigh = bq.getLong("questIDHigh") ?: return null
        val questIdLow = bq.getLong("questIDLow") ?: return null

        val props = bq.getCompound("properties")?.getCompound("betterquesting")
        val name = props?.getString("name") ?: return null

        // Parse prerequisites
        val prerequisites = mutableListOf<QuestPrerequisite>()
        bq.getList("preRequisites")?.values?.forEach { prereqValue ->
            if (prereqValue is BQValue.BQCompound) {
                val high = prereqValue.getLong("questIDHigh")
                val low = prereqValue.getLong("questIDLow")
                if (high != null && low != null) {
                    prerequisites.add(QuestPrerequisite(high, low))
                }
            }
        }

        // Parse tasks
        val tasks = mutableListOf<QuestTask>()
        bq.getList("tasks")?.values?.forEachIndexed { idx, taskValue ->
            if (taskValue is BQValue.BQCompound) {
                tasks.add(parseTask(taskValue, idx))
            }
        }

        // Parse rewards
        val rewards = mutableListOf<QuestReward>()
        bq.getList("rewards")?.values?.forEachIndexed { idx, rewardValue ->
            if (rewardValue is BQValue.BQCompound) {
                rewards.add(parseReward(rewardValue, idx))
            }
        }

        return Quest(
            base64Id = base64Id,
            questIdHigh = questIdHigh,
            questIdLow = questIdLow,
            name = name,
            description = props?.getString("desc"),
            iconItemId = props?.getCompound("icon")?.getString("id"),
            iconDamage = props?.getCompound("icon")?.getInt("Damage") ?: 0,
            isMain = props?.getBoolean("isMain") ?: false,
            questLogic = props?.getString("questLogic") ?: "AND",
            taskLogic = props?.getString("taskLogic") ?: "AND",
            visibility = props?.getString("visibility") ?: "NORMAL",
            repeatTime = props?.getInt("repeatTime") ?: -1,
            prerequisites = prerequisites,
            tasks = tasks,
            rewards = rewards
        )
    }

    private fun parseTask(bq: BQValue.BQCompound, index: Int): QuestTask {
        val taskType = bq.getString("taskID") ?: "unknown"

        val requiredItems = mutableListOf<ItemStack>()
        bq.getList("requiredItems")?.values?.forEach { itemValue ->
            if (itemValue is BQValue.BQCompound) {
                requiredItems.add(parseItemStack(itemValue))
            }
        }

        val requiredEntities = mutableListOf<EntityTarget>()
        bq.getList("required")?.values?.forEach { entityValue ->
            if (entityValue is BQValue.BQCompound) {
                requiredEntities.add(parseEntityTarget(entityValue))
            }
        }

        return QuestTask(
            index = bq.getInt("index") ?: index,
            taskType = taskType,
            consume = bq.getBoolean("consume") ?: false,
            ignoreNbt = bq.getBoolean("ignoreNBT") ?: false,
            partialMatch = bq.getBoolean("partialMatch") ?: false,
            requiredItems = requiredItems,
            requiredEntities = requiredEntities
        )
    }

    private fun parseReward(bq: BQValue.BQCompound, index: Int): QuestReward {
        val rewardType = bq.getString("rewardID") ?: "unknown"

        val items = mutableListOf<ItemStack>()
        bq.getList("rewards")?.values?.forEach { itemValue ->
            if (itemValue is BQValue.BQCompound) {
                items.add(parseItemStack(itemValue))
            }
        }

        return QuestReward(
            index = bq.getInt("index") ?: index,
            rewardType = rewardType,
            items = items
        )
    }

    private fun parseItemStack(bq: BQValue.BQCompound): ItemStack {
        return ItemStack(
            itemId = bq.getString("id") ?: "unknown",
            count = bq.getInt("Count") ?: 1,
            damage = bq.getInt("Damage") ?: 0,
            oreDict = bq.getString("OreDict")?.takeIf { it.isNotEmpty() },
            nbt = bq.getCompound("tag")?.let { serializeNbt(it) }
        )
    }

    private fun parseEntityTarget(bq: BQValue.BQCompound): EntityTarget {
        return EntityTarget(
            entityId = bq.getString("id") ?: "unknown",
            count = bq.getInt("required") ?: 1,
            ignoreNbt = bq.getBoolean("ignoreNBT") ?: false,
            subtypes = bq.getBoolean("subtypes") ?: true
        )
    }

    private fun serializeNbt(compound: BQValue.BQCompound): String {
        // Simple JSON-like serialization for NBT data
        val entries = compound.values.entries.map { (k, v) ->
            "\"$k\":${serializeValue(v)}"
        }
        return "{${entries.joinToString(",")}}"
    }

    private fun serializeValue(value: BQValue): String {
        return when (value) {
            is BQValue.BQString -> "\"${value.value.replace("\"", "\\\"")}\""
            is BQValue.BQInt -> value.value.toString()
            is BQValue.BQLong -> value.value.toString()
            is BQValue.BQFloat -> value.value.toString()
            is BQValue.BQDouble -> value.value.toString()
            is BQValue.BQBoolean -> value.value.toString()
            is BQValue.BQCompound -> serializeNbt(value)
            is BQValue.BQList -> "[${value.values.joinToString(",") { serializeValue(it) }}]"
            is BQValue.BQNull -> "null"
        }
    }

    private fun insertQuest(stmt: PreparedStatement, quest: Quest) {
        stmt.setString(1, quest.base64Id)
        stmt.setLong(2, quest.questIdHigh)
        stmt.setLong(3, quest.questIdLow)
        stmt.setString(4, quest.name)
        stmt.setString(5, quest.description)
        stmt.setString(6, quest.iconItemId)
        stmt.setInt(7, quest.iconDamage)
        stmt.setInt(8, if (quest.isMain) 1 else 0)
        stmt.setString(9, quest.questLogic)
        stmt.setString(10, quest.taskLogic)
        stmt.setString(11, quest.visibility)
        stmt.setInt(12, quest.repeatTime)
        stmt.executeUpdate()
    }

    private fun insertTask(stmt: PreparedStatement, questDbId: Long, task: QuestTask) {
        stmt.setLong(1, questDbId)
        stmt.setInt(2, task.index)
        stmt.setString(3, task.taskType)
        stmt.setInt(4, if (task.consume) 1 else 0)
        stmt.setInt(5, if (task.ignoreNbt) 1 else 0)
        stmt.setInt(6, if (task.partialMatch) 1 else 0)
        stmt.executeUpdate()
    }

    private fun insertTaskItem(stmt: PreparedStatement, taskDbId: Long, index: Int, item: ItemStack) {
        stmt.setLong(1, taskDbId)
        stmt.setInt(2, index)
        stmt.setString(3, item.itemId)
        stmt.setInt(4, item.count)
        stmt.setInt(5, item.damage)
        stmt.setString(6, item.oreDict)
        stmt.setString(7, item.nbt)
        stmt.executeUpdate()
    }

    private fun insertTaskEntity(stmt: PreparedStatement, taskDbId: Long, index: Int, entity: EntityTarget) {
        stmt.setLong(1, taskDbId)
        stmt.setInt(2, index)
        stmt.setString(3, entity.entityId)
        stmt.setInt(4, entity.count)
        stmt.setInt(5, if (entity.ignoreNbt) 1 else 0)
        stmt.setInt(6, if (entity.subtypes) 1 else 0)
        stmt.executeUpdate()
    }

    private fun insertReward(stmt: PreparedStatement, questDbId: Long, reward: QuestReward) {
        stmt.setLong(1, questDbId)
        stmt.setInt(2, reward.index)
        stmt.setString(3, reward.rewardType)
        stmt.executeUpdate()
    }

    private fun insertRewardItem(stmt: PreparedStatement, rewardDbId: Long, index: Int, item: ItemStack) {
        stmt.setLong(1, rewardDbId)
        stmt.setInt(2, index)
        stmt.setString(3, item.itemId)
        stmt.setInt(4, item.count)
        stmt.setInt(5, item.damage)
        stmt.setString(6, item.oreDict)
        stmt.setString(7, item.nbt)
        stmt.executeUpdate()
    }

    private fun getGeneratedId(stmt: PreparedStatement): Long {
        val keys = stmt.generatedKeys
        return if (keys.next()) keys.getLong(1) else -1
    }

    private fun linkQuestLineEntries(
        conn: Connection,
        questLines: List<QuestLineOrderEntry>,
        onProgress: (String, Int, Int) -> Unit
    ) {
        val insertStmt = conn.prepareStatement("""
            INSERT OR IGNORE INTO quest_line_entries (quest_line_id, quest_id, x_position, y_position, size_x, size_y)
            VALUES (?, ?, ?, ?, ?, ?)
        """)

        val questLinesDir = File(questsDir, "QuestLines")

        questLines.forEachIndexed { index, entry ->
            onProgress("Linking quest positions", index + 1, questLines.size)

            val questLineDbId = questLineDbIds[entry.base64Id] ?: return@forEachIndexed

            val questLineDir = questLinesDir.listFiles()?.find { dir ->
                dir.isDirectory && dir.name.endsWith("-${entry.base64Id}")
            } ?: return@forEachIndexed

            // Parse all quest entry files (excluding QuestLine.json)
            questLineDir.listFiles { f -> f.extension == "json" && f.name != "QuestLine.json" }?.forEach { entryFile ->
                try {
                    val bq = BetterQuestingJson.parseFile(entryFile)
                    val questIdHigh = bq.getLong("questIDHigh") ?: return@forEach
                    val questIdLow = bq.getLong("questIDLow") ?: return@forEach
                    val questDbId = questDbIds[questIdHigh to questIdLow] ?: return@forEach

                    insertStmt.setLong(1, questLineDbId)
                    insertStmt.setLong(2, questDbId)
                    insertStmt.setInt(3, bq.getInt("x") ?: 0)
                    insertStmt.setInt(4, bq.getInt("y") ?: 0)
                    insertStmt.setInt(5, bq.getInt("sizeX") ?: 24)
                    insertStmt.setInt(6, bq.getInt("sizeY") ?: 24)
                    insertStmt.executeUpdate()
                } catch (e: Exception) {
                    // Skip invalid entry files
                }
            }
        }

        insertStmt.close()
    }

    private fun linkPrerequisites(
        conn: Connection,
        questFiles: Array<File>,
        onProgress: (String, Int, Int) -> Unit
    ) {
        val insertStmt = conn.prepareStatement("""
            INSERT OR IGNORE INTO quest_prerequisites (quest_id, prerequisite_quest_id)
            VALUES (?, ?)
        """)

        questFiles.forEachIndexed { index, file ->
            if ((index + 1) % 500 == 0 || index == questFiles.size - 1) {
                onProgress("Linking prerequisites", index + 1, questFiles.size)
            }

            try {
                val bq = BetterQuestingJson.parseFile(file)
                val questIdHigh = bq.getLong("questIDHigh") ?: return@forEachIndexed
                val questIdLow = bq.getLong("questIDLow") ?: return@forEachIndexed
                val questDbId = questDbIds[questIdHigh to questIdLow] ?: return@forEachIndexed

                bq.getList("preRequisites")?.values?.forEach { prereqValue ->
                    if (prereqValue is BQValue.BQCompound) {
                        val prereqHigh = prereqValue.getLong("questIDHigh") ?: return@forEach
                        val prereqLow = prereqValue.getLong("questIDLow") ?: return@forEach
                        val prereqDbId = questDbIds[prereqHigh to prereqLow] ?: return@forEach

                        insertStmt.setLong(1, questDbId)
                        insertStmt.setLong(2, prereqDbId)
                        insertStmt.executeUpdate()
                    }
                }
            } catch (e: Exception) {
                // Skip files with errors
            }
        }

        insertStmt.close()
    }
}

private data class QuestLineOrderEntry(
    val order: Int,
    val base64Id: String,
    val displayName: String
)

data class IndexResult(
    val questLineCount: Int,
    val questCount: Int,
    val success: Boolean,
    val errors: List<String>
)
