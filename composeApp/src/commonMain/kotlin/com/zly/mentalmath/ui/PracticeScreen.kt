package com.zly.mentalmath.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zly.mentalmath.model.AnswerOutcome
import com.zly.mentalmath.model.InputMode
import com.zly.mentalmath.model.Question

@Composable
fun PracticeScreen(
    state: PracticeUiState,
    isStarting: Boolean,
    onToggleInputMode: (InputMode) -> Unit,
    onKeypadSubmit: (Int) -> Unit,
    onRestartSession: () -> Unit,
    onResumeSession: () -> Unit,
    onOpenAnalysis: () -> Unit,
    onExit: () -> Unit
) {
    if (state.isPaused) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(state.voiceHint, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onResumeSession) { Text("点击继续") }
            OutlinedButton(onClick = onExit) { Text("返回首页") }
        }
        return
    }

    if (isStarting) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "正在进入答题…",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    if (state.phase == QuestionPhase.SESSION_SUMMARY) {
        SessionSummaryView(
            state = state,
            onRestartSession = onRestartSession,
            onOpenAnalysis = onOpenAnalysis,
            onExit = onExit
        )
        return
    }

    val question = state.currentQuestion ?: return

    val isVoice = state.config.inputMode == InputMode.VOICE

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isPad = maxWidth >= 600.dp

        if (isVoice) {
            // 🎙 纯语音护眼沉浸模式（无键盘干扰，超大字号远距可见，让耳朵替代眼睛）
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 920.dp)
                        .padding(
                            horizontal = if (isPad) 28.dp else 16.dp,
                            vertical = if (isPad) 20.dp else 12.dp
                        ),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    PracticeTopHeader(
                        state = state,
                        onExit = onExit,
                        onToggleInputMode = onToggleInputMode
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        VoiceEyesFreeBlackboardCard(
                            state = state,
                            question = question,
                            isPad = isPad
                        )
                    }

                    EyesFreeTipsFooter()
                }
            }
        } else {
            // ⌨️ 按键模式（安静备用场景：平板左右分栏，手机上下流）
            if (isPad) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 960.dp)
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        PracticeTopHeader(
                            state = state,
                            onExit = onExit,
                            onToggleInputMode = onToggleInputMode
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 左栏：大算式卡片
                            Box(
                                modifier = Modifier
                                    .weight(0.52f)
                                    .fillMaxHeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                QuestionBlackboardCard(
                                    state = state,
                                    question = question,
                                    isPad = true
                                )
                            }

                            // 右栏：按键提示 + 4 列严格对齐大数字键盘
                            Column(
                                modifier = Modifier
                                    .weight(0.48f)
                                    .fillMaxHeight(),
                                verticalArrangement = Arrangement.Center
                            ) {
                                VoiceHintBanner(state = state)
                                Spacer(modifier = Modifier.height(14.dp))
                                NumberKeypadGrid(
                                    enabled = state.phase != QuestionPhase.FEEDBACK,
                                    buttonHeight = 76.dp,
                                    onNumberClick = onKeypadSubmit
                                )
                            }
                        }
                    }
                }
            } else {
                // 手机端按键流
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    PracticeTopHeader(
                        state = state,
                        onExit = onExit,
                        onToggleInputMode = onToggleInputMode
                    )

                    QuestionBlackboardCard(
                        state = state,
                        question = question,
                        isPad = false
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        VoiceHintBanner(state = state)
                        NumberKeypadGrid(
                            enabled = state.phase != QuestionPhase.FEEDBACK,
                            buttonHeight = 62.dp,
                            onNumberClick = onKeypadSubmit
                        )
                    }
                }
            }
        }
    }
}

/**
 * 三段式对称顶部导航与进度条（保证中间题号严格居中，右侧按钮单行不折行）
 */
/**
 * 纯粹护眼极简顶栏：单行对称呈现，去除边缘横向倒计时干扰，聚焦中央算式
 */
