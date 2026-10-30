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
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.remember
import com.zly.mentalmath.domain.WeaknessAnalyzer
import com.zly.mentalmath.model.MasteryLevel
import com.zly.mentalmath.model.InputMode
import com.zly.mentalmath.model.PracticeConfig
import com.zly.mentalmath.model.QuestionMode
import com.zly.mentalmath.model.QuestionStat
import com.zly.mentalmath.model.SessionRecord
import com.zly.mentalmath.model.TimeLimitOption
import com.zly.mentalmath.ui.components.AppIcons

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
    val mistakes = remember(statsMap) { statsMap.values.filter { it.isInMistakeBook } }
    val mistakeCount = mistakes.size
    val totalAnswered = statsMap.values.sumOf { it.totalAttempts }
    val totalCorrect = statsMap.values.sumOf { it.correctCount }
    val totalCorrectTimeMs = statsMap.values.sumOf { it.totalCorrectTimeMs }
    val avgSpeedMs = if (totalCorrect > 0) totalCorrectTimeMs / totalCorrect else 0L
    val overallAccuracy = if (totalAnswered == 0) {
        0
    } else {
        ((totalCorrect.toFloat() / totalAnswered) * 100).toInt()
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isPad = maxWidth >= 600.dp
        val isLandscapePad = maxWidth >= 840.dp && maxWidth > maxHeight

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 上方内容区（靠上对齐，小视口可滚动）
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            when {
                                isLandscapePad -> Modifier.widthIn(max = 1140.dp)
                                isPad -> Modifier.widthIn(max = 680.dp)
                                else -> Modifier
                            }
                        )
                        .padding(
                            horizontal = if (isPad) 28.dp else 16.dp,
                            vertical = if (isPad) 20.dp else 12.dp
                        ),
                    verticalArrangement = Arrangement.spacedBy(
                        when {
                            isLandscapePad -> 20.dp
                            isPad -> 16.dp
                            else -> 12.dp
                        }
                    )
                ) {
                    BrandHeader()

                    OverviewDashboardCard(
                        statsMap = statsMap,
                        mistakes = mistakes,
                        totalAnswered = totalAnswered,
                        overallAccuracy = overallAccuracy,
                        avgSpeedMs = avgSpeedMs,
                        onOpenAnalysis = onOpenAnalysis,
                        onStartMistakeReview = onStartMistakeReview,
                        isPad = isPad,
                        isLandscape = isLandscapePad
                    )

                    if (isLandscapePad) {
                        LandscapeConfigRow(
                            config = config,
                            onSelectQuestionMode = onSelectQuestionMode,
                            onSelectTimeLimit = onSelectTimeLimit,
                            onSelectInputMode = onSelectInputMode
                        )
                    } else {
                        ConfigCardsGroup(
                            config = config,
                            onSelectQuestionMode = onSelectQuestionMode,
                            onSelectTimeLimit = onSelectTimeLimit,
                            onSelectInputMode = onSelectInputMode,
                            isPad = isPad
                        )
                    }
                }
            }

            // 底部主挑战操作区（平板居中靠下，手机固定底栏）
            if (isPad) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = if (isLandscapePad) 420.dp else 624.dp)
                            .fillMaxWidth()
                    ) {
                        StartChallengeButton(
                            config = config,
                            onClick = onStartPractice,
                            isPad = true
                        )
                    }
                }
            } else {
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
                            onClick = onStartPractice,
                            isPad = false
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
    statsMap: Map<String, QuestionStat>,
    mistakes: List<QuestionStat>,
    totalAnswered: Int,
    overallAccuracy: Int,
    avgSpeedMs: Long,
    onOpenAnalysis: () -> Unit,
    onStartMistakeReview: () -> Unit,
    isPad: Boolean = false,
    isLandscape: Boolean = false,
    modifier: Modifier = Modifier
) {
    val totalCount = 132
    val mistakeCount = mistakes.size
    val masteredCount = remember(statsMap) {
        statsMap.values.count {
            val level = WeaknessAnalyzer.evaluateMastery(it)
            level == MasteryLevel.MASTERED || level == MasteryLevel.FAMILIAR
        }
    }
    val pendingCount = (totalCount - masteredCount - mistakeCount).coerceAtLeast(0)
    val percent = ((masteredCount.toFloat() / totalCount) * 100).toInt()
    val avgSpeedValue = if (avgSpeedMs == 0L) {
        "--"
    } else {
        val tenths = (avgSpeedMs + 50L) / 100L
        "${tenths / 10}.${tenths % 10}秒"
    }
    val cardPadding = if (isPad) 20.dp else 14.dp

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(cardPadding)) {
            if (isLandscape) {
                // 横屏双栏总览：左侧 132 题掌握度进度条，右侧核心战绩黄金三角
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1.1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        MasteryProgressSection(
                            masteredCount = masteredCount,
                            pendingCount = pendingCount,
                            mistakeCount = mistakeCount,
                            totalCount = totalCount,
                            percent = percent
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f))
                    )

                    Row(
                        modifier = Modifier.weight(1f),
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
                            label = "平均耗时",
                            value = avgSpeedValue
                        )
                    }
                }
            } else {
                // 竖屏 / 手机端：上下紧凑结构
                MasteryProgressSection(
                    masteredCount = masteredCount,
                    pendingCount = pendingCount,
                    mistakeCount = mistakeCount,
                    totalCount = totalCount,
                    percent = percent
                )

                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f))
                )
                Spacer(modifier = Modifier.height(10.dp))

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
                        label = "平均耗时",
                        value = avgSpeedValue
                    )
                }
            }

            // 按需内嵌的重点错题胶囊条（仅当存在错题时展示，0 错题时零冗余）
            if (mistakes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                InlineMistakeStrip(
                    mistakes = mistakes,
                    onStartMistakeReview = onStartMistakeReview,
                    isPad = isPad
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 底部学情分析入口
            OutlinedButton(
                onClick = onOpenAnalysis,
                modifier = Modifier
                    .fillMaxWidth()
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
        }
    }
}

