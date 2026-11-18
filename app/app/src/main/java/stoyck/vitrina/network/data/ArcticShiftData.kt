package stoyck.vitrina.network.data

import com.google.gson.annotations.SerializedName
import stoyck.vitrina.persistence.data.PersistedSubredditData

/**
 * Response from the Arctic Shift public Reddit archive:
 * https://arctic-shift.photon-reddit.com/api/posts/search?author=<user>
 *
 * Used only as a fallback for Reddit *user* feeds (u/<name>), because
 * Reddit now blocks unauthenticated /user/<name>/submitted.json (403)
 * and its user RSS feeds return 404. No API key is required.
 */
data class ArcticShiftSearchResponse(
    @SerializedName("data")
    val data: List<ArcticShiftPost>? = null,

    @SerializedName("error")
    val error: String? = null
)

data class ArcticShiftPost(
    @SerializedName("id")
    val id: String = "",

    @SerializedName("title")
    val title: String = "",

    @SerializedName("url")
    val url: String = "",

    @SerializedName("domain")
    val domain: String = "",

    @SerializedName("score")
    val score: Long = 0,

    @SerializedName("over_18")
    val over18: Boolean = false,

    @SerializedName("subreddit")
    val subreddit: String = "",

    @SerializedName("author")
    val author: String = "",

    @SerializedName("permalink")
    val permalink: String = "",

    @SerializedName("post_hint")
    val postHint: String? = null,

    @SerializedName("created_utc")
    val createdUtc: Long = 0,

    @SerializedName("stickied")
    val stickied: Boolean = false
) {
    fun toRedditPost(): RedditPost {
        return RedditPost(
            domain = domain,
            subreddit = subreddit,
            id = id,
            title = title,
            score = score,
            over18 = over18,
            postHint = when (postHint?.toLowerCase(java.util.Locale.ROOT)) {
                "image" -> PostHint.Image
                "link" -> PostHint.Link
                else -> PostHint.Other
            },
            stickied = stickied,
            name = if (id.startsWith("t3_")) id else "t3_$id",
            permalink = permalink,
            createdUtc = createdUtc,
            created = createdUtc,
            url = url,
            author = author,
            subredditNamePrefixed = PersistedSubredditData.prefixedName(subreddit),
            ups = score
        )
    }
}
