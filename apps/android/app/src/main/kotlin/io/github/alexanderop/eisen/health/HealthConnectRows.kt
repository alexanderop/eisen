package io.github.alexanderop.eisen.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.alexanderop.eisen.domain.elm.HealthAvailability
import io.github.alexanderop.eisen.domain.elm.HealthModel
import io.github.alexanderop.eisen.domain.elm.Msg
import io.github.alexanderop.eisen.ui.EisenColors
import io.github.alexanderop.eisen.ui.pathIcon

// Lucide "heart-pulse" and "refresh-cw", the same icon set as the rest of the settings.
private val HealthIcon = pathIcon(
    "HeartPulse",
    "M2 9.5a5.5 5.5 0 0 1 9.591-3.676.56.56 0 0 0 .818 0A5.49 5.49 0 0 1 22 9.5c0 2.29-1.5 4-3 5.5l-5.492 5.313a2 2 0 0 1-3 .019L5 15c-1.5-1.5-3-3.2-3-5.5",
    "M3.22 13H9.5l.5-1 2 4.5 2-7 1.5 3.5h5.27",
)
private val SyncIcon = pathIcon(
    "Sync",
    "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8",
    "M21 3v5h-5",
    "M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16",
    "M8 16H3v5",
)

private const val PROVIDER = "com.google.android.apps.healthdata"
private const val LABEL = "Sync workouts to Health Connect"

/** The Play Store page that installs or updates Health Connect, as the Health Connect docs describe it. */
private fun openPlayStore(context: Context) {
    val store = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PROVIDER&url=healthconnect%3A%2F%2Fonboarding"))
        .setPackage("com.android.vending")
        .putExtra("overlay", true)
        .putExtra("callerId", context.packageName)
    // Phones without the Play Store still get the page in a browser.
    val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$PROVIDER"))
    runCatching { context.startActivity(store) }.recoverCatching { context.startActivity(web) }
}

/**
 * The Health Connect rows of the settings list. They look like the other rows: the whole line is
 * the tap target and the switch only shows the state. Whether Health Connect is installed comes
 * from the model; the runtime asks Android when the app comes to the front and when these rows show.
 */
@Composable
fun HealthConnectRows(health: HealthModel, dispatch: (Msg) -> Unit) {
    val context = LocalContext.current

    // Back from the Play Store: Health Connect may be installed or updated now.
    LifecycleResumeEffect(Unit) {
        dispatch(Msg.Health.RowsShown)
        onPauseOrDispose { }
    }

    when (health.availability) {
        HealthAvailability.AVAILABLE -> {
            ListItem(
                headlineContent = { Text(LABEL, fontSize = 16.sp) },
                supportingContent = { Text("Finished workouts show up in Google Fit, Samsung Health and other apps.", fontSize = 13.sp) },
                modifier = Modifier.toggleable(value = health.enabled, role = Role.Switch, onValueChange = { dispatch(Msg.Health.Toggle) })
                    .heightIn(min = 56.dp),
                leadingContent = { Icon(HealthIcon, contentDescription = null, modifier = Modifier.size(24.dp)) },
                trailingContent = { Switch(checked = health.enabled, onCheckedChange = null) },
                colors = rowColors(),
            )
            if (health.enabled) {
                ListItem(
                    headlineContent = { Text("Sync past workouts", fontSize = 16.sp) },
                    supportingContent = { Text("Writes every workout in your history once.", fontSize = 13.sp) },
                    modifier = Modifier.clickable(enabled = !health.syncing, role = Role.Button, onClick = { dispatch(Msg.Health.SyncPast) })
                        .heightIn(min = 56.dp)
                        .alpha(if (health.syncing) 0.5f else 1f),
                    leadingContent = { Icon(SyncIcon, contentDescription = null, modifier = Modifier.size(24.dp)) },
                    colors = rowColors(),
                )
            }
        }
        HealthAvailability.NEEDS_INSTALL -> ListItem(
            headlineContent = { Text(LABEL, fontSize = 16.sp) },
            supportingContent = { Text("Health Connect needs to be installed or updated first. Opens the Play Store.", fontSize = 13.sp) },
            modifier = Modifier.clickable(role = Role.Button, onClick = { openPlayStore(context) }).heightIn(min = 56.dp),
            leadingContent = { Icon(HealthIcon, contentDescription = null, modifier = Modifier.size(24.dp)) },
            colors = rowColors(),
        )
        HealthAvailability.UNAVAILABLE -> ListItem(
            headlineContent = { Text(LABEL, fontSize = 16.sp) },
            supportingContent = { Text("Health Connect is not available on this phone.", fontSize = 13.sp) },
            modifier = Modifier.heightIn(min = 56.dp).alpha(0.5f),
            leadingContent = { Icon(HealthIcon, contentDescription = null, modifier = Modifier.size(24.dp)) },
            colors = rowColors(),
        )
    }

    val notice = health.notice
    if (notice != null) {
        Text(
            notice,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).semantics { liveRegion = LiveRegionMode.Polite },
            fontSize = 13.sp,
            color = EisenColors.MutedForeground,
        )
    }
}

@Composable
private fun rowColors() = ListItemDefaults.colors(
    containerColor = Color.Transparent,
    headlineColor = EisenColors.Foreground,
    leadingIconColor = EisenColors.Foreground,
    supportingColor = EisenColors.MutedForeground,
    trailingIconColor = EisenColors.MutedForeground,
)
