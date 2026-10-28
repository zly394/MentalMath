package com.zly.mentalmath.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zly.mentalmath.domain.QuestionGenerator
import com.zly.mentalmath.domain.WeaknessAnalyzer
import com.zly.mentalmath.model.MasteryLevel
import com.zly.mentalmath.model.Operator
import com.zly.mentalmath.model.QuestionStat
import com.zly.mentalmath.model.SessionRecord
import com.zly.mentalmath.ui.components.AppIcons

/**
 * 精炼三主项：薄弱诊断、错题专练、全库热力
 */
private enum class AnalysisTab(val title: String) {
    WEAKNESS("薄弱诊断"),
    MISTAKE_BOOK("错题专练"),
    HEATMAP("全库热力")
}

@Composable
fun AnalysisScreen(
    statsMap: Map<String, QuestionStat>,
    sessions: List<SessionRecord>,
    initialTabIndex: Int = 0,
    onStartMistakePractice: () -> Unit,
    onRemoveMistake: (String) -> Unit,
    onBack: () -> Unit
) {
    var currentTab by remember {
        mutableStateOf(AnalysisTab.entries.getOrElse(initialTabIndex) { AnalysisTab.WEAKNESS })
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isPad = maxWidth >= 600.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (isPad) 24.dp else 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 顶栏：返回 + 居中标题
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    BackButton(onClick = onBack)
                }
                Box(
                    modifier = Modifier.weight(2.2f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "学情体检 & 错题复盘",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                }
                Box(modifier = Modifier.weight(1f))
            }

            // 🌟 顶部学情体检总评看板（一目了然看清错题数、均速水准与全库掌握度）
            AnalysisSummaryBanner(statsMap = statsMap)

            if (isPad) {
                // 平板端左右双栏：左侧诊断与错题消灭（高频互动），右侧全库热力与战报历史（全局纵览）
                val padTabs = listOf(AnalysisTab.WEAKNESS, AnalysisTab.MISTAKE_BOOK)
                val activeLeftTab = if (currentTab == AnalysisTab.HEATMAP) AnalysisTab.WEAKNESS else currentTab

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // 左栏（50%）：薄弱诊断 / 错题专练
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SegmentedTabRow(
                            tabs = padTabs,
                            selectedTab = activeLeftTab,
                            onSelectTab = { currentTab = it }
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            when (activeLeftTab) {
                                AnalysisTab.WEAKNESS -> WeaknessDiagnosisTab(
                                    statsMap = statsMap,
                                    onStartMistakePractice = onStartMistakePractice
                                )
                                AnalysisTab.MISTAKE_BOOK -> MistakeBookTab(
                                    statsMap = statsMap,
                                    onStartMistakePractice = onStartMistakePractice,
                                    onRemoveMistake = onRemoveMistake
                                )
                                AnalysisTab.HEATMAP -> {}
                            }
                        }
                    }

                    // 右栏（50%）：常驻 10×10 热力矩阵 + 近期训练历史
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "10 × 10 熟练度全库热力矩阵",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                MasteryHeatmapTab(statsMap = statsMap)
                            }
                        }

                        // 训练历史模块
                        if (sessions.isNotEmpty()) {
                            Text(
                                text = "近期训练记录",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            SessionHistoryTab(sessions = sessions)
                        }
                    }
                }
            } else {
                // 手机端：舒展 3 Tab（薄弱诊断 / 错题专练 / 全库热力）
                SegmentedTabRow(
                    tabs = AnalysisTab.entries,
                    selectedTab = currentTab,
                    onSelectTab = { currentTab = it }
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (currentTab) {
                        AnalysisTab.WEAKNESS -> {
                            WeaknessDiagnosisTab(
                                statsMap = statsMap,
                                onStartMistakePractice = onStartMistakePractice
                            )
                            if (sessions.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "近期答题战报",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                SessionHistoryTab(sessions = sessions)
                            }
                        }
                        AnalysisTab.MISTAKE_BOOK -> MistakeBookTab(
                            statsMap = statsMap,
                            onStartMistakePractice = onStartMistakePractice,
                            onRemoveMistake = onRemoveMistake
                        )
                        AnalysisTab.HEATMAP -> {
                            MasteryHeatmapTab(statsMap = statsMap)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 🌟 顶部学情体检总评看板（错题数、均速水准与全库掌握度）
 */
@Composable
private fun AnalysisSummaryBanner(statsMap: Map<String, QuestionStat>) {
    val mistakesCount = remember(statsMap) { statsMap.values.count { it.isInMistakeBook } }
    val totalCorrectCount = remember(statsMap) { statsMap.values.sumOf { it.correctCount.toLong() } }
    val totalCorrectTimeMs = remember(statsMap) { statsMap.values.sumOf { it.totalCorrectTimeMs } }
    val avgSpeedMs = if (totalCorrectCount > 0L) totalCorrectTimeMs / totalCorrectCount else 0L
    val speedRating = when {
        avgSpeedMs == 0L -> "未测"
        avgSpeedMs < 1000L -> "极速"
        avgSpeedMs < 1500L -> "优秀"
        avgSpeedMs < 2000L -> "良好"
        else -> "待提升"
    }
    val masteredCount = remember(statsMap) {
        statsMap.values.count {
            val level = WeaknessAnalyzer.evaluateMastery(it)
            level == MasteryLevel.MASTERED || level == MasteryLevel.FAMILIAR
        }
    }
    val masteryPercent = ((masteredCount / 132f) * 100).toInt()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SummaryPillItem(
                icon = { AppIcons.MistakeBook(modifier = Modifier.size(13.dp), tint = Color(0xFFDC2626)) },
                label = "待消灭错题",
                value = "${mistakesCount}题",
                valueColor = if (mistakesCount > 0) Color(0xFFD32F2F) else Color(0xFF2E7D32)
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(28.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            )
            SummaryPillItem(
                icon = { AppIcons.Bolt(modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary) },
                label = "均速评级",
                value = if (avgSpeedMs == 0L) "--" else "${avgSpeedMs}ms ($speedRating)",
                valueColor = MaterialTheme.colorScheme.primary
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(28.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            )
            SummaryPillItem(
                icon = { AppIcons.Trophy(modifier = Modifier.size(13.dp), tint = Color(0xFFD97706)) },
                label = "全库掌握率",
                value = "$masteryPercent%",
                valueColor = Color(0xFF2E7D32)
            )
        }
    }
}

@Composable
private fun SummaryPillItem(
    icon: @Composable () -> Unit,
    label: String,
    value: String,
    valueColor: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            icon()
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            color = valueColor
        )
    }
}

@Composable
private fun SegmentedTabRow(
    tabs: List<AnalysisTab>,
    selectedTab: AnalysisTab,
    onSelectTab: (AnalysisTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tabs.forEach { tab ->
            val selected = selectedTab == tab
            val bgColor = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
            val textColor = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(bgColor)
                    .clickable { onSelectTab(tab) },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    when (tab) {
                        AnalysisTab.WEAKNESS -> AppIcons.Diagnosis(modifier = Modifier.size(14.dp), tint = textColor)
                        AnalysisTab.MISTAKE_BOOK -> AppIcons.MistakeBook(modifier = Modifier.size(14.dp), tint = textColor)
                        AnalysisTab.HEATMAP -> AppIcons.Chart(modifier = Modifier.size(14.dp), tint = textColor)
                    }
                    Text(
                        text = tab.title,
                        fontSize = 13.5.sp,
                        maxLines = 1,
                        fontWeight = FontWeight.ExtraBold,
                        color = textColor
                    )
                }
            }
        }
    }
}

