package io.github.alexanderop.eisen.feature.health.platform

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSegment
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import io.github.alexanderop.eisen.BuildConfig
import io.github.alexanderop.eisen.feature.food.FoodEntry
import io.github.alexanderop.eisen.feature.timer.TimerSession
import io.github.alexanderop.eisen.feature.workout.Workout
import io.github.alexanderop.eisen.app.elm.HealthAvailability
import io.github.alexanderop.eisen.app.elm.HealthJob
import io.github.alexanderop.eisen.app.elm.Msg
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

    /** `android.permission.health.WRITE_NUTRITION`: what was eaten, asked for together with the workouts. */
    val FOOD_PERMISSION: String = HealthPermission.getWritePermission(NutritionRecord::class)
    val PERMISSIONS: Set<String> = setOf(WRITE_PERMISSION, FOOD_PERMISSION)

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
        scope.launch {
            for (job in jobs) {
                // One job that fails in a way [guarded] does not catch must not end the queue: every later
                // sync of the process would wait in it forever.
                try {
                    job()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Throwable) {
                    Log.w(TAG, "a Health Connect job failed", failure)
                }
            }
        }
    }

    /** Whether Health Connect is installed and usable on this phone, as `HealthConnectClient.SDK_*`. */
    private fun status(context: Context): Int = HealthConnectClient.getSdkStatus(context)

    fun run(job: HealthJob) {
        when (job) {
            // Not queued behind a long sync: the switch should answer at once.
            is HealthJob.CheckPermission -> scope.launch {
                val granted = granted()
                dispatch(Msg.Health.PermissionChecked(job.purpose, WRITE_PERMISSION in granted, FOOD_PERMISSION in granted))
            }
            is HealthJob.Upsert -> enqueue { guarded { upsert(job.workouts, job.timers, job.food) } }
            is HealthJob.Delete -> enqueue { guarded { delete(job.ids, job.food) } }
            is HealthJob.SyncPast -> enqueue {
                val written = guarded { upsert(job.workouts, job.timers, job.food) }
                dispatch(Msg.Health.SyncedPast(written?.first, written?.second ?: 0))
            }
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

    private suspend fun granted(): Set<String> = guarded { client()?.permissionController?.getGrantedPermissions() }.orEmpty()

    /**
     * Runs [block], reporting a revoked permission and swallowing every other failure, an [Error] from
     * the Health Connect library too: a sync that cannot run is skipped, and a past sync still answers.
     */
    private suspend fun <T> guarded(block: suspend () -> T): T? = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: SecurityException) {
        dispatch(Msg.Health.Revoked)
        null
    } catch (failure: Throwable) {
        Log.w(TAG, "Health Connect failed", failure)
        null
    }

    private const val TAG = "EisenHealth"

    /** The number of workout and timer records written and of food records, or null when Health Connect is not there. */
    private suspend fun upsert(workouts: List<Workout>, timers: List<TimerSession>, food: List<FoodEntry>): Pair<Int, Int>? {
        val client = client() ?: return null
        val granted = client.permissionController.getGrantedPermissions()
        if (WRITE_PERMISSION !in granted) {
            dispatch(Msg.Health.Revoked)
            return 0 to 0
        }
        val foods = if (food.isEmpty()) 0 else if (FOOD_PERMISSION !in granted) 0.also { dispatch(Msg.Health.FoodRevoked) } else writeFood(client, food)
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
        return records.size to foods
    }

    /** Food entries as nutrition records, rewritten by their id like workouts. The number written. */
    private suspend fun writeFood(client: HealthConnectClient, food: List<FoodEntry>): Int {
        val version = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val records = food.mapNotNull { entry -> runCatching { nutritionFields(entry, zone)?.let { nutrition(entry.id, version, it) } }.getOrNull() }
        for (batch in records.chunked(BATCH)) client.insertRecords(batch)
        if (BuildConfig.DEBUG) logReadBack(client, records)
        return records.size
    }

    /**
     * Debug builds only: reads back the nutrition records just written and logs what Health Connect holds, the
     * emulator's witness (Health Connect's own screens leave protein out). Needs `READ_NUTRITION`, which only the debug
     * manifest declares and nothing in the app asks for; without it this logs that and does nothing.
     */
    private suspend fun logReadBack(client: HealthConnectClient, records: List<NutritionRecord>) {
        if (records.isEmpty()) return
        try {
            val ids = records.mapNotNull { it.metadata.clientRecordId }.toSet()
            val range = TimeRangeFilter.between(records.minOf { it.startTime }, records.maxOf { it.endTime })
            for (record in client.readRecords(ReadRecordsRequest(NutritionRecord::class, range)).records.filter { it.metadata.clientRecordId in ids }) {
                Log.i(
                    TAG,
                    "read back ${record.metadata.clientRecordId}: ${record.name}, ${record.energy?.inKilocalories} kcal, " +
                        "protein ${record.protein?.inGrams} g, carbs ${record.totalCarbohydrate?.inGrams} g, fat ${record.totalFat?.inGrams} g, " +
                        "meal type ${record.mealType}, ${record.startTime} to ${record.endTime}",
                )
            }
        } catch (_: SecurityException) {
            Log.i(TAG, "read back skipped: READ_NUTRITION not granted")
        }
    }

    private suspend fun delete(ids: List<String>, food: List<String>) {
        val client = client() ?: return
        for (batch in ids.chunked(BATCH)) client.deleteRecords(ExerciseSessionRecord::class, emptyList(), batch)
        for (batch in food.chunked(BATCH)) client.deleteRecords(NutritionRecord::class, emptyList(), batch)
    }

    private fun nutrition(id: String, version: Long, fields: NutritionFields) = NutritionRecord(
        startTime = fields.start,
        startZoneOffset = fields.startOffset,
        endTime = fields.end,
        endZoneOffset = fields.endOffset,
        // Typed or picked by the lifter, nothing measured.
        metadata = Metadata.manualEntry(clientRecordId = id, clientRecordVersion = version),
        energy = Energy.kilocalories(fields.kcal),
        protein = fields.protein?.let(Mass::grams),
        totalCarbohydrate = fields.carbs?.let(Mass::grams),
        totalFat = fields.fat?.let(Mass::grams),
        name = fields.name,
        mealType = MealType.MEAL_TYPE_UNKNOWN,
    )

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
