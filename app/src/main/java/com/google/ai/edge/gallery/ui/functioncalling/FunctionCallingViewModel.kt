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

package com.google.ai.edge.gallery.ui.functioncalling

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.gallery.data.TASK_LLM_FUNCTION_CALLING
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.functioncalling.FunctionCallingHelper
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageLoading
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageText
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageType
import com.google.ai.edge.gallery.ui.common.chat.ChatSide
import com.google.ai.edge.gallery.ui.common.chat.ChatViewModel
import com.google.ai.edge.gallery.ui.llmchat.LlmChatModelHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "AGFunctionCallingViewModel"

@HiltViewModel
class FunctionCallingViewModel @Inject constructor() : ChatViewModel(task = TASK_LLM_FUNCTION_CALLING) {
    
    /**
     * Generates a response using function calling capabilities.
     */
    fun generateResponseWithFunctionCalling(
        model: Model,
        input: String,
        onError: () -> Unit
    ) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                setInProgress(true)
                setPreparing(true)
                
                // Add loading message
                addMessage(model = model, message = ChatMessageLoading())
                
                // Check if the input requires function calling
                val functionCall = FunctionCallingHelper.processFunctionCalling(input)
                
                if (functionCall != null) {
                    // Function call is needed
                    Log.d(TAG, "Function call detected: ${functionCall.functionName}")
                    
                    // Remove loading message and show function call info
                    removeLastMessage(model = model)
                    addMessage(
                        model = model,
                        message = ChatMessageText(
                            content = "🔧 Calling function: ${functionCall.functionName}(${functionCall.parameters.entries.joinToString(", ") { "${it.key}='${it.value}'" }})\n📊 Result: ${functionCall.result}",
                            side = ChatSide.AGENT
                        )
                    )
                    
                    // Create a modified prompt that includes the function result
                    val enhancedPrompt = """${FunctionCallingHelper.createFunctionCallingSystemPrompt()}

User asked: "$input"

Function call result:
- Function: ${functionCall.functionName}
- Parameters: ${functionCall.parameters}
- Result: ${functionCall.result}

Please provide a helpful response to the user based on this function call result."""
                    
                    // Generate LLM response with the function result context
                    generateLlmResponse(model, enhancedPrompt, functionCall, onError)
                } else {
                    // No function call needed, proceed with normal LLM response
                    val systemPrompt = FunctionCallingHelper.createFunctionCallingSystemPrompt()
                    val fullPrompt = "$systemPrompt\n\nUser: $input"
                    
                    // Remove loading message
                    removeLastMessage(model = model)
                    addMessage(
                        model = model,
                        message = ChatMessageText(content = "", side = ChatSide.AGENT)
                    )
                    
                    generateLlmResponse(model, fullPrompt, null, onError)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error generating response with function calling", e)
                setInProgress(false)
                setPreparing(false)
                onError()
            }
        }
    }
    
    /**
     * Generates the actual LLM response using the existing LLM infrastructure.
     */
    private fun generateLlmResponse(
        model: Model,
        prompt: String,
        functionCall: com.google.ai.edge.gallery.functioncalling.FunctionCallResult?,
        onError: () -> Unit
    ) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                setPreparing(false)
                
                // Use the existing LLM helper to generate response
                LlmChatModelHelper.runInference(
                    model = model,
                    input = prompt,
                    resultListener = { partialResult, done ->
                        if (done) {
                            // Format the final response
                            val finalResponse = if (functionCall != null) {
                                FunctionCallingHelper.formatFunctionCallResponse(functionCall, partialResult)
                            } else {
                                partialResult
                            }
                            
                            // Update the last message with the final response
                            replaceLastMessage(
                                model = model,
                                message = ChatMessageText(
                                    content = finalResponse,
                                    side = ChatSide.AGENT
                                ),
                                type = ChatMessageType.TEXT
                            )
                        } else {
                            // For streaming updates, just update the text content
                            val currentResponse = if (functionCall != null) {
                                "🔧 **Function Called**: ${functionCall.functionName}\n📊 **Parameters**: ${functionCall.parameters.entries.joinToString(", ") { "${it.key}: ${it.value}" }}\n📋 **Result**: ${functionCall.result}\n\n💬 **AI Response**:\n$partialResult"
                            } else {
                                partialResult
                            }
                            
                            replaceLastMessage(
                                model = model,
                                message = ChatMessageText(
                                    content = currentResponse,
                                    side = ChatSide.AGENT
                                ),
                                type = ChatMessageType.TEXT
                            )
                        }
                    },
                    cleanUpListener = {
                        setInProgress(false)
                        setPreparing(false)
                    }
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "Error generating LLM response", e)
                setInProgress(false)
                setPreparing(false)
                onError()
            }
        }
    }
    
    /**
     * Resets the chat session for function calling.
     */
    fun resetFunctionCallingSession(model: Model) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                clearAllMessages(model = model)
                LlmChatModelHelper.resetSession(model = model)
                Log.d(TAG, "Function calling session reset for model: ${model.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Error resetting function calling session", e)
            }
        }
    }
    
    /**
     * Checks if a model has function calling capabilities.
     */
    fun hasFunctionCallingCapabilities(model: Model): Boolean {
        return FunctionCallingHelper.supportsFunctionCalling(model)
    }
}
