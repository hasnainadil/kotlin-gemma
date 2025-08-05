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

data class FeedIngredient(
    val name: String,
    val tdnPercent: Double,         // Total Digestible Nutrients (%)
    val nemMcalPerLb: Double,       // Net Energy for Maintenance (Mcal/lb)
    val negMcalPerLb: Double,       // Net Energy for Gain (Mcal/lb)
    val cpPercent: Double,          // Crude Protein (%)
    val caPercent: Double,          // Calcium (%)
    val pPercent: Double            // Phosphorus (%)
)

data class FeedRecommendation(
    val ingredient: FeedIngredient,
    val amountLbsDM: Double,        // Amount in pounds of dry matter
    val tdnLbs: Double,             // TDN contribution (lbs)
    val nemMcal: Double,            // NEm contribution (Mcal)
    val negMcal: Double,            // NEg contribution (Mcal)
    val cpLbs: Double,              // CP contribution (lbs)
    val caGrams: Double,            // Ca contribution (grams)
    val pGrams: Double              // P contribution (grams)
)

data class FeedMenu(
    val recommendations: List<FeedRecommendation>,
    val totalDryMatter: Double,
    val totalTdnLbs: Double,
    val totalNemMcal: Double,
    val totalNegMcal: Double,
    val totalCpLbs: Double,
    val totalCaGrams: Double,
    val totalPGrams: Double,
    val targetRequirements: NutritionPrediction,
    val meetsRequirements: Boolean
)

class FeedRecommendationEngine {
    
