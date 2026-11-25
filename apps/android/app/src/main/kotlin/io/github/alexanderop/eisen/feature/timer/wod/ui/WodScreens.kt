package io.github.alexanderop.eisen.feature.timer.wod.ui

import io.github.alexanderop.eisen.app.elm.schemeNote
import io.github.alexanderop.eisen.app.elm.schemeChip
import io.github.alexanderop.eisen.core.counted
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import io.github.alexanderop.eisen.feature.timer.wod.wodName
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.layout
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import io.github.alexanderop.eisen.app.elm.WOD_EXERCISES
import io.github.alexanderop.eisen.feature.workout.ui.WOD_FILTER
import io.github.alexanderop.eisen.app.elm.maxHint
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alexanderop.eisen.core.Equipment
import io.github.alexanderop.eisen.core.Exercise
import io.github.alexanderop.eisen.feature.timer.wod.FindExercise
import io.github.alexanderop.eisen.feature.timer.MAX_TIMER_ROUNDS
import io.github.alexanderop.eisen.feature.timer.wod.MAX_WOD_ITEMS
import io.github.alexanderop.eisen.feature.timer.wod.MAX_WOD_ROUNDS
import io.github.alexanderop.eisen.feature.timer.wod.SCHEMES
import io.github.alexanderop.eisen.feature.timer.TIMER_MODES
import io.github.alexanderop.eisen.feature.timer.WOD_MODES
import io.github.alexanderop.eisen.feature.timer.TimerConfig
import io.github.alexanderop.eisen.feature.timer.TimerMode
import io.github.alexanderop.eisen.feature.timer.TimerSession
import io.github.alexanderop.eisen.core.WeightUnit
import io.github.alexanderop.eisen.feature.timer.wod.Wod
import io.github.alexanderop.eisen.feature.timer.wod.WodItem
import io.github.alexanderop.eisen.feature.timer.wod.WodUnit
import io.github.alexanderop.eisen.feature.timer.wod.bestText
import io.github.alexanderop.eisen.feature.timer.wod.distanceText
import io.github.alexanderop.eisen.core.formatNumber
import io.github.alexanderop.eisen.core.holdText
import io.github.alexanderop.eisen.feature.timer.wod.isLibraryWod
import io.github.alexanderop.eisen.feature.timer.wod.loadFrom
import io.github.alexanderop.eisen.feature.timer.wod.loadIn
import io.github.alexanderop.eisen.feature.timer.timerSummary
import io.github.alexanderop.eisen.feature.timer.wod.wod
import io.github.alexanderop.eisen.feature.timer.wod.wodBest
import io.github.alexanderop.eisen.feature.timer.wod.wodFormat
import io.github.alexanderop.eisen.feature.timer.wod.wodImages
import io.github.alexanderop.eisen.feature.timer.wod.wodLines
import io.github.alexanderop.eisen.app.elm.AddTarget
import io.github.alexanderop.eisen.app.elm.Model
import io.github.alexanderop.eisen.app.elm.Msg
import io.github.alexanderop.eisen.app.elm.Builder
import io.github.alexanderop.eisen.app.elm.WodDraft
import io.github.alexanderop.eisen.app.elm.SchemeChoice
import io.github.alexanderop.eisen.app.elm.customText
import io.github.alexanderop.eisen.app.elm.pending
import io.github.alexanderop.eisen.app.elm.WodFilter
import io.github.alexanderop.eisen.app.elm.benchmarks
import io.github.alexanderop.eisen.app.elm.callsSummary
import io.github.alexanderop.eisen.app.elm.draftEditsOwn
import io.github.alexanderop.eisen.app.elm.exercises
import io.github.alexanderop.eisen.app.elm.lastWodRun
import io.github.alexanderop.eisen.app.elm.timerCapSeconds
import io.github.alexanderop.eisen.app.elm.timerWheelSeconds
import io.github.alexanderop.eisen.app.elm.withMax
import io.github.alexanderop.eisen.app.elm.withUnit
import io.github.alexanderop.eisen.app.elm.wodAmounts
import io.github.alexanderop.eisen.app.elm.wodFind
import io.github.alexanderop.eisen.app.elm.wodItemSummary
import io.github.alexanderop.eisen.app.elm.wodLoads
import io.github.alexanderop.eisen.app.elm.wodRows
import io.github.alexanderop.eisen.core.ui.ChoiceGroup
import io.github.alexanderop.eisen.core.ui.EisenColors
import io.github.alexanderop.eisen.core.ui.pressShapes
import io.github.alexanderop.eisen.core.ui.ExerciseMotion
import io.github.alexanderop.eisen.feature.timer.wod.SuggestionKind
import io.github.alexanderop.eisen.feature.timer.wod.WodSuggestion
import io.github.alexanderop.eisen.app.elm.bannerMotions
import io.github.alexanderop.eisen.app.elm.searchedWods
import io.github.alexanderop.eisen.app.elm.ownWods
import io.github.alexanderop.eisen.app.elm.noMatchText
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.delay
import io.github.alexanderop.eisen.app.elm.searching
import io.github.alexanderop.eisen.app.elm.typedParts
import io.github.alexanderop.eisen.app.elm.wodSuggestions
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import io.github.alexanderop.eisen.core.ui.ExerciseTile
import io.github.alexanderop.eisen.core.ui.Icons
import io.github.alexanderop.eisen.core.ui.ReorderableColumnItem
import io.github.alexanderop.eisen.core.ui.Wheel
import io.github.alexanderop.eisen.core.ui.formatShortDate
import io.github.alexanderop.eisen.core.ui.keyboardPadding
import io.github.alexanderop.eisen.core.ui.pathIcon
import io.github.alexanderop.eisen.feature.workout.ui.ExercisePickerSheet
import io.github.alexanderop.eisen.core.ui.rememberColumnReorderState
import io.github.alexanderop.eisen.core.ui.rememberFullSheetState
import io.github.alexanderop.eisen.core.ui.reorderContainer
import io.github.alexanderop.eisen.core.ui.reorderHandle
import kotlinx.coroutines.launch
import io.github.alexanderop.eisen.feature.timer.ui.CardShape
import io.github.alexanderop.eisen.feature.timer.ui.SetupRow
import io.github.alexanderop.eisen.feature.timer.ui.StartIcon
import io.github.alexanderop.eisen.feature.timer.ui.VoiceIcon
import io.github.alexanderop.eisen.feature.timer.ui.VoiceSheet
import io.github.alexanderop.eisen.feature.timer.ui.WheelBox
import io.github.alexanderop.eisen.feature.timer.ui.spokenSeconds

