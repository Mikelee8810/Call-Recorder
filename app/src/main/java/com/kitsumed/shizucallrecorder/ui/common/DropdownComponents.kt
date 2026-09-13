/*
 * ShizuCallRecorder: FOSS Call recording powered through ADB/Shizuku!
 *  Copyright (C) 2026-present kitsumed (Med)
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.kitsumed.shizucallrecorder.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kitsumed.shizucallrecorder.ui.theme.ShizuCallRecorderTheme

/**
 * A key/label/description/etc... data class used to populate dropdown menus.
 *
 * @property key         Machine-readable identifier, identifies the selection in the data layer persisted.
 * @property label       Human-readable text shown inside the dropdown field.
 * @property description Optional one-line description shown below the dropdown for the selected item.
 * @property enabled     Whether this item can be selected; `false` grays it out and disables clicks.
 */
data class OptionItem(
    val key: String,
    val label: String,
    val description: String? = null,
    val enabled: Boolean = true
)

/**
 * A settings row that opens a [ModalBottomSheet] to pick one of [options], instead of a Material
 * dropdown menu - a modal presentation for a secondary action, in keeping with the rest of the
 * design direction (see [com.kitsumed.shizucallrecorder.ui.screens.RecordingsScreen]'s sort sheet).
 *
 * @param label            Text label shown above the current value.
 * @param selected         The currently selected [OptionItem].
 * @param options          All available options shown in the sheet.
 * @param onOptionSelected Called with the chosen [OptionItem] when the user picks a new option.
 * @param modifier         Optional layout modifier forwarded to the root row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun M3DropdownField(
    label: String,
    selected: OptionItem,
    options: List<OptionItem>,
    onOptionSelected: (OptionItem) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    var showSheet by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .clickable { showSheet = true }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = selected.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )
                options.forEach { option ->
                    val isSelected = option.key == selected.key
                    ListItem(
                        headlineContent = {
                            Text(
                                option.label,
                                color = if (option.enabled) MaterialTheme.colorScheme.onSurface
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        },
                        supportingContent = option.description?.let { desc ->
                            { Text(desc, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        },
                        trailingContent = {
                            if (isSelected) {
                                Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable(enabled = option.enabled) {
                            onOptionSelected(option)
                            showSheet = false
                        }
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewM3DropdownField() {
    val options = listOf(
        OptionItem("opt1", "Standard Option", "Description for option 1"),
        OptionItem("opt2", "Another Option", "Description for option 2"),
        OptionItem("opt3", "Disabled Option", "This cannot be selected", enabled = false)
    )
    ShizuCallRecorderTheme(darkTheme = false) {
        Surface {
            M3DropdownField(
                label = "Select an option",
                selected = options[0],
                options = options,
                onOptionSelected = {},
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()
            )
        }
    }
}
