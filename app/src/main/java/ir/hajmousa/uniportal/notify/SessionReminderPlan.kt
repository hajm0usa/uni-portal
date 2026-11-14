package ir.hajmousa.uniportal.notify

internal const val SESSION_REMINDER_DELAY = 3 * 24 * 60 * 60L

internal fun sessionReminderEpoch(lastSuccess: Long?): Long? = lastSuccess
    ?.takeIf { it in 0..(Long.MAX_VALUE / 1000 - SESSION_REMINDER_DELAY) }
    ?.plus(SESSION_REMINDER_DELAY)
