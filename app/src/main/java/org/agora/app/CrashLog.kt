package org.agora.app

import android.content.Context
import android.os.Build
import java.io.File

/**
 * Debug builds only: keeps the stack trace of the last crash, so the next start can show it instead of crashing
 * again in the background (there is no emulator; testers send the text).
 */
object CrashLog {
    private fun file(context: Context) = File(context.filesDir, "last-crash.txt")

    fun install(context: Context) {
        if (!BuildConfig.DEBUG) return
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                file(app).writeText(
                    "Agora ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})" +
                        " · ${Build.MANUFACTURER} ${Build.MODEL}\nThread: ${thread.name}\n\n${error.stackTraceToString()}"
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** The saved crash report, if the last run crashed. */
    fun pending(context: Context): String? =
        if (BuildConfig.DEBUG) file(context).takeIf { it.exists() }?.let { runCatching { it.readText() }.getOrNull() } else null

    fun clear(context: Context) {
        file(context).delete()
    }
}
