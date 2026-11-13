package stoyck.vitrina.network

import org.w3c.dom.Element
import org.xml.sax.InputSource
import stoyck.vitrina.network.data.PostHint
import stoyck.vitrina.network.data.RedditPost
import java.io.StringReader
import java.net.URI
import java.util.*
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Parses Reddit's public Atom feed (/r/{subreddit}/.rss) into [RedditPost] items
 * without requiring any Reddit OAuth API token or developer key.
 */
object RedditRssParser {

    private val DIRECT_LINK_REGEX =
        Regex("""<a\s+[^>]*href="([^"]+)"[^>]*>\s*\[link\]\s*</a>""", RegexOption.IGNORE_CASE)

    private val IMAGE_EXTENSIONS = listOf(".jpg", ".jpeg", ".png", ".webp")

    // RSS feeds do not expose post scores; use a high sentinel score so valid image posts
    // still pass the per-subreddit minimum upvote filter when .json is unavailable.
    private const val RSS_DEFAULT_SCORE = 1_000_000L

    fun parse(xml: String, fallbackSubreddit: String = ""): List<RedditPost> {
        if (xml.isBlank()) return emptyList()

        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
        }
        val document = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        val entryNodes = document.getElementsByTagName("entry")
        val posts = ArrayList<RedditPost>(entryNodes.length)

        for (i in 0 until entryNodes.length) {
            val entry = entryNodes.item(i) as? Element ?: continue

            val rawId = entry.firstChildText("id").trim()
            val postId = rawId.removePrefix("t3_").ifBlank { rawId }
            if (postId.isBlank()) continue

            val title = entry.firstChildText("title").trim()
            val author = extractAuthor(entry)
            val (subreddit, subredditPrefixed) = extractCategory(entry, fallbackSubreddit)
            val permalink = extractPermalink(entry)
            val contentHtml = entry.firstChildText("content")

            val linkMatch = DIRECT_LINK_REGEX.find(contentHtml) ?: continue
            val directUrl = linkMatch.groupValues[1]
                .replace("&amp;", "&")
                .trim()
            if (directUrl.isBlank()) continue

            val domain = try {
                URI(directUrl).host.orEmpty().removePrefix("www.")
            } catch (_: Exception) {
                ""
            }

            val postHint = classifyUrl(directUrl, domain)

            posts.add(
                RedditPost(
                    id = postId,
                    name = if (rawId.startsWith("t3_")) rawId else "t3_$postId",
                    title = title,
                    author = author,
                    subreddit = subreddit,
                    subredditNamePrefixed = subredditPrefixed,
                    permalink = permalink,
                    url = directUrl,
                    domain = domain,
                    postHint = postHint,
                    score = RSS_DEFAULT_SCORE,
                    ups = RSS_DEFAULT_SCORE,
                    over18 = contentHtml.contains("nsfw", ignoreCase = true),
                    stickied = false
                )
            )
        }

        return posts
    }

    private fun classifyUrl(url: String, domain: String): PostHint {
        val cleanPath = url.substringBefore('?').substringBefore('#').toLowerCase(Locale.ROOT)
        val lowerDomain = domain.toLowerCase(Locale.ROOT)

        if (lowerDomain == "i.redd.it" ||
            lowerDomain == "i.imgur.com" ||
            IMAGE_EXTENSIONS.any { cleanPath.endsWith(it) }
        ) {
            return PostHint.Image
        }

        if (lowerDomain.contains("imgur.com") &&
            !cleanPath.contains("/a/") &&
            !cleanPath.contains("/gallery/")
        ) {
            return PostHint.Link
        }

        return PostHint.Other
    }

    private fun Element.firstChildText(tagName: String): String {
        val list = getElementsByTagName(tagName)
        if (list.length == 0) return ""
        return list.item(0)?.textContent.orEmpty()
    }

    private fun extractAuthor(entry: Element): String {
        val authorNodes = entry.getElementsByTagName("author")
        if (authorNodes.length == 0) return ""
        val authorEl = authorNodes.item(0) as? Element ?: return ""
        return authorEl.firstChildText("name")
            .trim()
            .removePrefix("/u/")
            .removePrefix("u/")
    }

    private fun extractCategory(entry: Element, fallbackSubreddit: String): Pair<String, String> {
        val catNodes = entry.getElementsByTagName("category")
        if (catNodes.length > 0) {
            val catEl = catNodes.item(0) as? Element
            val term = catEl?.getAttribute("term").orEmpty().ifBlank { fallbackSubreddit }
            val label = catEl?.getAttribute("label").orEmpty().ifBlank {
                if (term.isNotBlank()) "r/$term" else ""
            }
            return term to label
        }
        val pref = if (fallbackSubreddit.isNotBlank()) "r/$fallbackSubreddit" else ""
        return fallbackSubreddit to pref
    }

    private fun extractPermalink(entry: Element): String {
        val linkNodes = entry.getElementsByTagName("link")
        for (i in 0 until linkNodes.length) {
            val linkEl = linkNodes.item(i) as? Element ?: continue
            val href = linkEl.getAttribute("href").orEmpty().trim()
            if (href.isNotBlank()) {
                return try {
                    val uri = URI(href)
                    uri.rawPath.orEmpty().ifBlank { href }
                } catch (_: Exception) {
                    href
                }
            }
        }
        return ""
    }
}
