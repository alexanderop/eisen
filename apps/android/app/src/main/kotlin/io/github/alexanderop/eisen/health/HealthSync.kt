package io.github.alexanderop.eisen.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSegment
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.metadata.Metadata
import io.github.alexanderop.eisen.domain.TimerSession
import io.github.alexanderop.eisen.domain.Workout
import io.github.alexanderop.eisen.domain.elm.HealthAvailability
import io.github.alexanderop.eisen.domain.elm.HealthJob
import io.github.alexanderop.eisen.domain.elm.Msg
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * Health Connect's adapter: carries out the [HealthJob]s that `update` asks for and answers with Msgs.
 * Which workouts to write or remove, and whether sync is on at all, is decided in `update`.
 *
 * Health Connect can disappear, be updated or have its permission taken away at any time. Every call
 * is made off the main thread and any failure just skips the write: losing a sync is better than a crash.
 */
object HealthSync {
    val WRITE_PERMISSION: String = HealthPermission.getWritePermission(ExerciseSessionRecord::class)
    val PERMISSIONS: Set<String> = setOf(WRITE_PERMISSION)

    private lateinit var app: Context
    private lateinit var dispatch: (Msg) -> Unit
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Writes run one after the other in the order they were asked for. Otherwise a delete could
     * overtake the insert of the same workout and leave it in Health Connect.
     */
    private val jobs = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    fun start(context: Context, dispatch: (Msg) -> Unit) {
        app = context.applicationContext
        this.dispatch = dispatch
        scope.launch { for (job in jobs) job() }
    }

    /** Whether Health Connect is installed and usable on this phone, as `HealthConnectClient.SDK_*`. */
    private fun status(context: Context): Int = HealthConnectClient.getSdkStatus(context)

    fun run(job: HealthJob) {
        when (job) {
            // Not queued behind a long sync: the switch should answer at once.
            is HealthJob.CheckPermission -> scope.launch { dispatch(Msg.Health.PermissionChecked(job.purpose, hasPermission())) }
            is HealthJob.Upsert -> enqueue { guarded { upsert(job.workouts, job.timers) } }
            is HealthJob.Delete -> enqueue { guarded { delete(job.ids) } }
            is HealthJob.SyncPast -> enqueue { dispatch(Msg.Health.SyncedPast(guarded { upsert(job.workouts, job.timers) })) }
            // Answered at once: the activity asks before its first frame, so Settings never shows a stale row.
            HealthJob.CheckAvailability -> dispatch(Msg.Health.Availability(availability()))
        }
    }

    private fun availability(): HealthAvailability = when (status(app)) {
        HealthConnectClient.SDK_AVAILABLE -> HealthAvailability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthAvailability.NEEDS_INSTALL
        else -> HealthAvailability.UNAVAILABLE
    }

    private fun enqueue(job: suspend () -> Unit) {
        jobs.trySend(job)
    }

    /** Null when Health Connect cannot be used on this phone right now. */
    private fun client(): HealthConnectClient? =
        if (status(app) == HealthConnectClient.SDK_AVAILABLE) runCatching { HealthConnectClient.getOrCreate(app) }.getOrNull()
        else null

    private suspend fun hasPermission(): Boolean =
        guarded { client()?.permissionController?.getGrantedPermissions()?.containsAll(PERMISSIONS) } == true

    /** Runs [block], reporting a revoked permission and swallowing every other failure. */
    private suspend fun <T> guarded(block: suspend () -> T): T? = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: SecurityException) {
        dispatch(Msg.Health.Revoked)
        null
    } catch (_: Exception) {
        null
    }

    /** The number of records written, or null when Health Connect is not there. */
    private suspend fun upsert(workouts: List<Workout>, timers: List<TimerSession>): Int? {
        val client = client() ?: return null
        if (!client.permissionController.getGrantedPermissions().containsAll(PERMISSIONS)) {
            dispatch(Msg.Health.Revoked)
            return 0
        }
        // Newer than anything written before, so an edited workout replaces its old record.
        val version = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val records = workouts.mapNotNull { workout ->
            // A record Health Connect would refuse is skipped, not allowed to fail the whole batch.
            runCatching { sessionFields(workout, zone)?.let { record(workout.id, version, it) } }.getOrNull()
        } + timers.mapNotNull { timer ->
            runCatching { timerSessionFields(timer, zone)?.let { record(timer.id, version, it) } }.getOrNull()
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
        exerciseType = fields.exerciseType,
        title = fields.title,
        notes = fields.notes.ifEmpty { null },
        segments = fields.segments.map { ExerciseSegment(it.start, it.end, it.type, it.repetitions) },
    )

    /** A history of years is several hundred workouts. Smaller calls keep each binder message small. */
    private const val BATCH = 100
}
