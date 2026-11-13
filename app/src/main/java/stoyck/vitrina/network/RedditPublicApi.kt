package stoyck.vitrina.network

import okhttp3.ResponseBody
import retrofit2.http.*
import stoyck.vitrina.network.data.RedditAboutPage
import stoyck.vitrina.network.data.RedditAuthorizationResponse
import stoyck.vitrina.network.data.RedditPostPage
import stoyck.vitrina.network.data.SubredditSuggestionPage

/**
 * Endpoints that are reachable without any Reddit OAuth token or API key
 */
interface RedditPublicApi {

    @FormUrlEncoded
    @POST("/api/v1/access_token")
    suspend fun getAccessToken(
        @Header("authorization") authorization: String,
        @Field("grant_type") grantType: String = "https://oauth.reddit.com/grants/installed_client",
        @Field("device_id") deviceId: String = "DO_NOT_TRACK_THIS_DEVICE"
    ): RedditAuthorizationResponse

    @GET("/r/{subreddit}/about.json")
    suspend fun getAbout(
        @Path("subreddit") subreddit: String
    ): RedditAboutPage

    @GET("/r/{subreddit}.json")
    suspend fun getPosts(
        @Path("subreddit") subreddit: String,
        @Query("limit") limit: Int? = null,
        @Query("raw_json") rawJson: Int = 1
    ): RedditPostPage

    @GET("/r/{subreddit}/.rss")
    suspend fun getPostsRss(
        @Path("subreddit") subreddit: String
    ): ResponseBody

    @GET("/user/{username}/submitted.json")
    suspend fun getUserPosts(
        @Path("username") username: String,
        @Query("limit") limit: Int? = null,
        @Query("raw_json") rawJson: Int = 1
    ): RedditPostPage

    @GET("/user/{username}/submitted/.rss")
    suspend fun getUserPostsRss(
        @Path("username") username: String
    ): ResponseBody

    @GET("/subreddits.json")
    suspend fun getSubreddits(
        @Query("q") query: String,
        @Query("limit") limit: Int? = null,
        @Query("count") count: Int? = null,
        @Query("sort") sort: String = "relevance"
    ): SubredditSuggestionPage
}
