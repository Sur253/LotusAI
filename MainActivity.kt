package com.personalvideoai

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.speech.RecognizerIntent
import android.view.Gravity
import android.view.View
import android.widget.*
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var input: EditText
    private lateinit var chat: TextView
    private lateinit var videoLabel: TextView
    private val executor = Executors.newSingleThreadExecutor()
    private var selectedVideo: Uri? = null
    private val prefs by lazy { getSharedPreferences("lotus", Context.MODE_PRIVATE) }
    private val bg = Color.rgb(8, 9, 16)
    private val panel = Color.rgb(18, 20, 31)
    private val text = Color.WHITE
    private val muted = Color.rgb(165, 170, 190)
    private val accent = Color.rgb(210, 175, 255)
    private lateinit var tts: android.speech.tts.TextToSpeech
    private var ttsReady = false
    override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    window.statusBarColor = bg
    window.navigationBarColor = bg
    buildUi()

    tts = android.speech.tts.TextToSpeech(this) { status ->
        if (status == android.speech.tts.TextToSpeech.SUCCESS) {

            var result = tts.setLanguage(java.util.Locale("ta", "IN"))

            if (result == android.speech.tts.TextToSpeech.LANG_MISSING_DATA ||
                result == android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                result = tts.setLanguage(java.util.Locale.US)
            }

            ttsReady =
                result != android.speech.tts.TextToSpeech.LANG_MISSING_DATA &&
                result != android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED

        } else {
            ttsReady = false
        }
    }
    }  
    private fun rounded(fill: Int, stroke: Int = Color.TRANSPARENT, radius: Float = 24f): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = radius
            if (stroke != Color.TRANSPARENT) setStroke(1, stroke)
        }

    private fun pill(textValue: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = textValue
        textSize = 15f
        setTextColor(this@MainActivity.text)
        gravity = Gravity.CENTER
        setPadding(18, 14, 18, 14)
        background = rounded(panel, Color.rgb(55, 58, 78), 40f)
        setOnClickListener { onClick() }
    }

     
private fun buildUi() {
    val root = FrameLayout(this).apply {
        setBackgroundColor(Color.BLACK)
    }

    val content = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(20, 80, 20, 24)
    }

    val scroll = ScrollView(this)
    scroll.addView(content)

    // Lotus logo - always visible
    val icon = ImageView(this).apply {
        setImageResource(com.personalvideoai.R.drawable.lotus_icon)
        scaleType = ImageView.ScaleType.FIT_CENTER
        contentDescription = "Lotus"
    }

    content.addView(
        icon,
        LinearLayout.LayoutParams(220, 220).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        }
    )

    // AI answer below logo
    chat = TextView(this).apply {
        text = "வணக்கம்! நான் Lotus 🌸"
        textSize = 17f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setPadding(18, 20, 18, 20)
        background = rounded(
            Color.rgb(25, 18, 38),
            Color.rgb(120, 65, 160),
            24f
        )
    }

    content.addView(
        chat,
        LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(0, 18, 0, 12)
        }
    )

    videoLabel = TextView(this).apply {
        text = "🎥 Video ready இல்லை"
        textSize = 13f
        gravity = Gravity.CENTER
        setTextColor(Color.LTGRAY)
        setPadding(12, 12, 12, 12)
    }

    content.addView(videoLabel)

    root.addView(
        scroll,
        FrameLayout.LayoutParams(-1, -1)
    )

    // Three-dot menu
    val menuButton = TextView(this).apply {
        text = "⋮"
        textSize = 34f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        background = rounded(
            Color.rgb(35, 20, 50),
            accent,
            50f
        )
    }

    val menuParams = FrameLayout.LayoutParams(
        60, 60, Gravity.TOP or Gravity.END
    )
    menuParams.setMargins(0, 20, 18, 0)
    root.addView(menuButton, menuParams)

    menuButton.setOnClickListener {
        val popup = android.widget.PopupMenu(this, menuButton)

        popup.menu.add("Voice")
        popup.menu.add("Video")
        popup.menu.add("Ask Lotus")
        popup.menu.add("Settings")

        popup.setOnMenuItemClickListener { item ->
            when (item.title.toString()) {
                "Voice" -> startVoice()
                "Video" -> chooseVideo()
                "Ask Lotus" -> {
                    val questionBox = EditText(this).apply {
                        hint = "Lotus-விடம் கேளுங்கள்..."
                        setTextColor(Color.BLACK)
                    }

                    android.app.AlertDialog.Builder(this)
                        .setTitle("Ask Lotus")
                        .setView(questionBox)
                        .setPositiveButton("SEND") { _, _ ->
                            input = questionBox
                            sendToAi()
                        }
                        .setNegativeButton("CANCEL", null)
                        .show()
                }
                "Settings" -> showSettings()
            }
            true
        }

        popup.show()
    }

    setContentView(root)
} 
    private fun showSettings() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 8, 24, 0) }
        val info = TextView(this).apply { text = "Gemini API key-ஐ இங்கே save செய்யுங்கள்."; setTextColor(Color.DKGRAY) }
        val key = EditText(this).apply {
            hint = "AIza..."
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(prefs.getString("api_key", ""))
        }
        box.addView(info); box.addView(key)
        AlertDialog.Builder(this).setTitle("Lotus AI Settings").setView(box)
            .setPositiveButton("SAVE") { _, _ -> prefs.edit().putString("api_key", key.text.toString().trim()).apply(); Toast.makeText(this, "API key saved", Toast.LENGTH_SHORT).show() }
            .setNegativeButton("CANCEL", null).show()
    }

    private fun chooseVideo() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "video/*"; addCategory(Intent.CATEGORY_OPENABLE) }, 10)
    }

    private fun startVoice() {
        try {
            startActivityForResult(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ta-IN")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ta-IN")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Lotus-விடம் பேசுங்கள்")
            }, 20)
        } catch (_: Exception) { Toast.makeText(this, "Voice input கிடைக்கவில்லை.", Toast.LENGTH_SHORT).show() }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        if (requestCode == 10) {
            selectedVideo = data.data
            videoLabel.text = "🎥  Video: ${getFileName(selectedVideo!!)}"
            chat.text = "Video ready ✅\n\nLotus-க்கு கேள்வி கேளுங்கள்."
        } else if (requestCode == 20) {
            val spoken = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
            if (spoken.isNotBlank()) { input.setText(spoken); input.setSelection(input.text.length); sendToAi() }
        }
    }
    
