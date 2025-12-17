package com.claygillman.gtnh.cli

import picocli.CommandLine.Command
import picocli.CommandLine.ParentCommand
import com.claygillman.gtnh.GtnhDataCommand
import com.claygillman.gtnh.db.Database
import java.io.File

@Command(
    name = "info",
    mixinStandardHelpOptions = true,
    description = ["Display information about the GTNH database"]
)
class InfoCommand : Runnable {

    @ParentCommand
    private lateinit var parent: GtnhDataCommand

    override fun run() {
        val dbPath = GtnhDataCommand.getDatabasePath(parent)
        val dbFile = File(dbPath)

        println("GTNH Data Tool")
        println("==============")
        println()
        println("Database: $dbPath")
        println("Exists: ${dbFile.exists()}")

        if (dbFile.exists()) {
            val sizeKb = dbFile.length() / 1024
            println("Size: ${sizeKb} KB")
            println()

            // Show table info
            try {
                Database.withConnection(dbPath) { conn ->
                    val meta = conn.metaData
                    val tables = meta.getTables(null, null, "%", arrayOf("TABLE"))

                    println("Tables:")
                    var tableCount = 0
                    while (tables.next()) {
                        val tableName = tables.getString("TABLE_NAME")
                        val countStmt = conn.createStatement()
                        val countRs = countStmt.executeQuery("SELECT COUNT(*) FROM \"$tableName\"")
                        val rowCount = if (countRs.next()) countRs.getInt(1) else 0
                        println("  - $tableName: $rowCount rows")
                        tableCount++
                        countRs.close()
                        countStmt.close()
                    }
                    tables.close()

                    if (tableCount == 0) {
                        println("  (no tables - database is empty)")
                    }
                }
            } catch (e: Exception) {
                println("Error reading database: ${e.message}")
            }
        } else {
            println()
            println("Database does not exist yet. Run 'gtnh-data index' to create it.")
        }
    }
}
