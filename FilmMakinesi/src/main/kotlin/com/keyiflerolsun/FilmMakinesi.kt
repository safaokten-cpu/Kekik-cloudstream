// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer

class FilmMakinesi : MainAPI() {
    override var mainUrl              = "https://filmmakinesi.to"
    override var name                 = "FilmMakinesi"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Movie, TvType.TvSeries)

    // ! CloudFlare bypass
    override var sequentialMainPage            = true
    override var sequentialMainPageDelay       = 50L
    override var sequentialMainPageScrollDelay = 50L

    override val mainPage = mainPageOf(
        "${mainUrl}/filmler-1/"                                  to "Filmler",
        "${mainUrl}/yabanci-dizi-izle-fmq45w/"                   to "Yabancı Diziler",
        "${mainUrl}/ulke/turkiye-fm9984/"                        to "Yerli Filmler",
        "${mainUrl}/film-izle/olmeden-izlenmesi-gerekenler-fm1/" to "Ölmeden İzle",
        "${mainUrl}/kanal/netflix-fm6xv4/"                       to "Netflix",
        "${mainUrl}/kanal/disney-fmgj6b/"                        to "Disney+",
        "${mainUrl}/kanal/amazon/"                               to "Prime Video",
        "${mainUrl}/kanal/apple-fmrwah/"                         to "Apple TV+",
        "${mainUrl}/kanal/hulu/"                                 to "Hulu",
        "${mainUrl}/kanal/paramount/"                            to "Paramount+",
        "${mainUrl}/kanal/hbo-fmv33r/"                           to "HBO Max",
        "${mainUrl}/kanal/peacock/"                              to "Peacock",
        "${mainUrl}/tur/aksiyon-fms562/film/"                    to "Aksiyon",
        "${mainUrl}/tur/bilim-kurgu-fmas9s/film/"                to "Bilim Kurgu",
        "${mainUrl}/tur/komedi-fma6cz/film/"                     to "Komedi",
        "${mainUrl}/tur/korku-fmm7zg/film/"                      to "Korku",
        "${mainUrl}/tur/dram-fm1/film/"                          to "Dram",
        "${mainUrl}/tur/romantik-fm1/film/"                      to "Romantik",
        "${mainUrl}/tur/animasyon-fm2/film/"                     to "Animasyon",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url      = if (page <= 1) request.data else "${request.data}sayfa/${page}/"
        val document = app.get(url).document
        val home     = document.select("div.film-list a.item").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val href = fixUrlNull(this.attr("href")) ?: return null
        if (!href.contains("/film/") && !href.contains("/dizi/")) return null

        val title = this.selectFirst("div.item-footer div.title")?.text()?.trim()?.takeIf { it.isNotEmpty() }
            ?: this.attr("data-title").takeIf { it.isNotBlank() }
            ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return if (href.contains("/dizi/")) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}/arama/?s=${query}").document

        return document.select("a.item").mapNotNull { it.toSearchResult() }.distinctBy { it.url }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title       = document.selectFirst("#info--box h1.title")?.ownText()?.substringBefore(" izle")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val poster      = fixUrlNull(document.selectFirst("[property='og:image']")?.attr("content"))
        val year        = document.selectFirst("#info--box h1.title span.date a")?.text()?.trim()?.toIntOrNull()
        val description = document.selectFirst("#info--box div.info-description")?.text()?.trim()
        val tags        = document.select("#info--box div.info div.type a").map { it.text().trim() }
        val rating      = document.selectFirst("#info--box div.imdb b")?.text()?.trim()
        val duration    = Regex("""(\d+)""").find(document.selectFirst("#info--box div.info div.time")?.text() ?: "")?.groupValues?.get(1)?.toIntOrNull()
        val trailer     = document.selectFirst("a.trailer-button")?.attr("data-video_url")
        val actors      = document.select("#cast a.cast").mapNotNull {
            val actorName = it.selectFirst("div.cast-name")?.text()?.trim() ?: return@mapNotNull null
            Actor(actorName, fixUrlNull(it.selectFirst("img")?.attr("src")))
        }
        val recommendations = document.select("div.related a.item").mapNotNull { it.toSearchResult() }

        if (url.contains("/dizi/")) {
            val episodes = document.select("div.bolum-list a.item-ep").mapNotNull {
                val epHref    = fixUrlNull(it.attr("href")) ?: return@mapNotNull null
                val epTitle   = it.selectFirst("div.ep-title")?.text()?.trim() ?: return@mapNotNull null
                val epSeason  = Regex("""(\d+)\.\s*Sezon""").find(epTitle)?.groupValues?.get(1)?.toIntOrNull()
                val epEpisode = Regex("""(\d+)\.\s*Bölüm""").find(epTitle)?.groupValues?.get(1)?.toIntOrNull()

                newEpisode(epHref) {
                    this.name    = epTitle
                    this.season  = epSeason
                    this.episode = epEpisode
                }
            }

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl       = poster
                this.year            = year
                this.plot            = description
                this.tags            = tags
                this.score           = Score.from10(rating)
                this.duration        = duration
                this.recommendations = recommendations
                addActors(actors)
                addTrailer(trailer)
            }
        }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl       = poster
            this.year            = year
            this.plot            = description
            this.tags            = tags
            this.score           = Score.from10(rating)
            this.duration        = duration
            this.recommendations = recommendations
            addActors(actors)
            addTrailer(trailer)
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("FLMM", "data » $data")
        val document = app.get(data).document

        val links = document.select("div.video-parts a[data-video_url]")
            .map { it.attr("data-video_url") }
            .filter { it.isNotBlank() }
            .toMutableList()

        if (links.isEmpty()) {
            val iframe = fixUrlNull(document.selectFirst("div.after-player iframe")?.attr("data-src")) ?: return false
            links.add(iframe)
        }

        for (link in links.distinct()) {
            Log.d("FLMM", "link » $link")
            loadExtractor(link, "${mainUrl}/", subtitleCallback, callback)
        }

        return true
    }
}
