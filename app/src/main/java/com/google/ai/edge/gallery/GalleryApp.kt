package com.google.ai.edge.gallery
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.google.ai.edge.gallery.data.TASK_LLM_ASK_IMAGE
import com.google.ai.edge.gallery.ui.llmchat.LlmAskImageDestination
import com.google.ai.edge.gallery.ui.navigation.GalleryNavHost
@Composable
fun GalleryApp(
  navController: NavHostController = rememberNavController()
) {
  // as soon as GalleryApp comes up, navigate to Ask-Image 
  // and drop “home” off the back-stack:
  LaunchedEffect(Unit) {
    TASK_LLM_ASK_IMAGE.models
      .firstOrNull()    // safety: don’t crash if the models list is empty
      ?.name
      ?.let { modelName ->
        navController.navigate("${LlmAskImageDestination.route}/$modelName") {
          popUpTo("home") { inclusive = true }
        }
      }
  }
  GalleryNavHost(navController = navController)
}