package ir.hajmousa.uniportal.data

import ir.hajmousa.uniportal.core.AppLog
import ir.hajmousa.uniportal.core.PortalException
import ir.hajmousa.uniportal.provider.moodle.MoodleAjaxClient
import okhttp3.CookieJar

class CourseRepository internal constructor(private val client: MoodleAjaxClient) {
    constructor(cookieJar: CookieJar, userAgent: String) : this(MoodleAjaxClient(cookieJar, userAgent))

    // shortcut: live first-page verification only, add Room before claiming offline course support.
    fun fetchFirstPage(): Result<List<Course>> {
        val result = client.fetchFirstCoursePage().map { it.courses }
        val event = when {
            result.isSuccess -> AppLog.Event.COURSES_LOADED
            (result.exceptionOrNull() as? PortalException)?.reason == PortalException.Reason.SESSION_EXPIRED ->
                AppLog.Event.SESSION_EXPIRED
            else -> AppLog.Event.COURSE_LOAD_FAILED
        }
        AppLog.record(event, result.exceptionOrNull())
        return result
    }
}
