package eu.kanade.novel.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.novel.tts.TTSHelper

@Composable
fun TtsControlsBar(
    status: TTSHelper.TTSStatus,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(shadowElevation = 8.dp, modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (status) {
                TTSHelper.TTSStatus.IsRunning -> IconButton(onClick = onPause) {
                    Icon(Icons.Outlined.Pause, contentDescription = "Pause TTS")
                }
                TTSHelper.TTSStatus.IsPaused -> IconButton(onClick = onPlay) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = "Resume TTS")
                }
                TTSHelper.TTSStatus.IsStopped -> IconButton(onClick = onPlay) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = "Start TTS")
                }
            }
            IconButton(onClick = onStop) {
                Icon(Icons.Outlined.Stop, contentDescription = "Stop TTS")
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Outlined.SkipNext, contentDescription = "Next chapter")
            }
        }
    }
}
