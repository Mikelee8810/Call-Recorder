/*
 * ShizuCallRecorder: FOSS Call recording powered through ADB/Shizuku!
 *  Copyright (C) 2026-present kitsumed (Med)
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.kitsumed.shizucallrecorder.ui.screens

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CallMade
import androidx.compose.material.icons.outlined.CallReceived
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kitsumed.shizucallrecorder.R
import com.kitsumed.shizucallrecorder.data.call.CallDirection
import com.kitsumed.shizucallrecorder.data.recordings.RecordingItem
import com.kitsumed.shizucallrecorder.ui.viewmodels.PLAYBACK_SPEEDS
import com.kitsumed.shizucallrecorder.ui.viewmodels.PlaybackState
import com.kitsumed.shizucallrecorder.ui.viewmodels.RecordingDateFilter
import com.kitsumed.shizucallrecorder.ui.viewmodels.RecordingDirectionFilter
import com.kitsumed.shizucallrecorder.ui.viewmodels.RecordingListItem
import com.kitsumed.shizucallrecorder.ui.viewmodels.RecordingSortOrder
import com.kitsumed.shizucallrecorder.ui.viewmodels.RecordingsFilterState
import com.kitsumed.shizucallrecorder.ui.viewmodels.RecordingsSelectionState
import com.kitsumed.shizucallrecorder.ui.viewmodels.RecordingsUiState
import com.kitsumed.shizucallrecorder.ui.viewmodels.RecordingsViewModel
import com.kitsumed.shizucallrecorder.ui.theme.CharcoalGround
import com.kitsumed.shizucallrecorder.ui.theme.CharcoalSurfaceHigh
import com.kitsumed.shizucallrecorder.ui.theme.EmberBright
import com.kitsumed.shizucallrecorder.ui.theme.OnEmberBright
import com.kitsumed.shizucallrecorder.ui.theme.RecordingRed
import com.kitsumed.shizucallrecorder.ui.theme.TextOnCharcoal
import com.kitsumed.shizucallrecorder.ui.theme.TextOnCharcoalMuted
import com.kitsumed.shizucallrecorder.utils.RecordingShareHelper
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Stateful entry point: the recordings library, now the app's home screen once onboarding is
 * complete. Wires [RecordingsViewModel] to [RecordingsContent].
 *
 * @param onOpenSettings Called when the user taps the gear icon; Settings lives one tap away
 *                        rather than being the landing screen, since browsing/playing back
 *                        recordings is the everyday task.
 */
@Composable
fun RecordingsScreen(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val shareChooserTitle = stringResource(R.string.recordings_share)
    val viewModel: RecordingsViewModel = viewModel()

    val uiState by viewModel.uiState.collectAsState()
    val filterState by viewModel.filterState.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val selectionState by viewModel.selectionState.collectAsState()

    // Initial load, and re-scan whenever the user comes back to the app (a new call may have
    // just finished recording, or the recordings folder may have changed in Settings).
    LaunchedEffect(Unit) { viewModel.refresh() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    DisposableEffect(Unit) { onDispose { viewModel.stopPlayback() } }

    RecordingsContent(
        uiState = uiState,
        filterState = filterState,
        playbackState = playbackState,
        selectionState = selectionState,
        onOpenSettings = onOpenSettings,
        onRetry = { viewModel.refresh() },
        onSearchQueryChange = viewModel::setSearchQuery,
        onDirectionFilterChange = viewModel::setDirectionFilter,
        onDateFilterChange = viewModel::setDateFilter,
        onSortOrderChange = viewModel::setSortOrder,
        onPlayOrToggle = viewModel::playOrToggle,
        onSeek = viewModel::seekTo,
        onSetSpeed = viewModel::setPlaybackSpeed,
        onToggleStar = viewModel::toggleStar,
        onShare = { item -> context.startActivity(RecordingShareHelper.buildShareChooser(context, item.uri, item.displayName)) },
        onStartSelection = viewModel::startSelection,
        onToggleSelected = viewModel::toggleSelected,
        onClearSelection = viewModel::clearSelection,
        onSelectAllVisible = viewModel::selectAllVisible,
        onBatchStar = viewModel::batchStarSelected,
        onBatchDelete = viewModel::batchDeleteSelected,
        onBatchShare = {
            val items = viewModel.getSelectedItemsForShare()
            if (items.isNotEmpty()) {
                val uris = ArrayList(items.map { it.uri })
                val mimeType = context.contentResolver.getType(items.first().uri) ?: "audio/*"
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).apply {
                    type = mimeType
                    putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, uris)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(android.content.Intent.createChooser(intent, shareChooserTitle))
            }
        },
        modifier = modifier
    )
}

