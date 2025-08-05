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

import com.google.gson.*
import com.google.gson.reflect.TypeToken
import java.io.InputStream

// Data classes for JSON deserialization
data class TreeData(
    val n_nodes: Int,
    val children_left: List<Int>,
    val children_right: List<Int>,
    val feature: List<Int>,
    val threshold: List<Double>,
    val value: List<Double>,
    val n_node_samples: List<Int>
)

data class RandomForestData(
    val n_estimators: Int,
    val trees: List<TreeData>,
    val n_features: Int,
    val n_outputs: Int
)

data class ScalerData(
    val mean: List<Double>?,
    val scale: List<Double>?,
    val `var`: List<Double>?,
    val n_features_in: Int?
)

data class NutritionPrediction(
    val dryMatterIntake: Double,        // DM Intake (lbs/day)
    val tdnPercentage: Double,          // TDN (% DM)
    val nemPerLb: Double,               // NEm (Mcal/lb)
    val negPerLb: Double,               // NEg (Mcal/lb)
    val cpPercentage: Double,           // CP (% DM)
    val caPercentage: Double,           // Ca (%DM)
    val pPercentage: Double,            // P (% DM)
    val tdnLbs: Double,                 // TDN (lbs)
    val nemMcal: Double,                // NEm (Mcal)
    val negMcal: Double,                // NEg (Mcal)
    val cpLbs: Double,                  // CP (lbs)
    val caGrams: Double,                // Ca (grams)
    val pGrams: Double                  // P (grams)
)

class StandardScaler(private val scalerData: ScalerData) {
    fun transform(data: DoubleArray): DoubleArray {
        if (scalerData.mean == null || scalerData.scale == null) {
            return data.clone()
        }

        val result = DoubleArray(data.size)
        for (i in data.indices) {
            result[i] = (data[i] - scalerData.mean[i]) / scalerData.scale[i]
        }
        return result
    }

    fun inverseTransform(data: DoubleArray): DoubleArray {
        if (scalerData.mean == null || scalerData.scale == null) {
            return data.clone()
        }

        val result = DoubleArray(data.size)
        for (i in data.indices) {
            result[i] = data[i] * scalerData.scale[i] + scalerData.mean[i]
        }
        return result
    }
}

class DecisionTree(private val treeData: TreeData) {
    fun predict(features: DoubleArray): Double {
        var nodeId = 0

        while (treeData.children_left[nodeId] != -1 || treeData.children_right[nodeId] != -1) {
            val featureIdx = treeData.feature[nodeId]
            val threshold = treeData.threshold[nodeId]

            nodeId = if (features[featureIdx] <= threshold) {
                treeData.children_left[nodeId]
            } else {
                treeData.children_right[nodeId]
            }
        }

        return treeData.value[nodeId]
    }
}

class RandomForest(private val forestData: RandomForestData) {
    private val trees: List<DecisionTree> = forestData.trees.map { DecisionTree(it) }

    fun predict(features: DoubleArray): Double {
        val predictions = trees.map { it.predict(features) }
        return predictions.average()
    }
}

class NutritionPredictor {
    private lateinit var featureColumns: List<String>
    private lateinit var targetColumns: List<String>
    private lateinit var models: Map<String, RandomForest>
    private lateinit var featureScaler: StandardScaler
    private lateinit var targetScalers: Map<String, StandardScaler>
    
    companion object {
        // Cattle type mapping
        const val GROWING_STEER_HEIFER = 0.0
        const val GROWING_YEARLINGS = 1.0
        const val GROWING_MATURE_BULLS = 2.0
        
        fun getCattleTypeValue(cattleType: String): Double {
            return when (cattleType.lowercase()) {
                "growing steer/heifer", "growing_steer_heifer" -> GROWING_STEER_HEIFER
                "growing yearlings", "growing_yearlings" -> GROWING_YEARLINGS
                "growing mature bulls", "growing_mature_bulls" -> GROWING_MATURE_BULLS
                else -> GROWING_YEARLINGS // default
            }
        }
    }

    fun loadModels(inputStream: InputStream) {
        val gson = Gson()
        val jsonString = inputStream.bufferedReader().use { it.readText() }

        // Parse the main structure
        val jsonElement = JsonParser.parseString(jsonString)
        val jsonObject = jsonElement.asJsonObject

        // Extract basic info
        featureColumns = gson.fromJson(jsonObject.getAsJsonArray("feature_columns"),
            object : TypeToken<List<String>>() {}.type)
        targetColumns = gson.fromJson(jsonObject.getAsJsonArray("target_columns"),
            object : TypeToken<List<String>>() {}.type)

        // Parse scalers
        val scalersObject = jsonObject.getAsJsonObject("scalers")
        val featureScalerData = gson.fromJson(scalersObject.getAsJsonObject("features"), ScalerData::class.java)
        featureScaler = StandardScaler(featureScalerData)

        val targetScalersObject = scalersObject.getAsJsonObject("targets")
        targetScalers = targetColumns.associateWith { targetName ->
            val scalerData = gson.fromJson(targetScalersObject.getAsJsonObject(targetName), ScalerData::class.java)
            StandardScaler(scalerData)
        }

        // Parse models
        val modelsObject = jsonObject.getAsJsonObject("models")
        models = targetColumns.associateWith { targetName ->
            val modelData = gson.fromJson(modelsObject.getAsJsonObject(targetName), RandomForestData::class.java)
            RandomForest(modelData)
        }
    }

    fun predict(cattleType: String, targetWeight: Double, bodyWeight: Double, adg: Double): NutritionPrediction {
        val type = getCattleTypeValue(cattleType)
        
        // Create feature array
        val features = doubleArrayOf(type, targetWeight, bodyWeight, adg)

        // Scale features
        val scaledFeatures = featureScaler.transform(features)

        // Make predictions for each target
        val predictions = mutableMapOf<String, Double>()

        for (targetName in targetColumns) {
            val model = models[targetName] ?: continue
            val targetScaler = targetScalers[targetName] ?: continue

            // Get scaled prediction
            val scaledPrediction = model.predict(scaledFeatures)

            // Inverse transform to get actual value
            val prediction = targetScaler.inverseTransform(doubleArrayOf(scaledPrediction))[0]
            predictions[targetName] = prediction
        }

        // Map the predictions to our data class
        return NutritionPrediction(
            dryMatterIntake = predictions["DM Intake (lbs/day)"] ?: 0.0,
            tdnPercentage = predictions["TDN (% DM)"] ?: 0.0,
            nemPerLb = predictions["NEm (Mcal/lb)"] ?: 0.0,
            negPerLb = predictions["NEg (Mcal/lb)"] ?: 0.0,
            cpPercentage = predictions["CP (% DM)"] ?: 0.0,
            caPercentage = predictions["Ca (%DM)"] ?: 0.0,
            pPercentage = predictions["P (% DM)"] ?: 0.0,
            tdnLbs = predictions["TDN (lbs)"] ?: 0.0,
            nemMcal = predictions["NEm (Mcal)"] ?: 0.0,
            negMcal = predictions["NEg (Mcal)"] ?: 0.0,
            cpLbs = predictions["CP (lbs)"] ?: 0.0,
            caGrams = predictions["Ca (grams)"] ?: 0.0,
            pGrams = predictions["P (grams)"] ?: 0.0
        )
    }
}
