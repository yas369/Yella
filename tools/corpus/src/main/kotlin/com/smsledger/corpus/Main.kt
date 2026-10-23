package com.smsledger.corpus

import kotlin.system.exitProcess

/**
 * Developer-only tool: runs the core parser over an SMS XML export and prints reports.
 * Usage: corpus <report> <path-to-export.xml>
 */
fun main(args: Array<String>) {
    val report = args.firstOrNull()
    when (report) {
        null, "help", "hello" -> printUsage()
        else -> {
            System.err.println("Unknown report: $report")
            printUsage()
            exitProcess(2)
        }
    }
}

private fun printUsage() {
    println("sms-ledger corpus tool")
    println("usage: corpus <report> <path-to-export.xml>")
    println("reports: (none yet)")
}
