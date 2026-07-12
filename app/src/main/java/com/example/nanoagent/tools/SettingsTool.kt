package com.example.nanoagent.tools

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.example.nanoagent.agent.Tool

class SettingsTool(private val context: Context) : Tool {
    override val name = "changeSetting"
    override val description = "Adjusts phone settings. Support settings: 'volume' (0 to 100), 'brightness' (0 to 100)."
    override val parameters = mapOf(
        "setting" to "The setting name to change ('volume' or 'brightness')",
        "value" to "The integer target value for the setting (0 to 100)"
    )

    override suspend fun execute(args: Map<String, String>): String {
        val setting = args["setting"]?.lowercase() ?: return "ERROR: Missing 'setting' parameter."
        val valStr = args["value"] ?: return "ERROR: Missing 'value' parameter."
        val targetVal = valStr.toIntOrNull() ?: return "ERROR: 'value' must be an integer."

        return when (setting) {
            "volume" -> {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                // Normalize 0-100 value to maxVolume range
                val targetVol = (targetVal.coerceIn(0, 100) * maxVolume) / 100
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, AudioManager.FLAG_SHOW_UI)
                "Successfully changed music volume to $targetVal% (level $targetVol of $maxVolume)"
            }
            "brightness" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(context)) {
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                            data = Uri.parse("package:${context.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                        "ERROR: WRITE_SETTINGS permission is not granted. I have automatically opened the settings screen for you. Please enable 'Allow modifying system settings' (Разрешить изменение системных настроек) and request this action again."
                    } catch (e: Exception) {
                        "ERROR: WRITE_SETTINGS permission not granted. Please go to Settings -> Apps -> Special App Access -> Modify System Settings and enable it for NanoAgent."
                    }
                } else {
                    try {
                        // 1. Force manual brightness mode to prevent adaptive brightness from overriding our change
                        Settings.System.putInt(
                            context.contentResolver,
                            Settings.System.SCREEN_BRIGHTNESS_MODE,
                            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                        )
                        // 2. Convert percentage (0-100) to system value (0-255)
                        val systemBrightnessVal = (targetVal.coerceIn(0, 100) * 255) / 100
                        Settings.System.putInt(
                            context.contentResolver,
                            Settings.System.SCREEN_BRIGHTNESS,
                            systemBrightnessVal
                        )
                        "Successfully changed screen brightness to $targetVal% (level $systemBrightnessVal of 255)"
                    } catch (e: Exception) {
                        "ERROR: Could not change brightness: ${e.message}"
                    }
                }
            }
            else -> "ERROR: Unsupported setting '$setting'. Only 'volume' and 'brightness' are supported."
        }
    }
}
