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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        buildUi()
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
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 18, 20, 24)
            setBackgroundColor(bg)
        }
        scroll.addView(root)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val icon = ImageView(this).apply {
            setImageResource(com.personalvideoai.R.drawable.lotus_icon)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        header.addView(icon, LinearLayout.LayoutParams(48, 48))
        val titleBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(12, 0, 0, 0) }
        titleBox.addView(TextView(this).apply {
            text = "LOTUS AI"
            textSize = 23f
            setTextColor(this@MainActivity.text)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        titleBox.addView(TextView(this).apply {
            text = "Your intelligent companion"
            textSize = 12f
            setTextColor(muted)
        })
        header.addView(titleBox, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(TextView(this).apply {
            text = "⚙"
            textSize = 25f
            gravity = Gravity.CENTER
            setTextColor(this@MainActivity.text)
            setPadding(12, 8, 8, 8)
            setOnClickListener { showSettings() }
        })
        root.addView(header)

        root.addView(Space(this), LinearLayout.LayoutParams(1, 18))

        val orb = FrameLayout(this).apply {
            background = rounded(panel, Color.rgb(48, 42, 65), 32f)
            minimumHeight = 205
        }
        val glow = TextView(this).apply {
            text = "✦"
            textSize = 92f
            gravity = Gravity.CENTER
            setTextColor(accent)
            background = rounded(Color.rgb(30, 24, 46), Color.rgb(95, 73, 125), 160f)
        }
        orb.addView(glow, FrameLayout.LayoutParams(150, 150, Gravity.CENTER))
        orb.addView(TextView(this).apply {
            text = "LOTUS"
            textSize = 13f
            setTextColor(muted)
            gravity = Gravity.CENTER
        }, FrameLayout.LayoutParams(-1, -1))
        root.addView(orb)

        root.addView(TextView(this).apply {
            text = "வணக்கம்! நான் Lotus. ✨"
            textSize = 20f
            setTextColor(this@MainActivity.text)
            gravity = Gravity.CENTER
            setPadding(0, 18, 0, 4)
        })
        root.addView(TextView(this).apply {
            text = "Tamil / Tanglish-ல் பேசலாம் • Video-வை analyse செய்யலாம்"
            textSize = 13f
            setTextColor(muted)
            gravity = Gravity.CENTER
        })

        videoLabel = TextView(this).apply {
            text = "🎥  Video ready இல்லை"
            textSize = 13f
            setTextColor(muted)
            setPadding(16, 12, 16, 12)
            background = rounded(panel, Color.rgb(45, 47, 64), 18f)
        }
        val vp = LinearLayout.LayoutParams(-1, -2); vp.setMargins(0, 16, 0, 8)
        root.addView(videoLabel, vp)

        chat = TextView(this).apply {
            text = "Lotus உங்களுக்காக ready.\n\n🎙️ பேசலாம்\n🎥 Video upload செய்யலாம்\n💬 கேள்வி கேட்கலாம்"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(this@MainActivity.text)
            setPadding(20, 24, 20, 24)
            background = rounded(panel, Color.rgb(48, 40, 62), 24f)
        }
        val cp = LinearLayout.LayoutParams(-1, 180); cp.setMargins(0, 8, 0, 14)
        root.addView(chat, cp)

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val upload = pill("🎥  VIDEO") { chooseVideo() }
        val voice = pill("🎙️  பேசு") { startVoice() }
        buttons.addView(upload, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(0,0,6,0) })
        buttons.addView(voice, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(6,0,0,0) })
        root.addView(buttons)

        input = EditText(this).apply {
            hint = "Lotus-விடம் கேளுங்கள்..."
            setTextColor(this@MainActivity.text)
            setHintTextColor(Color.rgb(120, 125, 145))
            setPadding(18, 14, 18, 14)
            background = rounded(panel, Color.rgb(55, 58, 78), 22f)
            minLines = 1
            maxLines = 4
        }
        val ip = LinearLayout.LayoutParams(-1, -2); ip.setMargins(0, 12, 0, 8)
        root.addView(input, ip)

        val send = TextView(this).apply {
            text = "SEND  ➜"
            textSize = 16f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(18, 15, 18, 15)
            background = rounded(accent, Color.TRANSPARENT, 22f)
            setOnClickListener { sendToAi() }
        }
        root.addView(send)
        setContentView(scroll)
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
        if (question.isBlank() && selectedVideo == null) { Toast.makeText(this, "ஒரு கேள்வி கேளுங்கள்.", Toast.LENGTH_SHORT).show(); return }
        val key = prefs.getString("api_key", "").orEmpty()
        if (key.isBlank()) { Toast.makeText(this, "Settings-ல் Gemini API key save செய்யுங்கள்.", Toast.LENGTH_LONG).show(); showSettings(); return }
        chat.text = "Lotus யோசிக்கிறது… ⏳"
        input.isEnabled = false
        val video = selectedVideo
        executor.execute {
            try {
                val answer = if (video != null) GeminiClient(this).askAboutVideo(key, video, question.ifBlank { "இந்த video-வில் என்ன நடக்கிறது? Tamil/Tanglish-ல் விளக்குங்கள்." }) else GeminiClient(this).chat(key, question)
                runOnUiThread {
    chat.text = "Lotus:\n\n$answer"
    input.text.clear()
    input.isEnabled = true

    lateinit var voice: android.speech.tts.TextToSpeech

    voice = android.speech.tts.TextToSpeech(this) { status ->
        if (status == android.speech.tts.TextToSpeech.SUCCESS) {
            val result = voice.setLanguage(java.util.Locale("ta", "IN"))

            if (result == android.speech.tts.TextToSpeech.LANG_MISSING_DATA ||
                result == android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED) {
                voice.language = java.util.Locale.US
            }

            voice.speak(
                answer,
                android.speech.tts.TextToSpeech.QUEUE_FLUSH,
                null,
                "lotus_reply"
            )
        }
    }
                
     input.text.clear(); input.isEnabled = true }
            } catch (e: Exception) { runOnUiThread { chat.text = "Lotus:\n\n❌ ${e.message ?: "AI request failed"}"; input.isEnabled = true } }
        }
    }

    private fun getFileName(uri: Uri): String {
        var name = "video"
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) name = it.getString(0) }
        return name
    }

    override fun onDestroy() { executor.shutdownNow(); super.onDestroy() }
}
