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
import com.google.ai.edge.gallery.data.Model
import org.json.JSONObject

private const val TAG = "AGFunctionCallingHelper"

/**
 * Helper class for simulating function calling with LLM models.
 * This is a simplified implementation that demonstrates function calling concepts
 * without the full Function Calling SDK.
 */
object FunctionCallingHelper {
    
    /**
     * Checks if a model supports function calling.
     * Currently, we enable function calling for Gemma models only.
     */
    fun supportsFunctionCalling(model: Model): Boolean {
        // Check if the model name contains "gemma" (case insensitive)
        return model.name.lowercase().contains("gemma")
    }
    
    /**
     * Processes a user input and determines if it requires function calling.
     * Returns a function call result if applicable, or null if no function should be called.
     */
    fun processFunctionCalling(input: String): FunctionCallResult? {
        val lowerInput = input.lowercase()
        
        return when {
            lowerInput.contains("weather") -> {
                val location = extractLocation(input) ?: "San Francisco"
                val result = ToolsForLlm.getWeather(location)
                FunctionCallResult(
                    functionName = "getWeather",
                    parameters = mapOf("location" to location),
                    result = result
                )
            }
            lowerInput.contains("time") -> {
                val timezone = extractTimezone(input) ?: "PST"
                val result = ToolsForLlm.getTime(timezone)
                FunctionCallResult(
                    functionName = "getTime", 
                    parameters = mapOf("timezone" to timezone),
                    result = result
                )
            }
            else -> null
        }
    }
    
    /**
     * Extracts location from user input for weather queries.
     */
    private fun extractLocation(input: String): String? {
        val patterns = listOf(
            "weather in (.+?)($|\\s|\\?|\\.|,)",
            "weather for (.+?)($|\\s|\\?|\\.|,)",
            "weather at (.+?)($|\\s|\\?|\\.|,)"
        )
        
        for (pattern in patterns) {
            val regex = Regex(pattern, RegexOption.IGNORE_CASE)
            val match = regex.find(input)
            if (match != null) {
                return match.groups[1]?.value?.trim()
            }
        }
        
        // Common city names detection
        val cities = listOf(
            "san francisco", "new york", "london", "tokyo", "paris", 
            "seattle", "chicago", "boston", "miami", "denver"
        )
        
        for (city in cities) {
            if (input.lowercase().contains(city)) {
                return city
            }
        }
        
        return null
    }
    
    /**
     * Extracts timezone from user input for time queries.
     */
    private fun extractTimezone(input: String): String? {
        val patterns = listOf(
            "time in (.+?)($|\\s|\\?|\\.|,)",
            "time for (.+?)($|\\s|\\?|\\.|,)",
            "time at (.+?)($|\\s|\\?|\\.|,)"
        )
        
        for (pattern in patterns) {
            val regex = Regex(pattern, RegexOption.IGNORE_CASE)
            val match = regex.find(input)
            if (match != null) {
                return match.groups[1]?.value?.trim()
            }
        }
        
        // Common timezone/location detection
        val timezones = mapOf(
            "pst" to "PST", "pacific" to "PST", "california" to "PST", "san francisco" to "PST",
            "est" to "EST", "eastern" to "EST", "new york" to "EST", "boston" to "EST",
            "cst" to "CST", "central" to "CST", "chicago" to "CST",
            "mst" to "MST", "mountain" to "MST", "denver" to "MST",
            "utc" to "UTC", "gmt" to "GMT", "london" to "GMT",
            "jst" to "JST", "tokyo" to "JST", "japan" to "JST"
        )
        
        for ((key, value) in timezones) {
            if (input.lowercase().contains(key)) {
                return value
            }
        }
        
        return null
    }
    
    /**
     * Creates a system prompt that instructs the model to work with function calling.
     */
    fun createFunctionCallingSystemPrompt(): String {
        return """You are a helpful assistant with access to external functions for getting weather information and current time.

When users ask about weather, I can call the getWeather function with a location parameter.
When users ask about time, I can call the getTime function with a timezone parameter.

Available functions:
- getWeather(location): Returns weather information for the specified location
- getTime(timezone): Returns current time for the specified timezone

If the user's request can be fulfilled with these functions, I will call the appropriate function and provide a helpful response based on the results. If the request doesn't need these functions, I will respond normally."""
    }
    
    /**
     * Formats a function call result for display to the user.
     */
    fun formatFunctionCallResponse(functionCall: FunctionCallResult, llmResponse: String): String {
        return """🔧 **Function Called**: ${functionCall.functionName}
📊 **Parameters**: ${functionCall.parameters.entries.joinToString(", ") { "${it.key}: ${it.value}" }}
📋 **Result**: ${functionCall.result}

💬 **AI Response**:
$llmResponse"""
    }
}

/**
 * Data class representing a function call result.
 */
data class FunctionCallResult(
    val functionName: String,
    val parameters: Map<String, String>,
    val result: String
)
