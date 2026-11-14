package ir.hajmousa.uniportal.notify

import android.content.Intent
import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class ReminderIntentsTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val spec = ReminderSpec("moodle", "1/sub", 1700172800, 86400)

    @Test
    fun `broadcast identity is stable scoped encoded and independent of title or changed time`() {
        assertEquals(spec, reminderSpec(reminderIntent(context, spec)))
        assertEquals(reminderUri(spec), reminderUri(spec.copy(startEpoch = spec.startEpoch + 1)))
        assertNotEquals(reminderUri(spec), reminderUri(spec.copy(providerId = "other")))
        assertNotEquals(reminderUri(spec), reminderUri(spec.copy(offsetSeconds = 10800)))
        assertNotEquals(reminderUri(spec.copy(remoteId = "Aa")), reminderUri(spec.copy(remoteId = "BB")))
        assertNotNull(reminderIntent(context, spec).component)
        assertEquals(setOf("start_epoch"), reminderIntent(context, spec).extras!!.keySet())
    }

    @Test
    fun `malformed or foreign alarm payloads cannot authorize a reminder`() {
        val valid = reminderIntent(context, spec)
        assertNull(reminderSpec(Intent(valid).setAction("foreign")))
        assertNull(reminderSpec(Intent(valid).setData(Uri.parse("https://example.invalid/reminder/1"))))
        assertNull(reminderSpec(Intent(valid).setData(reminderUri(spec).buildUpon().appendQueryParameter("sesskey", "XXXX").build())))
        assertNull(reminderSpec(Intent(valid).setData(Uri.parse("uniportal://reminder/moodle/1/1"))))
        assertNull(reminderSpec(Intent(valid).putExtra("start_epoch", 0L)))
    }

    @Test
    fun `notification opens the matching native agenda without credentials or arbitrary browser URLs`() {
        val open = reminderOpenIntent(context, spec)
        assertEquals(spec.providerId to spec.remoteId, reminderTarget(open))
        assertNotNull(open.component)
        assertNull(open.extras)
        assertNull(reminderTarget(Intent(open).setData(Uri.parse("https://example.invalid/"))))
        assertNull(reminderTarget(Intent(open).setData(open.data!!.buildUpon().appendQueryParameter("token", "XXXX").build())))
        assertNull(reminderTarget(Intent(open).setAction(Intent.ACTION_MAIN)))
    }
}
