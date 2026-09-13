/*
 * ShizuCallRecorder: FOSS Call recording powered through ADB/Shizuku!
 *  Copyright (C) 2026-present kitsumed (Med)
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.kitsumed.shizucallrecorder.utils

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import com.kitsumed.shizucallrecorder.system.permissions.PermissionChecks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resolves a phone number to a device contact's display name.
 *
 * Used by the recordings list to show "Mom" instead of a raw phone number when possible.
 * Never attempts any third-party/online caller-ID lookup (e.g. Truecaller) - only the device's
 * own local Contacts Provider is queried, and only after confirming the app holds
 * `READ_CONTACTS`.
 */
object ContactLookupHelper {

    /**
     * Looks up the display name of the contact matching [phoneNumber].
     *
     * @param context     App context, used to check permission and query the ContactsProvider.
     * @param phoneNumber The phone number to resolve (any common formatting is fine — the
     *                    ContactsProvider's `PhoneLookup` table normalizes for comparison).
     * @return The contact's display name, or null if the permission is missing, the number is
     *         blank, or no matching contact was found.
     */
    suspend fun findContactName(context: Context, phoneNumber: String): String? {
        if (phoneNumber.isBlank()) return null
        if (!PermissionChecks.hasContactsPermission(context)) return null

        return withContext(Dispatchers.IO) {
            try {
                val lookupUri = Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode(phoneNumber)
                )
                val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)

                context.contentResolver.query(lookupUri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME)
                        return@withContext cursor.getString(nameIndex)
                    }
                }
                null
            } catch (e: Exception) {
                AppLogger.w("Contact lookup failed for a phone number: ${e.message}")
                null
            }
        }
    }
}
