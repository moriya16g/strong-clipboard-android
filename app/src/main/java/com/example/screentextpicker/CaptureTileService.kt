package com.example.screentextpicker

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** クイック設定パネルから取得を始めるためのタイル。 */
class CaptureTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        // UNAVAILABLE にするとタップを受け取れず設定画面へ案内できないので、常に INACTIVE にする
        qsTile?.let {
            it.state = Tile.STATE_INACTIVE
            it.updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val service = ScreenTextService.instance
        if (service != null) {
            service.closeShadeAndCapture()
        } else {
            // サービスが無効なら設定画面へ案内する
            openActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openActivity(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
