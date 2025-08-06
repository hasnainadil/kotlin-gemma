package com.google.ai.edge.gallery.api

import android.util.Log
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

data class NutritionQueryParams(
    val type_val: Int,
    val target_weight: Int,
    val body_weight: Int,
    val adg: Double
)

data class NutritionQueryResponse(
    val dmIntakePerDay: Double,
    val tdnPercentDm: Double,
    val nemMcalPerLb: Double,
    val negMcalPerLb: Double,
    val cpPercentDm: Double,
    val caPercentDm: Double,
    val pPercentDm: Double,
    val tdnLbs: Double,
    val nemMcal: Double,
    val negMcal: Double,
    val cpLbs: Double,
    val caGrams: Double,
    val pGrams: Double,
)

data class FeedRecommendationQuery(
    val predictions: NutritionQueryResponse,
    val unavailable_ingredients: List<String>? = null
)

data class RecommendationResponse(val recommendation: String)

interface NutritionAPI {
    @POST("/predict/nutrition")
    suspend fun predictNutrition(@Body params: NutritionQueryParams): Response<NutritionQueryResponse>

    @POST("/generate/feed-recommendation")
    suspend fun generateFeed(@Body params: FeedRecommendationQuery): Response<RecommendationResponse>
}

suspend fun predictNutrition(params: NutritionQueryParams): String {
    Log.d("API Call", "Predicting nutrition with params: $params")
    val response = RetrofitInstance.nutritionAPI.predictNutrition(params)
    val body = response.body()!!
    NutritionResponseCache.set(body);
    Log.d("API Call", "Nutrition prediction response: $body")

    val recommendation = RetrofitInstance.nutritionAPI.generateFeed(FeedRecommendationQuery(predictions = body)).body()
    Log.d("API Call", "Feed recommendation response: $recommendation")
    return recommendation?.recommendation ?: "No recommendation available"
}

suspend fun retryWithUnavailableIngredients(unavailable_ingredients: List<String>): String {
    val response = RetrofitInstance.nutritionAPI.generateFeed(FeedRecommendationQuery(predictions = NutritionResponseCache.get(), unavailable_ingredients))
    val body = response.body()!!
    return body.recommendation
}