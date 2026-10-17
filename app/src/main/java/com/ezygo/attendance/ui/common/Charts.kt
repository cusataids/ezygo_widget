package com.ezygo.attendance.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ezygo.attendance.ui.theme.LocalStatusColors
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.Fill

/**
 * Present / Duty leave / Absent as a single proportional bar.
 *
 * Deliberately not a charting-library chart: at three values a stacked strip with
 * a labelled legend reads faster than any plotted figure would, and it costs no
 * measuring pass.
 */
@Composable
fun AttendanceMixChart(
    present: Int,
    dutyLeave: Int,
    absent: Int,
    modifier: Modifier = Modifier,
) {
    val total = present + dutyLeave + absent
    if (total == 0) return
    val colors = LocalStatusColors.current

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp)),
        ) {
            Segment(present, total, colors.safe)
            Segment(dutyLeave, total, colors.watch)
            Segment(absent, total, colors.risk)
        }
        Gap(8)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (present > 0) LegendDot("Present $present", colors.safe)
            if (dutyLeave > 0) LegendDot("Duty leave $dutyLeave", colors.watch)
            if (absent > 0) LegendDot("Absent $absent", colors.risk)
        }
    }
}

@Composable
private fun RowScope.Segment(
    value: Int,
    total: Int,
    color: Color,
) {
    if (value <= 0) return
    Box(
        Modifier
            .weight(value.toFloat() / total.toFloat())
            .fillMaxHeight()
            .background(color),
    )
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 5.dp),
        )
    }
}

/**
 * Running attendance percentage across the semester, oldest class first, with an
 * optional dotted continuation showing where the chosen what-if scenario leads.
 *
 * This is the one place a real chart earns its keep: the trend shows whether the
 * student is climbing out of a shortage or sliding into one, which no single
 * number conveys.
 *
 * [actual] must be ordered oldest to newest. Nothing else is drawn on it: no
 * projection, no target line, no annotation. What happened is the whole subject of
 * this chart, and anything derived alongside it invites the reading that the derived
 * part happened too.
 */
@Composable
fun AttendanceTrendChart(
    actual: List<Double>,
    lineColor: Color,
    modifier: Modifier = Modifier,
) {
    // A trend needs a few points before it means anything.
    if (actual.size < MIN_TREND_POINTS) return

    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(actual) {
        modelProducer.runTransaction {
            lineModel { series(x = actual.indices.toList(), y = actual) }
        }
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                LineCartesianLayer.LineProvider.series(
                    LineCartesianLayer.Line(
                        fill = LineCartesianLayer.LineFill.single(Fill(lineColor)),
                    ),
                ),
            ),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(),
        ),
        modelProducer = modelProducer,
        modifier = modifier
            .fillMaxWidth()
            .height(170.dp),
    )
}

private const val MIN_TREND_POINTS = 3