/*
 * The WODs page of the Timer tab, a WOD's detail and the builder. What they show comes from the
 * model; which sheet is open stays here.
 */

private val RowShape = RoundedCornerShape(14.dp)

// Lucide's grip-vertical, as in the routine editor.
private val Grip = pathIcon(
    "GripVertical",
    "M7 5a2 2 0 1 0 4 0a2 2 0 1 0-4 0zM13 5a2 2 0 1 0 4 0a2 2 0 1 0-4 0z" +
        "M7 12a2 2 0 1 0 4 0a2 2 0 1 0-4 0zM13 12a2 2 0 1 0 4 0a2 2 0 1 0-4 0z" +
        "M7 19a2 2 0 1 0 4 0a2 2 0 1 0-4 0zM13 19a2 2 0 1 0 4 0a2 2 0 1 0-4 0z",
    filled = true,
)

private val CopyIcon = pathIcon("Copy", "M8 8h12v12H8z", "M4 16V4h12")

/** A row of chips that scrolls out to the screen's edges, past the padding of the page it sits in. */
private fun Modifier.bleed(by: androidx.compose.ui.unit.Dp): Modifier = layout { measurable, constraints ->
    val extra = (by * 2).roundToPx()
    val placeable = measurable.measure(constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra))
    layout(placeable.width - extra, placeable.height) { placeable.place(-by.roundToPx(), 0) }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(top = 8.dp).semantics { heading() }, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    containerColor = EisenColors.Muted,
    labelColor = EisenColors.MutedForeground,
    selectedContainerColor = EisenColors.Primary,
    selectedLabelColor = EisenColors.OnPrimary,
)

// The list

/**
 * The search field and the chips, pinned at the top, over a draft from before the process ended,
 * "New WOD", the lifter's own and the benchmarks, all through the chip. While something is typed the
 * page is what the search found, best first. [list] lives with the Timer tab, so a detail opened
 * from here comes back to the same place.
 */
@Composable
internal fun WodsPage(model: Model, list: LazyListState, dispatch: (Msg) -> Unit, heading: @Composable () -> Unit) {
    val find = wodFind(model)
    val unit = model.state.settings.unit
    val sessions = model.state.timerSessions
    val own = ownWods(model)
    val library = benchmarks(model)
    val searching = searching(model)
    val found = if (searching) searchedWods(model) else emptyList()
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    // A tap on the Timer tab while it shows goes back to the top, once per tap.
    var top by rememberSaveable { mutableIntStateOf(model.nav.timerTop) }
    LaunchedEffect(model.nav.timerTop) {
        if (model.nav.timerTop != top) {
            top = model.nav.timerTop
            list.animateScrollToItem(0)
        }
    }
    // Where the pinned chips and field end inside the page: the suggestions hang from there.
    var page by remember { mutableStateOf(0f) }
    var under by remember { mutableIntStateOf(0) }
    val wasSearching by rememberUpdatedState(searching)
    val pinned: @Composable () -> Unit = {
        Column(
            Modifier
                .fillMaxWidth()
                .background(EisenColors.Background)
                .padding(bottom = 8.dp)
                .onGloballyPositioned { under = (it.boundsInWindow().bottom - page).roundToInt() },
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // The chips above the field: the suggestions open under it and never cover them.
            WodFilters(model.wods.filter, dispatch)
            WodSearchField(
                query = model.wods.query,
                onQuery = { typed ->
                    // A search that starts shows its best match first, right under the field; typing on does not scroll.
                    val starting = !wasSearching && typed.isNotBlank()
                    dispatch(Msg.Wods.Search(typed))
                    if (starting) scope.launch { list.scrollToItem(SEARCH_ITEM) }
                },
                onFocus = { focused -> dispatch(if (focused) Msg.Wods.Search(model.wods.query) else Msg.Wods.CloseSuggestions) },
                onSearch = {
                    dispatch(Msg.Wods.CloseSuggestions)
                    focus.clearFocus()
                },
            )
        }
    }
    Box(Modifier.fillMaxSize().onGloballyPositioned { page = it.boundsInWindow().top }) {
        WodsList(model, list, searching, found, own, library, find, unit, sessions, dispatch, heading, pinned)
        val suggestions = wodSuggestions(model)
        if (suggestions.isNotEmpty()) {
            // A tap outside the list closes it and keeps the search. The field and the chips above it stay as they are.
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(top = with(LocalDensity.current) { under.toDp() })
                    .clickable(interactionSource = null, indication = null, onClickLabel = "Close suggestions") {
                        dispatch(Msg.Wods.CloseSuggestions)
                        focus.clearFocus()
                    },
            ) {
                SuggestionList(suggestions, model.wods.query) { suggestion ->
                    if (suggestion.kind == SuggestionKind.WOD) dispatch(Msg.Wods.Open(suggestion.id, suggested = true))
                    else {
                        dispatch(Msg.Wods.SearchMovement(suggestion.text))
                        scope.launch { list.scrollToItem(SEARCH_ITEM) }
                    }
                    focus.clearFocus()
                }
            }
        }
    }
}

