package org.taskhub.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.datetime.*
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.taskhub.network.FirestoreRepository
import org.taskhub.network.models.MemberResponse
import org.taskhub.network.models.TaskResponse
import org.taskhub.network.models.TaskAssignmentResponse
import org.taskhub.network.models.TaskHistoryResponse
import org.taskhub.ui.components.LocalAppSettings
import org.taskhub.ui.components.TaskHubTopBar
import org.taskhub.ui.i18n.AppStrings
import org.taskhub.ui.models.Achievement
import org.taskhub.ui.models.AchievementChecker
import org.taskhub.ui.models.TaskScreenModel
import org.taskhub.ui.theme.*

data class StatsScreen(
    val householdId: String,
    val memberId: String
) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val appSettings = LocalAppSettings.current
        val s = { key: String -> AppStrings.get(key, appSettings.currentLanguage) }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top bar
                TaskHubTopBar(
                    title = s("explore_tab_stats"),
                    onBack = { navigator.pop() }
                )

                StatsBody(householdId, memberId)
            }
        }
    }
}

/** Contenido reutilizable de estadísticas (sin barra superior), para la pantalla combinada. */
@Composable
internal fun StatsBody(householdId: String, memberId: String) {
    val repo = koinInject<FirestoreRepository>()
    val appSettings = LocalAppSettings.current
    val s = { key: String -> AppStrings.get(key, appSettings.currentLanguage) }

    var statsData by remember { mutableStateOf<MemberStatsData?>(null) }
    var achievements by remember { mutableStateOf<List<Achievement>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // ── Función de carga extraíble para poder reintentar ──
    suspend fun loadStats() {
        isLoading = true
        try {
            // Load all data — including taskHistory for accurate stats
            val tasks = repo.getTasks(householdId)
            val assignments = repo.getAllAssignments(householdId)
            val history = repo.getTaskHistory(householdId)
            val members = repo.getMembers(householdId)
            val member = members.find { it.id == memberId }

            if (member != null) {
                statsData = computeStats(tasks, assignments, history, member, appSettings.currentLanguage)
                val unlocked = repo.getMemberAchievements(householdId, memberId)
                achievements = AchievementChecker.getAchievementsWithStatus(unlocked)
                errorMessage = null
            } else {
                // Sin esto, la pantalla se quedaba completamente en blanco (ni error, ni
                // reintento) cuando memberId aún no se había resuelto (currentMemberId
                // arranca en "" y se resuelve de forma asíncrona vía red) — el usuario no
                // podía distinguir "cargando" de "roto".
                errorMessage = s("transfer_error_member_not_found")
            }
        } catch (e: Exception) {
            errorMessage = e.message ?: s("stats_error_loading")
        }
        isLoading = false
    }

    LaunchedEffect(householdId, memberId) {
        loadStats()
    }

    when {
                    isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Teal600)
                        }
                    }
                    errorMessage != null -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("❌ $errorMessage", color = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.height(16.dp))
                                Button(onClick = { coroutineScope.launch { loadStats() } }) { Text(s("tasks_retry")) }
                            }
                        }
                    }
                    statsData != null -> {
                        val data = statsData!!
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Streak card
                            item {
                                StreakCard(
                                    currentStreak = data.currentStreak,
                                    bestStreak = data.bestStreak
                                )
                            }

                            // Bar chart: tasks per day
                            item {
                                BarChartCard(
                                    title = s("stats_chart_tasks_title"),
                                    data = data.tasksPerDay
                                )
                            }

                            // Points chart
                            item {
                                PointsChartCard(
                                    title = s("stats_chart_points_title"),
                                    dailyPoints = data.dailyPoints
                                )
                            }

                            // Pie chart: distribution by category/tag
                            if (data.tasksByTag.isNotEmpty()) {
                                item {
                                    PieChartCard(
                                        title = s("stats_chart_category_title"),
                                        data = data.tasksByTag
                                    )
                                }
                            }

                            // Summary stats
                            item {
                                SummaryStatsCard(
                                    totalTasks = data.totalTasksCompleted,
                                    totalPoints = data.totalPoints,
                                    onTimeRate = data.onTimeRate,
                                    overdueCount = data.overdueCount
                                )
                            }

                            // Achievements
                            if (achievements.isNotEmpty()) {
                                item {
                                    Text(
                                        text = s("stats_achievements_title"),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }

                                items(achievements, key = { it.id }) { achievement ->
                                    AchievementCard(achievement)
                                }
                            }

                            item { Spacer(Modifier.height(16.dp)) }
                        }
                    }
                }
}

