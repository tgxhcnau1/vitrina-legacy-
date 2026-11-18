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

            // User feeds need a longer fallback chain than subreddits:
            // 1. Reddit JSON (often 403 without OAuth)
            // 2. Reddit user RSS (now returns 404 "Not Found" feeds)
            // 3. Arctic Shift public archive (no key needed) - this is
            //    currently the route that actually returns user posts.
            try {
                val result = publicApi.getUserPosts(
                    username = username,
                    limit = 100,
                    rawJson = 1
                )
                val posts = result.data.children.map { it.data }
                if (posts.isNotEmpty()) return posts
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // fall through to RSS
            }

            try {
                val rssXml = publicApi.getUserPostsRss(username = username).use { it.string() }
                val rssPosts = RedditRssParser.parse(rssXml, fallbackSubreddit = "u/$username")
                if (rssPosts.isNotEmpty()) return rssPosts
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // fall through to archive
            }

            return fetchFromArchive(author = username)
        }

        // Subreddit chain (same idea):
        // 1. Reddit JSON (403 without OAuth for most networks now)
        // 2. Reddit RSS (retired by Reddit on 2026-11-13, kept just in case)
        // 3. Arctic Shift public archive - currently the working route.
        try {
            val result = publicApi.getPosts(
                subreddit = subreddit,
                limit = 100,
                rawJson = 1
            )
            val posts = result.data.children.map { it.data }
            if (posts.isNotEmpty()) return posts
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // fall through to RSS
        }

        try {
            val rssXml = publicApi.getPostsRss(subreddit = subreddit).use { it.string() }
            val rssPosts = RedditRssParser.parse(rssXml, fallbackSubreddit = subreddit)
            if (rssPosts.isNotEmpty()) return rssPosts
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // fall through to archive
        }

        // Archive search takes one subreddit at a time; support "a+b" names.
        return subreddit.split("+")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .flatMap { single -> fetchFromArchive(subreddit = single) }
    }

    /**
     * Arctic Shift archive lookup with one retry on its transient
     * "Timeout" error responses. Never throws for network problems:
     * one failing entry must not poison fetching for all other entries,
     * so failures simply return an empty list for that entry.
     */
    private suspend fun fetchFromArchive(
        author: String? = null,
        subreddit: String? = null
    ): List<RedditPost> {
        repeat(2) { attempt ->
            try {
                val response = publicApi.getPostsFromArchive(
                    author = author,
                    subreddit = subreddit
                )
                val posts = response.data.orEmpty()
                if (posts.isNotEmpty()) {
                    return posts.map { it.toRedditPost() }
                }
                if (response.error == null) {
                    return emptyList()
                }
                // transient archive error (e.g. Timeout) -> retry once
                if (attempt == 0) kotlinx.coroutines.delay(2000L)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (attempt == 0) kotlinx.coroutines.delay(2000L)
            }
        }
        return emptyList()
    }

    @Deprecated("Subreddit autocomplete removed in favor of offline direct text input")
    suspend fun retrieveHints(
        query: String,
        includeOver18: Boolean = false
    ): List<SubredditSuggestionData> {
        return emptyList()
    }

}