@Composable
private fun WeaknessDiagnosisTab(
    statsMap: Map<String, QuestionStat>,
    onStartMistakePractice: () -> Unit
) {
    val diagnoses = remember(statsMap) { WeaknessAnalyzer.analyzeCategories(statsMap) }
    val slowestQuestions = remember(statsMap) {
        statsMap.values
            .filter { it.totalAttempts > 0 }
            .sortedWith(
                compareByDescending<QuestionStat> { it.isInMistakeBook }
                    .thenBy { it.accuracy }
                    .thenByDescending { it.avgResponseMs }
            )
            .take(8)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            val topWeak = diagnoses.firstOrNull { it.totalAttempts > 0 }
            if (topWeak == null) {
                Text(
                    text = "暂无足够答题数据，完成一轮挑战后将自动为您生成一年级五大口算阶梯诊断报告。",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AppIcons.Search(
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "核心薄弱点：${topWeak.category.title}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "教学建议：${topWeak.category.advice}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onStartMistakePractice,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("针对薄弱项与错题专项强化", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    Text(
        text = "五大口算认知阶梯表现（按薄弱度排序）",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold
    )

    diagnoses.forEach { item ->
        val accPercent = (item.accuracy * 100).toInt()
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.category.title,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = if (item.totalAttempts == 0) {
                            "未练 (${item.practicedCount}/${item.totalQuestionsInCategory}题)"
                        } else {
                            "正确率 $accPercent% · 均速 ${item.avgResponseMs}ms"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = item.category.advice,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (slowestQuestions.isNotEmpty()) {
        Text(
            text = "最需强化的高频卡壳算式 Top 8",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        slowestQuestions.forEach { stat ->
            val q = QuestionGenerator.findByKey(stat.key)
            val expr = q?.fullExpression ?: stat.key
            val acc = (stat.accuracy * 100).toInt()
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = expr,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "答 ${stat.totalAttempts}次 · 对 $acc% · 均速 ${stat.avgResponseMs}ms",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun MistakeBookTab(
    statsMap: Map<String, QuestionStat>,
    onStartMistakePractice: () -> Unit,
    onRemoveMistake: (String) -> Unit
) {
    val mistakes = remember(statsMap) {
        statsMap.values
            .filter { it.isInMistakeBook }
            .sortedByDescending { it.wrongCount + it.timeoutCount }
    }

    if (mistakes.isEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AppIcons.Check(
                        modifier = Modifier.size(18.dp),
                        tint = Color(0xFF16A34A)
                    )
                    Text(
                        text = "当前错题本为空，全部掌握！",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "答错或超时的题目会自动收录到这里，在『错题消灭』模式中连续秒对 2 次即可自动移出。",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    Button(
        onClick = onStartMistakePractice,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100))
    ) {
        Text(
            text = "开始错题专项消灭（共 ${mistakes.size} 道待消灭）",
            fontWeight = FontWeight.Bold
        )
    }

    mistakes.forEach { stat ->
        val q = QuestionGenerator.findByKey(stat.key)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 10.dp)
                ) {
                    Text(
                        text = q?.fullExpression ?: stat.key,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "错 ${stat.wrongCount} 次 · 超时 ${stat.timeoutCount} 次 · 再对 ${stat.mistakeStreakToClear} 次移出",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = { onRemoveMistake(stat.key) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("已掌握", fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    }
}

/**
 * 🌟 交互式熟练度热力矩阵（去密恐化，点击色块实时查看单题微观透视）
 */
@Composable
private fun MasteryHeatmapTab(statsMap: Map<String, QuestionStat>) {
    var selectedOp by remember { mutableStateOf(Operator.ADD) }
    var selectedKey by remember { mutableStateOf<String?>(null) }

    // 运算符切换
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        listOf(
            Operator.ADD to "10以内加法 (66题)",
            Operator.SUBTRACT to "10以内减法 (66题)"
        ).forEach { (op, label) ->
            val selected = selectedOp == op
            val bgColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bgColor)
                    .clickable {
                        selectedOp = op
                        selectedKey = null
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }

    val currentStat = selectedKey?.let { statsMap[it] }
    val currentQuestion = selectedKey?.let { QuestionGenerator.findByKey(it) }
    val selectedA = currentQuestion?.a
    val selectedB = currentQuestion?.b

    // 图例与坐标轴说明
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "纵轴 a  ${selectedOp.symbol}  横轴 b",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                AppIcons.StatusDot(color = Color(0xFF10B981), size = 6.dp)
                Text(text = "秒杀", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                AppIcons.StatusDot(color = Color(0xFFF59E0B), size = 6.dp)
                Text(text = "熟练", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                AppIcons.StatusDot(color = Color(0xFFEF4444), size = 6.dp)
                Text(text = "薄弱", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                AppIcons.StatusDot(color = Color(0xFFCBD5E1), size = 6.dp)
                Text(text = "未练", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    // 晶莹色块矩阵（含轻量坐标轴与十字精准定位）
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        // 顶部横坐标表头 (b = 0..10)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左上角原点运算符标记
            Box(
                modifier = Modifier.width(20.dp).height(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = selectedOp.symbol,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }

            for (b in 0..10) {
                val isColSelected = selectedB == b
                Box(
                    modifier = Modifier.weight(1f).height(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = b.toString(),
                        fontSize = 11.sp,
                        fontWeight = if (isColSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                        color = if (isColSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    )
                }
            }
        }

        // 矩阵数据行 (a = 0..10)
        for (a in 0..10) {
            val isRowSelected = selectedA == a
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧纵坐标数字 (a = 0..10)
                Box(
                    modifier = Modifier.width(20.dp).height(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = a.toString(),
                        fontSize = 11.sp,
                        fontWeight = if (isRowSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                        color = if (isRowSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    )
                }

                for (b in 0..10) {
                    val valid = if (selectedOp == Operator.ADD) {
                        a + b <= 10
                    } else {
                        a - b >= 0
                    }
                    if (!valid) {
                        Spacer(
                            modifier = Modifier
                                .weight(1f)
                                .height(24.dp)
                        )
                    } else {
                        val key = "$a${selectedOp.symbol}$b"
                        val stat = statsMap[key]
                        val level = WeaknessAnalyzer.evaluateMastery(stat)
                        val isSelected = selectedKey == key
                        val bg = when (level) {
                            MasteryLevel.MASTERED -> Color(0xFF81C784)
                            MasteryLevel.FAMILIAR -> Color(0xFFFFD54F)
                            MasteryLevel.WEAK -> Color(0xFFE57373)
                            MasteryLevel.UNPRACTICED -> MaterialTheme.colorScheme.surfaceVariant
                        }

                        val borderModifier = when {
                            isSelected -> Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(5.dp))
                            stat?.isInMistakeBook == true -> Modifier.border(1.5.dp, Color(0xFFD32F2F), RoundedCornerShape(5.dp))
                            else -> Modifier
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(24.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(bg)
                                .then(borderModifier)
                                .clickable { selectedKey = key },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .width(6.dp)
                                        .height(6.dp)
                                        .background(Color.White, RoundedCornerShape(3.dp))
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(2.dp))

    // 单题微观透视信息卡片（置于热力图下方，保证热力图在点击探查时绝对零跳动）
    if (selectedKey != null && currentQuestion != null) {
        val level = WeaknessAnalyzer.evaluateMastery(currentStat)
        val levelName = when (level) {
            MasteryLevel.MASTERED -> "秒杀精通 (<1.2s)"
            MasteryLevel.FAMILIAR -> "熟练掌握 (1.2~2s)"
            MasteryLevel.WEAK -> "错题薄弱"
            MasteryLevel.UNPRACTICED -> "尚未作答"
        }
        val dotColor = when (level) {
            MasteryLevel.MASTERED -> Color(0xFF10B981)
            MasteryLevel.FAMILIAR -> Color(0xFFF59E0B)
            MasteryLevel.WEAK -> Color(0xFFEF4444)
            MasteryLevel.UNPRACTICED -> Color(0xFF94A3B8)
        }
        val cardBg = when (level) {
            MasteryLevel.MASTERED -> Color(0xFFE8F5E9)
            MasteryLevel.FAMILIAR -> Color(0xFFFFF8E1)
            MasteryLevel.WEAK -> Color(0xFFFFEBEE)
            MasteryLevel.UNPRACTICED -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = currentQuestion.fullExpression,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        AppIcons.StatusDot(color = dotColor, size = 6.dp)
                        Text(
                            text = "$levelName · 累计答 ${currentStat?.totalAttempts ?: 0}次 · 均速 ${currentStat?.avgResponseMs ?: 0}ms",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (currentStat?.isInMistakeBook == true) {
                    Surface(
                        color = Color(0xFFFFCDD2),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "待消灭错题",
                            color = Color(0xFFC62828),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    } else {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIcons.Lightbulb(modifier = Modifier.size(13.dp), tint = Color(0xFFD97706))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "点击上方任意色块，可查看单道算式的精确战绩与反应用时",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun SessionHistoryTab(sessions: List<SessionRecord>) {
    if (sessions.isEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = "暂无训练历史记录，完成一轮挑战后将自动保存在此。",
                modifier = Modifier.padding(20.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        return
    }

    sessions.forEachIndexed { idx, record ->
        val accuracy = if (record.totalCount == 0) {
            0
        } else {
            (record.correctCount * 100) / record.totalCount
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "#${sessions.size - idx} ${record.modeLabel}（限时 ${record.timeLimitLabel}）",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "对 ${record.correctCount} · 错 ${record.wrongCount} · 超时 ${record.timeoutCount} · 均速 ${record.avgResponseMs}ms",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "$accuracy%",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
