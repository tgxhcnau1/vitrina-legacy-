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

        /**
         * Correct human-readable prefix for an entry name.
         * Subreddits become "r/EarthPorn"; Reddit users become "u/SomeUser".
         * Never produces broken mixes like "r/u/SomeUser".
         */
        fun prefixedName(name: String): String {
            val trimmed = name.trim().trim('/')
            return when {
                trimmed.startsWith("u/", ignoreCase = true) -> "u/" + trimmed.substring(2)
                trimmed.startsWith("r/", ignoreCase = true) -> "r/" + trimmed.substring(2)
                trimmed.startsWith("u_", ignoreCase = true) -> "u/" + trimmed.substring(2)
                trimmed.startsWith("r_", ignoreCase = true) -> "r/" + trimmed.substring(2)
                else -> "r/$trimmed"
            }
        }

        /** Like [prefixedName], but with a leading slash: "/r/EarthPorn", "/u/SomeUser". */
        fun displayLabel(name: String): String = "/${prefixedName(name)}"

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
