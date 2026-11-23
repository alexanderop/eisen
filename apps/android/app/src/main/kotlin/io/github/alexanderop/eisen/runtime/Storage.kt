package io.github.alexanderop.eisen.runtime

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import io.github.alexanderop.eisen.domain.AppState
import io.github.alexanderop.eisen.domain.decodeState
import io.github.alexanderop.eisen.domain.elm.RestState
import io.github.alexanderop.eisen.domain.encodeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors

/** The state file in the app's private storage. The same JSON the PWA exports. */
internal object StateFile {
    private const val NAME = "eisen.json"
    private val writer = Executors.newSingleThreadExecutor()

    /** Null when there is no file yet or it cannot be read. What then happens is `init`'s business. */
    fun read(context: Context): AppState? =
        runCatching { decodeState(File(context.filesDir, NAME).readText()) }.getOrNull()

    /** Off the main thread, one write after the other, so the newest state is the one that stays. */
    fun write(context: Context, state: AppState) {
        val target = File(context.filesDir, NAME)
        writer.execute {
            // Written next to the file and renamed, so a crash mid-write cannot leave half a file.
            runCatching {
                val draft = File(target.parentFile, target.name + ".tmp")
                draft.writeText(encodeState(state))
                if (!draft.renameTo(target)) target.writeText(encodeState(state))
            }
        }
    }
}

/**
 * Android-only preferences. Their names and keys are the ones earlier versions used, so an update
 * keeps a running rest, the Health Connect switch and the "asked for notifications" mark.
 */
internal object Prefs {
    private const val REST = "rest"
    private const val HEALTH = "health_connect"
    private const val HEALTH_ENABLED = "sync_enabled"
    private const val WORKOUT = "workout_screen"
    private const val ASKED_NOTIFICATIONS = "askedNotifications"

    fun rest(context: Context): RestState? {
        val prefs = context.getSharedPreferences(REST, Context.MODE_PRIVATE)
        val endsAt = prefs.getLong("endsAt", 0)
        if (endsAt <= 0) return null
        return RestState(prefs.getLong("startedAt", 0), endsAt, prefs.getString("note", "") ?: "")
    }

    fun saveRest(context: Context, rest: RestState?) = context.getSharedPreferences(REST, Context.MODE_PRIVATE).edit {
        if (rest == null) clear() else putLong("startedAt", rest.startedAt).putLong("endsAt", rest.endsAt).putString("note", rest.note)
    }

    fun healthEnabled(context: Context): Boolean =
        context.getSharedPreferences(HEALTH, Context.MODE_PRIVATE).getBoolean(HEALTH_ENABLED, false)

    fun saveHealthEnabled(context: Context, enabled: Boolean) =
        context.getSharedPreferences(HEALTH, Context.MODE_PRIVATE).edit { putBoolean(HEALTH_ENABLED, enabled) }

    fun notificationsAsked(context: Context): Boolean =
        context.getSharedPreferences(WORKOUT, Context.MODE_PRIVATE).getBoolean(ASKED_NOTIFICATIONS, false)

    fun markNotificationsAsked(context: Context) =
        context.getSharedPreferences(WORKOUT, Context.MODE_PRIVATE).edit { putBoolean(ASKED_NOTIFICATIONS, true) }

    /** Before Android 13 every app may show notifications. */
    fun notificationsGranted(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

/** Reading and writing backup files the lifter picked, off the main thread. */
internal object Backups {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun read(context: Context, uri: String, done: (String?) -> Unit) {
        scope.launch {
            done(runCatching { context.contentResolver.openInputStream(Uri.parse(uri))?.use { it.readBytes().decodeToString() } }.getOrNull())
        }
    }

    fun write(context: Context, uri: String, json: String, done: (Boolean) -> Unit) {
        scope.launch {
            // "wt" empties the file first, so a shorter backup over an old one leaves no tail behind.
            done(runCatching { context.contentResolver.openOutputStream(Uri.parse(uri), "wt")?.use { it.write(json.toByteArray()) } != null }.getOrDefault(false))
        }
    }
}
