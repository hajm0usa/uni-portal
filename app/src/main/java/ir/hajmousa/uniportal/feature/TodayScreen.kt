package ir.hajmousa.uniportal.feature

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.hajmousa.uniportal.R
import ir.hajmousa.uniportal.core.PortalException
import ir.hajmousa.uniportal.core.time.formatPersianDateTime
import ir.hajmousa.uniportal.core.time.formatPersianDay
import ir.hajmousa.uniportal.core.time.formatPersianTime
import ir.hajmousa.uniportal.core.time.tehranDate
import ir.hajmousa.uniportal.data.AgendaItem
import ir.hajmousa.uniportal.data.CachedCoursePage
import ir.hajmousa.uniportal.data.Course
import ir.hajmousa.uniportal.data.CourseContentRepository
import ir.hajmousa.uniportal.data.SyncCoordinator
import ir.hajmousa.uniportal.feature.theme.PortalTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TodayScreen(
    state: PortalState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    signedIn: Boolean = false,
    canSignOut: Boolean = false,
    loginFailed: Boolean = false,
    credentialStorageError: Boolean = false,
    onRefresh: () -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onShowLogs: () -> Unit,
    onCalendar: () -> Unit,
    onCourses: () -> Unit,
    onOpenCourse: (Course) -> Unit,
    onOpenAgendaItem: (AgendaItem) -> Unit = {},
) {
    val busy = state.loading || state.clearing
    val canRefresh = enabled && !busy && !state.needsLogin
    val agenda = remember(state.agenda, state.nowEpoch) { todayAgenda(state.agenda?.items.orEmpty(), state.nowEpoch) }
    val courses = state.courses?.courses.orEmpty()
    var accountMenu by remember { mutableStateOf(false) }
    var selectedEvent by remember(state.agenda) { mutableStateOf<AgendaItem?>(null) }

    PullToRefreshBox(isRefreshing = state.loading && !state.clearing,
        onRefresh = { if (canRefresh) onRefresh() }, modifier = modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item(key = "header") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).heightIn(min = 48.dp).combinedClickable(onClick = {},
                        onLongClickLabel = stringResource(R.string.logs_open), onLongClick = onShowLogs),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Image(painterResource(R.drawable.tehran_university), contentDescription = null, modifier = Modifier.size(36.dp))
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onRefresh, enabled = canRefresh) {
                        Icon(painterResource(R.drawable.home_refresh), stringResource(R.string.logs_refresh))
                    }
                    Box {
                        IconButton(onClick = { accountMenu = true }, enabled = enabled && !state.clearing) {
                            Icon(painterResource(R.drawable.home_account), stringResource(R.string.home_account))
                        }
                        DropdownMenu(expanded = accountMenu, onDismissRequest = { accountMenu = false }) {
                            if (!signedIn || state.needsLogin) DropdownMenuItem(
                                text = { Text(stringResource(R.string.login_open)) }, enabled = !busy,
                                onClick = { accountMenu = false; onSignIn() })
                            if (canSignOut) DropdownMenuItem(text = { Text(stringResource(R.string.logout)) }, enabled = !busy,
                                onClick = { accountMenu = false; onSignOut() })
                        }
                    }
                }
            }
            item(key = "date") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.tab_today), Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold)
                        if (state.nowEpoch > 0) Text(formatPersianDay(state.nowEpoch), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(painterResource(R.drawable.nav_today), contentDescription = null, Modifier.size(30.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (busy) Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    Text(stringResource(if (state.clearing) R.string.courses_clearing else R.string.home_refreshing),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else state.agenda?.let {
                    Text(stringResource(R.string.home_updated, formatPersianDateTime(it.lastSuccessEpochSeconds)),
                        Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (state.agenda != null || state.courses != null) {
                item(key = "reminder_permission") { ReminderPermissionCard() }
            }
            if (credentialStorageError) item { HomeNotice(stringResource(R.string.credentials_storage_error)) }
            if (loginFailed) item { HomeNotice(stringResource(R.string.login_failed)) }
            if (state.needsLogin || (!signedIn && !busy && state.agenda == null)) item {
                HomeNotice(stringResource(R.string.home_sign_in), stringResource(R.string.login_open), enabled && !busy, onSignIn)
            }
            state.agendaError?.takeUnless { it == PortalException.Reason.SESSION_EXPIRED }?.let { error ->
                item { HomeNotice(homeError(error, R.string.agenda_server_error), stringResource(R.string.logs_refresh), canRefresh, onRefresh) }
            }
            if (state.newItemsError) item { HomeNotice(stringResource(R.string.agenda_badge_error)) }
            state.agenda?.let { page ->
                if (SyncCoordinator.isStale(page.lastSuccessEpochSeconds, state.nowEpoch) && !state.needsLogin) item {
                    HomeNotice(stringResource(R.string.home_stale), stringResource(R.string.logs_refresh), canRefresh, onRefresh)
                }
                if (state.agendaError != null || SyncCoordinator.isStale(page.lastSuccessEpochSeconds, state.nowEpoch)) item {
                    Text(stringResource(R.string.courses_cached_note), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                agenda.featured?.let { event ->
                    item(key = "featured") { FeaturedDeadline(event, courses, state.nowEpoch, enabled && !state.clearing,
                        isNew = (event.providerId to event.remoteId) in state.newAgendaItemKeys) {
                        selectedEvent = event
                        onOpenAgendaItem(event)
                    } }
                }
                if (agenda.days.isNotEmpty()) item(key = "upcoming") {
                    HomeSectionHeading(stringResource(R.string.home_upcoming), stringResource(R.string.home_view_calendar), onCalendar)
                }
                agenda.days.forEach { (_, events) ->
                    item { Text(homeDay(events.first().startEpoch, state.nowEpoch), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(events, key = { "event:${it.providerId.length}:${it.providerId}:${it.remoteId}" }) { event ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(enabled = enabled && !state.clearing) {
                            selectedEvent = event
                            onOpenAgendaItem(event)
                        }
                            .padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(R.drawable.nav_calendar), contentDescription = null,
                                Modifier.size(38.dp).padding(7.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(event.title, style = MaterialTheme.typography.titleMedium)
                                if ((event.providerId to event.remoteId) in state.newAgendaItemKeys) NewAgendaBadge()
                                Text(eventContext(event, courses), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(formatPersianTime(event.startEpoch), style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (agenda.featured == null && agenda.days.isEmpty()) item(key = "empty") {
                    HomeNotice(stringResource(R.string.home_empty), stringResource(R.string.home_empty_hint), enabled, onCourses)
                }
                item(key = "scope") {
                    // shortcut: the source is still a preview, remove this notice after verified pagination.
                    Text(stringResource(R.string.home_agenda_scope), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (page.items.any { (it.providerId to it.remoteId) in state.newAgendaItemKeys }) {
                        Text(stringResource(R.string.agenda_newly_seen_note), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (state.agenda == null && !busy && !state.needsLogin) item { HomeNotice(stringResource(R.string.agenda_not_loaded)) }
            item(key = "courses") {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                HomeSectionHeading(stringResource(R.string.courses_title), stringResource(R.string.home_view_all), onCourses)
                state.coursesError?.takeUnless { it == PortalException.Reason.SESSION_EXPIRED }?.let { error ->
                    HomeNotice(homeError(error, R.string.courses_server_error))
                }
                if (state.courses != null && (state.coursesError != null || SyncCoordinator.isStale(state.courses.lastSuccessEpochSeconds, state.nowEpoch))) {
                    Text(stringResource(R.string.courses_cached_note), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    courses.take(2).forEach { course ->
                        Card(onClick = { onOpenCourse(course) }, enabled = canRefresh, modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(painterResource(R.drawable.nav_courses), contentDescription = null,
                                    Modifier.size(34.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp)).padding(7.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text(courseName(course), style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(R.string.content_open_course), style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                if (state.courses?.courses?.isEmpty() == true) Text(stringResource(R.string.courses_empty),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    selectedEvent?.takeUnless { state.clearing }?.let { event ->
        EventDetails(event, courses, onDismiss = { selectedEvent = null })
    }
}

@Composable
private fun FeaturedDeadline(event: AgendaItem, courses: List<Course>, nowEpoch: Long, enabled: Boolean, isNew: Boolean, onOpen: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.home_clock), contentDescription = null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.home_featured), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (isNew) NewAgendaBadge()
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(event.title, style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 32.sp, fontWeight = FontWeight.Medium))
                Text(eventContext(event, courses), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(homeDay(event.startEpoch, nowEpoch), style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.home_at_time, formatPersianTime(event.startEpoch)),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (tehranDate(event.startEpoch) == tehranDate(nowEpoch)) Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        shape = RoundedCornerShape(6.dp)) {
                        Text(stringResource(R.string.home_due_today), Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            TextButton(onClick = onOpen, enabled = enabled, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) {
                Text(stringResource(R.string.home_details), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                Icon(painterResource(R.drawable.home_arrow), contentDescription = null, Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun HomeSectionHeading(title: String, action: String, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        TextButton(onClick = onAction) {
            Text(action, style = MaterialTheme.typography.labelLarge)
            Icon(painterResource(R.drawable.home_arrow), contentDescription = null, Modifier.padding(start = 6.dp).size(17.dp))
        }
    }
}

@Composable
private fun HomeNotice(text: String, action: String? = null, enabled: Boolean = true, onAction: () -> Unit = {}) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (action != null) TextButton(onClick = onAction, enabled = enabled) { Text(action) }
        }
    }
}

@Composable
private fun homeError(reason: PortalException.Reason, fallback: Int): String = stringResource(when (reason) {
    PortalException.Reason.NETWORK_ERROR -> R.string.courses_network_error
    PortalException.Reason.STORAGE_ERROR -> R.string.courses_storage_error
    PortalException.Reason.PARSE_ERROR -> R.string.courses_parse_error
    else -> fallback
})

@Composable
private fun homeDay(epoch: Long, now: Long): String = when (tehranDate(epoch)) {
    tehranDate(now) -> stringResource(R.string.tab_today)
    tehranDate(now).plusDays(1) -> stringResource(R.string.home_tomorrow)
    else -> formatPersianDay(epoch)
}

@Composable
private fun courseName(course: Course): String = course.name?.takeIf { it.isNotBlank() }
    ?: course.shortName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.course_unnamed)

@Composable
private fun eventContext(event: AgendaItem, courses: List<Course>): String = courses.firstOrNull {
    it.providerId == event.providerId && it.remoteId == event.courseRemoteId
}?.let { courseName(it) } ?: stringResource(if (event.kind == AgendaItem.Kind.DEADLINE) R.string.agenda_deadline else R.string.agenda_event)

@Composable
internal fun EventDetails(event: AgendaItem, courses: List<Course>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val url = event.deepLink?.let(CourseContentRepository::safeBrowserUrl)
    var failed by remember(event) { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(event.title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(eventContext(event, courses), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatPersianDateTime(event.startEpoch))
                Text(stringResource(R.string.content_browser_only))
                if (url != null) Text(stringResource(R.string.content_browser_login_note))
                if (failed) Text(stringResource(R.string.content_browser_failed))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (url == null) onDismiss() else {
                    failed = try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        false
                    } catch (_: ActivityNotFoundException) { true } catch (_: SecurityException) { true }
                }
            }) { Text(stringResource(if (url == null) R.string.logs_back else R.string.content_browser)) }
        },
        dismissButton = { if (url != null) TextButton(onClick = onDismiss) { Text(stringResource(R.string.logs_back)) } },
    )
}

@Composable
internal fun NewAgendaBadge() {
    Badge(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
        Text(stringResource(R.string.agenda_newly_seen), style = MaterialTheme.typography.labelSmall)
    }
}

@Preview(locale = "fa", showBackground = true, widthDp = 360, heightDp = 850)
@Preview(locale = "fa", showBackground = true, widthDp = 320, heightDp = 850, fontScale = 1.5f,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TodayPreview(@PreviewParameter(AgendaPreviewProvider::class) state: PortalState) {
    PortalTheme {
        TodayScreen(state.copy(courses = CachedCoursePage(listOf(Course("moodle", "1", "درس نمونه", null)), state.nowEpoch)),
            onRefresh = {}, onSignIn = {}, onSignOut = {}, onShowLogs = {}, onCalendar = {}, onCourses = {}, onOpenCourse = {})
    }
}
