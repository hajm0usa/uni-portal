package ir.hajmousa.uniportal.provider.moodle

import ir.hajmousa.uniportal.core.PortalException
import ir.hajmousa.uniportal.core.PortalException.Reason
import ir.hajmousa.uniportal.data.Course
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
        val page = json.decodeFromJsonElement<CoursePageDto>(envelope["data"] ?: fail(Reason.PARSE_ERROR))
        val courses = (page.courses ?: fail(Reason.PARSE_ERROR)).map { course ->
            val id = course.id?.takeIf { it > 0 } ?: fail(Reason.PARSE_ERROR)
            Course("moodle", id.toString(), course.fullname, course.shortname)
        }
        if (courses.map { it.remoteId }.toSet().size != courses.size) fail(Reason.PARSE_ERROR)
        CoursePage(courses, page.nextoffset)
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
}