// ── Data model ──────────────────────────────────────────────

data class MemberStatsData(
    val currentStreak: Int,
    val bestStreak: Int,
    val tasksPerDay: List<DayCount>,        // Last 7 days
    val dailyPoints: List<DayPoints>,        // Last 7 days
    val tasksByTag: List<TagCount>,          // Tag distribution
    val totalTasksCompleted: Int,
    val totalPoints: Int,
    val onTimeRate: Float,                   // 0.0 - 1.0
    val overdueCount: Int
)

data class DayCount(val dayLabel: String, val count: Int)
data class DayPoints(val dayLabel: String, val points: Int)
data class TagCount(val tag: String, val count: Int)

// ── Computation ────────────────────────────────────────────

private fun computeStats(
    tasks: List<TaskResponse>,
    assignments: List<TaskAssignmentResponse>,
    history: List<TaskHistoryResponse>,
    member: MemberResponse,
    lang: String
): MemberStatsData {
    val tz = TimeZone.currentSystemDefault()
    val now = Clock.System.now()
    val today = now.toLocalDateTime(tz).date

    // Merge completions from assignments AND taskHistory
    // taskHistory captures direct completeTask() calls
    // assignments capture completeAssignment() calls
    val memberHistory = history.filter { it.memberId == member.id }

    // Tasks completed by member (from assignments)
    val memberAssignments = assignments.filter { it.memberId == member.id }
    val completedAssignments = memberAssignments.filter { it.status == "completed" && it.completedAt != null }

    // Combine both sources for per-day counts
    data class CompletionRecord(val completedAt: Long, val points: Int, val onTime: Boolean)

    val fromAssignments = completedAssignments.map { a ->
        CompletionRecord(a.completedAt ?: 0L, a.pointsAwarded ?: 0, a.onTime ?: true)
    }
    val fromHistory = memberHistory.map { h ->
        CompletionRecord(h.completedAt, h.points, h.onTime)
    }
    val allCompletions = fromAssignments + fromHistory

    // Tasks per day (last 7 days)
    val days = (0..6).map { offset ->
        val date = today.plus(-offset, DateTimeUnit.DAY)
        date
    }.reversed() // Most recent last

    val tasksPerDay = days.map { date ->
        val dayStart = date.atStartOfDayIn(tz).toEpochMilliseconds()
        val dayEnd = date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(tz).toEpochMilliseconds()
        val count = allCompletions.count { c ->
            c.completedAt >= dayStart && c.completedAt < dayEnd
        }
        val dayLabel = "${date.dayOfMonth}/${date.monthNumber}"
        DayCount(dayLabel, count)
    }

    // Daily points (last 7 days)
    val dailyPoints = days.map { date ->
        val dayStart = date.atStartOfDayIn(tz).toEpochMilliseconds()
        val dayEnd = date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(tz).toEpochMilliseconds()
        val points = allCompletions.sumOf { c ->
            if (c.completedAt in dayStart until dayEnd) c.points else 0
        }
        DayPoints("${date.dayOfMonth}/${date.monthNumber}", points)
    }

    // Tag distribution — from completed assignments (which have taskId)
    val taskMap = tasks.associateBy { it.id }
    val completedTaskIds = completedAssignments.map { it.taskId }.distinct()
    val completedTasks = completedTaskIds.mapNotNull { taskMap[it] }
    val tagCounts = mutableMapOf<String, Int>()
    for (task in completedTasks) {
        for (tag in task.tags.ifEmpty { listOf(AppStrings.get("stats_no_category", lang)) }) {
            tagCounts[tag] = (tagCounts[tag] ?: 0) + 1
        }
    }
    val tasksByTag = tagCounts.entries
        .sortedByDescending { it.value }
        .take(6)
        .map { TagCount(it.key, it.value) }

    // On-time rate — from all completions
    val onTimeCount = allCompletions.count { it.onTime }

    // Total completions = allCompletions (from both sources)
    val totalCompletions = allCompletions.size

    return MemberStatsData(
        currentStreak = member.currentStreak,
        bestStreak = member.bestStreak,
        tasksPerDay = tasksPerDay,
        dailyPoints = dailyPoints,
        tasksByTag = tasksByTag,
        totalTasksCompleted = totalCompletions,
        totalPoints = member.totalPoints,
        onTimeRate = if (totalCompletions > 0) onTimeCount.toFloat() / totalCompletions else 0f,
        overdueCount = allCompletions.count { !it.onTime }
    )
}

