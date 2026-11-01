package com.jarvis.ultimate

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.RecognizerIntent.EXTRA_PREFER_OFFLINE
import android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
import android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL
import android.speech.RecognizerIntent.EXTRA_LANGUAGE
import android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS
import android.speech.RecognizerIntent.EXTRA_MAX_RESULTS
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.Locale

/**
 * User-started foreground microphone service. It continuously restarts short
 * recognition sessions and waits for "Hey Jarvis" (or Punjabi "ਹੇ ਜਾਰਵਿਸ").
 * Android displays an ongoing notification with a Stop action while listening.
 */
class WakeWordService : Service() {
    private var recognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var awaitingCommand = false
    private var isListening = false
    private var stopped = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopped = true
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        stopped = false
        if (recognizer == null) {
            recognizer = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
                } else {
                    SpeechRecognizer.createSpeechRecognizer(this)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Could not create speech recognizer", e)
                null
            }
        }
        scheduleListening(250)
        return START_STICKY
    }

    private fun scheduleListening(delayMs: Long = 700) {
        if (stopped) return
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ startRecognition() }, delayMs)
    }

    private fun startRecognition() {
        val speech = recognizer ?: return
        if (stopped || isListening) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(EXTRA_LANGUAGE_MODEL, LANGUAGE_MODEL_FREE_FORM)
            putExtra(EXTRA_LANGUAGE, "pa-IN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pa-IN")
            putExtra(EXTRA_PREFER_OFFLINE, true)
            putExtra(EXTRA_PARTIAL_RESULTS, true)
            putExtra(EXTRA_MAX_RESULTS, 3)
        }
        try {
            speech.setRecognitionListener(listener)
            isListening = true
            speech.startListening(intent)
        } catch (e: Exception) {
            isListening = false
            Log.w(TAG, "Recognition start failed", e)
            scheduleListening(1500)
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() { isListening = false }
        override fun onError(error: Int) {
            isListening = false
            scheduleListening(if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1800 else 650)
        }
        override fun onResults(results: Bundle?) {
            isListening = false
            val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull().orEmpty().trim()
            processRecognizedSpeech(heard)
            scheduleListening(800)
        }
        override fun onPartialResults(partialResults: Bundle?) {
            val heard = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull().orEmpty()
            if (containsWakePhrase(heard)) {
                // Wait for the complete utterance; onResults will dispatch it.
            }
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun processRecognizedSpeech(heard: String) {
        if (heard.isBlank()) return
        val lower = heard.lowercase(Locale.ROOT)
        val wake = containsWakePhrase(lower)
        if (wake) {
            val command = lower
                .replace("hey jarvis", "")
                .replace("hello jarvis", "")
                .replace("ਹੇ ਜਾਰਵਿਸ", "")
                .replace("ਜਾਰਵਿਸ", "")
                .trim(' ', ',', '.', '!', '?')
            if (command.isNotBlank()) {
                dispatchCommand(command)
            } else {
                awaitingCommand = true
                // A brief voice acknowledgement; recognition resumes after it.
                try {
                    val tts = android.speech.tts.TextToSpeech(this) { status ->
                        if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                            ttsRef?.language = Locale("pa", "IN")
                            ttsRef?.speak("ਹਾਂ ਜੀ ਸਰ, ਦੱਸੋ।", android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "wake_ack")
                        }
                    }
                    ttsRef = tts
                } catch (_: Exception) { }
            }
        } else if (awaitingCommand) {
            awaitingCommand = false
            dispatchCommand(heard)
        }
    }

    private var ttsRef: android.speech.tts.TextToSpeech? = null

    private fun containsWakePhrase(text: String): Boolean {
        val value = text.lowercase(Locale.ROOT)
        return value.contains("hey jarvis") || value.contains("hello jarvis") ||
            value.contains("ਹੇ ਜਾਰਵਿਸ") || value.contains("ਜਾਰਵਿਸ")
    }

    private fun dispatchCommand(command: String) {
        val activity = Intent(this, MainActivity::class.java).apply {
            action = ACTION_WAKE_COMMAND
            putExtra(EXTRA_COMMAND, command)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        try {
            startActivity(activity)
        } catch (e: Exception) {
            Log.w(TAG, "Could not bring JARVIS to foreground for command", e)
            val manager = getSystemService(NotificationManager::class.java)
            val openIntent = PendingIntent.getActivity(
                this, 2, activity, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            manager.notify(NOTIFICATION_ID + 1, NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("JARVIS heard you")
                .setContentText(command)
                .setContentIntent(openIntent)
                .setAutoCancel(true)
                .build())
        }
    }

    private fun buildNotification(): Notification {
        val stop = PendingIntent.getService(
            this, 1, Intent(this, WakeWordService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("JARVIS is listening")
            .setContentText("Say “Hey Jarvis”. Tap Stop to turn off the microphone.")
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_media_pause, "Stop listening", stop)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "JARVIS always-on microphone", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopped = true
        handler.removeCallbacksAndMessages(null)
        try { recognizer?.cancel(); recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
        try { ttsRef?.stop(); ttsRef?.shutdown() } catch (_: Exception) {}
        ttsRef = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "com.jarvis.ultimate.STOP_WAKE_SERVICE"
        const val ACTION_WAKE_COMMAND = "com.jarvis.ultimate.WAKE_COMMAND"
        const val EXTRA_COMMAND = "wake_command"
        private const val TAG = "JarvisWakeWord"
        private const val CHANNEL_ID = "jarvis_always_on"
        private const val NOTIFICATION_ID = 7301
    }
}