/** Stateless visual layer for the recordings library. */
@Composable
fun RecordingsContent(
    uiState: RecordingsUiState,
    filterState: RecordingsFilterState,
    playbackState: PlaybackState,
    selectionState: RecordingsSelectionState,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onDirectionFilterChange: (RecordingDirectionFilter) -> Unit,
    onDateFilterChange: (RecordingDateFilter) -> Unit,
    onSortOrderChange: (RecordingSortOrder) -> Unit,
    onPlayOrToggle: (RecordingItem) -> Unit,
    onSeek: (Long) -> Unit,
    onSetSpeed: (Float) -> Unit,
    onToggleStar: (RecordingItem) -> Unit,
    onShare: (RecordingItem) -> Unit,
    onStartSelection: (RecordingItem) -> Unit,
    onToggleSelected: (RecordingItem) -> Unit,
    onClearSelection: () -> Unit,
    onSelectAllVisible: () -> Unit,
    onBatchStar: () -> Unit,
    onBatchDelete: () -> Unit,
    onBatchShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Surface(modifier = modifier.fillMaxSize(), color = Color.Transparent) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            if (selectionState.isSelectionMode) {
                SelectionTopBar(
                    selectedCount = selectionState.selectedPaths.size,
                    onClose = onClearSelection,
                    onSelectAll = onSelectAllVisible,
                    onShare = onBatchShare,
                    onStar = onBatchStar,
                    onDelete = { showDeleteConfirm = true }
                )
            } else {
                RecordingsTopBar(onOpenSettings = onOpenSettings)
            }

            if (uiState is RecordingsUiState.Success && !selectionState.isSelectionMode) {
                SearchAndFilterBar(
                    filterState = filterState,
                    onSearchQueryChange = onSearchQueryChange,
                    onDirectionFilterChange = onDirectionFilterChange,
                    onDateFilterChange = onDateFilterChange,
                    onSortOrderChange = onSortOrderChange
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                when (uiState) {
                    is RecordingsUiState.Loading -> LoadingState()
                    is RecordingsUiState.NoFolderSelected -> NoFolderState(onOpenSettings = onOpenSettings)
                    is RecordingsUiState.Error -> ErrorState(message = uiState.message, onRetry = onRetry)
                    is RecordingsUiState.Success -> {
                        if (uiState.visible.isEmpty()) {
                            if (uiState.all.isEmpty()) EmptyState() else EmptySearchState()
                        } else {
                            RecordingsList(
                                items = uiState.visible,
                                playbackState = playbackState,
                                selectionState = selectionState,
                                onPlayOrToggle = onPlayOrToggle,
                                onSeek = onSeek,
                                onSetSpeed = onSetSpeed,
                                onToggleStar = onToggleStar,
                                onShare = onShare,
                                onStartSelection = onStartSelection,
                                onToggleSelected = onToggleSelected
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.general_delete)) },
            text = { Text(stringResource(R.string.recordings_delete_confirm, selectionState.selectedPaths.size)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onBatchDelete()
                }) { Text(stringResource(R.string.general_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.general_cancel)) }
            }
        )
    }
}

// ── Top bars ────────────────────────────────────────────────────────────────────────────────

/**
 * The top bar is deliberately dark warm-charcoal "chrome" regardless of theme - the same
 * purposeful light-canvas-with-dark-chrome contrast used by apps like Linear/Notion - rather
 * than blending into the light background.
 */
@Composable
private fun RecordingsTopBar(onOpenSettings: () -> Unit) {
    Surface(color = CharcoalGround, contentColor = TextOnCharcoal) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.recordings_title),
                style = MaterialTheme.typography.displaySmall,
                color = TextOnCharcoal
            )
            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = stringResource(R.string.general_settings),
                    tint = TextOnCharcoal
                )
            }
        }
    }
}

