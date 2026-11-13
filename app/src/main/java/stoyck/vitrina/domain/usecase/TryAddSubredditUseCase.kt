package stoyck.vitrina.domain.usecase

import android.content.Context
import stoyck.vitrina.R
import stoyck.vitrina.domain.UserReadableException
import stoyck.vitrina.persistence.data.PersistedSubredditData
import stoyck.vitrina.util.VResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TryAddSubredditUseCase @Inject constructor(
    // Reference not saved
    context: Context,
    private val loadSubreddits: LoadSubredditsUseCase,
    private val saveSubreddits: SaveSubredditsUseCase
) {

    private val invalidSubredditNameMessage =
        context.resources.getString(R.string.error_invalid_subreddit_name)

    private val subredditAlreadyAddedMessage =
        context.resources.getString(R.string.error_subreddit_already_added)

    suspend operator fun invoke(requestedSubredditName: String):
            VResult<Pair<PersistedSubredditData, List<PersistedSubredditData>>> {
        val normalizedName = normalizeSubredditName(requestedSubredditName)

        if (!VALID_SUBREDDIT_REGEX.matches(normalizedName)) {
            return VResult.failure(UserReadableException(invalidSubredditNameMessage))
        }

        val existingSubreddits = loadSubreddits()
        val newSubreddit = PersistedSubredditData.fromDirectInput(normalizedName)

        val alreadyExists = existingSubreddits.any {
            it.name.equals(normalizedName, ignoreCase = true) ||
                it.id.equals(newSubreddit.id, ignoreCase = true)
        }

        if (alreadyExists) {
            val displayLabel = if (normalizedName.startsWith("u/", ignoreCase = true)) {
                "/$normalizedName"
            } else {
                "/r/$normalizedName"
            }
            val message = subredditAlreadyAddedMessage.format(displayLabel)
            return VResult.failure(UserReadableException(message))
        }

        val newSubreddits = listOf(
            // Add to the start
            newSubreddit,
            *existingSubreddits.toTypedArray()
        )

        saveSubreddits(newSubreddits)

        return VResult.success(newSubreddit to newSubreddits)
    }

    companion object {
        private val SUBREDDIT_PATH_REGEX =
            Regex("""(?:^|/)r/([^/?#\s]+)""", RegexOption.IGNORE_CASE)

        private val USER_PATH_REGEX =
            Regex("""(?:^|/)(?:u|user)/([A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE)

        private val VALID_SUBREDDIT_REGEX =
            Regex("""^(?:u/[A-Za-z0-9_-]+|[A-Za-z0-9_]+(?:\+[A-Za-z0-9_]+)*)$""", RegexOption.IGNORE_CASE)

        fun normalizeSubredditName(raw: String): String {
            val trimmed = raw.trim()

            val userMatch = USER_PATH_REGEX.find(trimmed)
            if (userMatch != null) {
                return "u/${userMatch.groupValues[1]}"
            }

            val pathMatch = SUBREDDIT_PATH_REGEX.find(trimmed)
            if (pathMatch != null) {
                return pathMatch.groupValues[1]
            }

            return trimmed
                .removePrefix("/")
                .let {
                    if (it.startsWith("r/", ignoreCase = true)) {
                        it.substring(2)
                    } else {
                        it
                    }
                }
                .trim('/')
                .trim()
        }
    }

}
