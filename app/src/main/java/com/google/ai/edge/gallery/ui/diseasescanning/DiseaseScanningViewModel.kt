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

package com.google.ai.edge.gallery.ui.diseasescanning

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.TASK_LLM_DISEASE_SCANNING
import com.google.ai.edge.gallery.ui.llmchat.LlmChatModelHelper
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "AGDiseaseScanningViewModel"

data class ScanResult(
    val image: Bitmap,
    val result: String,
    val isLoading: Boolean = false
)

@HiltViewModel
class DiseaseScanningViewModel @Inject constructor() : ViewModel() {

    private val _scanResults = mutableStateListOf<ScanResult>()
    val scanResults: List<ScanResult> = _scanResults

    var isScanning by mutableStateOf(false)
        private set

    fun scanImage(context: Context, modelManagerViewModel: ModelManagerViewModel, model: Model, image: Bitmap) {
        Log.d(TAG, "Starting disease scan for image")
        
        // Add loading result immediately
        val loadingResult = ScanResult(image = image, result = "", isLoading = true)
        _scanResults.add(loadingResult)
        
        isScanning = true

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Initialize the model if it's not already initialized
                if (model.instance == null) {
                    modelManagerViewModel.initializeModel(
                        context = context,
                        task = TASK_LLM_DISEASE_SCANNING,
                        model = model
                    )
                    
                    // Wait for the model to be initialized
                    while (model.instance == null) {
                        kotlinx.coroutines.delay(100)
                    }
                }
                
                val prompt = """
                    Please analyze this image for any visible signs of plant diseases, skin conditions, or other health-related abnormalities. 
                    Provide a detailed assessment including:
                    1. What you observe in the image
                    2. Potential conditions or diseases (if any)
                    3. Recommendations for next steps
                    4. Important disclaimer about consulting medical professionals for human health concerns
                    
                    Be thorough but clear in your analysis.
                """.trimIndent()

                Log.d(TAG, "Sending prompt to model: $prompt")

                LlmChatModelHelper.runInference(
                    model = model,
                    input = prompt,
                    images = listOf(image),
                    resultListener = { partialResult, done ->
                        // Update the loading result with partial response by concatenating
                        val index = _scanResults.indexOfLast { it.image == image }
                        if (index >= 0) {
                            val currentResult = _scanResults[index]
                            val newContent = currentResult.result + partialResult
                            _scanResults[index] = currentResult.copy(
                                result = newContent,
                                isLoading = !done
                            )
                        }
                        if (done) {
                            isScanning = false
                            Log.d(TAG, "Disease scan completed")
                        }
                    },
                    cleanUpListener = {
                        isScanning = false
                    }
                )
            } catch (e: Exception) {
                val index = _scanResults.indexOfLast { it.image == image }
                if (index >= 0) {
                    _scanResults[index] = _scanResults[index].copy(
                        result = "Error: ${e.message}",
                        isLoading = false
                    )
                }
                isScanning = false
                Log.e(TAG, "Disease scan exception", e)
            }
        }
    }

    fun clearResults() {
        _scanResults.clear()
    }

    val task = TASK_LLM_DISEASE_SCANNING
}
