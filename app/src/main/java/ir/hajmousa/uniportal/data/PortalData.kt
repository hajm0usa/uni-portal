package ir.hajmousa.uniportal.data

import ir.hajmousa.uniportal.provider.moodle.MoodleProvider
import okhttp3.CookieJar

fun createPortalRepositories(cookieJar: CookieJar, userAgent: String, database: PortalDatabase): PortalRepositories {
    val provider = MoodleProvider(cookieJar, userAgent)
    return PortalRepositories(SyncCoordinator(provider, CourseRepository(provider, database.courseDao()),
        AgendaRepository(provider, database.agendaDao())), CourseContentRepository(provider))
}
