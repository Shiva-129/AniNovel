package eu.kanade.novel.providers



class FanMtlnProvider : WuxiaBoxProvider()
{
    override val name = "FanMTL"
    override val mainUrl = "https://www.fanmtl.com"
    override val hasMainPage = true
    override val usesCloudFlareKiller = true
}