/** The pinned field's place in the list: after the heading, and it stays at the top once that scrolled away. */
private const val SEARCH_ITEM = 1

/** How long typing rests before the page searches: every key is kept, the search follows a moment later. */
private const val SEARCH_DEBOUNCE_MS = 150L

/**
 * "Search WODs or movements", with a clear button once something is typed. The text lives here, so no
 * key is lost while the page searches; it goes to the model a moment after the typing rests, and
 * comes back from it only when the model changed it (cleared, or a movement picked).
 */
@Composable
private fun WodSearchField(query: String, onQuery: (String) -> Unit, onFocus: (Boolean) -> Unit, onSearch: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    var text by rememberSaveable { mutableStateOf(query) }
    var sent by rememberSaveable { mutableStateOf(query) }
    val send by rememberUpdatedState(onQuery)
    LaunchedEffect(query) {
        if (query != sent) {
            sent = query
            text = query
        }
    }
    LaunchedEffect(text) {
        if (text == sent) return@LaunchedEffect
        delay(SEARCH_DEBOUNCE_MS)
        sent = text
        send(text)
    }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { state ->
                if (state.isFocused != focused) {
                    focused = state.isFocused
                    onFocus(state.isFocused)
                }
            }
            .semantics { contentDescription = "Search WODs" },
        placeholder = { Text("Search WODs or movements", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Search, contentDescription = null) },
        trailingIcon = if (text.isNotEmpty()) {
            {
                IconButton(onClick = {
                    text = ""
                    sent = ""
                    onQuery("")
                }) { Icon(Icons.Close, contentDescription = "Clear search") }
            }
        } else {
            null
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        // A search is not a sentence: no capitals, and no autocorrect turning a typed "fr" into "for".
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
            // The search key searches what is typed now, without waiting.
            if (text != sent) {
                sent = text
                onQuery(text)
            }
            onSearch()
        }),
    )
}

/**
 * What to offer while typing, attached under the field and the chips over the page: a WOD opens its
 * detail, a movement becomes the search. The typed part of each is bold.
 */
@Composable
private fun SuggestionList(suggestions: List<WodSuggestion>, query: String, onPick: (WodSuggestion) -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .shadow(12.dp, shape)
            .clip(shape)
            .background(EisenColors.Card)
            .border(1.dp, EisenColors.Border, shape)
            .semantics { collectionInfo = CollectionInfo(rowCount = suggestions.size, columnCount = 1) },
    ) {
        suggestions.forEachIndexed { index, suggestion ->
            if (index > 0) HorizontalDivider(color = EisenColors.Border)
            val movement = suggestion.kind == SuggestionKind.MOVEMENT
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClickLabel = if (movement) "Search for it" else "Open") { onPick(suggestion) }
                    .heightIn(min = 52.dp)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (movement) {
                    Icon(Icons.Workouts, contentDescription = null, tint = EisenColors.MutedForeground, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                }
                val (before, typed, after) = typedParts(suggestion.text, query)
                Text(
                    buildAnnotatedString {
                        append(before)
                        withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, color = EisenColors.Foreground)) { append(typed) }
                        append(after)
                    },
                    Modifier.weight(1f),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Normal,
                    color = EisenColors.Foreground.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(10.dp))
                Text(suggestion.detail, fontSize = 14.sp, color = EisenColors.MutedForeground, maxLines = 1)
            }
        }
    }
}

@Composable
private fun WodsList(
    model: Model,
    list: LazyListState,
    searching: Boolean,
    found: List<Wod>,
    own: List<Wod>,
    library: List<Wod>,
    find: FindExercise,
    unit: WeightUnit,
    sessions: List<TimerSession>,
    dispatch: (Msg) -> Unit,
    heading: @Composable () -> Unit,
    pinned: @Composable () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        state = list,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "heading") { Box(Modifier.bleed(16.dp)) { heading() } }
        // Cards scroll under it, so it has the page's ground.
        stickyHeader(key = "search") { pinned() }
        if (searching) {
            if (found.isEmpty()) item(key = "none") { NoMatch(noMatchText(model), chip = model.wods.filter != WodFilter.ALL, dispatch) }
            items(found, key = { "found:" + it.id }) { wod ->
                WodCard(wod, find, unit, wodBest(sessions, wod.id), onOpen = { dispatch(Msg.Wods.Open(wod.id)) }, onStart = { dispatch(Msg.Wods.Start(wod.id)) })
            }
            return@LazyColumn
        }
        model.wods.pending?.let { pending ->
            item(key = "pending") { PendingDraft(wodName(pending.wod), dispatch) }
        }
        item(key = "new") {
            FilledTonalButton(
                onClick = { dispatch(Msg.Wods.New) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = EisenColors.Muted, contentColor = EisenColors.Foreground),
            ) {
                Icon(Icons.Plus, contentDescription = null, tint = EisenColors.Primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("New WOD", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            }
        }
        if (own.isNotEmpty()) {
            item(key = "own") { SectionTitle("My WODs") }
            items(own, key = { "own:" + it.id }) { wod ->
                WodCard(wod, find, unit, wodBest(sessions, wod.id), onOpen = { dispatch(Msg.Wods.Open(wod.id)) }, onStart = { dispatch(Msg.Wods.Start(wod.id)) })
            }
        }
        item(key = "benchmarks") { SectionTitle("Benchmarks") }
        items(library, key = { it.id }) { wod ->
            WodCard(wod, find, unit, wodBest(sessions, wod.id), onOpen = { dispatch(Msg.Wods.Open(wod.id)) }, onStart = { dispatch(Msg.Wods.Start(wod.id)) })
        }
    }
}

