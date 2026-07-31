package de.aimtracer.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.aimtracer.android.analysis.MotionAnalysis
import de.aimtracer.android.analysis.SessionTrend
import de.aimtracer.android.analysis.ShotMetrics
import de.aimtracer.android.analysis.ShotStanding
import de.aimtracer.android.analysis.TracePoint
import de.aimtracer.android.model.LiveMotionSample
import de.aimtracer.android.model.ShotCapture
import java.util.Locale
import kotlin.math.abs

private val TraceCyan = Color(0xFF36D5E6)
private val TraceOrange = Color(0xFFFF9D3D)
private val TraceBlue = Color(0xFF4F93EF)
private val TraceMint = Color(0xFF32C8A6)

@Composable
fun SectionTitle(title: String, subtitle: String? = null) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        subtitle?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun TraceCard(
    shot: ShotCapture?,
    liveSamples: List<LiveMotionSample>,
    modifier: Modifier = Modifier
) {
    val points = if (shot != null) {
        MotionAnalysis.trace(shot)
    } else {
        MotionAnalysis.liveTrace(liveSamples)
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                if (shot == null) "Live-Bewegung" else "Letzter Schuss",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                if (shot == null) {
                    "Relative Winkelspur"
                } else {
                    "Blau vor, orange nach der Auslösung"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            MotionTraceCanvas(points, Modifier.fillMaxWidth().height(280.dp))
        }
    }
}

@Composable
private fun MotionTraceCanvas(
    points: List<TracePoint>,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier.background(Color(0xE6191C20), RoundedCornerShape(14.dp))
    ) {
        drawLine(
            Color.White.copy(alpha = 0.16f),
            Offset(size.width / 2, 12f),
            Offset(size.width / 2, size.height - 12f)
        )
        drawLine(
            Color.White.copy(alpha = 0.16f),
            Offset(12f, size.height / 2),
            Offset(size.width - 12f, size.height / 2)
        )
        if (points.size < 2) return@Canvas
        val maximum = maxOf(
            points.maxOf { abs(it.x) },
            points.maxOf { abs(it.y) },
            0.05
        )
        val scale = minOf(size.width, size.height) * 0.43 / maximum
        fun mapped(point: TracePoint) = Offset(
            x = (size.width / 2 + point.x * scale).toFloat(),
            y = (size.height / 2 - point.y * scale).toFloat()
        )

        val pre = Path()
        val post = Path()
        var hasPre = false
        var hasPost = false
        points.forEachIndexed { index, point ->
            val position = mapped(point)
            if (point.relativeTime <= 0) {
                if (!hasPre) {
                    pre.moveTo(position.x, position.y)
                    hasPre = true
                } else {
                    pre.lineTo(position.x, position.y)
                }
            } else {
                if (!hasPost) {
                    val previous = mapped(points[(index - 1).coerceAtLeast(0)])
                    post.moveTo(previous.x, previous.y)
                    hasPost = true
                }
                post.lineTo(position.x, position.y)
            }
        }
        val stroke = Stroke(
            width = 4f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
        drawPath(pre, TraceCyan, style = stroke)
        drawPath(post, TraceOrange, style = stroke)
        val trigger = mapped(points.minBy { abs(it.relativeTime) })
        drawCircle(Color.White, radius = 6f, center = trigger)
    }
}

@Composable
fun ShotMetricsRow(metrics: ShotMetrics) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MetricTile(
            "Ruhig halten",
            metrics.holdRms,
            Modifier.weight(1f)
        )
        MetricTile(
            "Abzug",
            metrics.triggerRms,
            Modifier.weight(1f)
        )
        MetricTile(
            "Nachhalten",
            metrics.followThroughRms,
            Modifier.weight(1f)
        )
    }
}

@Composable
fun MetricTile(
    title: String,
    value: Double,
    modifier: Modifier = Modifier,
    subtitle: String = "°/s RMS"
) {
    Card(
        modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 8.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                decimal(value, 2),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun LiveStandingCard(standing: ShotStanding, shotCount: Int) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(
                        "Einordnung in dieser Session",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Vergleich mit $shotCount Schüssen",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "#${standing.overallRank}",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                SmallRank(
                    "Ruhig halten",
                    standing.holdRank,
                    shotCount,
                    Modifier.weight(1f)
                )
                SmallRank(
                    "Abzug",
                    standing.triggerRank,
                    shotCount,
                    Modifier.weight(1f)
                )
                SmallRank(
                    "Nachhalten",
                    standing.followThroughRank,
                    shotCount,
                    Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SmallRank(
    title: String,
    rank: Int,
    count: Int,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Text(
            title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "$rank/$count",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun SessionTrendChart(
    standings: List<ShotStanding>,
    modifier: Modifier = Modifier
) {
    val maximum = standings.maxOfOrNull {
        maxOf(
            it.metrics.holdRms,
            it.metrics.triggerRms,
            it.metrics.followThroughRms
        )
    }?.coerceAtLeast(0.1) ?: 1.0

    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(190.dp)) {
            repeat(5) { index ->
                val y = size.height * index / 4f
                drawLine(
                    Color.Gray.copy(alpha = 0.25f),
                    Offset(0f, y),
                    Offset(size.width, y)
                )
            }
            if (standings.size < 2) return@Canvas
            fun drawSeries(values: List<Double>, color: Color) {
                val path = Path()
                values.forEachIndexed { index, value ->
                    val x = size.width * index / (values.size - 1)
                    val y = size.height * (1 - value / maximum).toFloat()
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path,
                    color,
                    style = Stroke(3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
            drawSeries(standings.map { it.metrics.holdRms }, TraceMint)
            drawSeries(standings.map { it.metrics.triggerRms }, TraceOrange)
            drawSeries(standings.map { it.metrics.followThroughRms }, TraceBlue)
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Legend("Halten", TraceMint)
            Legend("Abzug", TraceOrange)
            Legend("Nachhalten", TraceBlue)
        }
    }
}

@Composable
private fun Legend(label: String, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            "●",
            color = color,
            style = MaterialTheme.typography.labelSmall
        )
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun TrendSummaryRow(trend: SessionTrend) {
    Column {
        Text(
            "Letzte ${trend.segmentSize} gegen erste ${trend.segmentSize} Schüsse",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(Modifier.fillMaxWidth()) {
            TrendValue(
                "Halten",
                trend.holdImprovementPercent,
                Modifier.weight(1f)
            )
            TrendValue(
                "Abzug",
                trend.triggerImprovementPercent,
                Modifier.weight(1f)
            )
            TrendValue(
                "Nachhalten",
                trend.followThroughImprovementPercent,
                Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TrendValue(
    title: String,
    value: Double,
    modifier: Modifier = Modifier
) {
    Column(modifier.padding(top = 7.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            signedPercent(value),
            color = if (value >= 0) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
            fontWeight = FontWeight.Bold
        )
    }
}

fun decimal(value: Double, digits: Int = 1): String =
    String.format(Locale.GERMANY, "%.${digits}f", value)

fun signedPercent(value: Double): String =
    (if (value > 0) "+" else "") + decimal(value, 1) + " %"
