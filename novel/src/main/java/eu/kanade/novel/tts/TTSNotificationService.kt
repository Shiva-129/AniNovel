package eu.kanade.novel.tts

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Foreground service that keeps TTS alive when the app is backgrounded.
 * Actual TTS control is delegated to TTSSession via the bound ViewModel.
 */
class TTSNotificationService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.getStringExtra("type")) {
            "pause" -> NovelTTSController.pause()
            "resume" -> NovelTTSController.resume()
            "stop" -> { NovelTTSController.stop(); stopSelf() }
            "next" -> NovelTTSController.next()
        }
        return START_NOT_STICKY
    }
}

/**
 * Simple singleton bus so the service can reach the active TTS session
 * without a full bind/unbind cycle.
 */
object NovelTTSController {
    var onPause: (() -> Unit)? = null
    var onResume: (() -> Unit)? = null
    var onStop: (() -> Unit)? = null
    var onNext: (() -> Unit)? = null

    fun pause() { onPause?.invoke() }
    fun resume() { onResume?.invoke() }
    fun stop() { onStop?.invoke() }
    fun next() { onNext?.invoke() }
}