/** The chips, pinned over the search field: they filter the whole page, and what the search finds. */
@Composable
private fun WodFilters(selected: WodFilter, dispatch: (Msg) -> Unit) {
    LazyRow(Modifier.bleed(16.dp), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(WodFilter.entries, key = { it.name }) { filter ->
            FilterChip(
                selected = selected == filter,
                onClick = { dispatch(Msg.Wods.Filter(filter)) },
                label = { Text(filter.label, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) },
                modifier = Modifier.height(40.dp),
                shape = CircleShape,
                colors = chipColors(),
                border = null,
            )
        }
    }
}

/** Nothing the search could find, with the ways back: every chip ("Show all", the text stays) and every WOD. */
@Composable
private fun NoMatch(text: String, chip: Boolean, dispatch: (Msg) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = EisenColors.MutedForeground, textAlign = TextAlign.Center)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (chip) SecondaryButton("Show all", Icons.ChevronDown, Modifier) { dispatch(Msg.Wods.Filter(WodFilter.ALL)) }
            SecondaryButton("Clear search", Icons.Close, Modifier) { dispatch(Msg.Wods.ClearSearch) }
        }
    }
}

/** The draft the builder had when the process ended: nothing typed is lost. */
@Composable
private fun PendingDraft(name: String, dispatch: (Msg) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(CardShape).background(EisenColors.Card).border(1.dp, EisenColors.Primary.copy(alpha = 0.6f), CardShape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Continue editing $name", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { dispatch(Msg.Wods.ContinueDraft) },
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RowShape,
            ) { Text("Continue", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp) }
            FilledTonalButton(
                onClick = { dispatch(Msg.Wods.DiscardDraft) },
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RowShape,
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = EisenColors.Muted, contentColor = EisenColors.Destructive),
            ) { Text("Discard", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        }
    }
}

/**
 * A WOD as a card: the banner of its movements across the top (or, without any strip, its first
 * movement's picture beside the text), name, format and movements, and its best run. The whole card
 * opens the detail, the play button starts it at once. Without [onOpen] it is the builder's preview,
 * without [onStart] it has no play button.
 */
