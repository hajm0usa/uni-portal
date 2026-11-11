package ir.hajmousa.uniportal.provider.moodle

import ir.hajmousa.uniportal.core.PortalException
import ir.hajmousa.uniportal.core.PortalException.Reason
import ir.hajmousa.uniportal.data.AgendaItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class MoodleCalendarParserTest {
    @Test
    fun `real sanitized assignment maps to shared epoch-based model without private URLs or HTML`() {
        val item = MoodleParsers.parseAgenda(fixture("calendar_ok.json")).getOrThrow().single()
        assertEquals("moodle", item.providerId)
        assertEquals("101", item.remoteId)
        assertEquals("مهلت تکلیف نمونه", item.title)
        assertEquals(1700006400L, item.startEpoch)
        assertEquals(AgendaItem.Kind.DEADLINE, item.kind)
        assertEquals("301", item.courseRemoteId)
        assertNull(item.deepLink)
        assertFalse(item.toString().contains("sesskey"))
        assertFalse(item.toString().contains("<a"))
    }

    @Test
    fun `optional absent null and unknown fields work without guessing exam mappings`() {
        val item = MoodleParsers.parseAgenda(response("""{"id":1,"activityname":"Sample","timestart":1700000000,"future":{}}""")).getOrThrow().single()
        assertEquals("Sample", item.title)
        assertEquals(AgendaItem.Kind.OTHER, item.kind)
        assertNull(item.courseRemoteId)
        assertNull(item.deepLink)
        val quiz = MoodleParsers.parseAgenda(changed("modulename" to JsonPrimitive("quiz"), "course" to null, "url" to null)).getOrThrow().single()
        assertEquals(AgendaItem.Kind.OTHER, quiz.kind)
        assertNull(quiz.courseRemoteId)
        assertNull(quiz.deepLink)
    }

    @Test
    fun `only plain read-only Moodle assignment URLs reach shared models`() {
        val safe = "https://elearn.ut.ac.ir/mod/assign/view.php?id=201"
        assertEquals(safe, MoodleParsers.parseAgenda(changed("url" to JsonPrimitive(safe))).getOrThrow().single().deepLink)
        listOf(
            "$safe&sesskey=XXXX", "$safe&action=editsubmission", "$safe&id=202", "$safe#fragment",
            safe.replace("https:", "http:"), safe.replace("elearn.ut.ac.ir", "evil.invalid"),
            safe.replace("elearn.ut.ac.ir", "user:pass@elearn.ut.ac.ir"), safe.replace("elearn.ut.ac.ir", "elearn.ut.ac.ir:444"),
            safe.replace("view.php", "delete.php"), safe.replace("201", "0"), "javascript:alert(1)", "not a URL",
        ).forEach { url ->
            assertNull(MoodleParsers.parseAgenda(changed("url" to JsonPrimitive(url))).getOrThrow().single().deepLink)
        }
    }

    @Test
    fun `synthetic empty page is not confused with missing or malformed events`() {
        assertTrue(MoodleParsers.parseAgenda(fixture("calendar_empty_synthetic.json")).getOrThrow().isEmpty())
        listOf("", "{", "null", "[]", "[{},{}]", """[{"error":false,"data":{}}]""",
            """[{"error":false,"data":{"events":null}}]""", response("{}"),
            response("""{"id":1,"name":"Sample","timestart":0}"""),
            response("""{"id":1,"name":"Sample","timestart":-1}"""),
            response("""{"id":0,"name":"Sample","timestart":1700000000}"""),
            response("""{"id":1,"name":" ","timestart":1700000000}"""),
            response("""{"id":1,"name":{},"timestart":1700000000}"""),
            response("""{"id":1,"name":"Sample","timestart":1700000000},{"id":1,"name":"Sample","timestart":1700000000}"""),
        ).forEach { assertReason(Reason.PARSE_ERROR, it) }
    }

    @Test
    fun `synthetic errors and HTML return safe typed failures`() {
        assertReason(Reason.SESSION_EXPIRED, fixture("calendar_expired_session_synthetic.json"))
        assertReason(Reason.SERVER_ERROR, fixture("calendar_error_object_synthetic.json"))
        assertReason(Reason.SESSION_EXPIRED, "\uFEFF \n" + fixture("calendar_login_page_synthetic.html"))
        assertReason(Reason.INVALID_SESSION_KEY, """[{"error":true,"exception":{"errorcode":"invalidsesskey"}}]""")
        assertReason(Reason.SERVER_ERROR, """[{"error":{"message":"private-example-text"}}]""")
        assertReason(Reason.PARSE_ERROR, response("""{"id":1,"name":{"private-example-text":true}}"""))
        assertTrue(MoodleParsers.parseAgenda("\uFEFF \n" + fixture("calendar_ok.json")).isSuccess)
    }

    private fun changed(vararg fields: Pair<String, kotlinx.serialization.json.JsonElement?>): String {
        val event = Json.parseToJsonElement(fixture("calendar_ok.json")).jsonArray.single()
            .jsonObject.getValue("data").jsonObject.getValue("events").jsonArray.single().jsonObject.toMutableMap()
        fields.forEach { (key, value) -> if (value == null) event.remove(key) else event[key] = value }
        return response(JsonObject(event).toString())
    }

    private fun response(events: String) = """[{"error":false,"data":{"events":[$events]}}]"""
    private fun fixture(name: String) = requireNotNull(javaClass.getResource("/fixtures/moodle/$name")).readText()

    private fun assertReason(reason: Reason, body: String) {
        val error = MoodleParsers.parseAgenda(body).exceptionOrNull()
        assertTrue(error is PortalException)
        assertEquals(reason, (error as PortalException).reason)
        assertNull(error.cause)
        assertFalse(error.stackTraceToString().contains("private-example-text"))
    }
}
