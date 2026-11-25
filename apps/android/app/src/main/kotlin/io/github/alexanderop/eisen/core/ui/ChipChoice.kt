package io.github.alexanderop.eisen.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * One of many, or none: [title] over a row of chips that wraps onto more lines, so every chip fits at
 * any font size. A tap on a chip chooses it; a tap on the lit one takes the choice back ([onSelect]
 * with null). TalkBack reads the title as a heading and each chip as selected or not. Each chip takes
 * touches on 48 dp, like every Material chip.
 */
@Composable
fun <T> ChipChoice(
    title: String,
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    onSelect: (T?) -> Unit,
    modifier: Modifier = Modifier,
    centered: Boolean = false,
) {
    val haptics = rememberHaptics()
    val colors = FilterChipDefaults.filterChipColors(
        containerColor = EisenColors.Muted,
        labelColor = EisenColors.Foreground,
        selectedContainerColor = EisenColors.Primary,
        selectedLabelColor = EisenColors.OnPrimary,
    )
    Column(modifier.fillMaxWidth(), horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start) {
        Text(
            title,
            Modifier.semantics { heading() },
            color = EisenColors.MutedForeground,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        )
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, if (centered) Alignment.CenterHorizontally else Alignment.Start),
        ) {
            for (option in options) {
                val lit = option == selected
                FilterChip(
                    selected = lit,
                    onClick = {
                        onSelect(if (lit) null else option)
                        haptics.toggle(!lit)
                    },
                    label = { Text(label(option), fontWeight = FontWeight.SemiBold, fontSize = 15.sp) },
                    // A minimum, not a height: a fixed one would cut the 48 dp Material gives a chip to take touches on.
                    modifier = Modifier.heightIn(min = 40.dp).semantics { this.selected = lit },
                    shape = CircleShape,
                    colors = colors,
                    border = null,
                )
            }
        }
    }
}
