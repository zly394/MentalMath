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
            // Pad / 平板横竖屏双栏自适应布局
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // 左栏：品牌标题 + 数据概览仪表盘 + 最近记录
                Column(
                    modifier = Modifier
                        .weight(0.44f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    BrandHeader()
                    OverviewDashboardCard(
                        totalAnswered = totalAnswered,
                        overallAccuracy = overallAccuracy,
                        mistakeCount = mistakeCount,
                        onOpenAnalysis = onOpenAnalysis,
                        onStartMistakeReview = onStartMistakeReview
                    )
                    if (sessions.isNotEmpty()) {
                        LatestSessionBanner(sessions.first())
                    }
                }

                // 右栏：三大训练参数配置 + 开始挑战大按钮
                Column(
                    modifier = Modifier
                        .weight(0.56f)
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
                    Spacer(modifier = Modifier.height(4.dp))
                    StartChallengeButton(
                        config = config,
                        onClick = onStartPractice
                    )
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
private fun OverviewDashboardCard(
    totalAnswered: Int,
    overallAccuracy: Int,
    mistakeCount: Int,
    onOpenAnalysis: () -> Unit,
    onStartMistakeReview: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(vertical = 14.dp, horizontal = 14.dp)) {
            // 严格三等分居中对齐
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
