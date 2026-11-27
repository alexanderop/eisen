package io.github.alexanderop.eisen

import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.CompositionLocalProvider
import io.github.alexanderop.eisen.core.ui.LocalHapticsEnabled
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.health.connect.client.PermissionController
import io.github.alexanderop.eisen.app.elm.Feel
import io.github.alexanderop.eisen.app.elm.HistoryView
import io.github.alexanderop.eisen.app.elm.Model
import io.github.alexanderop.eisen.app.elm.Msg
import io.github.alexanderop.eisen.app.elm.Tab
import io.github.alexanderop.eisen.app.elm.BAR_TABS
import io.github.alexanderop.eisen.app.elm.UiEvent
import io.github.alexanderop.eisen.app.elm.handlesBack
import io.github.alexanderop.eisen.app.elm.showsWorkout
import io.github.alexanderop.eisen.app.elm.showsFood
import io.github.alexanderop.eisen.feature.food.ui.FoodScreen
import io.github.alexanderop.eisen.feature.food.ui.AddFoodScreen
import io.github.alexanderop.eisen.feature.food.ui.WeightSheet
import io.github.alexanderop.eisen.feature.food.platform.BarcodeScanner
import io.github.alexanderop.eisen.feature.food.platform.LabelReader
import io.github.alexanderop.eisen.app.elm.PhotoFailure
import io.github.alexanderop.eisen.app.elm.PhotoSource
import androidx.activity.result.PickVisualMediaRequest
import android.net.Uri
import io.github.alexanderop.eisen.app.elm.ScanFailure
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import io.github.alexanderop.eisen.feature.health.platform.HealthSync
import io.github.alexanderop.eisen.app.runtime.Eisen
import io.github.alexanderop.eisen.app.runtime.Prefs
import io.github.alexanderop.eisen.core.ui.EisenColors
import io.github.alexanderop.eisen.core.ui.EisenTheme
import io.github.alexanderop.eisen.core.ui.Haptic
import io.github.alexanderop.eisen.core.ui.Icons
import io.github.alexanderop.eisen.feature.history.ui.HistoryScreen
import io.github.alexanderop.eisen.feature.home.ui.HomeScreen
import io.github.alexanderop.eisen.feature.timer.ui.TimerOverlay
import io.github.alexanderop.eisen.feature.timer.ui.TimerScreen
import io.github.alexanderop.eisen.core.ui.rememberHaptics
import io.github.alexanderop.eisen.feature.settings.ui.SettingsScreen
import io.github.alexanderop.eisen.feature.workout.ui.WorkoutScreen
import io.github.alexanderop.eisen.feature.workout.ui.WorkoutSessionHost
import io.github.alexanderop.eisen.feature.workout.ui.WorkoutsScreen
import io.github.alexanderop.eisen.feature.home.platform.WorkoutLaunch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val SAVED_TAB = "tab"
private const val SAVED_WORKOUT_OPEN = "workoutOpen"
private const val SAVED_ROUTINE = "routine"
private const val SAVED_HISTORY = "history"
/** Build 24 kept the Food screen over Home under this key; read so such a saved state comes back on the Food tab. */
private const val SAVED_FOOD = "food"

