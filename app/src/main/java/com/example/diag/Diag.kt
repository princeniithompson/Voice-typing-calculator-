package com.example.diag

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

data class DiagLogEntry(
    val id: Long,
    val timestamp: Long,
    val formattedTimestamp: String,
    val category: String,
    val message: String
) {
    val fullText: String get() = "$formattedTimestamp [$category] $message"
}

object Diag {
    private const val TAG = "QuickCalcDiag"
    private const val MAX_MEMORY_ENTRIES = 5000
    private const val LOG_FILE_NAME = "diagnostics_log.txt"

    private val idCounter = AtomicLong(0)
    private val memoryLog = ConcurrentLinkedDeque<DiagLogEntry>()
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val fileWriterExecutor = Executors.newSingleThreadExecutor()

    private var appContext: Context? = null
    private var logFile: File? = null

    // Observability flow for UI updates (monotonic counter)
    private val _logVersion = MutableStateFlow(0L)
    val logVersion: StateFlow<Long> = _logVersion.asStateFlow()

    // Checklist state & historical timestamps
    var apiKeyMasked: String = "NOT CONFIGURED"
    var isApiKeySaved: Boolean = false
    var lastApiKeySavedTime: String? = null

    var isInternetReachable: Boolean = false
    var lastInternetTime: String? = null

    var isMicPermissionGranted: Boolean = false
    var lastMicPermissionTime: String? = null

    var isMicRecordingStarted: Boolean = false
    var lastMicRecordingStartedTime: String? = null

    var isAudioChunksBeingSent: Boolean = false
    var lastAudioSentTime: String? = null

    var isGeminiConnected: Boolean = false
    var lastGeminiConnectedTime: String? = null

    var isGeminiSetupAccepted: Boolean = false
    var lastSetupAcceptedTime: String? = null
    var setupTimedOut: Boolean = false

    var activeSpeechEngine: String = "GEMINI_LIVE (models/gemini-3.5-transcribe-live)"
    var isManualVoiceIsolated: Boolean = true
    var lastTranscriptReceivedTime: String? = null
    var lastFinalTranscriptReceivedTime: String? = null

    // Audio chunk accounting counters (distinguishing captured, transmitted, and noise-gated)
    val audioChunksCaptured = AtomicLong(0)
    val audioBytesCaptured = AtomicLong(0)
    val audioChunksSent = AtomicLong(0)
    val audioBytesSent = AtomicLong(0)
    val audioChunksGated = AtomicLong(0)
    val audioBytesGated = AtomicLong(0)

    val messagesReceivedByType = ConcurrentHashMap<String, AtomicInteger>()
    val transcriptsReceived = AtomicInteger(0)
    val finalTranscriptsReceived = AtomicInteger(0)
    val errorsLogged = AtomicInteger(0)

    // Latency metrics
    var sessionStartMs: Long = 0L
    var firstAudioSentMs: Long = 0L
    var timeToFirstTranscriptMs: Long? = null
    var timeAudioToFirstTranscriptMs: Long? = null

    private val checklistTimeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    private fun currentTimeFormatted(): String {
        return synchronized(checklistTimeFormat) { checklistTimeFormat.format(Date()) }
    }

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        logFile = File(context.filesDir, LOG_FILE_NAME)

