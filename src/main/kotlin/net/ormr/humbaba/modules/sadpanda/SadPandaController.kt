/*
 * Copyright 2022 Oliver Berg
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.ormr.humbaba.modules.sadpanda

import com.github.michaelbull.logging.InlineLogger
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.datetime.LocalDateTime
import net.ormr.humbaba.HumbabaConfig
import net.ormr.humbaba.utils.selectFirstSafe
import net.ormr.katbox.Catbox
import net.ormr.kommando.processor.Include
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.Closeable

private const val PAGE_COLUMNS = 10
private const val PAGE_ROWS = 4
private const val IMAGES_PER_PAGE = PAGE_COLUMNS * PAGE_ROWS

@Include
class SadPandaController(config: HumbabaConfig, private val catbox: Catbox) : Closeable {
    companion object {
        val COMIC_URL_REGEX = """(?:https?://)?(?:www\.)?e[x|-]hentai\.org/g/(\d+)/(\w+)/?""".toRegex()
        val PAGE_URL_REGEX = """(?:https?://)?(?:www\.)?e[x|-]hentai\.org/s/(\w+)/(\d+)-(\d+)/?""".toRegex()

        // I'm not sure if this regex is fool-proof, it might fail badly at some point
        private val coverRegex = """background:transparent url\((.*)\)""".toRegex()

        private val logger = InlineLogger()
    }

    private val sadPandaConfig: HumbabaConfig.SadPanda? = config.sadPanda
    private val httpClient: HttpClient by lazy {
        HttpClient {
            val cookies = sadPandaConfig
                ?.cookies
                ?.map { (name, value) -> Cookie(name, value, path = "/", domain = ".exhentai.org") }
                ?: error("Can't create http client for sad panda without credentials.")

            install(HttpCookies) {
                storage = ConstantCookiesStorage(*cookies.toTypedArray())
            }

            install(HttpTimeout) {
                connectTimeoutMillis = 40_000
            }
        }
    }

    fun canScrape(): Boolean = sadPandaConfig != null

    private suspend fun getDocument(url: String): Document {
        val response = httpClient.get(url)
        val text = response.bodyAsText()
        return Jsoup.parse(text)
    }

    // TODO: verify if it's a correct page
    suspend fun getComicPageContents(id: SadPandaId, page: Int): SadPandaImageContent = try {
        val document = getDocument("https://exhentai.org/s/${id.hash}/${id.id}-$page")
        val imageUrl = document.selectFirstSafe("body > div#i1 > div#i3 > a[href] > img#img").attr("src")
        logger.info { "Download image from page '${id.hash}/${id.id}-$page' with url $imageUrl." }
        val response = httpClient.request(imageUrl)
        // TODO: response.readBytes() ?
        SadPandaImageContent(response.body(), imageUrl.substringAfterLast('/'))
    } catch (e: NoSuchElementException) {
        scrapeError(id, e.message ?: "no message", e)
    }

    // TODO: verify if it's a correct page
    suspend fun getComicPage(id: SadPandaId, page: Int): String = try {
        val contents = getComicPageContents(id, page)
        logger.info { "Uploading image from page '${id.hash}/${id.id}-$page' to catbox." }
        catbox.upload(contents.content, contents.name)
    } catch (e: NoSuchElementException) {
        scrapeError(id, e.message ?: "no message", e)
    }

    // TODO: verify if it's a correct page
    suspend fun getComic(id: SadPandaId): SadPandaComic = try {
        logger.info { "Attempting to scrape '$id'." }
        val document = getDocument("https://exhentai.org/g/${id.id}/${id.hash}/")
        // gm is top info
        // gtb is image count and page selector
        // gdt is the actual images
        val gm = document.selectFirstSafe("body > div.gm")
        val gtb = document.selectFirstSafe("body > div.gtb")
        val gdt = document.selectFirstSafe("body > div#gdt")
        scrapeComicHtml(gm, gtb, gdt, id)
    } catch (e: NoSuchElementException) {
        scrapeError(id, e.message ?: "no message", e)
    }

    private suspend fun scrapeComicHtml(gm: Element, gtb: Element, gdt: Element, id: SadPandaId): SadPandaComic {
        val mainTitle = gm.selectFirstSafe("div#gd2 > h1#gn").text()
        val subTitle = gm.selectFirst("div#gd2 > h1#gj")?.text()?.ifBlank { null }
        // the tags table
        val tags = gm.selectFirstSafe("div#gmid > div#gd4 > div#taglist > table > tbody")
            .select("tr")
            .associate { tr ->
                val category = SadPandaTagCategory.of(tr.selectFirstSafe("td.tc").text().substringBeforeLast(':'))
                val tags = tr.select("td > div[id] > a[id]")
                    .map { SadPandaTag(category, it.text(), it.attr("href").ifBlank { null }) }
                Pair(category, tags)
            }
        // the info to the side, cover, favorites, page count, etc..
        val gd3Table = gm.selectFirstSafe("div#gd3").selectFirstSafe("div#gdd > table > tbody")
        val publishDate = getPublishDate(gd3Table)
        val pageCount = getSideElementText(gd3Table, "Length:").substringBefore(' ').toInt()
        val favoriteCount = getSideElementText(gd3Table, "Favorited:").substringBefore(' ').toInt()
        //val pages = fetchPages(gtb, gdt, id)
        // can entries exist without covers? Probably not, right?
        val coverUrl = getCover(gm, id)
        return SadPandaComic(id, mainTitle, subTitle, tags, coverUrl, publishDate, pageCount, favoriteCount)
    }

    private suspend fun fetchPages(gtb: Element, gdt: Element, id: SadPandaId): List<SadPandaPage> {
        val lastPageIndex = gtb.select("table.ptt > tbody > tr > td")
            .let { it[it.size - 2] }
            .selectFirstSafe("a[href]")
            .attr("href")
            .substringAfterLast("?p=")
            .toInt()
        return buildList(IMAGES_PER_PAGE * (lastPageIndex + 1)) {
            addAll(scrapeImages(0, gdt, id))
            for (i in 1..lastPageIndex) {
                val pageDocument = getDocument("https://exhentai.org/g/${id.id}/${id.hash}/?p=$i")
                val pageGdt = pageDocument.selectFirstSafe("body > div#gdt")
                addAll(scrapeImages(i, pageGdt, id))
            }
        }
    }

    private fun scrapeImages(index: Int, gdt: Element, id: SadPandaId): List<SadPandaPage> =
        buildList(IMAGES_PER_PAGE) {
            val images = gdt.select("div.gdtm > div > a[href]")
                .asSequence()
                .map { it.attr("href") }
                .map { PAGE_URL_REGEX.matchEntire(it) ?: scrapeError(id, "Image href does not match regex. '$it'") }
                .map { SadPandaPage(SadPandaId(it.groupValues[2], it.groupValues[1]), index) }
            addAll(images)
        }

    private suspend fun getCover(gm: Element, id: SadPandaId): SadPandaImageContent {
        val coverUrl = gm.selectFirstSafe("div#gleft > div#gd1 > div[style]")
            .attr("style")
            .let { coverRegex.find(it)?.groupValues?.get(1) ?: scrapeError(id, "Could not find cover in style attr") }
        logger.info { "Downloading thumbnail cover for '$id' from '$coverUrl'." }
        val coverResponse = httpClient.request(coverUrl)
        val coverContent = coverResponse.body<ByteArray>()
        return SadPandaImageContent(coverContent, coverUrl.substringAfterLast('/'))
    }

    // TODO: replace Catbox with the I currently use with ShareX, because Catbox is very unstable
    /*private suspend fun getCover(gm: Element, id: SadPandaId): String {
        val coverUrl = gm.selectFirstSafe("div#gleft > div#gd1 > div[style]")
            .attr("style")
            .let { coverRegex.find(it)?.groupValues?.get(1) ?: scrapeError(id, "Could not find cover in style attr") }
        logger.info { "Downloading thumbnail cover for '$id' from '$coverUrl'." }
        val coverContent = httpClient.request<ByteArray>(coverUrl)
        logger.info { "Uploading thumbnail cover for '$id' to Catbox." }
        return catbox.upload(coverContent, coverUrl.substringAfterLast('/'))
    }*/

    private fun getSideElementText(gd3Table: Element, text: String): String =
        gd3Table.selectFirstSafe("tr > td.gdt1:containsOwn($text)")
            .nextElementSibling()!!
            .text()

    private fun getPublishDate(gd3Table: Element): LocalDateTime {
        val text = getSideElementText(gd3Table, "Posted:")
        return LocalDateTime.parse(text.replace(' ', 'T'))
    }

    private fun scrapeError(id: SadPandaId, message: String, cause: Throwable? = null): Nothing =
        throw SadPandaScrapeException(id, message, cause)

    override fun close() {
        if (canScrape()) httpClient.close()
    }
}