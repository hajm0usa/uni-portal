package ir.hajmousa.uniportal.provider.moodle

import ir.hajmousa.uniportal.core.PortalException
import ir.hajmousa.uniportal.core.PortalException.Reason
import ir.hajmousa.uniportal.data.CourseActivity.Kind
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class MoodleContentParsersTest {
    @Test
    fun `real course excerpt preserves section ids empty weeks distinct same-title activities and order`() {
        val sections = MoodleContentParsers.parseCourse(course(), "1").getOrThrow()
        assertEquals(listOf("100", "101", "104", "107"), sections.map { it.remoteId })
        assertTrue(sections.all { it.providerId == "moodle" && it.courseRemoteId == "1" })
        assertTrue(sections[1].activities.isEmpty())
        assertEquals(listOf("208", "209"), sections[2].activities.map { it.remoteId })
        assertEquals(sections[2].activities[0].title, sections[2].activities[1].title)
        val general = sections.first().activities
        assertEquals(7, general.size)
        assertEquals("پیوند نمونه", general.first().title)
        assertEquals("https://elearn.ut.ac.ir/mod/url/view.php?id=201", general.first().deepLink)
        assertFalse(general.first().deepLink!!.contains("redirect"))
        assertEquals(Kind.LABEL, general[2].kind)
        assertEquals("عنوان نمونه", general[2].title)
        assertNull(general[2].deepLink)
        assertEquals(Kind.FOLDER, general[3].kind)
        assertEquals(Kind.OTHER, general[5].kind)
        assertEquals(Kind.ASSIGNMENT, sections.last().activities.single().kind)
        assertEquals(10, sections.sumOf { it.activities.size })
    }

    @Test
    fun `real folder excerpt maps only files with full scoped identity and decoded Unicode names`() {
        val files = MoodleContentParsers.parseFolder(folder(), "1", "205").getOrThrow()
        assertEquals(2, files.size)
        assertTrue(files.all { it.providerId == "moodle" && it.courseRemoteId == "1" && it.activityRemoteId == "205" })
        assertEquals("کتاب نمونه.zip", files.first().name)
        assertEquals("/pluginfile.php/300/mod_folder/content/0/کتاب نمونه.zip", files.first().remoteId)
        assertEquals("sample.pdf", files.last().name)
        assertEquals("/pluginfile.php/300/mod_folder/content/0/sample.pdf", files.last().remoteId)
        assertTrue(files.first().url.contains("%DA%A9"))
        assertTrue(files.all { it.url.endsWith("?forcedownload=1") })
        assertFalse(files.any { it.url.contains("download_folder") || it.url.contains("theme/image") })
    }

    @Test
    fun `unknown markup optional name attributes and changed display wrappers tolerate safe changes`() {
        val doc = Jsoup.parse(course())
        doc.select("[data-for=section]").forEach { it.removeAttr("data-sectionname"); it.attr("data-future", "value") }
        doc.select(".instancename").forEach { it.tagName("strong") }
        assertEquals(4, MoodleContentParsers.parseCourse(doc.outerHtml(), "1").getOrThrow().size)
        val row = doc.selectFirst("[data-for=cmitem]")!!
        row.removeClass("modtype_url").addClass("modtype_future")
        row.selectFirst("a")!!.attr("href", "https://elearn.ut.ac.ir/mod/future/view.php?id=201")
        val unknown = MoodleContentParsers.parseCourse(doc.outerHtml(), "1").getOrThrow().first().activities.first()
        assertEquals(Kind.OTHER, unknown.kind)
        assertEquals("https://elearn.ut.ac.ir/mod/future/view.php?id=201", unknown.deepLink)
        val noName = Jsoup.parse(course())
        noName.select(".instancename").remove()
        assertEquals("پیوند نمونه", MoodleContentParsers.parseCourse(noName.outerHtml(), "1").getOrThrow().first().activities.first().title)
    }

    @Test
    fun `unsafe action credential off-origin or mismatched activity links are never exposed`() {
        val safe = "https://elearn.ut.ac.ir/mod/url/view.php?id=201"
        listOf("$safe&sesskey=XXXX", "$safe&action=delete", "$safe&id=201", "$safe#fragment",
            safe.replace("https:", "http:"), safe.replace("elearn.ut.ac.ir", "evil.invalid"),
            safe.replace("elearn.ut.ac.ir", "user:pass@elearn.ut.ac.ir"), safe.replace("elearn.ut.ac.ir", "elearn.ut.ac.ir:444"),
            safe.replace("201", "999"), safe.replace("view.php", "delete.php"), "javascript:alert(1)",
        ).forEach { url ->
            val doc = Jsoup.parse(course())
            doc.selectFirst("[data-for=cmitem] .activityname a")!!.attr("href", url)
            assertNull(MoodleContentParsers.parseCourse(doc.outerHtml(), "1").getOrThrow().first().activities.first().deepLink)
        }
    }

    @Test
    fun `unsafe or ambiguous folder links fail the section instead of silently dropping files`() {
        val safe = "https://elearn.ut.ac.ir/pluginfile.php/300/mod_folder/content/0/sample.pdf?forcedownload=1"
        listOf(safe.replace("https:", "http:"), safe.replace("elearn.ut.ac.ir", "evil.invalid"),
            safe.replace("elearn.ut.ac.ir", "user:pass@elearn.ut.ac.ir"), safe.replace("elearn.ut.ac.ir", "elearn.ut.ac.ir:444"),
            "$safe&sesskey=XXXX", "$safe&forcedownload=1", "$safe#fragment", safe.replace("300", "999"),
            safe.replace("mod_folder", "assignsubmission_file"), safe.replace("content/0", "content/1"),
            safe.replace("sample.pdf", "bad%2Fname.pdf"), safe.replace("sample.pdf", "bad%252Fname.pdf"),
            safe.replace("sample.pdf", "bad%00name.pdf"), "javascript:alert(1)",
        ).forEach { url ->
            val doc = Jsoup.parse(folder())
            doc.selectFirst(".foldertree a")!!.attr("href", url)
            assertReason(Reason.PARSE_ERROR, MoodleContentParsers.parseFolder(doc.outerHtml(), "1", "205"))
        }
        val doc = Jsoup.parse(folder())
        doc.selectFirst(".foldertree a")!!.text("../unsafe.zip")
        assertReason(Reason.PARSE_ERROR, MoodleContentParsers.parseFolder(doc.outerHtml(), "1", "205"))
    }

    @Test
    fun `malformed missing duplicate and cross-page content is never empty success`() {
        listOf("", " ", "<broken>", fixture("content_error_synthetic.html"), folder()).forEach {
            assertReason(Reason.PARSE_ERROR, MoodleContentParsers.parseCourse(it, "1"))
        }
        listOf("", "<broken>", course(), fixture("content_error_synthetic.html")).forEach {
            assertReason(Reason.PARSE_ERROR, MoodleContentParsers.parseFolder(it, "1", "205"))
        }
        assertReason(Reason.PARSE_ERROR, MoodleContentParsers.parseCourse(course(), "2"))
        assertReason(Reason.PARSE_ERROR, MoodleContentParsers.parseCourse(course(), "0"))
        assertReason(Reason.PARSE_ERROR, MoodleContentParsers.parseFolder(folder(), "1", "204"))
        val noList = Jsoup.parse(course()).apply { select("[data-for=cmlist]").first()!!.remove() }
        assertReason(Reason.PARSE_ERROR, MoodleContentParsers.parseCourse(noList.outerHtml(), "1"))
        val duplicate = Jsoup.parse(course()).apply {
            val row = selectFirst("[data-for=cmitem]")!!
            row.after(row.clone())
        }
        assertReason(Reason.PARSE_ERROR, MoodleContentParsers.parseCourse(duplicate.outerHtml(), "1"))
        val files = Jsoup.parse(folder()).apply {
            val link = selectFirst(".foldertree a")!!
            link.after(link.clone())
        }
        assertReason(Reason.PARSE_ERROR, MoodleContentParsers.parseFolder(files.outerHtml(), "1", "205"))
    }

    @Test
    fun `synthetic empty structures succeed but missing folder structure fails`() {
        assertTrue(MoodleContentParsers.parseCourse(fixture("course_content_empty_synthetic.html"), "1").getOrThrow().isEmpty())
        assertTrue(MoodleContentParsers.parseFolder(fixture("folder_empty_synthetic.html"), "1", "205").getOrThrow().isEmpty())
        val doc = Jsoup.parse(folder()).apply { select(".foldertree").remove() }
        assertReason(Reason.PARSE_ERROR, MoodleContentParsers.parseFolder(doc.outerHtml(), "1", "205"))
    }

    @Test
    fun `login forms fail safely and parser failures never contain page secrets or exception causes`() {
        assertReason(Reason.SESSION_EXPIRED, MoodleContentParsers.parseCourse(fixture("content_login_synthetic.html"), "1"))
        assertReason(Reason.SESSION_EXPIRED, MoodleContentParsers.parseFolder(fixture("content_login_synthetic.html"), "1", "205"))
        val doc = Jsoup.parse(course()).apply { selectFirst("[data-for=section]")!!.attr("data-id", "private-example-input") }
        assertReason(Reason.PARSE_ERROR, MoodleContentParsers.parseCourse(doc.outerHtml(), "1"))
        assertReason(Reason.PARSE_ERROR, MoodleContentParsers.parseCourse("x".repeat(8 * 1024 * 1024 + 1), "1"))
    }

    // The real fixtures use a reserved host; substitute only the trusted host to exercise production link validation.
    private fun course() = fixture("course_content_ok.html").replace("https://example.invalid", "https://elearn.ut.ac.ir")
    private fun folder() = fixture("folder_ok.html").replace("https://example.invalid", "https://elearn.ut.ac.ir")
    private fun fixture(name: String) = requireNotNull(javaClass.getResource("/fixtures/moodle/$name")).readText()
    private fun assertReason(reason: Reason, result: Result<*>) {
        val error = result.exceptionOrNull()
        assertTrue(error is PortalException)
        assertEquals(reason, (error as PortalException).reason)
        assertNull(error.cause)
        assertFalse(error.stackTraceToString().contains("private-example-input"))
    }
}
