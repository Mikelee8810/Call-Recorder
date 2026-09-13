/*
 * ShizuCallRecorder: FOSS Call recording powered through ADB/Shizuku!
 *  Copyright (C) 2026-present kitsumed (Med)
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.kitsumed.shizucallrecorder

import android.app.Application
import android.os.UserManager
import com.kitsumed.shizucallrecorder.integrations.shizuku.ShizukuConnectionManager
import com.kitsumed.shizucallrecorder.integrations.shizuku.ShizukuWatchdogReceiver
import com.kitsumed.shizucallrecorder.services.callDetection.CallDetectionOrchestrator
import com.kitsumed.shizucallrecorder.services.watchdog.RecorderKeepAliveService
import com.kitsumed.shizucallrecorder.utils.AppLogger

/**
 * ShizuApplication is run when the app process is created. Can be seen as the very first entry point of the app.
 */
class ShizuApplication : Application() {
    private var unlockedRuntimeInitialized = false

    override fun onCreate() {
        super.onCreate()
        // Arms the periodic check even if the app is launched without a reboot in between
        // (fresh install, or the setting was just turned on).
        ShizukuWatchdogReceiver.schedule(applicationContext)
        ShizukuConnectionManager.installLifecycleListeners(applicationContext) {
            ShizukuWatchdogReceiver.onShizukuReconnected(applicationContext)
        }
        // Keep the recorder process ready between calls. If Android rejects a background FGS start,
        // the boot/alarm watchdog will retry from its allowed entry point.
        RecorderKeepAliveService.ensureRunning(applicationContext)

        initializeUnlockedRuntimeIfAvailable()
    }

    /**
     * Initializes anything that reads credential-protected preferences/files.
     * During a cold reboot the direct-boot watchdog may create the process before the first
     * unlock, when those stores are intentionally unavailable. USER_UNLOCKED calls this again.
     */
    fun initializeUnlockedRuntimeIfAvailable(): Boolean {
        if (unlockedRuntimeInitialized) return true
        val userManager = getSystemService(UserManager::class.java)
        if (userManager?.isUserUnlocked != true) return false

        AppLogger.init(applicationContext)
        CallDetectionOrchestrator(applicationContext).syncComponents()
        unlockedRuntimeInitialized = true
        return true
    }
}
