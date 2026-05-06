package eu.kanade.novel.tts

import android.content.Context
import android.content.IntentFilter
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import eu.kanade.novel.data.EPUB_LANG
import eu.kanade.novel.data.EPUB_VOICE
import eu.kanade.novel.data.NovelDataStore.getKey
import eu.kanade.novel.data.NovelDataStore.removeKey
import eu.kanade.novel.data.NovelDataStore.setKey
import io.noties.markwon.Markwon
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import org.jsoup.Jsoup
import java.util.Locale
import java.util.Stack
import kotlin.math.roundToInt

class TTSSession(val context: Context, event: (TTSHelper.TTSActionType) -> Boolean) {

    private val intentFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
    private val noisyReceiver = BecomingNoisyReceiver(event)
    private var focusRequest: AudioFocusRequest? = null
    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focus ->
        if (focus != AudioManager.AUDIOFOCUS_GAIN &&
            focus != AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE &&
            focus != AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
        ) {
            event(TTSHelper.TTSActionType.Pause)
        }
    }

    private var isRegistered = false
    private var tts: TextToSpeech? = null
    private var ttsQueue: Pair<TTSHelper.TTSLine, Int>? = null
    private var ttsQueueId = 0
    private var ttsStartSpeakId = 0
    private var ttsEndSpeakId = 0
    private var speed = 1.0f
    private var pitch = 1.0f

    fun setSpeed(speed: Float) { this.speed = speed; tts?.setSpeechRate(speed) }
    fun setPitch(pitch: Float) { this.pitch = pitch; tts?.setPitch(pitch) }

    fun setLanguage(locale: Locale?) {
        val real = locale ?: Locale.US
        context.setKey(EPUB_LANG, real.displayName)
        tts?.let { clearTTS(it); it.language = real }
    }

    fun setVoice(voice: Voice?) {
        if (voice == null) context.removeKey(EPUB_VOICE) else context.setKey(EPUB_VOICE, voice.name)
        tts?.let { clearTTS(it); it.voice = voice ?: it.defaultVoice }
    }

    fun interruptTTS() { tts?.let { clearTTS(it) } }
    fun ttsInitialized() = tts != null

    @Volatile var pendingSkip = 0

    fun skipForward() { pendingSkip++ }
    fun skipBackward() { pendingSkip-- }

    private fun clearTTS(t: TextToSpeech) { t.stop(); ttsQueue = null }

    suspend fun speak(line: TTSHelper.TTSLine, next: TTSHelper.TTSLine?, action: () -> Boolean): Int? {
        val t = requireTTS(action) ?: return null
        val ret = if (ttsQueue?.first == line) {
            ttsQueue!!.second
        } else {
            ttsQueueId++
            t.speak(line.speakOutMsg, TextToSpeech.QUEUE_FLUSH, null, ttsQueueId.toString())
            ttsQueueId
        }
        if (next != null) {
            ttsQueueId++
            ttsQueue = next to ttsQueueId
            t.speak(next.speakOutMsg, TextToSpeech.QUEUE_ADD, null, ttsQueueId.toString())
        }
        return ret
    }

    suspend fun waitForOr(id: Int?, action: () -> Boolean, then: () -> Unit) {
        if (id == null) return
        while (id > ttsEndSpeakId) {
            delay(50)
            if (action()) { interruptTTS(); then(); break }
        }
    }

    private val mutex = Mutex()

    suspend fun requireTTS(action: () -> Boolean = { false }): TextToSpeech? = with(mutex) {
        coroutineScope {
            tts?.let { return@coroutineScope it }
            var waiting = true
            var success = false
            val pending = TextToSpeech(context.applicationContext) { status ->
                success = status == TextToSpeech.SUCCESS
                waiting = false
            }
            while (waiting && isActive) {
                if (action()) return@coroutineScope null
                delay(100)
            }
            if (!success) return@coroutineScope null

            val voiceName = context.getKey<String>(EPUB_VOICE)
            val langName = context.getKey<String>(EPUB_LANG)
            val lang = pending.availableLanguages?.firstOrNull { it.displayName == langName } ?: Locale.US
            val result = pending.setLanguage(lang)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                pending.shutdown(); return@coroutineScope null
            }
            pending.voice = pending.voices?.firstOrNull { it.name == voiceName } ?: pending.defaultVoice
            pending.setPitch(pitch)
            pending.setSpeechRate(speed)
            pending.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onDone(id: String) { id.toIntOrNull()?.let { ttsEndSpeakId = maxOf(ttsEndSpeakId, it) } }
                @Deprecated("Deprecated") override fun onError(id: String?) { id?.toIntOrNull()?.let { ttsEndSpeakId = maxOf(ttsEndSpeakId, it) } }
                override fun onStart(id: String) { id.toIntOrNull()?.let { ttsStartSpeakId = maxOf(ttsStartSpeakId, it) } }
            })
            tts = pending
            pending
        }
    }

    fun register() {
        if (isRegistered) return
        isRegistered = true
        context.registerReceiver(noisyReceiver, intentFilter)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
                .requestAudioFocus(focusRequest!!)
        }
    }

    fun release() {
        tts?.apply { stop(); setOnUtteranceProgressListener(null); shutdown() }
        tts = null; ttsQueue = null
        unregister()
    }

    fun unregister() {
        if (!isRegistered) return
        isRegistered = false
        context.unregisterReceiver(noisyReceiver)
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).run {
                setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build())
                setAcceptsDelayedFocusGain(true)
                setOnAudioFocusChangeListener(audioFocusListener)
                build()
            }
        }
    }
}

