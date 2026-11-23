package io.github.alexanderop.eisen.ui.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import io.github.alexanderop.eisen.domain.Workout
import io.github.alexanderop.eisen.domain.recordIds
import io.github.alexanderop.eisen.domain.sharedUrl
import io.github.alexanderop.eisen.domain.toShared
import io.github.alexanderop.eisen.domain.workoutMarkdown
import io.github.alexanderop.eisen.ui.EisenColors
import io.github.alexanderop.eisen.ui.Icons
import io.github.alexanderop.eisen.ui.formatDate
import io.github.alexanderop.eisen.ui.pathIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import io.github.alexanderop.eisen.ui.rememberFullSheetState

/** Where the PWA lives. A shared link opens the workout there, so it works for people without the app. */
private const val APP_URL = "https://alexanderop.github.io/eisen/"

/** The address in the corner of the picture, as the PWA writes it: "alexanderop.github.io/eisen". */
private val SITE = APP_URL.replace(Regex("^https?://"), "").replace(Regex("/(index\\.html)?$"), "")

private val ImageIcon = pathIcon(
    "Image",
    "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z",
    "M7 9a2 2 0 1 0 4 0a2 2 0 1 0-4 0",
    "m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21",
)
private val LinkIcon = pathIcon(
    "Link",
    "M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71",
    "M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71",
)
private val TextIcon = pathIcon("Text", "M21 5H3", "M15 12H3", "M17 19H3")

private enum class Action { IMAGE, LINK, MARKDOWN }

/** The picture is drawn when the sheet opens. Null while it is being drawn. */
private sealed interface Picture {
    data object Failed : Picture
    class Ready(val bitmap: Bitmap) : Picture
}

/** Named after the day, in UTC like the PWA's `toISOString`: eisen-2026-11-21.png. */
private fun fileName(workout: Workout): String =
    "eisen-${Instant.ofEpochMilli(workout.startedAt).atOffset(ZoneOffset.UTC).toLocalDate()}.png"

private fun shareText(context: Context, text: String, title: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_TITLE, title)
    }
    context.startActivity(Intent.createChooser(send, null))
}

/** Writes the picture into the folder the FileProvider shares and opens the share sheet with it. */
private suspend fun sharePicture(context: Context, bitmap: Bitmap, workout: Workout) {
    val file = withContext(Dispatchers.IO) {
        val folder = File(context.cacheDir, "shares")
        // Only the newest picture is kept, so old ones do not pile up in the cache.
        folder.mkdirs()
        folder.listFiles()?.forEach { it.delete() }
        File(folder, fileName(workout)).apply {
            outputStream().use { if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) error("The picture could not be made.") }
        }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TITLE, workout.name)
        // Lets the share sheet show the picture and gives the receiving app the right to read it.
        clipData = ClipData.newRawUri(workout.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, null))
}

/**
 * Shares a finished workout three ways: as a picture, as a link that shows it, or as Markdown.
 * The web copies the link and the Markdown. A phone's share sheet sends them on in one step and can copy them too.
 * [history] is the saved history the workout is part of: it decides which of its weights were records.
 * Weights show in the workout's own unit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareWorkoutSheet(workout: Workout, history: List<Workout>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberFullSheetState()
    val close: () -> Unit = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } }

    val shared = remember(workout, history) {
        toShared(workout, recordIds(history, history.indexOfFirst { it.id == workout.id }))
    }
    val date = formatDate(workout.startedAt)

    var picture by remember { mutableStateOf<Picture?>(null) }
    LaunchedEffect(shared, date) {
        picture = null
        picture = runCatching { withContext(Dispatchers.Default) { drawWorkoutImage(shared, date, SITE) } }
            .fold(onSuccess = { Picture.Ready(it) }, onFailure = { Picture.Failed })
    }

    // What the last tapped action came to, shown on its row for a moment. Only failures: success is the share sheet itself.
    var result by remember { mutableStateOf<Pair<Action, String>?>(null) }
    var reports by remember { mutableIntStateOf(0) }
    LaunchedEffect(reports) {
        if (result == null) return@LaunchedEffect
        delay(2500)
        result = null
    }
    fun report(action: Action, text: String) {
        result = action to text
        reports++
    }
    fun describe(action: Action, idle: String): String = result?.takeIf { it.first == action }?.second ?: idle
    fun attempt(action: Action, share: suspend () -> Unit) {
        scope.launch {
            runCatching { share() }.onFailure { report(action, "Sharing failed") }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Share ${workout.name}",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            IconButton(onClick = close) { Icon(Icons.Close, contentDescription = "Close") }
        }
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Preview(picture, "${workout.name} on $date as a picture", Modifier.align(Alignment.CenterHorizontally).padding(horizontal = 16.dp))
            Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                val ready = picture as? Picture.Ready
                ShareRow(ImageIcon, "Picture", describe(Action.IMAGE, "A card to post or send"), enabled = ready != null) {
                    if (ready != null) attempt(Action.IMAGE) { sharePicture(context, ready.bitmap, workout) }
                }
                HorizontalDivider(color = EisenColors.Border)
                ShareRow(LinkIcon, "Link", describe(Action.LINK, "Opens this workout in the browser")) {
                    attempt(Action.LINK) { shareText(context, sharedUrl(APP_URL, shared), workout.name) }
                }
                HorizontalDivider(color = EisenColors.Border)
                ShareRow(TextIcon, "Markdown", describe(Action.MARKDOWN, "Text for a chat or a note")) {
                    attempt(Action.MARKDOWN) { shareText(context, workoutMarkdown(workout, date), workout.name) }
                }
            }
        }
    }
}

/** The picture as it will be sent, at most a bit under half the screen high. It keeps its place while it is drawn. */
@Composable
private fun Preview(picture: Picture?, description: String, modifier: Modifier) {
    val maxHeight = with(LocalDensity.current) { (LocalWindowInfo.current.containerSize.height * 0.46f).toDp() }
    val shape = RoundedCornerShape(18.dp)
    val frame = modifier.heightIn(max = maxHeight).clip(shape).border(BorderStroke(1.dp, EisenColors.Border), shape)
    when (picture) {
        is Picture.Ready -> Image(
            bitmap = remember(picture) { picture.bitmap.asImageBitmap() },
            contentDescription = description,
            contentScale = ContentScale.Fit,
            modifier = frame.aspectRatio(picture.bitmap.width.toFloat() / picture.bitmap.height, matchHeightConstraintsFirst = true),
        )
        null -> Box(frame.aspectRatio(4f / 5f, matchHeightConstraintsFirst = true).background(EisenColors.Card))
        Picture.Failed -> Unit
    }
}

@Composable
private fun ShareRow(icon: ImageVector, label: String, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label, fontSize = 16.sp) },
        modifier = Modifier
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp)
            .alpha(if (enabled) 1f else 0.5f),
        supportingContent = { Text(description, fontSize = 13.sp) },
        leadingContent = { Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp)) },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = EisenColors.Foreground,
            leadingIconColor = EisenColors.Foreground,
            supportingColor = EisenColors.MutedForeground,
        ),
    )
}
