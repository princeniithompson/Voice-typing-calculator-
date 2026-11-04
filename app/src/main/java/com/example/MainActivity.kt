package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.calculator.tokens.CalculatorTokens
import com.example.calculator.ui.ClassicSurface
import com.example.diag.DiagnosticsScreen
import com.example.ui.screens.QuickCalcCatalogDialog
import com.example.ui.screens.QuickCalcHistoryDialog
import com.example.ui.screens.QuickCalcSettingsDialog
import com.example.ui.screens.VoiceTestPlaygroundScreen
import com.example.ui.theme.VoicePosTheme
import com.example.ui.viewmodel.PosViewModel
import com.example.voice.VoiceAssistSurface

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VoicePosTheme {
                QuickCalcScreen()
            }
        }
    }
}

@Composable
fun QuickCalcScreen(
    viewModel: PosViewModel = viewModel()
) {
    val context = LocalContext.current
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasMicPermission = isGranted
        com.example.diag.Diag.log("PERMISSION", "Microphone permission result: granted=$isGranted")
    }

    LaunchedEffect(Unit) {
        if (!hasMicPermission) {
            com.example.diag.Diag.log("PERMISSION", "Microphone permission requested")
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            com.example.diag.Diag.log("PERMISSION", "Microphone permission status at startup: GRANTED")
        }
    }

    val isVoiceAssistEnabled by viewModel.isVoiceAssistEnabled.collectAsStateWithLifecycle()
    val classicExpression by viewModel.classicExpression.collectAsStateWithLifecycle()
    val classicLiveResult by viewModel.classicLiveResult.collectAsStateWithLifecycle()

    val voiceTranscript by viewModel.voiceTranscript.collectAsStateWithLifecycle()
    val voiceTotal by viewModel.voiceTotal.collectAsStateWithLifecycle()
    val manualExpression by viewModel.manualExpression.collectAsStateWithLifecycle()
    val manualResult by viewModel.manualResult.collectAsStateWithLifecycle()
    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()

    val showHistory by viewModel.showHistory.collectAsStateWithLifecycle()
    val showCatalog by viewModel.showCatalog.collectAsStateWithLifecycle()
    val showSettings by viewModel.showSettings.collectAsStateWithLifecycle()
    val showDiagnostics by viewModel.showDiagnostics.collectAsStateWithLifecycle()
    val showTestPlayground by viewModel.showTestPlayground.collectAsStateWithLifecycle()
    val geminiApiKey by viewModel.geminiApiKey.collectAsStateWithLifecycle()

    if (showDiagnostics) {
        DiagnosticsScreen(
            apiKey = geminiApiKey,
            onBack = { viewModel.closeDiagnostics() }
        )
    } else if (showTestPlayground) {
        VoiceTestPlaygroundScreen(
            viewModel = viewModel,
            onBack = { viewModel.closeTestPlayground() }
        )
    } else {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
            containerColor = MaterialTheme.colorScheme.surface,
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (!isVoiceAssistEnabled) {
                    // Surface A: Google Calculator Parity (Default)
                    // Strict isolation: ClassicSurface imports NO voice/ or pricing/ code
                    ClassicSurface(
                        expression = classicExpression,
                        liveResult = classicLiveResult,
                        isVoiceAssistEnabled = false,
                        onKeyPress = { key -> viewModel.onClassicKeypadPress(key) },
                        onToggleVoiceAssist = { viewModel.toggleVoiceAssist() },
                        onOpenHistory = { viewModel.openHistory() },
                        onOpenSettings = { viewModel.openSettings() },
                        onOpenDiagnostics = { viewModel.openDiagnostics() },
                        onOpenTestPlayground = { viewModel.openTestPlayground() }
                    )
                } else {
                    // Surface B: Custom Voice Assist
                    VoiceAssistSurface(
                        voiceTranscript = voiceTranscript,
                        voiceTotal = voiceTotal,
                        manualExpression = manualExpression,
                        manualResult = manualResult,
                        voiceState = voiceState,
                        isVoiceAssistEnabled = true,
                        onKeypadPress = { key -> viewModel.onVoiceKeypadPress(key) },
                        onToggleVoiceAssist = { viewModel.toggleVoiceAssist() },
                        onOpenHistory = { viewModel.openHistory() },
                        onOpenCatalog = { viewModel.openCatalog() },
                        onOpenSettings = { viewModel.openSettings() },
                        onOpenDiagnostics = { viewModel.openDiagnostics() },
                        onOpenTestPlayground = { viewModel.openTestPlayground() },
                        onHoldStart = { viewModel.onHoldStart() },
                        onHoldRelease = { swiped -> viewModel.onHoldRelease(swiped) },
                        onLockToggle = { viewModel.onVoiceLockToggle() },
                        onPauseToggle = { viewModel.onVoicePauseToggle() },
                        onClearVoice = { viewModel.onClearVoiceCard() },
                        onAddVoiceTotalToManual = { viewModel.addVoiceTotalToManual() }
                    )
                }
            }
        }

        // History Sheet
        if (showHistory) {
            QuickCalcHistoryDialog(
                viewModel = viewModel,
                onDismiss = { viewModel.closeHistory() }
            )
        }

        // Catalog & Pricing Sheet
        if (showCatalog) {
            QuickCalcCatalogDialog(
                viewModel = viewModel,
                onDismiss = { viewModel.closeCatalog() }
            )
        }

        // Settings Sheet
        if (showSettings) {
            QuickCalcSettingsDialog(
                viewModel = viewModel,
                onDismiss = { viewModel.closeSettings() }
            )
        }
    }
}