/**
 * The one activity. It turns what Android tells it (intents, start and stop, saved state) into Msgs,
 * and draws the model: the tabs, or the workout. Screens get the model (or the part they need) and
 * [Eisen.dispatch] from [App].
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Eisen.init(applicationContext)
        val bars = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        // After the process ended, the model starts over; the tab and the open workout come back from here.
        savedInstanceState?.let { saved ->
            val tab = saved.getString(SAVED_TAB)?.let { name -> Tab.entries.firstOrNull { it.name == name } } ?: Tab.HOME
            val history = saved.getString(SAVED_HISTORY)?.let { name -> HistoryView.entries.firstOrNull { it.name == name } } ?: HistoryView.LOG
            Eisen.dispatch(Msg.Nav.Restore(tab, saved.getBoolean(SAVED_WORKOUT_OPEN), saved.getString(SAVED_ROUTINE), history, food = saved.getBoolean(SAVED_FOOD)))
        }
        // The widget or a launcher shortcut may have opened the app to start a workout.
        WorkoutLaunch.message(intent, restored = savedInstanceState != null)?.let(Eisen.dispatch)
        setContent {
            EisenTheme {
                CompositionLocalProvider(LocalHapticsEnabled provides Eisen.model.state.settings.haptics) {
                    App(Eisen.model, Eisen.dispatch)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        WorkoutLaunch.message(intent, restored = false)?.let(Eisen.dispatch)
    }

    override fun onStart() {
        super.onStart()
        // Notifications may have been allowed or taken away in the system settings meanwhile.
        Eisen.dispatch(Msg.App.Foreground(notificationsGranted = Prefs.notificationsGranted(this)))
    }

    override fun onStop() {
        Eisen.dispatch(Msg.App.Background)
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val nav = Eisen.model.nav
        outState.putString(SAVED_TAB, nav.tab.name)
        outState.putBoolean(SAVED_WORKOUT_OPEN, nav.workoutOpen)
        outState.putString(SAVED_ROUTINE, nav.routine)
        outState.putString(SAVED_HISTORY, nav.history.name)
    }
}

private val Tab.label: String
    get() = when (this) {
        Tab.HOME -> "Home"
        Tab.WORKOUTS -> "Workouts"
        Tab.TIMER -> "Timer"
        Tab.FOOD -> "Food"
        Tab.HISTORY -> "History"
        Tab.SETTINGS -> "Settings"
    }

private val Tab.icon: ImageVector
    get() = when (this) {
        Tab.HOME -> Icons.Home
        Tab.WORKOUTS -> Icons.Workouts
        Tab.TIMER -> Icons.Timer
        Tab.FOOD -> Icons.Food
        Tab.HISTORY -> Icons.History
        Tab.SETTINGS -> Icons.Settings
    }

@Composable
private fun App(model: Model, dispatch: (Msg) -> Unit) {
    UiEvents(dispatch)
    // First of all back handlers, so the ones further in (a screen's own) come before it.
    // Back closes the workout (it keeps running), then goes home from any other tab.
    BackHandler(enabled = handlesBack(model)) { dispatch(Msg.Nav.Back) }
    // Above both branches: the discard question shows on the workout and on the home screen.
    WorkoutSessionHost(model.session, model.now, dispatch)

    // An interval timer covers everything, a running workout too, which stays as it is underneath.
    val timer = model.timer
    if (timer != null) {
        ShowOverLockScreen()
        TimerOverlay(timer, model.state, model.now, dispatch)
        return
    }

    if (showsWorkout(model)) {
        ShowOverLockScreen()
        WorkoutScreen(model, dispatch)
        return
    }

    // The add screen covers the tabs like the workout: the plate at its bottom, the app's snackbars above it.
    if (showsFood(model) && model.food.add != null) {
        AddFoodScreen(model, dispatch) { SnackbarHost(Eisen.snackbars.host) }
        return
    }

    val tab = model.nav.tab
    val screens = rememberSaveableStateHolder()
    Scaffold(
        containerColor = EisenColors.Background,
        // On the Food tab above its Scan and Add, which a snackbar with Undo must not cover.
        snackbarHost = { SnackbarHost(Eisen.snackbars.host, Modifier.padding(bottom = if (showsFood(model)) 72.dp else 0.dp)) },
        bottomBar = {
            NavigationBar(containerColor = EisenColors.Card) {
                for (item in BAR_TABS) {
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { dispatch(Msg.Nav.SelectTab(item)) },
                        icon = { Icon(item.icon, contentDescription = null) },
                        // A large font shrinks the label instead of breaking it mid-word ("Worko / uts").
                        label = {
                            Text(
                                item.label,
                                maxLines = 1,
                                softWrap = false,
                                autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = MaterialTheme.typography.labelMedium.fontSize),
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = EisenColors.OnPrimary,
                            selectedTextColor = EisenColors.Primary,
                            indicatorColor = EisenColors.Primary,
                            unselectedIconColor = EisenColors.MutedForeground,
                            unselectedTextColor = EisenColors.MutedForeground,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            // Each tab keeps its scroll position and open sections while another one shows.
            screens.SaveableStateProvider(tab) {
                when (tab) {
                    Tab.HOME -> HomeScreen(model, dispatch)
                    Tab.WORKOUTS -> WorkoutsScreen(model, dispatch)
                    Tab.TIMER -> TimerScreen(model, dispatch)
                    Tab.FOOD -> FoodScreen(model, dispatch)
                    Tab.HISTORY -> HistoryScreen(model, dispatch)
                    Tab.SETTINGS -> SettingsScreen(model, dispatch)
                }
            }
            // The weight is logged from the Food tab and from History › Progress, over either.
            if (tab == Tab.FOOD || tab == Tab.HISTORY) WeightSheet(model, dispatch)
        }
    }
}

/**
 * Carries out the one-shot UI events `update` asks for: haptics, the system permission dialogs and
 * the share sheet. The answers go back as Msgs. Snackbars are shown by the runtime's queue.
 */
