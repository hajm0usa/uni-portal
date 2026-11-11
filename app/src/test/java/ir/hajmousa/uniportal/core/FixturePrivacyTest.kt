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
            "https://example.invalid/?sesskey=private-key",
            "https://example.invalid/?token=private-token",
            "https://example.invalid/?ticket=private-ticket",
        ).forEach { assertTrue("Expected private-data detection", containsPrivateData(it)) }
        assertFalse(containsPrivateData("""{"sesskey":"XXXX","startdate":1700000000,"enddate":1710000000}"""))
        assertFalse(containsPrivateData("https://example.invalid/?token=XXXX"))
    }

    private fun containsPrivateData(text: String): Boolean {
        // Epoch fields are intentionally long numbers; other long digit runs need review.
        val withoutEpochs = text.replace(Regex(""""(?:startdate|enddate)"\s*:\s*[0-9]+"""), "")
        return '@' in text ||
            Regex("""[0-9]{8,}""").containsMatchIn(withoutEpochs) ||
            Regex("""(?i)MoodleSession\s*=""").containsMatchIn(text) ||
            Regex("""(?i)"(?:sesskey|token|ticket)"\s*:\s*"(?!XXXX")[^"]*"""").containsMatchIn(text) ||
            Regex("""(?i)[?&](?:sesskey|token|ticket)=(?!XXXX(?:[&#"\s]|$))[^&#"\s]+""").containsMatchIn(text)
    }
}
