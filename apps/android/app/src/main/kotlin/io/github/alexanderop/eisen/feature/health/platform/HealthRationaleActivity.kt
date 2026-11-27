package io.github.alexanderop.eisen.feature.health.platform

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alexanderop.eisen.core.ui.EisenColors
import io.github.alexanderop.eisen.core.ui.EisenTheme

private val PARAGRAPHS = listOf(
    "Eisen writes each finished workout to Health Connect as a strength training session, each food you log as " +
        "nutrition and each weight you log as your weight, so they show up in apps like Google Fit, Samsung Health or Garmin Connect.",
    "What is written: when the workout started and ended, its name, and the exercises with their weight, " +
        "sets and reps. Editing or deleting a workout in History changes or removes it in Health Connect too.",
    "For food: its name, calories, protein, carbs and fat, on the day you logged it for. Changing or deleting an " +
        "entry changes or removes it in Health Connect too.",
    "For weight: the weight in kilograms, on the day you logged it for. Changing or deleting it changes or removes it " +
        "in Health Connect too.",
    "What is read: nothing. Eisen only asks to write exercise sessions, nutrition and weight.",
    "Nothing leaves your phone except through Health Connect. Eisen has no account and no server. " +
        "Which apps can read your workouts is up to you, in the Health Connect settings.",
    "Turn sync off any time in Eisen's settings.",
)

/**
 * The privacy screen Health Connect opens from its permission dialog and its app settings
 * (the "privacy policy" link). Health Connect requires an app to have one before it grants anything.
 */
class HealthRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bars = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        setContent {
            EisenTheme {
                Column(
                    Modifier.fillMaxSize().background(EisenColors.Background).safeDrawingPadding()
                        .verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        "Eisen and Health Connect",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = EisenColors.Foreground,
                        modifier = Modifier.semantics { heading() },
                    )
                    for (paragraph in PARAGRAPHS) Text(paragraph, fontSize = 16.sp, color = EisenColors.Foreground)
                    Button(onClick = ::finish, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Text("Done", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}
