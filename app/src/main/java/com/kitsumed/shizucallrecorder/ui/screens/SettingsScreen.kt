/*
 * ShizuCallRecorder: FOSS Call recording powered through ADB/Shizuku!
 *  Copyright (C) 2026-present kitsumed (Med)
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.kitsumed.shizucallrecorder.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kitsumed.shizucallrecorder.R
import com.kitsumed.shizucallrecorder.data.AppPreferences
import com.kitsumed.shizucallrecorder.integrations.scrcpy.ScrcpyAudioCodec
import com.kitsumed.shizucallrecorder.integrations.scrcpy.ScrcpyAudioSource
import com.kitsumed.shizucallrecorder.integrations.scrcpy.ScrcpyConfig
import com.kitsumed.shizucallrecorder.services.callDetection.CallDetectionMode
import com.kitsumed.shizucallrecorder.system.PersistentFolderPickerContract
import com.kitsumed.shizucallrecorder.system.storage.SafHelper
import com.kitsumed.shizucallrecorder.system.takePersistableFolderPermission
import com.kitsumed.shizucallrecorder.ui.common.ContactSelectionDialog
import com.kitsumed.shizucallrecorder.ui.common.FileNameFormatDialog
import com.kitsumed.shizucallrecorder.ui.common.M3DropdownField
import com.kitsumed.shizucallrecorder.ui.common.OptionItem
import com.kitsumed.shizucallrecorder.ui.common.ToggleListItem
import com.kitsumed.shizucallrecorder.ui.theme.ShizuCallRecorderTheme
import com.kitsumed.shizucallrecorder.ui.viewmodels.ContactPickerState
import com.kitsumed.shizucallrecorder.ui.viewmodels.ContactPickerType
import com.kitsumed.shizucallrecorder.ui.viewmodels.ContactPickerViewModel
import com.kitsumed.shizucallrecorder.ui.viewmodels.DebugAction
import com.kitsumed.shizucallrecorder.ui.viewmodels.SettingsActions
import com.kitsumed.shizucallrecorder.ui.viewmodels.SettingsViewModel
import com.kitsumed.shizucallrecorder.system.permissions.PermissionChecks
import kotlinx.coroutines.delay
import org.xmlpull.v1.XmlPullParser
import java.util.Locale
import androidx.core.net.toUri

/**
 * Stateful wrapper for the Settings screen that connects [SettingsViewModel] to [SettingsContent].
 *
 * @param viewModel Handles saving whenever the user changes a setting.
 * @param modifier  Optional modifier for the root [Surface].
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Trigger recomposition when settings change by viewmodel.refresh()
    val updateTrigger by viewModel.updateTrigger.collectAsState()

    // ContactPickerViewModel owns the contact-loading logic and dialog state.
    val contactPickerViewModel: ContactPickerViewModel = viewModel()
    val contactPickerState by contactPickerViewModel.contactPickerState.collectAsState()

    // Folder picker — PersistentFolderPickerContract keeps access alive after a reboot.
    val folderPickerLauncher = rememberLauncherForActivityResult(PersistentFolderPickerContract()) { uri ->
        if (uri != null) {
            context.takePersistableFolderPermission(uri)
            viewModel.preferences.setRecordingFolderUri(uri)
        }
        viewModel.refresh()
    }

    // Google Drive backup destination. The Android document picker exposes Drive as a provider,
    // so this works without a second Google login flow or app-owned cloud credentials.
    val driveBackupFolderPickerLauncher = rememberLauncherForActivityResult(PersistentFolderPickerContract()) { uri ->
        if (uri != null) {
            context.takePersistableFolderPermission(uri)
            viewModel.setGoogleDriveBackupFolder(uri)
        } else {
            viewModel.refresh()
        }
    }

    // Export logs picker — creates a new text file and gives us access to write to it, then passes the URI to the viewmodel for writing.
    val exportLogLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri: Uri? ->
        if (uri != null) {
            viewModel.exportLogs(uri)
        }
    }

    SettingsContent(
        preferences = viewModel.preferences,
        updateTrigger = updateTrigger,
        actions = viewModel,
        contactPickerState = contactPickerState,
        onSelectFolder = { folderPickerLauncher.launch(null) },
        onSelectDriveBackupFolder = { driveBackupFolderPickerLauncher.launch(null) },
        onOpenContactsIncoming = { contactPickerViewModel.openContactPicker(ContactPickerType.INCOMING) },
        onOpenContactsOutgoing = { contactPickerViewModel.openContactPicker(ContactPickerType.OUTGOING) },
        onConfirmContacts = { lookupIDs ->
            contactPickerViewModel.confirmContactPicker(lookupIDs)
            // Refresh the screen so the new contact list information is shown immediately after confirming and closing the dialog.
            viewModel.refresh()
        },
        onDismissContacts = { contactPickerViewModel.dismissContactPicker() },
        onExportLogs = { exportLogLauncher.launch("call_recorder_diagnostic.log") },
        onBack = onBack,
        modifier = modifier
    )
}

/**
 * Stateless visual layer for the Settings screen.
 *
 * @param preferences            The [AppPreferences] instance to read data from.
 * @param updateTrigger          Trigger value to force/detect recomposition when settings change.
 * @param actions                Implementation of [SettingsActions] to handle user interaction.
 * @param contactPickerState     Current state of the contact picker dialog.
 * @param onSelectFolder         Called when the user taps the recording-folder row.
 * @param onSelectDriveBackupFolder Called when the user taps the Google Drive backup-folder row.
 * @param onOpenContactsIncoming Called to open picker for incoming contacts.
 * @param onOpenContactsOutgoing Called to open picker for outgoing contacts.
 * @param onConfirmContacts      Called when contacts are confirmed from the dialog.
 * @param onDismissContacts      Called when we want to close the dialog without confirmation/saving.
 * @param onExportLogs           Called to export diagnostic logs using SAF.
 * @param modifier               Optional size/position modifier.
 */
