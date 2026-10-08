package com.example.screentextpicker

/**
 * 直近の取得結果。内容はメモリ上にだけ置き、ディスクには保存しない。
 * （Intent の extra はサイズ上限があるので、長いページでは使えない）
 */
object CaptureStore {
    @Volatile
    var latest: Capture? = null
}