@Composable
private fun SelectionTopBar(
    selectedCount: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onShare: () -> Unit,
    onStar: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(color = CharcoalGround, contentColor = TextOnCharcoal) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.general_close), tint = TextOnCharcoal)
            }
            Text(
                text = selectedCount.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = TextOnCharcoal,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onSelectAll) {
                Icon(Icons.Outlined.Check, contentDescription = stringResource(R.string.recordings_select_all), tint = TextOnCharcoal)
            }
            IconButton(onClick = onStar) {
                Icon(Icons.Default.Star, contentDescription = stringResource(R.string.recordings_star_add), tint = EmberBright)
            }
            IconButton(onClick = onShare) {
                Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.recordings_share), tint = TextOnCharcoal)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.general_delete), tint = RecordingRed)
            }
        }
    }
}

// ── Search, filter & sort ──────────────────────────────────────────────────────────────────

@Composable
private fun SearchAndFilterBar(
    filterState: RecordingsFilterState,
    onSearchQueryChange: (String) -> Unit,
    onDirectionFilterChange: (RecordingDirectionFilter) -> Unit,
    onDateFilterChange: (RecordingDateFilter) -> Unit,
    onSortOrderChange: (RecordingSortOrder) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        OutlinedTextField(
            value = filterState.query,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.recordings_search_hint)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            DirectionSegmentedControl(
                current = filterState.direction,
                onSelected = onDirectionFilterChange,
                modifier = Modifier.weight(1f)
            )

            SortMenuButton(current = filterState.sortOrder, onSortOrderChange = onSortOrderChange)
        }

        AnimatedVisibility(visible = true) {
            DateFilterRow(current = filterState.dateFilter, onDateFilterChange = onDateFilterChange)
        }
    }
}

/**
 * A pill-style segmented control (an iOS-style tab switcher) used for the direction filter
 * instead of a row of individually-toggled chips: a single track with one sliding highlight
 * makes clearer that exactly one option is active at a time.
 */