// ─── Span types ───────────────────────────────────────────────────────────────

fun generateTTSId(type: Long, index: Int, start: Int, end: Int): Long {
    val typeBits = type and ((1 shl 5) - 1)
    val indexBits = index.toLong() and ((1 shl 17) - 1)
    val startBits = start.toLong() and ((1 shl 23) - 1)
    val endBits = end.toLong() and ((1 shl 23) - 1)
    return typeBits or (indexBits shl 4) or (startBits shl 20) or (endBits shl 42)
}

abstract class SpanDisplay {
    val id by lazy { id() }
    abstract val index: Int
    abstract val innerIndex: Int
    protected abstract fun id(): Long
}

data class TextSpan(
    val text: Spanned,
    val start: Int,
    val end: Int,
    override val index: Int,
    override var innerIndex: Int,
) : SpanDisplay() {
    val bionicText: Spanned by lazy {
        val wordToSpan: Spannable = SpannableString(text)
        val length = wordToSpan.length
        Regex("([a-zà-ýA-ZÀ-ÝåäöÅÄÖ].*?)[^a-zà-ýA-ZÀ-ÝåäöÅÄÖ'']").findAll(text).forEach { match ->
            val range = match.groups[1]!!.range
            val rangeLength = range.last + 1 - range.first
            val correctLength = when (rangeLength) {
                0 -> return@forEach
                1, 2, 3 -> 1
                4 -> 2
                else -> (rangeLength.toFloat() * 0.4).roundToInt()
            }
            wordToSpan.setSpan(
                StyleSpan(Typeface.BOLD),
                minOf(maxOf(match.range.first, 0), length),
                minOf(maxOf(match.range.first + correctLength, 0), length),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        wordToSpan
    }
    override fun id() = generateTTSId(0, index, start, end)
}

data class ChapterStartSpanned(override val index: Int, override val innerIndex: Int, val name: String, val canReload: Boolean) : SpanDisplay() {
    override fun id() = generateTTSId(1, index, 0, 0)
}

data class LoadingSpanned(val url: String?, override val index: Int) : SpanDisplay() {
    override val innerIndex = 0
    override fun id() = generateTTSId(2, index, 0, 0)
}

data class FailedSpanned(val reason: String, override val index: Int, val cause: Throwable?) : SpanDisplay() {
    override val innerIndex = 0
    override fun id() = generateTTSId(3, index, 0, 0)
}

data class ChapterLoadSpanned(override val index: Int, override val innerIndex: Int, val loadIndex: Int, val name: String) : SpanDisplay() {
    override fun id() = generateTTSId(4, loadIndex, 0, 0)
}

data class ChapterOverscrollSpanned(override val index: Int, override val innerIndex: Int, val loadIndex: Int, val name: String) : SpanDisplay() {
    override fun id() = generateTTSId(5, loadIndex, 0, 0)
}

// ─── TTSHelper ────────────────────────────────────────────────────────────────

object TTSHelper {

    data class TTSLine(val speakOutMsg: String, val startChar: Int, val endChar: Int, val index: Int)

    enum class TTSStatus { IsRunning, IsPaused, IsStopped }
    enum class TTSActionType { Pause, Resume, Stop, Next }

    fun preParseHtml(text: String, authorNotes: Boolean): String {
        val document = Jsoup.parse(text)
        document.select("style").remove()
        document.select("script").remove()
        document.select("img").removeAttr("alt")
        val titleEl = document.selectFirst("title")
        if (titleEl != null && Regex("^(/|[a-zA-Z]:[\\\\/]).*").matches(titleEl.text().trim())) {
            titleEl.remove()
        }
        if (!authorNotes) document.select("div.qnauthornotecontainer").remove()
        return document.html()
            .replace("</td>", " </td>")
            .replace("</tr>", "<br/></tr>")
            .replace("...", "…")
            .replace("<p>.*<strong>Translator:.*?Editor:.*>".toRegex(), "")
            .replace("<.*?Translator:.*?Editor:.*?>".toRegex(), "")
    }

    fun render(html: String, markwon: Markwon): Spanned = markwon.render(markwon.parse(html))

    fun parseTextToSpans(render: Spanned, index: Int): ArrayList<TextSpan> = parseSpan(render, index)

    private fun parseSpan(unsegmented: Spanned, index: Int): ArrayList<TextSpan> {
        val spans = ArrayList<TextSpan>()
        var currentOffset = 0
        var innerIndex = 0
        var nextIndex = unsegmented.indexOf('\n')
        while (nextIndex != -1) {
            if (currentOffset != nextIndex) {
                val text = unsegmented.subSequence(currentOffset, nextIndex) as Spanned
                if (!text.isBlank()) { spans.add(TextSpan(text, currentOffset, nextIndex, index, innerIndex)); innerIndex++ }
            }
            currentOffset = nextIndex + 1
            nextIndex = unsegmented.indexOf('\n', currentOffset)
        }
        val text = unsegmented.subSequence(currentOffset, unsegmented.length) as Spanned
        if (currentOffset != unsegmented.length && !text.isBlank())
            spans.add(TextSpan(text, currentOffset, unsegmented.length, index, innerIndex))
        return spans
    }

    fun ttsParseText(text: String, tag: Int): ArrayList<TTSLine> {
        val cleanText = text
            .replace("\\.([A-z])".toRegex(), ",$1")
            .replace("([.:])([0-9])".toRegex(), ",$2")
            .replace("(^|[ \"''])(Dr|Mr|Mrs)\\. ([A-Z])".toRegex(), "$1$2, $3")

        val lines = ArrayList<TTSLine>()
        val invalidStart = arrayOf(' ', '.', ',', '\n', '"', '\'', '\u2018', '\u2019', '\u201C', '\u201D', '«', '»', '「', '」', '…', '[', ']')
        val endings = arrayOf(".", "\n", ";", "?", ":")
        var idx = 0
        while (idx < text.length) {
            while (idx < text.length && invalidStart.contains(text[idx])) idx++
            if (idx >= text.length) break
            var endIdx = Int.MAX_VALUE
            for (e in endings) {
                val i = cleanText.indexOf(e, idx)
                if (i != -1 && i < endIdx) endIdx = i + 1
            }
            if (endIdx > text.length) endIdx = text.length
            while (endIdx > 0 && endIdx <= text.length && text[endIdx - 1] == '\n') endIdx--
            try {
                var msg = text.substring(idx, endIdx)
                for (c in arrayOf("-", "<", ">", "_", "^", "«", "»", "「", "」", "—", "–", "¿", "*", "~", "\u200c"))
                    msg = msg.replace(c, " ")
                msg = msg.replace("...", " ")
                if (msg.replace("\n", "").replace("\t", "").replace(".", "").isNotEmpty() &&
                    msg.isNotEmpty() && msg.isNotBlank() && msg.contains("[A-z0-9]".toRegex())
                ) {
                    lines.add(TTSLine(msg, idx, endIdx, tag))
                }
            } catch (_: Throwable) { break }
            idx = endIdx
            if (text.getOrNull(idx)?.isWhitespace() == true) idx++
        }
        return lines
    }
}
