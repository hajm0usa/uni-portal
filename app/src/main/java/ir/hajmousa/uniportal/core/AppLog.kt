package ir.hajmousa.uniportal.core

import java.util.ArrayDeque

object AppLog {
    const val CAPACITY = 200

    enum class Event { APP_STARTED, LOG_SCREEN_OPENED, COOKIE_STORAGE_FAILED, COURSES_LOADED, COURSE_LOAD_FAILED, SESSION_EXPIRED, CACHE_STORAGE_FAILED, CREDENTIAL_STORAGE_FAILED, AGENDA_LOADED, AGENDA_LOAD_FAILED, CONTENT_LOADED, CONTENT_LOAD_FAILED }

    data class Entry(val event: Event, val diagnostic: String?)

    private val entries = ArrayDeque<Entry>()

    // Accept fixed events, not free text; exception messages and causes can contain credentials or bodies.
    @Synchronized
    fun record(event: Event, error: Throwable? = null) {
        val diagnostic = error?.let {
            buildString {
                append(it.javaClass.name)
                it.stackTrace.take(32).forEach { frame -> append("\n    at ").append(frame) }
            }
        }
        if (entries.size == CAPACITY) entries.removeFirst()
        entries.addLast(Entry(event, diagnostic))
    }

    @Synchronized
    fun snapshot(): List<Entry> = entries.toList()

    @Synchronized
    fun clear() = entries.clear()
}
