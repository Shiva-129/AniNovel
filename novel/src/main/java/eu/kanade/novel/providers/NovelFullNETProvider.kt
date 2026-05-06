package eu.kanade.novel.providers



//It has content that novelfull.com  doesn’t have
class NovelFullNETProvider: AllNovelProvider() {
    override val name = "NovelFull-Net"
    override val mainUrl = "https://novelfull.net"
    override val hasMainPage = false
    override val requireMobilUserAgent = true
}