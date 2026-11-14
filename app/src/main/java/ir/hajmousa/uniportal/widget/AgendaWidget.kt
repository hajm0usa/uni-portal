package ir.hajmousa.uniportal.widget

import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import ir.hajmousa.uniportal.MainActivity
import ir.hajmousa.uniportal.R
import ir.hajmousa.uniportal.core.AppLog
import ir.hajmousa.uniportal.core.PortalException
import ir.hajmousa.uniportal.core.time.formatPersianDateTime
import ir.hajmousa.uniportal.data.PortalDatabase
import ir.hajmousa.uniportal.data.WidgetAgendaCache
import ir.hajmousa.uniportal.feature.theme.DarkColors
import ir.hajmousa.uniportal.feature.theme.LightColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class AgendaWidget : GlanceAppWidget(errorUiLayout = R.layout.widget_placeholder) {
    override val stateDefinition = null

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val database = PortalDatabase.get(context)
        val cache = database.invalidationTracker.createFlow("agenda_item", "sync_state")
            .map { database.agendaDao().readWidgetAgenda() }
            .flowOn(Dispatchers.IO)
            .catch {
                AppLog.record(AppLog.Event.CACHE_STORAGE_FAILED)
                emit(WidgetAgendaCache(error = PortalException.Reason.STORAGE_ERROR))
            }
        provideContent {
            val saved by cache.collectAsState(initial = WidgetAgendaCache())
            GlanceTheme(colors = if (Build.VERSION.SDK_INT >= 31) GlanceTheme.colors else ColorProviders(LightColors, DarkColors)) {
                // RemoteViews inherits RTL even when the launcher's language is not Persian.
                AndroidRemoteViews(RemoteViews(context.packageName, R.layout.widget_container), R.id.widget_content) {
                    WidgetContent(widgetState(saved))
                }
            }
        }
    }
}

class AgendaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AgendaWidget()
}

internal suspend fun updateAgendaWidgets(context: Context) {
    try {
        AgendaWidget().updateAll(context)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        AppLog.record(AppLog.Event.WIDGET_UPDATE_FAILED)
    }
}

@Composable
private fun WidgetContent(state: AgendaWidgetState) {
    val context = LocalContext.current
    Column(GlanceModifier.fillMaxSize().appWidgetBackground().background(GlanceTheme.colors.widgetBackground).padding(16.dp)) {
        Text(context.getString(R.string.widget_title), style = TextStyle(color = GlanceTheme.colors.onSurface,
            fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Start), modifier = GlanceModifier.fillMaxWidth())
        state.cache.lastSuccessEpochSeconds?.let {
            WidgetText(context.getString(R.string.courses_last_updated, formatPersianDateTime(it)))
        }
        when {
            state.cache.needsLogin -> WidgetText(context.getString(R.string.courses_needs_login))
            state.cache.error == PortalException.Reason.STORAGE_ERROR -> WidgetText(context.getString(R.string.courses_storage_error))
            state.cache.error != null -> WidgetText(context.getString(R.string.widget_refresh_failed))
            state.cache.lastSuccessEpochSeconds == null -> WidgetText(context.getString(R.string.agenda_not_loaded))
            state.stale -> WidgetText(context.getString(R.string.widget_stale))
        }
        WidgetText(context.getString(R.string.widget_preview_note))
        Spacer(GlanceModifier.height(8.dp))
        LazyColumn(GlanceModifier.fillMaxWidth().defaultWeight()) {
            if (state.cache.lastSuccessEpochSeconds != null && state.upcoming.isEmpty()) {
                item { WidgetText(context.getString(R.string.widget_empty)) }
            }
            items(state.upcoming) { item ->
                Column(GlanceModifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(item.title, maxLines = 2, modifier = GlanceModifier.fillMaxWidth(),
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, textAlign = TextAlign.Start))
                    WidgetText(formatPersianDateTime(item.startEpoch))
                }
            }
        }
        Button(context.getString(R.string.widget_open_app), actionStartActivity(Intent(context, MainActivity::class.java)),
            modifier = GlanceModifier.fillMaxWidth().height(48.dp))
    }
}

@Composable
private fun WidgetText(text: String) {
    Text(text, modifier = GlanceModifier.fillMaxWidth(),
        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp, textAlign = TextAlign.Start))
}
