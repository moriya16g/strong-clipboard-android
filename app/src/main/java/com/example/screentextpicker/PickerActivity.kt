package com.example.screentextpicker

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.text.Normalizer

/**
 * 取得したテキストを番号付きで一覧表示し、選んだものを編集欄でまとめて編集・コピーする画面。
 */
class PickerActivity : Activity() {

    companion object {
        const val EXTRA_FRESH = "fresh"
    }

    /** 一覧の 1 行。行分割で同じ文字列が並ぶこともあるので、同一性で区別する（data class にしない）。 */
    private class Entry(val text: String)

    private val entries = ArrayList<Entry>()
    private val selected = HashSet<Entry>()
    private var visible: List<Entry> = emptyList()
    private var sourcePackage: String? = null

    /** 利用者が編集欄を手で書き換えたら true。以降は選択の変更で上書きせず、追記だけする。 */
    private var editorDirty = false
    private var settingEditorText = false

    private lateinit var header: TextView
    private lateinit var query: EditText
    private lateinit var list: ListView
    private lateinit var editor: EditText
    private lateinit var btnToggleAll: Button
    private lateinit var btnSeparator: Button
    private val adapter = EntryAdapter()
    private var selectedColor = Color.TRANSPARENT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_picker)

        header = findViewById(R.id.header)
        query = findViewById(R.id.query)
        list = findViewById(R.id.list)
        editor = findViewById(R.id.editor)
        btnToggleAll = findViewById(R.id.btnToggleAll)
        btnSeparator = findViewById(R.id.btnSeparator)

        val tv = TypedValue()
        if (theme.resolveAttribute(android.R.attr.colorAccent, tv, true)) {
            selectedColor = (tv.data and 0x00FFFFFF) or 0x33000000
        }

        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ -> toggle(visible[position]) }
        list.setOnItemLongClickListener { _, _, position, _ ->
            showEntryMenu(visible[position])
            true
        }

        query.addTextChangedListener(SimpleWatcher { applyFilter() })
        query.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                if (parseNumbers(query.text.toString()) != null) selectByNumbers()
                hideKeyboard()
                true
            } else {
                false
            }
        }

        editor.addTextChangedListener(SimpleWatcher { if (!settingEditorText) editorDirty = true })

        btnToggleAll.setOnClickListener { toggleAll() }
        findViewById<Button>(R.id.btnCopyList).setOnClickListener { copyNumberedList() }
        findViewById<Button>(R.id.btnSelectNumbers).setOnClickListener { selectByNumbers() }
        btnSeparator.setOnClickListener { cycleSeparator() }
        findViewById<Button>(R.id.btnJoinLines).setOnClickListener {
            transformEditor { it.replace(Regex("[ \\t]*\\n[ \\t]*"), "") }
        }
        findViewById<Button>(R.id.btnLinesToSpace).setOnClickListener {
            transformEditor { it.replace(Regex("[ \\t]*\\n[ \\t\\n]*"), " ") }
        }
        findViewById<Button>(R.id.btnTidy).setOnClickListener { transformEditor(::tidy) }
        findViewById<Button>(R.id.btnSelectAllText).setOnClickListener {
            editor.requestFocus()
            editor.selectAll()
        }
        findViewById<Button>(R.id.btnRebuild).setOnClickListener { rebuildEditor() }
        findViewById<Button>(R.id.btnClear).setOnClickListener {
            setEditorText("")
            editorDirty = false
        }
        findViewById<Button>(R.id.btnShare).setOnClickListener { share() }
        findViewById<Button>(R.id.btnCopy).setOnClickListener { copyEditor(close = false) }
        findViewById<Button>(R.id.btnCopyClose).setOnClickListener { copyEditor(close = true) }

        updateSeparatorLabel()
        load()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_FRESH, false)) load()
    }

    // ---- 読み込み・一覧 ----

    private fun load() {
        val capture = CaptureStore.latest
        if (capture == null) {
            Toast.makeText(this, "取得済みのテキストがありません", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        sourcePackage = capture.sourcePackage
        entries.clear()
        capture.items.mapTo(entries) { Entry(it.text) }
        selected.clear()
        editorDirty = false
        setEditorText("")
        query.setText("")
        applyFilter()
    }

    private fun applyFilter() {
        val q = query.text.toString().trim()
        visible = if (q.isEmpty() || parseNumbers(q) != null) {
            entries.toList()
        } else {
            entries.filter { it.text.contains(q, ignoreCase = true) }
        }
        refresh()
    }

    private fun refresh() {
        adapter.notifyDataSetChanged()
        val source = sourcePackage?.let { " · $it" } ?: ""
        val filtered = if (visible.size != entries.size) "（${visible.size}件を表示）" else ""
        header.text = "${entries.size}件$filtered · ${selected.size}件選択中$source"
        btnToggleAll.text = if (selected.isNotEmpty()) "選択解除" else "全選択"
    }

    private fun numberOf(entry: Entry) = entries.indexOf(entry) + 1

    // ---- 選択 ----

    private fun toggle(entry: Entry) {
        val added = selected.add(entry)
        if (!added) selected.remove(entry)
        if (!editorDirty) {
            rebuildEditor()
        } else if (added) {
            appendToEditor(entry.text)
        }
        refresh()
    }

    private fun toggleAll() {
        if (selected.isNotEmpty()) selected.clear() else selected.addAll(visible)
        if (!editorDirty) rebuildEditor()
        refresh()
    }

    private fun selectByNumbers() {
        val numbers = parseNumbers(query.text.toString())
        if (numbers == null) {
            Toast.makeText(this, "番号を「3」「1,4-6」のように入力してください", Toast.LENGTH_SHORT).show()
            return
        }
        val picked = numbers.mapNotNull { entries.getOrNull(it - 1) }
        if (picked.isEmpty()) {
            Toast.makeText(this, "該当する番号がありません（1〜${entries.size}）", Toast.LENGTH_SHORT).show()
            return
        }
        selected.clear()
        selected.addAll(picked)
        editorDirty = false
        rebuildEditor()
        query.setText("")
        hideKeyboard()
        refresh()
        list.setSelection(entries.indexOf(picked.first()))
    }

    /**
     * 「3」「1,4-6」「２、５〜７」のような番号指定を解釈する。番号指定として読めなければ null。
     */
    private fun parseNumbers(input: String): List<Int>? {
        val s = Normalizer.normalize(input, Normalizer.Form.NFKC).trim()
        if (s.isEmpty() || !Regex("[0-9][0-9,、\\s\\-~〜ー]*").matches(s)) return null
        val result = LinkedHashSet<Int>()
        for (token in s.split(Regex("[,、\\s]+")).filter { it.isNotEmpty() }) {
            val range = Regex("(\\d+)[\\-~〜ー](\\d+)").matchEntire(token)
            when {
                range != null -> {
                    val a = range.groupValues[1].toIntOrNull() ?: return null
                    val b = range.groupValues[2].toIntOrNull() ?: return null
                    if (a <= b) (a..b).forEach(result::add) else (a downTo b).forEach(result::add)
                }
                token.all(Char::isDigit) -> result.add(token.toIntOrNull() ?: return null)
                else -> return null
            }
        }
        return result.toList()
    }

    private fun showEntryMenu(entry: Entry) {
        val actions = listOf(
            "この項目だけコピー" to { copy(entry.text); Unit },
            "編集欄の末尾に追加" to { appendToEditor(entry.text); editorDirty = true },
            "行ごとに分割して一覧に追加" to { splitIntoLines(entry) },
            "全文を表示" to { showFullText(entry) },
        )
        AlertDialog.Builder(this)
            .setTitle("${numberOf(entry)}番")
            .setItems(actions.map { it.first }.toTypedArray()) { _, which -> actions[which].second() }
            .show()
    }

    private fun splitIntoLines(entry: Entry) {
        val lines = entry.text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size <= 1) {
            Toast.makeText(this, "1行しかありません", Toast.LENGTH_SHORT).show()
            return
        }
        val at = entries.indexOf(entry)
        entries.addAll(at + 1, lines.map(::Entry))
        applyFilter()
        Toast.makeText(this, "${lines.size}行を ${at + 2}番以降に追加しました", Toast.LENGTH_SHORT).show()
    }

    private fun showFullText(entry: Entry) {
        val view = EditText(this).apply {
            setText(entry.text)
            setTextIsSelectable(true)
            setPadding(48, 24, 48, 24)
        }
        AlertDialog.Builder(this)
            .setTitle("${numberOf(entry)}番")
            .setView(view)
            .setPositiveButton("編集した内容をコピー") { _, _ -> copy(view.text.toString()) }
            .setNegativeButton("閉じる", null)
            .show()
    }

    // ---- 編集欄 ----

    private fun separator() = Prefs.separator(this).value

    private fun rebuildEditor() {
        val text = entries.filter { it in selected }.joinToString(separator()) { it.text }
        setEditorText(text)
        editorDirty = false
    }

    private fun appendToEditor(text: String) {
        val current = editor.text.toString()
        setEditorText(if (current.isEmpty()) text else current + separator() + text)
        editor.setSelection(editor.length())
    }

    private fun setEditorText(text: String) {
        settingEditorText = true
        editor.setText(text)
        settingEditorText = false
    }

    /** 編集欄で範囲選択していればその部分だけ、していなければ全体を変換する。 */
    private fun transformEditor(transform: (String) -> String) {
        val text = editor.text
        var start = minOf(editor.selectionStart, editor.selectionEnd)
        var end = maxOf(editor.selectionStart, editor.selectionEnd)
        if (start < 0 || start == end) {
            start = 0
            end = text.length
        }
        val replaced = transform(text.substring(start, end))
        text.replace(start, end, replaced)
        editor.setSelection(start, start + replaced.length)
        editorDirty = true
    }

    private fun tidy(s: String): String =
        s.lines()
            .joinToString("\n") { it.replace(Regex("[ \\t]+"), " ").trim() }
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()

    private fun cycleSeparator() {
        val values = Separator.entries
        val next = values[(Prefs.separator(this).ordinal + 1) % values.size]
        Prefs.setSeparator(this, next)
        updateSeparatorLabel()
        if (!editorDirty) rebuildEditor()
    }

    private fun updateSeparatorLabel() {
        btnSeparator.text = "区切り: ${Prefs.separator(this).label}"
    }

    // ---- 出力 ----

    private fun currentText(): String {
        val text = editor.text.toString()
        if (text.isNotEmpty()) return text
        return entries.filter { it in selected }.joinToString(separator()) { it.text }
    }

    private fun copyEditor(close: Boolean) {
        val text = currentText()
        if (text.isEmpty()) {
            Toast.makeText(this, "一覧から項目を選ぶか、編集欄に入力してください", Toast.LENGTH_SHORT).show()
            return
        }
        copy(text)
        if (close) finishAndRemoveTask()
    }

    private fun copyNumberedList() {
        val text = visible.joinToString("\n") { entry ->
            val n = numberOf(entry)
            val indent = " ".repeat(n.toString().length + 2)
            "$n. " + entry.text.replace("\n", "\n$indent")
        }
        copy(text)
    }

    private fun copy(text: String) {
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("text", text))
        // Android 13 以降は OS がコピー完了を表示する
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(this, "コピーしました（${text.length}文字）", Toast.LENGTH_SHORT).show()
        }
    }

    private fun share() {
        val text = currentText()
        if (text.isEmpty()) return
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        startActivity(Intent.createChooser(send, null))
    }

    private fun hideKeyboard() {
        getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(query.windowToken, 0)
    }

    private inner class EntryAdapter : BaseAdapter() {
        override fun getCount() = visible.size
        override fun getItem(position: Int): Any = visible[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: layoutInflater.inflate(R.layout.item_text, parent, false)
            val entry = visible[position]
            view.findViewById<TextView>(R.id.number).text = numberOf(entry).toString()
            view.findViewById<TextView>(R.id.text).text = entry.text
            view.setBackgroundColor(if (entry in selected) selectedColor else Color.TRANSPARENT)
            return view
        }
    }

    private class SimpleWatcher(private val onChanged: () -> Unit) : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) = onChanged()
    }
}