@Composable
internal fun WodCard(wod: Wod, find: FindExercise, unit: WeightUnit, best: TimerSession?, onOpen: (() -> Unit)?, onStart: (() -> Unit)?) {
    val colors = CardDefaults.cardColors(containerColor = EisenColors.Card, contentColor = EisenColors.Foreground)
    val border = BorderStroke(1.dp, EisenColors.Border)
    val banner = bannerMotions(wod).isNotEmpty()
    val content: @Composable () -> Unit = {
        if (banner) WodBanner(wod, height = 64.dp, find = find)
        Row(
            Modifier.fillMaxWidth().heightIn(min = if (banner) 0.dp else 88.dp).padding(
                start = if (banner) 16.dp else 12.dp,
                end = 10.dp,
                top = if (banner) 8.dp else 12.dp,
                bottom = if (banner) 10.dp else 12.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!banner) {
                val first = wod.items.firstNotNullOfOrNull { find(it.exerciseId) }
                ExerciseTile(first?.image, first?.name ?: "?", size = 56.dp)
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(wodName(wod), fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (wodFormat(wod) != wodName(wod)) {
                    Text(wodFormat(wod), fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold, color = EisenColors.Primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    wodLines(wod, find, unit).joinToString(" · ").ifEmpty { "No movements yet" },
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = EisenColors.MutedForeground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (best != null) Text("Best ${bestText(best)}", fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold, color = EisenColors.MutedForeground)
            }
            if (onStart != null) {
                Spacer(Modifier.width(8.dp))
                // Straight into the lead-in; the rest of the card opens the detail.
                FilledIconButton(
                    onClick = onStart,
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = EisenColors.Primary, contentColor = EisenColors.OnPrimary),
                ) { Icon(StartIcon, contentDescription = "Start ${wodName(wod)}", modifier = Modifier.size(20.dp)) }
            }
        }
    }
    if (onOpen != null) {
        Card(onClick = onOpen, modifier = Modifier.fillMaxWidth(), shape = CardShape, colors = colors, border = border) { content() }
    } else {
        Card(modifier = Modifier.fillMaxWidth().clearAndSetSemantics { }, shape = CardShape, colors = colors, border = border) { content() }
    }
}

// The detail

/** One column of the Best and Last block: the number big, its day under it. "Nov 12" reads at a glance. */
@Composable
private fun RunColumn(label: String, session: TimerSession, strong: Boolean, modifier: Modifier) {
    Column(modifier.clip(RowShape).background(EisenColors.Card).border(1.dp, EisenColors.Border, RowShape).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(label.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = EisenColors.MutedForeground)
        Text(bestText(session), fontSize = 24.sp, fontWeight = FontWeight.Black, color = if (strong) EisenColors.Primary else EisenColors.Foreground)
        Text(formatShortDate(session.startedAt), fontSize = 14.sp, color = EisenColors.MutedForeground)
    }
}

@Composable
internal fun WodDetail(model: Model, wod: Wod, dispatch: (Msg) -> Unit) {
    val find = wodFind(model)
    val unit = model.state.settings.unit
    val sessions = model.state.timerSessions
    val calls = model.state.settings.timerCalls
    val best = wodBest(sessions, wod.id)
    val last = lastWodRun(sessions, wod.id)
    var voiceOpen by rememberSaveable { mutableStateOf(false) }
    // The row whose movement shows large.
    var shown by rememberSaveable { mutableStateOf<Int?>(null) }
    val rows = wodRows(wod, find, unit)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { dispatch(Msg.Wods.Close) }) { Icon(Icons.Back, contentDescription = "Back to the WODs") }
            Text(
                wodName(wod),
                Modifier.weight(1f).semantics { heading() },
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (bannerMotions(wod).isNotEmpty()) WodBanner(wod, height = 140.dp, find = find, modifier = Modifier.clip(CardShape), playing = true)
            // The format once: under a name, or as the title of an unnamed WOD.
            Column {
                if (wodFormat(wod) != wodName(wod)) {
                    Text(wodFormat(wod), fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Black, color = EisenColors.Primary)
                }
                Text(timerSummary(wod.timer), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = EisenColors.MutedForeground)
            }
            Column(
                Modifier.fillMaxWidth().clip(CardShape).background(EisenColors.Card).border(1.dp, EisenColors.Border, CardShape),
            ) {
                rows.forEachIndexed { index, (exercise, line) ->
                    if (index > 0) HorizontalDivider(color = EisenColors.Border, modifier = Modifier.padding(start = 84.dp))
                    // The whole row opens the movement large, so TalkBack reads its line as the button's name.
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button, onClickLabel = "Show the movement") { shown = index }
                            .heightIn(min = 76.dp)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ExerciseMotion(exercise, size = 56.dp)
                        Spacer(Modifier.width(14.dp))
                        Text(line, fontSize = 18.sp, fontWeight = FontWeight.Bold, lineHeight = 23.sp)
                    }
                }
                if (rows.isEmpty()) Text("No movements", Modifier.padding(16.dp), color = EisenColors.MutedForeground)
            }
            if (best != null || last != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (best != null) RunColumn("Best", best, strong = true, modifier = Modifier.weight(1f))
                    if (last != null) RunColumn("Last", last, strong = false, modifier = Modifier.weight(1f))
                }
            }
            SetupRow(VoiceIcon, "Voice", callsSummary(calls)) { voiceOpen = true }
            if (isLibraryWod(wod.id)) {
                SecondaryButton("Edit a copy", CopyIcon, Modifier.fillMaxWidth()) { dispatch(Msg.Wods.Copy(wod.id)) }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("Edit", Icons.Pencil, Modifier.weight(1f)) { dispatch(Msg.Wods.Edit(wod.id)) }
                    // Gone at once, with Undo in the snackbar, like a saved timer.
                    SecondaryButton("Delete", Icons.Trash, Modifier.weight(1f), destructive = true) { dispatch(Msg.Wods.Delete(wod.id)) }
                }
            }
        }
        Button(
            onClick = { dispatch(Msg.Wods.Start(wod.id)) },
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(64.dp),
            shapes = pressShapes(16.dp),
        ) {
            Icon(StartIcon, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text("Start", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
    if (voiceOpen) VoiceSheet(calls, dispatch, onClose = { voiceOpen = false })
    val open = shown
    val row = open?.let { rows.getOrNull(it) }
    if (row != null) MotionSheet(row.first, row.second, onClose = { shown = null })
    else if (open != null) SideEffect { shown = null }
}

/** One movement of a WOD large: the figure doing it, its name and its line ("21 × Thruster 43kg"). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MotionSheet(exercise: Exercise, line: String, onClose: () -> Unit) {
    val sheet = rememberFullSheetState()
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheet, containerColor = EisenColors.Card) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ExerciseMotion(exercise, size = 240.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(exercise.name, Modifier.semantics { heading() }, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                // Under a scheme the line is the name alone.
                if (line != exercise.name) {
                    Text(line, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = EisenColors.MutedForeground, textAlign = TextAlign.Center)
                }
            }
            Button(
                onClick = { scope.launch { sheet.hide() }.invokeOnCompletion { onClose() } },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shapes = pressShapes(16.dp),
            ) { Text("Done", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp) }
        }
    }
}

@Composable
private fun SecondaryButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RowShape,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = EisenColors.Muted,
            contentColor = if (destructive) EisenColors.Destructive else EisenColors.Foreground,
        ),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

// The builder

/** The number rows of each mode, and the wheel sheet each one opens. */
private enum class Number { ROUNDS, CAP, TIME, EVERY, WORK, REST, INTERVALS }

private fun schemeLabel(scheme: List<Int>): String = when (scheme) {
    SCHEMES[1] -> "10 down to 1"
    SCHEMES[3] -> "1 up to 10"
    else -> scheme.joinToString("-")
}

@Composable
internal fun WodBuilder(model: Model, draft: WodDraft, dispatch: (Msg) -> Unit) {
    val wod = draft.wod
    val find = wodFind(model)
    val unit = model.state.settings.unit
    val mode = wod.timer.mode
    var picking by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf<Int?>(null) }
    var number by rememberSaveable { mutableStateOf<Number?>(null) }
    // The picker's search and chip stay while the builder is open.
    val pickerQuery = rememberSaveable { mutableStateOf("") }
    val pickerFilter = rememberSaveable { mutableStateOf(WOD_FILTER) }
    val focus = LocalFocusManager.current
    val scroll = rememberScrollState()
    // One exercise can be in a WOD twice, so the items are known by their place.
    val reorder = rememberColumnReorderState(
        keys = wod.items.indices.toList(),
        onMove = { from, to ->
            dispatch(Msg.Wods.MoveItem(from, to))
            editing = null
        },
        scrollState = scroll,
    )
    fun timer(change: (TimerConfig) -> TimerConfig) = dispatch(Msg.Wods.SetTimer(change(wod.timer)))

    Column(Modifier.fillMaxSize().keyboardPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { dispatch(Msg.Wods.Leave) }) { Icon(Icons.Back, contentDescription = "Leave the builder") }
            Text(
                if (draftEditsOwn(model, draft)) "Edit WOD" else "New WOD",
                Modifier.weight(1f).semantics { heading() },
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp,
            )
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().reorderContainer(reorder).verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = wod.name,
                onValueChange = { dispatch(Msg.Wods.Rename(it)) },
                placeholder = { Text("Name your WOD") },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                shape = MaterialTheme.shapes.small,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                modifier = Modifier.fillMaxWidth(),
            )
            ChoiceGroup(WOD_MODES, mode, { it.title }, { dispatch(Msg.Wods.SetMode(it)) }, fontSize = 14.sp)
            when (mode) {
                TimerMode.FOR_TIME -> {
                    val schemed = wod.scheme.isNotEmpty()
                    // The lit chip is what Save saves; the field shows while custom reps are typed.
                    val choice = schemeChip(draft)
                    val customText = customText(draft)
                    val note = schemeNote(draft)
                    if (schemed) ValueRow("Scheme", wod.scheme.joinToString("-"), enabled = false) {}
                    else ValueRow("Rounds", wod.rounds.toString()) { number = Number.ROUNDS }
                    Text("Reps per round", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = EisenColors.MutedForeground, modifier = Modifier.padding(bottom = 0.dp))
                    LazyRow(Modifier.bleed(16.dp), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            SchemeChip("Same each round", choice == SchemeChoice.Same) { dispatch(Msg.Wods.SetScheme(emptyList())) }
                        }
                        items(SCHEMES) { scheme ->
                            SchemeChip(schemeLabel(scheme), choice == SchemeChoice.Preset(scheme)) { dispatch(Msg.Wods.SetScheme(scheme)) }
                        }
                        item {
                            SchemeChip("Custom", choice is SchemeChoice.Custom) { dispatch(Msg.Wods.ChooseCustom) }
                        }
                    }
                    if (customText != null) {
                        OutlinedTextField(
                            value = customText,
                            onValueChange = { dispatch(Msg.Wods.TypeScheme(it)) },
                            label = { Text("Reps per round") },
                            placeholder = { Text("30-20-10") },
                            // What Save saves, when it is not what the field holds: the two always agree on screen.
                            supportingText = note?.let { { Text(it) } },
                            isError = note != null,
                            singleLine = true,
                            shape = MaterialTheme.shapes.small,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    ValueRow("Time cap", if (wod.timer.work == 0) "Off" else holdText(wod.timer.work)) { number = Number.CAP }
                }
                TimerMode.AMRAP -> ValueRow("Time", holdText(wod.timer.work)) { number = Number.TIME }
                TimerMode.EMOM -> {
                    ValueRow("Every", holdText(wod.timer.work)) { number = Number.EVERY }
                    ValueRow("Rounds", wod.timer.rounds.toString()) { number = Number.INTERVALS }
                }
                TimerMode.TABATA -> {
                    ValueRow("Work", holdText(wod.timer.work)) { number = Number.WORK }
                    ValueRow("Rest", holdText(wod.timer.rest)) { number = Number.REST }
                    ValueRow("Rounds", wod.timer.rounds.toString()) { number = Number.INTERVALS }
                }
                // Not one of the WOD modes: the builder never offers it.
                TimerMode.BOXING -> Unit
            }

            SectionTitle("Movements")
            wod.items.forEachIndexed { index, item ->
                key(index) {
                    ReorderableColumnItem(reorder, key = index, shape = RowShape) {
                        // The drag without a gesture, for TalkBack, like the routine editor.
                        val moves = buildList {
                            if (index > 0) add(CustomAccessibilityAction("Move up") { dispatch(Msg.Wods.MoveItem(index, index - 1)); true })
                            if (index < wod.items.lastIndex) add(CustomAccessibilityAction("Move down") { dispatch(Msg.Wods.MoveItem(index, index + 1)); true })
                        }
                        ItemRow(
                            item = item,
                            exercise = find(item.exerciseId),
                            summary = wodItemSummary(item, wod, unit),
                            handle = Modifier.reorderHandle(reorder, index, enabled = wod.items.size > 1),
                            canMove = wod.items.size > 1,
                            moves = moves,
                        ) { editing = index }
                    }
                }
            }
            AddMovement(enabled = wod.items.size < MAX_WOD_ITEMS) { picking = true }

            SectionTitle("Preview")
            WodCard(wod(wod), find, unit, wodBest(model.state.timerSessions, wod.id), onOpen = null, onStart = null)
        }
        Button(
            onClick = { dispatch(Msg.Wods.Save) },
            enabled = wod.items.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(64.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(if (wod.items.isEmpty()) "Add a movement to save" else "Save", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        }
    }

    when (number) {
        Number.ROUNDS -> WheelSheet("Rounds", (1..MAX_WOD_ROUNDS).toList(), wod.rounds, { it.toString() }, { counted(it, "round") }, onClose = { number = null }) {
            dispatch(Msg.Wods.SetRounds(it))
        }
        Number.CAP -> WheelSheet("Time cap", timerCapSeconds(wod.timer.work), wod.timer.work, { if (it == 0) "Off" else holdText(it) }, { spokenSeconds(it, "Off") }, onClose = { number = null }) { v ->
            timer { it.copy(work = v) }
        }
        Number.TIME -> WheelSheet("Time", timerWheelSeconds(30, 3600, wod.timer.work), wod.timer.work, ::holdText, { spokenSeconds(it, null) }, onClose = { number = null }) { v ->
            timer { it.copy(work = v) }
        }
        Number.EVERY -> WheelSheet("Every", timerWheelSeconds(10, 600, wod.timer.work), wod.timer.work, ::holdText, { spokenSeconds(it, null) }, onClose = { number = null }) { v ->
            timer { it.copy(work = v) }
        }
        Number.WORK -> WheelSheet("Work", timerWheelSeconds(5, 600, wod.timer.work), wod.timer.work, ::holdText, { spokenSeconds(it, null) }, onClose = { number = null }) { v ->
            timer { it.copy(work = v) }
        }
        Number.REST -> WheelSheet("Rest", timerWheelSeconds(5, 300, wod.timer.rest), wod.timer.rest, ::holdText, { spokenSeconds(it, null) }, onClose = { number = null }) { v ->
            timer { it.copy(rest = v) }
        }
        Number.INTERVALS -> WheelSheet("Rounds", (1..MAX_TIMER_ROUNDS).toList(), wod.timer.rounds, { it.toString() }, { counted(it, "round") }, onClose = { number = null }) { v ->
            timer { it.copy(rounds = v) }
        }
        null -> Unit
    }

    val open = editing
    val item = open?.let { wod.items.getOrNull(it) }
    if (open != null && item != null) {
        ItemSheet(
            item = item,
            exercise = find(item.exerciseId),
            byScheme = wod.scheme.isNotEmpty() && item.unit == WodUnit.REPS,
            tabata = mode == TimerMode.TABATA,
            unit = unit,
            onChange = { dispatch(Msg.Wods.UpdateItem(open, it)) },
            onRemove = {
                dispatch(Msg.Wods.RemoveItem(open, item.exerciseId))
                editing = null
            },
            onClose = { editing = null },
        )
    } else if (open != null) {
        // Removed meanwhile, or the WOD was already full.
        SideEffect { editing = null }
    }

    if (picking) {
        // Several at once, in the order they were ticked. Their rows are there to tap afterwards.
        ExercisePickerSheet(
            exercises = exercises(model),
            exclude = emptySet(),
            onPick = { id -> dispatch(Msg.Wods.AddItem(id)) },
            onDismiss = { picking = false },
            onCreate = { name, equipment, timed ->
                dispatch(Msg.Exercises.AddCustom(name, equipment, addTo = AddTarget.Wod, timed = timed))
                picking = false
            },
            onDelete = { id -> dispatch(Msg.Exercises.DeleteCustom(id)) },
            multi = true,
            onPickMany = { ids ->
                dispatch(Msg.Wods.AddItems(ids))
                picking = false
            },
            wodIds = WOD_EXERCISES,
            query = pickerQuery,
            filter = pickerFilter,
        )
    }

    if (model.wods.builder is Builder.Asking) {
        AlertDialog(
            onDismissRequest = { dispatch(Msg.Wods.KeepEditing) },
            title = { Text("Discard changes?") },
            text = { Text("What you changed in this WOD is not saved.") },
            confirmButton = {
                TextButton(
                    onClick = { dispatch(Msg.Wods.Discard) },
                    colors = ButtonDefaults.textButtonColors(contentColor = EisenColors.Destructive),
                ) { Text("Discard") }
            },
            dismissButton = { TextButton(onClick = { dispatch(Msg.Wods.KeepEditing) }) { Text("Keep editing") } },
        )
    }
}

@Composable
private fun SchemeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) },
        modifier = Modifier.height(40.dp),
        shape = CircleShape,
        colors = chipColors(),
        border = null,
    )
}

