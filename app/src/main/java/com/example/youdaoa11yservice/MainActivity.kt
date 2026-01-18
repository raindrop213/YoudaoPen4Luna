package com.example.youdaoa11yservice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.inputmethod.EditorInfo
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.core.widget.addTextChangedListener
import android.webkit.WebView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import androidx.core.view.isVisible
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private fun setupDictionarySheet() {
        val content = LayoutInflater.from(this).inflate(R.layout.dialog_dictionary, null)

        etDictQuery = content.findViewById(R.id.et_dict_query)
        btnDictAdd = content.findViewById(R.id.btn_dict_add)
        cgDictTabs = content.findViewById(R.id.cg_dict_tabs)
        wvDict = content.findViewById(R.id.wv_dict)
        val btnClose: ImageButton = content.findViewById(R.id.btn_dict_close)

        wvDict?.settings?.javaScriptEnabled = true
        wvDict?.settings?.domStorageEnabled = true
        wvDict?.settings?.textZoom = 120
        wvDict?.settings?.defaultFontSize = 18
        wvDict?.settings?.defaultFixedFontSize = 18

        etDictQuery?.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                scheduleDictionaryQuery(etDictQuery?.text?.toString().orEmpty())
                true
            } else {
                false
            }
        }

        etDictQuery?.addTextChangedListener {
            scheduleDictionaryQuery(it?.toString().orEmpty())
        }

        btnDictAdd?.setOnClickListener { appendNextTokenToQuery() }
        btnClose.setOnClickListener { hideDictionary() }

        dictSheet = BottomSheetDialog(this).apply {
            setContentView(content)
            setOnShowListener {
                // 不自动聚焦输入框，避免弹出输入法挡住内容
                window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
                etDictQuery?.clearFocus()

                val bottomSheet = findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
                if (bottomSheet != null) {
                    val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet)
                    behavior.isDraggable = false
                    behavior.skipCollapsed = false
                    behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
                }
            }
            setOnDismissListener {
                clearSelectedTokenHighlight()
            }
        }
    }

    private fun showDictionarySheet(word: String) {
        val sheet = dictSheet ?: return
        sheet.show()

        // 打开查词弹窗时不弹出软键盘
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        etDictQuery?.clearFocus()

        val w = word.trim()
        if (w.isNotEmpty()) {
            etDictQuery?.setText(w)
            etDictQuery?.setSelection(w.length)
            scheduleDictionaryQuery(w)
        }
    }

    companion object {
        private const val PREFS = "settings"
        private const val KEY_API_BASE = "api_base"
        private const val KEY_AUTO_TRANSLATE = "auto_translate"
    }

    private fun loadSettings() {
        val sp = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        apiBaseUrl = sp.getString(KEY_API_BASE, apiBaseUrl) ?: apiBaseUrl
        autoTranslate = sp.getBoolean(KEY_AUTO_TRANSLATE, autoTranslate)
    }

    private fun saveSettings() {
        val sp = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit().putString(KEY_API_BASE, apiBaseUrl)
            .putBoolean(KEY_AUTO_TRANSLATE, autoTranslate)
            .apply()
    }

    private fun showSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_settings, null)
        val etApi = dialogView.findViewById<TextInputEditText>(R.id.et_api_base)
        val swAuto = dialogView.findViewById<SwitchMaterial>(R.id.switch_auto_translate)
        etApi.setText(apiBaseUrl)
        swAuto.isChecked = autoTranslate

        MaterialAlertDialogBuilder(this)
            .setTitle("设置")
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                apiBaseUrl = etApi.text?.toString()?.trim().orEmpty().ifBlank { apiBaseUrl }
                autoTranslate = swAuto.isChecked
                saveSettings()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // --- UI components ---
    private lateinit var flTokens: FlowLayout
    private lateinit var llTranslationResults: LinearLayout
    private lateinit var etInput: TextInputEditText

    private lateinit var flBottomPanel: View
    private lateinit var svTranslation: View
    private var dictSheet: com.google.android.material.bottomsheet.BottomSheetDialog? = null
    private var etDictQuery: TextInputEditText? = null
    private var btnDictAdd: MaterialButton? = null
    private var cgDictTabs: ChipGroup? = null
    private var wvDict: WebView? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val httpExecutor = Executors.newFixedThreadPool(4)

    private var apiBaseUrl: String = "http://192.168.100.196:2333"
    private var autoTranslate: Boolean = true

    private val initialText = "僕が六歳だったときのことだ。『ほんとうにあった話』という原生林のことを書いた本で、すごい絵を見た。猛獣を飲みこもうとしている、大蛇ボアの絵だった。再現してみるなら、こんなふうだ。"

    private val debounceDelayMs = 350L
    private var pendingTokenize: Runnable? = null

    private val dictDebounceDelayMs = 250L
    private var pendingDictQuery: Runnable? = null

    private var lastTokens: List<Token> = emptyList()
    private var selectedTokenIndex: Int = -1
    private var selectedTokenView: View? = null

    private data class DictEntry(val id: String, val name: String, val resultHtml: String)
    private var dictEntries: List<DictEntry> = emptyList()
    private var selectedDictId: String? = null

    private val textReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == YoudaoA11yService.ACTION_UPDATE_TEXT) {
                val payload = intent.getStringExtra("payload") ?: return
                setInputText(payload)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        flTokens = findViewById(R.id.fl_tokens)
        llTranslationResults = findViewById(R.id.ll_translation_results)
        etInput = findViewById(R.id.et_input)

        flBottomPanel = findViewById(R.id.fl_bottom_panel)
        svTranslation = findViewById(R.id.sv_translation)
        setupDictionarySheet()

        etInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                scheduleTokenize(s?.toString().orEmpty())
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })



        loadSettings()

        setInputText(initialText)

        val btnSettings: FloatingActionButton = findViewById(R.id.btn_settings)
        btnSettings.setOnClickListener { showSettingsDialog() }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(YoudaoA11yService.ACTION_UPDATE_TEXT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(textReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(textReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(textReceiver)
    }

    private fun setInputText(text: String) {
        etInput.setText(text)
        etInput.setSelection(text.length.coerceAtLeast(0))
    }

    private fun scheduleTokenize(text: String) {
        pendingTokenize?.let { mainHandler.removeCallbacks(it) }
        val task = Runnable { tokenizeAndTranslate(text) }
        pendingTokenize = task
        mainHandler.postDelayed(task, debounceDelayMs)
    }

    private fun tokenizeAndTranslate(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            flTokens.removeAllViews()
            llTranslationResults.removeAllViews()
            return
        }

        flTokens.removeAllViews()
        llTranslationResults.removeAllViews()
        flTokens.addView(makeStatusChip("分词中..."))
        llTranslationResults.addView(makeTranslationStatusView("翻译中..."))

        httpExecutor.execute {
            val mecabResult = runCatching { fetchMecabTokens(trimmed) }
            mainHandler.post {
                flTokens.removeAllViews()
                val tokens = mecabResult.getOrNull()
                if (tokens == null) {
                    flTokens.addView(makeStatusChip("分词失败"))
                } else {
                    renderTokens(tokens)
                }
            }
        }

        requestTranslations(trimmed)
    }

    private data class Token(
        val word: String,
        val kana: String?,
        val isDeli: Boolean,
        val wordclass: String?,
    )

    private fun fetchMecabTokens(text: String): List<Token> {
        val url = URL("$apiBaseUrl/api/mecab?text=${URLEncoder.encode(text, "UTF-8")}")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 4000
            readTimeout = 8000
        }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
        conn.disconnect()

        if (code !in 200..299) throw RuntimeException("HTTP $code: $body")

        val arr = JSONArray(body)
        val items = ArrayList<Token>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val word = obj.optString("word", "")
            if (word.isBlank()) continue
            val kana = if (obj.has("kana") && !obj.isNull("kana")) obj.optString("kana") else null
            val isDeli = obj.optBoolean("isdeli", false)
            val wordclass = if (obj.has("wordclass") && !obj.isNull("wordclass")) obj.optString("wordclass") else null
            items.add(Token(word = word, kana = kana, isDeli = isDeli, wordclass = wordclass))
        }
        return items
    }

    private data class Translator(val id: String, val name: String)

    private fun requestTranslations(text: String) {
        if (!autoTranslate) {
            updateTranslationVisibility(hasContent = false)
            return
        }

        updateTranslationVisibility(hasContent = true)
        httpExecutor.execute {
            val translatorsResult = runCatching { fetchTranslators() }
            val translators = translatorsResult.getOrNull()
            mainHandler.post {
                llTranslationResults.removeAllViews()
                if (translators == null || translators.isEmpty()) {
                    llTranslationResults.addView(makeTranslationStatusView("未获取到翻译器"))
                } else {
                    llTranslationResults.addView(makeTranslationStatusView("翻译器: ${translators.joinToString { it.name }}"))
                }
            }

            if (translators.isNullOrEmpty()) return@execute

            translators.forEach { tr ->
                httpExecutor.execute {
                    val r = runCatching { fetchTranslation(text, tr.id) }
                    mainHandler.post {
                        addTranslationResult(tr.name, r.getOrNull())
                    }
                }
            }
        }
    }

    private fun fetchTranslators(): List<Translator> {
        val url = URL("$apiBaseUrl/api/list/translator")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 4000
            readTimeout = 8000
        }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
        conn.disconnect()

        if (code !in 200..299) throw RuntimeException("HTTP $code: $body")

        val arr = JSONArray(body)
        val out = ArrayList<Translator>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val id = obj.optString("id", "")
            val name = obj.optString("name", id)
            if (id.isBlank()) continue
            out.add(Translator(id = id, name = name))
        }
        return out
    }

    private fun fetchTranslation(text: String, translatorId: String): String {
        val url = URL("$apiBaseUrl/api/translate?text=${URLEncoder.encode(text, "UTF-8")}&id=${URLEncoder.encode(translatorId, "UTF-8")}")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 6000
            readTimeout = 15000
        }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
        conn.disconnect()

        if (code !in 200..299) throw RuntimeException("HTTP $code: $body")

        val obj = JSONObject(body)
        return obj.optString("result", body)
    }

    private fun addTranslationResult(title: String, result: String?) {
        // 清掉"翻译器:"那行（如果还在）
        if (llTranslationResults.childCount == 1) {
            val only = llTranslationResults.getChildAt(0)
            if (only is TextView && only.text.toString().startsWith("翻译器:")) {
                llTranslationResults.removeAllViews()
            }
        }

        val block = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(14))
        }

        val translationText = result ?: "(失败)"
        val fullText = "$translationText  ($title)"
        val spannable = SpannableString(fullText)
        
        // 设置翻译器名称部分的样式（斜体、小字号、灰色），包括圆括号
        val bracketStart = fullText.lastIndexOf('(')
        val bracketEnd = fullText.lastIndexOf(')')
        val titleStart = bracketStart // 包括 "("
        val titleEnd = bracketEnd + 1 // 包括 ")"
        spannable.setSpan(
            StyleSpan(Typeface.ITALIC),
            titleStart,
            titleEnd,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        spannable.setSpan(
            RelativeSizeSpan(0.75f), // 12sp / 16sp = 0.75，更小
            titleStart,
            titleEnd,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        spannable.setSpan(
            ForegroundColorSpan(Color.parseColor("#666666")),
            titleStart,
            titleEnd,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        val tvBody = TextView(this).apply {
            text = spannable
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(Color.parseColor("#111111"))
        }

        block.addView(tvBody)
        llTranslationResults.addView(block)
    }

    private fun renderTokens(tokens: List<Token>) {
        lastTokens = tokens
        selectedTokenIndex = -1
        selectedTokenView = null

        for ((idx, t) in tokens.withIndex()) {
            val chip = makeTokenChip(t)
            chip.setOnClickListener {
                showDictionaryForToken(idx)
            }
            flTokens.addView(chip)
        }
    }

    private fun makeStatusChip(text: String): View {
        val tv = TextView(this)
        tv.text = text
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        tv.setTextColor(Color.parseColor("#666666"))
        tv.setPadding(dp(10), dp(6), dp(10), dp(6))
        val bg = GradientDrawable().apply {
            cornerRadius = dpF(16)
            setColor(Color.parseColor("#F6F7F8"))
            setStroke(dp(1), Color.parseColor("#11000000"))
        }
        tv.background = bg
        tv.layoutParams = ViewGroup.MarginLayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        return tv
    }

    private fun makeTranslationStatusView(text: String): View {
        val tv = TextView(this)
        tv.text = text
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        tv.setTextColor(Color.parseColor("#666666"))
        return tv
    }

    private fun makeTokenChip(t: Token): View {
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM
            // token的pad
            setPadding(dp(0), dp(2), dp(0), dp(2))
            minimumHeight = dp(45)
        }

        val ruby = buildRubyTextOrNull(t)
        if (ruby != null) {
            val tvRuby = TextView(this).apply {
                text = ruby
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
                setTextColor(Color.parseColor("#666666"))
                includeFontPadding = false
                setLineSpacing(0f, 0.9f)
            }
            wrap.addView(tvRuby)
        }

        val wordContainer = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            // 分词字的pad
            setPadding(dp(1), dp(2), dp(1), dp(2))
            background = GradientDrawable().apply {
                cornerRadius = 0f
                setColor(applyAlpha(colorForWordClass(t.wordclass), 0.5f))
            }
        }

        val tvWord = TextView(this).apply {
            text = t.word
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(Color.parseColor("#111111"))
            includeFontPadding = false
            setLineSpacing(0f, 0.9f)
        }
        wordContainer.addView(tvWord)
        wrap.addView(wordContainer)

        wrap.layoutParams = ViewGroup.MarginLayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            leftMargin = 0
            rightMargin = 0
            topMargin = 0
            bottomMargin = 0
        }

        return wrap
    }

    private fun buildRubyTextOrNull(t: Token): String? {
        val kana = t.kana ?: return null
        if (t.isDeli) return null
        if (kana.isBlank()) return null
        if (isKanaEquivalent(t.word, kana)) return null
        return if (isAllKatakana(kana)) katakanaToHiragana(kana) else kana
    }

    private fun isAllKatakana(s: String): Boolean {
        if (s.isBlank()) return false
        for (ch in s) {
            val code = ch.code
            val isKana = code in 0x30A0..0x30FF
            val ok = isKana || ch == '・' || ch == 'ー' || ch == ' '
            if (!ok) return false
        }
        return true
    }

    private fun colorForWordClass(wordClass: String?): String {
        return when (wordClass) {
            "形容詞" -> "#eedbff"
            "形状詞" -> "#d3e8ff"
            "副詞" -> "#d9c392"
            "名詞" -> "#fbe0a4"
            "代名詞" -> "#e4deca"
            "動詞" -> "#e9f8c7"
            "助詞" -> "#dcfaf8"
            "助動詞" -> "#f5d7e2"
            "感動詞" -> "#bee0c7"
            "接頭辞" -> "#fbfaf7"
            "接尾辞" -> "#a0cdca"
            "接続詞" -> "#d9b29f"
            "指示詞" -> "#87b0d1"
            "連体詞" -> "#dae2e5"
            "判定詞" -> "#7fff00"
            "補助記号" -> "#ffffff"
            "記号" -> "#ffffff"
            "空白" -> "#ffffff"
            else -> "#ffffff"
        }
    }

    private fun dp(v: Int): Int = (resources.displayMetrics.density * v).toInt()
    private fun dpF(v: Int): Float = resources.displayMetrics.density * v

    private fun applyAlpha(color: String, alpha: Float): Int {
        val c = Color.parseColor(color)
        val a = (255 * alpha).toInt().coerceIn(0, 255)
        return (c and 0x00FFFFFF) or (a shl 24)
    }

    private fun isKanaEquivalent(word: String, kana: String): Boolean {
        if (word.isBlank() || kana.isBlank()) return false
        return toKatakana(word) == toKatakana(kana)
    }

    private fun toKatakana(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            val code = ch.code
            if (code in 0x3041..0x3096) sb.append((code + 0x60).toChar()) else sb.append(ch)
        }
        return sb.toString()
    }

    private fun scheduleDictionaryQuery(word: String) {
        pendingDictQuery?.let { mainHandler.removeCallbacks(it) }
        val task = Runnable {
            val w = word.trim()
            if (w.isEmpty() || dictSheet?.isShowing != true) return@Runnable
            requestDictionaryAll(w)
        }
        pendingDictQuery = task
        mainHandler.postDelayed(task, dictDebounceDelayMs)
    }

    private fun showDictionaryForToken(index: Int) {
        if (index !in lastTokens.indices) return
        selectedTokenIndex = index

        highlightSelectedToken()

        val w = lastTokens[index].word
        showDictionarySheet(w)
    }

    private fun hideDictionary() {
        dictSheet?.dismiss()
    }

    private fun highlightSelectedToken() {
        clearSelectedTokenHighlight()
        val v = flTokens.getChildAt(selectedTokenIndex) ?: return
        selectedTokenView = v
        v.alpha = 0.9f
    }

    private fun clearSelectedTokenHighlight() {
        selectedTokenView?.alpha = 1.0f
        selectedTokenView = null
    }

    private fun appendNextTokenToQuery() {
        val idx = selectedTokenIndex
        if (idx !in lastTokens.indices) return
        val nextIdx = idx + 1
        if (nextIdx !in lastTokens.indices) return

        val current = etDictQuery?.text?.toString().orEmpty()
        val appended = current + lastTokens[nextIdx].word
        etDictQuery?.setText(appended)
        etDictQuery?.setSelection(appended.length)
        scheduleDictionaryQuery(appended)

        selectedTokenIndex = nextIdx
        highlightSelectedToken()
    }

    private fun updateTranslationVisibility(hasContent: Boolean) {
        svTranslation.visibility = if (hasContent) View.VISIBLE else View.GONE
        flBottomPanel.visibility = if (hasContent) View.VISIBLE else View.GONE
    }

    private fun requestDictionaryAll(word: String) {
        cgDictTabs?.removeAllViews()
        dictEntries = emptyList()
        selectedDictId = null
        wvDict?.loadDataWithBaseURL(null, wrapHtml("加载中..."), "text/html", "UTF-8", null)

        val urlStr = "$apiBaseUrl/api/dictionary?word=${URLEncoder.encode(word, "UTF-8")}" 
        httpExecutor.execute {
            val result = runCatching { fetchDictionaryStream(urlStr) }
            if (result.isFailure) {
                mainHandler.post {
                    wvDict?.loadDataWithBaseURL(null, wrapHtml("查询失败"), "text/html", "UTF-8", null)
                }
            }
        }
    }

    private fun fetchDictionaryStream(urlStr: String) {
        val url = URL(urlStr)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5000
            readTimeout = 20000
        }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val contentType = conn.contentType ?: ""

        if (code !in 200..299) {
            val body = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
            conn.disconnect()
            throw RuntimeException("HTTP $code: $body")
        }

        if (contentType.contains("application/json")) {
            val body = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
            conn.disconnect()
            val obj = JSONObject(body)
            val id = obj.optString("id", "")
            val name = obj.optString("name", id)
            val result = obj.optString("result", "")
            if (id.isBlank() || result.isBlank()) return
            mainHandler.post { onDictionaryEntry(DictEntry(id, name, result)) }
            return
        }

        val reader = BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
        while (true) {
            val line = reader.readLine() ?: break
            val trimmed = line.trim()
            if (!trimmed.startsWith("data:")) continue
            val jsonStr = trimmed.removePrefix("data:").trim()
            if (jsonStr.isBlank()) continue

            val obj = runCatching { JSONObject(jsonStr) }.getOrNull() ?: continue
            val id = obj.optString("id", "")
            val name = obj.optString("name", id)
            val result = obj.optString("result", "")
            if (id.isBlank() || result.isBlank()) continue

            mainHandler.post { onDictionaryEntry(DictEntry(id, name, result)) }
        }
        conn.disconnect()
    }

    private fun onDictionaryEntry(entry: DictEntry) {
        if (dictEntries.any { it.id == entry.id }) return
        dictEntries = dictEntries + entry

        val chip = Chip(this).apply {
            text = entry.name
            tag = entry.id
            isCheckable = true
            setOnClickListener {
                selectDictionary(entry.id)
            }
        }
        cgDictTabs?.addView(chip)

        if (selectedDictId == null) {
            selectDictionary(entry.id)
        }
    }

    private fun selectDictionary(id: String) {
        selectedDictId = id
        val tabs = cgDictTabs
        if (tabs != null) {
            for (i in 0 until tabs.childCount) {
                val c = tabs.getChildAt(i)
                if (c is Chip) c.isChecked = (c.tag == id)
            }
        }
        val entry = dictEntries.firstOrNull { it.id == id }
        if (entry == null) {
            wvDict?.loadDataWithBaseURL(null, wrapHtml("无结果"), "text/html", "UTF-8", null)
            return
        }
        wvDict?.loadDataWithBaseURL(apiBaseUrl, wrapHtml(entry.resultHtml), "text/html", "UTF-8", null)
    }

    private fun wrapHtml(body: String): String {
        return """
<!DOCTYPE html>
<html>
<head>
<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"/>
<style>
body{font-family:sans-serif;margin:0;padding:12px;line-height:1.5;color:#111;}
img{max-width:100%;height:auto;}
</style>
</head>
<body>
$body
</body>
</html>
""".trimIndent()
    }

    private fun katakanaToHiragana(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            val code = ch.code
            if (code in 0x30A0..0x30FF) sb.append((code - 0x60).toChar()) else sb.append(ch)
        }
        return sb.toString()
    }
}
