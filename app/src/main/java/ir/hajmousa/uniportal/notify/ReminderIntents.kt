package ir.hajmousa.uniportal.notify

import android.content.Context
import android.content.Intent
import android.net.Uri
import ir.hajmousa.uniportal.MainActivity

internal const val REMINDER_ACTION = "ir.hajmousa.uniportal.REMINDER"
private const val START_EPOCH = "start_epoch"

internal fun reminderUri(spec: ReminderSpec): Uri = Uri.Builder().scheme("uniportal").authority("reminder")
    .appendPath(spec.providerId).appendPath(spec.remoteId).appendPath(spec.offsetSeconds.toString()).build()

internal fun reminderIntent(context: Context, spec: ReminderSpec): Intent = Intent(context, ReminderReceiver::class.java)
    .setAction(REMINDER_ACTION).setData(reminderUri(spec)).putExtra(START_EPOCH, spec.startEpoch)

internal fun reminderSpec(intent: Intent): ReminderSpec? = try {
    val uri = intent.data
    if (intent.action != REMINDER_ACTION || uri?.scheme != "uniportal" || uri.authority != "reminder" ||
        uri.query != null || uri.fragment != null || uri.pathSegments.size != 3) null
    else ReminderSpec(uri.pathSegments[0], uri.pathSegments[1], intent.getLongExtra(START_EPOCH, 0), uri.pathSegments[2].toLong())
} catch (_: Exception) { null }

internal fun reminderOpenIntent(context: Context, spec: ReminderSpec): Intent = Intent(context, MainActivity::class.java)
    .setAction(Intent.ACTION_VIEW)
    .setData(Uri.Builder().scheme("uniportal").authority("agenda").appendPath(spec.providerId).appendPath(spec.remoteId).build())
    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

internal fun reminderTarget(intent: Intent?): Pair<String, String>? {
    val uri = intent?.data ?: return null
    if (intent.action != Intent.ACTION_VIEW || uri.scheme != "uniportal" || uri.authority != "agenda" ||
        uri.query != null || uri.fragment != null || uri.pathSegments.size != 2) return null
    val (provider, remote) = uri.pathSegments
    return (provider to remote).takeIf { provider.isNotBlank() && provider.length <= 1024 && remote.isNotBlank() && remote.length <= 1024 }
}
