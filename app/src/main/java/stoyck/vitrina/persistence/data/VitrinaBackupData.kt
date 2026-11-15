package stoyck.vitrina.persistence.data

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import java.util.Locale

/**
 * Backup file format for vitrina+.
 *
 * Only the subreddit / Reddit-user list and each entry's minimum
 * upvote count are stored. General settings (shuffle, over-18,
 * minimum image resolution) are intentionally NOT included.
 *
 * Example:
 * {
 *   "app": "vitrina+",
 *   "version": 1,
 *   "subreddits": [
 *     { "id": "direct_earthporn", "name": "EarthPorn", "minUpvoteCount": 1200 },
 *     { "id": "direct_u_someuser", "name": "u/SomeUser", "minUpvoteCount": 10 }
 *   ]
 * }
 */
data class VitrinaBackupData(
    val app: String = APP_NAME,
    val version: Int = CURRENT_VERSION,
    val subreddits: List<PersistedSubredditData> = emptyList()
) {
    companion object {
        const val APP_NAME = "vitrina+"
        const val CURRENT_VERSION = 1
    }
}

object VitrinaBackupCodec {

    private val gson = Gson()

    fun encode(subreddits: List<PersistedSubredditData>): String {
        val clean = sanitize(subreddits)
        return gson.toJson(
            VitrinaBackupData(
                subreddits = clean
            )
        )
    }

    /**
     * @throws IllegalArgumentException if the file is not a valid vitrina+ backup.
     */
    fun decode(json: String): List<PersistedSubredditData> {
        val trimmed = json.trim()
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("Backup file is empty")
        }

        val entries: List<PersistedSubredditData> = try {
            if (trimmed.startsWith("[")) {
                // Also accept a plain JSON array of entries.
                val type = object : TypeToken<List<PersistedSubredditData>>() {}.type
                gson.fromJson<List<PersistedSubredditData>>(trimmed, type) ?: emptyList()
            } else {
                val root = JsonParser.parseString(trimmed).asJsonObject
                val version = if (root.has("version")) root.get("version").asInt else 1
                if (version > VitrinaBackupData.CURRENT_VERSION) {
                    throw IllegalArgumentException("Backup version $version is newer than supported")
                }
                val backupType = object : TypeToken<VitrinaBackupData>() {}.type
                val backup = gson.fromJson<VitrinaBackupData>(trimmed, backupType)
                    ?: throw IllegalArgumentException("Backup file is empty")
                backup.subreddits ?: emptyList()
            }
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw IllegalArgumentException("Not a valid vitrina+ backup file", e)
        }

        val clean = sanitize(entries)
        if (clean.isEmpty()) {
            throw IllegalArgumentException("Backup file contains no subreddits")
        }
        return clean
    }

    /**
     * Removes blank names, negative upvote counts and duplicates
     * (same name, ignoring case). Keeps the first occurrence and
     * generates a stable local id when one is missing.
     */
    fun sanitize(entries: List<PersistedSubredditData>): List<PersistedSubredditData> {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<PersistedSubredditData>()

        for (entry in entries) {
            val name = entry.name?.trim().orEmpty()
            if (name.isEmpty()) continue

            val key = name.lowercase(Locale.ROOT)
            if (!seen.add(key)) continue

            val safeUpvotes = if (entry.minUpvoteCount < 0) 0L else entry.minUpvoteCount
            val safeId = entry.id?.trim().orEmpty().ifEmpty {
                PersistedSubredditData.fromDirectInput(name, safeUpvotes).id
            }

            result.add(
                PersistedSubredditData(
                    id = safeId,
                    name = name,
                    minUpvoteCount = safeUpvotes
                )
            )
        }

        // If two different names produced the same id, make them unique.
        val usedIds = mutableSetOf<String>()
        return result.map { entry ->
            var candidate = entry.id
            var counter = 2
            while (!usedIds.add(candidate.lowercase(Locale.ROOT))) {
                candidate = "${entry.id}_$counter"
                counter++
            }
            if (candidate == entry.id) entry else entry.copy(id = candidate)
        }
    }
}
