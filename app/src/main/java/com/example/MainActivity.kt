package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PrintPreviewScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.DocViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: DocViewModel = viewModel()
                    val documents by viewModel.allDocuments.collectAsState(initial = emptyList())

                    var currentScreen by remember { mutableStateOf("splash") } // "splash", "home", "editor", "preview"

                    when (currentScreen) {
                        "splash" -> {
                            SplashScreen(
                                onSplashFinished = { currentScreen = "home" }
                            )
                        }
                        "home" -> {
                            HomeScreen(
                                documents = documents,
                                onCreateNew = {
                                    viewModel.clearCurrent()
                                    currentScreen = "editor"
                                },
                                onSelectDocument = { doc ->
                                    viewModel.loadDocument(doc)
                                    currentScreen = "editor"
                                },
                                onPreviewDocument = { doc ->
                                    viewModel.loadDocument(doc)
                                    currentScreen = "preview"
                                },
                                onDeleteDocument = { doc ->
                                    viewModel.deleteDocument(doc)
                                }
                            )
                        }
                        "preview" -> {
                            PrintPreviewScreen(
                                viewModel = viewModel,
                                onBack = { currentScreen = "editor" },
                                onPrintConfirmed = {
                                    // print initiated
                                }
                            )
                        }
                        else -> {
                            EditorScreen(
                                viewModel = viewModel,
                                onBack = { currentScreen = "home" },
                                onOpenPreview = { currentScreen = "preview" }
                            )
                        }
                    }
                }
            }
        }
    }
}
