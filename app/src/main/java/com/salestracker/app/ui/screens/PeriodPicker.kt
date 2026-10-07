package com.salestracker.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.salestracker.app.data.Period
import com.salestracker.app.data.localizedFormatter
import java.time.LocalDate

/** Tag used by the screenshot tests to open a period menu. */
const val PERIOD_PICKER_TAG = "periodPicker"

/**
 * One button showing the current choice; tapping it opens a short list to pick from.
 * Replaces rows of chips that had to be scrolled sideways.
 */
@Composable
fun <T> DropdownPicker(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    detail: @Composable (T) -> String? = { null },
    dividerBefore: (T) -> Boolean = { false },
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(
            onClick = { open = true },
            contentPadding = PaddingValues(start = 14.dp, end = 6.dp),
            modifier = Modifier.testTag(PERIOD_PICKER_TAG),
        ) {
            Icon(Icons.Filled.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label(selected), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o ->
                if (dividerBefore(o)) HorizontalDivider(Modifier.padding(vertical = 4.dp))
                val isSelected = o == selected
                val extra = detail(o)
                DropdownMenuItem(
                    text = { Text(label(o), fontWeight = if (isSelected) FontWeight.SemiBold else null) },
                    onClick = { onSelect(o); open = false },
                    leadingIcon = {
                        if (isSelected) Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        else Spacer(Modifier.size(24.dp))
                    },
                    trailingIcon = extra?.let { d ->
                        { Text(d, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    },
                )
            }
        }
    }
}

/** Period menu for the Stats and Sales tabs: quarters show their months, e.g. "Q2   Apr – Jun". */
@Composable
fun PeriodPicker(
    selected: Period,
    onSelect: (Period) -> Unit,
    modifier: Modifier = Modifier,
    options: List<Period> = Period.entries,
) {
    val monthFmt = localizedFormatter("MMM")
    val today = LocalDate.now()
    DropdownPicker(
        options = options,
        selected = selected,
        label = { stringResource(it.label) },
        onSelect = onSelect,
        modifier = modifier,
        detail = { p ->
            if (p in QUARTERS) p.range(today)?.let { (start, end) -> "${start.format(monthFmt)} – ${end.minusDays(1).format(monthFmt)}" } else null
        },
        dividerBefore = { (it == Period.Q1 || it == Period.YEAR) && it != options.first() },
    )
}

private val QUARTERS = setOf(Period.Q1, Period.Q2, Period.Q3, Period.Q4)
