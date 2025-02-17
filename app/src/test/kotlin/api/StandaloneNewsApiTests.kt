package api

import api.standalone.StandaloneNewsApi
import db.EntryQueries
import db.FeedQueries
import io.mockk.mockk
import okhttp3.OkHttpClient
import org.junit.Before

class StandaloneNewsApiTests {

    private lateinit var feedQueries: FeedQueries
    private lateinit var entryQueries: EntryQueries
    private lateinit var http: OkHttpClient

    private lateinit var api: StandaloneNewsApi

    @Before
    fun setup() {
        feedQueries = mockk()
        entryQueries = mockk()
        http = mockk()

        api = StandaloneNewsApi(
            feedQueries = feedQueries,
            entryQueries = entryQueries,
        )
    }

//    @Test
//    fun `add feed + 404`(): Unit = runBlocking {
//        val url = URL("https://example.com")
//        val response = mockk<Response>()
//        val call = mockk<Call>()
//        every { call.execute() } returns response
//        coEvery { http.newCall(any()) } returns call
//        every { response.isSuccessful } returns false
//        every { response.code } returns 404
//
//        Assert.assertThrows(Exception::class.java) {
//            runBlocking { api.addFeed(url) }
//        }
//    }
}