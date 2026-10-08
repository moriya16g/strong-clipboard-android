package com.example.screentextpicker

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/** 画面から取り出したテキスト 1 件分。 */
data class TextItem(val text: String)

/** 1 回の取得結果。 */
data class Capture(val sourcePackage: String?, val items: List<TextItem>)

object TextCollector {

    /**
     * 表示中のアプリのウィンドウを走査してテキストを集める。
     * ツリーを前順で辿るので、おおむね画面の読み順（上から下）に並ぶ。
     */
    fun collect(service: AccessibilityService, includeOffscreen: Boolean): Capture {
        val ownPackage = service.packageName
        val roots = service.windows
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .mapNotNull { it.root }
            .filter { it.packageName?.toString() != ownPackage }
            .ifEmpty { listOfNotNull(service.rootInActiveWindow) }

        val seen = HashSet<String>()
        val items = ArrayList<TextItem>()
        for (root in roots) {
            walk(root, includeOffscreen) { raw ->
                val text = normalize(raw)
                if (text.isNotEmpty() && seen.add(text)) items.add(TextItem(text))
            }
        }
        return Capture(roots.firstOrNull()?.packageName?.toString(), items)
    }

    private fun walk(
        root: AccessibilityNodeInfo,
        includeOffscreen: Boolean,
        emit: (String) -> Unit,
    ) {
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            if (!includeOffscreen && !node.isVisibleToUser) continue
            if (!node.isPassword) textOf(node)?.let(emit)
            // 子を逆順に積んで、取り出し順を元の並び順にする
            for (i in node.childCount - 1 downTo 0) {
                node.getChild(i)?.let { stack.addLast(it) }
            }
        }
    }

    private fun textOf(node: AccessibilityNodeInfo): String? {
        val text = node.text?.toString()
        // 未入力の入力欄はヒント文字列が text として返ることがあるので除外
        if (!text.isNullOrBlank() && !(node.isShowingHintText && text == node.hintText?.toString())) {
            return text
        }
        val desc = node.contentDescription?.toString()
        return if (desc.isNullOrBlank()) null else desc
    }

    /** 改行コードや余分な空白を整える。本文中の単一改行・空行は残す。 */
    fun normalize(s: String): String =
        s.replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace(' ', ' ')
            .replace("​", "")
            .lines()
            .joinToString("\n") { it.trimEnd() }
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
}
