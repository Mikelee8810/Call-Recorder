/*
 * ShizuCallRecorder: FOSS Call recording powered through ADB/Shizuku!
 *  Copyright (C) 2026-present kitsumed (Med)
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.kitsumed.shizucallrecorder.ui.viewmodels

import android.app.Application
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.kitsumed.shizucallrecorder.data.AppPreferences
import com.kitsumed.shizucallrecorder.data.call.CallDirection
import com.kitsumed.shizucallrecorder.data.recordings.RecordingItem
import com.kitsumed.shizucallrecorder.data.recordings.RecordingsRepository
import com.kitsumed.shizucallrecorder.data.recordings.RetentionPolicyEnforcer
import com.kitsumed.shizucallrecorder.data.recordings.WaveformExtractor
import com.kitsumed.shizucallrecorder.system.storage.SafHelper
import com.kitsumed.shizucallrecorder.utils.AppLogger
import com.kitsumed.shizucallrecorder.utils.ContactLookupHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** A single row of the recordings list: the on-disk [recording] plus data resolved for display. */
data class RecordingListItem(
    val recording: RecordingItem,
    val contactName: String?,
    val isStarred: Boolean
)

/** Which recordings are shown in the list. */
enum class RecordingDirectionFilter { ALL, INCOMING, OUTGOING, STARRED }

/** How far back to look, relative to now. */
enum class RecordingDateFilter(val days: Int?) {
    ALL(null), LAST_7_DAYS(7), LAST_30_DAYS(30), LAST_90_DAYS(90)
}

/** How the visible list is ordered. */
enum class RecordingSortOrder { NEWEST_FIRST, OLDEST_FIRST, LONGEST_DURATION, CONTACT_NAME }

/** The current filter/search/sort selection, kept separate from the loaded data itself. */
data class RecordingsFilterState(
    val query: String = "",
    val direction: RecordingDirectionFilter = RecordingDirectionFilter.ALL,
    val dateFilter: RecordingDateFilter = RecordingDateFilter.ALL,
    val sortOrder: RecordingSortOrder = RecordingSortOrder.NEWEST_FIRST
)

/** Multi-select state for batch share/star/delete. */
data class RecordingsSelectionState(
    val isSelectionMode: Boolean = false,
    val selectedPaths: Set<String> = emptySet()
)

/** Overall loading state of the recordings list. */
sealed class RecordingsUiState {
    data object Loading : RecordingsUiState()
    data object NoFolderSelected : RecordingsUiState()
    data class Error(val message: String) : RecordingsUiState()
    /** Successfully loaded — [all] is unfiltered, [visible] has the current search/filter applied. */
    data class Success(val all: List<RecordingListItem>, val visible: List<RecordingListItem>) : RecordingsUiState()
}

/** Playback + waveform state for the item currently loaded in the player, if any. */
data class PlaybackState(
    val currentUri: Uri? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val waveform: FloatArray? = null,
    val speed: Float = 1f,
    val error: String? = null
)

/** Stepped playback speeds offered on the player, matching common voice-note conventions. */
val PLAYBACK_SPEEDS = listOf(0.5f, 1f, 1.5f, 2f)

/**
 * The "Brain" of the recordings library screen: lists on-disk recordings, resolves contact
 * names, applies search/filter, enforces the retention policy, and owns in-app playback via
 * ExoPlayer.
 */
class RecordingsViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val preferences = AppPreferences(appContext)

    private val _uiState = MutableStateFlow<RecordingsUiState>(RecordingsUiState.Loading)
    val uiState: StateFlow<RecordingsUiState> = _uiState.asStateFlow()

    private val _filterState = MutableStateFlow(RecordingsFilterState())
    val filterState: StateFlow<RecordingsFilterState> = _filterState.asStateFlow()

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _selectionState = MutableStateFlow(RecordingsSelectionState())
    val selectionState: StateFlow<RecordingsSelectionState> = _selectionState.asStateFlow()

    private var player: ExoPlayer? = null
    private var progressJob: Job? = null
    private var loadedItems: List<RecordingListItem> = emptyList()

    /** Re-scans the recordings folder, resolves contact names, applies retention, then re-applies the current filter. */
    fun refresh() {
        val folderUri = preferences.getRecordingFolderUri()
        if (folderUri == null || !SafHelper.isFolderValid(appContext, folderUri)) {
            _uiState.value = RecordingsUiState.NoFolderSelected
            return
        }
        _uiState.value = RecordingsUiState.Loading
        viewModelScope.launch {
            try {
                val scanned = RecordingsRepository.listRecordings(appContext, folderUri)
                val kept = RetentionPolicyEnforcer.enforce(appContext, preferences, scanned)

                val starred = preferences.getStarredRecordings()
                val items = kept.map { recording ->
                    val contactName = recording.phoneNumber?.let { ContactLookupHelper.findContactName(appContext, it) }
                    RecordingListItem(
                        recording = recording,
                        contactName = contactName,
                        isStarred = starred.contains(recording.relativePath)
                    )
                }
                loadedItems = items
                applyFilter()
            } catch (e: Exception) {
                AppLogger.e("Failed to load recordings", e)
                _uiState.value = RecordingsUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    // -------- Search & filter --------

    fun setSearchQuery(query: String) {
        _filterState.update { it.copy(query = query) }
        applyFilter()
    }

    fun setDirectionFilter(filter: RecordingDirectionFilter) {
        _filterState.update { it.copy(direction = filter) }
        applyFilter()
    }

    fun setDateFilter(filter: RecordingDateFilter) {
        _filterState.update { it.copy(dateFilter = filter) }
        applyFilter()
    }

    fun setSortOrder(order: RecordingSortOrder) {
        _filterState.update { it.copy(sortOrder = order) }
        applyFilter()
    }

    private fun applyFilter() {
        val filter = _filterState.value
        val cutoff = filter.dateFilter.days?.let { System.currentTimeMillis() - TimeUnit.DAYS.toMillis(it.toLong()) }

        val filtered = loadedItems.filter { item ->
            val matchesQuery = filter.query.isBlank() ||
                    (item.contactName?.contains(filter.query, ignoreCase = true) == true) ||
                    (item.recording.phoneNumber?.contains(filter.query, ignoreCase = true) == true) ||
                    item.recording.displayName.contains(filter.query, ignoreCase = true)

            val matchesDirection = when (filter.direction) {
                RecordingDirectionFilter.ALL -> true
                RecordingDirectionFilter.STARRED -> item.isStarred
                RecordingDirectionFilter.INCOMING -> item.recording.direction == CallDirection.INCOMING
                RecordingDirectionFilter.OUTGOING -> item.recording.direction == CallDirection.OUTGOING
            }

            val matchesDate = cutoff == null || item.recording.timestampMillis >= cutoff

            matchesQuery && matchesDirection && matchesDate
        }

        val visible = when (filter.sortOrder) {
            RecordingSortOrder.NEWEST_FIRST -> filtered.sortedByDescending { it.recording.timestampMillis }
            RecordingSortOrder.OLDEST_FIRST -> filtered.sortedBy { it.recording.timestampMillis }
            RecordingSortOrder.LONGEST_DURATION -> filtered.sortedByDescending { it.recording.durationMillis ?: 0L }
            RecordingSortOrder.CONTACT_NAME -> filtered.sortedWith(
                compareBy(String.CASE_INSENSITIVE_ORDER) { it.contactName ?: it.recording.phoneNumber ?: it.recording.displayName }
            )
        }

        _uiState.value = RecordingsUiState.Success(all = loadedItems, visible = visible)

        // Drop selections for items that scrolled out of the current data set (e.g. deleted by retention).
        val validPaths = loadedItems.map { it.recording.relativePath }.toSet()
        _selectionState.update { it.copy(selectedPaths = it.selectedPaths.intersect(validPaths)) }
    }

    // -------- Starring --------

    /** Toggles whether [item] is starred/kept (exempt from auto-delete). */
    fun toggleStar(item: RecordingItem) {
        val current = preferences.getStarredRecordings()
        val updated = if (current.contains(item.relativePath)) current - item.relativePath else current + item.relativePath
        preferences.setStarredRecordings(updated)
        loadedItems = loadedItems.map {
            if (it.recording.relativePath == item.relativePath) it.copy(isStarred = updated.contains(item.relativePath)) else it
        }
        applyFilter()
    }

    // -------- Multi-select & batch actions --------

    /** Enters selection mode with [item] pre-selected (used for the long-press-to-start gesture). */
    fun startSelection(item: RecordingItem) {
        _selectionState.value = RecordingsSelectionState(isSelectionMode = true, selectedPaths = setOf(item.relativePath))
    }

    /** Exits selection mode and clears the selection. */
    fun clearSelection() {
        _selectionState.value = RecordingsSelectionState()
    }

    /** Toggles whether [item] is part of the current selection. No-op outside selection mode. */
    fun toggleSelected(item: RecordingItem) {
        _selectionState.update { state ->
            if (!state.isSelectionMode) return@update state
            val path = item.relativePath
            val updated = if (state.selectedPaths.contains(path)) state.selectedPaths - path else state.selectedPaths + path
            state.copy(selectedPaths = updated)
        }
    }

    /** Selects every currently visible recording. */
    fun selectAllVisible() {
        val visible = (_uiState.value as? RecordingsUiState.Success)?.visible ?: return
        _selectionState.value = RecordingsSelectionState(
            isSelectionMode = true,
            selectedPaths = visible.map { it.recording.relativePath }.toSet()
        )
    }

    private fun selectedItems(): List<RecordingItem> {
        val selected = _selectionState.value.selectedPaths
        return loadedItems.map { it.recording }.filter { it.relativePath in selected }
    }

    /** Stars every currently selected recording (does not unstar already-starred ones). */
    fun batchStarSelected() {
        val selected = _selectionState.value.selectedPaths
        if (selected.isEmpty()) return
        preferences.setStarredRecordings(preferences.getStarredRecordings() + selected)
        loadedItems = loadedItems.map { if (it.recording.relativePath in selected) it.copy(isStarred = true) else it }
        applyFilter()
    }

    /** Deletes every currently selected recording from disk, then exits selection mode. */
    fun batchDeleteSelected() {
        val items = selectedItems()
        if (items.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                for (item in items) {
                    try {
                        DocumentFile.fromSingleUri(appContext, item.uri)?.delete()
                    } catch (e: Exception) {
                        AppLogger.e("Failed to delete recording during batch delete: ${item.relativePath}", e)
                    }
                }
            }
            clearSelection()
            refresh()
        }
    }

    /** Returns the on-disk items currently selected, for the caller to build a share intent from. */
    fun getSelectedItemsForShare(): List<RecordingItem> = selectedItems()

    // -------- Playback --------

    /** Starts playing [item], or toggles play/pause if it is already the loaded item. */
    fun playOrToggle(item: RecordingItem) {
        if (_playbackState.value.currentUri == item.uri && player != null) {
            togglePlayPause()
            return
        }
        stopInternal()

        val exo = try {
            ExoPlayer.Builder(appContext).build()
        } catch (e: Exception) {
            _playbackState.value = PlaybackState(currentUri = item.uri, error = e.message)
            return
        }
        player = exo
        _playbackState.value = PlaybackState(
            currentUri = item.uri,
            isBuffering = true,
            durationMs = item.durationMillis ?: 0L
        )

        exo.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                AppLogger.w("Playback error: ${error.message}")
                _playbackState.update { it.copy(error = error.message, isPlaying = false, isBuffering = false) }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> _playbackState.update {
                        it.copy(isBuffering = false, durationMs = exo.duration.coerceAtLeast(0))
                    }
                    Player.STATE_BUFFERING -> _playbackState.update { it.copy(isBuffering = true) }
                    Player.STATE_ENDED -> _playbackState.update { it.copy(isPlaying = false, positionMs = it.durationMs) }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playbackState.update { it.copy(isPlaying = isPlaying) }
            }
        })

        try {
            exo.setMediaItem(MediaItem.fromUri(item.uri))
            exo.prepare()
            exo.playWhenReady = true
        } catch (e: Exception) {
            _playbackState.update { it.copy(error = e.message, isBuffering = false) }
        }

        startProgressLoop()

        // Waveform generation is best-effort and can take a moment; it never blocks playback start.
        viewModelScope.launch {
            val waveform = WaveformExtractor.extract(appContext, item.uri)
            if (_playbackState.value.currentUri == item.uri) {
                _playbackState.update { it.copy(waveform = waveform) }
            }
        }
    }

    fun togglePlayPause() {
        val exo = player ?: return
        exo.playWhenReady = !exo.playWhenReady
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
        _playbackState.update { it.copy(positionMs = positionMs) }
    }

    /** Sets the playback speed (e.g. one of [PLAYBACK_SPEEDS]) on the currently loaded player. */
    fun setPlaybackSpeed(speed: Float) {
        player?.setPlaybackSpeed(speed)
        _playbackState.update { it.copy(speed = speed) }
    }

    fun stopPlayback() {
        stopInternal()
        _playbackState.value = PlaybackState()
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                val exo = player
                if (exo != null) {
                    _playbackState.update { it.copy(positionMs = exo.currentPosition.coerceAtLeast(0)) }
                }
                kotlinx.coroutines.delay(200)
            }
        }
    }

    private fun stopInternal() {
        progressJob?.cancel()
        progressJob = null
        player?.release()
        player = null
    }

    override fun onCleared() {
        super.onCleared()
        stopInternal()
    }
}