@Composable
private fun PracticeTopHeader(
    state: PracticeUiState,
    onExit: () -> Unit,
    onToggleInputMode: (InputMode) -> Unit
) {
    val isVoice = state.config.inputMode == InputMode.VOICE

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左栏：退出
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart
        ) {
            BackButton(onClick = onExit)
        }

        // 中栏：题号严格居中
        Box(
            modifier = Modifier.weight(1.1f),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "第 ${state.currentIndex + 1} / ${state.config.totalQuestions} 题",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1
            )
        }

        // 右栏：连对徽标 + 模式切换胶囊
        Box(
            modifier = Modifier.weight(1.3f),
            contentAlignment = Alignment.CenterEnd
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.comboCount >= 2) {
                    Surface(
                        color = Color(0xFFFFF3E0),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "🔥 x${state.comboCount}",
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            color = Color(0xFFE65100),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .clickable {
                            val next = if (isVoice) InputMode.KEYPAD else InputMode.VOICE
                            onToggleInputMode(next)
                        }
                ) {
                    Text(
                        text = if (isVoice) "🎤 语音·切按键" else "⌨️ 按键·切语音",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

/**
 * 固定答案框对齐的超大算式展示卡片：如 `6 + 2 = [ ? ]` -> `6 + 2 = [ 8 ]`
 */
@Composable
private fun QuestionBlackboardCard(
    state: PracticeUiState,
    question: Question,
    isPad: Boolean
) {
    val lastAttempt = state.lastAttempt
    val isFeedback = state.phase == QuestionPhase.FEEDBACK && lastAttempt != null

    val cardColor = if (isFeedback && lastAttempt != null) {
        when (lastAttempt.outcome) {
            AnswerOutcome.CORRECT -> Color(0xFFE8F5E9)
            AnswerOutcome.WRONG -> Color(0xFFFFEBEE)
            AnswerOutcome.TIMEOUT -> Color(0xFFFFF8E1)
        }
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    }

    val answerBoxBorderColor = if (isFeedback && lastAttempt != null) {
        when (lastAttempt.outcome) {
            AnswerOutcome.CORRECT -> Color(0xFF2E7D32)
            AnswerOutcome.WRONG -> Color(0xFFC62828)
            AnswerOutcome.TIMEOUT -> Color(0xFFEF6C00)
        }
    } else {
        when {
            state.phase == QuestionPhase.SPEECH_DETECTED -> Color(0xFF1E88E5)
            state.phase == QuestionPhase.COUNTDOWN -> when {
                state.timeProgress > 0.5f -> Color(0xFF10B981) // 充裕：生机绿
                state.timeProgress > 0.25f -> Color(0xFFF97316) // 警示：暖阳橙
                else -> Color(0xFFEF4444) // 临界冲刺：醒目红
            }
            else -> MaterialTheme.colorScheme.primary
        }
    }

    val fontSize = if (isPad) 64.sp else 50.sp

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = if (isPad) 40.dp else 26.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 算式行：左侧算式 + 等于号 + 固定宽度答案框，切换状态绝不抖动
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "${question.leftExpression} = ",
                    fontSize = fontSize,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Box(
                    modifier = Modifier
                        .widthIn(min = if (isPad) 104.dp else 82.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface).background(answerBoxBorderColor.copy(alpha = 0.06f))
                        .border(2.5.dp, answerBoxBorderColor, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isFeedback) question.answer.toString() else "?",
                        fontSize = fontSize,
                        fontWeight = FontWeight.ExtraBold,
                        color = answerBoxBorderColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (isFeedback && lastAttempt != null) {
                val feedbackText = when (lastAttempt.outcome) {
                    AnswerOutcome.CORRECT -> {
                        "✓ 回答正确！反应耗时 ${lastAttempt.elapsedMs} ms"
                    }
                    AnswerOutcome.WRONG -> {
                        "✗ 刚才答了 ${lastAttempt.userAnswer}，正确答案是 ${question.answer}（已记入错题本）"
                    }
                    AnswerOutcome.TIMEOUT -> {
                        "⏰ 超过 ${state.config.timeLimit.label}未答，正确答案是 ${question.answer}（已记入错题本）"
                    }
                }
                Text(
                    text = feedbackText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = answerBoxBorderColor,
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = state.voiceHint,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun VoiceHintBanner(state: PracticeUiState) {
    val isVoice = state.config.inputMode == InputMode.VOICE
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isVoice) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
            .padding(vertical = 8.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isVoice) {
                "🎙 语音抢答中：直接喊出 0~10，也可直接点下方数字键"
            } else {
                "⌨️ 听音按键中：听完题目后快速点击下方 0~10 数字键"
            },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (isVoice) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

/**
 * 几何级严格 4 列垂直对齐的 0 ~ 10 大数字键盘
 *
 * 每一行均采用左右对称双半区（Left weight 1f + Right weight 1f，间距 10.dp）：
 * - 第 1 行：左半区 [1, 2]  |  右半区 [3, 4]
 * - 第 2 行：左半区 [5, 6]  |  右半区 [7, 8]
 * - 第 3 行：左半区 [9, 0]  |  右半区 [ 10 ]
 * 由此保证 9 与 1/5、0 与 2/6、10 的左右边界与 3/7 和 4/8 做到 100% 像素级垂直对齐！
 */
@Composable
private fun NumberKeypadGrid(
    enabled: Boolean,
    buttonHeight: Dp,
    onNumberClick: (Int) -> Unit
) {
    val gap = 10.dp
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(gap)
    ) {
        // 第 1 行：1, 2 | 3, 4
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(gap)
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(gap)
            ) {
                KeypadButton(1, enabled, buttonHeight, Modifier.weight(1f), onNumberClick)
                KeypadButton(2, enabled, buttonHeight, Modifier.weight(1f), onNumberClick)
            }
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(gap)
            ) {
                KeypadButton(3, enabled, buttonHeight, Modifier.weight(1f), onNumberClick)
                KeypadButton(4, enabled, buttonHeight, Modifier.weight(1f), onNumberClick)
            }
        }

        // 第 2 行：5, 6 | 7, 8
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(gap)
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(gap)
            ) {
                KeypadButton(5, enabled, buttonHeight, Modifier.weight(1f), onNumberClick)
                KeypadButton(6, enabled, buttonHeight, Modifier.weight(1f), onNumberClick)
            }
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(gap)
            ) {
                KeypadButton(7, enabled, buttonHeight, Modifier.weight(1f), onNumberClick)
                KeypadButton(8, enabled, buttonHeight, Modifier.weight(1f), onNumberClick)
            }
        }

        // 第 3 行：9, 0 | 10（10 精确占满右半区，与上方 3+4 完全对齐）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(gap)
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(gap)
            ) {
                KeypadButton(9, enabled, buttonHeight, Modifier.weight(1f), onNumberClick)
                KeypadButton(0, enabled, buttonHeight, Modifier.weight(1f), onNumberClick)
            }
            Box(modifier = Modifier.weight(1f)) {
                KeypadButton(
                    number = 10,
                    enabled = enabled,
                    buttonHeight = buttonHeight,
                    modifier = Modifier.fillMaxWidth(),
                    onNumberClick = onNumberClick
                )
            }
        }
    }
}

