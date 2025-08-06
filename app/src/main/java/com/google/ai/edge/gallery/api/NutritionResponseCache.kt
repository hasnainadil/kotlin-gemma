package com.google.ai.edge.gallery.api

object NutritionResponseCache {
    lateinit var response: NutritionQueryResponse

    fun set(response: NutritionQueryResponse) {
        this.response = response
    }

    fun get(): NutritionQueryResponse {
        return response
    }
}