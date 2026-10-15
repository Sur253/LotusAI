package com.personalvideoai

import android.content.Context
import android.net.Uri
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class GeminiClient(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .build()

    private val model = "gemini-3.8-flash"
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    fun chat(apiKey: String, question: String): String {
        val prompt = """
            You are Lotus, a friendly South Indian female AI assistant.
            Reply naturally in Tamil or Tanglish, matching the user's language.
            Be concise but useful. If the user asks in English, you may answer in simple English.
            User question:
            $question
        """.trimIndent()
        return generate(apiKey, prompt, null, null)
    }

    fun askAboutVideo(apiKey: String, videoUri: Uri, question: String): String {
        val uploaded = uploadVideo(apiKey, videoUri)
        val fileUri = uploaded.first
        val mime = uploaded.second

        val prompt = """
            You are Lotus, a personal South Indian video assistant.
            Understand the video using both visual and audio information.
            Answer the user's question in natural Tamil/Tanglish unless the user clearly asks for another language.
            Mention timestamps when useful. Do not invent events that are not in the video.

            User question:
            $question
        """.trimIndent()

        return generate(apiKey, prompt, fileUri, mime)
    }

    private fun generate(apiKey: String, prompt: String, fileUri: String?, mime: String?): String {
        val parts = JSONArray()
        parts.put(JSONObject().put("text", prompt))

        if (fileUri != null && mime != null) {
            parts.put(
                JSONObject().put(
                    "file_data",
                    JSONObject()
                        .put("mime_type", mime)
                        .put("file_uri", fileUri)
                )
            )
        }

        val body = JSONObject().put(
            "contents",
            JSONArray().put(JSONObject().put("parts", parts))
        )

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .addHeader("x-goog-api-key", apiKey)
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(jsonType))
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException(parseError(text, response.code))

            val root = JSONObject(text)
            val candidates = root.optJSONArray("candidates")
                ?: throw IOException("AI response-ல் candidates இல்லை.")
            if (candidates.length() == 0) throw IOException("AI response empty.")

            val content = candidates.getJSONObject(0).optJSONObject("content")
                ?: throw IOException("AI content கிடைக்கவில்லை.")
            val partsOut = content.optJSONArray("parts") ?: JSONArray()
            val answer = StringBuilder()
            for (i in 0 until partsOut.length()) {
                val p = partsOut.optJSONObject(i) ?: continue
                val t = p.optString("text", "")
                if (t.isNotBlank()) answer.append(t)
            }
            return answer.toString().trim().ifBlank { "AI பதில் கிடைக்கவில்லை." }
        }
    }

    private fun uploadVideo(apiKey: String, uri: Uri): Pair<String, String> {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "video/mp4"
        val size = getSize(uri)

        if (size <= 0) throw IOException("Video size கண்டுபிடிக்க முடியவில்லை.")
        if (size > 2L * 1024L * 1024L * 1024L) {
            throw IOException("Video 2GB-க்கு மேல் இருக்கிறது.")
        }

        // Gemini Files API resumable upload.
        val startBody = JSONObject()
            .put("file", JSONObject().put("display_name", "LotusVideo"))
            .toString()
            .toRequestBody(jsonType)

        val start = Request.Builder()
            .url("https://generativelanguage.googleapis.com/upload/v1beta/files?key=$apiKey")
            .addHeader("X-Goog-Upload-Protocol", "resumable")
            .addHeader("X-Goog-Upload-Command", "start")
            .addHeader("X-Goog-Upload-Header-Content-Length", size.toString())
            .addHeader("X-Goog-Upload-Header-Content-Type", mime)
            .addHeader("Content-Type", "application/json")
            .post(startBody)
            .build()

        val uploadUrl = client.newCall(start).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException(parseError(response.body?.string().orEmpty(), response.code))
            }
            response.header("X-Goog-Upload-URL")
                ?: throw IOException("Gemini upload URL கிடைக்கவில்லை.")
        }

        val body = object : RequestBody() {
            override fun contentType() = mime.toMediaType()
            override fun contentLength() = size

            override fun writeTo(sink: okio.BufferedSink) {
                resolver.openInputStream(uri)?.use { input ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        sink.write(buffer, 0, read)
                    }
                } ?: throw IOException("Video file open செய்ய முடியவில்லை.")
            }
        }

        val upload = Request.Builder()
            .url(uploadUrl)
            .addHeader("X-Goog-Upload-Offset", "0")
            .addHeader("X-Goog-Upload-Command", "upload, finalize")
            .post(body)
            .build()

        val fileName: String
        val fileUri: String
        client.newCall(upload).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException(parseError(text, response.code))
            val file = JSONObject(text).optJSONObject("file")
                ?: throw IOException("Uploaded file response கிடைக்கவில்லை.")
            fileName = file.optString("name")
            fileUri = file.optString("uri")
        }

        if (fileName.isBlank() || fileUri.isBlank()) throw IOException("Gemini file URI கிடைக்கவில்லை.")

        // Wait until Gemini has processed the video.
        var state = "PROCESSING"
        repeat(90) {
            val statusRequest = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/$fileName?key=$apiKey")
                .get()
                .build()

            client.newCall(statusRequest).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IOException(parseError(text, response.code))
                val file = JSONObject(text).optJSONObject("file") ?: JSONObject(text)
                state = file.optString("state", "PROCESSING")
            }

            if (state == "ACTIVE") return Pair(fileUri, mime)
            if (state == "FAILED") throw IOException("Gemini video processing failed.")
            Thread.sleep(2000)
        }

        throw IOException("Video processing நேரம் அதிகமாகிறது. மீண்டும் try செய்யுங்கள்.")
    }

    private fun getSize(uri: Uri): Long {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use {
            if (it.moveToFirst() && !it.isNull(0)) return it.getLong(0)
        }
        return -1L
    }

    private fun parseError(text: String, code: Int): String {
        return try {
            val root = JSONObject(text)
            root.optJSONObject("error")?.optString("message")
                ?.takeIf { it.isNotBlank() }
                ?: "Gemini API error ($code)"
        } catch (_: Exception) {
            "Gemini API error ($code)"
        }
    }
}