@Composable
private fun KeypadButton(
    number: Int,
    enabled: Boolean,
    buttonHeight: Dp,
    modifier: Modifier = Modifier,
    onNumberClick: (Int) -> Unit
) {
    Button(
        onClick = { onNumberClick(number) },
        enabled = enabled,
        modifier = modifier.height(buttonHeight),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (number == 10) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.primary
            }
        )
    ) {
        Text(
            text = number.toString(),
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

/**
 * 本轮训练完成后的成绩单与错题复盘视图
 */
@Composable
private fun SessionSummaryView(
    state: PracticeUiState,
    onRestartSession: () -> Unit,
    onOpenAnalysis: () -> Unit,
    onExit: () -> Unit
) {
    val attempts = state.roundAttempts
    val correctCount = attempts.count { it.outcome == AnswerOutcome.CORRECT }
    val wrongCount = attempts.count { it.outcome == AnswerOutcome.WRONG }
    val timeoutCount = attempts.count { it.outcome == AnswerOutcome.TIMEOUT }
    val correctAttempts = attempts.filter { it.outcome == AnswerOutcome.CORRECT }
    val avgMs = if (correctAttempts.isEmpty()) {
        0L
    } else {
        correctAttempts.sumOf { it.elapsedMs } / correctAttempts.size
    }
    val mistakes = attempts.filter { it.outcome != AnswerOutcome.CORRECT }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val horizontalPadding = if (maxWidth >= 600.dp) 64.dp else 20.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = horizontalPadding, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "🎉 本轮挑战完成！",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SummaryMetric(Modifier.weight(1f), "答对", "$correctCount/${attempts.size}")
                        SummaryMetric(Modifier.weight(1f), "答错", "${wrongCount}题")
                        SummaryMetric(Modifier.weight(1f), "超时", "${timeoutCount}题")
                        SummaryMetric(Modifier.weight(1f), "最高连击", "${state.maxCombo}连")
                    }
                    Text(
                        text = "平均反应速度：${avgMs} 毫秒（限时档位：${state.config.timeLimit.label}）",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            if (mistakes.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "本轮错题与超时题（已自动记入错题本）：",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE65100)
                        )
                        mistakes.forEach { item ->
                            val reason = if (item.outcome == AnswerOutcome.TIMEOUT) {
                                "超时未答"
                            } else {
                                "误答为 ${item.userAnswer}"
                            }
                            Text(
                                text = "• ${item.question.fullExpression}   （$reason）",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onExit,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("返回首页", fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = onOpenAnalysis,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("薄弱环节分析", fontWeight = FontWeight.SemiBold)
                }
            }

            Button(
                onClick = onRestartSession,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "再来一轮",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun SummaryMetric(
    modifier: Modifier = Modifier,
    label: String,
    value: String
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1
        )
    }
}

/**
 * 🎙 专为“听音回答·保护视力”打造的远距离护眼算式黑板
 */
@Composable
private fun VoiceEyesFreeBlackboardCard(
    state: PracticeUiState,
    question: Question,
    isPad: Boolean
) {
    val lastAttempt = state.lastAttempt
    val isFeedback = state.phase == QuestionPhase.FEEDBACK && lastAttempt != null

    val cardColor = if (isFeedback && lastAttempt != null) {
        when (lastAttempt.outcome) {
            AnswerOutcome.CORRECT -> Color(0xFFE8F5E9)
            AnswerOutcome.WRONG -> Color(0xFFFFEBEE)
            AnswerOutcome.TIMEOUT -> Color(0xFFFFF8E1)
        }
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    val answerBoxBorderColor = if (isFeedback && lastAttempt != null) {
        when (lastAttempt.outcome) {
            AnswerOutcome.CORRECT -> Color(0xFF2E7D32)
            AnswerOutcome.WRONG -> Color(0xFFC62828)
            AnswerOutcome.TIMEOUT -> Color(0xFFEF6C00)
        }
    } else {
        when {
            state.phase == QuestionPhase.SPEECH_DETECTED -> Color(0xFF1E88E5)
            state.phase == QuestionPhase.COUNTDOWN -> when {
                state.timeProgress > 0.5f -> Color(0xFF10B981) // 充裕：生机绿
                state.timeProgress > 0.25f -> Color(0xFFF97316) // 警示：暖阳橙
                else -> Color(0xFFEF4444) // 临界冲刺：醒目红
            }
            else -> MaterialTheme.colorScheme.primary
        }
    }

    // 远距离护眼超大字体：平板 84sp，手机 62sp
    val fontSize = if (isPad) 84.sp else 62.sp

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = if (isPad) 48.dp else 32.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 算式展示行
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "${question.leftExpression} = ",
                    fontSize = fontSize,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Box(
                    modifier = Modifier
                        .widthIn(min = if (isPad) 130.dp else 96.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surface).background(answerBoxBorderColor.copy(alpha = 0.06f))
                        .border(3.dp, answerBoxBorderColor, RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isFeedback) question.answer.toString() else "?",
                        fontSize = fontSize,
                        fontWeight = FontWeight.ExtraBold,
                        color = answerBoxBorderColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 灵动语音状态与声波提示胶囊
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = when {
                    isFeedback && lastAttempt != null -> when (lastAttempt.outcome) {
                        AnswerOutcome.CORRECT -> Color(0xFFDCFCE7)
                        AnswerOutcome.WRONG -> Color(0xFFFEE2E2)
                        AnswerOutcome.TIMEOUT -> Color(0xFFFEF3C7)
                    }
                    state.phase == QuestionPhase.SPEECH_DETECTED -> Color(0xFFDBEAFE)
                    state.phase == QuestionPhase.SPEAKING -> Color(0xFFF1F5F9)
                    else -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f)
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    val (icon, text, textColor) = when {
                        isFeedback && lastAttempt != null -> when (lastAttempt.outcome) {
                            AnswerOutcome.CORRECT -> Triple("✓", "回答正确！用时 ${lastAttempt.elapsedMs}ms", Color(0xFF166534))
                            AnswerOutcome.WRONG -> Triple("✗", "回答了 ${lastAttempt.userAnswer}，正确是 ${question.answer}", Color(0xFF991B1B))
                            AnswerOutcome.TIMEOUT -> Triple("⏰", "超时未答，正确答案是 ${question.answer}", Color(0xFF92400E))
                        }
                        state.phase == QuestionPhase.SPEAKING -> Triple("🔊", "正在语音播报题目...", Color(0xFF475569))
                        state.phase == QuestionPhase.SPEECH_DETECTED -> Triple("⚡", "已听到开口！正在识别答案...", Color(0xFF1E40AF))
                        else -> Triple("🎙", "请直接大声说出答案！", MaterialTheme.colorScheme.onSecondaryContainer)
                    }

                    VoiceWaveformBars(
                        isListening = state.phase == QuestionPhase.COUNTDOWN,
                        isDetected = state.phase == QuestionPhase.SPEECH_DETECTED,
                        color = textColor
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(text = icon, fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    VoiceWaveformBars(
                        isListening = state.phase == QuestionPhase.COUNTDOWN,
                        isDetected = state.phase == QuestionPhase.SPEECH_DETECTED,
                        color = textColor
                    )
                }
            }
        }
    }
}

/**
 * 护眼远距作答建议条
 */
@Composable
private fun EyesFreeTipsFooter() {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFF0FDF4),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDCFCE7))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "🌱", fontSize = 13.sp)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "护眼建议：可将设备立于 1 米外观看，听题即答，无需靠近和触碰屏幕",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF166534)
            )
        }
    }
}

