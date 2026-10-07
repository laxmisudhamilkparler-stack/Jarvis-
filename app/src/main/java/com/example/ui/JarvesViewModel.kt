package com.example.ui

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bridge.ActionResult
import com.example.bridge.AndroidAppActionBridge
import com.example.bridge.ContactMatch
import com.example.bridge.ToolExecutionReport
import com.example.gemini.AudioPlayer
import com.example.gemini.GeminiLiveClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Locale

enum class AssistantState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING
}

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val languageBadge: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

class JarvesViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext

    val audioPlayer = AudioPlayer(context)

    val actionBridge = AndroidAppActionBridge(context) { actionResult ->
        handleActionResult(actionResult)
    }

    private val geminiClient = GeminiLiveClient(actionBridge, audioPlayer)

    private val _assistantState = MutableStateFlow(AssistantState.IDLE)
    val assistantState: StateFlow<AssistantState> = _assistantState.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _latestReport = MutableStateFlow<ToolExecutionReport?>(null)
    val latestReport: StateFlow<ToolExecutionReport?> = _latestReport.asStateFlow()

    private val _disambiguationCandidates = MutableStateFlow<List<ContactMatch>?>(null)
    val disambiguationCandidates: StateFlow<List<ContactMatch>?> = _disambiguationCandidates.asStateFlow()

    private val _liveSpokenText = MutableStateFlow("")
    val liveSpokenText: StateFlow<String> = _liveSpokenText.asStateFlow()

    private val _micPermissionGranted = MutableStateFlow(false)
    val micPermissionGranted: StateFlow<Boolean> = _micPermissionGranted.asStateFlow()

    private val _contactsPermissionGranted = MutableStateFlow(false)
    val contactsPermissionGranted: StateFlow<Boolean> = _contactsPermissionGranted.asStateFlow()

    private val _callPermissionGranted = MutableStateFlow(false)
    val callPermissionGranted: StateFlow<Boolean> = _callPermissionGranted.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null

    init {
        checkPermissions()
        // Welcome message
        _messages.value = listOf(
            ChatMessage(
                isUser = false,
                text = "Namaste! I am Jarves. You can speak to me naturally in Hindi, English, Hinglish, Marathi, Tamil, or any language. Try saying 'WhatsApp kholo', 'Call Mummy', or 'Open YouTube'!",
                languageBadge = "Hinglish / Multi"
            )
        )

        // Observe audio player state for AssistantState.SPEAKING
        viewModelScope.launch {
            audioPlayer.isPlaying.collect { playing ->
                if (playing) {
                    _assistantState.value = AssistantState.SPEAKING
                } else if (_assistantState.value == AssistantState.SPEAKING) {
                    _assistantState.value = AssistantState.IDLE
                }
            }
        }
    }

    fun checkPermissions() {
        _micPermissionGranted.value = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        _contactsPermissionGranted.value = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        _callPermissionGranted.value = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun handleActionResult(result: ActionResult) {
        val toolName = when (result) {
            is ActionResult.Success -> result.action
            is ActionResult.Failure -> result.action
            is ActionResult.DisambiguationNeeded -> result.action
        }
        val report = ToolExecutionReport(
            toolName = toolName,
            parameters = emptyMap(),
            result = result
        )
        _latestReport.value = report

        if (result is ActionResult.DisambiguationNeeded) {
            _disambiguationCandidates.value = result.candidates
        } else {
            _disambiguationCandidates.value = null
        }
    }

    fun dismissDisambiguation() {
        _disambiguationCandidates.value = null
    }

    fun selectContactCandidate(contact: ContactMatch) {
        _disambiguationCandidates.value = null
        actionBridge.makeCall(contact.phoneNumber)
        val msg = "Calling ${contact.name} (${contact.phoneNumber})..."
        addJarvesMessage(msg, "Action")
        audioPlayer.speakFallback(msg)
    }

    fun executeManualAction(command: String) {
        // Interrupt any ongoing speech
        interruptSpeaking()

        addUserMessage(command)
        _assistantState.value = AssistantState.THINKING

        viewModelScope.launch {
            geminiClient.processUserMessage(
                userText = command,
                onJarvesTextResponse = { text ->
                    addJarvesMessage(text, detectLanguage(text))
                },
                onToolExecuted = { name, json ->
                    // handled by action result callback or update latestReport
                }
            )
            if (!audioPlayer.isPlaying.value) {
                _assistantState.value = AssistantState.IDLE
            }
        }
    }

    fun startListening() {
        // Interruption: Stop current voice output immediately when user wants to speak!
        interruptSpeaking()

        if (!_micPermissionGranted.value) {
            addJarvesMessage("Microphone permission is required to listen. Please allow microphone access.", "System")
            return
        }

        try {
            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            }

            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _assistantState.value = AssistantState.LISTENING
                    _liveSpokenText.value = "Listening..."
                }

                override fun onBeginningOfSpeech() {
                    _liveSpokenText.value = "Listening to you..."
                }

                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    _assistantState.value = AssistantState.THINKING
                    _liveSpokenText.value = ""
                }

                override fun onError(error: Int) {
                    _assistantState.value = AssistantState.IDLE
                    _liveSpokenText.value = ""
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val spoken = matches?.firstOrNull() ?: ""
                    _liveSpokenText.value = ""
                    if (spoken.isNotEmpty()) {
                        executeManualAction(spoken)
                    } else {
                        _assistantState.value = AssistantState.IDLE
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val partial = matches?.firstOrNull() ?: ""
                    if (partial.isNotEmpty()) {
                        _liveSpokenText.value = partial
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                // Hindi & English multilingual Indian speech support
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }

            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            _assistantState.value = AssistantState.IDLE
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {}
        if (_assistantState.value == AssistantState.LISTENING) {
            _assistantState.value = AssistantState.IDLE
        }
    }

    fun interruptSpeaking() {
        audioPlayer.stop()
        if (_assistantState.value == AssistantState.SPEAKING) {
            _assistantState.value = AssistantState.IDLE
        }
    }

    fun clearHistory() {
        interruptSpeaking()
        geminiClient.clearHistory()
        _messages.value = emptyList()
        _latestReport.value = null
        _disambiguationCandidates.value = null
    }

    private fun addUserMessage(text: String) {
        val badge = detectLanguage(text)
        _messages.value = _messages.value + ChatMessage(isUser = true, text = text, languageBadge = badge)
    }

    private fun addJarvesMessage(text: String, badge: String?) {
        _messages.value = _messages.value + ChatMessage(isUser = false, text = text, languageBadge = badge)
    }

    private fun detectLanguage(text: String): String {
        val lower = text.lowercase()
        // Check Devanagari Unicode block
        val hasDevanagari = text.any { it in '\u0900'..'\u097F' }
        if (hasDevanagari) return "Hindi"

        val hinglishWords = listOf("kholo", "karo", "chalao", "lagao", "main", "meri", "mera", "bilkul", "baat", "kahan", "kaise", "kya", "shukriya", "dhanyawad", "sunao")
        if (hinglishWords.any { lower.contains(it) }) return "Hinglish"

        return "English"
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.release()
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (_: Exception) {}
    }
}
