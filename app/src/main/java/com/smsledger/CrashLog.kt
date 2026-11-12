package com.smsledger

import android.content.Context
import android.os.Build
import java.io.File

/**
 * Keeps the last unexpected close as a small text file in the app's private storage, so the user
 * can copy it from Settings and share it. The app has no internet access, so nothing is sent.
 * Only the error type and where in the code it happened are kept: never the error's own text,
 * which could quote part of a message.
 */
object CrashLog {
    private const val FILE = "last_crash.txt"
    private const val MAX_FRAMES = 40

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { File(app.filesDir, FILE).writeText(describe(error)) }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: Context): String? = runCatching { File(context.filesDir, FILE).takeIf { it.exists() }?.readText() }.getOrNull()

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE).delete() }
    }

    internal fun describe(error: Throwable): String = buildString {
        append("SMS Ledger ").append(BuildConfig.VERSION_NAME).append(" (").append(BuildConfig.FLAVOR).append(") · Android ").append(Build.VERSION.SDK_INT).append('\n')
        var cause: Throwable? = error
        var depth = 0
        while (cause != null && depth < 4) {
            append(if (depth == 0) "" else "Caused by: ").append(cause.javaClass.name).append('\n')
            cause.stackTrace.take(MAX_FRAMES).forEach { append("  at ").append(it.className).append('.').append(it.methodName).append(':').append(it.lineNumber).append('\n') }
            cause = cause.cause
            depth++
        }
    }
}
