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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.TASK_LLM_CATTLE_ADVISOR
import com.google.ai.edge.gallery.nutrition.CattleNutritionService
import com.google.ai.edge.gallery.nutrition.NutritionModelFactory
import com.google.ai.edge.gallery.ui.llmchat.LlmChatModelHelper
import com.google.ai.edge.gallery.ui.llmchat.LlmModelInstance
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
    val isLoading: Boolean = false,
    val showUnavailableIngredientsInput: Boolean = false,
    val unavailableIngredients: List<String> = emptyList()
)

@HiltViewModel
class CattleAdvisorViewModel @Inject constructor() : ViewModel() {
    
    private val _analysisResults = mutableStateListOf<CattleAdvisorResult>()
    val analysisResults: List<CattleAdvisorResult> get() = _analysisResults.toList()
    
    private val nutritionService = CattleNutritionService.getInstance()
    
    var isAnalyzing by mutableStateOf(false)
        private set
    
    var errorMessage by mutableStateOf<String?>(null)
        private set
    
    var isNutritionServiceInitialized by mutableStateOf(false)
        private set

    var isRetryingWithUnavailableIngredients by mutableStateOf(false)
        private set

    fun clearError() {
        errorMessage = null
    }

    fun showUnavailableIngredientsInput() {
        val latestResult = _analysisResults.lastOrNull()
        if (latestResult != null && !latestResult.isLoading) {
            val updatedResult = latestResult.copy(showUnavailableIngredientsInput = true)
            _analysisResults[_analysisResults.size - 1] = updatedResult
        }
    }

