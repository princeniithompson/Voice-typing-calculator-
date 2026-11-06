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

    val noiseGate = AudioNoiseGate(initialThresholdDb = -45.0f)
    private val manualHandler = ManualKeypadHandler()

    val voiceTranscriptionManager = VoiceTranscriptionManager(
        scope = viewModelScope,
        noiseGate = noiseGate,
        menuItemsProvider = { pricingRepository.cachedItems },
        isSmartTranscriptionEnabledProvider = { _isSmartTranscriptionEnabled.value }
    )

    // 1. Surface Toggle: false = Surface A (Classic Google Calculator parity), true = Surface B (Voice assist)
    private val _isVoiceAssistEnabled = MutableStateFlow(
        prefs.getBoolean("prefs_voice_assist_enabled", true)
    )
    val isVoiceAssistEnabled: StateFlow<Boolean> = _isVoiceAssistEnabled.asStateFlow()

    // Gemini API Key management
    private fun getBuildConfigApiKey(): String {
        return try {
            val field = com.example.BuildConfig::class.java.getField("GEMINI_API_KEY")
            (field.get(null) as? String) ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    private val _geminiApiKey = MutableStateFlow(
        prefs.getString("prefs_gemini_api_key", null)?.takeIf { it.isNotBlank() } ?: getBuildConfigApiKey()
    )
    val geminiApiKey: StateFlow<String> = _geminiApiKey.asStateFlow()

    fun saveGeminiApiKey(key: String) {
        val trimmed = key.trim()
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
        prefs.getStringSet(
            "prefs_test_vocabulary",
            setOf("princeniithompson@gmail.com", "30first", "Banku", "Fried Fish", "Waakye", "Kelewele")
        )?.toList() ?: listOf("princeniithompson@gmail.com", "30first", "Banku", "Fried Fish", "Waakye", "Kelewele")
    )
    val testVocabularyList: StateFlow<List<String>> = _testVocabularyList.asStateFlow()

    init {
        com.example.voice.SharedVocabularyManager.setTerms(_testVocabularyList.value)
    }

    fun toggleSmartTranscription(enabled: Boolean) {
        _isSmartTranscriptionEnabled.value = enabled
        prefs.edit().putBoolean("prefs_smart_transcription", enabled).apply()
        com.example.diag.Diag.log("GEMINI", "Smart transcription mode toggled to: $enabled")
        if (voiceTranscriptionManager.isListening.value) {
            voiceTranscriptionManager.restartSession(_geminiApiKey.value)
            com.example.diag.Diag.log("GEMINI", "Restarted active Gemini session to apply new Smart Mode: $enabled")
        }
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
            com.example.voice.SharedVocabularyManager.addTerm(trimmed)
            prefs.edit().putStringSet("prefs_test_vocabulary", updated.toSet()).apply()
        }
    }

    fun removeTestVocabulary(phrase: String) {
        val updated = _testVocabularyList.value.filter { it != phrase }
        _testVocabularyList.value = updated
        com.example.voice.SharedVocabularyManager.removeTerm(phrase)
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

        voiceTranscriptionManager.onVoiceResultUpdated = { transcript, items, total, rawOutput ->
            updateVoiceTranscription(transcript, items, total, rawOutput)
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
    
    /**
     * Mic button tap handler.
     * IDLE -> start listening.
     * LISTENING/PAUSED -> finish session, commit order to history, and return to IDLE.
     */
    fun onMicTap() {
        val currentMode = _voiceState.value.mode
        if (currentMode == VoiceMode.IDLE) {
            _voiceState.value = _voiceState.value.copy(
                mode = VoiceMode.LISTENING,
                liveTranscript = "",
                detectedItems = emptyList(),
                total = BigDecimal.ZERO,
                pendingItems = emptyList(),
                pendingTotal = BigDecimal.ZERO,
                accumulatedBatches = emptyList(),
                lastBatchTotal = null,
                isEvaluated = false,
                statusCaption = "Listening... tap = to calculate"
            )
            com.example.diag.Diag.log("VOICE_STATE", "Mic state -> LISTENING, reason: user tapped mic")
            voiceTranscriptionManager.startListening(_geminiApiKey.value)
        } else {
            // LISTENING or PAUSED -> User tapped Finish / Stop
            commitVoiceOrderToHistory()
            _voiceState.value = _voiceState.value.copy(
                mode = VoiceMode.IDLE,
                statusCaption = "Tap the mic to start"
            )
            com.example.diag.Diag.log("VOICE_STATE", "Mic state -> IDLE, reason: user tapped finish mic")
            voiceTranscriptionManager.stopListening()
        }
    }

    /**
     * Voice Equal (=) button tap handler in the voice bar.
     * 1. Evaluates current batch speech with EchoGuardFilter to prevent duplicate number echoes.
     * 2. Accumulates batch to running session total without clearing the display container.
     * 3. Cleanly restarts Gemini Live transcription stream for the next section.
     */
    fun onVoiceEqualTap() {
        val state = _voiceState.value
        val rawTranscript = state.liveTranscript.ifBlank { "0" }

        // Filter out any standalone repetition of the previous batch total (e.g. "56, plus 2 cups...")
        val filteredTranscript = com.example.voice.EchoGuardFilter.filterEchoedTotal(rawTranscript, state.lastBatchTotal)
        val parseResult = voiceTranscriptionManager.parseTranscriptManually(filteredTranscript.ifBlank { rawTranscript })

        val batchSubtotal = if (parseResult.total > BigDecimal.ZERO) parseResult.total else state.pendingTotal
        val batchItems = if (parseResult.items.isNotEmpty()) parseResult.items else state.pendingItems

        if (batchSubtotal <= BigDecimal.ZERO && batchItems.isEmpty()) {
            com.example.diag.Diag.log("VOICE_STATE", "Equal tapped with no new items; continuing stream.")
            return
        }

        val nextBatchNum = state.accumulatedBatches.size + 1
        val itemsSummary = if (batchItems.isNotEmpty()) {
            batchItems.joinToString(", ") { "${it.quantity} ${it.name}" }
        } else {
            rawTranscript
        }

        val newBatch = com.example.voice.VoiceBatch(
            batchNumber = nextBatchNum,
            subtotal = batchSubtotal,
            itemsSummary = itemsSummary,
            items = batchItems
        )

        val updatedBatches = state.accumulatedBatches + newBatch
        val newAccumulatedTotal = updatedBatches.fold(BigDecimal.ZERO) { acc, b -> acc.add(b.subtotal) }
        val allAccumulatedItems = updatedBatches.flatMap { it.items }

        com.example.diag.Diag.log(
            "VOICE_STATE",
            "Batch $nextBatchNum calculated: subtotal=GH₵ $batchSubtotal, newGrandTotal=GH₵ $newAccumulatedTotal"
        )

        _voiceState.value = state.copy(
            total = newAccumulatedTotal,
            detectedItems = allAccumulatedItems,
            accumulatedBatches = updatedBatches,
            lastBatchTotal = batchSubtotal,
            liveTranscript = "",
            pendingItems = emptyList(),
            pendingTotal = BigDecimal.ZERO,
            isEvaluated = true,
            statusCaption = "Batch $nextBatchNum: GH₵ ${String.format(java.util.Locale.US, "%.2f", batchSubtotal)} (Total: GH₵ ${String.format(java.util.Locale.US, "%.2f", newAccumulatedTotal)})"
        )

        // Cleanly restart Gemini Live stream for the next section so voice context starts fresh
        voiceTranscriptionManager.evaluateVoice()
        voiceTranscriptionManager.restartSession(_geminiApiKey.value)
    }

    /**
     * Pause / Resume toggle button handler.
     */
    fun onPauseTap() {
        val currentMode = _voiceState.value.mode
        if (currentMode == VoiceMode.LISTENING) {
            _voiceState.value = _voiceState.value.copy(
                mode = VoiceMode.PAUSED,
                statusCaption = "Paused. The mic is muted"
            )
            com.example.diag.Diag.log("VOICE_STATE", "Mic state -> PAUSED, reason: user tapped pause")
            voiceTranscriptionManager.pauseStreaming()
        } else if (currentMode == VoiceMode.PAUSED) {
            _voiceState.value = _voiceState.value.copy(
                mode = VoiceMode.LISTENING,
                statusCaption = "Listening... tap the mic to finish"
            )
            com.example.diag.Diag.log("VOICE_STATE", "Mic state -> LISTENING, reason: user tapped resume")
            voiceTranscriptionManager.resumeStreaming()
        }
    }

    /**
     * AC pill handler (while LISTENING or PAUSED).
     * Saves current session to History, clears Voice card, and restarts Gemini session.
     */
    fun onACTap() {
        commitVoiceOrderToHistory()

        val isCurrentlyPaused = _voiceState.value.mode == VoiceMode.PAUSED

        // Clear Voice card completely
        _voiceState.value = _voiceState.value.copy(
            liveTranscript = "",
            detectedItems = emptyList(),
            total = BigDecimal.ZERO,
            pendingItems = emptyList(),
            pendingTotal = BigDecimal.ZERO,
            accumulatedBatches = emptyList(),
            lastBatchTotal = null,
            isEvaluated = false,
            statusCaption = "Saved to history. New order started."
        )

        // Restart clean Gemini session
        voiceTranscriptionManager.restartSession(_geminiApiKey.value)

        if (isCurrentlyPaused) {
            voiceTranscriptionManager.pauseStreaming()
        }

        // Reset status caption back to normal after 2 seconds
        viewModelScope.launch {
            kotlinx.coroutines.delay(2000)
            val currentMode = _voiceState.value.mode
            val newCaption = if (currentMode == VoiceMode.PAUSED) {
                "Paused. The mic is muted"
            } else if (currentMode == VoiceMode.LISTENING) {
                "Listening... tap the mic to finish"
            } else {
                "Tap the mic to start"
            }
            _voiceState.value = _voiceState.value.copy(statusCaption = newCaption)
        }
    }

    /**
     * 60-second silence safeguard trigger.
     */
    fun onSilenceAutoPause() {
        if (_voiceState.value.mode == VoiceMode.LISTENING) {
            _voiceState.value = _voiceState.value.copy(
                mode = VoiceMode.PAUSED,
                statusCaption = "Paused: no speech heard"
            )
            com.example.diag.Diag.log("VOICE_STATE", "Mic state -> PAUSED, reason: 60s silence timeout")
            voiceTranscriptionManager.pauseStreaming()
        }
    }

    /**
     * App backgrounded or screen turned off handler.
     */
    fun onAppBackgrounded() {
        if (_voiceState.value.mode != VoiceMode.IDLE) {
            _voiceState.value = _voiceState.value.copy(
                mode = VoiceMode.IDLE,
                statusCaption = "Tap the mic to start"
            )
            com.example.diag.Diag.log("VOICE_STATE", "Mic state -> IDLE, reason: app backgrounded")
            voiceTranscriptionManager.stopListening()
        }
    }

    // Backward-compatibility wrappers for old callers
    fun onHoldStart() { onMicTap() }
    fun onHoldRelease(swipedToLock: Boolean) { /* Taps only */ }
    fun onVoiceLockToggle() { onMicTap() }
    fun onVoicePauseToggle() { onPauseTap() }
    fun onClearVoiceCard() { onACTap() }

    // --- Voice Transcript & Total Updates (Simulated / Live Flow: ONLY modifies VoiceState) ---
    fun updateVoiceTranscription(
        transcript: String,
        detectedItems: List<DetectedVoiceItem>,
        total: BigDecimal,
        rawModelOutput: String = ""
    ) {
        val currentState = _voiceState.value
        val effectiveRaw = rawModelOutput.ifBlank { transcript }
        com.example.diag.Diag.log(
            "CALC",
            "Voice calc update: transcript='$transcript', items=${detectedItems.size}, total=$total, isEvaluated=${currentState.isEvaluated}"
        )
        com.example.diag.Diag.log(
            "GEMINI_RAW_RESPONSE",
            "Raw Gemini Model Response: '$effectiveRaw'"
        )
        if (currentState.isEvaluated) {
            _voiceState.value = currentState.copy(
                liveTranscript = transcript,
                rawModelOutput = effectiveRaw,
                detectedItems = detectedItems,
                total = total,
                pendingItems = detectedItems,
                pendingTotal = total
            )
        } else {
            _voiceState.value = currentState.copy(
                liveTranscript = transcript,
                rawModelOutput = effectiveRaw,
                pendingItems = detectedItems,
                pendingTotal = total
            )
        }
    }

    fun setVoiceStatusCaption(caption: String?) {
        _voiceState.value = _voiceState.value.copy(statusCaption = caption)
    }

    /**
     * Commits all accumulated batches from the current voice session to the database.
     * Preserves individual batch breakdowns (e.g. Batch 1, Batch 2...) in order item entities.
     */
    private fun commitVoiceOrderToHistory() {
        val state = _voiceState.value
        val batches = state.accumulatedBatches
        val grandTotal = state.total.toDouble()

        if (grandTotal <= 0.0 && batches.isEmpty()) return

        viewModelScope.launch {
            val orderItems = if (batches.isNotEmpty()) {
                batches.map { batch ->
                    OrderItemEntity(
                        orderId = 0,
                        menuItemId = 0,
                        name = "Batch ${batch.batchNumber}: ${batch.itemsSummary}",
                        unitPrice = batch.subtotal.toDouble(),
                        quantity = 1,
                        subtotal = batch.subtotal.toDouble(),
                        unitLabel = "${batch.batchNumber}-in-${batches.size}"
                    )
                }
            } else {
                state.detectedItems.map { item ->
                    OrderItemEntity(
                        orderId = 0,
                        menuItemId = 0,
                        name = item.name,
                        unitPrice = item.unitPrice.toDouble(),
                        quantity = item.quantity,
                        subtotal = item.lineTotal.toDouble(),
                        unitLabel = "1-in-1"
                    )
                }
            }

            val sessionType = if (batches.size > 1) "${batches.size}-in-1 Session" else "1-in-1 Session"

            orderRepository.createOrder(
                orderType = sessionType,
                subtotal = grandTotal,
                grandTotal = grandTotal,
                paymentMethod = "VOICE",
                items = orderItems
            )
            com.example.diag.Diag.log("VOICE_STATE", "Committed $sessionType to history: GH₵ $grandTotal")
        }
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
                    unitLabel = "1-in-1"
                )
            }
            orderRepository.createOrder(
                orderType = "1-in-1 Session",
                subtotal = total,
                grandTotal = total,
                paymentMethod = "VOICE",
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
