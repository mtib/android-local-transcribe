package dev.mtib.localtranscribe.phone

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.mtib.localtranscribe.phone.ui.LocalTranscribeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LocalTranscribeTheme { AppNav() } }
    }
}

@Composable
private fun AppNav() {
    val nav = rememberNavController()
    val vm: RecordingsViewModel = viewModel()
    val context = LocalContext.current

    fun beginRecording() {
        RecordingService.start(context)
        nav.navigate("active")
    }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] == true) beginRecording()
    }

    fun onNewRecording() {
        val hasMic = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (hasMic) {
            beginRecording()
        } else {
            val perms = buildList {
                add(Manifest.permission.RECORD_AUDIO)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }.toTypedArray()
            permissionLauncher.launch(perms)
        }
    }

    NavHost(navController = nav, startDestination = "list") {
        composable("list") {
            RecordingListScreen(
                vm = vm,
                onNew = { onNewRecording() },
                onOpen = { id -> nav.navigate("detail/$id") },
            )
        }
        composable("active") {
            ActiveRecordingScreen(
                onStopped = {
                    vm.refresh()
                    if (!nav.popBackStack("list", inclusive = false)) nav.navigate("list")
                },
            )
        }
        composable("detail/{id}") { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            RecordingDetailScreen(id = id, vm = vm, onBack = { nav.popBackStack() })
        }
    }
}