/**
 * 🌊 灵动声波呼吸律动条：无屏护眼交互的核心“生命体征”指示器
 */
@Composable
private fun VoiceWaveformBars(
    isListening: Boolean,
    isDetected: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    if (isDetected) {
        // 开口瞬间捕获：高挺定格
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val heights = listOf(10.dp, 16.dp, 22.dp, 16.dp, 10.dp)
            heights.forEach { h ->
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(h)
                        .clip(RoundedCornerShape(2.dp))
                        .background(color)
                )
            }
        }
    } else if (isListening) {
        // 聆听阶段：5 根声波自然起伏呼吸
        val transition = rememberInfiniteTransition()
        val h1 by transition.animateFloat(
            initialValue = 6f,
            targetValue = 18f,
            animationSpec = infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse)
        )
        val h2 by transition.animateFloat(
            initialValue = 16f,
            targetValue = 7f,
            animationSpec = infiniteRepeatable(tween(520, easing = LinearEasing), RepeatMode.Reverse)
        )
        val h3 by transition.animateFloat(
            initialValue = 8f,
            targetValue = 22f,
            animationSpec = infiniteRepeatable(tween(380, easing = LinearEasing), RepeatMode.Reverse)
        )

        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.width(3.dp).height(h1.dp).clip(RoundedCornerShape(2.dp)).background(color))
            Box(Modifier.width(3.dp).height(h2.dp).clip(RoundedCornerShape(2.dp)).background(color))
            Box(Modifier.width(3.dp).height(h3.dp).clip(RoundedCornerShape(2.dp)).background(color))
            Box(Modifier.width(3.dp).height(h2.dp).clip(RoundedCornerShape(2.dp)).background(color))
            Box(Modifier.width(3.dp).height(h1.dp).clip(RoundedCornerShape(2.dp)).background(color))
        }
    } else {
        // 静止微条（播题或反馈结算）
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(5) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(color.copy(alpha = 0.45f))
                )
            }
        }
    }
}