private fun sendToAi() {
    val question = input.text.toString().trim()

    if (question.isBlank() && selectedVideo == null) {
        Toast.makeText(this, "ஒரு கேள்வி கேளுங்கள்.", Toast.LENGTH_SHORT).show()
        return
    }

    val key = prefs.getString("api_key", "").orEmpty()

    if (key.isBlank()) {
        Toast.makeText(this, "Settings-ல் Gemini API key save செய்யுங்கள்.", Toast.LENGTH_LONG).show()
        showSettings()
        return
    }

    chat.text = "Lotus யோசிக்கிறது…"
    input.isEnabled = false

    val video = selectedVideo

    executor.execute {
        try {
            val answer = if (video != null) {
                GeminiClient(this).askAboutVideo(
                    key,
                    video,
                    question.ifBlank {
                        "இந்த video-வில் என்ன நடக்கிறது? Tamil/Tanglish-ல் விளக்குங்கள்."
                    }
                )
            } else {
                GeminiClient(this).chat(key, question)
            }

            runOnUiThread {
                chat.text = "Lotus:\n\n$answer"

                if (::tts.isInitialized && ttsReady) {
                    tts.setSpeechRate(1.0f)

                    val speakStatus = tts.speak(
                        answer,
                        android.speech.tts.TextToSpeech.QUEUE_FLUSH,
                        null,
                        "lotus_reply"
                    )

                    if (speakStatus == android.speech.tts.TextToSpeech.ERROR) {
                        Toast.makeText(
                            this,
                            "Voice play ஆகவில்லை. TTS settings சரிபார்க்கவும்.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    Toast.makeText(
                        this,
                        "Voice இன்னும் தயாராகவில்லை. மீண்டும் முயற்சி செய்யுங்கள்.",
                        Toast.LENGTH_LONG
                    ).show()
                }

                input.text.clear()
                input.isEnabled = true
            }

        } catch (e: Exception) {
            runOnUiThread {
                chat.text = "Lotus:\n\n❌ ${e.message ?: "AI request failed"}"
                input.isEnabled = true
            }
        }
    }
}
    private fun getFileName(uri: Uri): String {
        var name = "video"
        contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null, null, null
        )?.use {
            if (it.moveToFirst()) name = it.getString(0)
        }
        return name
    }

    override fun onDestroy() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        executor.shutdownNow()
        super.onDestroy()
    }
}
