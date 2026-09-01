package org.taskhub.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.datetime.*
import org.taskhub.network.RecurrenceRules
import org.taskhub.network.models.TaskResponse
import org.taskhub.ui.components.BadgeTone
import org.taskhub.ui.components.LocalAppSettings
import org.taskhub.ui.components.PointsBadge
import org.taskhub.ui.components.TaskHubTopBar
import org.taskhub.ui.i18n.AppStrings
import org.taskhub.ui.models.*
import org.taskhub.ui.theme.*

// ── Calendar mode ──────────────────────────────────────────

private enum class CalendarMode { WEEK, MONTH }

// ── Calendar entry (task + status for a specific day) ─────

private data class DayTaskEntry(
    val task: TaskResponse,
    val isOverdue: Boolean,
    val isDueToday: Boolean,
    val isCompleted: Boolean
)

// ── Color helpers ──────────────────────────────────────────
// Antes: colores fijos (0xFFFFCDD2/Teal500/0xFFA5D6A7) iguales en los 3 temas
// y en claro/oscuro. En el tema Minimal (monocromo por diseño) rompían la
// identidad "escala de grises"; combinados con texto que sí sigue el tema
// (ver TaskChip), fallaban WCAG AA en varias combinaciones — mismo patrón ya
// corregido en el popup de esta misma pantalla (ver TaskPopupItem/statusLabel
// más abajo, que ya usa PointsBadge con pares container/onContainer
// auditados). Sustituidos por la paleta semántica (éxito/error/info), común
// a los 3 temas y ya auditada ≥4.5:1 en las 6 combinaciones.

@Composable
private fun DayTaskEntry.dotColor(): Color = when {
    isCompleted -> MaterialTheme.semanticColors.success
    isOverdue -> MaterialTheme.colorScheme.error
    isDueToday -> MaterialTheme.semanticColors.info
    else -> MaterialTheme.colorScheme.primary
}

@Composable
private fun DayTaskEntry.containerColor(): Color = when {
    isCompleted -> MaterialTheme.semanticColors.successContainer
    isOverdue -> MaterialTheme.colorScheme.errorContainer
    isDueToday -> MaterialTheme.semanticColors.infoContainer
    else -> MaterialTheme.colorScheme.primaryContainer
}

@Composable
private fun DayTaskEntry.onContainerColor(): Color = when {
    isCompleted -> MaterialTheme.semanticColors.onSuccessContainer
    isOverdue -> MaterialTheme.colorScheme.onErrorContainer
    isDueToday -> MaterialTheme.semanticColors.onInfoContainer
    else -> MaterialTheme.colorScheme.onPrimaryContainer
}

// ────────────────────────────────────────────────────────────
//  CalendarScreen
// ────────────────────────────────────────────────────────────

