package ir.hajmousa.uniportal.core.time

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class PersianDateTimeTest {
    @Test
    fun `Tehran midnight at Nowruz uses Jalali date and Persian digits`() {
        assertEquals("۱۴۰۳/۰۱/۰۱ ۰۰:۰۰", formatPersianDateTime(Instant.parse("2024-03-19T20:30:00Z").epochSecond))
        assertEquals("۱۴۰۳/۰۱/۰۱", formatPersianDate(Instant.parse("2024-03-19T20:30:00Z").epochSecond))
    }

    @Test
    fun `Persian leap-year final day formats correctly`() {
        assertEquals("۱۴۰۳/۱۲/۳۰ ۰۰:۰۰", formatPersianDateTime(Instant.parse("2025-03-19T20:30:00Z").epochSecond))
    }
}