@Composable
private fun MasteryProgressSection(
    masteredCount: Int,
    pendingCount: Int,
    mistakeCount: Int,
    totalCount: Int,
    percent: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AppIcons.Chart(modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
            Text(
                text = "132题全库掌握度",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Text(
            text = "已掌握 $masteredCount/132 题 ($percent%)",
            style = MaterialTheme.typography.labelMedium,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
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
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.Start)
        ) {
            AppIcons.StatusDot(color = Color(0xFF10B981), size = 7.dp)
            Text(
                text = "已掌握 $masteredCount",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                color = Color(0xFF047857)
            )
        }
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
        ) {
            AppIcons.StatusDot(color = Color(0xFFF59E0B), size = 7.dp)
            Text(
                text = "磨合中 $pendingCount",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
        }
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End)
        ) {
            AppIcons.StatusDot(color = Color(0xFFEF4444), size = 7.dp)
            Text(
                text = "待攻克 $mistakeCount",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                color = if (mistakeCount > 0) Color(0xFFB45309) else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun InlineMistakeStrip(
    mistakes: List<QuestionStat>,
    onStartMistakeReview: () -> Unit,
    isPad: Boolean
) {
    val maxDisplay = if (isPad) 4 else 3
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFFFF7ED))
            .border(1.dp, Color(0xFFFED7AA), RoundedCornerShape(12.dp))
            .clickable { onStartMistakeReview() }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AppIcons.Target(modifier = Modifier.size(14.dp), tint = Color(0xFFEA580C))
            mistakes.take(maxDisplay).forEach { stat ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White)
                        .border(1.dp, Color(0xFFFDBA74), RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${stat.key}=?",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        color = Color(0xFFC2410C)
                    )
                }
            }
            if (mistakes.size > maxDisplay) {
                Text(
                    text = "+${mistakes.size - maxDisplay}",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF9A3412)
                )
            }
        }
        Text(
            text = "消灭错题 (${mistakes.size}) →",
            style = MaterialTheme.typography.labelMedium,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            color = Color(0xFFEA580C)
        )
    }
}

