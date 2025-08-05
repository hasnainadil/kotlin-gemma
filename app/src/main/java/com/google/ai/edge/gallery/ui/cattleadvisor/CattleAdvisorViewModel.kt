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

package com.google.ai.edge.gallery.ui.cattleadvisor

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.TASK_LLM_CATTLE_ADVISOR
import com.google.ai.edge.gallery.ui.llmchat.LlmChatModelHelper
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "AGCattleAdvisorViewModel"

data class CattleAdvisorResult(
    val recommendation: String,
    val timestamp: Long = System.currentTimeMillis(),
    val cattleType: String,
    val targetWeight: Double,
    val bodyWeight: Double,
    val averageDailyGain: Double,
    val isLoading: Boolean = false
)

@HiltViewModel
class CattleAdvisorViewModel @Inject constructor() : ViewModel() {
    
    private val _analysisResults = mutableListOf<CattleAdvisorResult>()
    val analysisResults: List<CattleAdvisorResult> get() = _analysisResults.toList()
    
    var isAnalyzing by mutableStateOf(false)
        private set
    
    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun analyzeNutrition(
        context: Context,
        cattleType: String,
        targetWeight: Double,
        bodyWeight: Double,
        averageDailyGain: Double,
        modelManagerViewModel: ModelManagerViewModel,
        model: Model
    ) {
        Log.d(TAG, "Starting cattle nutrition analysis")
        
        errorMessage = null
        
        // Validate inputs
        val validationError = validateInputs(cattleType, targetWeight, bodyWeight, averageDailyGain)
        if (validationError != null) {
            errorMessage = validationError
            return
        }
        
        // Add loading result immediately
        val loadingResult = CattleAdvisorResult(
            recommendation = "",
            cattleType = cattleType,
            targetWeight = targetWeight,
            bodyWeight = bodyWeight,
            averageDailyGain = averageDailyGain,
            isLoading = true
        )
        _analysisResults.add(loadingResult)
        
        isAnalyzing = true
        
        viewModelScope.launch {
            try {
                // Initialize the model if it's not already initialized
                if (model.instance == null) {
                    modelManagerViewModel.initializeModel(
                        context = context,
                        task = TASK_LLM_CATTLE_ADVISOR,
                        model = model
                    )
                    
                    // Wait for the model to be initialized
                    while (model.instance == null) {
                        kotlinx.coroutines.delay(100)
                    }
                }
                
                // Create prompt for AI model
                val prompt = createNutritionPrompt(cattleType, targetWeight, bodyWeight, averageDailyGain)
                
                Log.d(TAG, "Sending prompt to model: $prompt")

                LlmChatModelHelper.runInference(
                    model = model,
                    input = prompt,
                    resultListener = { partialResult, done ->
                        // Update the loading result with partial response by concatenating
                        val index = _analysisResults.indexOfLast { 
                            it.cattleType == cattleType && 
                            it.targetWeight == targetWeight && 
                            it.bodyWeight == bodyWeight && 
                            it.averageDailyGain == averageDailyGain 
                        }
                        if (index >= 0) {
                            val currentResult = _analysisResults[index]
                            val newContent = currentResult.recommendation + partialResult
                            _analysisResults[index] = currentResult.copy(
                                recommendation = newContent,
                                isLoading = !done
                            )
                        }
                        
                        if (done) {
                            isAnalyzing = false
                            Log.d(TAG, "Cattle nutrition analysis completed")
                        }
                    },
                    cleanUpListener = {
                        isAnalyzing = false
                    }
                )
            } catch (e: Exception) {
                // Remove the loading result on error
                _analysisResults.removeAll { 
                    it.cattleType == cattleType && 
                    it.targetWeight == targetWeight && 
                    it.bodyWeight == bodyWeight && 
                    it.averageDailyGain == averageDailyGain &&
                    it.isLoading
                }
                
                isAnalyzing = false
                errorMessage = "Analysis failed: ${e.message}"
                Log.e(TAG, "Cattle nutrition analysis exception", e)
            }
        }
    }
    
    private fun validateInputs(
        cattleType: String,
        targetWeight: Double,
        bodyWeight: Double,
        averageDailyGain: Double
    ): String? {
        if (cattleType.isEmpty()) {
            return "Please select a cattle type"
        }
        
        if (targetWeight <= 0) {
            return "Target weight must be greater than 0"
        }
        
        if (bodyWeight <= 0) {
            return "Body weight must be greater than 0"
        }
        
        if (averageDailyGain <= 0) {
            return "Average daily gain must be greater than 0"
        }
        
        if (bodyWeight >= targetWeight) {
            return "Target weight must be greater than current body weight"
        }
        
        // Validate weight limits based on cattle type (from Python validation rules)
        val maxWeight = when (cattleType) {
            "Growing Steer/Heifer" -> 1400.0
            "Growing Yearlings" -> 1400.0
            "Growing Mature Bulls" -> 2300.0
            else -> return "Invalid cattle type"
        }
        
        if (targetWeight > maxWeight) {
            return "Target weight for $cattleType should not exceed $maxWeight lbs. Please consult an expert for higher weights."
        }
        
        return null
    }
    
    private fun createNutritionPrompt(
        cattleType: String,
        targetWeight: Double,
        bodyWeight: Double,
        averageDailyGain: Double
    ): String {
        return """
You are an expert cattle nutritionist. A farmer has cattle with the following characteristics:

**Cattle Information:**
- Type: $cattleType
- Current Body Weight: $bodyWeight lbs
- Target Weight: $targetWeight lbs
- Average Daily Gain (ADG): $averageDailyGain lbs/day

**Your Task:**
Please provide a comprehensive nutrition plan that includes:

1. **Daily Nutrient Requirements:**
   - Estimated Dry Matter Intake (DMI) in lbs/day
   - Total Digestible Nutrients (TDN) percentage and amount
   - Net Energy for Maintenance (NEm) in Mcal
   - Net Energy for Gain (NEg) in Mcal
   - Crude Protein (CP) percentage and amount
   - Calcium (Ca) requirements
   - Phosphorus (P) requirements

2. **Feed Recommendation:**
   Create a daily feed menu with 5-7 common feed ingredients including:
   - Alfalfa Hay
   - Corn Silage
   - Soybean Meal (48%)
   - Ground Corn
   - Dicalcium Phosphate
   - Trace Mineral Mix
   - Salt

   For each ingredient, specify:
   - Amount in pounds of dry matter
   - Contribution to total nutrients

3. **Feeding Guidelines:**
   - Feeding frequency and timing
   - Water requirements
   - Any special considerations for this cattle type

4. **Cost Optimization:**
   - Suggest alternative feed ingredients if available
   - Mention seasonal feeding considerations

Please format your response clearly with sections and bullet points for easy reading.

**Important:** Base your calculations on standard cattle nutrition requirements and ensure the feed meets the energy and protein needs for the specified growth rate.
        """.trimIndent()
    }
    
    fun clearError() {
        errorMessage = null
    }
    
    fun clearResults() {
        _analysisResults.clear()
    }
    
    val task = TASK_LLM_CATTLE_ADVISOR
}
