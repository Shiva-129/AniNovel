package eu.kanade.tachiyomi.di

import android.app.Application
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import data.History
import data.Novel_chapters
import data.Novels
import data.Mangas
import dataanime.Animehistory
import dataanime.Animes
import eu.kanade.domain.track.anime.store.DelayedAnimeTrackingStore
import eu.kanade.domain.track.manga.store.DelayedMangaTrackingStore
import eu.kanade.domain.source.service.SourcePreferences
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import mihon.domain.extensionrepo.anime.interactor.CreateAnimeExtensionRepo
import mihon.domain.extensionrepo.anime.repository.AnimeExtensionRepoRepository
import mihon.domain.extensionrepo.manga.interactor.CreateMangaExtensionRepo
import mihon.domain.extensionrepo.manga.repository.MangaExtensionRepoRepository
import eu.kanade.novel.data.NovelPreferences
import eu.kanade.novel.providers.NovelProviderManager
import tachiyomi.data.handlers.novel.AndroidNovelDatabaseHandler
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.data.repository.novel.NovelRepository
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.cache.AnimeBackgroundCache
import eu.kanade.tachiyomi.data.cache.AnimeCoverCache
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.cache.MangaCoverCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadProvider
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadCache
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadProvider
import eu.kanade.tachiyomi.data.saver.ImageSaver
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.network.JavaScriptEngine
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.anime.AndroidAnimeSourceManager
import eu.kanade.tachiyomi.source.manga.AndroidMangaSourceManager
import eu.kanade.tachiyomi.ui.player.ExternalIntents
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import nl.adaptivity.xmlutil.XmlDeclMode.Charset
import nl.adaptivity.xmlutil.core.XmlVersion
import nl.adaptivity.xmlutil.serialization.XML
import tachiyomi.core.common.storage.AndroidStorageFolderProvider
import tachiyomi.data.AnimeUpdateStrategyColumnAdapter
import tachiyomi.data.Database
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.FetchTypeColumnAdapter
import tachiyomi.data.MangaUpdateStrategyColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.handlers.anime.AndroidAnimeDatabaseHandler
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.data.handlers.manga.AndroidMangaDatabaseHandler
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.mi.data.AnimeDatabase
import tachiyomi.source.local.entries.anime.LocalAnimeFetchTypeManager
import tachiyomi.source.local.image.anime.LocalAnimeBackgroundManager
import tachiyomi.source.local.image.anime.LocalAnimeCoverManager
import tachiyomi.source.local.image.anime.LocalEpisodeThumbnailManager
import tachiyomi.source.local.image.manga.LocalMangaCoverManager
import tachiyomi.source.local.io.anime.LocalAnimeSourceFileSystem
import tachiyomi.source.local.io.manga.LocalMangaSourceFileSystem
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get

class AppModule(val app: Application) : InjektModule {

