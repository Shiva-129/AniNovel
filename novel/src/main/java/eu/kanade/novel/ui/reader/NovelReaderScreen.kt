package eu.kanade.novel.ui.reader

import android.graphics.Color
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.KeyEvent
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.NestedScrollView
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.novel.tts.TTSHelper
import io.noties.markwon.Markwon
import io.noties.markwon.SoftBreakAddsNewLinePlugin
import io.noties.markwon.html.HtmlPlugin

data class NovelReaderScreen(
    val novelUrl: String,
    val apiName: String,
    val chapterIndex: Int,
) : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val model = rememberScreenModel { NovelReaderViewModel(novelUrl, apiName, chapterIndex) }
        val state by model.state.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current

        val markwon = remember {
            Markwon.builder(context)
                .usePlugin(HtmlPlugin.create { plugin -> plugin.excludeDefaults(false) })
                .usePlugin(SoftBreakAddsNewLinePlugin.create())
                .build()
        }

        val chapterTitle = state.chapters.getOrNull(state.chapterIndex)?.name ?: "Chapter"

        var textViewRef by remember { mutableStateOf<TextView?>(null) }
        var scrollViewRef by remember { mutableStateOf<NestedScrollView?>(null) }

        // Volume key interception for TTS skip
        val activity = context as? android.app.Activity
        DisposableEffect(Unit) {
            val originalCallback = activity?.window?.callback
            if (activity != null && originalCallback != null) {
                activity.window.callback = object : android.view.Window.Callback by originalCallback {
                    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                        if (event.action == KeyEvent.ACTION_DOWN &&
                            model.state.value.ttsStatus == TTSHelper.TTSStatus.IsRunning
                        ) {
                            when (event.keyCode) {
                                KeyEvent.KEYCODE_VOLUME_DOWN -> { model.skipForward(); return true }
                                KeyEvent.KEYCODE_VOLUME_UP -> { model.skipBackward(); return true }
                            }
                        }
                        return originalCallback.dispatchKeyEvent(event)
                    }
                }
            }
            onDispose {
                if (activity != null && originalCallback != null) {
                    activity.window.callback = originalCallback
                }
            }
        }

        // Apply TTS highlight whenever ttsLine changes
        LaunchedEffect(state.ttsLine) {
            val tv = textViewRef ?: return@LaunchedEffect
            val line = state.ttsLine
            val text = tv.text
            if (text is Spannable) {
                text.getSpans(0, text.length, BackgroundColorSpan::class.java).forEach { text.removeSpan(it) }
                text.getSpans(0, text.length, ForegroundColorSpan::class.java).forEach { text.removeSpan(it) }
                if (line != null) {
                    val start = line.startChar.coerceIn(0, text.length)
                    val end = line.endChar.coerceIn(0, text.length)
                    if (start < end) {
                        text.setSpan(BackgroundColorSpan(Color.parseColor("#7B2FBE")), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        text.setSpan(ForegroundColorSpan(Color.WHITE), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        val layout = tv.layout
                        if (layout != null) {
                            val lineNum = layout.getLineForOffset(start)
                            scrollViewRef?.smoothScrollTo(0, layout.getLineTop(lineNum))
                        }
                    }
                }
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(chapterTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
            bottomBar = {
                if (state.ttsStatus != TTSHelper.TTSStatus.IsStopped || state.html != null) {
                    TtsControlsBar(
                        status = state.ttsStatus,
                        onPlay = { model.startTTS(context, markwon) },
                        onPause = { model.pauseTTS() },
                        onStop = { model.stopTTS() },
                        onNext = { model.nextChapter() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
        ) { padding ->
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                state.error != null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(state.error!!, color = MaterialTheme.colorScheme.error)
                }
                else -> {
                    val html = state.html ?: ""
                    val processed = remember(html) { TTSHelper.preParseHtml(html, authorNotes = true) }
                    AndroidView(
                        factory = { ctx ->
                            NestedScrollView(ctx).apply {
                                val tv = TextView(ctx).apply {
                                    textSize = state.fontSize.toFloat()
                                    setLineSpacing(0f, 1.6f)
                                    setPadding(48, 24, 48, 24)
                                }
                                addView(tv)
                                scrollViewRef = this
                                textViewRef = tv
                            }
                        },
                        update = { scrollView ->
                            val tv = scrollView.getChildAt(0) as? TextView ?: return@AndroidView
                            textViewRef = tv
                            scrollViewRef = scrollView
                            tv.textSize = state.fontSize.toFloat()
                            markwon.setMarkdown(tv, processed)
                            if (tv.text !is Spannable) {
                                tv.setText(SpannableString(tv.text), TextView.BufferType.SPANNABLE)
                            }
                        },
                        modifier = Modifier.fillMaxSize().padding(padding),
                    )
                }
            }
        }
    }
}
