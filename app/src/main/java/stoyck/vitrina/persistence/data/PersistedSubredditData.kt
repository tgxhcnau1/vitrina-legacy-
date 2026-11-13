package stoyck.vitrina.persistence.data

import androidx.annotation.VisibleForTesting
import stoyck.vitrina.network.data.RedditAbout
import java.util.*

/**
 * Don't change this if possible
 */
data class PersistedSubredditData(
    val id: String,
    val name: String,
    val minUpvoteCount: Long
) {

    companion object {
        fun fromAbout(about: RedditAbout): PersistedSubredditData {
            return PersistedSubredditData(
                id = about.id,
                name = about.displayName,
                minUpvoteCount = 10
            )
        }

        fun fromDirectInput(name: String, minUpvoteCount: Long = 10): PersistedSubredditData {
            val safeKey = name.toLowerCase(Locale.ROOT).replace('/', '_')
            return PersistedSubredditData(
                id = "direct_$safeKey",
                name = name,
                minUpvoteCount = minUpvoteCount
            )
        }

        @VisibleForTesting
        fun fromName(name: String, minUpvoteCount: Long = 0): PersistedSubredditData {
            val safeKey = name.toLowerCase(Locale.ROOT).replace('/', '_')
            return PersistedSubredditData(
                id = "direct_$safeKey",
                minUpvoteCount = minUpvoteCount,
                name = name
            )
        }
    }

}
