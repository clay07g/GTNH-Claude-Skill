package com.claygillman.gtnh.db

import java.io.File
import java.sql.Connection
import java.sql.DriverManager

object Database {

    init {
        // Load SQLite JDBC driver
        Class.forName("org.sqlite.JDBC")
    }

    /**
     * Opens a connection to the SQLite database at the given path.
     * Creates the parent directory if it doesn't exist.
     */
    fun connect(dbPath: String): Connection {
        val file = File(dbPath)
        file.parentFile?.mkdirs()
        return DriverManager.getConnection("jdbc:sqlite:$dbPath")
    }

    /**
     * Executes a block with a database connection, ensuring the connection is closed afterward.
     */
    fun <T> withConnection(dbPath: String, block: (Connection) -> T): T {
        val conn = connect(dbPath)
        return try {
            block(conn)
        } finally {
            conn.close()
        }
    }

    /**
     * Initializes the database schema if tables don't exist.
     */
    fun initializeSchema(conn: Connection) {
        conn.createStatement().use { stmt ->
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS schema_version (
                    version INTEGER PRIMARY KEY,
                    applied_at TEXT DEFAULT CURRENT_TIMESTAMP
                )
            """.trimIndent())
        }
    }

    /**
     * Initializes the quest-related database schema.
     */
    fun initializeQuestSchema(conn: Connection) {
        conn.createStatement().use { stmt ->
            // Quest lines (46 total in GTNH)
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS quest_lines (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    base64_id TEXT NOT NULL UNIQUE,
                    display_order INTEGER NOT NULL,
                    name TEXT NOT NULL,
                    description TEXT,
                    icon_item_id TEXT,
                    icon_damage INTEGER DEFAULT 0,
                    visibility TEXT DEFAULT 'NORMAL'
                )
            """.trimIndent())

            // Quests (3,739 total in GTNH)
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS quests (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    base64_id TEXT NOT NULL UNIQUE,
                    quest_id_high INTEGER NOT NULL,
                    quest_id_low INTEGER NOT NULL,
                    name TEXT NOT NULL,
                    description TEXT,
                    icon_item_id TEXT,
                    icon_damage INTEGER DEFAULT 0,
                    is_main INTEGER DEFAULT 0,
                    quest_logic TEXT DEFAULT 'AND',
                    task_logic TEXT DEFAULT 'AND',
                    visibility TEXT DEFAULT 'NORMAL',
                    repeat_time INTEGER DEFAULT -1,
                    UNIQUE(quest_id_high, quest_id_low)
                )
            """.trimIndent())

            // Quest placement within quest lines (position data for UI)
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS quest_line_entries (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    quest_line_id INTEGER NOT NULL,
                    quest_id INTEGER NOT NULL,
                    x_position INTEGER NOT NULL,
                    y_position INTEGER NOT NULL,
                    size_x INTEGER DEFAULT 24,
                    size_y INTEGER DEFAULT 24,
                    FOREIGN KEY (quest_line_id) REFERENCES quest_lines(id) ON DELETE CASCADE,
                    FOREIGN KEY (quest_id) REFERENCES quests(id) ON DELETE CASCADE,
                    UNIQUE(quest_line_id, quest_id)
                )
            """.trimIndent())

