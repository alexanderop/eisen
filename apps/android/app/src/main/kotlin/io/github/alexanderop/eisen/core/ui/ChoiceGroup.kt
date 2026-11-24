package io.github.alexanderop.eisen.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The smallest a label shrinks to before it would be cut. */
private val MIN_LABEL = 9.sp

/**
 * One choice out of a few, as a connected button group: the options share the width, the chosen one
 * is lime and round, and a pressed one squares its corners. TalkBack reads each option as a radio
 * button in a group, with its label (or [description]) as the handle. A long label shrinks a little instead of being cut.
 * Choosing ticks like a switch ([Haptic.TOGGLE_ON]); tapping the chosen one again does nothing.
 */
@Composable
fun <T> ChoiceGroup(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 15.sp,
    description: ((T) -> String)? = null,
) {
    val haptics = rememberHaptics()
    val colors = ToggleButtonDefaults.colors(
        containerColor = EisenColors.Muted,
        contentColor = EisenColors.Foreground,
        checkedContainerColor = EisenColors.Primary,
        checkedContentColor = EisenColors.OnPrimary,
    )
    val leading = ButtonGroupDefaults.connectedLeadingButtonShapes()
    val middle = ButtonGroupDefaults.connectedMiddleButtonShapes()
    val trailing = ButtonGroupDefaults.connectedTrailingButtonShapes()
    ButtonGroup(
        // Never shown: every option takes an equal share of the width, so all of them always fit.
        overflowIndicator = {},
        modifier = modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, option ->
            val checked = option == selected
            customItem(
                buttonGroupContent = {
                    ToggleButton(
                        checked = checked,
                        onCheckedChange = {
                            if (!checked) {
                                onSelect(option)
                                haptics.perform(Haptic.TOGGLE_ON)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            // A radio button of the group, not a checkbox: one of them is always chosen.
                            .semantics {
                                role = Role.RadioButton
                                this.selected = checked
                                if (description != null) contentDescription = description(option)
                            },
                        shapes = when (index) {
                            0 -> leading
                            options.lastIndex -> trailing
                            else -> middle
                        },
                        colors = colors,
                        contentPadding = PaddingValues(horizontal = 6.dp),
                    ) {
                        Text(
                            label(option),
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            // Never broken onto a second line ("For" over "Time"): it shrinks instead.
                            softWrap = false,
                            autoSize = TextAutoSize.StepBased(minFontSize = MIN_LABEL, maxFontSize = fontSize),
                        )
                    }
                },
                menuContent = {},
            )
        }
    }
}
