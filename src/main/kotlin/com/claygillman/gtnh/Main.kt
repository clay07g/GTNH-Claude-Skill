package com.claygillman.gtnh

import com.claygillman.gtnh.cli.IndexCommand
import com.claygillman.gtnh.cli.InfoCommand
import com.claygillman.gtnh.cli.QueryCommand
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import kotlin.system.exitProcess

@Command(
    name = "gtnh-data",
    mixinStandardHelpOptions = true,
    version = ["gtnh-data 1.0.0"],
    description = ["CLI tool for indexing and querying Gregtech: New Horizons modpack data"],
    subcommands = [
        IndexCommand::class,
        QueryCommand::class,
        InfoCommand::class
    ]
)
class GtnhDataCommand : Runnable {

    @Option(
        names = ["-d", "--database"],
        description = ["Path to SQLite database file (default: ./gtnh.db)"],
        scope = CommandLine.ScopeType.INHERIT
    )
    var databasePath: String? = null

    override fun run() {
        // When no subcommand is specified, print usage
        CommandLine(this).usage(System.out)
    }

    companion object {
        fun getDatabasePath(cmd: GtnhDataCommand?): String {
            return cmd?.databasePath
                ?: System.getenv("GTNH_DATABASE")
                ?: "./gtnh.db"
        }
    }
}

fun main(args: Array<String>) {
    val exitCode = CommandLine(GtnhDataCommand()).execute(*args)
    exitProcess(exitCode)
}
