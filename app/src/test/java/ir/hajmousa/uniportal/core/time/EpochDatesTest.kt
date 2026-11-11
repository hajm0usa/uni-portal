package ir.hajmousa.uniportal.core.time

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class EpochDatesTest {
    @Test
    fun `Tehran date crosses midnight independently of UTC date`() {
        assertEquals(LocalDate.of(2026, 11, 10), tehranDate(Instant.parse("2026-11-10T20:29:59Z").epochSecond))
        assertEquals(LocalDate.of(2026, 11, 11), tehranDate(Instant.parse("2026-11-10T20:30:00Z").epochSecond))
    }
}