@Composable
private fun DirectionSegmentedControl(
    current: RecordingDirectionFilter,
    onSelected: (RecordingDirectionFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = listOf(
        RecordingDirectionFilter.ALL to stringResource(R.string.recordings_filter_all),
        RecordingDirectionFilter.INCOMING to stringResource(R.string.recordings_filter_incoming),
        RecordingDirectionFilter.OUTGOING to stringResource(R.string.recordings_filter_outgoing),
        RecordingDirectionFilter.STARRED to stringResource(R.string.recordings_filter_starred)
    )
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(3.dp),
    ) {
        options.forEach { (value, label) ->
            val selected = current == value
            val backgroundColor by androidx.compose.animation.animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                animationSpec = springAnim(),
                label = "segmentBg"
            )
            val contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(backgroundColor)
                    .combinedClickable(onClick = { onSelected(value) })
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun DateFilterRow(current: RecordingDateFilter, onDateFilterChange: (RecordingDateFilter) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 8.dp)
    ) {
        val options = listOf(
            RecordingDateFilter.ALL to stringResource(R.string.recordings_filter_all),
            RecordingDateFilter.LAST_7_DAYS to stringResource(R.string.recordings_date_7d),
            RecordingDateFilter.LAST_30_DAYS to stringResource(R.string.recordings_date_30d),
            RecordingDateFilter.LAST_90_DAYS to stringResource(R.string.recordings_date_90d)
        )
        options.forEach { (value, label) ->
            val selected = current == value
            AssistChip(
                onClick = { onDateFilterChange(value) },
                label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    labelColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

/**
 * Sort lives in a bottom sheet rather than a dropdown menu - a modal presentation for a
 * secondary action, in keeping with the rest of the design direction.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortMenuButton(current: RecordingSortOrder, onSortOrderChange: (RecordingSortOrder) -> Unit) {
    var showSheet by remember { mutableStateOf(false) }
    IconButton(onClick = { showSheet = true }) {
        Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = stringResource(R.string.recordings_sort))
    }

    if (showSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            val entries = listOf(
                RecordingSortOrder.NEWEST_FIRST to stringResource(R.string.recordings_sort_newest),
                RecordingSortOrder.OLDEST_FIRST to stringResource(R.string.recordings_sort_oldest),
                RecordingSortOrder.LONGEST_DURATION to stringResource(R.string.recordings_sort_longest),
                RecordingSortOrder.CONTACT_NAME to stringResource(R.string.recordings_sort_name)
            )
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    text = stringResource(R.string.recordings_sort),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )
                entries.forEach { (value, label) ->
                    val selected = current == value
                    ListItem(
                        headlineContent = { Text(label) },
                        trailingContent = { if (selected) Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .combinedClickable(onClick = {
                                onSortOrderChange(value)
                                showSheet = false
                            })
                    )
                }
            }
        }
    }
}

// ── States ─────────────────────────────────────────────────────────────────────────────────

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.tertiary)
        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.recordings_loading), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NoFolderState(onOpenSettings: () -> Unit) {
    CenteredMessage(
        icon = Icons.Outlined.FolderOff,
        title = stringResource(R.string.recordings_no_folder_title),
        message = stringResource(R.string.recordings_no_folder_message)
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = onOpenSettings) { Text(stringResource(R.string.recordings_no_folder_action)) }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    CenteredMessage(
        icon = Icons.Outlined.ErrorOutline,
        title = stringResource(R.string.recordings_error_title),
        message = message,
        tint = MaterialTheme.colorScheme.error
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.recordings_error_retry)) }
    }
}

@Composable
private fun EmptyState() {
    CenteredMessage(
        icon = Icons.Outlined.Call,
        title = stringResource(R.string.recordings_empty_title),
        message = stringResource(R.string.recordings_empty_message)
    )
}

@Composable
private fun EmptySearchState() {
    CenteredMessage(
        icon = Icons.Outlined.Search,
        title = stringResource(R.string.recordings_empty_search_title),
        message = stringResource(R.string.recordings_empty_search_message)
    )
}

@Composable
private fun CenteredMessage(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    extra: @Composable ColumnScope.() -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        extra()
    }
}

// ── List ───────────────────────────────────────────────────────────────────────────────────

@Composable
private fun RecordingsList(
    items: List<RecordingListItem>,
    playbackState: PlaybackState,
    selectionState: RecordingsSelectionState,
    onPlayOrToggle: (RecordingItem) -> Unit,
    onSeek: (Long) -> Unit,
    onSetSpeed: (Float) -> Unit,
    onToggleStar: (RecordingItem) -> Unit,
    onShare: (RecordingItem) -> Unit,
    onStartSelection: (RecordingItem) -> Unit,
    onToggleSelected: (RecordingItem) -> Unit
) {
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        itemsIndexed(items, key = { _, item -> item.recording.uri.toString() }) { index, item ->
            StaggeredReveal(index = index) {
                RecordingRow(
                    item = item,
                    isPlaying = playbackState.currentUri == item.recording.uri,
                    playbackState = playbackState,
                    isSelectionMode = selectionState.isSelectionMode,
                    isSelected = selectionState.selectedPaths.contains(item.recording.relativePath),
                    onPlayOrToggle = { onPlayOrToggle(item.recording) },
                    onSeek = onSeek,
                    onSetSpeed = onSetSpeed,
                    onToggleStar = { onToggleStar(item.recording) },
                    onShare = { onShare(item.recording) },
                    onLongPress = { onStartSelection(item.recording) },
                    onToggleSelected = { onToggleSelected(item.recording) }
                )
            }
        }
        item { Spacer(modifier = Modifier.height(72.dp)) }
    }
}

/** Fades + slides a row in shortly after composition, staggered by [index], for a deliberate reveal instead of an instant dump of rows. */
@Composable
private fun StaggeredReveal(index: Int, content: @Composable () -> Unit) {
    var visible by remember(index) { mutableStateOf(false) }
    LaunchedEffect(index) {
        kotlinx.coroutines.delay((index.coerceAtMost(12) * 35).toLong())
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(springAnim()) + androidx.compose.animation.slideInVertically(springAnim()) { it / 6 }
    ) {
        content()
    }
}

@Composable
private fun RecordingRow(
    item: RecordingListItem,
    isPlaying: Boolean,
    playbackState: PlaybackState,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onPlayOrToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onSetSpeed: (Float) -> Unit,
    onToggleStar: () -> Unit,
    onShare: () -> Unit,
    onLongPress: () -> Unit,
    onToggleSelected: () -> Unit
) {
    val recording = item.recording
    var showMenu by remember { mutableStateOf(false) }

    // The "now playing" card deliberately breaks from the light canvas into the app's dark
    // warm-charcoal chrome (the same treatment as the top bar) for contrast and hierarchy -
    // Linear/Notion-style purposeful dark elements on an otherwise light surface, rather than
    // everything going pastel-flat. It stays dark the same way in the dark theme too, just with
    // a touch more elevation to separate from the already-dark background.
    val chromeAccent = if (isPlaying) EmberBright else MaterialTheme.colorScheme.tertiary
    val chromeContent = if (isPlaying) TextOnCharcoal else MaterialTheme.colorScheme.onSurface
    val chromeMuted = if (isPlaying) TextOnCharcoalMuted else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        shape = if (isPlaying) MaterialTheme.shapes.large else MaterialTheme.shapes.medium,
        color = if (isPlaying) CharcoalSurfaceHigh else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = chromeContent,
        tonalElevation = if (isPlaying) 4.dp else 0.dp,
        shadowElevation = if (isPlaying) 6.dp else 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(recording.uri, isSelectionMode) {
                detectTapGestures(
                    onTap = { if (isSelectionMode) onToggleSelected() else onPlayOrToggle() },
                    onLongPress = { if (!isSelectionMode) onLongPress() }
                )
            }
            .semantics(mergeDescendants = true) {
                contentDescription = item.contactName ?: recording.phoneNumber ?: recording.displayName
            }
    ) {
        Column(modifier = Modifier.padding(vertical = 10.dp, horizontal = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isSelectionMode) {
                    Checkbox(checked = isSelected, onCheckedChange = { onToggleSelected() })
                    Spacer(modifier = Modifier.width(4.dp))
                } else {
                    DirectionBadge(direction = recording.direction, isPlaying = isPlaying)
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.contactName ?: recording.phoneNumber ?: stringResource(R.string.recordings_unknown_number),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildString {
                            append(formatTimestamp(recording.timestampMillis))
                            recording.durationMillis?.let { append(" · "); append(formatDuration(it)) }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = chromeMuted
                    )
                }

                if (!isSelectionMode) {
                    IconButton(onClick = onToggleStar) {
                        Icon(
                            imageVector = if (item.isStarred) Icons.Default.Star else Icons.Outlined.StarBorder,
                            contentDescription = stringResource(if (item.isStarred) R.string.recordings_star_remove else R.string.recordings_star_add),
                            tint = if (item.isStarred) chromeAccent else chromeMuted
                        )
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.general_more), tint = chromeMuted)
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.recordings_share)) },
                                leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                                onClick = { showMenu = false; onShare() }
                            )
                        }
                    }
                    IconButton(onClick = onPlayOrToggle) {
                        Icon(
                            imageVector = if (isPlaying && playbackState.isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                            contentDescription = stringResource(if (isPlaying && playbackState.isPlaying) R.string.a11y_pause else R.string.a11y_play),
                            tint = chromeAccent
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isPlaying && !isSelectionMode,
                enter = fadeIn(springAnim()) + expandVertically(springAnim()),
                exit = fadeOut(tween(150)) + shrinkVertically(tween(150))
            ) {
                InlinePlayer(
                    playbackState = playbackState,
                    onSeek = onSeek,
                    onSetSpeed = onSetSpeed,
                    accentColor = chromeAccent,
                    mutedColor = chromeMuted
                )
            }
        }
    }
}

