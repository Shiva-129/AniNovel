package eu.kanade.novel.providers

import com.lagradost.nicehttp.Requests
import eu.kanade.novel.api.MainAPI

/**
 * Registers and provides access to all novel source providers.
 * Providers are lazy-initialised and share a single Requests (NiceHttp) client.
 */
object NovelProviderManager {

    private val _providers = mutableListOf<MainAPI>()
    val providers: List<MainAPI> get() = _providers

    fun init(app: Requests) {
        if (_providers.isNotEmpty()) return
        _providers.addAll(buildProviderList().onEach { it.app = app })
    }

    fun getByName(name: String): MainAPI? = _providers.firstOrNull { it.name == name }

    private fun buildProviderList(): List<MainAPI> = listOf(
        AllNovelProvider(),
        FreewebnovelProvider(),
        HiraethTranslationProvider(),
        LibReadProvider(),
        MeioNovelProvider(),
        MoreNovelProvider(),
        MtlNovelProvider(),
        NovelBinProvider(),
        NovelFullProvider(),
        NovelsOnlineProvider(),
        PawReadProver(),
        ReadfromnetProvider(),
        RoyalRoadProvider(),
    )
}
