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
import com.google.ai.edge.gallery.nutrition.CattleNutritionService
import com.google.ai.edge.gallery.nutrition.NutritionModelFactory
import com.google.ai.edge.gallery.ui.llmchat.LlmChatModelHelper
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
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
    
    private val nutritionService = CattleNutritionService.getInstance()
    
    var isAnalyzing by mutableStateOf(false)
        private set
    
    var errorMessage by mutableStateOf<String?>(null)
        private set
    
    var isNutritionServiceInitialized by mutableStateOf(false)
        private set

    fun initializeNutritionService(context: Context) {
        if (!isNutritionServiceInitialized) {
            viewModelScope.launch(Dispatchers.IO) {
                isNutritionServiceInitialized = nutritionService.initialize(context)
                if (!isNutritionServiceInitialized) {
                    errorMessage = "Failed to initialize nutrition models"
                }
            }
        }
    }

    fun analyzeNutrition(
        context: Context,
        cattleType: String,
        targetWeight: Double,
        bodyWeight: Double,
        averageDailyGain: Double,
        modelManagerViewModel: ModelManagerViewModel,
        model: Model,
        useAIEnhancement: Boolean = false // Disabled by default to use only nutrition model
    ) {
        Log.d(TAG, "Starting cattle nutrition analysis")
        
        errorMessage = null
        
        // Initialize nutrition service if not already initialized
        if (!isNutritionServiceInitialized) {
            initializeNutritionService(context)
        }
        
        // Validate inputs using nutrition service
        val validation = nutritionService.validateInputs(cattleType, targetWeight, bodyWeight, averageDailyGain)
        if (!validation.isValid) {
            errorMessage = validation.errorMessage
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
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Always use pure nutrition analysis without AI enhancement
                val analysisResult = nutritionService.getNutritionAnalysis(
                    cattleType = cattleType,
                    targetWeight = targetWeight,
                    bodyWeight = bodyWeight,
                    averageDailyGain = averageDailyGain
                )
                
                when (analysisResult) {
                    is CattleNutritionService.NutritionAnalysisResult.Success -> {
                        updateAnalysisResult(
                            cattleType = cattleType,
                            targetWeight = targetWeight,
                            bodyWeight = bodyWeight,
                            averageDailyGain = averageDailyGain,
                            recommendation = analysisResult.formattedAnalysis,
                            isLoading = false
                        )
                        isAnalyzing = false
                    }
                    is CattleNutritionService.NutritionAnalysisResult.Error -> {
                        removeLoadingResult(cattleType, targetWeight, bodyWeight, averageDailyGain)
                        errorMessage = analysisResult.message
                        isAnalyzing = false
                    }
                }
            } catch (e: Exception) {
                removeLoadingResult(cattleType, targetWeight, bodyWeight, averageDailyGain)
                isAnalyzing = false
                errorMessage = "Analysis failed: ${e.message}"
                Log.e(TAG, "Cattle nutrition analysis exception", e)
            }
        }
    }
    
    private suspend fun enhanceWithAI(
        context: Context,
        baseRecommendation: String,
        cattleType: String,
        targetWeight: Double,
        bodyWeight: Double,
        averageDailyGain: Double,
        modelManagerViewModel: ModelManagerViewModel,
        model: Model
    ) {
        try {
            // Initialize the AI model if it's not already initialized
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
            
            // Create enhanced prompt that includes the nutrition analysis
            val enhancedPrompt = createEnhancedPrompt(
                baseRecommendation = baseRecommendation,
                cattleType = cattleType,
                targetWeight = targetWeight,
                bodyWeight = bodyWeight,
                averageDailyGain = averageDailyGain
            )
            
            Log.d(TAG, "Sending enhanced prompt to AI model")

            LlmChatModelHelper.runInference(
                model = model,
                input = enhancedPrompt,
                resultListener = { partialResult, done ->
                    // Update the loading result with AI-enhanced response
                    val combinedRecommendation = if (done) {
                        baseRecommendation + "\n\n## AI-Enhanced Insights\n\n" + partialResult
                    } else {
                        baseRecommendation + "\n\n## AI-Enhanced Insights\n\n" + partialResult
                    }
                    
                    updateAnalysisResult(
                        cattleType = cattleType,
                        targetWeight = targetWeight,
                        bodyWeight = bodyWeight,
                        averageDailyGain = averageDailyGain,
                        recommendation = combinedRecommendation,
                        isLoading = !done
                    )
                    
                    if (done) {
                        isAnalyzing = false
                        Log.d(TAG, "AI-enhanced cattle nutrition analysis completed")
                    }
                },
                cleanUpListener = {
                    isAnalyzing = false
                }
            )
        } catch (e: Exception) {
            // Fallback to base recommendation if AI enhancement fails
            updateAnalysisResult(
                cattleType = cattleType,
                targetWeight = targetWeight,
                bodyWeight = bodyWeight,
                averageDailyGain = averageDailyGain,
                recommendation = baseRecommendation + "\n\n*Note: AI enhancement unavailable*",
                isLoading = false
            )
            isAnalyzing = false
            Log.w(TAG, "AI enhancement failed, using base recommendation", e)
        }
    }
    
    private fun updateAnalysisResult(
        cattleType: String,
        targetWeight: Double,
        bodyWeight: Double,
        averageDailyGain: Double,
        recommendation: String,
        isLoading: Boolean
    ) {
        val index = _analysisResults.indexOfLast { 
            it.cattleType == cattleType && 
            it.targetWeight == targetWeight && 
            it.bodyWeight == bodyWeight && 
            it.averageDailyGain == averageDailyGain 
        }
        if (index >= 0) {
            _analysisResults[index] = _analysisResults[index].copy(
                recommendation = recommendation,
                isLoading = isLoading
            )
        }
    }
    
    private fun removeLoadingResult(
        cattleType: String,
        targetWeight: Double,
        bodyWeight: Double,
        averageDailyGain: Double
    ) {
        _analysisResults.removeAll { 
            it.cattleType == cattleType && 
            it.targetWeight == targetWeight && 
            it.bodyWeight == bodyWeight && 
            it.averageDailyGain == averageDailyGain &&
            it.isLoading
        }
    }
    private fun createEnhancedPrompt(
        baseRecommendation: String,
        cattleType: String,
        targetWeight: Double,
        bodyWeight: Double,
        averageDailyGain: Double
    ): String {
        return """
You are an expert cattle nutritionist and consultant. I have already generated a comprehensive nutrition analysis for cattle with the following characteristics:

**Cattle Information:**
- Type: $cattleType
- Current Body Weight: $bodyWeight lbs
- Target Weight: $targetWeight lbs
- Average Daily Gain (ADG): $averageDailyGain lbs/day

**Complete Nutrition Analysis:**
$baseRecommendation

Based on this detailed analysis, please provide additional insights and practical advice including:

1. **Management Tips:**
   - Best practices for feeding schedule and timing
   - Environmental considerations (weather, housing, etc.)
   - Signs to monitor for proper nutrition

2. **Cost Optimization:**
   - Alternative feed ingredients for cost savings
   - Seasonal feeding strategies
   - Bulk purchasing recommendations

3. **Health Monitoring:**
   - Key indicators of nutritional health
   - Warning signs of deficiencies
   - When to consult a veterinarian

4. **Practical Implementation:**
   - Step-by-step feeding routine
   - Equipment and storage considerations
   - Record-keeping suggestions

5. **Regional Considerations:**
   - Local feed availability tips
   - Climate-specific adjustments
   - Market timing for feed purchases

Please keep your response practical, actionable, and focused on helping the farmer implement this nutrition plan successfully.
        """.trimIndent()
    }
    
    private fun validateInputs(
        cattleType: String,
        targetWeight: Double,
        bodyWeight: Double,
        averageDailyGain: Double
    ): String? {
        // Use nutrition service validation if available
        if (isNutritionServiceInitialized) {
            val validation = nutritionService.validateInputs(cattleType, targetWeight, bodyWeight, averageDailyGain)
            return if (validation.isValid) null else validation.errorMessage
        }
        
        // Fallback validation
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