        // Install uncaught exception handler
        val oldHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            val stack = sw.toString()
            log("ERROR", "Uncaught exception on thread '${thread.name}': ${throwable.message}\n$stack")
            errorsLogged.incrementAndGet()
            oldHandler?.uncaughtException(thread, throwable)
        }

        // Initialize environment & initial checklist values
        checkMicPermission(context, silent = true)
        checkInternet(context, silent = true)
        log("LIFECYCLE", "Diag initialized. Storage path: ${logFile?.absolutePath}")
        log("SPEECH_ENGINE", "Active speech engine: $activeSpeechEngine")
    }

    fun updateApiKey(rawKey: String?) {
        val key = rawKey?.trim().orEmpty()
        val previousState = isApiKeySaved
        isApiKeySaved = key.isNotBlank() && key != "MY_GEMINI_API_KEY" && !key.contains("YOUR_KEY") && key.length >= 10
        apiKeyMasked = maskKey(key)

        if (isApiKeySaved) {
            lastApiKeySavedTime = currentTimeFormatted()
        }

        if (previousState != isApiKeySaved || memoryLog.isEmpty()) {
            log("GEMINI", "API key status: $apiKeyMasked")
        }
    }

    fun maskKey(key: String): String {
        val clean = key.trim()
        return if (clean.isBlank() || clean == "MY_GEMINI_API_KEY" || clean.contains("YOUR_KEY") || clean.length < 10) {
            "NOT CONFIGURED"
        } else {
            "CONFIGURED"
        }
    }

    fun checkInternet(context: Context? = appContext, silent: Boolean = false): Boolean {
        val ctx = context ?: return false
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val isOnline = cm?.let {
            val net = it.activeNetwork ?: return@let false
            val caps = it.getNetworkCapabilities(net) ?: return@let false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } ?: false
        val previous = isInternetReachable
        isInternetReachable = isOnline
        if (isOnline) {
            lastInternetTime = currentTimeFormatted()
        }
        if (!silent && previous != isOnline) {
            log("NETWORK", "Internet connectivity changed: reachable=$isOnline")
        }
        return isOnline
    }

    fun checkMicPermission(context: Context? = appContext, silent: Boolean = false): Boolean {
        val ctx = context ?: return false
        val granted = ContextCompat.checkSelfPermission(
            ctx,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        val previous = isMicPermissionGranted
        isMicPermissionGranted = granted
        if (granted) {
            lastMicPermissionTime = currentTimeFormatted()
        }
        if (!silent && previous != granted) {
            log("AUDIO", "Microphone permission status changed: granted=$granted")
        }
        return granted
    }

    fun recordMicRecordingStarted() {
        isMicRecordingStarted = true
        lastMicRecordingStartedTime = currentTimeFormatted()
        log("AUDIO", "AudioRecord capture loop active (mic recording started at $lastMicRecordingStartedTime)")
    }

    fun recordGeminiConnected() {
        isGeminiConnected = true
        lastGeminiConnectedTime = currentTimeFormatted()
        log("GEMINI", "Gemini Live WebSocket connected at $lastGeminiConnectedTime")
    }

    fun recordSetupAccepted() {
        isGeminiSetupAccepted = true
        setupTimedOut = false
        lastSetupAcceptedTime = currentTimeFormatted()
        log("GEMINI", "Gemini setup accepted at $lastSetupAcceptedTime")
    }

    fun recordSetupTimeout() {
        setupTimedOut = true
        isGeminiSetupAccepted = false
        log("GEMINI", "Gemini setup timed out after 5 seconds")
    }

    fun recordAudioChunkCaptured(length: Int) {
        audioChunksCaptured.incrementAndGet()
        audioBytesCaptured.addAndGet(length.toLong())
    }

    fun recordAudioChunkGated(length: Int) {
        audioChunksGated.incrementAndGet()
        audioBytesGated.addAndGet(length.toLong())
    }

    fun recordAudioChunkSent(length: Int) {
        isAudioChunksBeingSent = true
        val count = audioChunksSent.incrementAndGet()
        audioBytesSent.addAndGet(length.toLong())
        lastAudioSentTime = currentTimeFormatted()

        if (count == 1L) {
            firstAudioSentMs = System.currentTimeMillis()
            val connToAudioMs = if (sessionStartMs > 0) firstAudioSentMs - sessionStartMs else 0
            log("GEMINI", "First audio chunk transmitted to Gemini Live (size=$length bytes, connection-to-audio latency=${connToAudioMs}ms)")
        }
    }

    fun recordMessageReceived(type: String) {
        messagesReceivedByType.computeIfAbsent(type) { AtomicInteger(0) }.incrementAndGet()
    }

    fun recordTranscriptReceived(delta: String) {
        val count = transcriptsReceived.incrementAndGet()
        lastTranscriptReceivedTime = currentTimeFormatted()
        val now = System.currentTimeMillis()

        if (sessionStartMs > 0 && timeToFirstTranscriptMs == null) {
            timeToFirstTranscriptMs = now - sessionStartMs
        }
        if (firstAudioSentMs > 0 && timeAudioToFirstTranscriptMs == null) {
            timeAudioToFirstTranscriptMs = now - firstAudioSentMs
        }

        log("GEMINI", "Transcript update #$count: '$delta' (last: $lastTranscriptReceivedTime)")
    }

    fun recordFinalTranscriptReceived(text: String) {
        finalTranscriptsReceived.incrementAndGet()
        lastFinalTranscriptReceivedTime = currentTimeFormatted()
        log("GEMINI", "Final transcription received: '$text' (at $lastFinalTranscriptReceivedTime)")
    }

    fun recordSessionClosure(reason: String) {
        isGeminiConnected = false
        log("GEMINI", "Session closure confirmed: $reason")
    }

    fun log(category: String, message: String) {
        val now = System.currentTimeMillis()
        val formattedTime = synchronized(timeFormat) { timeFormat.format(Date(now)) }
        val id = idCounter.incrementAndGet()
        val entry = DiagLogEntry(id, now, formattedTime, category, message)

        // Keep 5000 in memory
        memoryLog.addLast(entry)
        while (memoryLog.size > MAX_MEMORY_ENTRIES) {
            memoryLog.pollFirst()
        }

        if (category == "ERROR") {
            errorsLogged.incrementAndGet()
        }

        // Increment flow version to trigger UI refresh
        _logVersion.value = id

        // Mirror to Android Logcat
        try {
            when (category) {
                "ERROR" -> Log.e(TAG, "[$category] $message")
                "WARN" -> Log.w(TAG, "[$category] $message")
                else -> Log.d(TAG, "[$category] $message")
            }
        } catch (_: Exception) {
            // Unmocked Log in JVM unit tests
        }

        // Asynchronously persist to file
        val file = logFile
        if (file != null) {
            fileWriterExecutor.execute {
                try {
                    FileWriter(file, true).use { fw ->
                        fw.write(entry.fullText)
                        fw.write("\n")
                    }
                } catch (_: Exception) {
                    // Do not recursively log file errors
                }
            }
        }
    }

    fun logException(category: String, message: String, throwable: Throwable) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        log(category, "$message - ${throwable.message}\n$sw")
    }

    fun getEntries(): List<DiagLogEntry> {
        return memoryLog.toList()
    }

    /**
     * Completely resets and clears all diagnostics data, memory logs, log files,
     * latency metrics, and counters, starting completely fresh for an updated app version.
     */
    fun resetAll(context: Context? = appContext) {
        memoryLog.clear()
        logFile?.let { file ->
            fileWriterExecutor.execute {
                try {
                    file.writeText("")
                } catch (_: Exception) {}
            }
        }

        // Reset all counters
        audioChunksCaptured.set(0)
        audioBytesCaptured.set(0)
        audioChunksSent.set(0)
        audioBytesSent.set(0)
        audioChunksGated.set(0)
        audioBytesGated.set(0)
        messagesReceivedByType.clear()
        transcriptsReceived.set(0)
        finalTranscriptsReceived.set(0)
        errorsLogged.set(0)

        // Reset latency measurements
        sessionStartMs = 0L
        firstAudioSentMs = 0L
        timeToFirstTranscriptMs = null
        timeAudioToFirstTranscriptMs = null

        // Reset status flags & timestamps
        lastMicRecordingStartedTime = null
        lastAudioSentTime = null
        lastGeminiConnectedTime = null
        lastSetupAcceptedTime = null
        lastTranscriptReceivedTime = null
        lastFinalTranscriptReceivedTime = null
        setupTimedOut = false

        _logVersion.value = idCounter.incrementAndGet()

        val ctx = context ?: appContext
        if (ctx != null) {
            checkInternet(ctx, silent = true)
            checkMicPermission(ctx, silent = true)
        }

        log("DIAG", "Diagnostics system fully reset and refreshed by user.")
        log("ENVIRONMENT", "QuickCalc v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) on Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        log("SPEECH_ENGINE", "Speech engine: $activeSpeechEngine")
        log("AUDIO_GATE", "Noise gate lookahead (200ms) + hangover (500ms) active")
    }

    fun getSummaryText(context: Context): String {
        checkInternet(context, silent = true)
        checkMicPermission(context, silent = true)

        return buildString {
            appendLine("=== ENVIRONMENT ===")
            appendLine("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Android version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("API key: $apiKeyMasked")
            appendLine("Active speech engine: $activeSpeechEngine")
            appendLine("Internet available: ${if (isInternetReachable) "yes" else "no"}")
            appendLine("Mic permission: ${if (isMicPermissionGranted) "granted" else "denied"}")
            appendLine()
            appendLine("=== CHECKLIST ===")
            appendLine("- API key: $apiKeyMasked (last ${lastApiKeySavedTime ?: "N/A"})")
            appendLine("- Internet: ${if (isInternetReachable) "reachable (last ${lastInternetTime ?: "N/A"})" else "not reachable"}")
            appendLine("- Mic permission: ${if (isMicPermissionGranted) "granted (last ${lastMicPermissionTime ?: "N/A"})" else "denied"}")
            appendLine("- Mic recording started: ${if (lastMicRecordingStartedTime != null) "yes (last $lastMicRecordingStartedTime)" else "no"}")
            appendLine("- Audio transmitted: ${if (lastAudioSentTime != null) "yes (last $lastAudioSentTime, ${audioChunksSent.get()} chunks)" else "no"}")
            appendLine("- Gemini connected: ${if (lastGeminiConnectedTime != null) "yes (last $lastGeminiConnectedTime)" else "no"}")
            appendLine("- Setup accepted: ${if (lastSetupAcceptedTime != null) "yes (last $lastSetupAcceptedTime)" else if (setupTimedOut) "no (timed out after 5 s)" else "no"}")
            appendLine("- Transcript received: ${if (lastTranscriptReceivedTime != null) "yes (last $lastTranscriptReceivedTime, ${transcriptsReceived.get()} updates)" else "no"}")
            appendLine("- Final transcript received: ${if (lastFinalTranscriptReceivedTime != null) "yes (last $lastFinalTranscriptReceivedTime, ${finalTranscriptsReceived.get()} items)" else "none"}")
            appendLine("- Active speech engine: $activeSpeechEngine")
            appendLine("- Manual and voice states separate: ${if (isManualVoiceIsolated) "verified" else "leaked"}")
            appendLine()
            appendLine("=== AUDIO ACCOUNTING ===")
            appendLine("- Audio chunks captured: ${audioChunksCaptured.get()} (${audioBytesCaptured.get()} bytes)")
            appendLine("- Audio chunks transmitted: ${audioChunksSent.get()} (${audioBytesSent.get()} bytes)")
            appendLine("- Audio chunks noise-gated (silence): ${audioChunksGated.get()} (${audioBytesGated.get()} bytes)")
            appendLine()
            appendLine("=== PERFORMANCE & LATENCY ===")
            val connLatency = timeToFirstTranscriptMs?.let { "${it} ms" } ?: "N/A"
            val audioLatency = timeAudioToFirstTranscriptMs?.let { "${it} ms" } ?: "N/A"
            appendLine("- Connection + First Transcript Latency: $connLatency")
            appendLine("- Audio-to-Transcript Latency: $audioLatency")
            appendLine("- Transcripts received: ${transcriptsReceived.get()} (final: ${finalTranscriptsReceived.get()})")
            appendLine("- Errors logged: ${errorsLogged.get()}")
            val msgs = if (messagesReceivedByType.isEmpty()) "none" else messagesReceivedByType.entries.joinToString { "${it.key}=${it.value.get()}" }
            appendLine("- Messages received by type: $msgs")
            appendLine()
            appendLine("=== RECENT TIMELINE (Last 30 lines) ===")
            val recent = memoryLog.toList().takeLast(30)
            if (recent.isEmpty()) {
                appendLine("(no log entries recorded yet)")
            } else {
                recent.forEach { appendLine(it.fullText) }
            }
        }
    }

    fun getFullLogText(context: Context): String {
        checkInternet(context, silent = true)
        checkMicPermission(context, silent = true)

        return buildString {
            appendLine("=== ENVIRONMENT ===")
            appendLine("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Android version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("API key: $apiKeyMasked")
            appendLine("Active speech engine: $activeSpeechEngine")
            appendLine("Internet available: ${if (isInternetReachable) "yes" else "no"}")
            appendLine("Mic permission: ${if (isMicPermissionGranted) "granted" else "denied"}")
            appendLine()
            appendLine("=== CHECKLIST ===")
            appendLine("- API key: $apiKeyMasked (last ${lastApiKeySavedTime ?: "N/A"})")
            appendLine("- Internet: ${if (isInternetReachable) "reachable (last ${lastInternetTime ?: "N/A"})" else "not reachable"}")
            appendLine("- Mic permission: ${if (isMicPermissionGranted) "granted (last ${lastMicPermissionTime ?: "N/A"})" else "denied"}")
            appendLine("- Mic recording started: ${if (lastMicRecordingStartedTime != null) "yes (last $lastMicRecordingStartedTime)" else "no"}")
            appendLine("- Audio transmitted: ${if (lastAudioSentTime != null) "yes (last $lastAudioSentTime, ${audioChunksSent.get()} chunks)" else "no"}")
            appendLine("- Gemini connected: ${if (lastGeminiConnectedTime != null) "yes (last $lastGeminiConnectedTime)" else "no"}")
            appendLine("- Setup accepted: ${if (lastSetupAcceptedTime != null) "yes (last $lastSetupAcceptedTime)" else if (setupTimedOut) "no (timed out after 5 s)" else "no"}")
            appendLine("- Transcript received: ${if (lastTranscriptReceivedTime != null) "yes (last $lastTranscriptReceivedTime, ${transcriptsReceived.get()} updates)" else "no"}")
            appendLine("- Final transcript received: ${if (lastFinalTranscriptReceivedTime != null) "yes (last $lastFinalTranscriptReceivedTime, ${finalTranscriptsReceived.get()} items)" else "none"}")
            appendLine("- Active speech engine: $activeSpeechEngine")
            appendLine("- Manual and voice states separate: ${if (isManualVoiceIsolated) "verified" else "leaked"}")
            appendLine()
            appendLine("=== AUDIO ACCOUNTING ===")
            appendLine("- Audio chunks captured: ${audioChunksCaptured.get()} (${audioBytesCaptured.get()} bytes)")
            appendLine("- Audio chunks transmitted: ${audioChunksSent.get()} (${audioBytesSent.get()} bytes)")
            appendLine("- Audio chunks noise-gated (silence): ${audioChunksGated.get()} (${audioBytesGated.get()} bytes)")
            appendLine()
            appendLine("=== PERFORMANCE & LATENCY ===")
            val connLatency = timeToFirstTranscriptMs?.let { "${it} ms" } ?: "N/A"
            val audioLatency = timeAudioToFirstTranscriptMs?.let { "${it} ms" } ?: "N/A"
            appendLine("- Connection + First Transcript Latency: $connLatency")
            appendLine("- Audio-to-Transcript Latency: $audioLatency")
            appendLine("- Transcripts received: ${transcriptsReceived.get()} (final: ${finalTranscriptsReceived.get()})")
            appendLine("- Errors logged: ${errorsLogged.get()}")
            val msgs = if (messagesReceivedByType.isEmpty()) "none" else messagesReceivedByType.entries.joinToString { "${it.key}=${it.value.get()}" }
            appendLine("- Messages received by type: $msgs")
            appendLine()
            appendLine("=== TIMELINE ===")
            val allEntries = memoryLog.toList()
            if (allEntries.isEmpty()) {
                appendLine("(no log entries recorded yet)")
            } else {
                allEntries.forEach { appendLine(it.fullText) }
            }
        }
    }

    fun saveAndShareLogFile(context: Context) {
        try {
            log("DIAG", "Log export initiated: preparing diagnostics file")
            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            val fileName = "quickcalc_diagnostics_${dateFormat.format(Date())}.txt"
            val fullText = getFullLogText(context)

            val exportFile = File(context.filesDir, fileName)
            exportFile.writeText(fullText)
            log("DIAG", "Diagnostics file generated: $fileName (${exportFile.length()} bytes)")

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                exportFile
            )

            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "QuickCalc Diagnostics Log - ${exportFile.name}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(sendIntent, "Share Diagnostics Log").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            log("DIAG", "Share sheet opened successfully for file: $fileName")
        } catch (e: Exception) {
            logException("ERROR", "Failed to save and share diagnostics log file", e)
        }
    }
}
