package com.mediflow.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.mediflow.app.navigation.MediFlowNavGraph
import com.mediflow.app.ui.theme.MediFlowTheme

/**
 * MediFlow — Main Activity
 *
 * Entry point of the application. Sets up:
 *  • Edge-to-edge display (status bar transparent)
 *  • MediFlowTheme (Material3 + brand colors)
 *  • Navigation host controller
 *  • MediFlowNavGraph routing all screens
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MediFlowApp()
        }
    }
}

@Composable
fun MediFlowApp() {
    MediFlowTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            val navController = rememberNavController()
            MediFlowNavGraph(navController = navController)
        }
    }
}
