package ir.hajmousa.uniportal.provider.moodle

import ir.hajmousa.uniportal.core.PortalException
import ir.hajmousa.uniportal.core.PortalException.Reason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoodleParsersTest {
    @Test
    fun `real sanitized course fixture maps to shared models and retains offset without interpreting it`() {
        val page = MoodleParsers.parseCoursePage(fixture("courses_ok.json")).getOrThrow()
        assertEquals(21, page.courses.size)
        assertEquals(21, page.nextOffset)
        assertTrue(page.courses.all { it.providerId == "moodle" })
        assertEquals("1", page.courses.first().remoteId)
        assertEquals("درس نمونه ۱", page.courses.first().name)
        assertEquals("COURSE-01", page.courses.first().shortName)
        assertEquals("21", page.courses.last().remoteId)
    }

    @Test
    fun `unknown fields and absent optional fields do not break mapping`() {
        val page = MoodleParsers.parseCoursePage(
            """[{"error":false,"extra":true,"data":{"courses":[{"id":1,"future":{"value":true}}],"extra":null}}]""",
        ).getOrThrow()
        assertEquals(1, page.courses.size)
        assertNull(page.courses.single().name)
        assertNull(page.courses.single().shortName)
        assertNull(page.nextOffset)
        val nullable = MoodleParsers.parseCoursePage(
            """[{"error":false,"data":{"courses":[{"id":1,"fullname":null,"shortname":null}],"nextoffset":null}}]""",
        ).getOrThrow()
        assertNull(nullable.courses.single().name)
        assertNull(nullable.nextOffset)
    }

    @Test
    fun `synthetic empty page succeeds without claiming pagination termination`() {
        val page = MoodleParsers.parseCoursePage(fixture("courses_empty_synthetic.json")).getOrThrow()
        assertTrue(page.courses.isEmpty())
        assertEquals(0, page.nextOffset)
    }

    @Test
    fun `offset values are metadata rather than a guessed end of list rule`() {
        val page = MoodleParsers.parseCoursePage(
            """[{"error":false,"data":{"courses":[],"nextoffset":-1}}]""",
        ).getOrThrow()
        assertEquals(-1, page.nextOffset)
    }

    @Test
    fun `synthetic session and server errors become typed failures`() {
        assertFailure("courses_expired_session_synthetic.json", Reason.SESSION_EXPIRED)
        assertFailure("courses_invalid_sesskey_synthetic.json", Reason.INVALID_SESSION_KEY)
        assertFailure("courses_error_object_synthetic.json", Reason.SERVER_ERROR)
        assertFailure("courses_login_page_synthetic.html", Reason.SESSION_EXPIRED)
        val errorObject = """[{"error":{"errorcode":"servicerequireslogin"}}]"""
        assertReason(Reason.SESSION_EXPIRED, MoodleParsers.parseCoursePage(errorObject))
    }

    @Test
    fun `malformed empty ambiguous or incomplete responses never look like empty success`() {
        listOf(
            "", " ", "{", "null", "[]", "{}", "[false]", "[{},{}]",
            """[{"data":{"courses":[]}}]""",
            """[{"error":"false","data":{"courses":[]}}]""",
            """[{"error":null,"data":{"courses":[]}}]""",
            """[{"error":false}]""",
            """[{"error":false,"data":null}]""",
            """[{"error":false,"data":{}}]""",
            """[{"error":false,"data":{"courses":null}}]""",
            """[{"error":false,"data":{"courses":[{}]}}]""",
            """[{"error":false,"data":{"courses":[{"id":0}]}}]""",
            """[{"error":false,"data":{"courses":[{"id":-1}]}}]""",
            """[{"error":false,"data":{"courses":[{"id":1},{"id":1}]}}]""",
            """[{"error":false,"data":{"courses":[{"id":1,"fullname":{}}]}}]""",
        ).forEach { assertReason(Reason.PARSE_ERROR, MoodleParsers.parseCoursePage(it)) }
    }

    @Test
    fun `BOM whitespace and HTML are handled before JSON decoding`() {
        assertTrue(MoodleParsers.parseCoursePage("\uFEFF \n" + fixture("courses_ok.json")).isSuccess)
        assertReason(Reason.SESSION_EXPIRED, MoodleParsers.parseCoursePage("\uFEFF \n<html>"))
    }

    @Test
    fun `deep JSON cannot overflow parsing or unknown config skipping`() {
        val nested = "[".repeat(2000) + "0" + "]".repeat(2000)
        assertReason(Reason.PARSE_ERROR, MoodleParsers.parseCoursePage(nested))
        assertEquals("XXXX", MoodleParsers.extractSesskey("M.cfg={\"extra\":$nested,\"sesskey\":\"XXXX\"};").getOrThrow())
    }

    @Test
    fun `sesskey is extracted from the real sanitized config fragment`() {
        assertEquals("XXXX", MoodleParsers.extractSesskey(fixture("moodle_config_ok.html")).getOrThrow())
    }

    @Test
    fun `config scanning respects nested objects escaped quotes braces and semicolons`() {
        val html = """<script>M.cfg={"extra":{"text":"escaped \" }; {"},"sesskey":"XXXX"}; unrelated();</script>"""
        assertEquals("XXXX", MoodleParsers.extractSesskey(html).getOrThrow())
        assertEquals("XXXX", MoodleParsers.extractSesskey("M.cfg = \n{\n\"sesskey\":\"XXXX\"\n};").getOrThrow())
    }

    @Test
    fun `missing or blank config keys expire the session and unrelated keys are ignored`() {
        listOf(
            "", "<html>Sign in</html>", """<script>other={"sesskey":"XXXX"};</script>""",
            "M.cfg={};", """M.cfg={"sesskey":null};""", """M.cfg={"sesskey":" "};""",
            """otherM.cfg={"sesskey":"XXXX"};""",
        ).forEach { assertReason(Reason.SESSION_EXPIRED, MoodleParsers.extractSesskey(it)) }
        listOf("M.cfg={", "M.cfg={bad};", """M.cfg={"sesskey":{}};""", """M.cfg={"sesskey":123};""").forEach {
            assertReason(Reason.PARSE_ERROR, MoodleParsers.extractSesskey(it))
        }
    }

    @Test
    fun `failure objects never retain input messages or deserialization causes`() {
        val privateText = "private-example-message"
        val input = """[{"error":true,"exception":{"errorcode":"syntheticerror","message":"$privateText"}}]"""
        val malformed = """[{"error":false,"data":{"courses":[{"id":1,"fullname":{"message":"$privateText"}}]}}]"""
        listOf(input, malformed).forEach { body ->
            val failure = MoodleParsers.parseCoursePage(body).exceptionOrNull()
            assertTrue(failure is PortalException)
            assertNull(failure!!.cause)
            assertFalse(failure.stackTraceToString().contains(privateText))
        }
    }

    private fun fixture(name: String): String = requireNotNull(
        javaClass.getResource("/fixtures/moodle/$name"),
    ).readText()

    private fun assertFailure(name: String, reason: Reason) = assertReason(reason, MoodleParsers.parseCoursePage(fixture(name)))

    private fun assertReason(reason: Reason, result: Result<*>) {
        assertTrue("Expected a typed failure", result.exceptionOrNull() is PortalException)
        assertEquals(reason, (result.exceptionOrNull() as PortalException).reason)
    }
}