            // Quest prerequisites (dependency graph)
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS quest_prerequisites (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    quest_id INTEGER NOT NULL,
                    prerequisite_quest_id INTEGER NOT NULL,
                    FOREIGN KEY (quest_id) REFERENCES quests(id) ON DELETE CASCADE,
                    FOREIGN KEY (prerequisite_quest_id) REFERENCES quests(id) ON DELETE CASCADE,
                    UNIQUE(quest_id, prerequisite_quest_id)
                )
            """.trimIndent())

            // Tasks within quests
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS quest_tasks (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    quest_id INTEGER NOT NULL,
                    task_index INTEGER NOT NULL,
                    task_type TEXT NOT NULL,
                    consume INTEGER DEFAULT 0,
                    ignore_nbt INTEGER DEFAULT 0,
                    partial_match INTEGER DEFAULT 0,
                    FOREIGN KEY (quest_id) REFERENCES quests(id) ON DELETE CASCADE
                )
            """.trimIndent())

            // Items required for tasks
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS task_items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    task_id INTEGER NOT NULL,
                    item_index INTEGER NOT NULL,
                    item_id TEXT NOT NULL,
                    count INTEGER DEFAULT 1,
                    damage INTEGER DEFAULT 0,
                    ore_dict TEXT,
                    nbt TEXT,
                    FOREIGN KEY (task_id) REFERENCES quest_tasks(id) ON DELETE CASCADE
                )
            """.trimIndent())

            // Entities for hunt tasks
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS task_entities (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    task_id INTEGER NOT NULL,
                    entity_index INTEGER NOT NULL,
                    entity_id TEXT NOT NULL,
                    count INTEGER DEFAULT 1,
                    ignore_nbt INTEGER DEFAULT 0,
                    subtypes INTEGER DEFAULT 1,
                    FOREIGN KEY (task_id) REFERENCES quest_tasks(id) ON DELETE CASCADE
                )
            """.trimIndent())

            // Rewards
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS quest_rewards (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    quest_id INTEGER NOT NULL,
                    reward_index INTEGER NOT NULL,
                    reward_type TEXT NOT NULL,
                    FOREIGN KEY (quest_id) REFERENCES quests(id) ON DELETE CASCADE
                )
            """.trimIndent())

            // Reward items
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS reward_items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    reward_id INTEGER NOT NULL,
                    item_index INTEGER NOT NULL,
                    item_id TEXT NOT NULL,
                    count INTEGER DEFAULT 1,
                    damage INTEGER DEFAULT 0,
                    ore_dict TEXT,
                    nbt TEXT,
                    FOREIGN KEY (reward_id) REFERENCES quest_rewards(id) ON DELETE CASCADE
                )
            """.trimIndent())

            // Full-text search for quests
            stmt.executeUpdate("""
                CREATE VIRTUAL TABLE IF NOT EXISTS quests_fts USING fts5(
                    name,
                    description,
                    content='quests',
                    content_rowid='id'
                )
            """.trimIndent())

            // Full-text search for quest lines
            stmt.executeUpdate("""
                CREATE VIRTUAL TABLE IF NOT EXISTS quest_lines_fts USING fts5(
                    name,
                    description,
                    content='quest_lines',
                    content_rowid='id'
                )
            """.trimIndent())

            // Indexes for common queries
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_quests_name ON quests(name)")
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_quests_is_main ON quests(is_main)")
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_quest_line_entries_quest ON quest_line_entries(quest_id)")
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_quest_line_entries_line ON quest_line_entries(quest_line_id)")
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_quest_prerequisites_quest ON quest_prerequisites(quest_id)")
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_quest_prerequisites_prereq ON quest_prerequisites(prerequisite_quest_id)")
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_quest_tasks_quest ON quest_tasks(quest_id)")
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_quest_tasks_type ON quest_tasks(task_type)")
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_task_items_task ON task_items(task_id)")
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_task_items_item ON task_items(item_id)")
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_reward_items_item ON reward_items(item_id)")
        }

        // Create triggers to keep FTS in sync
        conn.createStatement().use { stmt ->
            stmt.executeUpdate("""
                CREATE TRIGGER IF NOT EXISTS quests_fts_insert AFTER INSERT ON quests BEGIN
                    INSERT INTO quests_fts(rowid, name, description) VALUES (NEW.id, NEW.name, NEW.description);
                END
            """.trimIndent())

            stmt.executeUpdate("""
                CREATE TRIGGER IF NOT EXISTS quests_fts_delete AFTER DELETE ON quests BEGIN
                    INSERT INTO quests_fts(quests_fts, rowid, name, description) VALUES('delete', OLD.id, OLD.name, OLD.description);
                END
            """.trimIndent())

            stmt.executeUpdate("""
                CREATE TRIGGER IF NOT EXISTS quests_fts_update AFTER UPDATE ON quests BEGIN
                    INSERT INTO quests_fts(quests_fts, rowid, name, description) VALUES('delete', OLD.id, OLD.name, OLD.description);
                    INSERT INTO quests_fts(rowid, name, description) VALUES (NEW.id, NEW.name, NEW.description);
                END
            """.trimIndent())

            stmt.executeUpdate("""
                CREATE TRIGGER IF NOT EXISTS quest_lines_fts_insert AFTER INSERT ON quest_lines BEGIN
                    INSERT INTO quest_lines_fts(rowid, name, description) VALUES (NEW.id, NEW.name, NEW.description);
                END
            """.trimIndent())

            stmt.executeUpdate("""
                CREATE TRIGGER IF NOT EXISTS quest_lines_fts_delete AFTER DELETE ON quest_lines BEGIN
                    INSERT INTO quest_lines_fts(quest_lines_fts, rowid, name, description) VALUES('delete', OLD.id, OLD.name, OLD.description);
                END
            """.trimIndent())

            stmt.executeUpdate("""
                CREATE TRIGGER IF NOT EXISTS quest_lines_fts_update AFTER UPDATE ON quest_lines BEGIN
                    INSERT INTO quest_lines_fts(quest_lines_fts, rowid, name, description) VALUES('delete', OLD.id, OLD.name, OLD.description);
                    INSERT INTO quest_lines_fts(rowid, name, description) VALUES (NEW.id, NEW.name, NEW.description);
                END
            """.trimIndent())
        }
    }

    /**
     * Clears all quest-related tables for re-indexing.
     */
    fun clearQuestTables(conn: Connection) {
        conn.createStatement().use { stmt ->
            // Delete in order respecting foreign keys
            stmt.executeUpdate("DELETE FROM reward_items")
            stmt.executeUpdate("DELETE FROM quest_rewards")
            stmt.executeUpdate("DELETE FROM task_entities")
            stmt.executeUpdate("DELETE FROM task_items")
            stmt.executeUpdate("DELETE FROM quest_tasks")
            stmt.executeUpdate("DELETE FROM quest_prerequisites")
            stmt.executeUpdate("DELETE FROM quest_line_entries")
            stmt.executeUpdate("DELETE FROM quests")
            stmt.executeUpdate("DELETE FROM quest_lines")
        }
    }
}
