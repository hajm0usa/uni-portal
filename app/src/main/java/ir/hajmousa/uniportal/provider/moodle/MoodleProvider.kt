package ir.hajmousa.uniportal.provider.moodle

import ir.hajmousa.uniportal.data.Course
import ir.hajmousa.uniportal.data.DataSource
import ir.hajmousa.uniportal.data.Provider
import ir.hajmousa.uniportal.data.ProviderSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CookieJar

internal class MoodleProvider(private val client: MoodleAjaxClient) : Provider {
    constructor(cookieJar: CookieJar, userAgent: String) : this(MoodleAjaxClient(cookieJar, userAgent))

    override val id = "moodle"
    override val session = ProviderSession()
    // shortcut: expose the verified first page only, expand after pagination termination is confirmed.
    val courses = DataSource<Course> {
        withContext(Dispatchers.IO) {
            client.fetchFirstCoursePage().map { it.courses }.also(session::onFetch)
        }
    }
    override val sources: List<DataSource<*>> = listOf(courses)
}
