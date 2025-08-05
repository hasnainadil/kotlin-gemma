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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.ai.edge.gallery.data.TASK_LLM_FUNCTION_CALLING
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.functioncalling.FunctionCallingHelper
import com.google.ai.edge.gallery.ui.common.chat.ChatMessage
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageText
import com.google.ai.edge.gallery.ui.common.chat.ChatPanel
import com.google.ai.edge.gallery.ui.common.chat.ChatSide
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object FunctionCallingDestination {
    val route = "FunctionCallingRoute"
}

@Composable
fun FunctionCallingScreen(
    modelManagerViewModel: ModelManagerViewModel,
    navigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FunctionCallingViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val modelManagerUiState by modelManagerViewModel.uiState.collectAsState()
    val selectedModel = modelManagerUiState.selectedModel
    val task = TASK_LLM_FUNCTION_CALLING
    
    // Initialize function calling when model is ready
    LaunchedEffect(selectedModel.name) {
        // No special initialization needed for simplified approach
        // Just log that the model is ready for function calling
        if (FunctionCallingHelper.supportsFunctionCalling(selectedModel)) {
            Log.d("FunctionCallingScreen", "Model ${selectedModel.name} is ready for function calling")
        }
    }
    
    // Check if the selected model supports function calling
    val supportsFunctionCalling = FunctionCallingHelper.supportsFunctionCalling(selectedModel)
    
    if (!supportsFunctionCalling) {
        // Show message that function calling is not supported
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "Function Calling Not Supported",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                text = "The selected model '${selectedModel.name}' does not support function calling. " +
                        "Please select a Gemma model to use function calling capabilities.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                text = "\nAvailable functions when using a compatible model:\n" +
                        "• Weather information (getWeather)\n" +
                        "• Current time (getTime)\n\n" +
                        "Try asking: 'What's the weather in San Francisco?' or 'What time is it in Tokyo?'",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    } else {
        // Show the chat panel with function calling capabilities
        ChatPanel(
            modelManagerViewModel = modelManagerViewModel,
            task = task,
            selectedModel = selectedModel,
            viewModel = viewModel,
            onSendMessage = { model, messages ->
                // Add user messages to the chat
                for (message in messages) {
                    viewModel.addMessage(model = model, message = message)
                }
                
                // Extract text from messages for function calling
                var text = ""
                for (message in messages) {
                    if (message is ChatMessageText) {
                        text = message.content
                        break
                    }
                }
                
                if (text.isNotEmpty()) {
                    // Use function calling to generate response
                    viewModel.generateResponseWithFunctionCalling(
                        model = model,
                        input = text,
                        onError = {
                            // Handle error - could show error message or fallback
                            viewModel.addMessage(
                                model = model,
                                message = ChatMessageText(
                                    content = "Sorry, I encountered an error while processing your request.",
                                    side = ChatSide.AGENT
                                )
                            )
                        }
                    )
                }
            },
            onRunAgainClicked = { model, message ->
                if (message is ChatMessageText) {
                    // Re-run the same message with function calling
                    viewModel.generateResponseWithFunctionCalling(
                        model = model,
                        input = message.content,
                        onError = {
                            viewModel.addMessage(
                                model = model,
                                message = ChatMessageText(
                                    content = "Sorry, I encountered an error while processing your request.",
                                    side = ChatSide.AGENT
                                )
                            )
                        }
                    )
                }
            },
            onBenchmarkClicked = { _, _, _, _ -> 
                // Benchmarking not implemented for function calling
            },
            navigateUp = {
                // No special cleanup needed for simplified approach
                navigateUp()
            },
            modifier = modifier
        )
    }
}
