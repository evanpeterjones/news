package opml

import db.Feed
import org.junit.Assert
import org.junit.Test
import java.io.InputStream
import java.nio.charset.Charset
import java.util.UUID

class OpmlTests {

    private val sampleElements = listOf(
        Outline(
            text = "WirelessMoves",
            type = "rss",
            xmlUrl = "https://blog.wirelessmoves.com/feed",
            openEntriesInBrowser = true,
            blockedWords = "abc",
            showPreviewImages = false,
        ),
        Outline(
            text = "Nextcloud",
            type = "rss",
            xmlUrl = "https://nextcloud.com/blogfeed",
            openEntriesInBrowser = false,
            blockedWords = "",
            showPreviewImages = true,
        ),
        Outline(
            text = "PINE64",
            type = "rss",
            xmlUrl = "https://www.pine64.org/feed/",
            openEntriesInBrowser = true,
            blockedWords = "xyz",
            showPreviewImages = null,
        ),
    )

    @Test
    fun readsSampleDocument() {
        val elements = importOpml(readFile("sample.opml"))
        Assert.assertArrayEquals(sampleElements.toTypedArray(), elements.toTypedArray())
    }

    @Test
    fun writesSampleDocument() {
        val feeds = sampleElements.map {
            Feed(
                id = UUID.randomUUID().toString(),
                title = it.text,
                selfLink = it.xmlUrl,
                alternateLink = "",
                openEntriesInBrowser = it.openEntriesInBrowser,
                blockedWords = it.blockedWords,
                showPreviewImages = it.showPreviewImages,
            )
        }

        val opml = exportOpml(feeds)
        val elements = importOpml(opml)
        Assert.assertTrue(opml.lines().size > 1)
        Assert.assertArrayEquals(sampleElements.toTypedArray(), elements.toTypedArray())
    }

    @Test
    fun readNestedOpml() {
        val elements = importOpml(readFile("nested.opml"))
        Assert.assertEquals(6, elements.size)
    }

    @Test
    fun readsMozillaOpml() {
        val elements = importOpml(readFile("mozilla.opml"))
        Assert.assertEquals(2, elements.size)
    }

    private fun readFile(path: String) = javaClass.getResourceAsStream(path)!!.readTextAndClose()

    private fun InputStream.readTextAndClose(charset: Charset = Charsets.UTF_8): String {
        return this.bufferedReader(charset).use { it.readText() }
    }
}