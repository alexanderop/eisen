package io.github.alexanderop.eisen.health

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSegment
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.metadata.Metadata
import io.github.alexanderop.eisen.domain.AppState
import io.github.alexanderop.eisen.domain.Workout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId

/**
 * Mirrors finished workouts into Health Connect, so they show up in Google Fit, Samsung Health and
 * the like. Off until the lifter turns it on. The switch is an Android-only preference, so it lives
 * here and not in the saved state the PWA shares.
 *
 * Health Connect can disappear, be updated or have its permission taken away at any time. Every call
 * is made off the main thread and any failure just skips the write: losing a sync is better than a crash.
 */
object HealthSync {
    val WRITE_PERMISSION: String = HealthPermission.getWritePermission(ExerciseSessionRecord::class)
    val PERMISSIONS: Set<String> = setOf(WRITE_PERMISSION)

    private const val PREFS = "health_connect"
    private const val KEY_ENABLED = "sync_enabled"

    var enabled: Boolean by mutableStateOf(false)
        private set

    private lateinit var app: Context
    private lateinit var prefs: SharedPreferences
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Writes run one after the other in the order they were asked for. Otherwise a delete could
     * overtake the insert of the same workout and leave it in Health Connect.
     */
    private val jobs = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    /** The history as last seen, to tell which workouts were edited or deleted since. */
    private var known: Map<String, Workout> = emptyMap()
    private var knownList: List<Workout> = emptyList()
    private var activeId: String? = null

    fun init(context: Context, state: AppState) {
        app = context.applicationContext
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        enabled = prefs.getBoolean(KEY_ENABLED, false)
        track(state)
        scope.launch { for (job in jobs) job() }
        if (enabled) enqueue { if (!hasPermission()) turnOff() }
    }

    fun status(context: Context): Int = HealthConnectClient.getSdkStatus(context)

    fun updateEnabled(value: Boolean) {
        enabled = value
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
    }

    /** Null when Health Connect cannot be used on this phone right now. */
    private fun client(): HealthConnectClient? =
        if (status(app) == HealthConnectClient.SDK_AVAILABLE) runCatching { HealthConnectClient.getOrCreate(app) }.getOrNull()
        else null

    suspend fun hasPermission(): Boolean = withContext(Dispatchers.IO) {
        guarded { client()?.permissionController?.getGrantedPermissions()?.containsAll(PERMISSIONS) } == true
    }

    /** The permission was taken away in Health Connect: show the switch as off instead of failing quietly forever. */
    private suspend fun turnOff() = withContext(Dispatchers.Main) { updateEnabled(false) }

    /** Runs [block], turning sync off on a revoked permission and swallowing every other failure. */
    private suspend fun <T> guarded(block: suspend () -> T): T? = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: SecurityException) {
        turnOff()
        null
    } catch (_: Exception) {
        null
    }

    private fun enqueue(job: suspend () -> Unit) {
        jobs.trySend { guarded { job() } }
    }

    /** Store listener: called on the main thread with the workout that was just finished. */
    fun onFinished(workout: Workout) {
        if (enabled) enqueue { upsert(listOf(workout)) }
    }

    /**
     * Store listener: called on the main thread after every change. Health Connect follows the
     * history: a workout edited in History is written again, a deleted one is removed. A finished
     * workout is left to [onFinished]. Workouts that arrive any other way (a restored backup) are
     * written too, so the two stay the same.
     */
    fun onChanged(state: AppState) {
        val before = known
        val finishedId = activeId
        // Most changes (a tapped set, a new weight) keep the same history list.
        val sameHistory = state.history === knownList
        track(state)
        if (sameHistory || !enabled) return

        val removed = before.keys - known.keys
        val changed = state.history.filter { workout ->
            val old = before[workout.id]
            if (old == null) workout.id != finishedId else old != workout
        }
        if (removed.isNotEmpty()) enqueue { delete(removed.toList()) }
        if (changed.isNotEmpty()) enqueue { upsert(changed) }
    }

    private fun track(state: AppState) {
        knownList = state.history
        known = state.history.associateBy { it.id }
        activeId = state.active?.id
    }

    /** Writes the whole history once. The number of workouts written, or null when Health Connect failed. */
    suspend fun syncPast(history: List<Workout>): Int? {
        val result = CompletableDeferred<Int?>()
        jobs.trySend { result.complete(guarded { upsert(history) }) }
        return result.await()
    }

    /** The number of records written, or null when Health Connect is not there. */
    private suspend fun upsert(workouts: List<Workout>): Int? {
        val client = client() ?: return null
        if (!client.permissionController.getGrantedPermissions().containsAll(PERMISSIONS)) {
            turnOff()
            return 0
        }
        // Newer than anything written before, so an edited workout replaces its old record.
        val version = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val records = workouts.mapNotNull { workout ->
            // A record Health Connect would refuse is skipped, not allowed to fail the whole batch.
            runCatching { sessionFields(workout, zone)?.let { record(workout.id, version, it) } }.getOrNull()
        }
        for (batch in records.chunked(BATCH)) client.insertRecords(batch)
        return records.size
    }

    private suspend fun delete(ids: List<String>) {
        val client = client() ?: return
        for (batch in ids.chunked(BATCH)) client.deleteRecords(ExerciseSessionRecord::class, emptyList(), batch)
    }

    private fun record(id: String, version: Long, fields: SessionFields) = ExerciseSessionRecord(
        startTime = fields.start,
        startZoneOffset = fields.startOffset,
        endTime = fields.end,
        endZoneOffset = fields.endOffset,
        // Sets are tapped in by hand, nothing is measured by a sensor.
        metadata = Metadata.manualEntry(clientRecordId = id, clientRecordVersion = version),
        exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
        title = fields.title,
        notes = fields.notes.ifEmpty { null },
        segments = fields.segments.map { ExerciseSegment(it.start, it.end, it.type, it.repetitions) },
    )

    /** A history of years is several hundred workouts. Smaller calls keep each binder message small. */
    private const val BATCH = 100
}