@Composable
private fun ConfigCardsGroup(
    config: PracticeConfig,
    onSelectQuestionMode: (QuestionMode) -> Unit,
    onSelectTimeLimit: (TimeLimitOption) -> Unit,
    onSelectInputMode: (InputMode) -> Unit,
    isPad: Boolean = false
) {
    val modeBtnHeight = if (isPad) 48.dp else 40.dp
    val modeFontSize = if (isPad) 14.sp else 13.sp
    val itemVerticalPadding = if (isPad) 14.dp else 10.dp

    // 1. 题型范围选择
    SectionCard(title = "1. 题型范围", isPad = isPad) {
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
                        .height(modeBtnHeight)
                        .clip(RoundedCornerShape(10.dp))
                        .background(bgColor)
                        .clickable { onSelectQuestionMode(mode) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mode.label,
                        fontSize = modeFontSize,
                        maxLines = 1,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                }
            }
        }
    }

    // 2. 单题限时三档可调
    SectionCard(title = "2. 单题作答限时（超时判错）", isPad = isPad) {
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
                        .padding(vertical = itemVerticalPadding, horizontal = 6.dp),
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
    SectionCard(title = "3. 作答方式（训练中也可随时切换）", isPad = isPad) {
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
                        .padding(horizontal = 12.dp, vertical = itemVerticalPadding),
                    verticalArrangement = Arrangement.Center
                ) {
                    val titleColor = if (selected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (mode == InputMode.VOICE) {
                                AppIcons.Mic(modifier = Modifier.size(15.dp), tint = titleColor)
                            } else {
                                AppIcons.Keyboard(modifier = Modifier.size(15.dp), tint = titleColor)
                            }
                            Text(
                                text = mode.label,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                color = titleColor
                            )
                        }
                        AppIcons.RadioCheck(
                            selected = selected,
                            modifier = Modifier.size(16.dp),
                            selectedColor = MaterialTheme.colorScheme.primary
                        )
                    }
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
    onClick: () -> Unit,
    isPad: Boolean = false
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isPad) 60.dp else 54.dp),
        shape = RoundedCornerShape(if (isPad) 18.dp else 16.dp)
    ) {
        Text(
            text = "开始挑战（${config.totalQuestions} 题 · ${config.timeLimit.label}限时）",
            fontSize = if (isPad) 18.sp else 17.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1
        )
    }
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
private fun LandscapeConfigRow(
    config: PracticeConfig,
    onSelectQuestionMode: (QuestionMode) -> Unit,
    onSelectTimeLimit: (TimeLimitOption) -> Unit,
    onSelectInputMode: (InputMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 1. 题型范围（2×2 网格，高 110dp）
        SectionCard(
            title = "1. 题型范围",
            isPad = true,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuestionMode.entries.chunked(2).forEach { rowModes ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowModes.forEach { mode ->
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
                                    .height(50.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(bgColor)
                                    .clickable { onSelectQuestionMode(mode) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = mode.label,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. 单题作答限时（1×3 并排，等高 110dp）
        SectionCard(
            title = "2. 单题作答限时（超时判错）",
            isPad = true,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
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
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(12.dp))
                            .background(bgColor)
                            .clickable { onSelectTimeLimit(option) }
                            .padding(horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = option.label,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = textColor,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(4.dp))
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

        // 3. 作答方式（上下 2 行叠放，等高 110dp）
        SectionCard(
            title = "3. 作答方式（训练中也可切换）",
            isPad = true,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
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
                    val titleColor = if (selected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(bgColor)
                            .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
                            .clickable { onSelectInputMode(mode) }
                            .padding(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (mode == InputMode.VOICE) {
                                AppIcons.Mic(modifier = Modifier.size(16.dp), tint = titleColor)
                            } else {
                                AppIcons.Keyboard(modifier = Modifier.size(16.dp), tint = titleColor)
                            }
                            Text(
                                text = mode.label,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                color = titleColor
                            )
                            Text(
                                text = "· ${mode.desc}",
                                fontSize = 12.sp,
                                maxLines = 1,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        AppIcons.RadioCheck(
                            selected = selected,
                            modifier = Modifier.size(16.dp),
                            selectedColor = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    isPad: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val cardPadding = if (isPad) 18.dp else 14.dp
    val titleBottomGap = if (isPad) 10.dp else 8.dp
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
        Column(modifier = Modifier.padding(cardPadding)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(titleBottomGap))
            content()
        }
    }
}
