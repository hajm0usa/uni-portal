package ir.hajmousa.uniportal.provider.moodle

import ir.hajmousa.uniportal.core.PortalException
import ir.hajmousa.uniportal.core.PortalException.Reason
import ir.hajmousa.uniportal.data.Course
import ir.hajmousa.uniportal.data.AgendaItem
import ir.hajmousa.uniportal.auth.SsoHosts
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

internal data class CoursePage(val courses: List<Course>, val nextOffset: Int?)

internal object MoodleParsers {
    private val json = Json { ignoreUnknownKeys = true }
    private val configAssignment = Regex("""(?<![\w$.])M\.cfg\s*=\s*\{""")

    fun extractSesskey(html: String): Result<String> = parse {
        val assignment = configAssignment.find(html) ?: fail(Reason.SESSION_EXPIRED)
        val config = json.decodeFromString<ConfigDto>(configObject(html, assignment.range.last))
        val key = config.sesskey
        if (key == null || key.isBlank()) fail(Reason.SESSION_EXPIRED)
        key
    }

    fun parseCoursePage(body: String): Result<CoursePage> = parse {
        val page = json.decodeFromJsonElement<CoursePageDto>(responseData(body))
        val courses = (page.courses ?: fail(Reason.PARSE_ERROR)).map { course ->
            val id = course.id?.takeIf { it > 0 } ?: fail(Reason.PARSE_ERROR)
            Course("moodle", id.toString(), course.fullname, course.shortname)
        }
        if (courses.map { it.remoteId }.toSet().size != courses.size) fail(Reason.PARSE_ERROR)
        CoursePage(courses, page.nextoffset)
    }

    fun parseAgenda(body: String): Result<List<AgendaItem>> = parse {
        val page = json.decodeFromJsonElement<CalendarDto>(responseData(body))
        val items = (page.events ?: fail(Reason.PARSE_ERROR)).map { event ->
            val id = event.id?.takeIf { it > 0 } ?: fail(Reason.PARSE_ERROR)
            val title = event.name?.takeIf { it.isNotBlank() }
                ?: event.activityname?.takeIf { it.isNotBlank() } ?: fail(Reason.PARSE_ERROR)
            val start = event.timestart?.takeIf { it > 0 } ?: fail(Reason.PARSE_ERROR)
            val deadline = event.component == "mod_assign" && event.modulename == "assign" && event.eventtype == "due"
            AgendaItem(
                providerId = "moodle",
                remoteId = id.toString(),
                title = title,
                startEpoch = start,
                // Only assignment deadlines have a confirmed mapping; do not guess quiz/exam types.
                kind = if (deadline) AgendaItem.Kind.DEADLINE else AgendaItem.Kind.OTHER,
                courseRemoteId = event.course?.id?.takeIf { it > 0 }?.toString(),
                deepLink = if (deadline) assignmentLink(event.url) else null,
            )
        }
        if (items.map { it.remoteId }.toSet().size != items.size) fail(Reason.PARSE_ERROR)
        items
    }

    private fun assignmentLink(value: String?): String? {
        val url = value?.toHttpUrlOrNull() ?: return null
        // Never expose edit/delete/submission links or carry credentials into cached models.
        return url.takeIf {
            it.scheme == "https" && it.host == SsoHosts.MOODLE_HOST && it.port == 443 &&
                it.username.isEmpty() && it.password.isEmpty() && it.fragment == null &&
                it.encodedPath == "/mod/assign/view.php" && it.querySize == 1 &&
                it.queryParameterNames == setOf("id") && (it.queryParameter("id")?.toLongOrNull() ?: 0) > 0
        }?.toString()
    }

    private fun responseData(body: String): kotlinx.serialization.json.JsonElement {
        val text = body.trimStart { it.isWhitespace() || it == '\uFEFF' }
        if (text.startsWith('<')) fail(Reason.SESSION_EXPIRED)
        val envelope = (json.parseToJsonElement(text) as? JsonArray)?.singleOrNull() as? JsonObject
            ?: fail(Reason.PARSE_ERROR)
        val error = envelope["error"] ?: fail(Reason.PARSE_ERROR)
        if (error != JsonPrimitive(false)) {
            if (error != JsonPrimitive(true) && error !is JsonObject) fail(Reason.PARSE_ERROR)
            val details = error as? JsonObject ?: envelope["exception"] as? JsonObject
            val code = (details?.get("errorcode") as? JsonPrimitive)?.content
            fail(when (code) {
                "invalidsesskey" -> Reason.INVALID_SESSION_KEY
                "servicerequireslogin" -> Reason.SESSION_EXPIRED
                else -> Reason.SERVER_ERROR
            })
        }
        return envelope["data"] ?: fail(Reason.PARSE_ERROR)
    }

    private fun configObject(html: String, start: Int): String {
        var depth = 0
        var quoted = false
        var escaped = false
        // Balance braces without interpreting braces or semicolons inside JSON strings.
        for (index in start until html.length) {
            val character = html[index]
            if (quoted) {
                when {
                    escaped -> escaped = false
                    character == '\\' -> escaped = true
                    character == '"' -> quoted = false
                }
            } else {
                when (character) {
                    '"' -> quoted = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return html.substring(start, index + 1)
                    }
                }
            }
        }
        fail(Reason.PARSE_ERROR)
    }

    private inline fun <T> parse(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (error: PortalException) {
        Result.failure(error)
    } catch (_: IllegalArgumentException) {
        Result.failure(PortalException(Reason.PARSE_ERROR))
    }

    private fun fail(reason: Reason): Nothing = throw PortalException(reason)

    @Serializable
    private data class ConfigDto(val sesskey: String? = null)

    @Serializable
    private data class CoursePageDto(val courses: List<CourseDto>? = null, val nextoffset: Int? = null)

    @Serializable
    private data class CourseDto(val id: Long? = null, val fullname: String? = null, val shortname: String? = null)

    @Serializable
    private data class CalendarDto(val events: List<CalendarEventDto>? = null)

    @Serializable
    private data class CalendarEventDto(
        val id: Long? = null,
        val name: String? = null,
        val activityname: String? = null,
        val component: String? = null,
        val modulename: String? = null,
        val eventtype: String? = null,
        val timestart: Long? = null,
        val course: CourseDto? = null,
        val url: String? = null,
    )
}
