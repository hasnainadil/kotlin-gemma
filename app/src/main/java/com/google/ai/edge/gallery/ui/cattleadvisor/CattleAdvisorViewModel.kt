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
        useAIEnhancement: Boolean = true
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
        
        // Check if this is nutrition model workflow and validate LLM availability
        if (NutritionModelFactory.isNutritionModel(model)) {
            // For nutrition workflow, we need an LLM model to be available
            val availableLLMs = modelManagerViewModel.uiState.value.tasks
                .flatMap { it.models }
                .filter { it != model && !NutritionModelFactory.isNutritionModel(it) }
                .filter { modelManagerViewModel.uiState.value.modelDownloadStatus[it.name]?.status == com.google.ai.edge.gallery.data.ModelDownloadStatusType.SUCCEEDED }
            
            if (availableLLMs.isEmpty()) {
                errorMessage = "Nutrition analysis requires an AI model to be downloaded. Please download an AI model (like LoRa-3n-DDX-ft-int4) from the model manager first."
                return
            }
            
            // Use the first available LLM model for processing
            val llmModel = availableLLMs.first()
            Log.d(TAG, "Using LLM model: ${llmModel.name} for nutrition workflow")
            
            // Continue with nutrition workflow using the available LLM
            processNutritionWorkflow(
                context, cattleType, targetWeight, bodyWeight, averageDailyGain,
                modelManagerViewModel, llmModel
            )
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
                // For non-nutrition models, use AI model with optional nutrition enhancement
                val analysisResult = nutritionService.getNutritionAnalysis(
                    cattleType = cattleType,
                    targetWeight = targetWeight,
                    bodyWeight = bodyWeight,
                    averageDailyGain = averageDailyGain
                )
                
                when (analysisResult) {
                    is CattleNutritionService.NutritionAnalysisResult.Success -> {
                        val baseRecommendation = analysisResult.formattedAnalysis
                        
                        if (useAIEnhancement && model.instance != null) {
                            // Enhance with AI model for additional insights
                            enhanceWithAI(
                                context = context,
                                baseRecommendation = baseRecommendation,
                                cattleType = cattleType,
                                targetWeight = targetWeight,
                                bodyWeight = bodyWeight,
                                averageDailyGain = averageDailyGain,
                                modelManagerViewModel = modelManagerViewModel,
                                model = model
                            )
                        } else {
                            // Use only the nutrition model results
                            updateAnalysisResult(
                                cattleType = cattleType,
                                targetWeight = targetWeight,
                                bodyWeight = bodyWeight,
                                averageDailyGain = averageDailyGain,
                                recommendation = baseRecommendation,
                                isLoading = false
                            )
                            isAnalyzing = false
                        }
                    }
                    is CattleNutritionService.NutritionAnalysisResult.Error -> {
                        // Fallback to AI-only analysis if nutrition service fails
                        if (useAIEnhancement && model.instance != null) {
                            enhanceWithAI(
                                context = context,
                                baseRecommendation = "",
                                cattleType = cattleType,
                                targetWeight = targetWeight,
                                bodyWeight = bodyWeight,
                                averageDailyGain = averageDailyGain,
                                modelManagerViewModel = modelManagerViewModel,
                                model = model
                            )
                        } else {
                            removeLoadingResult(cattleType, targetWeight, bodyWeight, averageDailyGain)
                            errorMessage = analysisResult.message
                            isAnalyzing = false
                        }
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
    
    private fun processNutritionWorkflow(
        context: Context,
        cattleType: String,
        targetWeight: Double,
        bodyWeight: Double,
        averageDailyGain: Double,
        modelManagerViewModel: ModelManagerViewModel,
        llmModel: Model
    ) {
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
                // Step 1: Get nutrition predictions only
                val nutritionPrediction = nutritionService.getNutritionPrediction(
                    cattleType = cattleType,
                    targetWeight = targetWeight,
                    bodyWeight = bodyWeight,
                    averageDailyGain = averageDailyGain
                )
                
                if (nutritionPrediction != null) {
                    // Step 2: Generate LLM prompt with nutrition requirements and ingredients
                    val feedPrompt = nutritionService.generateFeedRecommendationPrompt(
                        prediction = nutritionPrediction,
                        unavailableIngredients = emptyList()
                    )
                    
                    Log.d(TAG, "Generated feed prompt for LLM processing")
                    Log.d(TAG, "Prompt preview: ${feedPrompt.take(200)}...")
                    
                    // Step 3: Send prompt to LLM model for feed recommendations
                    enhanceWithAI(
                        context = context,
                        baseRecommendation = feedPrompt,
                        cattleType = cattleType,
                        targetWeight = targetWeight,
                        bodyWeight = bodyWeight,
                        averageDailyGain = averageDailyGain,
                        modelManagerViewModel = modelManagerViewModel,
                        model = llmModel, // Use the selected LLM model
                        isNutritionWorkflow = true
                    )
                } else {
                    removeLoadingResult(cattleType, targetWeight, bodyWeight, averageDailyGain)
                    errorMessage = "Failed to get nutrition predictions"
                    isAnalyzing = false
                }
            } catch (e: Exception) {
                removeLoadingResult(cattleType, targetWeight, bodyWeight, averageDailyGain)
                isAnalyzing = false
                errorMessage = "Nutrition workflow failed: ${e.message}"
                Log.e(TAG, "Nutrition workflow exception", e)
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
        model: Model,
        isNutritionWorkflow: Boolean = false
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
            
            // Create enhanced prompt based on workflow type
            val promptToSend = if (isNutritionWorkflow) {
                // For nutrition workflow, send the generated prompt directly to LLM
                baseRecommendation
            } else {
                // For regular workflow, enhance the base recommendation
                createEnhancedPrompt(
                    baseRecommendation = baseRecommendation,
                    cattleType = cattleType,
                    targetWeight = targetWeight,
                    bodyWeight = bodyWeight,
                    averageDailyGain = averageDailyGain
                )
            }
            
            Log.d(TAG, "Sending ${if (isNutritionWorkflow) "nutrition" else "enhanced"} prompt to AI model")
            Log.d(TAG, "Prompt length: ${promptToSend.length} characters")
            Log.d(TAG, "Model instance available: ${model.instance != null}")

            LlmChatModelHelper.runInference(
                model = model,
                input = promptToSend,
                resultListener = { partialResult, done ->
                    Log.d(TAG, "LLM Response - Done: $done, Length: ${partialResult.length}")
                    
                    val finalRecommendation = if (isNutritionWorkflow) {
                        // For nutrition workflow, show only the LLM response
                        if (partialResult.trim().isEmpty()) {
                            "LLM processing... (No response yet)"
                        } else {
                            partialResult
                        }
                    } else {
                        // For regular workflow, combine base + AI enhancement
                        baseRecommendation + "\n\n## AI-Enhanced Insights\n\n" + partialResult
                    }
                    
                    updateAnalysisResult(
                        cattleType = cattleType,
                        targetWeight = targetWeight,
                        bodyWeight = bodyWeight,
                        averageDailyGain = averageDailyGain,
                        recommendation = finalRecommendation,
                        isLoading = !done
                    )
                    
                    if (done) {
                        isAnalyzing = false
                        Log.d(TAG, "AI-enhanced cattle nutrition analysis completed")
                    }
                },
                cleanUpListener = {
                    Log.d(TAG, "LLM inference cleanup called")
                    isAnalyzing = false
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error during LLM inference", e)
            
            // Fallback handling based on workflow type
            val fallbackMessage = if (isNutritionWorkflow) {
                "Feed recommendation generation failed. Error: ${e.message}\n\nThe nutrition model predictions were successful, but the AI model couldn't process the feed recommendations. Please ensure the AI model is properly downloaded and try again."
            } else {
                baseRecommendation + "\n\n*Note: AI enhancement unavailable due to error: ${e.message}*"
            }
            
            updateAnalysisResult(
                cattleType = cattleType,
                targetWeight = targetWeight,
                bodyWeight = bodyWeight,
                averageDailyGain = averageDailyGain,
                recommendation = fallbackMessage,
                isLoading = false
            )
            isAnalyzing = false
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
