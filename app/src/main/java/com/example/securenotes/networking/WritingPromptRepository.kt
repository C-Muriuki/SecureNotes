package com.example.securenotes.networking

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

data class WritingPrompt(val text: String, val author: String?, val isOffline: Boolean)

object WritingPromptRepository {
    private const val PREFS = "writing_prompt_cache"
    private const val KEY_TEXT = "quote_text"
    private const val KEY_AUTHOR = "quote_author"
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun load(context: Context, callback: (WritingPrompt) -> Unit) {
        val appContext = context.applicationContext
        worker.execute {
            val live = if (hasInternet(appContext)) runCatching { fetchRemotePrompt() }.getOrNull() else null
            val result = if (live != null) {
                cachePrompt(appContext, live)
                live
            } else {
                offlinePrompt(appContext)
            }
            main.post { callback(result) }
        }
    }

    private fun hasInternet(context: Context): Boolean {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = connectivity.activeNetwork ?: return false
        return connectivity.getNetworkCapabilities(network)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    private fun fetchRemotePrompt(): WritingPrompt {
        val connection = URL(ENDPOINT).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 4_000
        connection.readTimeout = 5_000
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "SecureNotes-Android")
        return try {
            parseHttpResponse(connection)
        } finally {
            connection.disconnect()
        }
    }

    internal fun parseHttpResponse(connection: HttpURLConnection): WritingPrompt {
        check(connection.responseCode in 200..299) { "Writing inspiration service unavailable." }
        val body = StringBuilder()
        var totalCharacters = 0
        connection.inputStream.reader(Charsets.UTF_8).use { reader ->
            val buffer = CharArray(1024)
            while (true) {
                val remaining = MAX_RESPONSE_CHARS - totalCharacters
                val readLimit = minOf(buffer.size, remaining + 1)
                val count = reader.read(buffer, 0, readLimit)
                if (count < 0) break
                require(count <= remaining) { "Writing inspiration response was too large." }
                body.append(buffer, 0, count)
                totalCharacters += count
            }
        }
        return parseJsonPrompt(body.toString())
    }

    internal fun parseJsonPrompt(body: String): WritingPrompt {
        val json = JSONObject(body)
        val text = json.optString("quote").trim()
        val author = json.optString("author").trim()
        require(text.isNotEmpty() && text.length <= MAX_QUOTE_CHARS) { "No usable writing prompt was returned." }
        return WritingPrompt(text, author.ifEmpty { null }, isOffline = false)
    }

    internal fun cachePrompt(context: Context, prompt: WritingPrompt) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_TEXT, prompt.text)
            .putString(KEY_AUTHOR, prompt.author)
            .apply()
    }

    internal fun offlinePrompt(context: Context): WritingPrompt =
        cachedPrompt(context.applicationContext) ?: localXmlPrompt(context.applicationContext)

    private fun cachedPrompt(context: Context): WritingPrompt? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val text = prefs.getString(KEY_TEXT, null)?.takeIf(String::isNotBlank) ?: return null
        return WritingPrompt(text, prefs.getString(KEY_AUTHOR, null), isOffline = true)
    }

    private fun localXmlPrompt(context: Context): WritingPrompt {
        val parser = context.resources.getXml(com.example.securenotes.R.xml.offline_prompts)
        val prompts = mutableListOf<String>()
        try {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "prompt") {
                    prompts += parser.nextText().trim()
                }
                event = parser.next()
            }
        } finally {
            parser.close()
        }
        return WritingPrompt(
            prompts.randomOrNull() ?: "Write one small thing you want to remember from today.",
            null,
            isOffline = true
        )
    }

    private const val ENDPOINT = "https://dummyjson.com/quotes/random"
    internal const val MAX_RESPONSE_CHARS = 16_384
    private const val MAX_QUOTE_CHARS = 1_000
}
