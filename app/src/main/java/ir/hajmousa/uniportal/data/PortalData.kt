package ir.hajmousa.uniportal.data

import ir.hajmousa.uniportal.provider.moodle.MoodleProvider
import okhttp3.CookieJar

fun createSyncCoordinator(cookieJar: CookieJar, userAgent: String, database: PortalDatabase): SyncCoordinator {
    val provider = MoodleProvider(cookieJar, userAgent)
    return SyncCoordinator(provider, CourseRepository(provider, database.courseDao()),
        AgendaRepository(provider, database.agendaDao()))
}