/** A number of the WOD: its name, its value, and a tap opens its wheel. */
@Composable
private fun ValueRow(label: String, value: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RowShape)
            .background(EisenColors.Card)
            .border(1.dp, EisenColors.Border, RowShape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Change $label", onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = EisenColors.MutedForeground)
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = if (enabled) EisenColors.Foreground else EisenColors.Primary)
        if (enabled) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.ChevronRight, contentDescription = null, tint = EisenColors.MutedForeground, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ItemRow(item: WodItem, exercise: Exercise?, summary: String, handle: Modifier, canMove: Boolean, moves: List<CustomAccessibilityAction>, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RowShape)
            .background(EisenColors.Card)
            .border(1.dp, EisenColors.Border, RowShape)
            .clickable(role = Role.Button, onClickLabel = "Change", onClick = onClick)
            .semantics { customActions = moves }
            .heightIn(min = 68.dp)
            .padding(end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A drag that starts here moves the row at once; a tap counts as a tap on the row.
        Box(Modifier.size(48.dp).then(handle), contentAlignment = Alignment.Center) {
            Icon(Grip, contentDescription = null, tint = if (canMove) EisenColors.MutedForeground else EisenColors.Border, modifier = Modifier.size(20.dp))
        }
        ExerciseTile(exercise?.image, exercise?.name ?: "?")
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(exercise?.name ?: "Deleted exercise", fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(summary, fontSize = 14.sp, color = if (item.amount == 0) EisenColors.Primary else EisenColors.MutedForeground)
        }
        Icon(Icons.ChevronRight, contentDescription = null, tint = EisenColors.MutedForeground, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun AddMovement(enabled: Boolean, onClick: () -> Unit) {
    val tone = if (enabled) EisenColors.Primary else EisenColors.Border
    Row(
        Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RowShape)
            .drawBehind {
                val width = 1.5.dp.toPx()
                drawRoundRect(
                    color = tone,
                    topLeft = androidx.compose.ui.geometry.Offset(width / 2, width / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - width, size.height - width),
                    cornerRadius = CornerRadius(14.dp.toPx()),
                    style = Stroke(width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx()))),
                )
            }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Plus, contentDescription = null, tint = tone, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(if (enabled) "Add movement" else "$MAX_WOD_ITEMS movements at most", color = tone, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
    }
}

