package stoyck.vitrina.domain.usecase

import com.google.gson.JsonParseException
import kotlinx.coroutines.delay
import org.xml.sax.SAXException
import retrofit2.HttpException
import stoyck.vitrina.network.RedditService
import stoyck.vitrina.network.data.PostHint
import stoyck.vitrina.network.data.RedditPost
import stoyck.vitrina.persistence.data.PersistedFuturePosts
import stoyck.vitrina.persistence.data.PersistedSubredditData
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RetrieveLatestImagesUseCase @Inject constructor(
    private val loadPosts: LoadPostsUseCase,
    private val loadSubreddits: LoadSubredditsUseCase,
    private val reddit: RedditService
) {

    private suspend fun loadPostsFromAllSubreddits(
        subreddits: List<PersistedSubredditData>
    ): PersistedFuturePosts {
        val posts = subreddits.mapIndexed { index, subreddit ->
            if (index > 0) {
                delay(1500L)
            }

            val fetchedPosts = try {
                reddit.retrievePosts(subreddit.name)
            } catch (e: HttpException) {
                emptyList<RedditPost>()
            } catch (e: JsonParseException) {
                emptyList<RedditPost>()
            } catch (e: SAXException) {
                emptyList<RedditPost>()
            }

            return@mapIndexed subreddit.id to fetchedPosts
        }.toMap()

        return PersistedFuturePosts(posts)
    }

    /**
     * Return type is used as the future posts to make sure the key is the id
     */
    suspend operator fun invoke(): PersistedFuturePosts {
        val previousPosts = loadPosts()
        val subreddits = loadSubreddits()

        val existingPostIds = previousPosts.map { it.id }.toSet()

        @Suppress("UnnecessaryVariable")
        val posts = loadPostsFromAllSubreddits(subreddits)
            .postIdsRemoved(existingPostIds)
            .processImages()

        return posts
    }

    private fun PersistedFuturePosts.processImages(): PersistedFuturePosts {
        return PersistedFuturePosts(
            this.futurePostsBySubredditId
                .map { (key, value) ->
                    key to value.asSequence().filterImages().toList()
                }
                .toMap()
        )
    }

    private fun Sequence<RedditPost>.filterImages(): Sequence<RedditPost> {
        return this
            .filter { !it.stickied }
            .map { convertToDirectImageLink(it) }
            .filterNotNull()
    }

    private fun convertToDirectImageLink(info: RedditPost): RedditPost? {
        val cleanUrl = info.url.replace("&amp;", "&")
        return when (info.postHint) {
            PostHint.Image -> {
                if (cleanUrl.isBlank())
                    null
                else
                    info.copy(url = cleanUrl)
            }
            PostHint.Link -> {
                if (info.domain.contains("imgur", ignoreCase = true)) {
                    try {
                        info.copy(
                            url = convertImgurLinkToDirectLink(cleanUrl),
                            postHint = PostHint.Image
                        )
                    } catch (_: IllegalArgumentException) {
                        null
                    }
                } else {
                    null
                }
            }
            null, PostHint.Other -> {
                val pathOnly = cleanUrl.substringBefore('?').substringBefore('#')
                if (cleanUrl.contains("i.redd.it/", ignoreCase = true) ||
                    pathOnly.endsWith(".jpg", ignoreCase = true) ||
                    pathOnly.endsWith(".jpeg", ignoreCase = true) ||
                    pathOnly.endsWith(".png", ignoreCase = true) ||
                    pathOnly.endsWith(".webp", ignoreCase = true)
                ) {
                    info.copy(url = cleanUrl, postHint = PostHint.Image)
                } else {
                    null
                }
            }
        }
    }

    private fun convertImgurLinkToDirectLink(imgurUrl: String): String {
        if (!imgurUrl.toLowerCase(Locale.ROOT).contains("imgur")) {
            throw IllegalArgumentException("The link provided is not an imgur link: $imgurUrl")
        }

        val id = imgurUrl
            .substringAfterLast("com/")
            .substringBefore(".")
            .substringBefore("?")
            .substringBefore("/")

        if (id.isBlank() || id == imgurUrl) {
            throw IllegalArgumentException("Could not extract id from the imgur url.")
        } else {
            return "https://i.imgur.com/$id.jpg"
        }
    }
}
