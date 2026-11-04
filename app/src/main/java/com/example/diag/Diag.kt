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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    var apiKeyMasked: String = "NOT SET"
    var apiKeyLength: Int = 0
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

    // Counters
    val audioChunksSent = AtomicLong(0)
    val audioBytesSent = AtomicLong(0)
    val messagesReceivedByType = ConcurrentHashMap<String, AtomicInteger>()
    val transcriptsReceived = AtomicInteger(0)
    val errorsLogged = AtomicInteger(0)
    var sessionStartMs: Long = 0L
    var timeToFirstTranscriptMs: Long? = null

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
        checkMicPermission(context)
        checkInternet(context)
        log("LIFECYCLE", "Diag initialized. Storage path: ${logFile?.absolutePath}")
        log("SPEECH_ENGINE", "Active speech engine: $activeSpeechEngine")
    }

    fun updateApiKey(rawKey: String?) {
        val key = rawKey?.trim().orEmpty()
        apiKeyLength = key.length
        isApiKeySaved = key.isNotBlank()
        if (isApiKeySaved) {
            lastApiKeySavedTime = currentTimeFormatted()
        }
        apiKeyMasked = maskKey(key)
        log("GEMINI", "API key updated: $apiKeyMasked")
    }

    fun maskKey(key: String): String {
        return if (key.isBlank()) {
            "NOT SET"
        } else {
            "set, length ${key.length}, ends with ${key.takeLast(4)}"
        }
    }

    fun checkInternet(context: Context? = appContext): Boolean {
        val ctx = context ?: return false
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val isOnline = cm?.let {
            val net = it.activeNetwork ?: return@let false
            val caps = it.getNetworkCapabilities(net) ?: return@let false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } ?: false
        isInternetReachable = isOnline
        if (isOnline) {
            lastInternetTime = currentTimeFormatted()
        }
        return isOnline
    }

    fun checkMicPermission(context: Context? = appContext): Boolean {
        val ctx = context ?: return false
        val granted = ContextCompat.checkSelfPermission(
            ctx,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        isMicPermissionGranted = granted
        if (granted) {
            lastMicPermissionTime = currentTimeFormatted()
        }
        return granted
    }

    fun recordMicRecordingStarted() {
        isMicRecordingStarted = true
        lastMicRecordingStartedTime = currentTimeFormatted()
    }

    fun recordGeminiConnected() {
        isGeminiConnected = true
        lastGeminiConnectedTime = currentTimeFormatted()
    }

    fun recordSetupAccepted() {
        isGeminiSetupAccepted = true
        setupTimedOut = false
        lastSetupAcceptedTime = currentTimeFormatted()
        log("GEMINI", "Gemini setup accepted (last: $lastSetupAcceptedTime)")
    }

    fun recordSetupTimeout() {
        setupTimedOut = true
        isGeminiSetupAccepted = false
        log("GEMINI", "No setup response after 5 s")
    }

    fun recordAudioChunkSent(length: Int) {
        isAudioChunksBeingSent = true
        val count = audioChunksSent.incrementAndGet()
        val totalBytes = audioBytesSent.addAndGet(length.toLong())
        lastAudioSentTime = currentTimeFormatted()

        if (count == 1L) {
            log("GEMINI", "First audio chunk sent to Gemini Live (size=$length bytes)")
        }
    }

    fun recordMessageReceived(type: String) {
        messagesReceivedByType.computeIfAbsent(type) { AtomicInteger(0) }.incrementAndGet()
    }

    fun recordTranscriptReceived(delta: String) {
        val count = transcriptsReceived.incrementAndGet()
        lastTranscriptReceivedTime = currentTimeFormatted()
        if (sessionStartMs > 0 && timeToFirstTranscriptMs == null) {
            timeToFirstTranscriptMs = System.currentTimeMillis() - sessionStartMs
        }
        log("GEMINI", "Transcript #$count delta: '$delta' (last: $lastTranscriptReceivedTime)")
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
        when (category) {
            "ERROR" -> Log.e(TAG, "[$category] $message")
            "WARN" -> Log.w(TAG, "[$category] $message")
            else -> Log.d(TAG, "[$category] $message")
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

    fun clearLog() {
        memoryLog.clear()
        _logVersion.value = idCounter.incrementAndGet()
        logFile?.let { file ->
            fileWriterExecutor.execute {
                try {
                    file.writeText("")
                } catch (_: Exception) {}
            }
        }
        log("DIAG", "Diagnostics log cleared by user.")
    }

    fun getSummaryText(context: Context): String {
        checkInternet(context)
        checkMicPermission(context)

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
            appendLine("- API key saved: ${if (isApiKeySaved) "yes ($apiKeyLength chars, last ${lastApiKeySavedTime ?: "N/A"})" else "no"}")
            appendLine("- Internet: ${if (isInternetReachable) "reachable (last ${lastInternetTime ?: "N/A"})" else "not reachable"}")
            appendLine("- Mic permission: ${if (isMicPermissionGranted) "granted (last ${lastMicPermissionTime ?: "N/A"})" else "denied"}")
            appendLine("- Mic recording started: ${if (lastMicRecordingStartedTime != null) "yes (last $lastMicRecordingStartedTime)" else "no"}")
            appendLine("- Audio sent: ${if (lastAudioSentTime != null) "yes (last $lastAudioSentTime, ${audioChunksSent.get()} chunks)" else "no"}")
            appendLine("- Gemini connected: ${if (lastGeminiConnectedTime != null) "yes (last $lastGeminiConnectedTime)" else "no"}")
            appendLine("- Setup accepted: ${if (lastSetupAcceptedTime != null) "yes (last $lastSetupAcceptedTime)" else if (setupTimedOut) "no (timed out after 5 s)" else "no"}")
            appendLine("- Transcript received: ${if (lastTranscriptReceivedTime != null) "yes (last $lastTranscriptReceivedTime, ${transcriptsReceived.get()} items)" else "no"}")
            appendLine("- Active speech engine: $activeSpeechEngine")
            appendLine("- Manual and voice states separate: ${if (isManualVoiceIsolated) "verified" else "leaked"}")
            appendLine()
            appendLine("=== COUNTERS ===")
            appendLine("- audio chunks sent: ${audioChunksSent.get()}, bytes sent: ${audioBytesSent.get()}")
            val msgs = if (messagesReceivedByType.isEmpty()) "none" else messagesReceivedByType.entries.joinToString { "${it.key}=${it.value.get()}" }
            appendLine("- messages received by type: $msgs")
            appendLine("- transcripts received: ${transcriptsReceived.get()}")
            appendLine("- errors logged: ${errorsLogged.get()}")
            val ttft = timeToFirstTranscriptMs?.let { "${it}ms" } ?: "none"
            appendLine("- time to first transcript: $ttft")
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
        checkInternet(context)
        checkMicPermission(context)

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
            appendLine("- API key saved: ${if (isApiKeySaved) "yes ($apiKeyLength chars, last ${lastApiKeySavedTime ?: "N/A"})" else "no"}")
            appendLine("- Internet: ${if (isInternetReachable) "reachable (last ${lastInternetTime ?: "N/A"})" else "not reachable"}")
            appendLine("- Mic permission: ${if (isMicPermissionGranted) "granted (last ${lastMicPermissionTime ?: "N/A"})" else "denied"}")
            appendLine("- Mic recording started: ${if (lastMicRecordingStartedTime != null) "yes (last $lastMicRecordingStartedTime)" else "no"}")
            appendLine("- Audio sent: ${if (lastAudioSentTime != null) "yes (last $lastAudioSentTime, ${audioChunksSent.get()} chunks)" else "no"}")
            appendLine("- Gemini connected: ${if (lastGeminiConnectedTime != null) "yes (last $lastGeminiConnectedTime)" else "no"}")
            appendLine("- Setup accepted: ${if (lastSetupAcceptedTime != null) "yes (last $lastSetupAcceptedTime)" else if (setupTimedOut) "no (timed out after 5 s)" else "no"}")
            appendLine("- Transcript received: ${if (lastTranscriptReceivedTime != null) "yes (last $lastTranscriptReceivedTime, ${transcriptsReceived.get()} items)" else "no"}")
            appendLine("- Active speech engine: $activeSpeechEngine")
            appendLine("- Manual and voice states separate: ${if (isManualVoiceIsolated) "verified" else "leaked"}")
            appendLine()
            appendLine("=== COUNTERS ===")
            appendLine("- audio chunks sent: ${audioChunksSent.get()}, bytes sent: ${audioBytesSent.get()}")
            val msgs = if (messagesReceivedByType.isEmpty()) "none" else messagesReceivedByType.entries.joinToString { "${it.key}=${it.value.get()}" }
            appendLine("- messages received by type: $msgs")
            appendLine("- transcripts received: ${transcriptsReceived.get()}")
            appendLine("- errors logged: ${errorsLogged.get()}")
            val ttft = timeToFirstTranscriptMs?.let { "${it}ms" } ?: "none"
            appendLine("- time to first transcript: $ttft")
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
            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            val fileName = "quickcalc_diagnostics_${dateFormat.format(Date())}.txt"
            val fullText = getFullLogText(context)

            val exportFile = File(context.filesDir, fileName)
            exportFile.writeText(fullText)

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
            log("DIAG", "Exported and opened share sheet for file: $fileName")
        } catch (e: Exception) {
            logException("ERROR", "Failed to save and share diagnostics log file", e)
        }
    }
}