// ── UI Components ──────────────────────────────────────────

@Composable
private fun StreakCard(currentStreak: Int, bestStreak: Int) {
    val appSettings = LocalAppSettings.current
    val s = { key: String -> AppStrings.get(key, appSettings.currentLanguage) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Coral100),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🔥", style = MaterialTheme.typography.displaySmall)
                Text(
                    s("stats_current_streak_label"),
                    style = MaterialTheme.typography.bodySmall,
                    // Color fijo (no onSurfaceVariant): el fondo Coral100 de esta card
                    // tampoco cambia con el tema/modo oscuro (1.30:1 con onSurfaceVariant en dark).
                    color = Coral800
                )
                Text(
                    s("stats_days_suffix").replace("%d", currentStreak.toString()),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Coral700
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🏆", style = MaterialTheme.typography.displaySmall)
                Text(
                    s("stats_best_streak_label"),
                    style = MaterialTheme.typography.bodySmall,
                    color = Coral800
                )
                Text(
                    s("stats_days_suffix").replace("%d", bestStreak.toString()),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Teal800
                )
            }
        }
    }
}

@Composable
private fun BarChartCard(title: String, data: List<DayCount>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))

            val maxCount = (data.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)
            val barColor = Teal500
            val textMeasurer = rememberTextMeasurer()
            val labelTextStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
            // Los Canvas de esta pantalla no tienen ningún texto alternativo:
            // para un lector de pantalla, las tres tarjetas de estadísticas son
            // invisibles/mudas sin esto.
            val chartDescription = remember(data) {
                data.joinToString(", ") { "${it.dayLabel}: ${it.count}" }
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .semantics { contentDescription = "$title. $chartDescription" }
            ) {
                val chartWidth = size.width
                val chartHeight = size.height - 30f
                val barCount = data.size
                if (barCount == 0) return@Canvas

                val barWidth = (chartWidth / barCount) * 0.6f
                val gap = (chartWidth / barCount) * 0.4f

                data.forEachIndexed { index, dayCount ->
                    val barHeight = if (maxCount > 0) (dayCount.count.toFloat() / maxCount) * chartHeight else 0f
                    val x = index * (barWidth + gap) + gap / 2

                    // Bar
                    drawRect(
                        color = barColor,
                        topLeft = Offset(x, chartHeight - barHeight),
                        size = Size(barWidth, barHeight.coerceAtLeast(2f))
                    )

                    // Count on top
                    if (dayCount.count > 0) {
                        val textLayout = textMeasurer.measure(
                            "${dayCount.count}",
                            labelTextStyle
                        )
                        drawText(
                            textLayout,
                            topLeft = Offset(
                                x + barWidth / 2 - textLayout.size.width / 2,
                                chartHeight - barHeight - textLayout.size.height - 4f
                            )
                        )
                    }

                    // Label
                    val labelLayout = textMeasurer.measure(
                        dayCount.dayLabel,
                        labelTextStyle
                    )
                    drawText(
                        labelLayout,
                        topLeft = Offset(
                            x + barWidth / 2 - labelLayout.size.width / 2,
                            chartHeight + 5f
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun PointsChartCard(title: String, dailyPoints: List<DayPoints>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))

            val maxPoints = (dailyPoints.maxOfOrNull { it.points } ?: 10).coerceAtLeast(1)
            val textMeasurer = rememberTextMeasurer()
            val lineColor = Coral500
            val pointColor = Coral600
            val labelTextStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
            val chartDescription = remember(dailyPoints) {
                dailyPoints.joinToString(", ") { "${it.dayLabel}: ${it.points}" }
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .semantics { contentDescription = "$title. $chartDescription" }
            ) {
                val chartWidth = size.width
                val chartHeight = size.height - 30f
                val padding = 20f
                val usableWidth = chartWidth - padding * 2
                val usableHeight = chartHeight

                if (dailyPoints.isEmpty()) return@Canvas

                val points = dailyPoints.mapIndexed { index, dp ->
                    val x = padding + (index.toFloat() / (dailyPoints.size - 1).coerceAtLeast(1)) * usableWidth
                    val y = usableHeight - (dp.points.toFloat() / maxPoints) * usableHeight
                    Offset(x, y)
                }

                // Draw line
                for (i in 0 until points.size - 1) {
                    drawLine(
                        color = lineColor,
                        start = points[i],
                        end = points[i + 1],
                        strokeWidth = 3f,
                        cap = StrokeCap.Round
                    )
                }

                // Draw points
                points.forEach { point ->
                    drawCircle(color = pointColor, radius = 5f, center = point)
                    drawCircle(color = Color.White, radius = 3f, center = point)
                }

                // Labels
                dailyPoints.forEachIndexed { index, dp ->
                    val labelLayout = textMeasurer.measure(
                        dp.dayLabel,
                        labelTextStyle
                    )
                    val x = padding + (index.toFloat() / (dailyPoints.size - 1).coerceAtLeast(1)) * usableWidth
                    drawText(
                        labelLayout,
                        topLeft = Offset(
                            x - labelLayout.size.width / 2,
                            chartHeight + 5f
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun PieChartCard(title: String, data: List<TagCount>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))

            val colors = listOf(Teal500, Coral500, Teal300, Coral300, Teal700, Coral700)
            val total = data.sumOf { it.count }.toFloat().coerceAtLeast(1f)
            val chartDescription = remember(data) {
                data.joinToString(", ") { "${it.tag}: ${it.count}" }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pie chart
                Canvas(
                    modifier = Modifier
                        .size(140.dp)
                        .semantics { contentDescription = "$title. $chartDescription" }
                ) {
                    var startAngle = -90f
                    data.forEachIndexed { index, tagCount ->
                        val sweep = (tagCount.count.toFloat() / total) * 360f
                        drawArc(
                            color = colors[index % colors.size],
                            startAngle = startAngle,
                            sweepAngle = sweep,
                            useCenter = true,
                            size = Size(size.width, size.height)
                        )
                        startAngle += sweep
                    }
                }

                Spacer(Modifier.width(16.dp))

                // Legend
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    data.forEachIndexed { index, tagCount ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(colors[index % colors.size], CircleShape)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${tagCount.tag} (${tagCount.count})",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryStatsCard(
    totalTasks: Int,
    totalPoints: Int,
    onTimeRate: Float,
    overdueCount: Int
) {
    val appSettings = LocalAppSettings.current
    val s = { key: String -> AppStrings.get(key, appSettings.currentLanguage) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                s("stats_summary_title"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(s("stats_summary_tasks"), "$totalTasks", "✅")
                StatItem(s("stats_summary_points"), "$totalPoints", "⭐")
                StatItem(s("stats_summary_on_time"), "${(onTimeRate * 100).toInt()}%", "⏱️")
                StatItem(s("stats_summary_overdue"), "$overdueCount", "⚠️")
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, emoji: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, style = MaterialTheme.typography.titleLarge)
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AchievementCard(achievement: Achievement) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (achievement.isUnlocked) Teal50 else MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (achievement.isUnlocked) achievement.emoji else "🔒",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = achievement.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (achievement.isUnlocked) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = achievement.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}