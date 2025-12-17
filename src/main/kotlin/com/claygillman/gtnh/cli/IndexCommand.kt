package com.claygillman.gtnh.cli

import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.ParentCommand
import com.claygillman.gtnh.GtnhDataCommand
import com.claygillman.gtnh.quest.QuestIndexer
import java.io.File

@Command(
    name = "index",
    mixinStandardHelpOptions = true,
    description = ["Index data from a GTNH modpack installation"]
)
class IndexCommand : Runnable {

    @ParentCommand
    private lateinit var parent: GtnhDataCommand

    @Option(
        names = ["-p", "--path"],
        description = ["Path to GTNH modpack installation directory"],
        required = true
    )
    lateinit var modpackPath: String

    @Option(
        names = ["--force"],
        description = ["Force re-indexing even if data already exists"]
    )
    var force: Boolean = false

    override fun run() {
        val dbPath = GtnhDataCommand.getDatabasePath(parent)
        println("Indexing GTNH data from: $modpackPath")
        println("Database location: $dbPath")
        println()

        // Index quests
        val questsPath = File(modpackPath, "config/betterquesting/DefaultQuests").absolutePath

        if (!File(questsPath).exists()) {
            System.err.println("Error: Quest data not found at: $questsPath")
            System.err.println("Make sure the path points to a GTNH modpack installation directory.")
            return
        }

        try {
            val indexer = QuestIndexer(dbPath, questsPath, force)
            val result = indexer.index { phase, current, total ->
                print("\r$phase: $current/$total")
                if (current == total) println()
            }

            println()
            println("Indexing complete!")
            println("  Quest lines: ${result.questLineCount}")
            println("  Quests: ${result.questCount}")

            if (result.errors.isNotEmpty()) {
                println()
                println("Warnings (${result.errors.size}):")
                result.errors.take(10).forEach { println("  - $it") }
                if (result.errors.size > 10) {
                    println("  ... and ${result.errors.size - 10} more")
                }
            }
        } catch (e: Exception) {
            System.err.println("Error during indexing: ${e.message}")
            e.printStackTrace()
        }
    }
}
