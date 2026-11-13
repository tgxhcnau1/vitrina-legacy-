package stoyck.vitrina.network

import android.util.Base64
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.CancellationException
import stoyck.vitrina.network.data.RedditAbout
import stoyck.vitrina.network.data.RedditAuthorizationResponse
import stoyck.vitrina.network.data.RedditPost
import stoyck.vitrina.ui.suggestion.SubredditSuggestionData
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class RedditService @Inject constructor(
    private val publicApi: RedditPublicApi,
    private val oauthApi: RedditOauthApi,
    private val bearerBearerTokenWrapper: BearerTokenWrapper,
    @Named("reddit_client_id")
    private val clientId: String
) {
    private val usernameAndPassword = "$clientId:"

    private val authorization: String by lazy {
        "Basic " + Base64.encodeToString(
            usernameAndPassword.toByteArray(),
            Base64.DEFAULT
        ).trim()
    }

    @VisibleForTesting
    suspend fun retrieveAccessToken(): RedditAuthorizationResponse {
        return publicApi.getAccessToken(authorization = authorization)
    }

    suspend fun retrieveSubreddit(subreddit: String): RedditAbout {
        val page = publicApi.getAbout(subreddit = subreddit)
        return page.data
    }

    suspend fun retrievePosts(subreddits: List<String>): List<RedditPost> {
        return retrievePosts(subreddits.joinToString(separator = "+"))
    }

    /**
     * Fetches subreddit or user posts without using Reddit's OAuth API or client_id:
     * 1. Tries public unauthenticated JSON first.
     * 2. Falls back to public Atom/RSS if .json is blocked (403).
     */
    suspend fun retrievePosts(subreddit: String): List<RedditPost> {
        val isUserFeed = subreddit.startsWith("u/", ignoreCase = true) ||
            subreddit.startsWith("u_", ignoreCase = true)

        if (isUserFeed) {
            val username = subreddit
                .removePrefix("u/")
                .removePrefix("U/")
                .removePrefix("u_")
                .removePrefix("U_")

            return try {
                val result = publicApi.getUserPosts(
                    username = username,
                    limit = 100,
                    rawJson = 1
                )
                result.data.children.map { it.data }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                val rssXml = publicApi.getUserPostsRss(username = username).use { it.string() }
                RedditRssParser.parse(rssXml, fallbackSubreddit = "u/$username")
            }
        }

        return try {
            val result = publicApi.getPosts(
                subreddit = subreddit,
                limit = 100,
                rawJson = 1
            )
            result.data.children.map { it.data }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            val rssXml = publicApi.getPostsRss(subreddit = subreddit).use { it.string() }
            RedditRssParser.parse(rssXml, fallbackSubreddit = subreddit)
        }
    }

    @Deprecated("Subreddit autocomplete removed in favor of offline direct text input")
    suspend fun retrieveHints(
        query: String,
        includeOver18: Boolean = false
    ): List<SubredditSuggestionData> {
        return emptyList()
    }

}