data class CalendarScreen(
    val householdId: String,
    val memberId: String? = null
) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = koinScreenModel<TaskScreenModel>()
        val listState by model.listState.collectAsState()
        val appSettings = LocalAppSettings.current
        val s = { key: String -> AppStrings.get(key, appSettings.currentLanguage) }
        val lang = appSettings.currentLanguage

        // Nombre del hogar activo para la topbar (ver panel de expertos v2,
        // Estética #4): con varios hogares, esta pantalla no dejaba claro
        // en cuál se estaba trabajando.
        val householdModel = koinScreenModel<HouseholdScreenModel>()
        val householdUiState by householdModel.uiState.collectAsState()
        LaunchedEffect(householdId) {
            householdModel.loadHousehold(householdId)
        }
        val householdName = (householdUiState as? HouseholdUiState.Success)?.household?.name

        LaunchedEffect(householdId) {
            model.setCurrentMemberId(memberId)
            model.loadTasks(householdId)
        }

        var mode by remember { mutableStateOf(CalendarMode.WEEK) }

        val tz = remember { TimeZone.currentSystemDefault() }
        // "Hoy", recalculado cada minuto: si la pantalla se deja abierta cruzando
        // la medianoche, el resaltado de "hoy" en semana/mes no se quedaba
        // clavado en la fecha con la que se abrió la pantalla.
        var today by remember { mutableStateOf(Clock.System.now().toLocalDateTime(tz).date) }
        LaunchedEffect(Unit) {
            while (true) {
                kotlinx.coroutines.delay(60_000L)
                today = Clock.System.now().toLocalDateTime(tz).date
            }
        }

        // Anchor date: the "focus" date we use to compute week/month ranges
        var anchorDate by remember {
            mutableStateOf(today)
        }

        // ── Computed week / month ranges ────────────────────
        val weekRange = remember(anchorDate) {
            computeWeekRange(anchorDate)
        }
        val monthRange = remember(anchorDate) {
            computeMonthGrid(anchorDate)
        }

        // ── Tasks grouped by date ───────────────────────────
        val tasksByDate = remember(listState, anchorDate, mode) {
            val tasks = (listState as? TaskListUiState.Success)?.tasks ?: emptyList()
            groupTasksByDate(tasks, tz, mode, weekRange, monthRange)
        }

        // ── Selected day popup ──────────────────────────────
        var selectedDay by remember { mutableStateOf<LocalDate?>(null) }
        val selectedTasks = remember(selectedDay, tasksByDate) {
            val day = selectedDay ?: return@remember emptyList<DayTaskEntry>()
            tasksByDate[day] ?: emptyList()
        }

        // ── Day popup dialog ────────────────────────────────
        if (selectedDay != null) {
            DayTasksPopup(
                date = selectedDay!!,
                tasks = selectedTasks,
                onDismiss = { selectedDay = null },
                onTaskClick = { taskId ->
                    selectedDay = null
                    navigator.push(TaskDetailScreen(householdId, taskId))
                }
            )
        }

        // ── UI ──────────────────────────────────────────────
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ── Top bar ─────────────────────────────────
                TaskHubTopBar(
                    title = s("personal_space_calendar"),
                    subtitle = householdName,
                    onBack = { navigator.pop() },
                    actions = {
                        TextButton(
                            onClick = {
                                mode = if (mode == CalendarMode.WEEK) CalendarMode.MONTH else CalendarMode.WEEK
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(
                                if (mode == CalendarMode.WEEK) s("calendar_toggle_month") else s("calendar_toggle_week"),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                )
                // Navigation row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            anchorDate = when (mode) {
                                CalendarMode.WEEK -> anchorDate.minus(7, DateTimeUnit.DAY)
                                CalendarMode.MONTH -> {
                                    val ym = anchorDate.minus(1, DateTimeUnit.MONTH)
                                    LocalDate(ym.year, ym.month, 1)
                                }
                            }
                        },
                        modifier = Modifier.semantics {
                            contentDescription = if (mode == CalendarMode.WEEK) s("calendar_prev_week") else s("calendar_prev_month")
                        }
                    ) {
                        Text("◀", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = when (mode) {
                            CalendarMode.WEEK -> {
                                val monday = getMondayOfWeek(anchorDate)
                                val sunday = monday.plus(6, DateTimeUnit.DAY)
                                s("calendar_week_of")
                                    .replace("%1", monday.dayOfMonth.toString())
                                    .replace("%2", monthAbbr(monday.month, lang))
                            }
                            CalendarMode.MONTH ->
                                "${monthFull(anchorDate.month, lang)} ${anchorDate.year}"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                    TextButton(
                        onClick = {
                            anchorDate = when (mode) {
                                CalendarMode.WEEK -> anchorDate.plus(7, DateTimeUnit.DAY)
                                CalendarMode.MONTH -> {
                                    val ym = anchorDate.plus(1, DateTimeUnit.MONTH)
                                    LocalDate(ym.year, ym.month, 1)
                                }
                            }
                        },
                        modifier = Modifier.semantics {
                            contentDescription = if (mode == CalendarMode.WEEK) s("calendar_next_week") else s("calendar_next_month")
                        }
                    ) {
                        Text("▶", style = MaterialTheme.typography.titleMedium)
                    }
                }

                // ── Loading / Error states ──────────────────
                when (val state = listState) {
                    is TaskListUiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    is TaskListUiState.Error -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = s("calendar_error_prefix").replace("%s", state.message),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    else -> {
                        if (tasksByDate.values.all { it.isEmpty() }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = s("calendar_no_tasks_range"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        when (mode) {
                            CalendarMode.WEEK -> WeekView(
                                weekRange = weekRange,
                                tasksByDate = tasksByDate,
                                today = today,
                                onDayClick = { selectedDay = it }
                            )
                            CalendarMode.MONTH -> MonthView(
                                monthGrid = monthRange,
                                tasksByDate = tasksByDate,
                                today = today,
                                onDayClick = { selectedDay = it }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────
//  Week view (7 columns: Mon–Sun)
// ────────────────────────────────────────────────────────────

@Composable
private fun WeekView(
    weekRange: List<LocalDate>,
    tasksByDate: Map<LocalDate, List<DayTaskEntry>>,
    today: LocalDate,
    onDayClick: (LocalDate) -> Unit
) {
    val lang = LocalAppSettings.current.currentLanguage
    Column(modifier = Modifier.fillMaxSize()) {
        // Day-of-week headers
        Row(modifier = Modifier.fillMaxWidth()) {
            val headers = dayAbbrHeaders(lang)
            headers.forEach { header ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = header,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // 7 day columns.
        // Modifier.height(IntrinsicSize.Min) es necesario para que
        // DayColumn.fillMaxHeight() (dentro) funcione: bajo un
        // verticalScroll() sin acotar (maxHeight = Infinity), fillMaxHeight()
        // se vuelve un no-op y cada columna se ajusta solo a su propio
        // contenido — las 7 columnas no comparten altura y el resaltado de
        // "hoy" queda recortado al contenido en vez de cubrir toda la fila.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .verticalScroll(rememberScrollState())
        ) {
            weekRange.forEach { date ->
                val entries = tasksByDate[date] ?: emptyList()
                val isToday = date == today
                DayColumn(
                    modifier = Modifier.weight(1f),
                    date = date,
                    entries = entries,
                    isToday = isToday,
                    onClick = { onDayClick(date) }
                )
            }
        }
    }
}

// ────────────────────────────────────────────────────────────
//  Month view (grid: 6 rows × 7 columns)
// ────────────────────────────────────────────────────────────

@Composable
private fun MonthView(
    monthGrid: List<List<LocalDate?>>,
    tasksByDate: Map<LocalDate, List<DayTaskEntry>>,
    today: LocalDate,
    onDayClick: (LocalDate) -> Unit
) {
    val lang = LocalAppSettings.current.currentLanguage
    Column(modifier = Modifier.fillMaxSize()) {
        // Day-of-week headers
        Row(modifier = Modifier.fillMaxWidth()) {
            val headers = dayAbbrHeaders(lang)
            headers.forEach { header ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = header,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Week rows
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            monthGrid.forEach { week ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                ) {
                    week.forEach { date ->
                        if (date != null) {
                            val entries = tasksByDate[date] ?: emptyList()
                            val isToday = date == today
                            MonthDayCell(
                                modifier = Modifier.weight(1f),
                                date = date,
                                entries = entries,
                                isToday = isToday,
                                onClick = { onDayClick(date) }
                            )
                        } else {
                            // Empty cell (padding days from adjacent months)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            }
        }
    }
}

// ────────────────────────────────────────────────────────────
//  Day column (week view)
// ────────────────────────────────────────────────────────────

@Composable
private fun DayColumn(
    modifier: Modifier = Modifier,
    date: LocalDate,
    entries: List<DayTaskEntry>,
    isToday: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick)
            .then(
                if (isToday) Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                else Modifier
            )
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Day number
        Surface(
            shape = RoundedCornerShape(50),
            color = if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent,
            modifier = Modifier.size(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "${date.dayOfMonth}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    color = if (isToday) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // Task chips
        entries.take(4).forEach { entry ->
            TaskChip(entry = entry)
            Spacer(Modifier.height(2.dp))
        }

        if (entries.size > 4) {
            Text(
                text = "+${entries.size - 4}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ────────────────────────────────────────────────────────────
//  Month day cell
// ────────────────────────────────────────────────────────────

@Composable
private fun MonthDayCell(
    modifier: Modifier = Modifier,
    date: LocalDate,
    entries: List<DayTaskEntry>,
    isToday: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick)
            .then(
                if (isToday) Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                else Modifier
            )
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Day number
        Surface(
            shape = RoundedCornerShape(50),
            color = if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent,
            modifier = Modifier.size(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "${date.dayOfMonth}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    color = if (isToday) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Compact task indicators (dots)
        if (entries.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Show colored dots — max 6
                entries.take(6).forEach { entry ->
                    val color = entry.dotColor()
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(color)
                    )
                    Spacer(Modifier.width(1.dp))
                }
                if (entries.size > 6) {
                    Text(
                        text = "+",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────
//  Task chip (small pill inside day cells)
// ────────────────────────────────────────────────────────────

@Composable
private fun TaskChip(entry: DayTaskEntry) {
    val bgColor = entry.containerColor()
    val textColor = entry.onContainerColor()

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = bgColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = entry.task.title,
            modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium
        )
    }
}

// ────────────────────────────────────────────────────────────
//  Day tasks popup dialog
// ────────────────────────────────────────────────────────────

@Composable
private fun DayTasksPopup(
    date: LocalDate,
    tasks: List<DayTaskEntry>,
    onDismiss: () -> Unit,
    onTaskClick: (String) -> Unit
) {
    val appSettings = LocalAppSettings.current
    val s = { key: String -> AppStrings.get(key, appSettings.currentLanguage) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.7f),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${dayFull(date.dayOfWeek, appSettings.currentLanguage)} ${date.dayOfMonth}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) {
                        Text(s("settings_close"))
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                if (tasks.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = s("calendar_no_tasks_for_day"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(tasks, key = { it.task.id }) { entry ->
                            TaskPopupItem(
                                entry = entry,
                                onClick = { onTaskClick(entry.task.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskPopupItem(
    entry: DayTaskEntry,
    onClick: () -> Unit
) {
    val appSettings = LocalAppSettings.current
    val s = { key: String -> AppStrings.get(key, appSettings.currentLanguage) }
    val statusColor = entry.dotColor()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status indicator dot
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(statusColor)
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                if (entry.task.description.isNotBlank()) {
                    Text(
                        text = entry.task.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // Status label — colores fijos (0xFF2E7D32/0xFFC62828/Teal600) sobre el
                // fondo de esta card (colorScheme.surfaceVariant, que SÍ cambia con el
                // tema/modo) fallaban WCAG AA en varias combinaciones (hasta 2.47:1 en
                // oscuro). PointsBadge ya usa pares container/onContainer auditados
                // (>=4.5:1 en los 3 temas x claro/oscuro), independientes del fondo.
                val statusLabel = when {
                    entry.isCompleted -> s("calendar_task_status_completed")
                    entry.isOverdue -> s("calendar_task_status_overdue")
                    entry.isDueToday -> s("calendar_task_status_pending")
                    else -> ""
                }
                if (statusLabel.isNotEmpty()) {
                    PointsBadge(
                        text = statusLabel,
                        tone = when {
                            entry.isCompleted -> BadgeTone.Success
                            entry.isOverdue -> BadgeTone.Error
                            else -> BadgeTone.Info
                        }
                    )
                }
            }

            // Points badge
            PointsBadge(
                text = "⭐ ${entry.task.points}",
                tone = BadgeTone.Teal
            )
        }
    }
}

// ────────────────────────────────────────────────────────────
//  Date calculations
// ────────────────────────────────────────────────────────────

/** Get Monday of the week containing [date]. */
private fun getMondayOfWeek(date: LocalDate): LocalDate {
    val dayOfWeek = date.dayOfWeek.ordinal // 0=Monday
    return date.minus(dayOfWeek, DateTimeUnit.DAY)
}

/** Compute 7 days (Mon–Sun) for the week containing [anchorDate]. */
private fun computeWeekRange(anchorDate: LocalDate): List<LocalDate> {
    val monday = getMondayOfWeek(anchorDate)
    return (0..6).map { monday.plus(it, DateTimeUnit.DAY) }
}

/** Compute a 6×7 grid for the month of [anchorDate]. Null = padding cell. */
private fun computeMonthGrid(anchorDate: LocalDate): List<List<LocalDate?>> {
    val firstOfMonth = LocalDate(anchorDate.year, anchorDate.month, 1)
    val daysInMonth = when (anchorDate.month) {
        Month.JANUARY, Month.MARCH, Month.MAY, Month.JULY,
        Month.AUGUST, Month.OCTOBER, Month.DECEMBER -> 31
        Month.APRIL, Month.JUNE, Month.SEPTEMBER, Month.NOVEMBER -> 30
        Month.FEBRUARY -> if (anchorDate.year % 4 == 0 && (anchorDate.year % 100 != 0 || anchorDate.year % 400 == 0)) 29 else 28
    }

    // Offset: Monday=0 ... Sunday=6
    val startOffset = firstOfMonth.dayOfWeek.ordinal // 0=Monday
    val allDays = (0 until daysInMonth).map { firstOfMonth.plus(it, DateTimeUnit.DAY) }

    val grid = mutableListOf<List<LocalDate?>>()
    var dayIndex = 0
    var totalCells = 0

    // Row 1: padding + first days
    val row1 = mutableListOf<LocalDate?>()
    repeat(startOffset) { row1.add(null); totalCells++ }
    while (dayIndex < allDays.size && row1.size < 7) {
        row1.add(allDays[dayIndex])
        dayIndex++
        totalCells++
    }
    grid.add(row1)

    // Subsequent rows
    while (dayIndex < allDays.size) {
        val row = mutableListOf<LocalDate?>()
        repeat(7) {
            if (dayIndex < allDays.size) {
                row.add(allDays[dayIndex])
                dayIndex++
                totalCells++
            } else {
                row.add(null)
                totalCells++
            }
        }
        grid.add(row)
    }

    return grid
}

// ────────────────────────────────────────────────────────────
//  isTaskDueOnDay — calendar variant of isTaskDueToday
// ────────────────────────────────────────────────────────────

/**
 * Determines if a task is due on a specific [date].
 *
 * daily/weekly/monthly delegan en [RecurrenceRules.isDueOn] — antes esta
 * función reimplementaba esa misma lógica de recurrencia (con riesgo real de
 * divergencia respecto a la regla usada en `TaskListScreen`/`HomeScreenModel`).
 * "once" se queda fuera de [RecurrenceRules]: a diferencia de
 * `isDueToday`/`isDueOn` (que solo miran si está pendiente, ignorando
 * `dueDate`), el calendario necesita saber en qué celda de día concreto cae
 * la tarea, así que sí usa `dueDate` — un propósito distinto, no una
 * duplicación de la misma regla.
 */
private fun isTaskDueOnDay(task: TaskResponse, date: LocalDate, tz: TimeZone): Boolean {
    if (task.frequency == "once") {
        // Due if not completed yet
        if (task.lastCompletedDate != null) return false
        if (task.dueDate > 0) {
            val dueDate = Instant.fromEpochMilliseconds(task.dueDate).toLocalDateTime(tz).date
            return date >= dueDate
        }
        // No dueDate and not completed: show as due
        return true
    }
    return RecurrenceRules.isDueOn(
        date = date,
        frequency = task.frequency,
        recurrenceDays = task.recurrenceDays,
        recurrenceDay = task.recurrenceDay,
        lastCompletedDate = task.lastCompletedDate,
        tz = tz
    )
}

/**
 * Check if a task was completed on a specific date.
 */
private fun isTaskCompletedOnDay(task: TaskResponse, date: LocalDate, tz: TimeZone): Boolean {
    val lcd = task.lastCompletedDate ?: return false
    val lcdDate = Instant.fromEpochMilliseconds(lcd).toLocalDateTime(tz).date
    return lcdDate == date
}

/**
 * Check if a task is overdue relative to a specific date.
 * Overdue = task.dueDate has passed and task didn't get completed on/before the due date.
 * Only applies to "once" tasks with an explicit dueDate — recurring tasks
 * (daily/weekly/monthly) have no dueDate and are shown as due (not overdue)
 * on each day cell where they're pending, see isTaskDueOnDay.
 */
private fun isTaskOverdueOnDay(task: TaskResponse, date: LocalDate, tz: TimeZone): Boolean {
    // For "once" tasks with a dueDate
    if (task.frequency == "once" && task.dueDate > 0) {
        val dueDate = Instant.fromEpochMilliseconds(task.dueDate).toLocalDateTime(tz).date
        if (date > dueDate && task.lastCompletedDate == null) return true
        // If completed, check if it was completed after the due date
        val lcd = task.lastCompletedDate
        if (lcd != null) {
            val lcdDate = Instant.fromEpochMilliseconds(lcd).toLocalDateTime(tz).date
            if (lcdDate > dueDate) return true
        }
    }
    return false
}

// ────────────────────────────────────────────────────────────
//  Group tasks by date for the calendar
// ────────────────────────────────────────────────────────────

private fun groupTasksByDate(
    tasks: List<TaskResponse>,
    tz: TimeZone,
    mode: CalendarMode,
    weekRange: List<LocalDate>,
    monthGrid: List<List<LocalDate?>>
): Map<LocalDate, List<DayTaskEntry>> {
    val result = mutableMapOf<LocalDate, MutableList<DayTaskEntry>>()

    val datesToCheck = when (mode) {
        CalendarMode.WEEK -> weekRange
        CalendarMode.MONTH -> monthGrid.flatten().filterNotNull()
    }

    for (date in datesToCheck) {
        for (task in tasks) {
            val isDue = isTaskDueOnDay(task, date, tz)
            val isCompleted = isTaskCompletedOnDay(task, date, tz)
            val isOverdue = isTaskOverdueOnDay(task, date, tz)

            if (isDue || isCompleted) {
                result.getOrPut(date) { mutableListOf() }.add(
                    DayTaskEntry(
                        task = task,
                        isOverdue = isOverdue && !isCompleted,
                        isDueToday = isDue && !isCompleted,
                        isCompleted = isCompleted
                    )
                )
            }
        }
    }

    return result
}

// ────────────────────────────────────────────────────────────
//  Localized date name helpers
// ────────────────────────────────────────────────────────────

private fun dayAbbrHeaders(lang: String): List<String> = listOf(
    AppStrings.get("day_abbr_monday", lang),
    AppStrings.get("day_abbr_tuesday", lang),
    AppStrings.get("day_abbr_wednesday", lang),
    AppStrings.get("day_abbr_thursday", lang),
    AppStrings.get("day_abbr_friday", lang),
    AppStrings.get("day_abbr_saturday", lang),
    AppStrings.get("day_abbr_sunday", lang)
)

private fun dayFull(dayOfWeek: DayOfWeek, lang: String): String = when (dayOfWeek) {
    DayOfWeek.MONDAY -> AppStrings.get("recurrence_day_monday", lang)
    DayOfWeek.TUESDAY -> AppStrings.get("recurrence_day_tuesday", lang)
    DayOfWeek.WEDNESDAY -> AppStrings.get("recurrence_day_wednesday", lang)
    DayOfWeek.THURSDAY -> AppStrings.get("recurrence_day_thursday", lang)
    DayOfWeek.FRIDAY -> AppStrings.get("recurrence_day_friday", lang)
    DayOfWeek.SATURDAY -> AppStrings.get("recurrence_day_saturday", lang)
    DayOfWeek.SUNDAY -> AppStrings.get("recurrence_day_sunday", lang)
    else -> ""
}

private fun monthAbbr(month: Month, lang: String): String = when (month) {
    Month.JANUARY -> AppStrings.get("month_abbr_january", lang)
    Month.FEBRUARY -> AppStrings.get("month_abbr_february", lang)
    Month.MARCH -> AppStrings.get("month_abbr_march", lang)
    Month.APRIL -> AppStrings.get("month_abbr_april", lang)
    Month.MAY -> AppStrings.get("month_abbr_may", lang)
    Month.JUNE -> AppStrings.get("month_abbr_june", lang)
    Month.JULY -> AppStrings.get("month_abbr_july", lang)
    Month.AUGUST -> AppStrings.get("month_abbr_august", lang)
    Month.SEPTEMBER -> AppStrings.get("month_abbr_september", lang)
    Month.OCTOBER -> AppStrings.get("month_abbr_october", lang)
    Month.NOVEMBER -> AppStrings.get("month_abbr_november", lang)
    Month.DECEMBER -> AppStrings.get("month_abbr_december", lang)
    else -> ""
}

private fun monthFull(month: Month, lang: String): String = when (month) {
    Month.JANUARY -> AppStrings.get("month_full_january", lang)
    Month.FEBRUARY -> AppStrings.get("month_full_february", lang)
    Month.MARCH -> AppStrings.get("month_full_march", lang)
    Month.APRIL -> AppStrings.get("month_full_april", lang)
    Month.MAY -> AppStrings.get("month_full_may", lang)
    Month.JUNE -> AppStrings.get("month_full_june", lang)
    Month.JULY -> AppStrings.get("month_full_july", lang)
    Month.AUGUST -> AppStrings.get("month_full_august", lang)
    Month.SEPTEMBER -> AppStrings.get("month_full_september", lang)
    Month.OCTOBER -> AppStrings.get("month_full_october", lang)
    Month.NOVEMBER -> AppStrings.get("month_full_november", lang)
    Month.DECEMBER -> AppStrings.get("month_full_december", lang)
    else -> ""
}