/** One number of the WOD on a wheel. It applies as the wheel settles; "Done" closes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WheelSheet(
    title: String,
    choices: List<Int>,
    value: Int,
    label: (Int) -> String,
    spoken: (Int) -> String,
    onClose: () -> Unit,
    onChange: (Int) -> Unit,
) {
    val sheet = rememberFullSheetState()
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheet, containerColor = EisenColors.Card) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.semantics { heading() })
            Box(
                Modifier
                    .fillMaxWidth(0.6f)
                    .align(Alignment.CenterHorizontally)
                    .clip(RowShape)
                    .background(EisenColors.Background)
                    .border(1.dp, EisenColors.Border, RowShape)
                    .padding(horizontal = 6.dp, vertical = 8.dp),
            ) {
                Wheel(
                    labels = remember(choices) { choices.map(label) },
                    selected = choices.indexOf(value).coerceAtLeast(0),
                    onSettle = { onChange(choices[it]) },
                    label = title,
                    spoken = { spoken(choices[it]) },
                    rowHeight = 56.dp,
                )
            }
            Button(
                onClick = { scope.launch { sheet.hide() }.invokeOnCompletion { onClose() } },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shapes = pressShapes(16.dp),
            ) { Text("Done", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp) }
        }
    }
}

private fun amountLabel(unit: WodUnit, amount: Int): String = when (unit) {
    WodUnit.REPS -> amount.toString()
    WodUnit.SECONDS -> holdText(amount)
    WodUnit.METERS -> distanceText(amount)
}

private val UNIT_LABELS = listOf(WodUnit.REPS to "Reps", WodUnit.SECONDS to "Seconds", WodUnit.METERS to "Metres")

/**
 * One movement of the WOD: how much, counted how, and with what load. Every change applies at once.
 * [byScheme]: a scheme gives the reps of every round, so there is no amount to choose.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemSheet(
    item: WodItem,
    exercise: Exercise?,
    byScheme: Boolean,
    tabata: Boolean,
    unit: WeightUnit,
    onChange: (WodItem) -> Unit,
    onRemove: () -> Unit,
    onClose: () -> Unit,
) {
    // The load the sheet opened with stays on the wheel, so it can always be turned back to.
    val opened = remember { item.load }
    val sheet = rememberFullSheetState()
    val scope = rememberCoroutineScope()
    fun close(then: () -> Unit) {
        scope.launch { sheet.hide() }.invokeOnCompletion { then() }
    }
    val loaded = exercise != null && exercise.equipment != Equipment.BODYWEIGHT
    // Max only exists in a Tabata: as often as possible in each round.
    val max = tabata && item.amount == 0
    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheet, containerColor = EisenColors.Card) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExerciseMotion(exercise, size = 96.dp)
                Spacer(Modifier.width(14.dp))
                Text(
                    exercise?.name ?: "Deleted exercise",
                    Modifier.weight(1f).semantics { heading() },
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((option, label) in UNIT_LABELS) {
                    FilterChip(
                        selected = item.unit == option,
                        onClick = { onChange(withUnit(item, option)) },
                        label = { Text(label, Modifier.fillMaxWidth(), fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center) },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = MaterialTheme.shapes.small,
                        colors = chipColors(),
                        border = null,
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth(if (loaded) 1f else 0.6f).align(Alignment.CenterHorizontally),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val amountHeader = when (item.unit) {
                    WodUnit.REPS -> "Reps"
                    WodUnit.SECONDS -> "Time"
                    WodUnit.METERS -> "Distance"
                }
                WheelBox(amountHeader, Modifier.weight(1f)) {
                    if (max || byScheme) {
                        Box(Modifier.fillMaxWidth().height(48.dp * 3), contentAlignment = Alignment.Center) {
                            Text(if (byScheme) "By scheme" else "Max", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = EisenColors.Primary)
                        }
                    } else {
                        key(item.unit) {
                            val choices = wodAmounts(item.unit, item.amount)
                            Wheel(
                                labels = remember(choices) { choices.map { amountLabel(item.unit, it) } },
                                selected = choices.indexOf(item.amount).coerceAtLeast(0),
                                onSettle = { onChange(item.copy(amount = choices[it])) },
                                label = amountHeader,
                                spoken = { amountLabel(item.unit, choices[it]) },
                                rowHeight = 48.dp,
                            )
                        }
                    }
                }
                if (loaded) {
                    WheelBox("Load (${unit.label})", Modifier.weight(1f)) {
                        val choices = remember(unit) { wodLoads(exercise!!.equipment, unit, opened) }
                        val shown = loadIn(item.load, unit)
                        Wheel(
                            labels = remember(choices) { choices.map { if (it == 0.0) "None" else formatNumber(it) + unit.label } },
                            selected = choices.indexOf(shown).coerceAtLeast(0),
                            onSettle = { onChange(item.copy(load = loadFrom(choices[it], unit))) },
                            label = "Load",
                            spoken = { if (choices[it] == 0.0) "No load" else "${formatNumber(choices[it])} ${unit.label}" },
                            rowHeight = 48.dp,
                        )
                    }
                }
            }
            if (tabata) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RowShape)
                        .toggleable(value = max, role = Role.Switch) { onChange(withMax(item, it)) }
                        .heightIn(min = 56.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Max", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(maxHint(item.unit), fontSize = 13.sp, color = EisenColors.MutedForeground)
                    }
                    Switch(checked = max, onCheckedChange = null)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(
                    onClick = { close(onRemove) },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = EisenColors.Muted, contentColor = EisenColors.Destructive),
                ) {
                    Icon(Icons.Trash, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Remove", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
                Button(
                    onClick = { close(onClose) },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shapes = pressShapes(16.dp),
                ) { Text("Done", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp) }
            }
        }
    }
}
