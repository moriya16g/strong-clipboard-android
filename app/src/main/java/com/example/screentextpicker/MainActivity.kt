package com.example.screentextpicker

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** 初期設定の案内と設定項目。 */
class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var openLast: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        status = TextView(this).apply { textSize = 16f }
        root.addView(status)

        root.addView(button("① ユーザー補助の設定を開く") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        root.addView(button("「制限付き設定」でオンにできないとき：アプリ情報を開く") {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            )
        })

        root.addView(CheckBox(this).apply {
            text = "画面外（スクロールしないと見えない部分）のテキストも含める"
            isChecked = Prefs.includeOffscreen(this@MainActivity)
            setOnCheckedChangeListener { _, checked -> Prefs.setIncludeOffscreen(this@MainActivity, checked) }
        })

        openLast = button("前回取得したテキストを開く") {
            startActivity(Intent(this, PickerActivity::class.java))
        }
        root.addView(openLast)

        root.addView(TextView(this).apply {
            textSize = 14f
            setPadding(0, pad, 0, 0)
            text = HELP
        })

        setContentView(ScrollView(this).apply {
            fitsSystemWindows = true
            addView(root, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        })
    }

    override fun onResume() {
        super.onResume()
        status.text = if (ScreenTextService.instance != null) {
            "✅ 有効です。ユーザー補助ボタンかクイック設定タイルで使えます。"
        } else {
            "⚠ ユーザー補助サービスが無効です。下のボタンから「画面テキスト取得」をオンにしてください。"
        }
        openLast.isEnabled = CaptureStore.latest != null
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    private companion object {
        const val HELP = """使い方

1. 「ユーザー補助の設定を開く」→「インストール済みのアプリ」→「画面テキスト取得」をオンにします。
   ショートカット（ユーザー補助ボタン）もオンにすると、画面端に呼び出しボタンが出ます。

2. テキストを取りたい画面で、ユーザー補助ボタンを押します。
   （または、クイック設定パネルに「画面テキスト」タイルを追加して押します）

3. 画面内のテキストが番号付きで一覧表示されます。
   ・タップで選択／解除。選んだ順ではなく番号順に下の編集欄へ入ります
   ・上の欄に「3」「1,4-6」と入れて「番号で選択」でもまとめて選べます
   ・番号以外の文字を入れると一覧を絞り込みます
   ・長押しで「この項目だけコピー」「行ごとに分割」などができます
   ・「番号付き一覧をコピー」で一覧全体をクリップボードへ送れます

4. 編集欄で自由に直してから「コピー」または「コピーして閉じる」。
   「改行を削除」「改行→空白」「空白を整理」は、編集欄で範囲選択していればその部分だけに効きます。

※ Android 13 以降でアプリストア以外から入れた場合、ユーザー補助をオンにしようとすると「制限付き設定」と表示されることがあります。
   その場合は「アプリ情報を開く」→ 右上の︙ →「制限付き設定を許可」を押してから、もう一度オンにしてください。

※ 読み取ったテキストはこの端末のメモリ上だけで扱い、保存や外部送信はしません。パスワード欄は読み取りません。
※ 画像として描かれている文字（写真・一部のゲームや PDF ビューアなど）は取得できません。その場合は Google レンズ等の OCR を使ってください。"""
    }
}
