package com.danielportelabyrne.cinema.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.danielportelabyrne.cinema.data.search.VoiceState

/**
 * Voice search with the system's speech recogniser. Asks for the microphone the first time, then listens with live
 * partial results and loudness, reporting each [VoiceState] to [onState]. Without a recogniser or the permission it
 * reports Unavailable with the reason, never fails silently.
 */
class VoiceSearch(private val activity: Activity, private val onState: (VoiceState) -> Unit) {
    var state: VoiceState = VoiceState.Idle
        private set
    private var recognizer: SpeechRecognizer? = null

    private fun move(event: VoiceState.Event) {
        val next = state.next(event)
        if (next == state) return
        state = next
        onState(next)
        if (next is VoiceState.Listening && recognizer == null) listen()
    }

    fun start() {
        if (state is VoiceState.Listening || state is VoiceState.Requesting) return
        state = VoiceState.Idle
        val permitted = activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        move(VoiceState.Event.Start(SpeechRecognizer.isRecognitionAvailable(activity), permitted))
        if (state is VoiceState.Requesting) activity.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), PERMISSION_REQUEST)
    }

    /** Forwarded from the activity's permission result. */
    fun onPermission(granted: Boolean) = move(VoiceState.Event.Permission(granted))

    /** The remote's mic button was let go: finish with what was heard. */
    fun stop() { recognizer?.stopListening() }

    fun cancel() {
        recognizer?.cancel(); recognizer?.destroy(); recognizer = null
        move(VoiceState.Event.Cancel)
    }

    private fun listen() {
        val r = SpeechRecognizer.createSpeechRecognizer(activity)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) = move(VoiceState.Event.Rms(rmsdB))
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) { finish(); move(VoiceState.Event.Error(error)) }
            override fun onResults(results: Bundle?) { val text = best(results); finish(); move(VoiceState.Event.Final(text)) }
            override fun onPartialResults(partialResults: Bundle?) = move(VoiceState.Event.Partial(best(partialResults)))
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        })
    }

    private fun finish() { recognizer?.destroy(); recognizer = null }

    private fun best(bundle: Bundle?): String = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()

    companion object { const val PERMISSION_REQUEST = 4711 }
}
