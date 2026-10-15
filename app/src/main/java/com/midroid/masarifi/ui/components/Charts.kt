package com.midroid.masarifi.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midroid.masarifi.ui.theme.AppTheme

val ChartPalette = listOf(
    Color(0xFFF25C54), Color(0xFFF5A623), Color(0xFFF8D347), Color(0xFF7ED321), Color(0xFF3DBE7A),
    Color(0xFF4A90E2), Color(0xFF7B61FF), Color(0xFFBD10E0), Color(0xFFFF7AA2), Color(0xFF50E3C2),
    Color(0xFF9B9B9B), Color(0xFFB8860B),
)

@Composable
fun DonutChart(values: List<Long>, colors: List<Color>, modifier: Modifier = Modifier, size: Dp = 200.dp, center: @Composable () -> Unit = {}) {
    val total = values.sum().toFloat()
    val track = AppTheme.colors.chip
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = this.size.minDimension * 0.18f
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            if (total <= 0f) {
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                return@Canvas
            }
            var start = -90f
            values.forEachIndexed { i, v ->
                val sweep = v / total * 360f
                drawArc(colors[i % colors.size], start, sweep, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                start += sweep
            }
        }
        center()
    }
}

/** Grouped bars: for each label, (income, expense). */
@Composable
fun TrendBars(items: List<Triple<String, Long, Long>>, modifier: Modifier = Modifier, highlightLast: Boolean = true) {
    val max = (items.maxOfOrNull { maxOf(it.second, it.third) } ?: 0L).coerceAtLeast(1L).toFloat()
    val c = AppTheme.colors
    Row(modifier.fillMaxWidth().height(170.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
        items.forEachIndexed { idx, (label, inc, exp) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f).fillMaxHeight()) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                    Bar(inc / max, c.income.copy(alpha = if (highlightLast && idx != items.lastIndex) 0.55f else 1f))
                    Spacer(Modifier.width(3.dp))
                    Bar(exp / max, c.expense.copy(alpha = if (highlightLast && idx != items.lastIndex) 0.55f else 1f))
                }
                Text(label, fontSize = 11.sp, color = c.muted, textAlign = TextAlign.Center, maxLines = 1)
            }
        }
    }
}

@Composable
private fun Bar(fraction: Float, color: Color) {
    Canvas(Modifier.width(12.dp).fillMaxHeight(fraction.coerceIn(0.01f, 1f))) {
        drawRoundRect(color, cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx()))
    }
}

@Composable
fun LegendDot(color: Color) {
    Canvas(Modifier.size(10.dp)) { drawCircle(color) }
}

@Composable
fun ChartLegend(items: List<Pair<String, Color>>) {
    Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        items.forEach { (label, color) ->
            LegendDot(color); Spacer(Modifier.width(4.dp))
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(14.dp))
        }
    }
}
