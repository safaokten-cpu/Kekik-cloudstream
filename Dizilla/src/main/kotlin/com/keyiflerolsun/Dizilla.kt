// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors

class Dizilla : MainAPI() {
    override var mainUrl              = "https://dizilla.nl"
    override var name                 = "Dizilla"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = true
    override val supportedTypes       = setOf(TvType.TvSeries)

    // ! CloudFlare bypass
    override var sequentialMainPage = true

    override val mainPage = mainPageOf(
        "${mainUrl}/tum-bolumler"          to "Altyazılı Bölümler",
        "${mainUrl}/dublaj-bolumler"       to "Dublaj Bölümler",
        "${mainUrl}/dizi-turu/aile"        to "Aile",
        "${mainUrl}/dizi-turu/aksiyon"     to "Aksiyon",
        "${mainUrl}/dizi-turu/bilim-kurgu" to "Bilim Kurgu",
        "${mainUrl}/dizi-turu/romantik"    to "Romantik",
        "${mainUrl}/dizi-turu/komedi"      to "Komedi"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data).document
        val home     = if (request.data.contains("dizi-turu")) { 
            document.select("div.grid-cols-3 a").mapNotNull { it.diziler() }
        } else {
            document.select("div.grid a").mapNotNull { it.sonBolumler() }
        }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.diziler(): SearchResponse? {
        val title     = this.selectFirst("h2")?.text() ?: return null
        val href      = fixUrlNull(this.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("data-src")) ?: fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
    }

    private suspend fun Element.sonBolumler(): SearchResponse? {
        val name   = this.selectFirst("h2")?.text() ?: return null
        val epName = this.selectFirst("div.opacity-80")!!.text().replace(". Sezon ", "x").replace(". Bölüm", "")
        val title  = "$name -$epName"

        val epDoc     = app.get(this.attr("href")).document
        val href      = fixUrlNull(epDoc.selectFirst("a.relative")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(epDoc.selectFirst("img.imgt")?.attr("onerror")?.substringAfter("= '")?.substringBefore("';"))

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val response = app.get("${mainUrl}/arama?q=${query}").document
        return response.select("div.grid-cols-3 a").mapNotNull { it.diziler() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title       = document.selectFirst("h1")?.text()?.trim() ?: return null
        val poster      = fixUrlNull(document.selectFirst("img.rounded-lg")?.attr("src"))
        val description = document.selectFirst("p.text-gray-300")?.text()?.trim()
        val year        = document.selectXpath("//span[contains(text(), 'Yıl')]//following-sibling::span").text().trim().toIntOrNull()
        val rating      = document.selectXpath("//span[contains(text(), 'IMDb')]//following-sibling::span").text().trim()
        val tags        = document.select("div.flex.flex-wrap.gap-2 a").map { it.text().trim() }

        val episodes = document.select("div.flex.flex-col.gap-2 a").mapNotNull {
            val epHref  = fixUrlNull(it.attr("href")) ?: return@mapNotNull null
            val epTitle = it.selectFirst("span")?.text()?.trim() ?: "Bölüm"
            
            val season  = Regex("""(\d+)\. Sezon""").find(epTitle)?.groupValues?.get(1)?.toIntOrNull() ?: 1
            val episode = Regex("""(\d+)\. Bölüm""").find(epTitle)?.groupValues?.get(1)?.toIntOrNull()

            newEpisode(epHref) {
                this.name    = epTitle
                this.season  = season
                this.episode = episode
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.plot      = description
            this.year      = year
            this.tags      = tags
            this.score     = Score.from10(rating)
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("DZL", "data » $data")
        val document = app.get(data).document
        val iframe   = document.selectFirst("iframe")?.attr("src") ?: return false
        Log.d("DZL", "iframe » $iframe")

        return loadExtractor(iframe, "${mainUrl}/", subtitleCallback, callback)
    }
}
