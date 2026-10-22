package com.zly.mentalmath.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

private enum class AnalysisTab(val title: String) {
    WEAKNESS("薄弱诊断"),
    MISTAKE_BOOK("错题本"),
    HEATMAP("熟练度矩阵"),
    HISTORY("答题记录")
}

@Composable
fun AnalysisScreen(
    statsMap: Map<String, QuestionStat>,
    sessions: List<SessionRecord>,
    onStartMistakePractice: () -> Unit,
    onRemoveMistake: (String) -> Unit,
    onBack: () -> Unit
) {
    var currentTab by remember { mutableStateOf(AnalysisTab.WEAKNESS) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isPad = maxWidth >= 600.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (isPad) 24.dp else 16.dp, vertical = 10.dp)
        ) {
            // 三段式对称顶部标题栏（解决标题与返回按钮拥挤错位）
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    TextButton(
                        onClick = onBack,
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text(
                            text = "← 返回",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
                Box(
                    modifier = Modifier.weight(2.2f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "错题本 & 薄弱分析",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                }
                Box(modifier = Modifier.weight(1f))
            }

            if (isPad) {
                // Pad / 平板端：左右双栏看板（左栏切换薄弱诊断/错题本/记录，右栏常驻 10×10 熟练度矩阵热力图）
                val padTabs = listOf(
                    AnalysisTab.WEAKNESS,
                    AnalysisTab.MISTAKE_BOOK,
                    AnalysisTab.HISTORY
                )
                val activeLeftTab = if (currentTab == AnalysisTab.HEATMAP) {
                    AnalysisTab.WEAKNESS
                } else {
                    currentTab
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // 左栏：诊断 / 错题本 / 历史记录
                    Column(
                        modifier = Modifier
                            .weight(0.52f)
                            .fillMaxHeight()
                    ) {
                        SegmentedTabRow(
                            tabs = padTabs,
                            selectedTab = activeLeftTab,
                            onSelectTab = { currentTab = it }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
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
                                AnalysisTab.HISTORY -> SessionHistoryTab(sessions = sessions)
                                AnalysisTab.HEATMAP -> {}
                            }
                        }
                    }

                    // 右栏：常驻 10×10 熟练度矩阵热力图
                    Card(
                        modifier = Modifier
                            .weight(0.48f)
                            .fillMaxHeight(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "10 × 10 熟练度矩阵热力图",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                            MasteryHeatmapTab(statsMap = statsMap)
                        }
                    }
                }
            } else {
                // 手机端：四等分对称分段标签栏 + 内容列表
                SegmentedTabRow(
                    tabs = AnalysisTab.entries,
                    selectedTab = currentTab,
                    onSelectTab = { currentTab = it }
                )
                Spacer(modifier = Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (currentTab) {
                        AnalysisTab.WEAKNESS -> WeaknessDiagnosisTab(
                            statsMap = statsMap,
                            onStartMistakePractice = onStartMistakePractice
                        )
                        AnalysisTab.MISTAKE_BOOK -> MistakeBookTab(
                            statsMap = statsMap,
                            onStartMistakePractice = onStartMistakePractice,
                            onRemoveMistake = onRemoveMistake
                        )
                        AnalysisTab.HEATMAP -> MasteryHeatmapTab(statsMap = statsMap)
                        AnalysisTab.HISTORY -> SessionHistoryTab(sessions = sessions)
                    }
                }
            }
        }
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
            .padding(top = 4.dp),
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
                    .height(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bgColor)
                    .clickable { onSelectTab(tab) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tab.title,
                    fontSize = 13.sp,
                    maxLines = 1,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
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
                Text(
                    text = "🔍 核心薄弱点：${topWeak.category.title}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
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
                Text(
                    text = "👏 当前错题本为空！",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
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

@Composable
private fun MasteryHeatmapTab(statsMap: Map<String, QuestionStat>) {
    var selectedOp by remember { mutableStateOf(Operator.ADD) }

    // 严格对称等宽切换按钮
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
                    .clickable { selectedOp = op },
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

    Text(
        text = "🟢 秒杀(<1.2s)   🟡 熟练(1.2~2s)   🔴 薄弱/错题   ⚪ 未练",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
    )

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (a in 0..10) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
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
                                .height(32.dp)
                        )
                    } else {
                        val key = "$a${selectedOp.symbol}$b"
                        val level = WeaknessAnalyzer.evaluateMastery(statsMap[key])
                        val bg = when (level) {
                            MasteryLevel.MASTERED -> Color(0xFF81C784)
                            MasteryLevel.FAMILIAR -> Color(0xFFFFD54F)
                            MasteryLevel.WEAK -> Color(0xFFE57373)
                            MasteryLevel.UNPRACTICED -> MaterialTheme.colorScheme.surfaceVariant
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(bg),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = key,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
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
