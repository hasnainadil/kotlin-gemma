import com.google.gson.*
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileInputStream
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

data class ModelData(
    val feature_columns: List<String>,
    val target_columns: List<String>,
    val models: Map<String, RandomForestData>,
    val scalers: Map<String, Any>,
    val model_metadata: Map<String, Any>
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

    fun loadModels(inputStream: InputStream) {
        val gson = Gson()
        val jsonString = inputStream.bufferedReader().use { it.readText() }

        println("Loading models from JSON...")

        // Parse the main structure
        val jsonElement = JsonParser.parseString(jsonString)
        val jsonObject = jsonElement.asJsonObject

        // Extract basic info
        featureColumns = gson.fromJson(jsonObject.getAsJsonArray("feature_columns"),
            object : TypeToken<List<String>>() {}.type)
        targetColumns = gson.fromJson(jsonObject.getAsJsonArray("target_columns"),
            object : TypeToken<List<String>>() {}.type)

        println("Feature columns: $featureColumns")
        println("Target columns: ${targetColumns.size} targets")

        // Parse scalers
        val scalersObject = jsonObject.getAsJsonObject("scalers")
        val featureScalerData = gson.fromJson(scalersObject.getAsJsonObject("features"), ScalerData::class.java)
        featureScaler = StandardScaler(featureScalerData)

        val targetScalersObject = scalersObject.getAsJsonObject("targets")
        targetScalers = targetColumns.associateWith { targetName ->
            val scalerData = gson.fromJson(targetScalersObject.getAsJsonObject(targetName), ScalerData::class.java)
            StandardScaler(scalerData)
        }

        println("Loaded scalers for features and ${targetScalers.size} targets")

        // Parse models
        val modelsObject = jsonObject.getAsJsonObject("models")
        models = targetColumns.associateWith { targetName ->
            val modelData = gson.fromJson(modelsObject.getAsJsonObject(targetName), RandomForestData::class.java)
            println("Loaded model for $targetName: ${modelData.n_estimators} trees")
            RandomForest(modelData)
        }

        println("Successfully loaded ${models.size} models")
    }

    fun predict(type: Double, targetWeight: Double, bodyWeight: Double, adg: Double): Map<String, Double> {
        // Create feature array
        val features = doubleArrayOf(type, targetWeight, bodyWeight, adg)

        println("Input features: [type=$type, targetWeight=$targetWeight, bodyWeight=$bodyWeight, adg=$adg]")

        // Scale features
        val scaledFeatures = featureScaler.transform(features)
        println("Scaled features: ${scaledFeatures.contentToString()}")

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

        return predictions
    }

    fun predictFormatted(type: Double, targetWeight: Double, bodyWeight: Double, adg: Double): String {
        val predictions = predict(type, targetWeight, bodyWeight, adg)
        val result = StringBuilder()
        result.append("Predicted nutrition requirements:\n")
        result.append("=" .repeat(50) + "\n")

        for ((target, value) in predictions) {
            result.append("${target.padEnd(20)}: ${"%.3f".format(value)}\n")
        }

        return result.toString()
    }
}

