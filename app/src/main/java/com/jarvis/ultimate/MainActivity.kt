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
import android.provider.AlarmClock
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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

class MainActivity : ComponentActivity() {
    private var recognizer: SpeechRecognizer? = null
    private var sarvamApiKey: String? = null
    private val chatHistory = mutableListOf<JSONObject>()
    private var pendingQuestion: String? = null
    private val chatLog = mutableStateListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        }
        loadChatLog()
        setContent {
            JarvisScreen(
                onListen = { language -> startListening(language) },
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
        when (intent?.action) {
            WakeWordService.ACTION_WAKE_COMMAND -> {
                val command = intent.getStringExtra(WakeWordService.EXTRA_COMMAND)
                if (!command.isNullOrBlank()) handleCommand(command)
                intent.action = null
            }
            ACTION_WIDGET_TALK -> {
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    startListening("en-IN")
                } else {
                    addReply("Tap the Arc Reactor button once to grant microphone permission, then use the home-screen widget.")
                }
                intent.action = null
            }
        }
    }

    private fun startAlwaysOnService() {
        try {
            ContextCompat.startForegroundService(this, Intent(this, WakeWordService::class.java))
            addReply("Always-on listening started. Say Hey Jarvis.")
        } catch (_: Exception) {
            addReply("Could not start always-on listening. Please try again.")
        }
    }

    private fun startListening(language: String = "en-IN") {
        val speech = recognizer ?: run {
            addReply("Speech recognition is not available on this phone.")
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Listening, Sir")
        }
        speech.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) { addReply("I couldn't hear that clearly, Sir. Please try again.") }
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
        if (command.isNotBlank()) {
            chatLog.add("You: $command")
            persistChatLog()
        }
        val lower = command.lowercase(Locale.getDefault())
        when {
            lower.startsWith("send whatsapp") || lower.startsWith("send message") -> confirmWhatsAppSend(command)
            lower.startsWith("remind me") || lower.startsWith("set reminder") -> setReminder(command)
            lower.contains("joke") || lower.contains("ਚੁਟਕਲਾ") || lower.contains("ਮਜ਼ਾਕ") -> tellJoke()
            lower.contains("time") || lower.contains("ਸਮਾਂ") -> addReply("The time is " + SimpleDateFormat("h:mm a", Locale("en", "IN")).format(Date()))
            lower.contains("date") || lower.contains("day") || lower.contains("ਤਾਰੀਖ") -> addReply("Today is " + SimpleDateFormat("EEEE, d MMMM yyyy", Locale("en", "IN")).format(Date()))
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
                    if (!openInstalledApp(target)) addReply("I could not find an installed app named $target, Sir.")
                } else openWebsite(target)
            }
            lower.startsWith("navigate to ") || lower.startsWith("maps to ") -> {
                val destination = command.substringAfter("to ", command)
                launch(Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(destination))), "Maps")
            }
            lower == "hello" || lower.contains("hello jarvis") || lower.contains("ਸਤ ਸ੍ਰੀ ਅਕਾਲ") -> addReply("Good day, Sir. JARVIS is ready to assist.")
            else -> askSarvam(command)
        }
    }

    private fun confirmWhatsAppSend(command: String) {
        val match = Regex("(?i)^send (?:whatsapp )?message to (.+?) (?:saying|message) (.+)$").find(command)
            ?: Regex("(?i)^send whatsapp to (.+?) message (.+)$").find(command)
        if (match == null) {
            addReply("Use: send WhatsApp message to [contact name] saying [message]. I will ask you to confirm before opening WhatsApp.")
            return
        }
        val contact = match.groupValues[1].trim()
        val message = match.groupValues[2].trim()
        AlertDialog.Builder(this)
            .setTitle("Confirm WhatsApp draft")
            .setMessage("""Prepare this message for $contact?

$message

JARVIS will open WhatsApp. You must choose the recipient and press Send yourself.""")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Continue") { _, _ ->
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message)
                    setPackage("com.whatsapp")
                }
                try {
                    startActivity(share)
                    addReply("WhatsApp opened with your draft. Please verify the recipient and press Send yourself.")
                } catch (_: Exception) {
                    addReply("WhatsApp is not installed or could not be opened.")
                }
            }
            .show()
    }

    private fun setReminder(command: String) {
        val title = command
            .replace(Regex("(?i)^remind me( to)?"), "")
            .replace(Regex("(?i)^set reminder( for)?"), "")
            .replace(Regex("(?i)\\s+in\\s+\\d+\\s+(minutes?|hours?)"), "")
            .replace(Regex("(?i)\\s+at\\s+\\d{1,2}(:\\d{2})?\\s*(am|pm)?"), "")
            .trim()
            .ifBlank { "JARVIS reminder" }
        val relative = Regex("(?i)\\bin\\s+(\\d+)\\s+(minutes?|hours?)").find(command)
        val absolute = Regex("(?i)\\bat\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?").find(command)
        val time = when {
            relative != null -> {
                val amount = relative.groupValues[1].toLong()
                if (relative.groupValues[2].startsWith("hour", true)) java.time.LocalTime.now().plusHours(amount)
                else java.time.LocalTime.now().plusMinutes(amount)
            }
            absolute != null -> {
                var hour = absolute.groupValues[1].toInt().coerceIn(0, 23)
                val minute = absolute.groupValues[2].ifBlank { "0" }.toInt().coerceIn(0, 59)
                val meridiem = absolute.groupValues[3]
                if (meridiem.equals("pm", true) && hour in 1..11) hour += 12
                if (meridiem.equals("am", true) && hour == 12) hour = 0
                java.time.LocalTime.of(hour, minute)
            }
            else -> null
        }
        if (time == null) {
            addReply("To set a reminder, say: remind me to study in 10 minutes, or remind me to call home at 7:30 PM.")
            return
        }
        val alarm = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, time.hour)
            putExtra(AlarmClock.EXTRA_MINUTES, time.minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, title)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        }
        try {
            startActivity(alarm)
            addReply("I opened the Clock app to confirm your reminder: $title at " +
                time.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)) + ".")
        } catch (_: Exception) {
            addReply("The Clock app is not available, so I could not create that reminder.")
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
                    else if (saved != null) addReply(offlineFallback(saved))
                    else addReply("Please enter a valid Sarvam API key, Sir.")
                }.setNeutralButton("Use Offline") { _, _ ->
                    val saved = pendingQuestion; pendingQuestion = null
                    if (saved != null) addReply(offlineFallback(saved))
                }.setNegativeButton("Cancel", null).show()
            return
        }
        Thread {
            try {
                val messages = JSONArray()
                val replyLanguage = when {
                    question.any { it in '\u0A00'..'\u0A7F' } -> "Punjabi (Gurmukhi)"
                    question.any { it in '\u0900'..'\u097F' } -> "Hindi (Devanagari)"
                    else -> "English"
                }
                messages.put(JSONObject().put("role", "system").put("content", "You are JARVIS, a helpful hybrid personal assistant. Reply in the same language the user uses; English is the default when unclear. Address the user respectfully as Sir. Keep answers concise unless detail is requested. You are a patient study tutor: explain school subjects step by step, solve problems with working, create notes, flashcards, quizzes and revision plans. You are a Free Fire India guide: advise on fair-play sensitivity, HUD, aim practice, weapons, characters, maps, rotations, survival and rank push. Never recommend cheats, hacks, config abuse or account theft. Do not claim live updates or current meta without verification. Do not redirect users to Google unless they explicitly ask. Detected language: " + replyLanguage + "."))
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
                runOnUiThread { addReply(answer) }
            } catch (_: Exception) { runOnUiThread { addReply(offlineFallback(question)) } }
        }.start()
    }

    // Local fallback keeps basic assistance available when internet or Sarvam is unavailable.
    private fun offlineFallback(question: String): String {
        val q = question.lowercase(Locale.ROOT)
        return when {
            q.contains("who are you") -> "I am JARVIS, your personal AI assistant, Sir."
            q.contains("what can you do") -> "OFFLINE MODE: I can open supported apps, use the camera, show time and date, set Clock alarms, prepare WhatsApp drafts with confirmation, tell jokes, and give basic study and Free Fire tips. Connect Sarvam for full conversational AI."
            q.contains("thank") -> "You're welcome, Sir."
            q.contains("hello") || q.contains("hi jarvis") -> "Good day, Sir. JARVIS is ready in offline mode."
            q.contains("joke") -> "Why did the computer visit the doctor? It caught a virus, Sir!"
            q.contains("study") || q.contains("homework") || q.contains("exam") -> "Offline study tip, Sir: choose one small topic, read it for 20 minutes, close your book and write five key points from memory, then test yourself with practice questions. Connect Sarvam for subject-specific explanations."
            q.contains("free fire") || q.contains("freefire") -> "Offline Free Fire tip, Sir: use a comfortable HUD, keep sensitivity consistent, practise drag headshots in the training ground, use cover before healing, and avoid running in open areas. Connect Sarvam for tailored coaching."
            q.contains("math") || q.contains("addition") -> "Offline maths tip, Sir: write the known values first, choose the relevant formula, solve one step at a time, and check your answer by substituting it back. Connect Sarvam for worked solutions."
            else -> "I am in OFFLINE MODE, Sir. Phone commands and basic built-in help remain available, but I cannot generate a full AI answer without Sarvam. Check your internet/API key or choose a supported local command."
        }
    }

    private fun openCamera() {
        try {
            startActivity(Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
        } catch (_: Exception) {
            try { startActivity(Intent("android.media.action.IMAGE_CAPTURE")) }
            catch (_: Exception) { addReply("Sorry, Sir. I could not open the camera.") }
        }
    }

    private fun openWebsite(rawTarget: String) {
        if (rawTarget.isBlank()) {
            addReply("Which website would you like me to open, Sir?")
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
                addReply("Sorry, Sir. I could not open $label.")
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
                try { startActivity(launchIntent); addReply("Opening ${match.second}, Sir."); return true }
                catch (_: Exception) { }
            }
        }
        return false
    }

    private fun tellJoke() {
        val jokes = listOf(
            "Why did the computer visit the doctor? It caught a virus, Sir!",
            "Why was the Wi-Fi feeling lonely? It had no connection, Sir!",
            "Why did the student eat the homework? The teacher said it was a piece of cake, Sir!"
        )
        addReply(jokes.random())
    }

    private fun openApp(packageName: String, label: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            startActivity(launchIntent)
            addReply("Opening $label, Sir.")
        } else addReply("$label is not installed on this phone, Sir.")
    }

    private fun launch(intent: Intent, label: String) {
        try { startActivity(intent) } catch (_: Exception) { addReply("Sorry, Sir. I could not open $label.") }
    }

    // Microphone input with on-screen replies only; no text-to-speech audio.
    private fun addReply(message: String) {
        if (message.isNotBlank()) {
            chatLog.add("JARVIS: $message")
            while (chatLog.size > 30) chatLog.removeAt(0)
            persistChatLog()
        }
    }

    private fun loadChatLog() {
        val raw = getSharedPreferences("jarvis_chat", MODE_PRIVATE).getString("messages", null) ?: return
        try {
            val saved = JSONArray(raw)
            for (i in 0 until saved.length()) chatLog.add(saved.getString(i))
        } catch (_: Exception) { chatLog.clear() }
    }

    private fun persistChatLog() {
        val saved = JSONArray()
        chatLog.takeLast(30).forEach { saved.put(it) }
        getSharedPreferences("jarvis_chat", MODE_PRIVATE).edit().putString("messages", saved.toString()).apply()
    }

    override fun onDestroy() {
        recognizer?.destroy()
        super.onDestroy()
    }

    companion object {
        const val ACTION_WIDGET_TALK = "com.jarvis.ultimate.WIDGET_TALK"
    }
}

