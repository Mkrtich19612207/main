package com.example.myapplication

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.*

class ChatActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var chatLog: TextView
    private lateinit var input: EditText
    private lateinit var btnSend: Button
    private lateinit var btnMic: ImageButton
    private lateinit var btnSpeak: ImageButton
    private lateinit var scrollView: ScrollView

    private val REQUEST_CODE_VOICE = 1234
    private val PREFS_CHAT = "prefs_ai_chat"
    private val PREFS_KEY_COUNT = "ai_chat_count"
    private val LIMIT = 20 // лимит на бесплатные запросы

    private val OPENAI_API_KEY = "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"
    private var tts: TextToSpeech? = null
    private var lastAIReply: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        chatLog = findViewById(R.id.chatLog)
        input = findViewById(R.id.input)
        btnSend = findViewById(R.id.btnSend)
        btnMic = findViewById(R.id.btnMic)
        btnSpeak = findViewById(R.id.btnSpeak)
        scrollView = findViewById(R.id.scrollView)

        tts = TextToSpeech(this, this)
        btnSpeak.isEnabled = false

        btnSend.setOnClickListener { sendMessage() }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE) {
                sendMessage()
                true
            } else false
        }
        btnMic.setOnClickListener { startVoiceInput() }
        btnSpeak.setOnClickListener { speakLastAIReply() }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    // Получаем наиболее релевантный язык: сначала с клавиатуры, потом с интерфейса
    private fun getBestLocale(): Locale {
        try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            val subtype = imm?.currentInputMethodSubtype
            val localeString = subtype?.locale ?: "en"
            if (!localeString.isNullOrEmpty()) {
                return Locale.forLanguageTag(localeString.replace('_', '-'))
            }
        } catch (_: Exception) {}

        // 2. Если не удалось — используем язык интерфейса приложения
        val config = resources.configuration
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            config.locales.get(0)
        } else {
            @Suppress("DEPRECATION")
            config.locale
        }
    }

    // Устанавливаем язык TTS динамически
    private fun setTTSLanguageDynamic() {
        val locale = getBestLocale()
        val result = tts?.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Toast.makeText(this, "TTS: выбранный язык не поддерживается", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            setTTSLanguageDynamic()
        }
    }

    private fun speakLastAIReply() {
        val reply = lastAIReply
        if (!reply.isNullOrEmpty() && tts != null) {
            setTTSLanguageDynamic()
            tts?.speak(reply, TextToSpeech.QUEUE_FLUSH, null, "chat_ai_tts")
        }
    }

    private fun sendMessage() {
        val msg = input.text.toString().trim()
        if (msg.isEmpty()) return

        val count = getPrefsCount()
        if (count >= LIMIT) {
            showLimitDialog()
            return
        }

        appendChat(getString(R.string.you) + ": $msg\n")
        input.text = null
        btnSpeak.isEnabled = false

        askOpenAI(msg) { reply ->
            runOnUiThread {
                lastAIReply = reply
                appendChat(getString(R.string.ai) + ": $reply\n")
                incPrefsCount()
                btnSpeak.isEnabled = true
            }
        }
    }

    private fun appendChat(text: String) {
        chatLog.append(text)
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun getPrefsCount(): Int {
        val prefs = getSharedPreferences(PREFS_CHAT, Context.MODE_PRIVATE)
        return prefs.getInt(PREFS_KEY_COUNT, 0)
    }
    private fun incPrefsCount() {
        val prefs = getSharedPreferences(PREFS_CHAT, Context.MODE_PRIVATE)
        prefs.edit().putInt(PREFS_KEY_COUNT, getPrefsCount() + 1).apply()
    }
    private fun showLimitDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.access_limited))
            .setMessage(getString(R.string.limit_message))
            .setPositiveButton("OK", null)
            .show()
    }

    // Голосовой ввод с автоопределением языка по клавиатуре/интерфейсу
    private fun startVoiceInput() {
        val locale = getBestLocale()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.language)
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.speak_prompt))
        try {
            startActivityForResult(intent, REQUEST_CODE_VOICE)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.voice_error), Toast.LENGTH_SHORT).show()
        }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_VOICE && resultCode == Activity.RESULT_OK) {
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!results.isNullOrEmpty()) {
                input.setText(results[0])
                sendMessage()
            }
        }
    }

    // Запрос к OpenAI GPT-3.5
    private fun askOpenAI(message: String, onResult: (String) -> Unit) {
        val client = OkHttpClient()
        val url = "https://api.openai.com/v1/chat/completions"
        val json = JSONObject()
        json.put("model", "gpt-3.5-turbo")
        json.put("messages", org.json.JSONArray().apply {
            put(JSONObject().apply {
                put("role", "user")
                put("content", message)
            })
        })
        val mediaType = "application/json".toMediaType()
        val body = json.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $OPENAI_API_KEY")
            .post(body)
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResult(getString(R.string.network_error) + ": " + e.localizedMessage)
            }
            override fun onResponse(call: Call, response: Response) {
                val respStr = response.body?.string()
                if (!response.isSuccessful || respStr == null) {
                    onResult(getString(R.string.ai_error) + ": " + response.code)
                    return
                }
                try {
                    val respJson = JSONObject(respStr)
                    val choices = respJson.getJSONArray("choices")
                    val content = choices.getJSONObject(0).getJSONObject("message").getString("content")
                    onResult(content.trim())
                } catch (e: Exception) {
                    onResult(getString(R.string.parse_error) + ": " + e.localizedMessage)
                }
            }
        })
    }
}