// Mock data generator for testing when no real model file exists
object MockDataGenerator {
    fun generateMockModel(): String {
        val mockTree = TreeData(
            n_nodes = 3,
            children_left = listOf(-1, -1, -1),
            children_right = listOf(-1, -1, -1),
            feature = listOf(-2, -2, -2),
            threshold = listOf(-2.0, -2.0, -2.0),
            value = listOf(15.5, 12.3, 18.7),
            n_node_samples = listOf(100, 80, 120)
        )

        val mockForest = RandomForestData(
            n_estimators = 3,
            trees = listOf(mockTree, mockTree, mockTree),
            n_features = 4,
            n_outputs = 1
        )

        val mockScaler = ScalerData(
            mean = listOf(1.0, 1000.0, 500.0, 2.0),
            scale = listOf(0.5, 300.0, 200.0, 0.8),
            `var` = listOf(0.25, 90000.0, 40000.0, 0.64),
            n_features_in = 4
        )

        val targetScaler = ScalerData(
            mean = listOf(15.0),
            scale = listOf(5.0),
            `var` = listOf(25.0),
            n_features_in = 1
        )

        val gson = Gson()
        val jsonObject = JsonObject().apply {
            add("feature_columns", gson.toJsonTree(listOf("type", "target_weight", "Body weight (lbs)", "ADG (lbs)")))
            add("target_columns", gson.toJsonTree(listOf("DM Intake (lbs/day)", "TDN (% DM)", "NEm (Mcal/lb)")))

            val modelsObj = JsonObject().apply {
                add("DM Intake (lbs/day)", gson.toJsonTree(mockForest))
                add("TDN (% DM)", gson.toJsonTree(mockForest))
                add("NEm (Mcal/lb)", gson.toJsonTree(mockForest))
            }
            add("models", modelsObj)

            val scalersObj = JsonObject().apply {
                add("features", gson.toJsonTree(mockScaler))
                val targetScalersObj = JsonObject().apply {
                    add("DM Intake (lbs/day)", gson.toJsonTree(targetScaler))
                    add("TDN (% DM)", gson.toJsonTree(targetScaler))
                    add("NEm (Mcal/lb)", gson.toJsonTree(targetScaler))
                }
                add("targets", targetScalersObj)
            }
            add("scalers", scalersObj)

            val metadataObj = JsonObject().apply {
                addProperty("model_type", "random_forest")
                addProperty("n_features", 4)
                addProperty("n_targets", 3)
            }
            add("model_metadata", metadataObj)
        }

        return gson.toJson(jsonObject)
    }
}

fun main() {
    println("=".repeat(60))
    println("           Nutrition Predictor Test Application")
    println("=".repeat(60))

    val predictor = NutritionPredictor()

    try {
        // Try to load real model file first
        val modelFile = File("nutrition_models.json")

        if (modelFile.exists()) {
            println("Found nutrition_models.json file, loading real models...")
            val inputStream = FileInputStream(modelFile)
            predictor.loadModels(inputStream)
        } else {
            println("No nutrition_models.json found, generating mock data for testing...")
            val mockJson = MockDataGenerator.generateMockModel()
            predictor.loadModels(mockJson.byteInputStream())
        }

        println("\n" + "=".repeat(60))
        println("Running Test Predictions")
        println("=".repeat(60))

        // Test cases
        val testCases = listOf(
            TestCase("Growing Yearling - Light", 2.0, 2000.0, 1050.0, 0.5)
        )

        for ((index, testCase) in testCases.withIndex()) {
            println("\nTest Case ${index + 1}: ${testCase.name}")
            println("-".repeat(40))

            val result = predictor.predictFormatted(
                testCase.type,
                testCase.targetWeight,
                testCase.bodyWeight,
                testCase.adg
            )
            println(result)
        }

        // Performance test
//        println("\n" + "=".repeat(60))
//        println("Performance Test")
//        println("=".repeat(60))
//
//        val iterations = 1000
//        val startTime = System.currentTimeMillis()
//
//        repeat(iterations) {
//            predictor.predict(1.0, 1300.0, 780.0, 2.1)
//        }
//
//        val endTime = System.currentTimeMillis()
//        val avgTime = (endTime - startTime).toDouble() / iterations
//
//        println("Performed $iterations predictions")
//        println("Average time per prediction: ${"%.2f".format(avgTime)} ms")
//        println("Predictions per second: ${"%.0f".format(1000.0 / avgTime)}")

    } catch (e: Exception) {
        println("Error: ${e.message}")
        e.printStackTrace()
    }
}

data class TestCase(
    val name: String,
    val type: Double,
    val targetWeight: Double,
    val bodyWeight: Double,
    val adg: Double
)