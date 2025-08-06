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
import com.google.ai.edge.gallery.api.NutritionQueryParams
import com.google.ai.edge.gallery.api.predictNutrition
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
    suspend fun getNutritionAnalysis(
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
    
    private suspend fun formatNutritionAnalysis(
        cattleType: String,
        targetWeight: Double,
        bodyWeight: Double,
        averageDailyGain: Double,
        prediction: NutritionPrediction,
        feedMenu: FeedMenu
    ): String {
        // Map cattle type string to integer value for API
        val typeValue = when (cattleType) {
            "Growing Steer/Heifer" -> 0
            "Growing Yearlings" -> 1
            "Growing Mature Bulls" -> 2
            else -> 1 // default to Growing Yearlings
        }
        
        val params = NutritionQueryParams(
            type_val = typeValue,
            target_weight = targetWeight.toInt(),
            body_weight = bodyWeight.toInt(),
            adg = averageDailyGain
        )
        
        val prediction = predictNutrition(params)
        Log.d("API Call", "Nutrition prediction response: $prediction")
        return prediction
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
