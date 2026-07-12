package com.example.nanoagent.tools

import com.example.nanoagent.agent.Tool
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class TimeTool : Tool {
    override val name = "getCurrentTime"
    override val description = "Gets the current system time, day of the week, date, and timezone."
    override val parameters = emptyMap<String, String>()

    override suspend fun execute(args: Map<String, String>): String {
        return try {
            val sdf = SimpleDateFormat("EEEE, MMMM dd, yyyy HH:mm:ss", Locale.getDefault())
            val dateStr = sdf.format(Date())
            val tz = TimeZone.getDefault().displayName
            "Current Time: $dateStr, Timezone: $tz"
        } catch (e: Exception) {
            "ERROR: Could not get system time: ${e.message}"
        }
    }
}
