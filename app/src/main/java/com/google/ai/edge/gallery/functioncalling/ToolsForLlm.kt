/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ai.edge.gallery.functioncalling

import android.util.Log
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private const val TAG = "AGToolsForLlm"

/**
 * Tools available to the LLM for function calling.
 * These functions provide external capabilities to the model.
 */
object ToolsForLlm {
    
    /**
     * Returns weather information for a given location.
     * In a real implementation, this would call a weather API.
     */
    fun getWeather(location: String): String {
        Log.d(TAG, "Getting weather for location: $location")
        // This is a mock implementation. In production, you would call a real weather API
        return when (location.lowercase()) {
            "san francisco" -> "Cloudy, 56°F (13°C). Light fog expected."
            "new york" -> "Sunny, 72°F (22°C). Clear skies."
            "london" -> "Rainy, 48°F (9°C). Light showers throughout the day."
            "tokyo" -> "Partly cloudy, 68°F (20°C). Mild humidity."
            "paris" -> "Overcast, 52°F (11°C). Cool and breezy."
            else -> "Cloudy, 56°F (13°C). Weather data not available for specific location."
        }
    }
    
    /**
     * Returns the current time for a given timezone.
     * Uses actual system time with timezone conversion.
     */
    fun getTime(timezone: String): String {
        Log.d(TAG, "Getting time for timezone: $timezone")
        return try {
            val zoneId = when (timezone.lowercase()) {
                "pst", "pacific", "us/pacific" -> ZoneId.of("US/Pacific")
                "est", "eastern", "us/eastern" -> ZoneId.of("US/Eastern")
                "cst", "central", "us/central" -> ZoneId.of("US/Central")
                "mst", "mountain", "us/mountain" -> ZoneId.of("US/Mountain")
                "utc", "gmt" -> ZoneId.of("UTC")
                "jst", "asia/tokyo" -> ZoneId.of("Asia/Tokyo")
                "cet", "europe/paris" -> ZoneId.of("Europe/Paris")
                "gmt", "europe/london" -> ZoneId.of("Europe/London")
                else -> {
                    // Try to parse as a standard timezone ID
                    try {
                        ZoneId.of(timezone)
                    } catch (e: Exception) {
                        ZoneId.systemDefault()
                    }
                }
            }
            
            val zonedDateTime = ZonedDateTime.now(zoneId)
            val formatter = DateTimeFormatter.ofPattern("h:mm a")
            val timeString = zonedDateTime.format(formatter)
            val zoneName = zoneId.toString()
            
            "$timeString $zoneName"
        } catch (e: Exception) {
            Log.e(TAG, "Error getting time for timezone: $timezone", e)
            "Unable to get time for the specified timezone"
        }
    }
}
