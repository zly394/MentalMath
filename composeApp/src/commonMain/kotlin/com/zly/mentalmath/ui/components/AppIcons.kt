package com.zly.mentalmath.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 🎨 全局统一原生矢量图标系统（Zero Font Dependency Icon System）
 * 彻底消灭 Emoji 字体未加载前的“豆腐块/方框问号”与跳动重排，0 网络依赖，首帧即刻极速高保真呈现
 */
object AppIcons {

    /**
     * 🟢/🟡/🔴/⚪ 掌握度与状态语义圆点
     */
    @Composable
    fun StatusDot(
        color: Color,
        modifier: Modifier = Modifier,
        size: Dp = 8.dp
    ) {
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(color)
        )
    }

    /**
     * 🎤 语音 / 麦克风图标
     */
    @Composable
    fun Mic(
        modifier: Modifier = Modifier.size(16.dp),
        tint: Color = LocalContentColor.current
    ) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            // 麦克风头（胶囊）
            drawRoundRect(
                color = tint,
                topLeft = Offset(w * 0.32f, h * 0.12f),
                size = Size(w * 0.36f, h * 0.50f),
                cornerRadius = CornerRadius(w * 0.18f, w * 0.18f)
            )
            // 外围托架弧线
            val cradlePath = Path().apply {
                arcTo(
                    rect = androidx.compose.ui.geometry.Rect(
                        w * 0.20f, h * 0.28f, w * 0.80f, h * 0.76f
                    ),
                    startAngleDegrees = 0f,
                    sweepAngleDegrees = 180f,
                    forceMoveTo = false
                )
            }
            drawPath(
                path = cradlePath,
                color = tint,
                style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
            )
            // 支架底座
            drawLine(
                color = tint,
                start = Offset(w * 0.5f, h * 0.76f),
                end = Offset(w * 0.5f, h * 0.90f),
                strokeWidth = 1.8.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = tint,
                start = Offset(w * 0.34f, h * 0.90f),
                end = Offset(w * 0.66f, h * 0.90f),
                strokeWidth = 1.8.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }

    /**
     * ⌨️ 键盘图标
     */
    @Composable
    fun Keyboard(
        modifier: Modifier = Modifier.size(16.dp),
        tint: Color = LocalContentColor.current
    ) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            // 键盘外框
            drawRoundRect(
                color = tint,
                topLeft = Offset(w * 0.10f, h * 0.22f),
                size = Size(w * 0.80f, h * 0.56f),
                cornerRadius = CornerRadius(w * 0.10f, w * 0.10f),
                style = Stroke(width = 1.8.dp.toPx())
            )
            // 键位小点
            val keySize = 1.8.dp.toPx()
            val row1Y = h * 0.40f
            val row2Y = h * 0.55f
            for (i in 0..3) {
                val x = w * (0.26f + i * 0.16f)
                drawCircle(color = tint, radius = keySize, center = Offset(x, row1Y))
            }
            // 空格键
            drawLine(
                color = tint,
                start = Offset(w * 0.32f, row2Y),
                end = Offset(w * 0.68f, row2Y),
                strokeWidth = 1.8.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }

    /**
     * ⚡ 极速 / 闪电图标
     */
    @Composable
    fun Bolt(
        modifier: Modifier = Modifier.size(16.dp),
        tint: Color = Color(0xFFF59E0B)
    ) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val boltPath = Path().apply {
                moveTo(w * 0.56f, h * 0.08f)
                lineTo(w * 0.24f, h * 0.54f)
                lineTo(w * 0.50f, h * 0.54f)
                lineTo(w * 0.44f, h * 0.92f)
                lineTo(w * 0.78f, h * 0.44f)
                lineTo(w * 0.52f, h * 0.44f)
                close()
            }
            drawPath(path = boltPath, color = tint, style = Fill)
        }
    }

    /**
     * 🔥 连对火焰图标
     */
    @Composable
    fun Fire(
        modifier: Modifier = Modifier.size(16.dp),
        tint: Color = Color(0xFFEA580C)
    ) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val firePath = Path().apply {
                moveTo(w * 0.50f, h * 0.10f)
                cubicTo(w * 0.62f, h * 0.28f, w * 0.82f, h * 0.46f, w * 0.80f, h * 0.68f)
                cubicTo(w * 0.78f, h * 0.86f, w * 0.64f, h * 0.94f, w * 0.50f, h * 0.94f)
                cubicTo(w * 0.36f, h * 0.94f, w * 0.22f, h * 0.86f, w * 0.20f, h * 0.68f)
                cubicTo(w * 0.18f, h * 0.50f, w * 0.32f, h * 0.36f, w * 0.42f, h * 0.38f)
                cubicTo(w * 0.38f, h * 0.48f, w * 0.44f, h * 0.58f, w * 0.50f, h * 0.54f)
                cubicTo(w * 0.58f, h * 0.48f, w * 0.54f, h * 0.28f, w * 0.50f, h * 0.10f)
                close()
            }
            drawPath(path = firePath, color = tint, style = Fill)
        }
    }

    /**
     * 👑 连对皇冠图标
     */
    @Composable
    fun Crown(
        modifier: Modifier = Modifier.size(16.dp),
        tint: Color = Color(0xFFD97706)
    ) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val crownPath = Path().apply {
                moveTo(w * 0.12f, h * 0.78f)
                lineTo(w * 0.88f, h * 0.78f)
                lineTo(w * 0.82f, h * 0.32f)
                lineTo(w * 0.64f, h * 0.54f)
                lineTo(w * 0.50f, h * 0.22f)
                lineTo(w * 0.36f, h * 0.54f)
                lineTo(w * 0.18f, h * 0.32f)
                close()
            }
            drawPath(path = crownPath, color = tint, style = Fill)
            // 底座装饰线
            drawLine(
                color = tint,
                start = Offset(w * 0.12f, h * 0.86f),
                end = Offset(w * 0.88f, h * 0.86f),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }

    /**
     * 🏆 奖杯 / 掌握精通图标
     */
    @Composable
    fun Trophy(
        modifier: Modifier = Modifier.size(16.dp),
        tint: Color = Color(0xFFD97706)
    ) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            // 奖杯杯身
            val cupPath = Path().apply {
                moveTo(w * 0.26f, h * 0.18f)
                lineTo(w * 0.74f, h * 0.18f)
                lineTo(w * 0.68f, h * 0.52f)
                cubicTo(w * 0.64f, h * 0.64f, w * 0.56f, h * 0.68f, w * 0.50f, h * 0.68f)
                cubicTo(w * 0.44f, h * 0.68f, w * 0.36f, h * 0.64f, w * 0.32f, h * 0.52f)
                close()
            }
            drawPath(path = cupPath, color = tint, style = Fill)
            // 杯座支柱
            drawLine(
                color = tint,
                start = Offset(w * 0.50f, h * 0.68f),
                end = Offset(w * 0.50f, h * 0.84f),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round
            )
            // 底座
            drawLine(
                color = tint,
                start = Offset(w * 0.32f, h * 0.86f),
                end = Offset(w * 0.68f, h * 0.86f),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }

    /**
     * 📕 错题专练 / 笔记本图标
     */
    @Composable
    fun MistakeBook(
        modifier: Modifier = Modifier.size(16.dp),
        tint: Color = Color(0xFFDC2626)
    ) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            // 书本封面
            drawRoundRect(
                color = tint,
                topLeft = Offset(w * 0.18f, h * 0.14f),
                size = Size(w * 0.64f, h * 0.72f),
                cornerRadius = CornerRadius(w * 0.08f, w * 0.08f),
                style = Fill
            )
            // 书脊白线
            drawLine(
                color = Color.White.copy(alpha = 0.8f),
                start = Offset(w * 0.32f, h * 0.14f),
                end = Offset(w * 0.32f, h * 0.86f),
                strokeWidth = 1.6.dp.toPx()
            )
            // 封面标记
            drawCircle(
                color = Color.White.copy(alpha = 0.9f),
                radius = 2.dp.toPx(),
                center = Offset(w * 0.54f, h * 0.44f)
            )
        }
    }

    /**
     * 📊 全库热力 / 统计图表柱状图图标
     */
    @Composable
    fun Chart(
        modifier: Modifier = Modifier.size(16.dp),
        tint: Color = Color(0xFF2563EB)
    ) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val barW = w * 0.18f
            // 柱 1
            drawRoundRect(
                color = tint.copy(alpha = 0.7f),
                topLeft = Offset(w * 0.14f, h * 0.52f),
                size = Size(barW, h * 0.36f),
                cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
            )
            // 柱 2（最高）
            drawRoundRect(
                color = tint,
                topLeft = Offset(w * 0.41f, h * 0.22f),
                size = Size(barW, h * 0.66f),
                cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
            )
            // 柱 3
            drawRoundRect(
                color = tint.copy(alpha = 0.85f),
                topLeft = Offset(w * 0.68f, h * 0.38f),
                size = Size(barW, h * 0.50f),
                cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
            )
        }
    }

    /**
     * 🩺 薄弱诊断 / 听诊器雷达图标
     */
    @Composable
    fun Diagnosis(
        modifier: Modifier = Modifier.size(16.dp),
        tint: Color = Color(0xFF0D9488)
    ) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            // 听诊探头圆环
            drawCircle(
                color = tint,
                radius = w * 0.18f,
                center = Offset(w * 0.62f, h * 0.62f),
                style = Stroke(width = 2.dp.toPx())
            )
            drawCircle(
                color = tint,
                radius = w * 0.07f,
                center = Offset(w * 0.62f, h * 0.62f),
                style = Fill
            )
            // 导管
            val tubePath = Path().apply {
                moveTo(w * 0.28f, h * 0.22f)
                lineTo(w * 0.28f, h * 0.48f)
                cubicTo(w * 0.28f, h * 0.72f, w * 0.48f, h * 0.72f, w * 0.48f, h * 0.62f)
            }
            drawPath(
                path = tubePath,
                color = tint,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }

    /**
     * 🎯 练习特训靶心图标
     */
    @Composable
    fun Target(
        modifier: Modifier = Modifier.size(16.dp),
        tint: Color = Color(0xFF2563EB)
    ) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            drawCircle(
                color = tint,
                radius = w * 0.42f,
                center = Offset(w * 0.5f, h * 0.5f),
                style = Stroke(width = 1.8.dp.toPx())
            )
            drawCircle(
                color = tint,
                radius = w * 0.24f,
                center = Offset(w * 0.5f, h * 0.5f),
                style = Stroke(width = 1.6.dp.toPx())
            )
            drawCircle(
                color = tint,
                radius = w * 0.10f,
                center = Offset(w * 0.5f, h * 0.5f),
                style = Fill
            )
        }
    }

    /**
     * 💡 灵感提示小灯泡
     */
    @Composable
    fun Lightbulb(
        modifier: Modifier = Modifier.size(16.dp),
        tint: Color = Color(0xFFEAB308)
    ) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            // 灯泡圆头
            drawCircle(
                color = tint,
                radius = w * 0.28f,
                center = Offset(w * 0.50f, h * 0.38f)
            )
            // 底部灯座
            drawRoundRect(
                color = tint,
                topLeft = Offset(w * 0.38f, h * 0.62f),
                size = Size(w * 0.24f, h * 0.18f),
                cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
            )
        }
    }

    /**
     * 🔊 语音播报音量扬声器
     */
    @Composable
    fun Speaker(
        modifier: Modifier = Modifier.size(16.dp),
        tint: Color = LocalContentColor.current
    ) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            // 喇叭身
            val hornPath = Path().apply {
                moveTo(w * 0.16f, h * 0.36f)
                lineTo(w * 0.34f, h * 0.36f)
                lineTo(w * 0.56f, h * 0.18f)
                lineTo(w * 0.56f, h * 0.82f)
                lineTo(w * 0.34f, h * 0.64f)
                lineTo(w * 0.16f, h * 0.64f)
                close()
            }
            drawPath(path = hornPath, color = tint, style = Fill)
            // 声波弧
            val wavePath = Path().apply {
                arcTo(
                    rect = androidx.compose.ui.geometry.Rect(
                        w * 0.44f, h * 0.30f, w * 0.84f, h * 0.70f
                    ),
                    startAngleDegrees = -60f,
                    sweepAngleDegrees = 120f,
                    forceMoveTo = false
                )
            }
            drawPath(
                path = wavePath,
                color = tint,
                style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }

    /**
     * 🌱 护眼嫩芽图标
     */
    @Composable
    fun Sprout(
        modifier: Modifier = Modifier.size(16.dp),
        color: Color = Color(0xFF16A34A)
    ) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height

            // 茎
            val stemPath = Path().apply {
                moveTo(w * 0.46f, h * 0.92f)
                quadraticTo(w * 0.52f, h * 0.62f, w * 0.50f, h * 0.38f)
            }
            drawPath(
                path = stemPath,
                color = color,
                style = Stroke(
                    width = 1.8.dp.toPx(),
                    cap = StrokeCap.Round
                )
            )

            // 左叶
            val leftLeaf = Path().apply {
                moveTo(w * 0.48f, h * 0.58f)
                cubicTo(w * 0.28f, h * 0.56f, w * 0.10f, h * 0.40f, w * 0.14f, h * 0.24f)
                cubicTo(w * 0.34f, h * 0.22f, w * 0.50f, h * 0.38f, w * 0.48f, h * 0.58f)
                close()
            }
            drawPath(path = leftLeaf, color = color)

            // 右叶
            val rightLeaf = Path().apply {
                moveTo(w * 0.50f, h * 0.44f)
                cubicTo(w * 0.66f, h * 0.38f, w * 0.90f, h * 0.22f, w * 0.88f, h * 0.08f)
                cubicTo(w * 0.68f, h * 0.12f, w * 0.48f, h * 0.28f, w * 0.50f, h * 0.44f)
                close()
            }
            drawPath(path = rightLeaf, color = color.copy(alpha = 0.9f))
        }
    }
}

/**
 * 🏷 统一图文水平混排标准组件：像素级严格对齐，禁止文字与 Emoji 字符串混排
 */
@Composable
fun LabelWithIcon(
    text: String,
    modifier: Modifier = Modifier,
    spacing: Dp = 6.dp,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    icon: @Composable () -> Unit
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(modifier = Modifier.width(spacing))
        Text(
            text = text,
            style = textStyle,
            color = textColor,
            maxLines = 1
        )
    }
}
