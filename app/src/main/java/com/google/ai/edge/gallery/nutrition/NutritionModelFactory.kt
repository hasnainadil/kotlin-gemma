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

import com.google.ai.edge.gallery.data.Accelerator
import com.google.ai.edge.gallery.data.Config
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.createLlmChatConfigs

/**
 * Factory for creating the nutrition model entry that integrates with the app's model system
 */
object NutritionModelFactory {
    
    /**
     * Creates a virtual model entry for the nutrition prediction system
     * This allows the nutrition model to appear alongside AI models in the cattle advisor task
     */
    fun createNutritionModel(): Model {
        val configs: List<Config> = createLlmChatConfigs(
            defaultMaxToken = 2048,
            defaultTopK = 50,
            defaultTopP = 0.9f,
            defaultTemperature = 0.7f,
            accelerators = listOf(Accelerator.CPU, Accelerator.GPU)
        )
        
        val model = Model(
            name = "Cattle Nutrition Predictor",
            url = "", // No download needed - models are in assets
            configs = configs,
            sizeInBytes = 0L, // Already included in app assets
            downloadFileName = "", // Not applicable
            showBenchmarkButton = false,
            showRunAgainButton = false,
            imported = false,
            llmSupportImage = false,
            llmSupportAudio = false,
            info = "Scientific nutrition prediction model using Random Forest algorithms trained on cattle nutrition datasets. Provides precise feed recommendations based on cattle type, weight, and growth targets.",
            learnMoreUrl = "https://github.com/hasnainadil/kotlin-gemma"
        )
        
        model.preProcess()
        
        // Mark this as a special nutrition model
        model.instance = "NUTRITION_MODEL_MARKER"
        
        return model
    }
    
    /**
     * Checks if a model is the nutrition prediction model
     */
    fun isNutritionModel(model: Model): Boolean {
        return model.name == "Cattle Nutrition Predictor" || 
               model.instance == "NUTRITION_MODEL_MARKER"
    }
}
