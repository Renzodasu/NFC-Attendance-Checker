package com.nezzar.nfcattendance.data

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * The last crash, written down before the app dies.
 *
 * Android shows a dialog and then forgets: a teacher who says "it closed by
 * itself" gives nothing to fix. This keeps ONE stack trace in the app's own
 * private storage - never uploaded, never shared - so Settings can show it and
 * the failure arrives with the words the system actually used.
 *
 * The handler chains to the one already installed, so the system still shows its
 * own crash dialog and still kills the process: this watches, it does not swallow.
 */
object CrashLog {

    const val FILE_NAME = "crash-last.txt"

    /** Called once, before anything else in the process has a chance to fail. */
    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(app, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    /** The text of the last crash, or null when the app has not crashed. */
    fun last(context: Context): String? = runCatching {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.isFile) return@runCatching null
        file.readText().takeIf { it.isNotBlank() }
    }.getOrNull()

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE_NAME).delete() }
    }

    private fun write(context: Context, thread: Thread, error: Throwable) {
        val stack = StringWriter()
        PrintWriter(stack).use { error.printStackTrace(it) }
        val text = buildString {
            append(System.currentTimeMillis())
            append("  ")
            append(thread.name)
            append('\n')
            append(error.javaClass.name)
            append(": ")
            append(error.message ?: "no message")
            append("\n\n")
            append(stack.toString())
        }
        File(context.filesDir, FILE_NAME).writeText(text)
    }
}
