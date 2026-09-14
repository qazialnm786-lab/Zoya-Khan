package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.ui.PermissionsScreen
import com.example.ui.ZoyaMainScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.ZoyaViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: ZoyaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val uiState by viewModel.uiState.collectAsState()
                var hasCheckedPermissions by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    val micGranted = ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED

                    viewModel.setPermissionsGranted(micGranted)
                    hasCheckedPermissions = true
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { _ ->
                    if (!hasCheckedPermissions || !uiState.permissionsGranted) {
                        PermissionsScreen(
                            onAllPermissionsHandled = {
                                viewModel.setPermissionsGranted(true)
                            }
                        )
                    } else {
                        ZoyaMainScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            viewModel.bindService(this)
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.unbindService(this)
    }
}

/**
 * Retained for backwards compatibility with tests.
 */
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}
