package io.github.alexanderop.eisen

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.alexanderop.eisen.data.Store
import io.github.alexanderop.eisen.ui.EisenColors
import io.github.alexanderop.eisen.ui.EisenTheme
import io.github.alexanderop.eisen.ui.Icons
import io.github.alexanderop.eisen.ui.history.HistoryScreen
import io.github.alexanderop.eisen.ui.home.HomeScreen
import io.github.alexanderop.eisen.ui.progress.ProgressScreen
import io.github.alexanderop.eisen.ui.settings.SettingsScreen
import io.github.alexanderop.eisen.ui.workout.WorkoutScreen
import io.github.alexanderop.eisen.ui.workouts.WorkoutsScreen
import io.github.alexanderop.eisen.widget.WorkoutLaunch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bars = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        // The widget or a launcher shortcut may have opened the app to start a workout.
        WorkoutLaunch.handle(intent, restored = savedInstanceState != null)
        setContent { EisenTheme { App() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        WorkoutLaunch.handle(intent, restored = false)
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Home),
    WORKOUTS("Workouts", Icons.Workouts),
    HISTORY("History", Icons.History),
    PROGRESS("Progress", Icons.Progress),
    SETTINGS("Settings", Icons.Settings),
}

@Composable
private fun App() {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    // Reopen an unfinished workout after the phone closed the app.
    var workoutOpen by rememberSaveable { mutableStateOf(Store.state.active != null) }
    val openRequested = WorkoutLaunch.openRequested
    LaunchedEffect(openRequested) {
        if (openRequested) {
            workoutOpen = true
            WorkoutLaunch.openRequested = false
        }
    }
    val showWorkout = workoutOpen && Store.state.active != null

    if (showWorkout) {
        // The back gesture closes the workout, not the app. The workout itself keeps running.
        BackHandler { workoutOpen = false }
        ShowOverLockScreen()
        WorkoutScreen(onClose = { workoutOpen = false })
        return
    }

    // Back from another tab goes home first, like every app with a bottom bar.
    BackHandler(enabled = tab != Tab.HOME) { tab = Tab.HOME }
    val screens = rememberSaveableStateHolder()
    Scaffold(
        containerColor = EisenColors.Background,
        bottomBar = {
            NavigationBar(containerColor = EisenColors.Card) {
                for (item in Tab.entries) {
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label) },
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
                    Tab.HOME -> HomeScreen(onOpenWorkout = { workoutOpen = true })
                    Tab.WORKOUTS -> WorkoutsScreen()
                    Tab.HISTORY -> HistoryScreen()
                    Tab.PROGRESS -> ProgressScreen()
                    Tab.SETTINGS -> SettingsScreen()
                }
            }
        }
    }
}

/**
 * While the workout shows, it also shows on top of the lock screen: the next set is one
 * tap away without unlocking the phone. Every other screen stays behind the lock.
 */
@Composable
private fun ShowOverLockScreen() {
    val activity = LocalActivity.current ?: return
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return
    DisposableEffect(activity) {
        activity.setShowWhenLocked(true)
        onDispose { activity.setShowWhenLocked(false) }
    }
}
