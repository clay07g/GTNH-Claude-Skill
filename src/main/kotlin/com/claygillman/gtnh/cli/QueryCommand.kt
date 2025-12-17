package com.claygillman.gtnh.cli

import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import picocli.CommandLine.ParentCommand
import com.claygillman.gtnh.GtnhDataCommand
import com.claygillman.gtnh.db.Database

@Command(
    name = "query",
    mixinStandardHelpOptions = true,
    description = ["Query the GTNH database with SQL"]
)
class QueryCommand : Runnable {

    @ParentCommand
    private lateinit var parent: GtnhDataCommand

    @Parameters(
        index = "0",
        description = ["SQL query to execute"],
        arity = "0..1"
    )
    var query: String? = null

    override fun run() {
        val dbPath = GtnhDataCommand.getDatabasePath(parent)

        if (query.isNullOrBlank()) {
            println("Usage: gtnh-data query \"<SQL query>\"")
            println()
            println("Example queries:")
            println("  gtnh-data query \"SELECT name FROM quest_lines ORDER BY display_order\"")
            println("  gtnh-data query \"SELECT name FROM quests WHERE is_main = 1 LIMIT 10\"")
            println("  gtnh-data query \"SELECT name FROM quests_fts WHERE quests_fts MATCH 'quantum'\"")
            return
        }

        Database.withConnection(dbPath) { conn ->
            try {
                conn.createStatement().use { stmt ->
                    val rs = stmt.executeQuery(query)
                    val meta = rs.metaData
                    val colCount = meta.columnCount

                    // Print column headers
                    val headers = (1..colCount).map { meta.getColumnName(it) }
                    println(headers.joinToString(" | "))
                    println("-".repeat(headers.sumOf { it.length } + (colCount - 1) * 3))

                    // Print rows
                    var rowCount = 0
                    while (rs.next()) {
                        val row = (1..colCount).map { rs.getString(it) ?: "NULL" }
                        println(row.joinToString(" | "))
                        rowCount++
                    }

                    println()
                    println("($rowCount rows)")
                }
            } catch (e: Exception) {
                System.err.println("Query error: ${e.message}")
            }
        }
    }
}
