package ir.hajmousa.uniportal.core

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FixturePrivacyTest {
    @Test
    fun `fixtures contain no obvious private data`() {
        val root = File(requireNotNull(javaClass.getResource("/fixtures")).toURI())
        val files = root.walkTopDown().filter { it.isFile && it.extension in setOf("json", "html") }.toList()
        assertTrue("Expected parser fixtures", files.isNotEmpty())
        files.forEach { file ->
            assertFalse("Potential private data in ${file.relativeTo(root)}", containsPrivateData(file.readText()))
        }
    }

    @Test
    fun `privacy check catches secrets and allows placeholder credentials and epochs`() {
        listOf(
            "student@example.invalid",
            "123456789",
            "MoodleSession=private-cookie",
            """{"sesskey":"private-key"}""",
            """{"token":"private-token"}""",
            """{"ticket":"private-ticket"}""",
            """{"code":"private-code"}""",
            """{"state":"private-state"}""",
            """{"session_state":"private-session-state"}""",
            "https://example.invalid/?sesskey=private-key",
            "https://example.invalid/?token=private-token",
            "https://example.invalid/?ticket=private-ticket",
            "https://example.invalid/?code=private-code",
            "https://example.invalid/?state=private-state",
            "https://example.invalid/?session_state=private-session-state",
            "https://example.invalid/?redirect_uri=https%3A%2F%2Fexample.invalid%3Fcode%3Dprivate-code",
            "https://example.invalid/?redirect_uri=https%3A%2F%2Fexample.invalid%3Fstate%3Dpath%26sesskey%3Dprivate-key",
            """<input name="__RequestVerificationToken" value="private-token">""",
            """<input value="private-password" name="Password">""",
            """<input name="Username" value="private-username">""",
        ).forEach { assertTrue("Expected private-data detection", containsPrivateData(it)) }
        assertFalse(containsPrivateData("""{"sesskey":"XXXX","startdate":1700000000,"enddate":1710000000}"""))
        assertFalse(containsPrivateData("https://example.invalid/?token=XXXX"))
        assertFalse(containsPrivateData("""{"code":"XXXX","state":"XXXX","session_state":"XXXX"}"""))
        assertFalse(containsPrivateData("https://example.invalid/?redirect_uri=https%3A%2F%2Fexample.invalid%3Fcode%3DXXXX"))
        assertFalse(containsPrivateData("""<input name="__RequestVerificationToken" value="XXXX"><input name="Password" value="">"""))
    }

    private fun containsPrivateData(text: String): Boolean {
        // Epoch fields are intentionally long numbers; other long digit runs need review.
        val withoutEpochs = text.replace(Regex(""""(?:startdate|enddate)"\s*:\s*[0-9]+"""), "")
        val secretNames = "sesskey|token|ticket|code|state|session_state"
        val privateInput = Regex("""<input\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(text).any { input ->
            val attributes = Regex("""([\w-]+)\s*=\s*(["'])(.*?)\2""").findAll(input.value)
                .associate { it.groupValues[1].lowercase() to it.groupValues[3] }
            attributes["name"]?.lowercase() in setOf("username", "password", "__requestverificationtoken") &&
                attributes["value"].orEmpty() !in setOf("", "XXXX")
        }
        return '@' in text ||
            privateInput ||
            Regex("""[0-9]{8,}""").containsMatchIn(withoutEpochs) ||
            Regex("""(?i)MoodleSession\s*=""").containsMatchIn(text) ||
            Regex("""(?i)"(?:$secretNames)"\s*:\s*"(?!XXXX")[^"]*"""").containsMatchIn(text) ||
            Regex("""(?i)(?:[?&]|%3f|%26)(?:$secretNames)(?:=|%3d)(?!XXXX(?:[&#"\s]|%26|%23|$))[^&#"\s]+""").containsMatchIn(text)
    }
}
