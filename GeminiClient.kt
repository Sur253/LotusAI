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

    

fun chatWithSearch(apiKey: String, question: String): String {
    val tavilyKey = context
        .getSharedPreferences("lotus", Context.MODE_PRIVATE)
        .getString("tavily_api_key", "")
        .orEmpty()

    if (tavilyKey.isBlank()) {
        throw IOException("Settings-ல் Tavily API key save செய்யுங்கள்.")
    }

    val searchQuery = """
        $question
        Tamil Nadu India latest breaking news today.
        Local district news from all 38 districts of Tamil Nadu.
        Chennai, Coimbatore, Madurai, Sivaganga, Ramanathapuram,
        Tiruchirappalli, Salem, Thanjavur, Tirunelveli,
        Kanyakumari, Dindigul, Theni, Virudhunagar,
        Thoothukudi, Erode, Tiruppur and all other districts.
        District local news, politics, accidents, crime,
        education, agriculture, jobs, government announcements.
    """.trimIndent()

    val searchResults = searchTavily(tavilyKey, searchQuery)

    val prompt = """
        You are Lotus, a Tamil-speaking AI news assistant.

        Reply in natural Tamil.
        Give detailed news, not just headlines.

        Prioritize news from ALL 38 districts of Tamil Nadu.
        Include district name, news headline, date,
        what happened and available details.

        Also cover:
        1. Tamil Nadu state news and politics
        2. District local news
        3. India national news
        4. International headlines and detailed world news
           from USA, UK, Europe, China, Russia, Japan,
           Middle East, Asia and other countries.
           Include international politics, wars, economy,
           technology, science and major world events.
        5. Business and economy
        6. Sports
        7. Technology and science
        8. Cinema and entertainment
        9. Weather and government announcements

        Do not invent news or district reports.
        If a district has no relevant search result,
        do not create a fake news story for that district.
        Mention that available search results are limited.
        Include source URLs for the news.

        User question:
        $question

        Latest web search results:
        $searchResults
    """.trimIndent()

    return generate(apiKey, prompt, null, null)
}
 private fun searchTavily(apiKey: String, question: String): String {
    val body = JSONObject()
        .put("query", question)
        .put("search_depth", "basic")
        .put("max_results", 10)
        .put("include_answer", false)

    val request = Request.Builder()
        .url("https://api.tavily.com/search")
        .addHeader("Authorization", "Bearer $apiKey")
        .addHeader("Content-Type", "application/json")
        .post(body.toString().toRequestBody(jsonType))
        .build()

    client.newCall(request).execute().use { response ->
        val responseText = response.body?.string().orEmpty()

        if (!response.isSuccessful) {
            throw IOException("Tavily Search Error: ${response.code} - $responseText")
        }

        val root = JSONObject(responseText)
        val results = root.optJSONArray("results")
            ?: throw IOException("Tavily search results கிடைக்கவில்லை.")

        if (results.length() == 0) {
            throw IOException("Search results எதுவும் கிடைக்கவில்லை.")
        }

        val output = StringBuilder()

        for (i in 0 until results.length()) {
            val item = results.optJSONObject(i) ?: continue

            output.append("Title: ")
                .append(item.optString("title"))
                .append("\n")

            output.append("Content: ")
                .append(item.optString("content"))
                .append("\n")

            output.append("Source: ")
                .append(item.optString("url"))
                .append("\n\n")
        }

        return output.toString()
    }
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
   private fun generate(
    apiKey: String,
    prompt: String,
    fileUri: String?,
    mime: String?,
): String {
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
        JSONArray().put(
            JSONObject().put("parts", parts)
        )
    )
    val requestModel = model
    val request = Request.Builder()
        .url("https://generativelanguage.googleapis.com/v1beta/models/$requestModel:generateContent")
        .addHeader("x-goog-api-key", apiKey)
        .addHeader("Content-Type", "application/json")
        .post(body.toString().toRequestBody(jsonType))
        .build()

    client.newCall(request).execute().use { response ->
        val responseText = response.body?.string().orEmpty()

        if (!response.isSuccessful) {
            throw IOException(parseError(responseText, response.code))
        }

        val root = JSONObject(responseText)
        val candidates = root.optJSONArray("candidates")
            ?: throw IOException("AI response-ல் candidates இல்லை.")

        if (candidates.length() == 0) {
            throw IOException("AI response empty.")
        }

        val candidate = candidates.getJSONObject(0)
        val content = candidate.optJSONObject("content")
            ?: throw IOException("AI content கிடைக்கவில்லை.")

        val partsOut = content.optJSONArray("parts") ?: JSONArray()
        val answer = StringBuilder()

        for (i in 0 until partsOut.length()) {
            val p = partsOut.optJSONObject(i) ?: continue
            val t = p.optString("text", "")
            if (t.isNotBlank()) answer.append(t)
        }
        val answerText = answer.toString().trim()
            .ifBlank { "AI பதில் கிடைக்கவில்லை." }

        return answerText
    }
   }
fun searchTamilYouTube(query: String): String {
    val prefs = context.getSharedPreferences("lotus", Context.MODE_PRIVATE)
    val youtubeKey = prefs.getString("youtube_api_key", "")
        ?.trim().orEmpty()

    if (youtubeKey.isBlank()) {
        throw IOException("Settings-ல் YouTube API Key save செய்யுங்கள்.")
    }

    val url = Uri.parse("https://www.googleapis.com/youtube/v3/search")
        .buildUpon()
        .appendQueryParameter("part", "snippet")
        .appendQueryParameter("type", "video")
        .appendQueryParameter("maxResults", "5")
        .appendQueryParameter("order", "date")
        .appendQueryParameter("regionCode", "IN")
        .appendQueryParameter("relevanceLanguage", "ta")
        .appendQueryParameter("q", "$query Tamil news")
        .appendQueryParameter("key", youtubeKey)
        .build()
        .toString()

    val request = Request.Builder()
        .url(url)
        .get()
        .build()

    client.newCall(request).execute().use { response ->
        val responseText = response.body?.string().orEmpty()

        if (!response.isSuccessful) {
            throw IOException("YouTube API error: ${response.code} - $responseText")
        }

        val root = JSONObject(responseText)
        val items = root.optJSONArray("items")
            ?: throw IOException("YouTube results கிடைக்கவில்லை.")

        if (items.length() == 0) {
            return "தமிழ் செய்தி வீடியோக்கள் கிடைக்கவில்லை."
        }

        val result = StringBuilder()
        result.append("சமீபத்திய தமிழ் YouTube செய்திகள்:\n\n")

        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            val snippet = item.getJSONObject("snippet")
            val videoId = item.getJSONObject("id")
                .optString("videoId")

            if (videoId.isBlank()) continue

            val title = snippet.optString("title")
            val channel = snippet.optString("channelTitle")
            val date = snippet.optString("publishedAt")
            val link = "https://www.youtube.com/watch?v=$videoId"

            result.append("${i + 1}. $title\n")
            result.append("சேனல்: $channel\n")
            result.append("தேதி: $date\n")
            result.append("வீடியோ: $link\n\n")
        }

        return result.toString().trim()
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