    override fun InjektRegistrar.registerInjectables() {
        addSingleton(app)

        val sqlDriverManga = AndroidSqliteDriver(
            schema = Database.Schema,
            context = app,
            name = "tachiyomi.db",
            factory = if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Support database inspector in Android Studio
                FrameworkSQLiteOpenHelperFactory()
            } else {
                RequerySQLiteOpenHelperFactory()
            },
            callback = object : AndroidSqliteDriver.Callback(Database.Schema) {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    setPragma(db, "foreign_keys = ON")
                    setPragma(db, "journal_mode = WAL")
                    setPragma(db, "synchronous = NORMAL")
                }
                private fun setPragma(db: SupportSQLiteDatabase, pragma: String) {
                    val cursor = db.query("PRAGMA $pragma")
                    cursor.moveToFirst()
                    cursor.close()
                }
            },
        )

        val sqlDriverAnime = AndroidSqliteDriver(
            schema = AnimeDatabase.Schema,
            context = app,
            name = "tachiyomi.animedb",
            factory = if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Support database inspector in Android Studio
                FrameworkSQLiteOpenHelperFactory()
            } else {
                RequerySQLiteOpenHelperFactory()
            },
            callback = object : AndroidSqliteDriver.Callback(AnimeDatabase.Schema) {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    setPragma(db, "foreign_keys = ON")
                    setPragma(db, "journal_mode = WAL")
                    setPragma(db, "synchronous = NORMAL")
                }
                private fun setPragma(db: SupportSQLiteDatabase, pragma: String) {
                    val cursor = db.query("PRAGMA $pragma")
                    cursor.moveToFirst()
                    cursor.close()
                }
            },
        )

        addSingletonFactory {
            Database(
                driver = sqlDriverManga,
                historyAdapter = History.Adapter(
                    last_readAdapter = DateColumnAdapter,
                ),
                mangasAdapter = Mangas.Adapter(
                    genreAdapter = StringListColumnAdapter,
                    update_strategyAdapter = MangaUpdateStrategyColumnAdapter,
                ),
                novel_chaptersAdapter = Novel_chapters.Adapter(
                    download_statusAdapter = tachiyomi.data.NovelDownloadStateColumnAdapter,
                ),
                novelsAdapter = Novels.Adapter(
                    genreAdapter = StringListColumnAdapter,
                ),
            )
        }

        addSingletonFactory {
            AnimeDatabase(
                driver = sqlDriverAnime,
                animehistoryAdapter = Animehistory.Adapter(
                    last_seenAdapter = DateColumnAdapter,
                ),
                animesAdapter = Animes.Adapter(
                    genreAdapter = StringListColumnAdapter,
                    update_strategyAdapter = AnimeUpdateStrategyColumnAdapter,
                    fetch_typeAdapter = FetchTypeColumnAdapter,
                ),
            )
        }

        addSingletonFactory<MangaDatabaseHandler> {
            AndroidMangaDatabaseHandler(
                get(),
                sqlDriverManga,
            )
        }

        addSingletonFactory<AnimeDatabaseHandler> {
            AndroidAnimeDatabaseHandler(
                get(),
                sqlDriverAnime,
            )
        }

        addSingletonFactory {
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            }
        }
        addSingletonFactory {
            XML {
                defaultPolicy {
                    ignoreUnknownChildren()
                }
                autoPolymorphic = true
                xmlDeclMode = Charset
                indent = 2
                xmlVersion = XmlVersion.XML10
            }
        }
        addSingletonFactory<ProtoBuf> {
            ProtoBuf
        }

        addSingletonFactory { ChapterCache(app, get()) }

        addSingletonFactory { MangaCoverCache(app) }
        addSingletonFactory { AnimeCoverCache(app) }
        addSingletonFactory { AnimeBackgroundCache(app) }

        addSingletonFactory { NetworkHelper(app, get()) }
        addSingletonFactory { JavaScriptEngine(app) }

        addSingletonFactory<MangaSourceManager> { AndroidMangaSourceManager(app, get(), get()) }
        addSingletonFactory<AnimeSourceManager> { AndroidAnimeSourceManager(app, get(), get()) }

        addSingletonFactory { MangaExtensionManager(app) }
        addSingletonFactory { AnimeExtensionManager(app) }

        addSingletonFactory { MangaDownloadProvider(app) }
        addSingletonFactory { MangaDownloadManager(app) }
        addSingletonFactory { MangaDownloadCache(app) }

        addSingletonFactory { AnimeDownloadProvider(app) }
        addSingletonFactory { AnimeDownloadManager(app) }
        addSingletonFactory { AnimeDownloadCache(app) }

        addSingletonFactory { TrackerManager(app) }
        addSingletonFactory { DelayedAnimeTrackingStore(app) }
        addSingletonFactory { DelayedMangaTrackingStore(app) }

        addSingletonFactory { ImageSaver(app) }

        addSingletonFactory { AndroidStorageFolderProvider(app) }

        addSingletonFactory { LocalMangaSourceFileSystem(get()) }
        addSingletonFactory { LocalMangaCoverManager(app, get()) }

        addSingletonFactory { LocalAnimeSourceFileSystem(get()) }
        addSingletonFactory { LocalAnimeBackgroundManager(app, get()) }
        addSingletonFactory { LocalAnimeCoverManager(app, get()) }
        addSingletonFactory { LocalAnimeFetchTypeManager(app, get()) }
        addSingletonFactory { LocalEpisodeThumbnailManager(app, get()) }

        addSingletonFactory { StorageManager(app, get()) }

        addSingletonFactory { ExternalIntents() }

        // Novel DB handler — reuses the same manga Database (novels.sq is in it)
        addSingletonFactory<NovelDatabaseHandler> {
            AndroidNovelDatabaseHandler(get<Database>(), sqlDriverManga)
        }
        addSingletonFactory { NovelRepository(get()) }
        addSingletonFactory { NovelPreferences(get()) }

        // Seed default extension repos into DB if none exist yet
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch(Dispatchers.IO) {
            // Small delay to ensure DomainModule has registered all factories
            kotlinx.coroutines.delay(3000)
            val mangaRepoCount = get<MangaExtensionRepoRepository>().getAll().size
                if (mangaRepoCount == 0) {
                    val createMangaRepo = get<CreateMangaExtensionRepo>()
                    listOf(
                        "https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json",
                        "https://raw.githubusercontent.com/yuzono/manga-repo/repo/index.min.json",
                        "https://raw.githubusercontent.com/yuzono/cursed-manga-repo/repo/index.min.json",
                        "https://raw.githubusercontent.com/Kareadita/tach-extension/repo/index.min.json",
                        "https://raw.githubusercontent.com/Suwayomi/tachiyomi-extension/repo/index.min.json",
                        "https://raw.githubusercontent.com/LittleSurvival/copymanga-copy20/repo/index.min.json",
                    ).forEach { createMangaRepo.await(it) }
                }

                val animeRepoCount = get<AnimeExtensionRepoRepository>().getAll().size
                if (animeRepoCount == 0) {
                    val createAnimeRepo = get<CreateAnimeExtensionRepo>()
                    listOf(
                        "https://kohiden.xyz/Kohi-den/extensions/raw/branch/main/index.min.json",
                        "https://raw.githubusercontent.com/yuzono/anime-repo/repo/index.min.json",
                        "https://raw.githubusercontent.com/Secozzi/aniyomi-extensions/refs/heads/repo/index.min.json",
                        "https://raw.githubusercontent.com/Claudemirovsky/cursedyomi-extensions/repo/index.min.json",
                        "https://codeberg.org/hollow/aniyomi-extensions-fr/media/branch/repo/index.min.json",
                    ).forEach { createAnimeRepo.await(it) }
                }
        }

        // Novel module — initialise provider list with the shared OkHttp client
        ContextCompat.getMainExecutor(app).execute {
            val network = get<NetworkHelper>()
            val niceHttp = com.lagradost.nicehttp.Requests(network.client)
            NovelProviderManager.init(niceHttp)
        }

        // Asynchronously init expensive components for a faster cold start
        ContextCompat.getMainExecutor(app).execute {
            get<NetworkHelper>()

            get<MangaSourceManager>()
            get<AnimeSourceManager>()

            get<Database>()
            get<AnimeDatabase>()

            get<MangaDownloadManager>()
            get<AnimeDownloadManager>()
        }
    }
}
