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

package com.google.ai.edge.gallery.nutrition

import android.content.Context
import android.util.Log
import java.io.IOException

class CattleNutritionService private constructor() {
    
    private var nutritionPredictor: NutritionPredictor? = null
    private val feedRecommendationEngine = FeedRecommendationEngine()
    
    companion object {
        private const val TAG = "CattleNutritionService"
        private const val NUTRITION_MODELS_FILE = "nutrition_models.json"
        
        @Volatile
        private var INSTANCE: CattleNutritionService? = null
        
        fun getInstance(): CattleNutritionService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CattleNutritionService().also { INSTANCE = it }
            }
        }
    }
    
    /**
     * Initialize the nutrition service with models from assets
     */
    fun initialize(context: Context): Boolean {
        return try {
            Log.d(TAG, "Initializing CattleNutritionService...")
            
            val inputStream = context.assets.open(NUTRITION_MODELS_FILE)
            nutritionPredictor = NutritionPredictor().apply {
                loadModels(inputStream)
            }
            
            Log.d(TAG, "CattleNutritionService initialized successfully")
            true
        } catch (e: IOException) {
            Log.e(TAG, "Failed to load nutrition models from assets", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize nutrition service", e)
            false
        }
    }
    
    /**
     * Check if the service is initialized and ready to use
     */
    fun isInitialized(): Boolean {
        return nutritionPredictor != null
    }
    
    /**
     * Validate input parameters for cattle nutrition analysis
     */
    fun validateInputs(
        cattleType: String,
        targetWeight: Double,
        bodyWeight: Double,
        averageDailyGain: Double
    ): ValidationResult {
        if (cattleType.isEmpty()) {
            return ValidationResult(false, "Please select a cattle type")
        }
        
        if (targetWeight <= 0) {
            return ValidationResult(false, "Target weight must be greater than 0")
        }
        
        if (bodyWeight <= 0) {
            return ValidationResult(false, "Body weight must be greater than 0")
        }
        
        if (averageDailyGain <= 0) {
            return ValidationResult(false, "Average daily gain must be greater than 0")
        }
        
        if (bodyWeight >= targetWeight) {
            return ValidationResult(false, "Target weight must be greater than current body weight")
        }
        
        // Validate weight limits based on cattle type
        val normalizedCattleType = normalizeCattleType(cattleType)
        val maxWeight = when (normalizedCattleType) {
            "growing_steer_heifer" -> 1400.0
            "growing_yearlings" -> 1400.0
            "growing_mature_bulls" -> 2300.0
            else -> return ValidationResult(false, "Invalid cattle type")
        }
        
        if (targetWeight > maxWeight) {
            return ValidationResult(
                false, 
                "Target weight for $cattleType should not exceed $maxWeight lbs. Please consult an expert for higher weights."
            )
        }
        
        return ValidationResult(true, "")
    }
    
    /**
     * Get comprehensive nutrition analysis including predictions and feed recommendations
     */
    fun getNutritionAnalysis(
        cattleType: String,
        targetWeight: Double,
        bodyWeight: Double,
        averageDailyGain: Double,
        unavailableIngredients: List<String> = emptyList()
    ): NutritionAnalysisResult {
        
        if (!isInitialized()) {
            return NutritionAnalysisResult.error("Nutrition service not initialized")
        }
        
        // Validate inputs
        val validation = validateInputs(cattleType, targetWeight, bodyWeight, averageDailyGain)
        if (!validation.isValid) {
            return NutritionAnalysisResult.error(validation.errorMessage)
        }
        
        return try {
            Log.d(TAG, "Getting nutrition analysis for: $cattleType, target: $targetWeight, current: $bodyWeight, ADG: $averageDailyGain")
            
            // Get nutrition predictions
            val prediction = nutritionPredictor!!.predict(
                cattleType = cattleType,
                targetWeight = targetWeight,
                bodyWeight = bodyWeight,
                adg = averageDailyGain
            )
            
            // Generate feed recommendations
            val feedMenu = feedRecommendationEngine.generateFeedMenu(
                prediction = prediction,
                unavailableIngredients = unavailableIngredients
            )
            
            // Format the complete analysis
            val formattedAnalysis = formatNutritionAnalysis(
                cattleType = cattleType,
                targetWeight = targetWeight,
                bodyWeight = bodyWeight,
                averageDailyGain = averageDailyGain,
                prediction = prediction,
                feedMenu = feedMenu
            )
            
            NutritionAnalysisResult.success(
                prediction = prediction,
                feedMenu = feedMenu,
                formattedAnalysis = formattedAnalysis
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during nutrition analysis", e)
            NutritionAnalysisResult.error("Analysis failed: ${e.message}")
        }
    }
    
    /**
     * Get only nutrition predictions without feed recommendations
     */
    fun getNutritionPrediction(
        cattleType: String,
        targetWeight: Double,
        bodyWeight: Double,
        averageDailyGain: Double
    ): NutritionPrediction? {
        
        if (!isInitialized()) {
            Log.e(TAG, "Nutrition service not initialized")
            return null
        }
        
        return try {
            nutritionPredictor!!.predict(
                cattleType = cattleType,
                targetWeight = targetWeight,
                bodyWeight = bodyWeight,
                adg = averageDailyGain
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting nutrition prediction", e)
            null
        }
    }
    
    private fun normalizeCattleType(cattleType: String): String {
        return when (cattleType.lowercase()) {
            "growing steer/heifer", "growing_steer_heifer" -> "growing_steer_heifer"
            "growing yearlings", "growing_yearlings" -> "growing_yearlings"
            "growing mature bulls", "growing_mature_bulls" -> "growing_mature_bulls"
            else -> cattleType.lowercase().replace(" ", "_")
        }
    }
    
    private fun formatNutritionAnalysis(
        cattleType: String,
        targetWeight: Double,
        bodyWeight: Double,
        averageDailyGain: Double,
        prediction: NutritionPrediction,
        feedMenu: FeedMenu
    ): String {
        val sb = StringBuilder()
        
        sb.append("# Cattle Nutrition Analysis Report\n\n")
        
        // Cattle information
        sb.append("## Cattle Information\n")
        sb.append("- **Type:** $cattleType\n")
        sb.append("- **Current Body Weight:** ${String.format("%.1f", bodyWeight)} lbs\n")
        sb.append("- **Target Weight:** ${String.format("%.1f", targetWeight)} lbs\n")
        sb.append("- **Average Daily Gain (ADG):** ${String.format("%.2f", averageDailyGain)} lbs/day\n")
        sb.append("- **Weight Gain Needed:** ${String.format("%.1f", targetWeight - bodyWeight)} lbs\n")
        sb.append("- **Estimated Days to Target:** ${String.format("%.0f", (targetWeight - bodyWeight) / averageDailyGain)} days\n\n")
        
        // Daily nutrient requirements
        sb.append("## Daily Nutrient Requirements\n")
        sb.append("- **Dry Matter Intake (DMI):** ${String.format("%.1f", prediction.dryMatterIntake)} lbs/day\n")
        sb.append("- **Total Digestible Nutrients (TDN):** ${String.format("%.1f", prediction.tdnPercentage)}% of DM (${String.format("%.1f", prediction.tdnLbs)} lbs)\n")
        sb.append("- **Net Energy for Maintenance (NEm):** ${String.format("%.2f", prediction.nemPerLb)} Mcal/lb (${String.format("%.1f", prediction.nemMcal)} Mcal)\n")
        sb.append("- **Net Energy for Gain (NEg):** ${String.format("%.2f", prediction.negPerLb)} Mcal/lb (${String.format("%.1f", prediction.negMcal)} Mcal)\n")
        sb.append("- **Crude Protein (CP):** ${String.format("%.1f", prediction.cpPercentage)}% of DM (${String.format("%.2f", prediction.cpLbs)} lbs)\n")
        sb.append("- **Calcium (Ca):** ${String.format("%.2f", prediction.caPercentage)}% of DM (${String.format("%.0f", prediction.caGrams)} g)\n")
        sb.append("- **Phosphorus (P):** ${String.format("%.2f", prediction.pPercentage)}% of DM (${String.format("%.0f", prediction.pGrams)} g)\n\n")
        
        // Feed recommendations
        sb.append(feedRecommendationEngine.formatFeedMenu(feedMenu))
        
        // Additional recommendations
        sb.append("\n## Additional Recommendations\n")
        sb.append("- **Pasture Management:** Ensure good quality pasture if available\n")
        sb.append("- **Body Condition Monitoring:** Check body condition score regularly\n")
        sb.append("- **Health Check:** Monitor for signs of illness or nutritional deficiencies\n")
        sb.append("- **Cost Optimization:** Consider seasonal availability of feeds for cost savings\n")
        sb.append("- **Professional Consultation:** Consult with a veterinarian or animal nutritionist for specific health concerns\n\n")
        
        // Disclaimer
        sb.append("## Important Note\n")
        sb.append("This analysis is based on nutritional models and should be used as a guide. ")
        sb.append("Individual cattle may have varying requirements based on genetics, health status, environmental conditions, and management practices. ")
        sb.append("Always consult with a qualified veterinarian or animal nutritionist for specific situations.\n")
        
        return sb.toString()
    }
    
    data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String
    )
    
    sealed class NutritionAnalysisResult {
        data class Success(
            val prediction: NutritionPrediction,
            val feedMenu: FeedMenu,
            val formattedAnalysis: String
        ) : NutritionAnalysisResult()
        
        data class Error(val message: String) : NutritionAnalysisResult()
        
        companion object {
            fun success(prediction: NutritionPrediction, feedMenu: FeedMenu, formattedAnalysis: String) =
                Success(prediction, feedMenu, formattedAnalysis)
            
            fun error(message: String) = Error(message)
        }
    }
}
