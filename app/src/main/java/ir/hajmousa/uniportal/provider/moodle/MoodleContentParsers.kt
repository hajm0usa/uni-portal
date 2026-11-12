package ir.hajmousa.uniportal.provider.moodle

import ir.hajmousa.uniportal.auth.SsoHosts
import ir.hajmousa.uniportal.core.PortalException
import ir.hajmousa.uniportal.core.PortalException.Reason
import ir.hajmousa.uniportal.data.CourseActivity
import ir.hajmousa.uniportal.data.CourseFile
import ir.hajmousa.uniportal.data.CourseSection
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

internal object MoodleContentParsers {
    fun parseCourse(body: String, courseId: String): Result<List<CourseSection>> = parse {
        val course = id(courseId)
        val document = page(body)
        require(document.body().id() == "page-course-view-weeks" && document.body().hasClass("course-$course"))
        val list = document.select("#region-main .course-content [data-for=course_sectionlist]").singleOrNull() ?: invalid()
        val sections = list.children().map { section ->
            require(section.tagName() == "li" && section.attr("data-for") == "section")
            val remoteId = id(section.attr("data-id"))
            val title = section.attr("data-sectionname").trim().ifBlank { section.selectFirst("h3.sectionname")?.text().orEmpty() }
            require(title.isNotBlank())
            val activities = section.select("[data-for=cmlist]").singleOrNull() ?: invalid()
            CourseSection("moodle", remoteId, course, title, activities.children().map(::activity))
        }
        require(sections.map { it.remoteId }.distinct().size == sections.size)
        val activityIds = sections.flatMap { it.activities }.map { it.remoteId }
        require(activityIds.distinct().size == activityIds.size)
        sections
    }

    fun parseFolder(body: String, courseId: String, activityId: String): Result<List<CourseFile>> = parse {
        val course = id(courseId)
        val module = id(activityId)
        val document = page(body)
        val marker = document.body()
        require(marker.id() == "page-mod-folder-view" && marker.hasClass("course-$course") && marker.hasClass("cmid-$module"))
        val context = marker.classNames().singleOrNull { it.startsWith("context-") }?.removePrefix("context-")?.let(::id) ?: invalid()
        val tree = document.select("#region-main .filemanager ul.foldertree[role=tree]").singleOrNull() ?: invalid()
        val files = tree.select("a[href]").map { link ->
            val url = safeUrl(link.attr("href")) ?: invalid()
            val segments = url.pathSegments
            require(segments.size >= 6 && segments.take(5) == listOf("pluginfile.php", context, "mod_folder", "content", "0"))
            require(segments.drop(5).all(::safePathPart))
            require(url.querySize == 1 && url.queryParameterNames == setOf("forcedownload") && url.queryParameter("forcedownload") == "1")
            val name = link.text().trim()
            require(safePathPart(name))
            // Keep the complete scoped remote path, not the display or local filename, as identity.
            CourseFile("moodle", segments.joinToString("/", prefix = "/"), course, module, name, url.toString())
        }
        require(files.map { it.remoteId }.distinct().size == files.size)
        files
    }

    private fun activity(row: Element): CourseActivity {
        require(row.tagName() == "li" && row.attr("data-for") == "cmitem")
        val remoteId = id(row.attr("data-id"))
        require(row.id() == "module-$remoteId")
        val type = row.classNames().singleOrNull { it.startsWith("modtype_") }?.removePrefix("modtype_") ?: invalid()
        require(type.matches(Regex("[a-z][a-z0-9_]*")))
        val card = row.select("[data-region=activity-card]").singleOrNull() ?: invalid()
        val title = if (type == "label") {
            card.selectFirst(".activity-altcontent")?.text().orEmpty().ifBlank { card.attr("data-activityname") }
        } else {
            val name = card.selectFirst(".activityname .instancename")?.clone()
            name?.select(".accesshide, .visually-hidden")?.remove()
            name?.text().orEmpty().ifBlank { card.attr("data-activityname") }
        }.trim()
        require(title.isNotBlank())
        val kind = when (type) {
            "folder" -> CourseActivity.Kind.FOLDER
            "assign" -> CourseActivity.Kind.ASSIGNMENT
            "label" -> CourseActivity.Kind.LABEL
            else -> CourseActivity.Kind.OTHER
        }
        val link = if (kind == CourseActivity.Kind.LABEL) null else {
            val anchor = card.select(".activityname a[href]").singleOrNull()
            anchor?.attr("href")?.let(::safeUrl)?.takeIf {
                it.encodedPath == "/mod/$type/view.php" && it.querySize == 1 &&
                    it.queryParameterNames == setOf("id") && it.queryParameter("id") == remoteId
            }?.toString()
        }
        return CourseActivity("moodle", remoteId, title, kind, link)
    }

    private fun page(body: String): Document {
        require(body.isNotBlank() && body.length <= 8 * 1024 * 1024)
        val document = Jsoup.parse(body)
        // A password form is a login response, not an empty course/folder; real expiry redirects are also checked by the client.
        if (document.select("input[type=password]").isNotEmpty()) throw PortalException(Reason.SESSION_EXPIRED)
        return document
    }

    private fun safeUrl(value: String): HttpUrl? = value.toHttpUrlOrNull()?.takeIf {
        it.scheme == "https" && it.host == SsoHosts.MOODLE_HOST && it.port == 443 &&
            it.username.isEmpty() && it.password.isEmpty() && it.fragment == null
    }

    private fun safePathPart(value: String): Boolean = value.isNotBlank() && value !in setOf(".", "..") &&
        value.none { it == '/' || it == '\\' || it.isISOControl() } &&
        !Regex("(?i)%(?:2f|5c|00)").containsMatchIn(value)

    private fun id(value: String): String {
        require(value.matches(Regex("[1-9][0-9]*")) && value.toLongOrNull() != null)
        return value
    }

    private fun invalid(): Nothing = throw PortalException(Reason.PARSE_ERROR)

    private inline fun <T> parse(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (error: PortalException) {
        Result.failure(error)
    } catch (_: IllegalArgumentException) {
        Result.failure(PortalException(Reason.PARSE_ERROR))
    }
}