@Composable
fun SettingsContent(
    preferences: AppPreferences,
    updateTrigger: Int,
    actions: SettingsActions,
    contactPickerState: ContactPickerState?,
    onSelectFolder: () -> Unit,
    onSelectDriveBackupFolder: () -> Unit,
    onOpenContactsIncoming: () -> Unit,
    onOpenContactsOutgoing: () -> Unit,
    onConfirmContacts: (Set<String>) -> Unit,
    onDismissContacts: () -> Unit,
    onExportLogs: () -> Unit,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxSize(),
        color = Color.Transparent // The shared AppBackground (drawn by AppNavigationScreen) shows through.
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            // Equivalent to .safeDrawingPadding() but allow UI to extend behind the status bar when scrolling
            contentPadding = WindowInsets.safeDrawing
                .add(WindowInsets(left = 16.dp, right = 16.dp, top = 0.dp, bottom = 0.dp))
                .asPaddingValues(),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.a11y_back),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = stringResource(R.string.general_settings),
                        style = MaterialTheme.typography.displaySmall
                    )
                }
            }
            item { AboutSection(versionString = actions.getAppVersion()) }
            item {
                RecordingSection(
                    preferences = preferences,
                    updateTrigger = updateTrigger,
                    actions = actions,
                    onSelectFolder = onSelectFolder,
                    onOpenContactsIncoming = onOpenContactsIncoming,
                    onOpenContactsOutgoing = onOpenContactsOutgoing
                )
            }
            item { RetentionSection(preferences, updateTrigger, actions) }
            item { DriveBackupSection(preferences, updateTrigger, actions, onSelectDriveBackupFolder) }
            item { AudioSection(preferences, updateTrigger, actions) }
            item { SecuritySection(preferences, updateTrigger, actions) }
            item { VisualSection(preferences, updateTrigger, actions) }
            item { DebugSection(preferences, updateTrigger, actions, onExportLogs) }
            item {  } // extra padding at bottom
        }
    }

    // The contact-picker dialog sits on top of the settings content.
    contactPickerState?.let { picker ->
        ContactSelectionDialog(
            title = when (picker.type) {
                ContactPickerType.INCOMING -> stringResource(R.string.settings_select_contacts_incoming)
                ContactPickerType.OUTGOING -> stringResource(R.string.settings_select_contacts_outgoing)
            },
            contacts = picker.contacts,
            initialSelection = picker.selectedContactsLookupId,
            onConfirm = onConfirmContacts,
            onDismiss = onDismissContacts
        )
    }
}

