package com.zly.mentalmath.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zly.mentalmath.model.InputMode
import com.zly.mentalmath.model.PracticeConfig
import com.zly.mentalmath.model.QuestionMode
import com.zly.mentalmath.model.QuestionStat
import com.zly.mentalmath.model.SessionRecord
import com.zly.mentalmath.model.TimeLimitOption

@Composable
fun HomeScreen(
    config: PracticeConfig,
    statsMap: Map<String, QuestionStat>,
    sessions: List<SessionRecord>,
    onSelectQuestionMode: (QuestionMode) -> Unit,
    onSelectTimeLimit: (TimeLimitOption) -> Unit,
    onSelectInputMode: (InputMode) -> Unit,
    onStartPractice: () -> Unit,
    onStartMistakeReview: () -> Unit,
    onOpenAnalysis: () -> Unit
) {
    val mistakeCount = statsMap.values.count { it.isInMistakeBook }
    val totalAnswered = statsMap.values.sumOf { it.totalAttempts }
    val totalCorrect = statsMap.values.sumOf { it.correctCount }
    val overallAccuracy = if (totalAnswered == 0) {
        0
    } else {
        ((totalCorrect.toFloat() / totalAnswered) * 100).toInt()
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isPad = maxWidth >= 600.dp

        if (isPad) {
            // Pad / 平板横竖屏双栏自适应布局（居中定宽，高度均衡对称）
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 1140.dp)
                        .padding(horizontal = 28.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // 左栏：品牌中枢 + 仪表盘 + 重点错题快速通道 + 近期战绩/口诀秘籍
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        BrandHeader()
                        OverviewDashboardCard(
                            statsMap = statsMap,
                            totalAnswered = totalAnswered,
                            overallAccuracy = overallAccuracy,
                            mistakeCount = mistakeCount,
                            onOpenAnalysis = onOpenAnalysis,
                            onStartMistakeReview = onStartMistakeReview
                        )
                        MistakeQuickCards(
                            mistakes = statsMap.values.filter { it.isInMistakeBook },
                            onStartMistakeReview = onStartMistakeReview
                        )
                        RecentSessionHistoryCard(sessions = sessions)
                    }

                    // 右栏：三大训练参数配置 + 开始挑战大按钮
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        ConfigCardsGroup(
                            config = config,
                            onSelectQuestionMode = onSelectQuestionMode,
                            onSelectTimeLimit = onSelectTimeLimit,
                            onSelectInputMode = onSelectInputMode
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        StartChallengeButton(
                            config = config,
                            onClick = onStartPractice
                        )
                    }
                }
            }
        } else {
            // 手机端：上方紧凑可滚动配置区 + 底部固定“开始挑战”操作栏（免滚动一触即达）
            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BrandHeader()
                    OverviewDashboardCard(
                        statsMap = statsMap,
                        totalAnswered = totalAnswered,
                        overallAccuracy = overallAccuracy,
                        mistakeCount = mistakeCount,
                        onOpenAnalysis = onOpenAnalysis,
                        onStartMistakeReview = onStartMistakeReview
                    )
                    ConfigCardsGroup(
                        config = config,
                        onSelectQuestionMode = onSelectQuestionMode,
                        onSelectTimeLimit = onSelectTimeLimit,
                        onSelectInputMode = onSelectInputMode
                    )
                    if (sessions.isNotEmpty()) {
                        LatestSessionBanner(sessions.first())
                    }
                }

                // 底部固定主操作栏
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 4.dp,
                    shadowElevation = 6.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        StartChallengeButton(
                            config = config,
                            onClick = onStartPractice
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BrandHeader() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "口算特训",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "一年级 10 以内极速条件反射训练",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MistakeQuickCards(
    mistakes: List<QuestionStat>,
    onStartMistakeReview: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFF7ED)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFEDD5))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "🎯", fontSize = 15.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "重点错题快速攻克",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF9A3412)
                    )
                }
                if (mistakes.isNotEmpty()) {
                    Text(
                        text = "消灭全部 →",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFEA580C),
                        modifier = Modifier.clickable { onStartMistakeReview() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (mistakes.isEmpty()) {
                Text(
                    text = "太棒了！当前没有待消灭的错题，继续保持极速答题！",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF9A3412).copy(alpha = 0.8f)
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    mistakes.take(3).forEach { stat ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White)
                                .border(1.dp, Color(0xFFFDBA74), RoundedCornerShape(10.dp))
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${stat.key} = ?",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFC2410C)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "需连对${stat.mistakeStreakToClear}次",
                                    fontSize = 10.sp,
                                    color = Color(0xFF9A3412).copy(alpha = 0.75f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentSessionHistoryCard(
    sessions: List<SessionRecord>
) {
    if (sessions.isEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFF0FDF4)
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDCFCE7))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "💡", fontSize = 15.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "一年级心算秘籍 · 凑十口诀",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF166534)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "一九一九好朋友，二八二八手拉手。\n三七四六大团圆，五五凑成一双手。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF15803D),
                    fontWeight = FontWeight.Medium,
                    lineHeight = 22.sp
                )
            }
        }
    } else {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "📈", fontSize = 15.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "近期挑战战绩",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "已完赛 ${sessions.size} 场",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                sessions.take(3).forEach { s ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${s.modeLabel} · ${s.timeLimitLabel}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${s.correctCount}/${s.totalCount} 题",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${s.avgResponseMs}ms",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewDashboardCard(
    statsMap: Map<String, QuestionStat>,
    totalAnswered: Int,
    overallAccuracy: Int,
    mistakeCount: Int,
    onOpenAnalysis: () -> Unit,
    onStartMistakeReview: () -> Unit
) {
    val totalCount = 132
    val masteredCount = statsMap.values.count { it.totalAttempts > 0 && !it.isInMistakeBook && it.correctCount > 0 }
    val pendingCount = (totalCount - masteredCount - mistakeCount).coerceAtLeast(0)
    val percent = ((masteredCount.toFloat() / totalCount) * 100).toInt()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // 上半部：132题全库掌握度微型进度区
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "📊", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "132题全库掌握度",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Text(
                    text = "已掌握 $masteredCount/132 题 ($percent%)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 三色掌握度分段进度条
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(9999.dp))
                    .background(Color(0xFFE2E8F0))
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    if (masteredCount > 0) {
                        Box(
                            modifier = Modifier
                                .weight((masteredCount.toFloat() / totalCount).coerceAtLeast(0.01f))
                                .fillMaxHeight()
                                .background(Color(0xFF10B981))
                        )
                    }
                    if (mistakeCount > 0) {
                        Box(
                            modifier = Modifier
                                .weight((mistakeCount.toFloat() / totalCount).coerceAtLeast(0.01f))
                                .fillMaxHeight()
                                .background(Color(0xFFF97316))
                        )
                    }
                    if (pendingCount > 0) {
                        Box(
                            modifier = Modifier
                                .weight((pendingCount.toFloat() / totalCount).coerceAtLeast(0.01f))
                                .fillMaxHeight()
                                .background(Color(0xFFE2E8F0))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 掌握度细分标签
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🟢 已掌握 $masteredCount",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF047857)
                )
                Text(
                    text = "🟡 磨合中 $pendingCount",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Text(
                    text = "🔴 待攻克 $mistakeCount",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (mistakeCount > 0) Color(0xFFB45309) else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f))
            )
            Spacer(modifier = Modifier.height(10.dp))

            // 下半部：三项作答战绩
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatSummaryItem(
                    modifier = Modifier.weight(1f),
                    label = "累计作答",
                    value = "${totalAnswered}题"
                )
                StatSummaryItem(
                    modifier = Modifier.weight(1f),
                    label = "综合正确率",
                    value = "$overallAccuracy%"
                )
                StatSummaryItem(
                    modifier = Modifier.weight(1f),
                    label = "错题待消灭",
                    value = "${mistakeCount}道",
                    highlight = mistakeCount > 0
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenAnalysis,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "错题本 & 薄弱分析",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
                if (mistakeCount > 0) {
                    Button(
                        onClick = onStartMistakeReview,
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE65100)
                        )
                    ) {
                        Text(
                            text = "消灭错题 ($mistakeCount)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigCardsGroup(
    config: PracticeConfig,
    onSelectQuestionMode: (QuestionMode) -> Unit,
    onSelectTimeLimit: (TimeLimitOption) -> Unit,
    onSelectInputMode: (InputMode) -> Unit
) {
    // 1. 题型范围选择
    SectionCard(title = "1. 题型范围") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuestionMode.entries.forEach { mode ->
                val selected = config.questionMode == mode
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
                        .clip(RoundedCornerShape(10.dp))
                        .background(bgColor)
                        .clickable { onSelectQuestionMode(mode) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mode.label,
                        fontSize = 13.sp,
                        maxLines = 1,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                }
            }
        }
    }

    // 2. 单题限时三档可调
    SectionCard(title = "2. 单题作答限时（超时判错）") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TimeLimitOption.entries.forEach { option ->
                val selected = config.timeLimit == option
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
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(bgColor)
                        .clickable { onSelectTimeLimit(option) }
                        .padding(vertical = 10.dp, horizontal = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = option.label,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = textColor,
                        maxLines = 1
                    )
                    Text(
                        text = option.tag,
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.85f),
                        maxLines = 1
                    )
                }
            }
        }
    }

    // 3. 作答交互方式（等高双卡片对齐）
    SectionCard(title = "3. 作答方式（训练中也可随时切换）") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            InputMode.entries.forEach { mode ->
                val selected = config.inputMode == mode
                val bgColor = if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                }
                val borderColor = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.Transparent
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .background(bgColor)
                        .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
                        .clickable { onSelectInputMode(mode) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (selected) "✓ ${mode.label}" else mode.label,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = mode.desc,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun StartChallengeButton(
    config: PracticeConfig,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(
            text = "开始挑战（${config.totalQuestions} 题 · ${config.timeLimit.label}限时）",
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
private fun LatestSessionBanner(latest: SessionRecord) {
    Text(
        text = "最近一轮：${latest.modeLabel}（${latest.timeLimitLabel}）· 正确 ${latest.correctCount}/${latest.totalCount} · 均速 ${latest.avgResponseMs}ms",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun StatSummaryItem(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    highlight: Boolean = false
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 21.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            color = if (highlight) {
                Color(0xFFD84315)
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            }
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}