@Composable
private fun DirectionBadge(direction: CallDirection?, isPlaying: Boolean) {
    val backgroundColor = if (isPlaying) EmberBright else MaterialTheme.colorScheme.surfaceContainerHighest
    val iconTint = if (isPlaying) OnEmberBright else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = when (direction) {
                CallDirection.INCOMING -> Icons.Outlined.CallReceived
                CallDirection.OUTGOING -> Icons.Outlined.CallMade
                null -> Icons.Outlined.Call
            },
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(18.dp)
        )
    }
}

// ── Inline player with waveform scrubber ──────────────────────────────────────────────────

@Composable
private fun InlinePlayer(
    playbackState: PlaybackState,
    onSeek: (Long) -> Unit,
    onSetSpeed: (Float) -> Unit,
    accentColor: Color,
    mutedColor: Color
) {
    // A translucent "glass" panel over the dark now-playing card - a lightweight stand-in for a
    // true background blur (Compose's blur modifier needs API 31+; this app supports API 30+),
    // using layered translucency to get a similar frosted feel across all supported versions.
    Surface(
        color = Color.White.copy(alpha = 0.06f),
        contentColor = TextOnCharcoal,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            if (playbackState.error != null) {
                Text(
                    text = stringResource(R.string.recordings_playback_error),
                    style = MaterialTheme.typography.bodySmall,
                    color = RecordingRed
                )
                return@Column
            }

            WaveformScrubber(
                waveform = playbackState.waveform,
                positionMs = playbackState.positionMs,
                durationMs = playbackState.durationMs,
                onSeek = onSeek,
                accentColor = accentColor,
                trackColor = mutedColor.copy(alpha = 0.35f)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${formatDuration(playbackState.positionMs)} / ${formatDuration(playbackState.durationMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = mutedColor
                )

                if (playbackState.isBuffering) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = accentColor)
                }

                SpeedControl(current = playbackState.speed, onSetSpeed = onSetSpeed, accentColor = accentColor)
            }
        }
    }
}