@Composable
private fun DriveBackupSection(
    preferences: AppPreferences,
    updateTrigger: Int,
    actions: SettingsActions,
    onSelectDriveBackupFolder: () -> Unit
) {
    val context = LocalContext.current
    val backupFolderUri = remember(updateTrigger) { preferences.getGoogleDriveBackupFolderUri() }
    val backupFolderLabel = remember(updateTrigger) {
        SafHelper.getFolderDisplayNameOrNull(context, backupFolderUri)
    }
    val backupEnabled = remember(updateTrigger) { preferences.isGoogleDriveBackupEnabled() }

    SettingsSection(title = stringResource(R.string.settings_section_backup)) {
        ToggleListItem(
            label = stringResource(R.string.settings_google_drive_backup),
            description = stringResource(R.string.settings_google_drive_backup_description),
            checked = backupEnabled,
            enabled = backupFolderUri != null,
            onCheckedChange = { actions.setGoogleDriveBackupEnabled(it) }
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)

        ListItem(
            modifier = Modifier
                .clickable { onSelectDriveBackupFolder() }
                .semantics(mergeDescendants = true) {},
            headlineContent = { Text(stringResource(R.string.settings_google_drive_folder)) },
            supportingContent = {
                Text(
                    text = backupFolderLabel ?: stringResource(R.string.settings_google_drive_choose_folder),
                    color = MaterialTheme.colorScheme.primary
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            trailingContent = {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null
                )
            }
        )
    }
}

// ── Settings sections ──────────────────────────────────────────────────────────────────────

/** Shows the app and bundled server versions. */
@Composable
private fun AboutSection(versionString: String) {
    val serverVersion = ScrcpyConfig.SCRCPY_VERSION

    SettingsSection(title = stringResource(R.string.settings_section_about)) {
        ListItem(
            headlineContent = { Text(versionString) },
            supportingContent = {
                Text(stringResource(R.string.settings_scrcpy_server, serverVersion))
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

/** Shows the theme and dynamic colour settings.
 *
 * @param preferences   The [AppPreferences] instance to read data from.
 * @param updateTrigger Trigger value to force recomposition when settings change.
 * @param actions       Implementation of [SettingsActions] to handle user interaction.
 */
@Composable
private fun VisualSection(preferences: AppPreferences, updateTrigger: Int, actions: SettingsActions) {
    val currentThemeMode = remember(updateTrigger) { preferences.getThemeMode() }
    val isShowToastsEnabled = remember(updateTrigger) { preferences.isShowToastsEnabled() }
    val isRecordingOverlayEnabled = remember(updateTrigger) { preferences.isOverlayEnabled() }
    val context = LocalContext.current
    val resources = LocalResources.current

    // Read the current applied language without warnings
    val currentLanguage = remember {
        val currentLocales = AppCompatDelegate.getApplicationLocales()
        if (currentLocales.isEmpty) "" else currentLocales[0]?.toLanguageTag() ?: ""
    }

    // Fetch available languages from dynamically generated XML resource file.
    val languageOptions = remember(context) {
        val options = mutableListOf(OptionItem("", resources.getString(R.string.settings_language_system)))

        // Suppress the warning right here since AGP create this file dynamically at compile time
        @SuppressLint("DiscouragedApi")
        val resId = resources.getIdentifier("_generated_res_locale_config", "xml", context.packageName)

        try {
            val parser = resources.getXml(resId)
            var eventType = parser.eventType

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "locale") {
                    val localeName = parser.getAttributeValue("http://schemas.android.com/apk/res/android", "name")
                    if (localeName != null) {
                        val locale = Locale.forLanguageTag(localeName)
                        val displayName = locale.getDisplayName(locale).replaceFirstChar {
                            if (it.isLowerCase()) it.titlecase(locale) else it.toString()
                        }
                        options.add(OptionItem(localeName, displayName))
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {
            options.add(OptionItem("en", "English (Provided as fallback)"))
        }
        options.distinctBy { it.key }
    }

    SettingsSection(title = stringResource(R.string.settings_section_visual)) {
        M3DropdownField(
            label = stringResource(R.string.settings_language),
            selected = languageOptions.find { it.key == currentLanguage } ?: languageOptions.first(),
            options = languageOptions,
            onOptionSelected = { actions.setAppLanguage(it.key) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )

        val themeOptions = AppPreferences.ThemeMode.entries.map { mode ->
            OptionItem(mode.key, stringResource(mode.displayNameResId))
        }
        val defaultThemeMode = AppPreferences.DefaultsValue.THEME_MODE.key
        M3DropdownField(
            label    = stringResource(R.string.settings_theme_mode),
            selected = themeOptions.find { it.key == currentThemeMode.key } 
                ?: themeOptions.find { it.key == defaultThemeMode } 
                ?: themeOptions.first(),
            options  = themeOptions,
            onOptionSelected = { actions.setThemeMode(AppPreferences.ThemeMode.fromKey(it.key)) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
        ToggleListItem(
            label           = stringResource(R.string.settings_show_toasts),
            checked         = isShowToastsEnabled,
            onCheckedChange = { actions.setShowToastsEnabled(it) }
        )
        ToggleListItem(
            label = stringResource(R.string.settings_overlay_title),
            description = stringResource(R.string.settings_overlay_subtitle),
            checked = isRecordingOverlayEnabled,
            onCheckedChange = { enabled ->
                if (enabled && !PermissionChecks.hasOverlayPermission(context)) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        "package:${context.packageName}".toUri()
                    )
                    context.startActivity(intent)
                } else {
                    actions.setOverlayEnabled(enabled)
                }
            }
        )
    }
}

/** Shows the security settings.
 *
 * @param preferences   The [AppPreferences] instance to read data from.
 * @param updateTrigger Trigger value to force recomposition when settings change.
 * @param actions       Implementation of [SettingsActions] to handle user interaction.
 */
@Composable
private fun SecuritySection(preferences: AppPreferences, updateTrigger: Int, actions: SettingsActions) {
    val autoManageShizuku = remember(updateTrigger) { preferences.isShizukuAutoManageEnabled() }
    val shizukuStartOnRecord = remember(updateTrigger) { preferences.isShizukuStartOnRecordEnabled() }

    SettingsSection(title = stringResource(R.string.settings_section_security)) {
        ToggleListItem(
            label           = stringResource(R.string.settings_shizuku_auto_manage),
            checked         = autoManageShizuku,
            onCheckedChange = { actions.setShizukuAutoManageEnabled(it) },
            description     = stringResource(R.string.settings_shizuku_auto_manage_desc)
        )

        AnimatedVisibility(
            visible = autoManageShizuku,
            enter = fadeIn(animationSpec = tween(durationMillis = 500)) +
                    expandVertically(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        expandFrom = Alignment.Top
                    ),
            exit = fadeOut(animationSpec = tween(durationMillis = 450)) +
                    shrinkVertically(
                        animationSpec = tween(durationMillis = 450, easing = LinearOutSlowInEasing),
                        shrinkTowards = Alignment.Top
                    )
        ) {
            Column {
                ToggleListItem(
                    label           = stringResource(R.string.settings_shizuku_start_on_record),
                    checked         = shizukuStartOnRecord,
                    onCheckedChange = { actions.setShizukuStartOnRecordEnabled(it) },
                    description     = stringResource(R.string.settings_shizuku_start_on_record_desc)
                )
            }
        }
    }
}

/** Shows the recording folder, auto-record toggles, and contact-filter options.
 *
 * @param preferences              The [AppPreferences] instance to read data from.
 * @param updateTrigger          Trigger value to force recomposition when settings change.
 * @param actions                Implementation of [SettingsActions] to handle user interaction.
 * @param onSelectFolder         Called when the user taps the recording-folder row; opens the SAF picker
 *                               whose launcher lives in AppNavigation.
 * @param onOpenContactsIncoming Called when the user wants to pick incoming contacts to ignore;
 *                               opens the [ContactSelectionDialog] via [ContactPickerViewModel].
 * @param onOpenContactsOutgoing Called when the user wants to pick outgoing contacts to ignore;
 *                               opens the [ContactSelectionDialog] via [ContactPickerViewModel].
 */
@Composable
private fun RecordingSection(
    preferences: AppPreferences,
    updateTrigger: Int,
    actions: SettingsActions,
    onSelectFolder: () -> Unit,
    onOpenContactsIncoming: () -> Unit,
    onOpenContactsOutgoing: () -> Unit
) {
    val context = LocalContext.current
    
    // Evaluate these here so they are fetched on every recomposition.
    val recordingFolderLabel = remember(updateTrigger) { SafHelper.getFolderDisplayNameOrNull(context, preferences.getRecordingFolderUri()) }
    val callDetectionMode = remember(updateTrigger) { preferences.getCallDetectionMode() }
    val recordThirdPartyCalls = remember(updateTrigger) { preferences.isRecordThirdPartyCallsEnabled() }
    val fileNameFormat = remember(updateTrigger) { preferences.getFileNameTemplate() }
    val postRecordingFileNotifications = remember(updateTrigger) { preferences.isPostRecordingFileActionsNotificationEnabled() }
    val isVibrationEnabled = remember(updateTrigger) { preferences.isVibrationEnabled() }
    val autoRecordIncoming = remember(updateTrigger) { preferences.isAutoRecordIncomingEnabled() }
    val autoRecordOutgoing = remember(updateTrigger) { preferences.isAutoRecordOutgoingEnabled() }
    val ignoreAnonymousIncoming = remember(updateTrigger) { preferences.isIgnoreAnonymousIncomingEnabled() }
    val ignoreCrossCountryIncoming = remember(updateTrigger) { preferences.isIgnoreCrossCountryIncomingEnabled() }
    val ignoreContactsModeIncoming = remember(updateTrigger) { preferences.getIgnoreContactsModeIncoming() }
    val ignoreContactsModeOutgoing = remember(updateTrigger) { preferences.getIgnoreContactsModeOutgoing() }
    val ignoreCrossCountryOutgoing = remember(updateTrigger) { preferences.isIgnoreCrossCountryOutgoingEnabled() }
    val ignoredContactsIncomingCount = remember(updateTrigger) { preferences.getIgnoredContactsIncoming().size }
    val ignoredContactsOutgoingCount = remember(updateTrigger) { preferences.getIgnoredContactsOutgoing().size }

    var showFileNameFormatDialog by remember { mutableStateOf(false) }

    val detectionOptions = CallDetectionMode.entries.map { mode ->
        OptionItem(
            key = mode.key,
            label = stringResource(mode.titleResId),
            description = stringResource(mode.descriptionResId),
            // Automatically grays out option if the user device's OS API level is incompatible.
            enabled = mode.isSupportedOnCurrentApi()
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingsSectionHeader(stringResource(R.string.settings_section_recording))

        SettingsCard {
            M3DropdownField(
                label = stringResource(R.string.settings_call_detection_method),
                selected = detectionOptions.find { it.key == callDetectionMode.key } ?: detectionOptions.first(),
                options = detectionOptions,
                onOptionSelected = { selectedItem ->
                    val chosenMode = CallDetectionMode.fromKey(selectedItem.key)
                    actions.setCallDetectionMode(chosenMode)
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )

            AnimatedContent(
                targetState = callDetectionMode,
                transitionSpec = {
                    val enterTransition = fadeIn(tween(300)) + expandVertically(tween(300))
                    val exitTransition = fadeOut(tween(250)) + shrinkVertically(tween(250))
                    enterTransition togetherWith exitTransition
                },
                label = "CallDetectionModeSettingsTransition"
            ) { targetMode ->
                when (targetMode) {
                    CallDetectionMode.InCallService -> {
                        ToggleListItem(
                            label = stringResource(R.string.settings_record_third_party_calls),
                            description = stringResource(R.string.settings_record_third_party_calls_description),
                            checked = recordThirdPartyCalls,
                            onCheckedChange = { actions.setRecordThirdPartyCalls(it) }
                        )
                    }
                    CallDetectionMode.PhoneState -> {
                        WarningCard(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            title = stringResource(R.string.settings_call_detection_method_warning_title),
                            message = stringResource(R.string.call_detection_mode_phonestate_limited_support)
                        )
                    }
                }
            }
        }

        SettingsCard {
            ListItem(
                modifier = Modifier
                    .clickable { onSelectFolder() }
                    .semantics(mergeDescendants = true) {},
                headlineContent = { Text(stringResource(R.string.settings_recording_folder_label)) },
                supportingContent = {
                    Text(
                        text = recordingFolderLabel ?: stringResource(R.string.settings_tap_to_select_folder),
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                trailingContent = {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null
                    )
                }
            )

            SettingsDivider()

            ListItem(
                modifier = Modifier
                    .clickable { showFileNameFormatDialog = true }
                    .semantics(mergeDescendants = true) {},
                headlineContent = { Text(stringResource(R.string.settings_file_name_template)) },
                supportingContent = {
                    Text(
                        text = fileNameFormat,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                trailingContent = {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
        }

        SettingsCard {
            ToggleListItem(
                label = stringResource(R.string.settings_post_recording_notification),
                description = stringResource(R.string.settings_post_recording_notification_description),
                checked = postRecordingFileNotifications,
                onCheckedChange = { actions.setPostRecordingFileNotification(it) }
            )

            SettingsDivider()

            ToggleListItem(
                label = stringResource(R.string.settings_vibration_enabled),
                checked = isVibrationEnabled,
                onCheckedChange = { actions.setVibrationEnabled(it) }
            )
        }

        SettingsCard {
            ToggleListItem(
                label = stringResource(R.string.settings_auto_record_incoming),
                checked = autoRecordIncoming,
                onCheckedChange = { actions.setAutoRecordIncoming(it) }
            )
            AnimatedVisibility(
                visible = autoRecordIncoming,
                enter = fadeIn(animationSpec = tween(durationMillis = 500)) +
                        expandVertically(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            expandFrom = Alignment.Top
                        ),
                exit = fadeOut(animationSpec = tween(durationMillis = 450)) +
                        shrinkVertically(
                            animationSpec = tween(durationMillis = 450, easing = LinearOutSlowInEasing),
                            shrinkTowards = Alignment.Top
                        )
            ) {
                Column {
                    SettingsDivider()
                    ToggleListItem(
                        label = stringResource(R.string.settings_ignore_anonymous_incoming),
                        checked = ignoreAnonymousIncoming,
                        onCheckedChange = { actions.setIgnoreAnonymousIncoming(it) }
                    )
                    ToggleListItem(
                        label = stringResource(R.string.settings_ignore_cross_country_incoming),
                        checked = ignoreCrossCountryIncoming,
                        onCheckedChange = { actions.setIgnoreCrossCountryIncoming(it) },
                        enabled = ignoreAnonymousIncoming
                    )
                    IgnoreContactsOptions(
                        label = stringResource(R.string.settings_ignore_contacts_incoming),
                        selectedEnum = ignoreContactsModeIncoming,
                        selectedCount = ignoredContactsIncomingCount,
                        onSelected = { actions.setIgnoreContactsModeIncoming(it) },
                        onSelectContacts = onOpenContactsIncoming
                    )
                }
            }
        }

        SettingsCard {
            ToggleListItem(
                label = stringResource(R.string.settings_auto_record_outgoing),
                checked = autoRecordOutgoing,
                onCheckedChange = { actions.setAutoRecordOutgoing(it) }
            )
            AnimatedVisibility(
                visible = autoRecordOutgoing,
                enter = fadeIn(animationSpec = tween(durationMillis = 500)) +
                        expandVertically(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            expandFrom = Alignment.Top
                        ),
                exit = fadeOut(animationSpec = tween(durationMillis = 450)) +
                        shrinkVertically(
                            animationSpec = tween(durationMillis = 450, easing = LinearOutSlowInEasing),
                            shrinkTowards = Alignment.Top
                        )
            ) {
                Column {
                    SettingsDivider()
                    ToggleListItem(
                        label = stringResource(R.string.settings_ignore_cross_country_outgoing),
                        checked = ignoreCrossCountryOutgoing,
                        onCheckedChange = { actions.setIgnoreCrossCountryOutgoing(it) }
                    )
                    IgnoreContactsOptions(
                        label = stringResource(R.string.settings_ignore_contacts_outgoing),
                        selectedEnum = ignoreContactsModeOutgoing,
                        selectedCount = ignoredContactsOutgoingCount,
                        onSelected = { actions.setIgnoreContactsModeOutgoing(it) },
                        onSelectContacts = onOpenContactsOutgoing
                    )
                }
            }
        }
    }

    if (showFileNameFormatDialog) {
        FileNameFormatDialog(
            initialFormat = fileNameFormat,
            activeMode = preferences.getCallDetectionMode(),
            onConfirm = { format ->
                actions.setFileNameTemplate(format)
                showFileNameFormatDialog = false
            },
            onDismiss = { showFileNameFormatDialog = false }
        )
    }
}

/** Shows the auto-delete retention policy for saved recordings.
 *
 * @param preferences   The [AppPreferences] instance to read data from.
 * @param updateTrigger Trigger value to force recomposition when settings change.
 * @param actions       Implementation of [SettingsActions] to handle user interaction.
 */
@Composable
private fun RetentionSection(preferences: AppPreferences, updateTrigger: Int, actions: SettingsActions) {
    val retentionMode = remember(updateTrigger) { preferences.getRetentionMode() }
    val maxAgeDays = remember(updateTrigger) { preferences.getRetentionMaxAgeDays() }
    val maxStorageMb = remember(updateTrigger) { preferences.getRetentionMaxStorageMb() }

    SettingsSection(title = stringResource(R.string.settings_section_retention)) {
        val retentionOptions = AppPreferences.RetentionMode.entries.map { mode ->
            OptionItem(mode.key, stringResource(mode.displayNameResId))
        }

        M3DropdownField(
            label = stringResource(R.string.settings_retention_mode),
            selected = retentionOptions.find { it.key == retentionMode.key } ?: retentionOptions.first(),
            options = retentionOptions,
            onOptionSelected = { actions.setRetentionMode(AppPreferences.RetentionMode.fromKey(it.key)) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )

        AnimatedContent(
            targetState = retentionMode,
            transitionSpec = {
                (fadeIn(tween(300)) + expandVertically(tween(300))) togetherWith
                    (fadeOut(tween(250)) + shrinkVertically(tween(250)))
            },
            label = "RetentionModeSettingsTransition"
        ) { targetMode ->
            when (targetMode) {
                AppPreferences.RetentionMode.MAX_AGE -> {
                    var textState by remember(maxAgeDays) { mutableStateOf(maxAgeDays.toString()) }
                    OutlinedTextField(
                        value = textState,
                        onValueChange = { value ->
                            if (value.all { it.isDigit() } && value.length <= 4) {
                                textState = value
                                value.toIntOrNull()?.takeIf { it > 0 }?.let { actions.setRetentionMaxAgeDays(it) }
                            }
                        },
                        label = { Text(stringResource(R.string.settings_retention_max_age_days)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done)
                    )
                }
                AppPreferences.RetentionMode.MAX_STORAGE -> {
                    var textState by remember(maxStorageMb) { mutableStateOf(maxStorageMb.toString()) }
                    OutlinedTextField(
                        value = textState,
                        onValueChange = { value ->
                            if (value.all { it.isDigit() } && value.length <= 6) {
                                textState = value
                                value.toIntOrNull()?.takeIf { it > 0 }?.let { actions.setRetentionMaxStorageMb(it) }
                            }
                        },
                        label = { Text(stringResource(R.string.settings_retention_max_storage_mb)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done)
                    )
                }
                AppPreferences.RetentionMode.KEEP_FOREVER -> {}
            }
        }

        if (retentionMode != AppPreferences.RetentionMode.KEEP_FOREVER) {
            Text(
                text = stringResource(R.string.settings_retention_starred_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
    }
}

/** Shows the audio source, codec, and bit-rate dropdowns.
 *
 * The audio-source list is generated from [ScrcpyAudioSource.entries], filtered by
 * [ScrcpyAudioSource.isDebugOnly] based on [AppPreferences.isDebugEnabled]. Items whose
 * [ScrcpyAudioSource.minApi]/[ScrcpyAudioSource.maxApi] range does not include the current
 * device's API level are shown grayed out and cannot be selected.
 *
 * @param preferences   The [AppPreferences] instance to read data from.
 * @param updateTrigger Trigger value to force recomposition when settings change.
 * @param actions       Implementation of [SettingsActions] to handle user interaction.
 */
@Composable
private fun AudioSection(preferences: AppPreferences, updateTrigger: Int, actions: SettingsActions) {

    val isDebugEnabled = remember(updateTrigger) { preferences.isDebugEnabled() }
    val audioSource = remember(updateTrigger) { preferences.getAudioSource() }
    val savedBitRate = remember(updateTrigger) { preferences.getAudioBitRate() }
        
    SettingsSection(title = stringResource(R.string.settings_section_audio)) {
        val currentSdk = Build.VERSION.SDK_INT

        // Build the source list from the enum, hiding debug-only entries when debug is off.
        // Items that require an API level not available on this device are shown as disabled.
        val audioSourceOptions = ScrcpyAudioSource.entries
            .filter { !it.isDebugOnly || isDebugEnabled }
            .map { source ->
                OptionItem(
                    key         = source.cliKey,
                    label       = stringResource(source.titleResId),
                    description = stringResource(source.descriptionResId),
                    // Enabled only when the current SDK is within the source's API range.
                    enabled     = currentSdk >= source.minApi &&
                                  (source.maxApi == null || currentSdk <= source.maxApi)
                )
            }

        val selectedAudio = audioSourceOptions.find { it.key == audioSource }
            ?: audioSourceOptions.first()

        M3DropdownField(
            label    = stringResource(R.string.settings_audio_source),
            selected = selectedAudio,
            options  = audioSourceOptions,
            onOptionSelected = { actions.setAudioSource(it.key) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )

        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_audio_codec)) },
            supportingContent = { Text("M4A · AAC") },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        )
        val bitrateOptions = listOf(8000, 16000, 32000, 64000, 128000)
            .map { OptionItem(it.toString(), stringResource(R.string.audio_bitrate_kbps, it / 1000)) }

        M3DropdownField(
            label    = stringResource(R.string.settings_audio_bitrate),
            selected = bitrateOptions.find { it.key == savedBitRate.toString() } 
                ?: bitrateOptions.first(), // fallback gracefully if bitrate was removed from expected options
            options  = bitrateOptions,
            onOptionSelected = { actions.setAudioBitRate(it.key.toInt()) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}

/** Shows the debug toggle, and when enabled, a caller-number field and test-call buttons.
 *
 * @param preferences   The [AppPreferences] instance to read data from.
 * @param updateTrigger Trigger value to force recomposition when settings change.
 * @param actions       Implementation of [SettingsActions] to handle user interaction.
 * @param onExportLogs  Called to export logs via SAF when logging is enabled.
 */
@Composable
private fun DebugSection(preferences: AppPreferences, updateTrigger: Int, actions: SettingsActions, onExportLogs: () -> Unit) {
    val isDebugEnabled = remember(updateTrigger) { preferences.isDebugEnabled() }
    val debugCallerNumber = remember(updateTrigger) { preferences.getDebugCallerNumber() }
    val isLoggingEnabled = remember(updateTrigger) { preferences.isLoggingEnabled() }
    SettingsSection(title = stringResource(R.string.settings_section_debug)) {
        ToggleListItem(
            label           = stringResource(R.string.settings_debug_logging_enabled),
            checked         = isLoggingEnabled,
            onCheckedChange = { actions.setLoggingEnabled(it) },
            description     = if (!isLoggingEnabled) stringResource(R.string.settings_debug_logging_enabled_description) else null
        )

        AnimatedVisibility(
            visible = isLoggingEnabled,
            enter = fadeIn(animationSpec = tween(400)) +
                    expandVertically(
                        animationSpec = tween(400, easing = LinearOutSlowInEasing),
                        expandFrom = Alignment.Top
                    ),
            exit = fadeOut(animationSpec = tween(300)) +
                    shrinkVertically(
                        animationSpec = tween(300, easing = LinearOutSlowInEasing),
                        shrinkTowards = Alignment.Top
                    )
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                    Text(
                        text = stringResource(R.string.settings_debug_logging_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = stringResource(R.string.settings_debug_logging_steps),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.settings_debug_logging_step_warning),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )

                    if (isDebugEnabled) {
                        Spacer(modifier = Modifier.height(5.dp))
                        Text(
                            text = stringResource(R.string.settings_debug_logging_step_warning_no_redaction),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = onExportLogs,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.settings_debug_logging_generate_report))
                    }
                }

            }
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), thickness = 0.5.dp)

        ToggleListItem(
            label           = stringResource(R.string.settings_debug_mode),
            checked         = isDebugEnabled,
            onCheckedChange = { actions.setDebugEnabled(it) },
            description = stringResource(R.string.settings_debug_mode_description)
        )

        AnimatedVisibility(
            visible = isDebugEnabled,
            enter = fadeIn(animationSpec = tween(400)) +
                    expandVertically(
                        animationSpec = tween(400, easing = LinearOutSlowInEasing),
                        expandFrom = Alignment.Top
                    ),
            exit = fadeOut(animationSpec = tween(300)) +
                    shrinkVertically(
                        animationSpec = tween(300, easing = LinearOutSlowInEasing),
                        shrinkTowards = Alignment.Top
                    )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                var textState by remember(debugCallerNumber) { mutableStateOf(debugCallerNumber) }
                val allowedChars = "^[0-9+-]*$".toRegex()

                LaunchedEffect(textState) {
                    delay(100) // Cancel current if new textState comes in within x time
                    if (textState != debugCallerNumber) {
                        actions.setDebugCallerNumber(textState)
                    }
                }

                OutlinedTextField(
                    value    = textState,
                    onValueChange = { newValue ->
                        if (newValue.matches(allowedChars)) {
                            textState = newValue
                        }
                    },
                    label    = { Text(stringResource(R.string.settings_debug_caller_number)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, keyboardType = KeyboardType.Phone, showKeyboardOnFocus = true),
                )
                DebugActionGrid(actions)
            }

        }
    }
}

// ── Internal helper composables ────────────────────────────────────────────────────────────


/** A titled card that groups related settings together.
 *
 * @param title   Section heading shown in the app's primary colour above the card.
 * @param content The slot for child Composables rendered inside the [ElevatedCard].
 */
@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        SettingsSectionHeader(title)
        SettingsCard(content = content)
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title.uppercase(Locale.getDefault()),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 6.dp
    ) {
        Column(modifier = Modifier.padding(vertical = 2.dp), content = content)
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
    )
}

/**
 * A radio-button group for choosing which contacts to ignore.
 * When "selected" is active, shows a text field and a "Pick Contacts" button.
 *
 * @param label           Label shown above the radio buttons.
 * @param selectedEnum     The currently active mode ("none", "all", or "selected").
 * @param selectedCount    The number of contacts currently selected
 * @param onSelected      Called with the new active mode when the user taps a radio button.
 * @param onSelectContacts Called when the user taps the "Select Contacts" button; opens the
 *                        [ContactSelectionDialog] via [ContactPickerViewModel].
 */
@Composable
private fun IgnoreContactsOptions(
    label: String,
    selectedEnum: AppPreferences.IgnoreContactsMode,
    selectedCount: Int,
    onSelected: (AppPreferences.IgnoreContactsMode) -> Unit,
    onSelectContacts: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text  = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.height(8.dp))

        val enumEntries = AppPreferences.IgnoreContactsMode.entries
        enumEntries.forEach { ignoreContactMode ->
            val isCurrentlySelected = (selectedEnum == ignoreContactMode)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    // This make the box/text next to the radio button selectable, not just the button itself, which is more user-friendly.
                    .minimumInteractiveComponentSize()
                    .selectable(
                        selected = isCurrentlySelected,
                        onClick = { onSelected(ignoreContactMode) },
                        role = Role.RadioButton
                    )
                    .semantics(mergeDescendants = true) {
                    }
                    .padding(vertical = 4.dp)
            ) {
                RadioButton(selected = isCurrentlySelected, onClick = null)
                Text(
                    text = when (ignoreContactMode) {
                        AppPreferences.IgnoreContactsMode.NONE -> stringResource(R.string.settings_ignore_contacts_none)
                        AppPreferences.IgnoreContactsMode.ALL -> stringResource(R.string.settings_ignore_contacts_all)
                        AppPreferences.IgnoreContactsMode.SELECTED   -> stringResource(R.string.settings_ignore_contacts_selected)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        }
        AnimatedVisibility(
            visible = selectedEnum == AppPreferences.IgnoreContactsMode.SELECTED,
            enter = fadeIn(animationSpec = tween(400)) +
                    expandVertically(
                        animationSpec = tween(400, easing = LinearOutSlowInEasing),
                        expandFrom = Alignment.Top
                    ),
            exit = fadeOut(animationSpec = tween(300)) +
                    shrinkVertically(
                        animationSpec = tween(300, easing = LinearOutSlowInEasing),
                        shrinkTowards = Alignment.Top
                    )
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick  = onSelectContacts,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth(),
                shape = MaterialTheme.shapes.small
            ) { Text(stringResource(R.string.settings_select_contacts, selectedCount)) }
        }
    }
}

/**
 * A red warning card used to highlight important information or potential issues in the settings.
 * @param message The main warning message to display.
 * @param modifier Modifier for styling the card.
 * @param title An optional title for the warning, shown in bold red text above the main message.
 */
@Composable
fun WarningCard(
    message: String,
    modifier: Modifier = Modifier,
    title: String? = null
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        ),
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            // Warning Icon aligned to the top of text lines
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 2.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Text Content Block
            Column(
                modifier = Modifier.weight(1f)
            ) {
                if (title != null) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/** A row of buttons for simulating different call events during testing.
 *
 * @param actions Called via proxy for each button press.
 */
@Composable
private fun DebugActionGrid(actions: SettingsActions) {
    val items = listOf(
        DebugAction.RINGING  to stringResource(R.string.settings_debug_action_ringing),
        DebugAction.OFFHOOK  to stringResource(R.string.settings_debug_action_offhook),
        DebugAction.IDLE     to stringResource(R.string.settings_debug_action_idle)
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items.forEach { (action, label) ->
            FilledTonalButton(
                onClick = { actions.triggerDebugAction(action) },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                Text(label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/**
 * Safe Compose Preview for Settings.
 */
@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    ShizuCallRecorderTheme(darkTheme = false, dynamicColor = false) {
        val mockContext = LocalContext.current
        val dummyPreferences = AppPreferences(mockContext)
        val dummyActions = object : SettingsActions {
            override fun setAutoRecordIncoming(enabled: Boolean) {}
            override fun setAutoRecordOutgoing(enabled: Boolean) {}
            override fun setVibrationEnabled(enabled: Boolean) {}
            override fun setIgnoreAnonymousIncoming(enabled: Boolean) {}
            override fun setIgnoreCrossCountryIncoming(enabled: Boolean) {}
            override fun setIgnoreCrossCountryOutgoing(enabled: Boolean) {}
            override fun setIgnoreContactsModeIncoming(modeEnum: AppPreferences.IgnoreContactsMode) {}
            override fun setIgnoreContactsModeOutgoing(modeEnum: AppPreferences.IgnoreContactsMode) {}
            override fun setAudioSource(source: String) {}
            override fun setAudioCodec(codec: String) {}
            override fun setAudioBitRate(bitRate: Int) {}
            override fun setGoogleDriveBackupEnabled(enabled: Boolean) {}
            override fun setThemeMode(mode: AppPreferences.ThemeMode) {}
            override fun setDynamicColorEnabled(enabled: Boolean) {}
            override fun setShowToastsEnabled(enabled: Boolean) {}
            override fun setAppLanguage(languageCode: String) {}
            override fun setLoggingEnabled(enabled: Boolean) {}
            override fun setDebugEnabled(enabled: Boolean) {}
            override fun setDebugCallerNumber(number: String) {}
            override fun triggerDebugAction(action: DebugAction) {}
            override fun exportLogs(uri: Uri) {}
            override fun getAppVersion(): String = "Version 1.0.0 (Mock)"
            override fun setShizukuAutoManageEnabled(enabled: Boolean) {}
            override fun setShizukuStartOnRecordEnabled(enabled: Boolean) {}
            override fun setFileNameTemplate(template: String) {}
            override fun setCallDetectionMode(mode: CallDetectionMode) {}
            override fun setRecordThirdPartyCalls(enabled: Boolean) {}
            override fun setPostRecordingFileNotification(enabled: Boolean) {}
            override fun setOverlayEnabled(enabled: Boolean) {}
            override fun setRetentionMode(mode: AppPreferences.RetentionMode) {}
            override fun setRetentionMaxAgeDays(days: Int) {}
            override fun setRetentionMaxStorageMb(mb: Int) {}
        }

        // File name template selection dialog
        //FileNameFormatDialog(AppPreferences.DefaultsValue.FILE_NAME_TEMPLATE, {},{})

        SettingsContent(
            preferences = dummyPreferences,
            updateTrigger = 0,
            actions = dummyActions,
            contactPickerState = null,
            onSelectFolder = {},
            onSelectDriveBackupFolder = {},
            onOpenContactsIncoming = {},
            onOpenContactsOutgoing = {},
            onConfirmContacts = {},
            onDismissContacts = {},
            onExportLogs = {}
        )
    }
}