@Composable
private fun JarvisScreen(onListen: (String) -> Unit, onCommand: (String) -> Unit, onAlwaysOn: () -> Unit, chatMessages: List<String>) {
    val navy = Color(0xFF030B16)
    val panel = Color(0xFF0A1B2C)
    val cyan = Color(0xFF35DFFF)
    var status by remember { mutableStateOf("SYSTEM ONLINE • READY") }
    var messageText by remember { mutableStateOf("") }
    var voiceLanguage by remember { mutableStateOf("en-IN") }
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
                        Text("● SYSTEM READY", color = Color(0xFF69F5B5), fontSize = 10.sp, letterSpacing = 1.sp)
                    }
                }
                Spacer(Modifier.height(26.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().background(panel.copy(alpha = 0.8f), RoundedCornerShape(18.dp))
                        .border(1.dp, cyan.copy(alpha = 0.35f), RoundedCornerShape(18.dp)).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("GOOD DAY, SIR.", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(5.dp))
                        Text("Your personal AI assistant is ready.", color = cyan, fontSize = 13.sp)
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
                        onListen(voiceLanguage)
                    } else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                })
                Spacer(Modifier.height(8.dp))
                Text("VOICE INPUT LANGUAGE", color = Color(0xFF8DAFC4), fontSize = 9.sp, letterSpacing = 1.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("EN" to "en-IN", "HI" to "hi-IN", "PA" to "pa-IN").forEach { (label, locale) ->
                        Button(
                            onClick = { voiceLanguage = locale },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (voiceLanguage == locale) Color(0xFF087D9B) else Color(0xFF10283A)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text(label, color = Color.White, fontSize = 11.sp) }
                    }
                }
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
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickAction("STUDY HELP", "✎", Modifier.weight(1f)) { onCommand("Be my study tutor. Explain a topic simply, make notes and quiz me.") }
                    QuickAction("FREE FIRE", "🎮", Modifier.weight(1f)) { onCommand("Be my Free Fire India game guide. Help me improve my gameplay and rank.") }
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
    val transition = rememberInfiniteTransition(label = "arc-reactor")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "reactor-rotation"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(1700, easing = LinearEasing), repeatMode = androidx.compose.animation.core.RepeatMode.Reverse),
        label = "reactor-pulse"
    )
    Box(
        modifier = Modifier.size(248.dp).clickable(onClick = onClick)
            .background(Brush.radialGradient(listOf(Color(0xFF1A6685), Color(0xFF0A263B), Color(0xFF020914))), CircleShape)
            .border(2.dp, cyan.copy(alpha = 0.95f), CircleShape).padding(9.dp)
            .border(2.dp, cyan.copy(alpha = 0.42f), CircleShape).padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension * 0.43f * pulse
            drawCircle(cyan.copy(alpha = 0.12f), radius = radius * 1.13f, center = c)
            drawCircle(cyan.copy(alpha = 0.92f), radius = radius, center = c, style = Stroke(width = 3.dp.toPx()))
            drawCircle(cyan.copy(alpha = 0.48f), radius = radius * 0.78f, center = c, style = Stroke(width = 2.dp.toPx()))
            drawCircle(Color(0xFFB8F8FF).copy(alpha = 0.9f), radius = radius * 0.48f, center = c, style = Stroke(width = 2.dp.toPx()))
            drawArc(cyan, rotation, 105f, false,
                topLeft = Offset(c.x - radius * 0.9f, c.y - radius * 0.9f),
                size = androidx.compose.ui.geometry.Size(radius * 1.8f, radius * 1.8f),
                style = Stroke(width = 5.dp.toPx()))
            drawArc(Color(0xFFB8F8FF), -rotation * 0.7f, 70f, false,
                topLeft = Offset(c.x - radius * 0.69f, c.y - radius * 0.69f),
                size = androidx.compose.ui.geometry.Size(radius * 1.38f, radius * 1.38f),
                style = Stroke(width = 3.dp.toPx()))
            for (i in 0 until 24) {
                val angle = Math.toRadians((i * 15.0) + rotation)
                val inner = radius * 0.84f
                val outer = radius * (if (i % 3 == 0) 1.08f else 1.01f)
                drawLine(
                    color = cyan.copy(alpha = if (i % 3 == 0) 1f else 0.55f),
                    start = Offset(c.x + cos(angle).toFloat() * inner, c.y + sin(angle).toFloat() * inner),
                    end = Offset(c.x + cos(angle).toFloat() * outer, c.y + sin(angle).toFloat() * outer),
                    strokeWidth = if (i % 3 == 0) 4.dp.toPx() else 2.dp.toPx()
                )
            }
            // Audio-reactor style radial waveform around the centre.
            for (i in 0 until 48) {
                val angle = Math.toRadians(i * 7.5)
                val wave = if (i % 4 == 0) 1.12f else if (i % 2 == 0) 1.07f else 1.035f
                drawLine(
                    color = Color(0xFF72EFFF).copy(alpha = 0.7f),
                    start = Offset(c.x + cos(angle).toFloat() * radius * 1.1f, c.y + sin(angle).toFloat() * radius * 1.1f),
                    end = Offset(c.x + cos(angle).toFloat() * radius * wave, c.y + sin(angle).toFloat() * radius * wave),
                    strokeWidth = 1.5.dp.toPx()
                )
            }
            drawCircle(Color(0xFF6DEBFF).copy(alpha = 0.22f), radius = radius * 0.39f, center = c)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Mic, contentDescription = "Talk to JARVIS", tint = Color.White, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(7.dp))
            Text("TAP TO", color = cyan, fontSize = 10.sp, letterSpacing = 2.sp)
            Text("J.A.R.V.I.S.", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Text("VOICE LINK", color = cyan.copy(alpha = 0.9f), fontSize = 8.sp, letterSpacing = 2.sp)
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
