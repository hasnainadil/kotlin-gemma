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

package com.google.ai.edge.gallery.ui.cattleadvisor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.ai.edge.gallery.GalleryTopAppBar
import com.google.ai.edge.gallery.data.AppBarAction
import com.google.ai.edge.gallery.data.AppBarActionType
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import com.google.ai.edge.gallery.ui.common.ConfigDialog
import com.google.ai.edge.gallery.ui.common.MarkdownText
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CattleAdvisorScreen(
    viewModel: CattleAdvisorViewModel,
    modelManagerViewModel: ModelManagerViewModel,
    navigateUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val analysisResults = viewModel.analysisResults
    val isAnalyzing = viewModel.isAnalyzing
    val errorMessage = viewModel.errorMessage
    val isNutritionServiceInitialized = viewModel.isNutritionServiceInitialized

    // Initialize nutrition service on first composition
    LaunchedEffect(Unit) {
        viewModel.initializeNutritionService(context)
    }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Input states
    var selectedCattleType by remember { mutableStateOf("") }
    var targetWeight by remember { mutableStateOf("") }
    var bodyWeight by remember { mutableStateOf("") }
    var averageDailyGain by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    // Cattle types from Python code
    val cattleTypes = listOf(
        "Growing Steer/Heifer",
        "Growing Yearlings",
        "Growing Mature Bulls"
    )

    Scaffold(
        topBar = {
            GalleryTopAppBar(
                title = "Cattle Advisor",
                leftAction = AppBarAction(
                    actionType = AppBarActionType.NAVIGATE_UP,
                    actionFn = navigateUp
                )
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Show input form only when no analysis results
            if (analysisResults.isEmpty()) {
                // Input form
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Cattle Information",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Cattle Type Dropdown
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
                            readOnly = true,
                            value = selectedCattleType,
                            onValueChange = {},
                            label = { Text("Cattle Type") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                        ) {
                            cattleTypes.forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type) },
                                    onClick = {
                                        selectedCattleType = type
                                        expanded = false
                                    },
                                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Target Weight
                    OutlinedTextField(
                        value = targetWeight,
                        onValueChange = { targetWeight = it },
                        label = { Text("Target Weight (lbs)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Current Body Weight
                    OutlinedTextField(
                        value = bodyWeight,
                        onValueChange = { bodyWeight = it },
                        label = { Text("Current Body Weight (lbs)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Average Daily Gain
                    OutlinedTextField(
                        value = averageDailyGain,
                        onValueChange = { averageDailyGain = it },
                        label = { Text("Average Daily Gain (lbs/day)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Analyze button - no model selection needed
                    Button(
                        onClick = {
                            viewModel.clearError()
                            val targetWeightValue = targetWeight.toDoubleOrNull() ?: 0.0
                            val bodyWeightValue = bodyWeight.toDoubleOrNull() ?: 0.0
                            val adgValue = averageDailyGain.toDoubleOrNull() ?: 0.0

                            // Direct analysis without model selection
                            viewModel.analyzeNutrition(
                                context = context,
                                cattleType = selectedCattleType,
                                targetWeight = targetWeightValue,
                                bodyWeight = bodyWeightValue,
                                averageDailyGain = adgValue,
                                modelManagerViewModel = modelManagerViewModel
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isAnalyzing && isNutritionServiceInitialized
                    ) {
                        if (isAnalyzing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Analyzing...")
                        } else {
                            Icon(
                                Icons.Default.Analytics,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Analyze Nutrition")
                        }
                    }
                }
            }
            }

            // Error message
            errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // Show results in full screen when available
            if (analysisResults.isNotEmpty()) {
                // New Analysis button when results exist
                Button(
                    onClick = {
                        viewModel.clearResults() // Add this method to clear results and show form again
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Icon(
                        Icons.Default.Analytics,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("New Analysis")
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Show latest result in full screen mode
                analysisResults.lastOrNull()?.let { latestResult ->
                    // Debug log to check if UI is getting updated results
                    LaunchedEffect(latestResult.recommendation) {
                        android.util.Log.d("UI Update", "FullScreenAnalysisCard received recommendation length: ${latestResult.recommendation.length}")
                        android.util.Log.d("UI Update", "FullScreenAnalysisCard loading state: ${latestResult.isLoading}")
                    }
                    FullScreenAnalysisCard(result = latestResult, viewModel = viewModel)
                }
            }
        }
    }
    
    // Auto-scroll to latest result
    LaunchedEffect(analysisResults.size) {
        if (analysisResults.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(0)
            }
        }
    }
}

@Composable
private fun CattleAdvisorResultCard(
    result: CattleAdvisorResult,
    modifier: Modifier = Modifier
) {
    var showFullScreen by remember { mutableStateOf(false) }
    
    if (showFullScreen) {
        FullScreenAnalysisDialog(
            result = result,
            onDismiss = { showFullScreen = false }
        )
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { showFullScreen = true },
//        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header with cattle info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = result.cattleType,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Target: ${result.targetWeight} lbs",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Current: ${result.bodyWeight} lbs",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "ADG: ${result.averageDailyGain} lbs/day",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            HorizontalDivider()
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Recommendation Header
            Text(
                text = "🐄 Nutrition Recommendation",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Recommendation
            if (result.isLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Generating nutrition advice...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (result.recommendation.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 150.dp, max = 400.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            MarkdownText(
                                text = result.recommendation,
                                modifier = Modifier
                                    .padding(14.dp)
                                    .fillMaxWidth(),
                                smallFontSize = true
                            )
                        }
                    }
                }
            } else {
                // Use a Card with better styling for the final result
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 600.dp), // Allow more height for tables
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        MarkdownText(
                            text = result.recommendation,
                            modifier = Modifier
                                .padding(16.dp) // Increased padding for better readability
                                .fillMaxWidth(), // Use fillMaxWidth instead of wrapContentSize
                            smallFontSize = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FullScreenAnalysisCard(
    result: CattleAdvisorResult,
    viewModel: CattleAdvisorViewModel,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Header Card with Cattle Information
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp), // Reduced shadow
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Analytics,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = result.cattleType,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Cattle Stats in a beautiful grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        CattleStatCard(
                            label = "Current Weight",
                            value = "${result.bodyWeight} lbs",
                            color = MaterialTheme.colorScheme.secondary
                        )
                        CattleStatCard(
                            label = "Target Weight",
                            value = "${result.targetWeight} lbs",
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        CattleStatCard(
                            label = "Daily Gain",
                            value = "${result.averageDailyGain} lbs/day",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        // Recommendation Content
        item {
            if (result.isLoading) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp), // Reduced shadow
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            strokeWidth = 4.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (viewModel.isRetryingWithUnavailableIngredients) 
                                "Regenerating recommendations with your constraints..."
                            else
                                "Analyzing nutrition requirements...",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            textAlign = TextAlign.Center
                        )
                        if (result.recommendation.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(24.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                // Add key to force recomposition when recommendation changes
                                key(result.recommendation.hashCode()) {
                                    MarkdownText(
                                        text = result.recommendation,
                                        modifier = Modifier
                                            .widthIn(min = 300.dp, max = 1000.dp)
                                            .wrapContentWidth(),
                                        smallFontSize = false
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Final recommendation in beautiful format
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp), // Reduced shadow
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp)
                    ) {
                        // Title with icon
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "🐄",
                                style = MaterialTheme.typography.headlineLarge
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Nutrition Recommendations",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Content with better formatting and horizontal scroll for tables
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            // Add key to force recomposition when recommendation changes
                            key(result.recommendation.hashCode()) {
                                MarkdownText(
                                    text = result.recommendation,
                                    modifier = Modifier
                                        .widthIn(min = 300.dp, max = 1000.dp) // Increased max width for better table display
                                        .wrapContentWidth(),
                                    smallFontSize = false
                                )
                            }
                        }
                    }
                }
            }
        }

        // Unavailable Ingredients Input Section
        if (result.showUnavailableIngredientsInput && !result.isLoading) {
            item {
                UnavailableIngredientsCard(result = result, viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullScreenAnalysisDialog(
    result: CattleAdvisorResult,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Top App Bar
        TopAppBar(
            title = {
                Text(
                    text = "Nutrition Analysis",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.primary
            )
        )

        // Content
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 64.dp), // Account for top bar
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Header Card with Cattle Information
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Analytics,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = result.cattleType,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Cattle Stats in a beautiful grid
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            CattleStatCard(
                                label = "Current Weight",
                                value = "${result.bodyWeight} lbs",
                                color = MaterialTheme.colorScheme.secondary
                            )
                            CattleStatCard(
                                label = "Target Weight",
                                value = "${result.targetWeight} lbs",
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            CattleStatCard(
                                label = "Daily Gain",
                                value = "${result.averageDailyGain} lbs/day",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // Recommendation Content
            item {
                if (result.isLoading) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                strokeWidth = 4.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Analyzing nutrition requirements...",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                textAlign = TextAlign.Center
                            )
                            if (result.recommendation.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(24.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                ) {
                                    MarkdownText(
                                        text = result.recommendation,
                                        modifier = Modifier
                                            .widthIn(min = 300.dp, max = 800.dp)
                                            .wrapContentWidth(),
                                        smallFontSize = false
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Final recommendation in beautiful format
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp)
                        ) {
                            // Title with icon
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "🐄",
                                    style = MaterialTheme.typography.headlineLarge
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Nutrition Recommendations",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Content with better formatting and horizontal scroll for tables
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                MarkdownText(
                                    text = result.recommendation,
                                    modifier = Modifier
                                        .widthIn(min = 300.dp, max = 800.dp)
                                        .wrapContentWidth(),
                                    smallFontSize = false
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CattleStatCard(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color
) {
    Card(
        modifier = Modifier
            .width(110.dp)
            .height(90.dp), // Fixed height for consistency
//        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp), // Reduced shadow
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp), // Reduced padding
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center // Center content vertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = color,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2 // Allow text to wrap if needed
            )
            Spacer(modifier = Modifier.height(6.dp)) // Reduced spacing
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2 // Allow text to wrap if needed
            )
        }
    }
}

@Composable
private fun UnavailableIngredientsCard(
    result: CattleAdvisorResult,
    viewModel: CattleAdvisorViewModel,
    modifier: Modifier = Modifier
) {
    var unavailableIngredientsInput by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp), // Reduced shadow
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            // Title with icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "🚫",
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Customize Feed Recommendations",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "If some ingredients are not available in your area, list them below (comma-separated) and we'll generate alternative recommendations:",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = unavailableIngredientsInput,
                onValueChange = { unavailableIngredientsInput = it },
                label = { Text("Unavailable ingredients (comma-separated)") },
                placeholder = { Text("e.g., Alfalfa Hay, Soybean Meal, Ground Corn") },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { focusState ->
                        isFocused = focusState.isFocused
                    },
                minLines = 2,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        keyboardController?.hide()
                        if (unavailableIngredientsInput.isNotBlank()) {
                            viewModel.retryWithUnavailableIngredients(unavailableIngredientsInput)
                        }
                    }
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    keyboardController?.hide()
                    if (unavailableIngredientsInput.isNotBlank()) {
                        viewModel.retryWithUnavailableIngredients(unavailableIngredientsInput)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = unavailableIngredientsInput.isNotBlank() && !viewModel.isRetryingWithUnavailableIngredients,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary
                )
            ) {
                if (viewModel.isRetryingWithUnavailableIngredients) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onTertiary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generating Alternatives...")
                } else {
                    Icon(
                        Icons.Default.Analytics,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate Alternative Recommendations")
                }
            }
        }
    }
}

