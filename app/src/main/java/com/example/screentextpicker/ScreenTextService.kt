package com.example.screentextpicker

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

/**
 * ユーザー補助ボタン（またはクイック設定タイル）が押されたときだけ画面のテキストを読み取る。
 * イベントは監視しない。
 */
class ScreenTextService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: ScreenTextService? = null
            private set
    }

    private val handler = Handler(Looper.getMainLooper())

    private val buttonCallback = object : AccessibilityButtonController.AccessibilityButtonCallback() {
        override fun onClicked(controller: AccessibilityButtonController) {
            captureAndShow(0)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        accessibilityButtonController.registerAccessibilityButtonCallback(buttonCallback, handler)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        accessibilityButtonController.unregisterAccessibilityButtonCallback(buttonCallback)
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    /** クイック設定パネルを閉じてから取得する（パネル自体を読まないように）。 */
    fun closeShadeAndCapture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        } else {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
        captureAndShow(600)
    }

    fun captureAndShow(delayMs: Long) {
        handler.postDelayed({
            val capture = try {
                TextCollector.collect(this, Prefs.includeOffscreen(this))
            } catch (e: RuntimeException) {
                Toast.makeText(this, "テキストを取得できませんでした: ${e.message}", Toast.LENGTH_LONG).show()
                return@postDelayed
            }
            if (capture.items.isEmpty()) {
                Toast.makeText(this, "この画面からはテキストを取得できませんでした", Toast.LENGTH_SHORT).show()
                return@postDelayed
            }
            CaptureStore.latest = capture
            startActivity(
                Intent(this, PickerActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(PickerActivity.EXTRA_FRESH, true)
            )
        }, delayMs)
    }
}