    companion object {
        // Feed ingredients database based on common cattle feeds
        val FEED_INGREDIENTS = mapOf(
            "Alfalfa Hay" to FeedIngredient("Alfalfa Hay", 58.0, 0.50, 0.30, 17.0, 1.20, 0.22),
            "Corn Silage" to FeedIngredient("Corn Silage", 65.0, 0.60, 0.35, 8.0, 0.30, 0.22),
            "Soybean Meal (48%)" to FeedIngredient("Soybean Meal (48%)", 82.0, 0.70, 0.40, 48.0, 0.30, 0.65),
            "Ground Corn" to FeedIngredient("Ground Corn", 88.0, 0.90, 0.65, 9.0, 0.02, 0.28),
            "Dicalcium Phosphate" to FeedIngredient("Dicalcium Phosphate", 0.0, 0.0, 0.0, 0.0, 23.0, 18.0),
            "Trace Mineral Mix" to FeedIngredient("Trace Mineral Mix", 0.0, 0.0, 0.0, 0.0, 12.0, 8.0),
            "Salt" to FeedIngredient("Salt", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
            "Timothy Hay" to FeedIngredient("Timothy Hay", 55.0, 0.48, 0.28, 10.0, 0.50, 0.25),
            "Barley Grain" to FeedIngredient("Barley Grain", 85.0, 0.85, 0.58, 12.0, 0.05, 0.35),
            "Wheat Bran" to FeedIngredient("Wheat Bran", 68.0, 0.62, 0.32, 16.0, 0.12, 1.20)
        )
        
        // Validation rules for different cattle types
        val VALIDATION_RULES = mapOf(
            "growing_mature_bulls" to mapOf("max_target_weight" to 2300.0),
            "growing_steer_heifer" to mapOf("max_target_weight" to 1400.0),
            "growing_yearlings" to mapOf("max_target_weight" to 1400.0)
        )
    }
    
    fun generateFeedMenu(
        prediction: NutritionPrediction,
        unavailableIngredients: List<String> = emptyList()
    ): FeedMenu {
        // Filter out unavailable ingredients
        val availableIngredients = FEED_INGREDIENTS.filterKeys { 
            !unavailableIngredients.contains(it) 
        }
        
        // Create a basic feed menu using common ratios
        val recommendations = mutableListOf<FeedRecommendation>()
        
        // Start with forage base (40-60% of dry matter)
        val forageRatio = 0.5
        val forageDryMatter = prediction.dryMatterIntake * forageRatio
        
        // Use Alfalfa Hay as primary forage if available, otherwise use other hay
        val primaryForage = when {
            availableIngredients.containsKey("Alfalfa Hay") -> availableIngredients["Alfalfa Hay"]!!
            availableIngredients.containsKey("Timothy Hay") -> availableIngredients["Timothy Hay"]!!
            else -> availableIngredients.values.first { it.tdnPercent > 50 }
        }
        
        recommendations.add(createFeedRecommendation(primaryForage, forageDryMatter))
        
        // Add concentrate for energy (20-30% of dry matter)
        val concentrateRatio = 0.25
        val concentrateDryMatter = prediction.dryMatterIntake * concentrateRatio
        
        val energyFeed = when {
            availableIngredients.containsKey("Ground Corn") -> availableIngredients["Ground Corn"]!!
            availableIngredients.containsKey("Barley Grain") -> availableIngredients["Barley Grain"]!!
            else -> availableIngredients.values.first { it.tdnPercent > 80 }
        }
        
        recommendations.add(createFeedRecommendation(energyFeed, concentrateDryMatter))
        
        // Add protein source (10-15% of dry matter)
        val proteinRatio = 0.12
        val proteinDryMatter = prediction.dryMatterIntake * proteinRatio
        
        val proteinFeed = when {
            availableIngredients.containsKey("Soybean Meal (48%)") -> availableIngredients["Soybean Meal (48%)"]!!
            availableIngredients.containsKey("Wheat Bran") -> availableIngredients["Wheat Bran"]!!
            else -> availableIngredients.values.first { it.cpPercent > 15 }
        }
        
        recommendations.add(createFeedRecommendation(proteinFeed, proteinDryMatter))
        
        // Add silage if available (10-15% of dry matter)
        if (availableIngredients.containsKey("Corn Silage")) {
            val silageRatio = 0.10
            val silageDryMatter = prediction.dryMatterIntake * silageRatio
            recommendations.add(createFeedRecommendation(availableIngredients["Corn Silage"]!!, silageDryMatter))
        }
        
        // Add mineral supplements (small amounts)
        if (availableIngredients.containsKey("Dicalcium Phosphate")) {
            recommendations.add(createFeedRecommendation(availableIngredients["Dicalcium Phosphate"]!!, 0.05))
        }
        
        if (availableIngredients.containsKey("Trace Mineral Mix")) {
            recommendations.add(createFeedRecommendation(availableIngredients["Trace Mineral Mix"]!!, 0.03))
        }
        
        if (availableIngredients.containsKey("Salt")) {
            recommendations.add(createFeedRecommendation(availableIngredients["Salt"]!!, 0.02))
        }
        
        // Calculate totals
        val totalDryMatter = recommendations.sumOf { it.amountLbsDM }
        val totalTdnLbs = recommendations.sumOf { it.tdnLbs }
        val totalNemMcal = recommendations.sumOf { it.nemMcal }
        val totalNegMcal = recommendations.sumOf { it.negMcal }
        val totalCpLbs = recommendations.sumOf { it.cpLbs }
        val totalCaGrams = recommendations.sumOf { it.caGrams }
        val totalPGrams = recommendations.sumOf { it.pGrams }
        
        // Check if requirements are met (within 10% tolerance)
        val tolerance = 0.10
        val meetsRequirements = listOf(
            Math.abs(totalDryMatter - prediction.dryMatterIntake) / prediction.dryMatterIntake <= tolerance,
            Math.abs(totalTdnLbs - prediction.tdnLbs) / prediction.tdnLbs <= tolerance,
            Math.abs(totalNemMcal - prediction.nemMcal) / prediction.nemMcal <= tolerance,
            Math.abs(totalNegMcal - prediction.negMcal) / prediction.negMcal <= tolerance,
            Math.abs(totalCpLbs - prediction.cpLbs) / prediction.cpLbs <= tolerance
        ).all { it }
        
        return FeedMenu(
            recommendations = recommendations,
            totalDryMatter = totalDryMatter,
            totalTdnLbs = totalTdnLbs,
            totalNemMcal = totalNemMcal,
            totalNegMcal = totalNegMcal,
            totalCpLbs = totalCpLbs,
            totalCaGrams = totalCaGrams,
            totalPGrams = totalPGrams,
            targetRequirements = prediction,
            meetsRequirements = meetsRequirements
        )
    }
    
    private fun createFeedRecommendation(ingredient: FeedIngredient, amountLbsDM: Double): FeedRecommendation {
        return FeedRecommendation(
            ingredient = ingredient,
            amountLbsDM = amountLbsDM,
            tdnLbs = (ingredient.tdnPercent / 100.0) * amountLbsDM,
            nemMcal = ingredient.nemMcalPerLb * amountLbsDM,
            negMcal = ingredient.negMcalPerLb * amountLbsDM,
            cpLbs = (ingredient.cpPercent / 100.0) * amountLbsDM,
            caGrams = (ingredient.caPercent / 100.0) * amountLbsDM * 453.592, // Convert lbs to grams
            pGrams = (ingredient.pPercent / 100.0) * amountLbsDM * 453.592  // Convert lbs to grams
        )
    }
    
    fun formatFeedMenu(feedMenu: FeedMenu): String {
        val sb = StringBuilder()
        
        sb.append("## Daily Feed Menu\n\n")
        sb.append("### Feed Ingredients:\n")
        sb.append("| Ingredient | Amount (lbs DM) | TDN (lbs) | NEm (Mcal) | NEg (Mcal) | CP (lbs) | Ca (g) | P (g) |\n")
        sb.append("|------------|-----------------|-----------|------------|------------|----------|--------|-------|\n")
        
        for (rec in feedMenu.recommendations) {
            sb.append("| ${rec.ingredient.name.padEnd(10)} | ")
            sb.append("${String.format("%.1f", rec.amountLbsDM).padEnd(15)} | ")
            sb.append("${String.format("%.1f", rec.tdnLbs).padEnd(9)} | ")
            sb.append("${String.format("%.1f", rec.nemMcal).padEnd(10)} | ")
            sb.append("${String.format("%.1f", rec.negMcal).padEnd(10)} | ")
            sb.append("${String.format("%.2f", rec.cpLbs).padEnd(8)} | ")
            sb.append("${String.format("%.0f", rec.caGrams).padEnd(6)} | ")
            sb.append("${String.format("%.0f", rec.pGrams)} |\n")
        }
        
        sb.append("| **Total** | ")
        sb.append("**${String.format("%.1f", feedMenu.totalDryMatter)}** | ")
        sb.append("**${String.format("%.1f", feedMenu.totalTdnLbs)}** | ")
        sb.append("**${String.format("%.1f", feedMenu.totalNemMcal)}** | ")
        sb.append("**${String.format("%.1f", feedMenu.totalNegMcal)}** | ")
        sb.append("**${String.format("%.2f", feedMenu.totalCpLbs)}** | ")
        sb.append("**${String.format("%.0f", feedMenu.totalCaGrams)}** | ")
        sb.append("**${String.format("%.0f", feedMenu.totalPGrams)}** |\n\n")
        
        sb.append("### Target Requirements:\n")
        sb.append("| Requirement | Target | Provided | Status |\n")
        sb.append("|-------------|--------|----------|--------|\n")
        
        val requirements = listOf(
            "DM Intake (lbs)" to Pair(feedMenu.targetRequirements.dryMatterIntake, feedMenu.totalDryMatter),
            "TDN (lbs)" to Pair(feedMenu.targetRequirements.tdnLbs, feedMenu.totalTdnLbs),
            "NEm (Mcal)" to Pair(feedMenu.targetRequirements.nemMcal, feedMenu.totalNemMcal),
            "NEg (Mcal)" to Pair(feedMenu.targetRequirements.negMcal, feedMenu.totalNegMcal),
            "CP (lbs)" to Pair(feedMenu.targetRequirements.cpLbs, feedMenu.totalCpLbs),
            "Ca (g)" to Pair(feedMenu.targetRequirements.caGrams, feedMenu.totalCaGrams),
            "P (g)" to Pair(feedMenu.targetRequirements.pGrams, feedMenu.totalPGrams)
        )
        
        for ((name, values) in requirements) {
            val (target, provided) = values
            val status = if (Math.abs(provided - target) / target <= 0.10) "✓" else "⚠"
            sb.append("| $name | ${String.format("%.1f", target)} | ${String.format("%.1f", provided)} | $status |\n")
        }
        
        sb.append("\n### Feeding Guidelines:\n")
        sb.append("- **Feeding Frequency:** 2-3 times per day\n")
        sb.append("- **Water:** Provide fresh, clean water at all times (approximately ${String.format("%.0f", feedMenu.totalDryMatter * 3)} gallons per day)\n")
        sb.append("- **Feeding Order:** Provide roughage first, then concentrates\n")
        sb.append("- **Transition:** When changing feeds, do so gradually over 7-10 days\n")
        
        if (!feedMenu.meetsRequirements) {
            sb.append("\n⚠ **Note:** This feed menu may not fully meet all nutritional requirements. Consider adjusting ingredients or consulting with a nutritionist.\n")
        }
        
        return sb.toString()
    }
}
