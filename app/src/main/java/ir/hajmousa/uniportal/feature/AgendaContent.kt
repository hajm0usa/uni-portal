package ir.hajmousa.uniportal.feature

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import ir.hajmousa.uniportal.R
import ir.hajmousa.uniportal.core.PortalException
import ir.hajmousa.uniportal.core.time.formatPersianDate
import ir.hajmousa.uniportal.core.time.formatPersianDateTime
import ir.hajmousa.uniportal.core.time.tehranDate
import ir.hajmousa.uniportal.data.AgendaItem
import ir.hajmousa.uniportal.data.CachedAgendaPreview
import ir.hajmousa.uniportal.data.SyncCoordinator
import ir.hajmousa.uniportal.feature.theme.PortalTheme
import java.text.NumberFormat
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable
internal fun AgendaContent(state: PortalState, calendar: Boolean, enabled: Boolean, onRefresh: () -> Unit,
    target: Pair<String, String>? = null, onOpenAgendaItem: (AgendaItem) -> Unit = {}) {
    var selectedEvent by remember(state.agenda) { mutableStateOf<AgendaItem?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(if (calendar) R.string.tab_calendar else R.string.tab_today), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.agenda_preview_note))
        Button(onClick = onRefresh, enabled = enabled && !state.loading && !state.clearing && !state.needsLogin) {
            Text(stringResource(R.string.logs_refresh))
        }
        if (state.loading || state.clearing) CircularProgressIndicator()
        if (state.needsLogin) Text(stringResource(R.string.courses_needs_login))
        if (state.newItemsError) Text(stringResource(R.string.agenda_badge_error))
        state.agendaError?.takeUnless { it == PortalException.Reason.SESSION_EXPIRED }?.let { error ->
            Card(Modifier.fillMaxWidth()) {
                Text(stringResource(when (error) {
                    PortalException.Reason.NETWORK_ERROR -> R.string.courses_network_error
                    PortalException.Reason.STORAGE_ERROR -> R.string.courses_storage_error
                    PortalException.Reason.PARSE_ERROR -> R.string.courses_parse_error
                    else -> R.string.agenda_server_error
                }), Modifier.padding(16.dp))
            }
        }
        val preview = state.agenda
        if (preview == null && !state.loading && !state.clearing) Text(stringResource(R.string.agenda_not_loaded))
        if (preview != null) {
            Text(stringResource(R.string.courses_last_updated, formatPersianDateTime(preview.lastSuccessEpochSeconds)))
            if (state.agendaError != null || SyncCoordinator.isStale(preview.lastSuccessEpochSeconds, state.nowEpoch)) {
                val days = ChronoUnit.DAYS.between(tehranDate(preview.lastSuccessEpochSeconds), tehranDate(state.nowEpoch))
                if (days > 0) Text(stringResource(R.string.agenda_stale_days, NumberFormat.getInstance(Locale("fa")).format(days)))
                Text(stringResource(R.string.courses_cached_note))
            }
            val today = tehranDate(state.nowEpoch)
            val groups = preview.items.sortedBy { it.startEpoch }.groupBy { tehranDate(it.startEpoch) }
                .filterKeys { calendar || it >= today }
            if (groups.isEmpty()) Text(stringResource(R.string.agenda_empty_preview))
            if (!calendar && today !in groups) Text(stringResource(R.string.agenda_empty_today))
            groups.forEach { (day, events) ->
                Text(if (day == today) stringResource(R.string.tab_today) else formatPersianDate(events.first().startEpoch),
                    style = MaterialTheme.typography.titleMedium)
                events.forEach { item ->
                    Card(onClick = { selectedEvent = item; onOpenAgendaItem(item) }, enabled = enabled && !state.clearing,
                        modifier = Modifier.fillMaxWidth(), border = if (target == (item.providerId to item.remoteId))
                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(item.title, style = MaterialTheme.typography.titleMedium)
                            if ((item.providerId to item.remoteId) in state.newAgendaItemKeys) NewAgendaBadge()
                            Text(formatPersianDateTime(item.startEpoch))
                            Text(stringResource(if (item.kind == AgendaItem.Kind.DEADLINE) R.string.agenda_deadline else R.string.agenda_event))
                        }
                    }
                }
            }
        }
    }
    selectedEvent?.takeUnless { state.clearing }?.let { event ->
        EventDetails(event, state.courses?.courses.orEmpty()) { selectedEvent = null }
    }
}

class AgendaPreviewProvider : PreviewParameterProvider<PortalState> {
    private val preview = CachedAgendaPreview(listOf(AgendaItem("moodle", "1", "تکلیف نمونه", 1700006400, AgendaItem.Kind.DEADLINE)), 1700000000)
    override val values = sequenceOf(
        PortalState(loading = true),
        PortalState(loading = false),
        PortalState(agenda = CachedAgendaPreview(emptyList(), 1700000000), loading = false, nowEpoch = 1700000000),
        PortalState(agenda = preview, loading = false, nowEpoch = 1700000000),
        PortalState(agenda = preview, loading = false, nowEpoch = 1700000000, newAgendaItemKeys = setOf("moodle" to "1")),
        PortalState(agenda = preview, loading = false, nowEpoch = 1700000000, newItemsError = true),
        PortalState(agenda = preview, loading = false, nowEpoch = 1700300000, agendaError = PortalException.Reason.NETWORK_ERROR),
        PortalState(agenda = preview, loading = false, nowEpoch = 1700000000, needsLogin = true),
    )
}

@Preview(locale = "fa", showBackground = true)
@Composable
private fun AgendaPreview(@PreviewParameter(AgendaPreviewProvider::class) state: PortalState) {
    PortalTheme { AgendaContent(state, calendar = false, enabled = true, onRefresh = {}) }
}
