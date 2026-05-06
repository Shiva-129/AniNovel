package eu.kanade.novel.tts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BecomingNoisyReceiver(
    private val event: (TTSHelper.TTSActionType) -> Boolean,
) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        event(TTSHelper.TTSActionType.Pause)
    }
}