    fun retryWithUnavailableIngredients(unavailableIngredientsText: String) {
        val unavailableIngredients = unavailableIngredientsText
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        Log.d("API Call", "Processing unavailable ingredients: $unavailableIngredients")

        val latestResult = _analysisResults.lastOrNull()
        if (latestResult != null) {
            // Update the current result to show loading state for retry
            val updatedResult = latestResult.copy(
                isLoading = true,
                showUnavailableIngredientsInput = false,
                unavailableIngredients = unavailableIngredients
            )
            _analysisResults[_analysisResults.size - 1] = updatedResult
            Log.d("API Call", "Updated result to loading state")

            isRetryingWithUnavailableIngredients = true
            
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    // Call the retry API with unavailable ingredients
                    Log.d("API Call", "Calling retryWithUnavailableIngredients with: $unavailableIngredients")
                    val newRecommendation = com.google.ai.edge.gallery.api.retryWithUnavailableIngredients(unavailableIngredients)
                    Log.d("API Call", "Received alternative recommendation length: ${newRecommendation.length}")
                    Log.d("API Call", "Alternative recommendation content: $newRecommendation")
                    
                    // Update the result with new recommendation on main thread
                    viewModelScope.launch(Dispatchers.Main) {
                        val finalResult = updatedResult.copy(
                            recommendation = newRecommendation,
                            isLoading = false,
                            showUnavailableIngredientsInput = true // Keep showing the input for further modifications
                        )
                        _analysisResults[_analysisResults.size - 1] = finalResult
                        Log.d("API Call", "Updated analysis result with alternative recommendation on main thread")
                        Log.d("API Call", "Final result recommendation length: ${finalResult.recommendation.length}")
                        Log.d("API Call", "Final result loading state: ${finalResult.isLoading}")
                    }
                    
                } catch (e: Exception) {
                    Log.e("API Call", "Error retrying with unavailable ingredients", e)
                    viewModelScope.launch(Dispatchers.Main) {
                        errorMessage = "Failed to regenerate recommendation: ${e.message}"
                        
                        // Revert to previous state
                        val revertedResult = updatedResult.copy(
                            isLoading = false,
                            showUnavailableIngredientsInput = true
                        )
                        _analysisResults[_analysisResults.size - 1] = revertedResult
                    }
                } finally {
                    viewModelScope.launch(Dispatchers.Main) {
                        isRetryingWithUnavailableIngredients = false
                        Log.d("API Call", "Retry operation completed")
                    }
                }
            }
        } else {
            Log.w("API Call", "No latest result found to retry with unavailable ingredients")
        }
    }

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
        modelManagerViewModel: ModelManagerViewModel
    ) {
        Log.d(TAG, "Starting cattle nutrition analysis - no model selection required")
        
        errorMessage = null
        
        // Initialize nutrition service if not already initialized
        if (!isNutritionServiceInitialized) {
            errorMessage = "Initializing nutrition models..."
            initializeNutritionService(context)
            return
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
                // Step 1: Get nutrition analysis from the nutrition service (no model selection needed)
                val analysisResult = nutritionService.getNutritionAnalysis(
                    cattleType = cattleType,
                    targetWeight = targetWeight,
                    bodyWeight = bodyWeight,
                    averageDailyGain = averageDailyGain
                )
                
                when (analysisResult) {
                    is CattleNutritionService.NutritionAnalysisResult.Success -> {
                        // Display the nutrition analysis result directly without LLM enhancement
                        updateAnalysisResult(
                            cattleType = cattleType,
                            targetWeight = targetWeight,
                            bodyWeight = bodyWeight,
                            averageDailyGain = averageDailyGain,
                            recommendation = analysisResult.formattedAnalysis,
                            isLoading = false,
                            showUnavailableIngredientsInput = true // Show input after initial analysis
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
    
    private suspend fun enhanceWithLoRaLLM(
        context: Context,
        baseRecommendation: String,
        nutritionPredictions: Map<String, Double>,
        cattleType: String,
        targetWeight: Double,
        bodyWeight: Double,
        averageDailyGain: Double,
        modelManagerViewModel: ModelManagerViewModel
    ) {
        try {
            // Clean workflow: Always use LoRa model for cattle advisor
            // Find the LoRa DDX model specifically (specialized for livestock)
            val loraModel = TASK_LLM_CATTLE_ADVISOR.models.find { 
                !NutritionModelFactory.isNutritionModel(it) && 
                it.name.lowercase().contains("lora") && 
                it.name.lowercase().contains("ddx")
            }
            
            if (loraModel == null) {
                Log.w(TAG, "LoRa DDX model not found in cattle advisor task")
                throw IllegalStateException("LoRa DDX model not available for AI enhancement")
            }
            
            Log.d(TAG, "Using LoRa model '${loraModel.name}' for AI enhancement")
            
            // Check if the LoRa model is actually downloaded first
            val downloadStatus = modelManagerViewModel.uiState.value.modelDownloadStatus[loraModel.name]
            Log.d(TAG, "LoRa model download status: ${downloadStatus?.status}")
            if (downloadStatus?.status != com.google.ai.edge.gallery.data.ModelDownloadStatusType.SUCCEEDED) {
                Log.w(TAG, "LoRa model '${loraModel.name}' is not downloaded yet. Download status: ${downloadStatus?.status}")
                throw IllegalStateException("LoRa model not downloaded - please download the model first from the model management screen")
            }
            
            // Initialize the LoRa model if it's not already initialized
            if (loraModel.instance == null || loraModel.instance !is LlmModelInstance) {
                Log.d(TAG, "Initializing LoRa model '${loraModel.name}' for cattle advisor...")
                Log.d(TAG, "Model path: ${loraModel.getPath(context)}")
                Log.d(TAG, "Model type: ${loraModel::class.simpleName}")
                
                modelManagerViewModel.initializeModel(
                    context = context,
                    task = TASK_LLM_CATTLE_ADVISOR,
                    model = loraModel
                )
                
                // Wait for the model to be initialized with proper type checking
                var attempts = 0
                val maxAttempts = 100 // 10 seconds timeout
                while ((loraModel.instance == null || loraModel.instance !is LlmModelInstance) && attempts < maxAttempts) {
                    kotlinx.coroutines.delay(100)
                    attempts++
                    if (attempts % 10 == 0) {
                        Log.d(TAG, "Still waiting for LoRa model initialization... attempt $attempts/$maxAttempts")
                    }
                }
                
                // Check if initialization was successful
                if (loraModel.instance == null || loraModel.instance !is LlmModelInstance) {
                    Log.w(TAG, "LoRa model '${loraModel.name}' initialization failed or timed out")
                    Log.w(TAG, "Model instance: ${loraModel.instance}")
                    Log.w(TAG, "Instance type: ${loraModel.instance?.javaClass?.simpleName}")
                    throw IllegalStateException("LoRa model initialization failed")
                }
            }
            
            // Verify model instance type before using
            if (loraModel.instance !is LlmModelInstance) {
                Log.w(TAG, "Model instance is not of type LlmModelInstance: ${loraModel.instance?.javaClass?.simpleName}")
                throw IllegalStateException("Invalid model instance type")
            }
            
            // Create feed recommendation prompt based on nutrition predictions
            // This is the clean workflow: Nutrition Model → Prompt → LoRa Model → Enhanced Result
            val feedRecommendationPrompt = createFeedRecommendationPrompt(nutritionPredictions)
            
            Log.d(TAG, "Sending nutrition-based prompt to LoRa model '${loraModel.name}'")

            LlmChatModelHelper.runInference(
                model = loraModel,
                input = feedRecommendationPrompt,
                resultListener = { partialResult, done ->
                    // Update with sequential results: Nutrition Model → LoRA Model
                    val combinedRecommendation = if (done) {
                        "## Nutrition Model Analysis\n\n$baseRecommendation\n\n---\n\n## LoRA Model Enhancement\n\n$partialResult"
                    } else {
                        "## Nutrition Model Analysis\n\n$baseRecommendation\n\n---\n\n## LoRA Model Enhancement\n\n$partialResult"
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
                        Log.d(TAG, "LoRa model '${loraModel.name}' feed recommendation completed")
                    }
                },
                cleanUpListener = {
                    isAnalyzing = false
                }
            )
        } catch (e: Exception) {
            // Fallback to base recommendation if LoRa enhancement fails
            val errorMessage = when {
                e.message?.contains("initialization failed") == true -> "LoRa model initialization failed. The model may need to be re-downloaded or there may be a compatibility issue."
                e.message?.contains("not downloaded") == true -> "LoRa model is not downloaded. Please download from model management screen."
                e.message?.contains("timed out") == true -> "LoRa model initialization timed out. Please try again or restart the app."
                else -> "LoRa model unavailable due to: ${e.message}"
            }
            
            updateAnalysisResult(
                cattleType = cattleType,
                targetWeight = targetWeight,
                bodyWeight = bodyWeight,
                averageDailyGain = averageDailyGain,
                recommendation = "## Nutrition Model Analysis\n\n$baseRecommendation\n\n---\n\n## LoRA Model Enhancement\n\n*$errorMessage*\n\n**Note**: The nutrition analysis above provides scientific feeding recommendations based on cattle type, weight, and growth targets. The LoRa model would have provided additional AI-enhanced suggestions but is currently unavailable.",
                isLoading = false
            )
            isAnalyzing = false
            Log.w(TAG, "LoRa AI enhancement failed, using base recommendation (${e.message})", e)
        }
    }
    
    private fun updateAnalysisResult(
        cattleType: String,
        targetWeight: Double,
        bodyWeight: Double,
        averageDailyGain: Double,
        recommendation: String,
        isLoading: Boolean,
        showUnavailableIngredientsInput: Boolean = false
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
                isLoading = isLoading,
                showUnavailableIngredientsInput = showUnavailableIngredientsInput
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
    private fun createFeedRecommendationPrompt(nutritionPredictions: Map<String, Double>): String {
        // Feed ingredients database from Python implementation
        val feedIngredients = mapOf(
            "Alfalfa Hay" to mapOf("TDN" to 58, "NEm" to 0.50, "NEg" to 0.30, "CP" to 17, "Ca" to 1.20, "P" to 0.22),
            "Corn Silage" to mapOf("TDN" to 65, "NEm" to 0.60, "NEg" to 0.35, "CP" to 8, "Ca" to 0.30, "P" to 0.22),
            "Soybean Meal (48%)" to mapOf("TDN" to 82, "NEm" to 0.70, "NEg" to 0.40, "CP" to 48, "Ca" to 0.30, "P" to 0.65),
            "Ground Corn" to mapOf("TDN" to 88, "NEm" to 0.90, "NEg" to 0.65, "CP" to 9, "Ca" to 0.02, "P" to 0.28),
            "Dicalcium Phosphate" to mapOf("TDN" to 0, "NEm" to 0.0, "NEg" to 0.0, "CP" to 0, "Ca" to 23.00, "P" to 18.00),
            "Trace Mineral Mix" to mapOf("TDN" to 0, "NEm" to 0.0, "NEg" to 0.0, "CP" to 0, "Ca" to 12.00, "P" to 8.00),
            "Salt" to mapOf("TDN" to 0, "NEm" to 0.0, "NEg" to 0.0, "CP" to 0, "Ca" to 0.00, "P" to 0.00)
        )
        
        // Extract nutrition requirements from predictions
        val dmIntake = nutritionPredictions["DM Intake (lbs/day)"] ?: 0.0
        val tdnPercent = nutritionPredictions["TDN (% DM)"] ?: 0.0
        val tdnLbs = nutritionPredictions["TDN (lbs)"] ?: 0.0
        val nemMcalLb = nutritionPredictions["NEm (Mcal/lb)"] ?: 0.0
        val nemMcal = nutritionPredictions["NEm (Mcal)"] ?: 0.0
        val negMcalLb = nutritionPredictions["NEg (Mcal/lb)"] ?: 0.0
        val negMcal = nutritionPredictions["NEg (Mcal)"] ?: 0.0
        val cpPercent = nutritionPredictions["CP (% DM)"] ?: 0.0
        val cpLbs = nutritionPredictions["CP (lbs)"] ?: 0.0
        val caPercent = nutritionPredictions["Ca (%DM)"] ?: 0.0
        val caGrams = nutritionPredictions["Ca (grams)"] ?: 0.0
        val pPercent = nutritionPredictions["P (% DM)"] ?: 0.0
        val pGrams = nutritionPredictions["P (grams)"] ?: 0.0
        
        // Create the prompt following Python implementation
        var prompt = """You are an expert cattle nutritionist.

A cow needs the following nutrients per day:
- Dry Matter Intake (DMI): ${String.format("%.1f", dmIntake)} lbs
- Total Digestible Nutrients (TDN): ${String.format("%.1f", tdnPercent)}% of DM (${String.format("%.1f", tdnLbs)} lbs)
- Net Energy for Maintenance (NEm): ${String.format("%.2f", nemMcalLb)} Mcal/lb (${String.format("%.1f", nemMcal)} Mcal)
- Net Energy for Gain (NEg): ${String.format("%.2f", negMcalLb)} Mcal/lb (${String.format("%.1f", negMcal)} Mcal)
- Crude Protein (CP): ${String.format("%.1f", cpPercent)}% of DM (${String.format("%.2f", cpLbs)} lbs)
- Calcium (Ca): ${String.format("%.2f", caPercent)}% of DM (${String.format("%.0f", caGrams)} g)
- Phosphorus (P): ${String.format("%.2f", pPercent)}% of DM (${String.format("%.0f", pGrams)} g)

Here is a list of available feed ingredients and their nutrient values per pound of dry matter:

| Feed Ingredient        | TDN (%) | NEm (Mcal/lb) | NEg (Mcal/lb) | CP (%) | Ca (%) | P (%) |
|------------------------|---------|----------------|----------------|--------|--------|--------|"""
        
        // Add feed ingredients table
        for ((name, values) in feedIngredients) {
            val tdnVal = values["TDN"] ?: 0
            val nemVal = values["NEm"] ?: 0.0
            val negVal = values["NEg"] ?: 0.0
            val cpVal = values["CP"] ?: 0
            val caVal = values["Ca"] ?: 0.0
            val pVal = values["P"] ?: 0.0
            
            prompt += String.format(
                "\n| %-20s | %-7s | %-12s | %-12s | %-6s | %-6s | %-6s |",
                name, tdnVal, nemVal, negVal, cpVal, caVal, pVal
            )
        }
        
        prompt += """

**Your Task:**
- Design a realistic daily feed menu of 5 to 7 ingredients from the available ingredients.
- Show quantity of each ingredient in pounds of dry matter.
- Calculate and show the contribution of each to total TDN, NEm, NEg, CP, Ca, and P.
- Ensure the totals are as close as possible to the cow's requirements above.
- Keep the ingredients reasonable and commonly used.

Return a table like this:

| Ingredient            | Amount (lbs DM) | TDN (lbs) | NEm (Mcal) | NEg (Mcal) | CP (lbs) | Ca (g) | P (g) |
|-----------------------|------------------|------------|-------------|-------------|----------|--------|--------|
| Feed 1                |                  |            |             |             |          |        |        |
| ...                   |                  |            |             |             |          |        |        |
| **Total**             | ${String.format("%.1f", dmIntake)} | ${String.format("%.1f", tdnLbs)} | ${String.format("%.1f", nemMcal)} | ${String.format("%.1f", negMcal)} | ${String.format("%.2f", cpLbs)} | ${String.format("%.0f", caGrams)} | ${String.format("%.0f", pGrams)} |

After your table, list any assumptions or notes you made.

Start your response with: "Here is the feed menu that meets the cow's nutrient needs."
"""
        
        return prompt
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
    
    fun clearResults() {
        _analysisResults.clear()
    }
    
    val task = TASK_LLM_CATTLE_ADVISOR
}