@Composable
private fun SpeedControl(current: Float, onSetSpeed: (Float) -> Unit, accentColor: Color) {
    Surface(
        shape = RoundedCornerShape(50),
        color = Color.White.copy(alpha = 0.10f),
        contentColor = accentColor,
        modifier = Modifier
            .combinedClickable(
                onClick = {
                    val currentIndex = PLAYBACK_SPEEDS.indexOf(current).let { if (it < 0) 1 else it }
                    val next = PLAYBACK_SPEEDS[(currentIndex + 1) % PLAYBACK_SPEEDS.size]
                    onSetSpeed(next)
                }
            )
            .semantics { contentDescription = "Playback speed ${formatSpeed(current)}" }
    ) {
        Text(
            text = formatSpeed(current),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

private fun formatSpeed(speed: Float): String {
    return if (speed == speed.toInt().toFloat()) "${speed.toInt()}x" else "${speed}x"
}

/**
 * A tappable/draggable approximate-amplitude waveform used as the seek control.
 * Falls back to a plain progress track while the waveform hasn't finished decoding yet.
 */
@Composable
private fun WaveformScrubber(
    waveform: FloatArray?,
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    accentColor: Color,
    trackColor: Color
) {
    val progressColor = accentColor
    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .pointerInput(durationMs) {
                detectTapGestures { offset ->
                    if (durationMs > 0) {
                        val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                        onSeek((fraction * durationMs).toLong())
                    }
                }
            }
    ) {
        val bars = waveform ?: FloatArray(48) { 0.18f }
        val barCount = bars.size
        val gap = 2.dp.toPx()
        val barWidth = (size.width - gap * (barCount - 1)) / barCount
        val centerY = size.height / 2f

        for (i in 0 until barCount) {
            val amplitude = bars[i].coerceIn(0.05f, 1f)
            val barHeight = size.height * amplitude
            val x = i * (barWidth + gap)
            val barProgress = i / barCount.toFloat()
            val color = if (barProgress <= progress) progressColor else trackColor
            drawRoundRect(
                color = color,
                topLeft = Offset(x, centerY - barHeight / 2f),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

// ── Motion ─────────────────────────────────────────────────────────────────────────────────

/** Shared spring spec for a natural, physical-feeling transition instead of linear/mechanical easing. */
private fun <T> springAnim(): androidx.compose.animation.core.SpringSpec<T> = androidx.compose.animation.core.spring(
    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
    stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
)

// ── Formatting helpers ─────────────────────────────────────────────────────────────────────

private fun formatDuration(millis: Long): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(millis.coerceAtLeast(0))
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}

private fun formatTimestamp(millis: Long): String {
    val formatter = java.text.SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault())
    return formatter.format(java.util.Date(millis))
}
