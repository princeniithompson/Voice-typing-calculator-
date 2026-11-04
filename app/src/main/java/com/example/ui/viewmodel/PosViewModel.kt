package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioNoiseGate
import com.example.calculator.CalculatorEngine
import com.example.data.AppDatabase
import com.example.data.model.MenuItemEntity
import com.example.data.model.OrderItemEntity
import com.example.data.model.OrderWithItems
import com.example.data.repository.InventoryRepository
import com.example.data.repository.OrderRepository
import com.example.pricing.PricingRepository
import com.example.manual.ManualKeypadHandler
import com.example.manual.ManualState
import com.example.voice.DetectedVoiceItem
import com.example.voice.GeminiApiKey
import com.example.voice.VoiceMode
import com.example.voice.VoiceState
import com.example.voice.VoiceTranscriptionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal

class PosViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("quick_calc_prefs", Context.MODE_PRIVATE)

    private val database = AppDatabase.getInstance(application)
    val inventoryRepository = InventoryRepository(database.menuItemDao())
    val orderRepository = OrderRepository(database.orderDao())
    val pricingRepository = PricingRepository(database.menuItemDao())

    val noiseGate = AudioNoiseGate(initialThresholdDb = -45f)
    private val manualHandler = ManualKeypadHandler()

    val voiceTranscriptionManager = VoiceTranscriptionManager(
        scope = viewModelScope,
        noiseGate = noiseGate,
        menuItemsProvider = { pricingRepository.cachedItems }
    )

    // 1. Surface Toggle: false = Surface A (Classic Google Calculator parity), true = Surface B (Voice assist)
    private val _isVoiceAssistEnabled = MutableStateFlow(
        prefs.getBoolean("prefs_voice_assist_enabled", true)
    )
    val isVoiceAssistEnabled: StateFlow<Boolean> = _isVoiceAssistEnabled.asStateFlow()

    // Gemini API Key management
    private val _geminiApiKey = MutableStateFlow(
        prefs.getString("prefs_gemini_api_key", null)?.let { GeminiApiKey.normalize(it) }?.takeIf { it.isNotEmpty() }
            ?: GeminiApiKey.fromBuildConfig()
    )
    val geminiApiKey: StateFlow<String> = _geminiApiKey.asStateFlow()

    fun saveGeminiApiKey(key: String) {
        val trimmed = GeminiApiKey.normalize(key)
        _geminiApiKey.value = trimmed
        prefs.edit().putString("prefs_gemini_api_key", trimmed).apply()
        com.example.diag.Diag.updateApiKey(trimmed)
        if (trimmed.isNotBlank()) {
            _voiceState.value = _voiceState.value.copy(statusCaption = null)
        }
    }

    // 2. Surface A Display State
    private val _classicExpression = MutableStateFlow("")
    val classicExpression: StateFlow<String> = _classicExpression.asStateFlow()

    private val _classicLiveResult = MutableStateFlow<String?>(null)
    val classicLiveResult: StateFlow<String?> = _classicLiveResult.asStateFlow()

    // 3. Independent State Holders: ManualState & VoiceState (Clean startup defaults)
    private val _manualState = MutableStateFlow(ManualState(expression = "", result = "0", isEvaluated = false))
    val manualState: StateFlow<ManualState> = _manualState.asStateFlow()

    private val _voiceState = MutableStateFlow(
        VoiceState(
            liveTranscript = "",
            detectedItems = emptyList(),
            total = BigDecimal.ZERO,
            mode = if (prefs.getBoolean("prefs_voice_locked", false)) VoiceMode.LOCKED else VoiceMode.IDLE
        )
    )
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    // Backward-compatible flow delegates for existing UI bindings
    val manualExpression: StateFlow<String> = MutableStateFlow(_manualState.value.expression).apply {
        viewModelScope.launch {
            _manualState.collect { value = it.expression }
        }
    }.asStateFlow()

    val manualResult: StateFlow<String> = MutableStateFlow(_manualState.value.result).apply {
        viewModelScope.launch {
            _manualState.collect { value = it.result }
        }
    }.asStateFlow()

    val voiceTranscript: StateFlow<String> = MutableStateFlow(_voiceState.value.liveTranscript).apply {
        viewModelScope.launch {
            _voiceState.collect { value = it.liveTranscript }
        }
    }.asStateFlow()

    val voiceTotal: StateFlow<Double> = MutableStateFlow(_voiceState.value.total.toDouble()).apply {
        viewModelScope.launch {
            _voiceState.collect { value = it.total.toDouble() }
        }
    }.asStateFlow()

    val voiceBreakdown: StateFlow<String> = voiceTranscript

    // Dialogs / Sheets
    private val _showHistory = MutableStateFlow(false)
    val showHistory: StateFlow<Boolean> = _showHistory.asStateFlow()

    private val _showCatalog = MutableStateFlow(false)
    val showCatalog: StateFlow<Boolean> = _showCatalog.asStateFlow()

    private val _showSettings = MutableStateFlow(false)
    val showSettings: StateFlow<Boolean> = _showSettings.asStateFlow()

    private val _showDiagnostics = MutableStateFlow(false)
    val showDiagnostics: StateFlow<Boolean> = _showDiagnostics.asStateFlow()

    private val _showTestPlayground = MutableStateFlow(false)
    val showTestPlayground: StateFlow<Boolean> = _showTestPlayground.asStateFlow()

    // Test Playground Settings
    private val _isSmartTranscriptionEnabled = MutableStateFlow(
        prefs.getBoolean("prefs_smart_transcription", true)
    )
    val isSmartTranscriptionEnabled: StateFlow<Boolean> = _isSmartTranscriptionEnabled.asStateFlow()

    private val _testPrimaryLanguage = MutableStateFlow(
        prefs.getString("prefs_test_language", "Detect") ?: "Detect"
    )
    val testPrimaryLanguage: StateFlow<String> = _testPrimaryLanguage.asStateFlow()

    private val _testSystemInstruction = MutableStateFlow(
        prefs.getString("prefs_test_system_instruction", "Transcribe cleanly, write numbers as digits, do not answer, comment or calculate.")
            ?: "Transcribe cleanly, write numbers as digits, do not answer, comment or calculate."
    )
    val testSystemInstruction: StateFlow<String> = _testSystemInstruction.asStateFlow()

    private val _testVocabularyList = MutableStateFlow(
        prefs.getStringSet("prefs_test_vocabulary", setOf("Banku", "Fried Fish", "Waakye", "Kelewele"))?.toList()
            ?: listOf("Banku", "Fried Fish", "Waakye", "Kelewele")
    )
    val testVocabularyList: StateFlow<List<String>> = _testVocabularyList.asStateFlow()

    fun toggleSmartTranscription(enabled: Boolean) {
        _isSmartTranscriptionEnabled.value = enabled
        prefs.edit().putBoolean("prefs_smart_transcription", enabled).apply()
        com.example.diag.Diag.log("GEMINI", "Smart transcription mode toggled to: $enabled")
    }

    fun setTestPrimaryLanguage(lang: String) {
        _testPrimaryLanguage.value = lang
        prefs.edit().putString("prefs_test_language", lang).apply()
    }

    fun setTestSystemInstruction(text: String) {
        _testSystemInstruction.value = text
        prefs.edit().putString("prefs_test_system_instruction", text).apply()
    }

    fun addTestVocabulary(phrase: String) {
        val trimmed = phrase.trim()
        if (trimmed.isNotBlank()) {
            val updated = (_testVocabularyList.value + trimmed).distinct()
            _testVocabularyList.value = updated
            prefs.edit().putStringSet("prefs_test_vocabulary", updated.toSet()).apply()
        }
    }

    fun removeTestVocabulary(phrase: String) {
        val updated = _testVocabularyList.value.filter { it != phrase }
        _testVocabularyList.value = updated
        prefs.edit().putStringSet("prefs_test_vocabulary", updated.toSet()).apply()
    }

    // Inventory & Orders Flows from Room
    val allMenuItems: StateFlow<List<MenuItemEntity>> = inventoryRepository.allMenuItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val orderHistory: StateFlow<List<OrderWithItems>> = orderRepository.allOrdersWithItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todaySales: StateFlow<Double?> = orderRepository.getTodaySales()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val todayOrderCount: StateFlow<Int> = orderRepository.getTodayOrderCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        com.example.diag.Diag.updateApiKey(_geminiApiKey.value)
        com.example.diag.Diag.log("SPEECH_ENGINE", "Active speech engine: ${com.example.diag.Diag.activeSpeechEngine}")

        viewModelScope.launch {
            inventoryRepository.checkAndSeedDefaults()
            pricingRepository.getItemsSnapshot()
        }

        voiceTranscriptionManager.onVoiceResultUpdated = { transcript, items, total ->
            updateVoiceTranscription(transcript, items, total)
        }
        voiceTranscriptionManager.onStatusCaptionChanged = { caption ->
            setVoiceStatusCaption(caption)
        }
    }

    // --- Surface Switcher ---
    fun toggleVoiceAssist() {
        val newState = !_isVoiceAssistEnabled.value
        _isVoiceAssistEnabled.value = newState
        prefs.edit().putBoolean("prefs_voice_assist_enabled", newState).apply()
        com.example.diag.Diag.log("LIFECYCLE", "Screen change: Voice assist toggled to $newState")

        if (!newState) {
            voiceTranscriptionManager.stopListening()
            _voiceState.value = _voiceState.value.copy(mode = VoiceMode.IDLE)
            prefs.edit().putBoolean("prefs_voice_locked", false).apply()
        }
    }

    // --- Surface A (Classic Keypad & Operations) ---
    fun onClassicKeypadPress(key: String) {
        val currentExpr = _classicExpression.value
        when (key) {
            "AC" -> {
                _classicExpression.value = ""
                _classicLiveResult.value = null
            }
            "⌫" -> {
                if (currentExpr.isNotEmpty()) {
                    val newExpr = if (currentExpr.endsWith(" ")) {
                        currentExpr.dropLast(2).trimEnd()
                    } else {
                        currentExpr.dropLast(1)
                    }
                    _classicExpression.value = newExpr
                    _classicLiveResult.value = CalculatorEngine.evaluateLive(newExpr)
                }
            }
            "=" -> {
                if (currentExpr.isNotBlank()) {
                    val finalResult = CalculatorEngine.evaluate(currentExpr)
                    if (finalResult.isNotBlank()) {
                        recordCalculation(currentExpr, finalResult)
                        _classicExpression.value = finalResult
                        _classicLiveResult.value = null
                    }
                }
            }
            "( )" -> {
                val openCount = currentExpr.count { it == '(' }
                val closeCount = currentExpr.count { it == ')' }
                val nextChar = if (openCount > closeCount && currentExpr.lastOrNull()?.isDigit() == true) {
                    ")"
                } else {
                    "("
                }
                val newExpr = currentExpr + nextChar
                _classicExpression.value = newExpr
                _classicLiveResult.value = CalculatorEngine.evaluateLive(newExpr)
            }
            "+", "−", "×", "÷", "%" -> {
                val newExpr = if (currentExpr.isEmpty()) {
                    if (key == "−") "−" else ""
                } else if (currentExpr.lastOrNull() in listOf('+', '−', '×', '÷', '%')) {
                    currentExpr.dropLast(1) + key
                } else {
                    currentExpr + key
                }
                _classicExpression.value = newExpr
                _classicLiveResult.value = CalculatorEngine.evaluateLive(newExpr)
            }
            else -> {
                val newExpr = currentExpr + key
                _classicExpression.value = newExpr
                _classicLiveResult.value = CalculatorEngine.evaluateLive(newExpr)
            }
        }
        com.example.diag.Diag.log(
            "CALC",
            "Manual calc (Classic): key='$key', exprBefore='$currentExpr', exprAfter='${_classicExpression.value}', result='${_classicLiveResult.value}', target=MANUAL_STATE"
        )
    }

    // --- Surface B (Keypad changes ManualState ONLY. NEVER touches VoiceState.) ---
    fun onVoiceKeypadPress(key: String) {
        val previousState = _manualState.value
        val newState = manualHandler.processKey(previousState, key)
        _manualState.value = newState

        com.example.diag.Diag.log(
            "CALC",
            "Manual calc (Voice hybrid): key='$key', exprBefore='${previousState.expression}', exprAfter='${newState.expression}', result='${newState.result}', target=MANUAL_STATE"
        )

        if (key == "=" && newState.expression.isNotBlank() && newState.expression != previousState.expression) {
            recordCalculation(previousState.expression, newState.result)
        }
    }

    // --- Deliberate Bridge: "+" button on Voice card adds voice total to Manual expression ---
    fun addVoiceTotalToManual() {
        val currentVoiceTotal = _voiceState.value.total.toDouble()
        if (currentVoiceTotal > 0.0) {
            val prevExpr = _manualState.value.expression
            val updatedManual = manualHandler.addVoiceTotal(_manualState.value, currentVoiceTotal)
            _manualState.value = updatedManual
            com.example.diag.Diag.log(
                "CALC",
                "Bridge '+': added voice total $currentVoiceTotal to manual expr, exprBefore='$prevExpr', exprAfter='${updatedManual.expression}', target=MANUAL_STATE"
            )
        }
    }

    // --- Voice Bar State Machine & Actions (Changes VoiceState ONLY. Speech never touches ManualState.) ---
    fun onHoldStart() {
        if (_voiceState.value.mode == VoiceMode.LOCKED) return
        _voiceState.value = _voiceState.value.copy(mode = VoiceMode.LISTENING)
        com.example.diag.Diag.log("VOICE_STATE", "Mic state -> LISTENING, reason: user hold start")
        voiceTranscriptionManager.startListening(_geminiApiKey.value)
    }

    fun onHoldRelease(swipedToLock: Boolean) {
        if (_voiceState.value.mode == VoiceMode.LOCKED) return
        if (swipedToLock) {
            _voiceState.value = _voiceState.value.copy(mode = VoiceMode.LOCKED)
            prefs.edit().putBoolean("prefs_voice_locked", true).apply()
            com.example.diag.Diag.log("VOICE_STATE", "Mic state -> LOCKED, reason: user swiped to lock")
            voiceTranscriptionManager.startListening(_geminiApiKey.value)
        } else {
            _voiceState.value = _voiceState.value.copy(mode = VoiceMode.IDLE)
            com.example.diag.Diag.log("VOICE_STATE", "Mic state -> IDLE, reason: user released hold")
            voiceTranscriptionManager.stopListening()
        }
    }

    fun onVoiceLockToggle() {
        if (_voiceState.value.mode == VoiceMode.LOCKED || _voiceState.value.mode == VoiceMode.PAUSED) {
            _voiceState.value = _voiceState.value.copy(mode = VoiceMode.IDLE)
            prefs.edit().putBoolean("prefs_voice_locked", false).apply()
            com.example.diag.Diag.log("VOICE_STATE", "Mic state -> IDLE, reason: user unlocked mic button")
            voiceTranscriptionManager.stopListening()
        } else {
            _voiceState.value = _voiceState.value.copy(mode = VoiceMode.LOCKED)
            prefs.edit().putBoolean("prefs_voice_locked", true).apply()
            com.example.diag.Diag.log("VOICE_STATE", "Mic state -> LOCKED, reason: user locked mic button")
            voiceTranscriptionManager.startListening(_geminiApiKey.value)
        }
    }

    fun onVoicePauseToggle() {
        if (_voiceState.value.mode == VoiceMode.LOCKED) {
            _voiceState.value = _voiceState.value.copy(mode = VoiceMode.PAUSED)
            com.example.diag.Diag.log("VOICE_STATE", "Mic state -> PAUSED, reason: user tapped pause")
            voiceTranscriptionManager.stopListening()
        } else if (_voiceState.value.mode == VoiceMode.PAUSED) {
            _voiceState.value = _voiceState.value.copy(mode = VoiceMode.LOCKED)
            com.example.diag.Diag.log("VOICE_STATE", "Mic state -> LOCKED (resumed), reason: user tapped resume")
            voiceTranscriptionManager.startListening(_geminiApiKey.value)
        }
    }

    fun onClearVoiceCard() {
        // Bar AC clears Voice card and saves order to history if non-empty
        val currentItems = _voiceState.value.detectedItems
        val currentTotal = _voiceState.value.total
        com.example.diag.Diag.log("VOICE_STATE", "Voice card cleared, reason: user tapped voice AC pill")
        com.example.diag.Diag.log("CALC", "Voice calc reset: items=0, total=0, target=VOICE_STATE")

        if (currentItems.isNotEmpty() && currentTotal > BigDecimal.ZERO) {
            saveVoiceOrderToHistory(currentItems, currentTotal.toDouble())
        }

        _voiceState.value = VoiceState(
            liveTranscript = "",
            detectedItems = emptyList(),
            total = BigDecimal.ZERO,
            mode = if (_voiceState.value.mode == VoiceMode.PAUSED) VoiceMode.PAUSED else if (_voiceState.value.mode == VoiceMode.LOCKED) VoiceMode.LOCKED else VoiceMode.IDLE
        )
    }

    // --- Voice Transcript & Total Updates (Simulated / Live Flow: ONLY modifies VoiceState) ---
    fun updateVoiceTranscription(
        transcript: String,
        detectedItems: List<DetectedVoiceItem>,
        total: BigDecimal
    ) {
        // Speech NEVER touches ManualState!
        com.example.diag.Diag.log(
            "CALC",
            "Voice calc update: transcript='$transcript', items=${detectedItems.size}, total=$total, target=VOICE_STATE"
        )
        _voiceState.value = _voiceState.value.copy(
            liveTranscript = transcript,
            detectedItems = detectedItems,
            total = total
        )
    }

    fun setVoiceStatusCaption(caption: String?) {
        _voiceState.value = _voiceState.value.copy(statusCaption = caption)
    }

    private fun saveVoiceOrderToHistory(items: List<DetectedVoiceItem>, total: Double) {
        viewModelScope.launch {
            val orderItems = items.map { item ->
                OrderItemEntity(
                    orderId = 0,
                    menuItemId = 0,
                    name = item.name,
                    unitPrice = item.unitPrice.toDouble(),
                    quantity = item.quantity,
                    subtotal = item.lineTotal.toDouble(),
                    unitLabel = ""
                )
            }
            orderRepository.createOrder(
                orderType = "VOICE",
                subtotal = total,
                grandTotal = total,
                paymentMethod = "CASH",
                items = orderItems
            )
        }
    }

    private fun recordCalculation(expression: String, result: String) {
        val resultNum = result.toDoubleOrNull() ?: return
        viewModelScope.launch {
            val item = OrderItemEntity(
                orderId = 0,
                menuItemId = 0,
                name = "Calculation: $expression",
                unitPrice = resultNum,
                quantity = 1,
                subtotal = resultNum,
                unitLabel = "calc"
            )
            orderRepository.createOrder(
                orderType = "MANUAL",
                subtotal = resultNum,
                grandTotal = resultNum,
                paymentMethod = "CALC",
                items = listOf(item)
            )
        }
    }

    // Modals
    fun openHistory() { _showHistory.value = true }
    fun closeHistory() { _showHistory.value = false }
    fun openCatalog() { _showCatalog.value = true }
    fun closeCatalog() { _showCatalog.value = false }
    fun openSettings() { _showSettings.value = true }
    fun closeSettings() { _showSettings.value = false }
    fun openDiagnostics() {
        _showDiagnostics.value = true
        com.example.diag.Diag.log("LIFECYCLE", "Opening Diagnostics Screen")
    }
    fun closeDiagnostics() {
        _showDiagnostics.value = false
        com.example.diag.Diag.log("LIFECYCLE", "Closing Diagnostics Screen")
    }
    fun openTestPlayground() {
        _showTestPlayground.value = true
        com.example.diag.Diag.log("LIFECYCLE", "Opening Voice Transcribe Test Playground")
    }
    fun closeTestPlayground() {
        _showTestPlayground.value = false
        com.example.diag.Diag.log("LIFECYCLE", "Closing Voice Transcribe Test Playground")
    }

    fun saveMenuItem(item: MenuItemEntity) {
        viewModelScope.launch {
            if (item.id == 0L) {
                inventoryRepository.insertItem(item)
            } else {
                inventoryRepository.updateItem(item)
            }
        }
    }

    fun deleteMenuItem(item: MenuItemEntity) {
        viewModelScope.launch {
            inventoryRepository.deleteItem(item)
        }
    }

    fun resetMenuDefaults() {
        viewModelScope.launch {
            inventoryRepository.resetToDefaults()
        }
    }

    fun setNoiseThreshold(threshold: Float) {
        noiseGate.setThreshold(threshold)
    }
}
