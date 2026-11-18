package stoyck.vitrina.domain.usecase

import android.net.Uri
import com.google.android.apps.muzei.api.provider.Artwork
import stoyck.vitrina.network.data.RedditPost
import stoyck.vitrina.network.data.fullPostLink
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TryRetrieveNextMuzeiArtworkUseCase @Inject constructor(
    private val tryRetrieveSingleImage: TryRetrieveSingleImageUseCase
) {

    data class Result(val artwork: Artwork, val temporaryFile: File)

    private fun RedditPost.toArtwork(): Artwork {
        // Reddit calls a user's profile "r/u_name"; display it as "u/name".
        val cleanTitle = if (subredditNamePrefixed.startsWith("r/u_", ignoreCase = true)) {
            "u/" + subredditNamePrefixed.substring(4)
        } else {
            subredditNamePrefixed
        }
        return Artwork(
            token = id,
            title = cleanTitle,
            byline = title,
            attribution = author,
            persistentUri = Uri.parse(url),
            webUri = Uri.parse(fullPostLink)
        )
    }

    suspend operator fun invoke(): Result? {
        /**
         * Image retrieval can only be done one by one
         * This makes sure that cache size is always enough
         */

        val image = tryRetrieveSingleImage()
            ?: return null

        return Result(
            image.originalPost.toArtwork(),
            image.temporaryImageLocation
        )
    }

}