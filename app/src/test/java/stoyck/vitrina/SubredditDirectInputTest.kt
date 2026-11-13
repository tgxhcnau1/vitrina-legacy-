package stoyck.vitrina

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import stoyck.vitrina.domain.usecase.TryAddSubredditUseCase
import stoyck.vitrina.network.RedditRssParser
import stoyck.vitrina.network.data.PostHint
import stoyck.vitrina.persistence.data.PersistedSubredditData

class SubredditDirectInputTest {

    @Test
    fun normalizeSubredditName_handlesPlainPrefixedUserAndUrlInputs() {
        assertEquals("EarthPorn", TryAddSubredditUseCase.normalizeSubredditName("EarthPorn"))
        assertEquals("EarthPorn", TryAddSubredditUseCase.normalizeSubredditName("  EarthPorn  "))
        assertEquals("EarthPorn", TryAddSubredditUseCase.normalizeSubredditName("r/EarthPorn"))
        assertEquals("EarthPorn", TryAddSubredditUseCase.normalizeSubredditName("/r/EarthPorn/"))
        assertEquals("EarthPorn", TryAddSubredditUseCase.normalizeSubredditName("r/EarthPorn/top"))
        assertEquals(
            "EarthPorn",
            TryAddSubredditUseCase.normalizeSubredditName("https://www.reddit.com/r/EarthPorn/comments/abc123/")
        )
        assertEquals("u/Alaric_Darconville", TryAddSubredditUseCase.normalizeSubredditName("u/Alaric_Darconville"))
        assertEquals("u/Alaric_Darconville", TryAddSubredditUseCase.normalizeSubredditName("/u/Alaric_Darconville/"))
        assertEquals(
            "u/Alaric_Darconville",
            TryAddSubredditUseCase.normalizeSubredditName("https://www.reddit.com/user/Alaric_Darconville/")
        )
        assertEquals("running", TryAddSubredditUseCase.normalizeSubredditName("running"))
    }

    @Test
    fun fromDirectInput_generatesDistinctDeterministicIds() {
        val first = PersistedSubredditData.fromDirectInput("EarthPorn")
        val second = PersistedSubredditData.fromDirectInput("u/Alaric_Darconville")

        assertEquals("direct_earthporn", first.id)
        assertEquals("direct_u_alaric_darconville", second.id)
        assertNotEquals(first.id, second.id)
    }

    @Test
    fun redditRssParser_extractsFullResImagePostsWithoutApiKey() {
        val sampleAtom = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <entry>
                <author><name>/u/Alaric_Darconville</name></author>
                <category term="EarthPorn" label="r/EarthPorn"/>
                <content type="html">&lt;span&gt;&lt;a href=&quot;https://i.redd.it/l0xu5t5s8t0i1.jpeg&quot;&gt;[link]&lt;/a&gt;&lt;/span&gt;</content>
                <id>t3_1xsr36x</id>
                <link href="https://www.reddit.com/r/EarthPorn/comments/1xsr36x/morning_light/" />
                <title>Morning light at Blue Lake</title>
              </entry>
            </feed>
        """.trimIndent()

        val posts = RedditRssParser.parse(sampleAtom, fallbackSubreddit = "EarthPorn")
        assertEquals(1, posts.size)
        assertEquals("https://i.redd.it/l0xu5t5s8t0i1.jpeg", posts[0].url)
        assertEquals(PostHint.Image, posts[0].postHint)
    }
}
