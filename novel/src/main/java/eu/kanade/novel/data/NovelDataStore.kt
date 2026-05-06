package eu.kanade.novel.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule

// ─── Preference keys (migrated from QuickNovel DataStore.kt) ─────────────────

const val NOVEL_PREFS_NAME = "aninovel_novel_prefs"

const val DOWNLOAD_FOLDER = "downloads_data"
const val DOWNLOAD_SIZE = "downloads_size"
const val DOWNLOAD_TOTAL = "downloads_total"
const val DOWNLOAD_OFFSET = "downloads_offset"
const val DOWNLOAD_EPUB_SIZE = "downloads_epub_size"
const val DOWNLOAD_EPUB_LAST_ACCESS = "downloads_epub_last_access"

const val EPUB_LOCK_ROTATION = "reader_epub_rotation"
const val EPUB_TEXT_SIZE = "reader_epub_text_size"
const val EPUB_TEXT_BIONIC = "reader_epub_bionic_reading"
const val EPUB_TEXT_SELECTABLE = "reader_epub_text_selectable"
const val EPUB_SCROLL_VOL = "reader_epub_scroll_volume"
const val EPUB_AUTHOR_NOTES = "reader_epub_author_notes"
const val EPUB_TTS_LOCK = "reader_epub_scroll_lock"
const val EPUB_TTS_SET_SPEED = "reader_epub_tts_speed"
const val EPUB_TTS_SET_PITCH = "reader_epub_tts_pitch"
const val EPUB_BG_COLOR = "reader_epub_bg_color"
const val EPUB_TEXT_COLOR = "reader_epub_text_color"
const val EPUB_TEXT_PADDING = "reader_epub_text_padding"
const val EPUB_TEXT_PADDING_TOP = "reader_epub_text_padding_top"
const val EPUB_TEXT_VERTICAL_PADDING = "reader_epub_vertical_padding"
const val EPUB_HAS_BATTERY = "reader_epub_has_battery"
const val EPUB_KEEP_SCREEN_ACTIVE = "reader_epub_keep_screen_active"
const val EPUB_SLEEP_TIMER = "reader_epub_tts_timer"
const val EPUB_ML_FROM_LANGUAGE = "reader_epub_ml_from"
const val EPUB_ML_TO_LANGUAGE = "reader_epub_ml_to"
const val EPUB_ML_USE_ONLINE = "reader_epub_ml_useOnlineTranslation"
const val EPUB_HAS_TIME = "reader_epub_has_time"
const val EPUB_TWELVE_HOUR_TIME = "reader_epub_twelve_hour_time"
const val EPUB_FONT = "reader_epub_font"
const val EPUB_LANG = "reader_epub_lang"
const val EPUB_VOICE = "reader_epub_voice"
const val EPUB_READER_TYPE = "reader_reader_type"
const val EPUB_CURRENT_POSITION = "reader_epub_position"
const val EPUB_CURRENT_POSITION_SCROLL = "reader_epub_position_scroll"
const val EPUB_CURRENT_POSITION_SCROLL_CHAR = "reader_epub_position_scroll_char"
const val EPUB_CURRENT_POSITION_CHAPTER = "reader_epub_position_chapter"
const val EPUB_CURRENT_ML = "reader_epub_ml"

const val RESULT_CHAPTER_SORT = "result_chapter_sort"
const val RESULT_CHAPTER_FILTER_DOWNLOADED = "result_chapter_filter_download"
const val RESULT_CHAPTER_FILTER_BOOKMARKED = "result_chapter_filter_bookmarked"
const val RESULT_CHAPTER_FILTER_READ = "result_chapter_filter_read"
const val RESULT_CHAPTER_FILTER_UNREAD = "result_chapter_filter_unread"
const val RESULT_BOOKMARK = "result_bookmarked"
const val RESULT_BOOKMARK_STATE = "result_bookmarked_state"
const val HISTORY_FOLDER = "result_history"

// ─── NovelDataStore ───────────────────────────────────────────────────────────

object NovelDataStore {

    val mapper: JsonMapper = JsonMapper.builder()
        .addModule(
            KotlinModule.Builder()
                .withReflectionCacheSize(512)
                .configure(KotlinFeature.NullToEmptyCollection, false)
                .configure(KotlinFeature.NullToEmptyMap, false)
                .configure(KotlinFeature.NullIsSameAsDefault, false)
                .configure(KotlinFeature.SingletonSupport, false)
                .configure(KotlinFeature.StrictNullChecks, false)
                .build()
        )
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .build()

    internal fun Context.prefs(): SharedPreferences =
        getSharedPreferences(NOVEL_PREFS_NAME, Context.MODE_PRIVATE)

    fun Context.getKeys(folder: String): List<String> =
        prefs().all.keys.filter { it.startsWith(folder) }

    fun <T> Context.setKey(path: String, value: T) {
        prefs().edit { putString(path, mapper.writeValueAsString(value)) }
    }

    fun <T> Context.setKey(folder: String, path: String, value: T) =
        setKey("$folder/$path", value)

    internal inline fun <reified T : Any> Context.getKey(path: String, defVal: T? = null): T? {
        return try {
            val json = prefs().getString(path, null) ?: return defVal
            mapper.readValue(json, T::class.java)
        } catch (_: Exception) {
            defVal
        }
    }

    internal inline fun <reified T : Any> Context.getKey(folder: String, path: String, defVal: T? = null): T? =
        getKey("$folder/$path", defVal)

    fun Context.removeKey(path: String) {
        prefs().edit { remove(path) }
    }

    fun Context.removeKey(folder: String, path: String) =
        removeKey("$folder/$path")
}