@Composable
private fun UiEvents(dispatch: (Msg) -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        dispatch(Msg.Rest.NotificationPermission(granted))
    }
    val health = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        dispatch(Msg.Health.PermissionResult(HealthSync.WRITE_PERMISSION in granted, HealthSync.FOOD_PERMISSION in granted, HealthSync.WEIGHT_PERMISSION in granted))
    }
    // A photo of a label: read on the phone, the text goes to the program, the photo is thrown away.
    fun readPhoto(uri: Uri) {
        dispatch(Msg.Food.PhotoChosen)
        LabelReader.read(context, uri) { text -> dispatch(if (text != null) Msg.Food.LabelRecognized(text) else Msg.Food.LabelFailed(PhotoFailure.UNREADABLE)) }
    }
    // The system's camera app: no camera permission. The photo picker: no storage permission.
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        if (taken) readPhoto(LabelReader.cameraUri(context)) else {
            LabelReader.discard(context)
            dispatch(Msg.Food.PhotoCancelled)
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) readPhoto(uri) else dispatch(Msg.Food.PhotoCancelled)
    }
    LaunchedEffect(Unit) {
        Eisen.uiEvents.collect { event ->
            when (event) {
                is UiEvent.Haptic -> haptics.perform(event.feel.haptic())
                UiEvent.RequestNotificationPermission -> notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                UiEvent.RequestHealthPermission ->
                    runCatching { health.launch(HealthSync.PERMISSIONS) }.onFailure { dispatch(Msg.Health.PermissionRequestFailed) }
                is UiEvent.ShareBackup -> scope.launch {
                    runCatching { shareBackup(context, event.fileName, event.json) }.onFailure { dispatch(Msg.Data.SendFailed) }
                }
                is UiEvent.CopyText -> runCatching {
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Eisen", event.text))
                }
                is UiEvent.ShareText -> runCatching {
                    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, event.text) }
                    context.startActivity(Intent.createChooser(send, null))
                }
                UiEvent.ScanBarcode -> activity?.let { BarcodeScanner.scan(it, dispatch) } ?: dispatch(Msg.Food.ScanFailed(ScanFailure.UNAVAILABLE))
                is UiEvent.TakePhoto -> when (event.source) {
                    PhotoSource.CAMERA -> runCatching { camera.launch(LabelReader.newCameraUri(context)) }.onFailure { dispatch(Msg.Food.LabelFailed(PhotoFailure.NO_APP)) }
                    PhotoSource.PICKER -> runCatching { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }.onFailure { dispatch(Msg.Food.LabelFailed(PhotoFailure.NO_APP)) }
                }
                is UiEvent.Snackbar, is UiEvent.DismissSnackbar -> Unit
            }
        }
    }
}

private fun Feel.haptic(): Haptic = when (this) {
    Feel.TAP -> Haptic.TAP
    Feel.WARNING -> Haptic.WARNING
    Feel.SUCCESS -> Haptic.SUCCESS
    Feel.DONE -> Haptic.DONE
}

/** Writes the backup into the folder the FileProvider shares and opens the share sheet with it. */
private suspend fun shareBackup(context: Context, name: String, json: String) {
    val file = withContext(Dispatchers.IO) {
        val folder = File(context.cacheDir, "backups")
        // Only the newest file is kept, so old backups do not pile up in the cache.
        folder.mkdirs()
        folder.listFiles()?.forEach { it.delete() }
        File(folder, name).apply { writeText(json) }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TITLE, name)
        clipData = ClipData.newRawUri(name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, null))
}

/**
 * While the workout or an interval timer shows, it also shows on top of the lock screen: the next
 * set is one tap away without unlocking the phone. Every other screen stays behind the lock.
 */
@Composable
private fun ShowOverLockScreen() {
    val activity = LocalActivity.current ?: return
    DisposableEffect(activity) {
        activity.setShowWhenLocked(true)
        onDispose { activity.setShowWhenLocked(false) }
    }
}
