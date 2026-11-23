package com.example.util

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

private const val VOICE_TAG = "VoiceEngine"

data class VoiceOption(
    val name: String,
    val localeTag: String,
    val displayLabel: String
)

data class VoiceUiState(
    val isListening: Boolean = false,
    val isSpeaking: Boolean = false,
    val partialTranscript: String = "",
    val rmsLevel: Float = 0f,
    val ttsReady: Boolean = false,
    val speechRecognizerAvailable: Boolean = false,
    val availableVoices: List<VoiceOption> = emptyList(),
    val errorMessage: String? = null
)

class VoiceEngine(private val context: Context) {

    private val _state = MutableStateFlow(
        VoiceUiState(
            speechRecognizerAvailable = SpeechRecognizer.isRecognitionAvailable(context)
        )
    )
    val state: StateFlow<VoiceUiState> = _state.asStateFlow()

    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var currentSpeechRate: Float = 1.0f
    private var currentPitch: Float = 1.0f
    private var currentVoiceName: String? = null
    private var onSpeechCompleteCallback: (() -> Unit)? = null

    init {
        initTts()
    }

    private fun initTts() {
        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val engine = tts ?: return@TextToSpeech
                    engine.language = Locale.getDefault()
                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _state.value = _state.value.copy(isSpeaking = true)
                        }

                        override fun onDone(utteranceId: String?) {
                            _state.value = _state.value.copy(isSpeaking = false)
                            onSpeechCompleteCallback?.invoke()
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            _state.value = _state.value.copy(isSpeaking = false)
                        }
                    })

                    val voices = try {
                        engine.voices.orEmpty()
                            .filter { !it.isNetworkConnectionRequired }
                            .sortedBy { it.locale.displayName }
                            .take(24)
                            .map { voice ->
                                VoiceOption(
                                    name = voice.name,
                                    localeTag = voice.locale.toLanguageTag(),
                                    displayLabel = "${voice.locale.displayLanguage} (${voice.locale.country.ifBlank { voice.locale.language.uppercase() }}) · ${voice.name.takeLast(14)}"
                                )
                            }
                    } catch (_: Exception) {
                        emptyList()
                    }
                    _state.value = _state.value.copy(
                        ttsReady = true,
                        availableVoices = voices
                    )
                    applyVoiceSettings(currentVoiceName, currentSpeechRate, currentPitch)
                } else {
                    Log.w(VOICE_TAG, "TTS initialization failed with status $status")
                }
            }
        } catch (e: Exception) {
            Log.e(VOICE_TAG, "Failed to create TextToSpeech", e)
        }
    }

    fun applyVoiceSettings(voiceName: String?, rate: Float, pitch: Float) {
        currentVoiceName = voiceName
        currentSpeechRate = rate.coerceIn(0.5f, 2.0f)
        currentPitch = pitch.coerceIn(0.5f, 2.0f)
        val engine = tts ?: return
        try {
            engine.setSpeechRate(currentSpeechRate)
            engine.setPitch(currentPitch)
            if (!voiceName.isNullOrBlank()) {
                val matched = engine.voices?.firstOrNull { it.name == voiceName }
                if (matched != null) {
                    engine.voice = matched
                }
            }
        } catch (e: Exception) {
            Log.w(VOICE_TAG, "Unable to apply voice settings: ${e.message}")
        }
    }

    fun speak(text: String, onComplete: (() -> Unit)? = null) {
        val engine = tts
        if (engine == null || !_state.value.ttsReady) {
            _state.value = _state.value.copy(errorMessage = "Text-to-speech engine is not ready on this device.")
            return
        }
        onSpeechCompleteCallback = onComplete
        // Strip markdown syntax for natural speech output
        val cleanText = text
            .replace(Regex("```[\\s\\S]*?```"), " Code block omitted. ")
            .replace(Regex("[#*_`>~]"), "")
            .replace(Regex("\\[([^\\]]+)]\\([^)]+\\)"), "$1")
            .take(3800)

        engine.stop()
        engine.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }

    fun stopSpeaking() {
        onSpeechCompleteCallback = null
        tts?.stop()
        _state.value = _state.value.copy(isSpeaking = false)
    }

    fun startListening(
        languageTag: String? = null,
        onFinalResult: (String) -> Unit,
        onError: (String) -> Unit = {}
    ) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            val msg = "Speech recognition service is not available on this device."
            _state.value = _state.value.copy(errorMessage = msg)
            onError(msg)
            return
        }
        stopSpeaking()
        stopListening()

        try {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer = recognizer
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _state.value = _state.value.copy(
                        isListening = true,
                        partialTranscript = "",
                        errorMessage = null
                    )
                }

                override fun onBeginningOfSpeech() = Unit

                override fun onRmsChanged(rmsdB: Float) {
                    val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                    _state.value = _state.value.copy(rmsLevel = normalized)
                }

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() {
                    _state.value = _state.value.copy(isListening = false, rmsLevel = 0f)
                }

                override fun onError(error: Int) {
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network error during speech recognition."
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected. Tap the mic and try again."
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Listening timed out."
                        else -> "Speech recognition error ($error)."
                    }
                    _state.value = _state.value.copy(isListening = false, rmsLevel = 0f, errorMessage = msg)
                    onError(msg)
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val transcript = matches?.firstOrNull()?.trim().orEmpty()
                    _state.value = _state.value.copy(
                        isListening = false,
                        partialTranscript = transcript,
                        rmsLevel = 0f
                    )
                    if (transcript.isNotEmpty()) {
                        onFinalResult(transcript)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    if (partial.isNotBlank()) {
                        _state.value = _state.value.copy(partialTranscript = partial)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                if (!languageTag.isNullOrBlank()) {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                } else {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                }
            }
            recognizer.startListening(intent)
        } catch (e: Exception) {
            Log.e(VOICE_TAG, "Failed to start speech recognition", e)
            val msg = e.localizedMessage ?: "Unable to start microphone."
            _state.value = _state.value.copy(isListening = false, errorMessage = msg)
            onError(msg)
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (_: Exception) {
            // Ignore cleanup exceptions
        }
        speechRecognizer = null
        _state.value = _state.value.copy(isListening = false, rmsLevel = 0f)
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    fun release() {
        stopListening()
        stopSpeaking()
        tts?.shutdown()
        tts = null
    }
}
