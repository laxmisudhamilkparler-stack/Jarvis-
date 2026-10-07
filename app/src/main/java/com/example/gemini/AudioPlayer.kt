package com.example.gemini

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.speech.tts.TextToSpeech
import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.abs

class AudioPlayer(private val context: Context) : TextToSpeech.OnInitListener {

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private var audioTrack: AudioTrack? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private var playbackJob: Job? = null
    private val pcmQueue = ConcurrentLinkedQueue<ByteArray>()
    private var isPlayingPcm = false

    // TTS fallback for graceful response if Gemini API key is missing
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false

    init {
        try {
            textToSpeech = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsReady = true
            textToSpeech?.language = Locale.forLanguageTag("hi-IN")
        }
    }

    @Synchronized
    private fun getOrCreateAudioTrack(sampleRate: Int = 24000): AudioTrack {
        val current = audioTrack
        if (current != null && current.state == AudioTrack.STATE_INITIALIZED) {
            return current
        }

        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = (minBufferSize * 4).coerceAtLeast(8192)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        track.play()
        audioTrack = track
        return track
    }

    fun playBase64Pcm(base64Data: String, sampleRate: Int = 24000) {
        try {
            val bytes = Base64.decode(base64Data, Base64.DEFAULT)
            enqueuePcm(bytes, sampleRate)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun enqueuePcm(pcmBytes: ByteArray, sampleRate: Int = 24000) {
        pcmQueue.offer(pcmBytes)
        if (!isPlayingPcm) {
            startPlaybackLoop(sampleRate)
        }
    }

    private fun startPlaybackLoop(sampleRate: Int) {
        isPlayingPcm = true
        _isPlaying.value = true

        playbackJob = scope.launch {
            try {
                val track = getOrCreateAudioTrack(sampleRate)
                if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                    track.play()
                }

                while (isPlayingPcm) {
                    val chunk = pcmQueue.poll()
                    if (chunk != null) {
                        calculateAndEmitAmplitude(chunk)
                        track.write(chunk, 0, chunk.size)
                    } else {
                        // Queue empty, wait briefly before concluding
                        kotlinx.coroutines.delay(100)
                        if (pcmQueue.isEmpty()) {
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isPlayingPcm = false
                _isPlaying.value = false
                _amplitude.value = 0f
            }
        }
    }

    private fun calculateAndEmitAmplitude(pcmBytes: ByteArray) {
        if (pcmBytes.size < 2) return
        var maxAmp = 0
        val buffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
        while (buffer.remaining() >= 2) {
            val sample = buffer.short
            val absSample = abs(sample.toInt())
            if (absSample > maxAmp) {
                maxAmp = absSample
            }
        }
        val normalized = (maxAmp / 32767f).coerceIn(0f, 1f)
        _amplitude.value = normalized
    }

    fun speakFallback(text: String, languageCode: String? = null) {
        stop()
        if (isTtsReady && textToSpeech != null) {
            _isPlaying.value = true
            val loc = when (languageCode?.lowercase()) {
                "hi", "hin", "hindi" -> Locale.forLanguageTag("hi-IN")
                "mr", "marathi" -> Locale.forLanguageTag("mr-IN")
                "bn", "bengali" -> Locale.forLanguageTag("bn-IN")
                "gu", "gujarati" -> Locale.forLanguageTag("gu-IN")
                "ta", "tamil" -> Locale.forLanguageTag("ta-IN")
                "te", "telugu" -> Locale.forLanguageTag("te-IN")
                "kn", "kannada" -> Locale.forLanguageTag("kn-IN")
                "ml", "malayalam" -> Locale.forLanguageTag("ml-IN")
                "pa", "punjabi" -> Locale.forLanguageTag("pa-IN")
                "ur", "urdu" -> Locale.forLanguageTag("ur-IN")
                else -> Locale.forLanguageTag("en-IN")
            }
            try {
                textToSpeech?.language = loc
            } catch (_: Exception) {}

            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarves_speech_id")
            scope.launch {
                // simulate speech amplitude during fallback
                for (i in 0..15) {
                    if (!_isPlaying.value) break
                    _amplitude.value = (0.3f + (i % 5) * 0.12f).coerceIn(0.1f, 0.9f)
                    kotlinx.coroutines.delay(150)
                }
                _amplitude.value = 0f
                _isPlaying.value = false
            }
        }
    }

    fun stop() {
        isPlayingPcm = false
        pcmQueue.clear()
        playbackJob?.cancel()
        playbackJob = null

        try {
            audioTrack?.let {
                if (it.state == AudioTrack.STATE_INITIALIZED) {
                    it.pause()
                    it.flush()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            textToSpeech?.stop()
        } catch (_: Exception) {}

        _isPlaying.value = false
        _amplitude.value = 0f
    }

    fun release() {
        stop()
        try {
            audioTrack?.release()
            audioTrack = null
        } catch (_: Exception) {}

        try {
            textToSpeech?.shutdown()
            textToSpeech = null
        } catch (_: Exception) {}
    }
}
