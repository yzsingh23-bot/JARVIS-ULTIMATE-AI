package com.jarvis.ultimate

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.app.AlertDialog
import android.widget.EditText
import android.text.InputType
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null
    private var speechReady = false
    private var sarvamApiKey: String? = null
    private val chatHistory = mutableListOf<JSONObject>()
    private var pendingQuestion: String? = null
    private val chatLog = mutableStateListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        }
        setContent {
            JarvisScreen(
                onListen = { startListening() },
                onCommand = { command -> handleCommand(command) },
                onAlwaysOn = { startAlwaysOnService() },
                chatMessages = chatLog.toList()
            )
        }
        handleWakeIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWakeIntent(intent)
    }

    private fun handleWakeIntent(intent: Intent?) {
        if (intent?.action == WakeWordService.ACTION_WAKE_COMMAND) {
            val command = intent.getStringExtra(WakeWordService.EXTRA_COMMAND)
            if (!command.isNullOrBlank()) handleCommand(command)
            intent.action = null
        }
    }

    private fun startAlwaysOnService() {
        try {
            ContextCompat.startForegroundService(this, Intent(this, WakeWordService::class.java))
            speak("ਜੀ ਸਰ, ਹਮੇਸ਼ਾ ਸੁਣਨ ਵਾਲਾ ਮੋਡ ਚਾਲੂ ਹੋ ਰਿਹਾ ਹੈ।")
        } catch (_: Exception) {
            speak("ਸਰ, ਹਮੇਸ਼ਾ ਸੁਣਨ ਵਾਲਾ ਮੋਡ ਚਾਲੂ ਨਹੀਂ ਹੋ ਸਕਿਆ। ਐਪ ਖੋਲ੍ਹ ਕੇ ਦੁਬਾਰਾ ਕੋਸ਼ਿਸ਼ ਕਰੋ।")
        }
    }

    override fun onInit(status: Int) {
        speechReady = status == TextToSpeech.SUCCESS
        if (speechReady) {
            tts?.language = Locale("pa", "IN")
            // Lower pitch and a slightly slower pace create a deeper, more deliberate AI voice.
            tts?.setPitch(0.70f)
            tts?.setSpeechRate(0.88f)
        }
    }

    private fun startListening() {
        val speech = recognizer ?: run {
            speak("ਇਸ ਫੋਨ ਤੇ ਬੋਲੀ ਪਛਾਣ ਦੀ ਸਹੂਲਤ ਉਪਲਬਧ ਨਹੀਂ ਹੈ।")
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pa-IN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pa-IN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Listening, Sir")
        }
        speech.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) { speak("ਮਾਫ ਕਰਨਾ ਸਰ, ਮੈਂ ਠੀਕ ਤਰ੍ਹਾਂ ਨਹੀਂ ਸੁਣ ਸਕਿਆ। ਕਿਰਪਾ ਕਰਕੇ ਦੁਬਾਰਾ ਬੋਲੋ।") }
            override fun onResults(results: Bundle?) {
                val words = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                words?.firstOrNull()?.let { handleCommand(it) }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        speech.startListening(intent)
    }

    private fun handleCommand(raw: String) {
        val command = raw.trim()
        if (command.isNotBlank()) chatLog.add("You: $command")
        val lower = command.lowercase(Locale.getDefault())
        when {
            lower.contains("joke") || lower.contains("ਚੁਟਕਲਾ") || lower.contains("ਮਜ਼ਾਕ") -> tellJoke()
            lower.contains("time") || lower.contains("ਸਮਾਂ") -> speak("ਹੁਣ ਸਮਾਂ ਹੈ " + SimpleDateFormat("h:mm a", Locale("pa", "IN")).format(Date()))
            lower.contains("date") || lower.contains("day") || lower.contains("ਤਾਰੀਖ") -> speak("ਅੱਜ ਦੀ ਤਾਰੀਖ ਹੈ " + SimpleDateFormat("EEEE, d MMMM yyyy", Locale("pa", "IN")).format(Date()))
            lower.contains("open whatsapp") -> openApp("com.whatsapp", "WhatsApp")
            lower.contains("open youtube") -> openApp("com.google.android.youtube", "YouTube")
            lower.contains("open chrome") -> openApp("com.android.chrome", "Chrome")
            lower.contains("open gmail") -> openApp("com.google.android.gm", "Gmail")
            lower.contains("camera") || lower.contains("ਕੈਮਰਾ") -> openCamera()
            lower.contains("open settings") -> launch(Intent(android.provider.Settings.ACTION_SETTINGS), "Settings")
            lower.startsWith("search ") || lower.startsWith("google ") -> {
                val query = command.substringAfter(" ").trim()
                openInChrome("https://www.google.com/search?q=" + Uri.encode(query), "Google Search")
            }
            lower.startsWith("open ") || lower.startsWith("visit ") || lower.startsWith("go to ") || lower.startsWith("browse ") -> {
                val target = when {
                    lower.startsWith("open ") -> command.substringAfter(" ").trim()
                    lower.startsWith("visit ") -> command.substringAfter(" ").trim()
                    lower.startsWith("go to ") -> command.substringAfter("go to ").trim()
                    else -> command.substringAfter("browse ").trim()
                }
                if (lower.startsWith("open ")) {
                    if (!openInstalledApp(target)) speak("ਸਰ, $target ਨਾਮ ਦੀ ਐਪ ਇਸ ਫੋਨ ਵਿੱਚ ਨਹੀਂ ਮਿਲੀ।")
                } else openWebsite(target)
            }
            lower.startsWith("navigate to ") || lower.startsWith("maps to ") -> {
                val destination = command.substringAfter("to ", command)
                launch(Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(destination))), "Maps")
            }
            lower == "hello" || lower.contains("hello jarvis") || lower.contains("ਸਤ ਸ੍ਰੀ ਅਕਾਲ") -> speak("ਸਤ ਸ੍ਰੀ ਅਕਾਲ ਸਰ। ਮੈਂ ਤੁਹਾਡੀ ਮਦਦ ਲਈ ਤਿਆਰ ਹਾਂ।")
            else -> askSarvam(command)
        }
    }

    private fun askSarvam(question: String) {
        val key = sarvamApiKey
        if (key.isNullOrBlank()) {
            pendingQuestion = question
            val input = EditText(this).apply { hint = "Sarvam API key (starts with sk_)"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
            AlertDialog.Builder(this).setTitle("Connect JARVIS AI")
                .setMessage("Enter your Sarvam API key. It stays in memory for this app session and is not embedded in the APK.")
                .setView(input).setPositiveButton("Connect") { _, _ ->
                    sarvamApiKey = input.text.toString().trim()
                    val saved = pendingQuestion; pendingQuestion = null
                    if (!sarvamApiKey.isNullOrBlank() && saved != null) askSarvam(saved)
                    else speak("ਕਿਰਪਾ ਕਰਕੇ ਵੈਧ ਸਰਵਮ API ਕੁੰਜੀ ਦਿਓ, ਸਰ।")
                }.setNegativeButton("Cancel", null).show()
            return
        }
        Thread {
            try {
                val messages = JSONArray()
                messages.put(JSONObject().put("role", "system").put("content", "You are JARVIS, a helpful voice assistant. Always answer in Punjabi (Gurmukhi script), concise and natural. Address the user respectfully as Sir (ਸਰ). Do not tell the user to search Google unless they explicitly ask for a web search."))
                synchronized(chatHistory) { chatHistory.takeLast(6).forEach { messages.put(it) }; messages.put(JSONObject().put("role", "user").put("content", question)) }
                val payload = JSONObject().put("model", "sarvam-105b-conversations").put("messages", messages).put("max_tokens", 350).put("temperature", 0.3)
                val conn = (URL("https://api.sarvam.ai/v1/chat/completions").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"; connectTimeout = 8000; readTimeout = 30000; doOutput = true
                    setRequestProperty("Content-Type", "application/json"); setRequestProperty("api-subscription-key", key)
                }
                conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode; val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val body = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }; conn.disconnect()
                if (code !in 200..299) throw IllegalStateException("Sarvam API error $code")
                val answer = JSONObject(body).getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content").trim()
                if (answer.isBlank()) throw IllegalStateException("Empty AI response")
                synchronized(chatHistory) { chatHistory.add(JSONObject().put("role", "user").put("content", question)); chatHistory.add(JSONObject().put("role", "assistant").put("content", answer)); while (chatHistory.size > 24) chatHistory.removeAt(0) }
                runOnUiThread { speak(answer) }
            } catch (_: Exception) { runOnUiThread { speak(offlineFallback(question)) } }
        }.start()
    }

    // Local fallback keeps basic assistance available when internet or Sarvam is unavailable.
    private fun offlineFallback(question: String): String {
        val q = question.lowercase(Locale.ROOT)
        return when {
            q.contains("who are you") || q.contains("ਤੇਰਾ ਨਾਮ") || q.contains("ਤੁਸੀਂ ਕੌਣ") ->
                "ਮੈਂ ਜਾਰਵਿਸ ਹਾਂ, ਤੁਹਾਡਾ ਨਿੱਜੀ ਸਹਾਇਕ।"
            q.contains("what can you do") || q.contains("ਕੀ ਕਰ ਸਕਦੇ") ->
                "ਸਰ, ਮੈਂ ਸਮਾਂ ਅਤੇ ਤਾਰੀਖ ਦੱਸ ਸਕਦਾ ਹਾਂ, ਕੈਮਰਾ, ਸੈਟਿੰਗਾਂ ਅਤੇ ਕੁਝ ਐਪਾਂ ਖੋਲ੍ਹ ਸਕਦਾ ਹਾਂ। ਆਨਲਾਈਨ ਸਵਾਲਾਂ ਲਈ ਇੰਟਰਨੈੱਟ ਅਤੇ ਸਰਵਮ ਕੁੰਜੀ ਚਾਹੀਦੀ ਹੈ।"
            q.contains("thank") || q.contains("ਧੰਨਵਾਦ") ->
                "ਤੁਹਾਡਾ ਸਵਾਗਤ ਹੈ ਸਰ।"
            q.contains("hello") || q.contains("ਸਤ ਸ੍ਰੀ ਅਕਾਲ") ->
                "ਸਤ ਸ੍ਰੀ ਅਕਾਲ ਸਰ। ਮੈਂ ਆਫਲਾਈਨ ਮੋਡ ਵਿੱਚ ਵੀ ਤਿਆਰ ਹਾਂ।"
            else ->
                "ਸਰ, ਇਸ ਸਵਾਲ ਦਾ ਪੂਰਾ ਜਵਾਬ ਦੇਣ ਲਈ ਇੰਟਰਨੈੱਟ ਅਤੇ ਸਰਵਮ AI ਦੀ ਲੋੜ ਹੈ। ਪਰ ਸਮਾਂ, ਤਾਰੀਖ ਅਤੇ ਫੋਨ ਦੇ ਕਮਾਂਡ ਆਫਲਾਈਨ ਵੀ ਕੰਮ ਕਰਦੇ ਹਨ।"
        }
    }

    private fun openCamera() {
        try {
            startActivity(Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
        } catch (_: Exception) {
            try { startActivity(Intent("android.media.action.IMAGE_CAPTURE")) }
            catch (_: Exception) { speak("ਮਾਫ ਕਰਨਾ ਸਰ, ਕੈਮਰਾ ਨਹੀਂ ਖੁੱਲ੍ਹ ਸਕਿਆ।") }
        }
    }

    private fun openWebsite(rawTarget: String) {
        if (rawTarget.isBlank()) {
            speak("ਸਰ, ਤੁਸੀਂ ਕਿਹੜੀ ਵੈੱਬਸਾਈਟ ਖੋਲ੍ਹਣੀ ਚਾਹੁੰਦੇ ਹੋ?")
            return
        }
        val target = rawTarget.trim()
        val url = if (target.startsWith("https://", true) || target.startsWith("http://", true)) {
            target
        } else if (target.contains(" ") || !target.contains(".")) {
            "https://www.google.com/search?q=" + Uri.encode(target)
        } else {
            "https://" + target
        }
        openInChrome(url, "Website")
    }

    private fun openInChrome(url: String, label: String) {
        val chromeIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            setPackage("com.android.chrome")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(chromeIntent)
        } catch (_: Exception) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {
                speak("ਮਾਫ ਕਰਨਾ ਸਰ, ਮੈਂ $label ਨਹੀਂ ਖੋਲ੍ਹ ਸਕਿਆ।")
            }
        }
    }

    private fun openInstalledApp(appName: String): Boolean {
        val wanted = appName.trim().lowercase(Locale.ROOT)
        val match = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .asSequence()
            .map { app -> app to packageManager.getApplicationLabel(app).toString() }
            .firstOrNull { (_, label) -> label.equals(wanted, ignoreCase = true) || label.lowercase(Locale.ROOT).contains(wanted) }
        if (match != null) {
            val launchIntent = packageManager.getLaunchIntentForPackage(match.first.packageName)
            if (launchIntent != null) {
                try { startActivity(launchIntent); speak("ਜੀ ਸਰ, ${match.second} ਖੋਲ੍ਹ ਰਿਹਾ ਹਾਂ।"); return true }
                catch (_: Exception) { }
            }
        }
        return false
    }

    private fun tellJoke() {
        val jokes = listOf(
            "ਸਰ, ਕੰਪਿਊਟਰ ਡਾਕਟਰ ਕੋਲ ਕਿਉਂ ਗਿਆ? ਕਿਉਂਕਿ ਉਸਨੂੰ ਵਾਇਰਸ ਹੋ ਗਿਆ ਸੀ!",
            "ਸਰ, Wi-Fi ਉਦਾਸ ਕਿਉਂ ਸੀ? ਕਿਉਂਕਿ ਉਸਦਾ ਕੋਈ ਕਨੈਕਸ਼ਨ ਨਹੀਂ ਸੀ!",
            "ਸਰ, ਕਿਤਾਬ ਨੇ ਦੂਜੀ ਕਿਤਾਬ ਨੂੰ ਕੀ ਕਿਹਾ? ਤੂੰ ਬਹੁਤ ਕਵਰ ਕਰਦੀ ਏਂ!"
        )
        speak(jokes.random())
    }

    private fun openApp(packageName: String, label: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) startActivity(launchIntent) else speak("ਸਰ, $label ਇਸ ਫੋਨ ਵਿੱਚ ਇੰਸਟਾਲ ਨਹੀਂ ਹੈ।")
    }

    private fun launch(intent: Intent, label: String) {
        try { startActivity(intent) } catch (_: Exception) { speak("ਮਾਫ ਕਰਨਾ ਸਰ, ਮੈਂ $label ਨਹੀਂ ਖੋਲ੍ਹ ਸਕਿਆ।") }
    }

    private fun speak(message: String) {
        if (message.isNotBlank() && (chatLog.isEmpty() || !chatLog.last().equals("JARVIS: $message", true))) {
            chatLog.add("JARVIS: $message")
            while (chatLog.size > 30) chatLog.removeAt(0)
        }
        if (speechReady) tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "jarvis_reply")
    }

    override fun onDestroy() {
        recognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

@Composable
private fun JarvisScreen(onListen: () -> Unit, onCommand: (String) -> Unit, onAlwaysOn: () -> Unit, chatMessages: List<String>) {
    val navy = Color(0xFF030B16)
    val panel = Color(0xFF0A1B2C)
    val cyan = Color(0xFF35DFFF)
    var status by remember { mutableStateOf("SYSTEM ONLINE • READY") }
    var messageText by remember { mutableStateOf("") }
    val context = LocalContext.current
    var startAlwaysOnAfterPermission by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        status = if (granted) "MICROPHONE READY" else "MICROPHONE PERMISSION NEEDED"
        if (granted && startAlwaysOnAfterPermission) {
            startAlwaysOnAfterPermission = false
            onAlwaysOn()
        }
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = navy) {
            Column(
                modifier = Modifier.fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color(0xFF0C2940), navy, Color(0xFF01050C))))
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("J.A.R.V.I.S.", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                        Text("YOUR HYBRID AI ASSISTANT", color = cyan, fontSize = 10.sp, letterSpacing = 2.2.sp)
                    }
                    Box(
                        modifier = Modifier.border(1.dp, cyan.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
                            .background(Color(0x3324C9F0), RoundedCornerShape(18.dp)).padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text("● ONLINE", color = Color(0xFF69F5B5), fontSize = 10.sp, letterSpacing = 1.sp)
                    }
                }
                Spacer(Modifier.height(26.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().background(panel.copy(alpha = 0.8f), RoundedCornerShape(18.dp))
                        .border(1.dp, cyan.copy(alpha = 0.35f), RoundedCornerShape(18.dp)).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("SAT SRI AKAL, SIR", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(5.dp))
                        Text("ਮੈਂ ਤੁਹਾਡੀ ਮਦਦ ਲਈ ਤਿਆਰ ਹਾਂ।", color = cyan, fontSize = 14.sp)
                        Text(status, color = Color(0xFF9BB7C9), fontSize = 9.sp, letterSpacing = 1.sp)
                    }
                    Text("◈", color = cyan, fontSize = 38.sp)
                }
                Spacer(Modifier.height(24.dp))
                Text("TAP THE CORE TO SPEAK", color = cyan, fontSize = 11.sp, letterSpacing = 2.sp)
                Spacer(Modifier.height(8.dp))
                ArcReactorButton(onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        status = "LISTENING..."
                        onListen()
                    } else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                })
                Spacer(Modifier.height(8.dp))
                Text("VOICE • PUNJABI • AI", color = Color(0xFF8DAFC4), fontSize = 10.sp, letterSpacing = 2.sp)
                Spacer(Modifier.height(22.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                onAlwaysOn()
                                status = "ALWAYS-ON SERVICE REQUESTED"
                            } else {
                                startAlwaysOnAfterPermission = true
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0B526B))
                    ) { Text("⚡ ALWAYS ON", color = Color.White, fontSize = 10.sp) }
                    Button(
                        onClick = { onCommand("tell me a joke") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF12334A))
                    ) { Text("✦ TELL A JOKE", color = Color.White, fontSize = 10.sp) }
                }
                Spacer(Modifier.height(18.dp))
                Text("QUICK ACCESS", color = cyan, fontSize = 10.sp, letterSpacing = 2.sp)
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickAction("CAMERA", "⌕", Modifier.weight(1f)) { onCommand("open camera") }
                    QuickAction("YOUTUBE", "▶", Modifier.weight(1f)) { onCommand("open youtube") }
                    QuickAction("APPS", "▦", Modifier.weight(1f)) { onCommand("open settings") }
                }
                Spacer(Modifier.height(18.dp))
                Text("CHAT WITH JARVIS", color = cyan, fontSize = 10.sp, letterSpacing = 2.sp, modifier = Modifier.align(Alignment.Start))
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("Type your message", color = cyan) },
                        shape = RoundedCornerShape(16.dp)
                    )
                    Spacer(Modifier.size(8.dp))
                    Button(
                        onClick = { val sent = messageText.trim(); if (sent.isNotEmpty()) { onCommand(sent); messageText = "" } },
                        modifier = Modifier.size(54.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF087D9B)),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                    ) { Icon(Icons.Default.Send, contentDescription = "Send message", tint = Color.White) }
                }
                Spacer(Modifier.height(10.dp))
                Column(
                    modifier = Modifier.fillMaxWidth().background(Color(0x99071624), RoundedCornerShape(14.dp))
                        .border(1.dp, cyan.copy(alpha = 0.18f), RoundedCornerShape(14.dp)).padding(12.dp)
                ) {
                    if (chatMessages.isEmpty()) {
                        Text("Your conversation will appear here.", color = Color(0xFF7895A8), fontSize = 11.sp)
                    } else chatMessages.takeLast(4).forEach { line ->
                        Text(line, color = if (line.startsWith("JARVIS:")) cyan else Color.White, fontSize = 11.sp, maxLines = 2)
                        Spacer(Modifier.height(4.dp))
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text("TRY SAYING  •  “OPEN CAMERA”  •  “TELL ME A JOKE”  •  “OPEN WHATSAPP”",
                    color = Color(0xFF7999AD), fontSize = 9.sp, textAlign = TextAlign.Center, letterSpacing = 0.5.sp)
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}

@Composable
private fun ArcReactorButton(onClick: () -> Unit) {
    val cyan = Color(0xFF38DFFF)
    Box(
        modifier = Modifier.size(224.dp).clickable(onClick = onClick)
            .background(Brush.radialGradient(listOf(Color(0xFF164D68), Color(0xFF071827), Color(0xFF020914))), CircleShape)
            .border(2.dp, cyan.copy(alpha = 0.9f), CircleShape).padding(9.dp)
            .border(2.dp, cyan.copy(alpha = 0.45f), CircleShape).padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension * 0.43f
            drawCircle(cyan.copy(alpha = 0.9f), radius = radius, center = c, style = Stroke(width = 3.dp.toPx()))
            drawCircle(cyan.copy(alpha = 0.55f), radius = radius * 0.78f, center = c, style = Stroke(width = 2.dp.toPx()))
            drawCircle(Color(0xFFB8F8FF).copy(alpha = 0.85f), radius = radius * 0.48f, center = c, style = Stroke(width = 2.dp.toPx()))
            for (i in 0 until 12) {
                val angle = Math.toRadians((i * 30.0) - 90.0)
                val inner = radius * 0.84f
                val outer = radius * 1.02f
                drawLine(
                    color = cyan.copy(alpha = if (i % 3 == 0) 1f else 0.65f),
                    start = Offset(c.x + cos(angle).toFloat() * inner, c.y + sin(angle).toFloat() * inner),
                    end = Offset(c.x + cos(angle).toFloat() * outer, c.y + sin(angle).toFloat() * outer),
                    strokeWidth = if (i % 3 == 0) 5.dp.toPx() else 3.dp.toPx()
                )
            }
            drawCircle(Color(0xFF6DEBFF).copy(alpha = 0.18f), radius = radius * 0.39f, center = c)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Mic, contentDescription = "Talk to JARVIS", tint = Color.White, modifier = Modifier.size(38.dp))
            Spacer(Modifier.height(7.dp))
            Text("TAP TO", color = cyan, fontSize = 10.sp, letterSpacing = 2.sp)
            Text("JARVIS", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        }
    }
}

@Composable
private fun QuickAction(label: String, symbol: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier.border(1.dp, Color(0xFF2CBFDA).copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .background(Color(0xAA0B2032), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(vertical = 13.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(symbol, color = Color(0xFF42E4FF), fontSize = 24.sp)
        Text(label, color = Color.White, fontSize = 9.sp, letterSpacing = 1.sp)
    }
}
