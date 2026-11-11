package ir.hajmousa.uniportal.provider.moodle

import ir.hajmousa.uniportal.auth.SsoHosts
import ir.hajmousa.uniportal.core.PortalException
import ir.hajmousa.uniportal.core.PortalException.Reason
import ir.hajmousa.uniportal.data.AgendaItem
import java.io.IOException
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal class MoodleAjaxClient(
    cookieJar: CookieJar,
    userAgent: String,
    private val baseUrl: HttpUrl = SsoHosts.MOODLE_URL.toHttpUrl(),
    private val requestDelayMillis: Long = 200,
) {
    private val gate = Semaphore(1, true)
    private var lastRequestNanos = 0L
    private val http = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .retryOnConnectionFailure(false)
        .callTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build())
        }
        .addNetworkInterceptor { chain ->
            val url = chain.request().url
            // Stop SSO/off-origin redirects before sending cookies or query credentials there.
            if (!sameOrigin(url)) {
                throw PortalException(Reason.SESSION_EXPIRED)
            }
            val response = chain.proceed(chain.request())
            val redirect = response.header("Location")?.let { url.resolve(it) }
            if (response.isRedirect && redirect != null && !sameOrigin(redirect)) {
                response.close()
                throw PortalException(Reason.SESSION_EXPIRED)
            }
            response
        }
        .build()

    private fun sameOrigin(url: HttpUrl): Boolean =
        url.scheme == baseUrl.scheme && url.host == baseUrl.host && url.port == baseUrl.port &&
            url.username.isEmpty() && url.password.isEmpty()

    fun fetchFirstCoursePage(): Result<CoursePage> = withSession { key ->
        MoodleParsers.parseCoursePage(readAjax(key, COURSE_METHOD, COURSE_REQUEST))
    }

    fun fetchAgendaPreview(fromEpoch: Long, toEpoch: Long): Result<List<AgendaItem>> {
        if (fromEpoch < 0 || toEpoch <= fromEpoch) return Result.failure(PortalException(Reason.PARSE_ERROR))
        // shortcut: one observed six-event page, add pagination only after its protocol is verified.
        val request = """[{"index":0,"methodname":"$CALENDAR_METHOD","args":{"limitnum":6,"limittononsuspendedevents":true,"timesortfrom":$fromEpoch,"timesortto":$toEpoch}}]"""
        return withSession { key -> MoodleParsers.parseAgenda(readAjax(key, CALENDAR_METHOD, request)) }
    }

    private fun <T> withSession(fetch: (String) -> Result<T>): Result<T> {
        var acquired = false
        return try {
            gate.acquire()
            acquired = true
            val first = fetch(fetchSesskey())
            val reason = (first.exceptionOrNull() as? PortalException)?.reason
            val result = if (reason == Reason.INVALID_SESSION_KEY || reason == Reason.SESSION_EXPIRED) {
                fetch(fetchSesskey())
            } else {
                first
            }
            if ((result.exceptionOrNull() as? PortalException)?.reason == Reason.INVALID_SESSION_KEY) {
                Result.failure(PortalException(Reason.SESSION_EXPIRED))
            } else {
                result
            }
        } catch (error: PortalException) {
            Result.failure(error)
        } catch (_: IOException) {
            Result.failure(PortalException(Reason.NETWORK_ERROR))
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            Result.failure(PortalException(Reason.NETWORK_ERROR))
        } catch (_: IllegalStateException) {
            Result.failure(PortalException(Reason.STORAGE_ERROR))
        } finally {
            if (acquired) gate.release()
        }
    }

    private fun fetchSesskey(): String = MoodleParsers.extractSesskey(
        read(Request.Builder().url(baseUrl).build(), json = false),
    ).getOrThrow()

    private fun readAjax(sesskey: String, method: String, body: String): String {
        val url = baseUrl.newBuilder().encodedPath("/lib/ajax/service.php")
            .addQueryParameter("sesskey", sesskey)
            .addQueryParameter("info", method)
            .build()
        val request = Request.Builder().url(url)
            .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        return read(request, json = true)
    }

    private fun read(request: Request, json: Boolean): String {
        if (lastRequestNanos != 0L) {
            val remaining = TimeUnit.MILLISECONDS.toNanos(requestDelayMillis) - (System.nanoTime() - lastRequestNanos)
            if (remaining > 0) TimeUnit.NANOSECONDS.sleep(remaining)
        }
        try {
            return http.newCall(request).execute().use { response ->
                if (response.code == 401 || response.code == 403) throw PortalException(Reason.SESSION_EXPIRED)
                if (!response.isSuccessful) throw PortalException(Reason.SERVER_ERROR)
                val body = response.body
                val type = body.contentType()
                if (json && type?.subtype == "html") throw PortalException(Reason.SESSION_EXPIRED)
                val source = body.source()
                source.request(MAX_BODY_BYTES + 1)
                if (source.buffer.size > MAX_BODY_BYTES) throw PortalException(Reason.PARSE_ERROR)
                val text = source.readUtf8()
                if (json && text.trimStart { it.isWhitespace() || it == '\uFEFF' }.startsWith('<')) {
                    throw PortalException(Reason.SESSION_EXPIRED)
                }
                if (json && type != null && type.subtype != "json" && !type.subtype.endsWith("+json")) {
                    throw PortalException(Reason.PARSE_ERROR)
                }
                text
            }
        } finally {
            lastRequestNanos = System.nanoTime()
        }
    }

    private companion object {
        const val MAX_BODY_BYTES = 8L * 1024 * 1024
        const val COURSE_METHOD = "core_course_get_enrolled_courses_by_timeline_classification"
        const val CALENDAR_METHOD = "core_calendar_get_action_events_by_timesort"
        // shortcut: fetch only the observed first page, add pagination after its termination rule is verified.
        const val COURSE_REQUEST = """[{"index":0,"methodname":"$COURSE_METHOD","args":{"offset":0,"limit":24,"classification":"all","sort":"ul.timeaccess desc","customfieldname":"","customfieldvalue":""}}]"""
    }
}
