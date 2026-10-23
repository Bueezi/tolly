package be.tolly.core.network

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class UltraApiTest {
    private val server = MockWebServer()
    private lateinit var api: UltraApi

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        isLenient = true
    }

    @Before
    fun setUp() {
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(UltraApi::class.java)
    }

    @After
    fun tearDown() = server.close()

    private fun enqueue(body: String) {
        server.enqueue(MockResponse.Builder().code(200).addHeader("Content-Type", "application/json").body(body).build())
    }

    @Test
    fun followsMembershipPagination() = runTest {
        enqueue(
            """
            {"results":[{"id":"_1_1","courseId":"_10_1","availability":{"available":"Yes"},"courseRoleId":"Student",
              "course":{"id":"_10_1","courseId":"ULTRA-B-KUL-G0T42a-2627","name":"Logica voor informatici [G0T42a]",
                        "organization":false,"availability":{"available":"Yes","duration":{"type":"Continuous"}}}}],
             "paging":{"nextPage":"/learn/api/public/v1/users/me/courses?offset=1&limit=1"}}
            """.trimIndent(),
        )
        enqueue(
            """
            {"results":[{"courseId":"_11_1","course":{"id":"_11_1","courseId":"ULTRA-C-1-K","name":"Community","organization":true,
              "availability":{"available":"No"},"unknownField":42}}]}
            """.trimIndent(),
        )

        val first = api.myCourses()
        val all = collectAllPages(first) { api.myCoursesPage(it) }

        assertEquals(2, all.size)
        assertEquals("ULTRA-B-KUL-G0T42a-2627", all[0].course?.courseId)
        assertTrue(all[1].course!!.organization)
        assertEquals("/learn/api/public/v1/users/me/courses", server.takeRequest().url.encodedPath)
        assertEquals("1", server.takeRequest().url.queryParameter("offset"))
    }

    @Test
    fun parsesContentHandlers() = runTest {
        enqueue(
            """
            {"results":[
              {"id":"_1_1","parentId":"_0_1","title":"Cursusmateriaal","position":0,"hasChildren":true,
               "availability":{"available":"Yes"},"contentHandler":{"id":"resource/x-bb-folder","isBbPage":true}},
              {"id":"_2_1","title":"slides.pdf","contentHandler":{"id":"resource/x-bb-file",
               "file":{"fileName":"slides.pdf","mimeType":"application/pdf"}},
               "links":[{"href":"/ultra/redirect?redirectType=nautilus&courseId=_9_1&contentId=_2_1","rel":"alternate"}]},
              {"id":"_3_1","title":"ECTS","contentHandler":{"id":"resource/x-bb-externallink","url":"https://example.org"}}
            ],"paging":{"nextPage":""}}
            """.trimIndent(),
        )

        val page = api.children("_9_1", "_0_1")

        assertNull(page.nextPage)
        assertEquals(true, page.results[0].contentHandler?.isBbPage)
        assertEquals("application/pdf", page.results[1].contentHandler?.file?.mimeType)
        assertEquals("https://example.org", page.results[2].contentHandler?.url)
        assertEquals("/learn/api/public/v1/courses/_9_1/contents/_0_1/children", server.takeRequest().url.encodedPath)
    }

    @Test
    fun parsesInternalAnnouncementsWithReadStatus() = runTest {
        enqueue(
            """
            {"results":[{"id":"_5_1","title":"Exam info","body":{"rawText":"<p>raw</p>","displayText":"<div><p>display</p></div>"},
              "createdDate":"2026-10-16T08:23:45.506Z","readStatus":{"isRead":false,"updateDate":null},"isDraft":false}],
             "paging":{"limit":100,"nextPage":"","count":1,"offset":0}}
            """.trimIndent(),
        )

        val result = api.announcementsWithReadStatus("_9_1").results.single()

        assertEquals("Exam info", result.title)
        assertFalse(result.readStatus!!.isRead)
        assertEquals("<div><p>display</p></div>", result.body?.displayText)
    }
}
