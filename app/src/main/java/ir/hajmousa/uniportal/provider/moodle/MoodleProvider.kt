package ir.hajmousa.uniportal.provider.moodle

import ir.hajmousa.uniportal.data.Course
import ir.hajmousa.uniportal.data.AgendaItem
import ir.hajmousa.uniportal.data.DataSource
import ir.hajmousa.uniportal.data.Provider
import ir.hajmousa.uniportal.data.ProviderSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CookieJar
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

internal class MoodleProvider(private val client: MoodleAjaxClient, private val clock: Clock = Clock.systemUTC()) : Provider {
    constructor(cookieJar: CookieJar, userAgent: String) : this(MoodleAjaxClient(cookieJar, userAgent))

    override val id = "moodle"
    override val session = ProviderSession()
    // shortcut: expose the verified first page only, expand after pagination termination is confirmed.
    val courses = DataSource<Course> {
        withContext(Dispatchers.IO) {
            client.fetchFirstCoursePage().map { it.courses }.also(session::onFetch)
        }
    }
    // Preview only: today's Tehran midnight through midnight seven days later, at most six events.
    val agendaPreview = DataSource<AgendaItem> {
        withContext(Dispatchers.IO) {
            val zone = ZoneId.of("Asia/Tehran")
            val today = LocalDate.now(clock.withZone(zone))
            client.fetchAgendaPreview(
                today.atStartOfDay(zone).toEpochSecond(),
                today.plusDays(7).atStartOfDay(zone).toEpochSecond(),
            ).also(session::onFetch)
        }
    }
    override val sources: List<DataSource<*>> = listOf(courses, agendaPreview)
}
