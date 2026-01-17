package com.example.youdaoa11yservice

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class YoudaoA11yService : AccessibilityService() {

    companion object {
        const val ACTION_UPDATE_TEXT = "com.example.youdaoa11yservice.UPDATE_TEXT"

        private const val ENDPOINT = "http://192.168.100.229:27183/"
        private const val TARGET_PACKAGE = "com.youdao.wisdom"
        private const val DEBUG = false

        private val IGNORE_CONTAINS = listOf(
            "http://",
            "https://"
        )

        private fun isLikelyContent(s: String): Boolean {
            val t = s.trim()
            if (t.isEmpty()) return false
            if (IGNORE_CONTAINS.any { t.contains(it, ignoreCase = true) }) return false
            return true
        }
    }

    private val httpExecutor = Executors.newSingleThreadExecutor()
    private var lastSent: String? = null

    private fun broadcastText(text: String) {
        val intent = Intent(ACTION_UPDATE_TEXT).apply { putExtra("payload", text) }
        sendBroadcast(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val root = findYoudaoRoot() ?: return

        val texts = ArrayList<String>(128)
        collectTexts(root, texts)

        if (DEBUG) {
            val debugPayload = buildString {
                append("[foundRoot]\n")
                append("[event]=").append(event.eventType).append('\n')
                append("[count]=").append(texts.size).append('\n')
                for (t in texts) {
                    val s = t.trim()
                    if (s.isNotEmpty()) append(s).append('\n')
                }
            }
            httpExecutor.execute { postText(debugPayload) }
            return
        }

        val candidate = extractBySecondTitle(texts) ?: return
        if (candidate == lastSent) return
        lastSent = candidate
        broadcastText(candidate)
        httpExecutor.execute { postText(candidate) }
    }

    override fun onInterrupt() {
        // no-op
    }

    private fun findYoudaoRoot(): AccessibilityNodeInfo? {
        for (window: AccessibilityWindowInfo in windows) {
            val r = window.root ?: continue
            if (r.packageName?.toString() == TARGET_PACKAGE) return r
        }
        return null
    }

    private fun collectTexts(node: AccessibilityNodeInfo, out: MutableList<String>) {
        node.text?.toString()?.let { out.add(it) }
        node.contentDescription?.toString()?.let { out.add(it) }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectTexts(it, out) }
        }
    }

    private fun normalizeText(s: String): String = s.trim()
        .replace(Regex("[\u0000-\u001F]"), "")
        .replace("“", "\"")
        .replace("”", "\"")
        .replace("‘", "'")
        .replace("’", "'")

    private fun extractBySecondTitle(texts: List<String>): String? {
        val normalized = texts.map { normalizeText(it) }
        var seenTitle = 0
        var secondIdx = -1
        for (i in normalized.indices) {
            if (normalized[i].trim() == "词句释义") {
                seenTitle++
                if (seenTitle == 2) { secondIdx = i; break }
            }
        }
        if (secondIdx < 0) return null
        for (i in secondIdx + 1 until normalized.size) {
            val s = normalized[i].trim()
            if (s.isNotEmpty() && isLikelyContent(s)) return s
        }
        return null
    }

    private fun postText(text: String) {
        try {
            val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 1500
                readTimeout = 1500
                doOutput = true
                setRequestProperty("Content-Type", "text/plain; charset=utf-8")
            }
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(text) }
            conn.inputStream.use { it.readBytes() }
            conn.disconnect()
        } catch (_: Exception) { }
    }
}
