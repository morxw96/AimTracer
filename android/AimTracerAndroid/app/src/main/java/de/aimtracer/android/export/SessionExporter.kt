package de.aimtracer.android.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import de.aimtracer.android.L10n
import de.aimtracer.android.analysis.MetricComparison
import de.aimtracer.android.analysis.MetricStatistics
import de.aimtracer.android.analysis.MotionAnalysis
import de.aimtracer.android.analysis.AxisDisplayConfiguration
import de.aimtracer.android.analysis.SessionAnalysis
import de.aimtracer.android.analysis.ShotStanding
import de.aimtracer.android.model.TrainingSession
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object SessionExporter {
    private val locale: Locale
        get() = L10n.locale
    private val dateTime = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", locale)
    private val time = SimpleDateFormat("HH:mm:ss", locale)

    fun csv(
        session: TrainingSession,
        allSessions: List<TrainingSession>
    ): ByteArray {
        require(session.shots.isNotEmpty()) {
            L10n.text("Session enthält keine Schüsse.")
        }
        val summary = SessionAnalysis.summary(session, allSessions)
        val previousSessions = SessionAnalysis.previousComparable(
            session,
            allSessions
        )
        val comparison = SessionAnalysis.comparison(session, previousSessions)
        val rows = mutableListOf<List<String>>(
            listOf("AimTracer Trainingsprotokoll"),
            listOf("Session", session.name),
            listOf("Programm", session.program.title),
            listOf("Beginn", dateTime.format(Date(session.startedAt))),
            listOf(
                "Ende",
                session.endedAt?.let { dateTime.format(Date(it)) }.orEmpty()
            ),
            listOf("Schüsse", session.shots.size.toString()),
            listOf("Geplant", session.program.plannedShots?.toString().orEmpty()),
            listOf(
                "Meyton-Gesamtergebnis",
                session.meytonScore?.let(::score).orEmpty()
            ),
            listOf("Technikindex", number(summary.techniqueIndex, 1)),
            listOf("Technik Halten (30 %)", number(summary.holdScore, 1)),
            listOf("Technik Abzug (50 %)", number(summary.triggerScore, 1)),
            listOf(
                "Technik Nachhalten (20 %)",
                number(summary.followThroughScore, 1)
            ),
            listOf(
                "Baseline",
                "${summary.baseline.sourceSessionCount}/" +
                    "${summary.baseline.targetSessionCount} Sessions"
            ),
            emptyList(),
            listOf(
                "Kennwert",
                "Mittelwert (°/s RMS)",
                "Median",
                "Standardabweichung",
                "Bestwert",
                "Schlechtester Wert"
            ),
            statisticsRow("Ruhig halten", summary.hold),
            statisticsRow("Abzugsverhalten", summary.trigger),
            statisticsRow("Nachhalten", summary.followThrough)
        )
        if (comparison != null) {
            rows.add(emptyList())
            rows += listOf(
                "Vergleich",
                "Aktuelle Session",
                "Vorherige Sessions",
                "Veränderung"
            )
            rows += comparisonRow("Ruhig halten", comparison.hold)
            rows += comparisonRow("Abzugsverhalten", comparison.trigger)
            rows += comparisonRow("Nachhalten", comparison.followThrough)
        }
        rows.add(emptyList())
        rows += listOf(
            "Schuss",
            "Geräte-ID",
            "Zeit",
            "Gesamtrang",
            "Technikindex",
            "Ruhig halten (°/s RMS)",
            "Rang Halten",
            "Abzugsverhalten (°/s RMS)",
            "Rang Abzug",
            "Nachhalten (°/s RMS)",
            "Rang Nachhalten",
            "Audio-Peak",
            "Accel-Peak",
            "Gyro-Peak"
        )
        summary.standings.forEach { standing ->
            rows += listOf(
                standing.ordinal.toString(),
                standing.shot.deviceShotId.toString(),
                time.format(Date(standing.shot.receivedAt)),
                standing.overallRank.toString(),
                number(standing.techniqueIndex, 1),
                number(standing.metrics.holdRms),
                standing.holdRank.toString(),
                number(standing.metrics.triggerRms),
                standing.triggerRank.toString(),
                number(standing.metrics.followThroughRms),
                standing.followThroughRank.toString(),
                standing.shot.audioPeak.toString(),
                standing.shot.accelerationPeak.toString(),
                standing.shot.gyroPeak.toString()
            )
        }
        rows.add(emptyList())
        rows += listOf(
            "Hinweis",
            "Niedrigere RMS-Werte bedeuten weniger Winkelbewegung. Der " +
                "Technikindex nutzt die persönliche Baseline (Halten 30 %, " +
                "Abzug 50 %, Nachhalten 20 %) und ist keine Ringzahl."
        )

        val text = rows.joinToString("\r\n") { row ->
            row.joinToString(";") { csvField(L10n.text(it)) }
        }
        return byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            text.toByteArray(Charsets.UTF_8)
    }

    fun pdf(
        session: TrainingSession,
        allSessions: List<TrainingSession>
    ): ByteArray {
        require(session.shots.isNotEmpty()) {
            L10n.text("Session enthält keine Schüsse.")
        }
        val document = PdfDocument()
        try {
            val report = PdfReport(document, session, allSessions)
            report.draw()
            return ByteArrayOutputStream().use { output ->
                document.writeTo(output)
                output.toByteArray()
            }
        } finally {
            document.close()
        }
    }

    fun csvFileName(session: TrainingSession) =
        "${safeName(session.name)}-Excel.csv"

    fun pdfFileName(session: TrainingSession) =
        "${safeName(session.name)}-${L10n.text("Bericht")}.pdf"

    fun rawJson(
        session: TrainingSession,
        axes: AxisDisplayConfiguration = AxisDisplayConfiguration()
    ): ByteArray {
        require(session.shots.isNotEmpty()) {
            L10n.text("Session enthält keine Schüsse.")
        }
        val root = JSONObject().apply {
            put("schemaVersion", 1)
            put("format", "aimtracer-raw-session")
            put("exportedAt", isoDate(System.currentTimeMillis()))
            put("mounting", JSONObject().apply {
                put("id", MotionAnalysis.MOUNTING_PROFILE_ID)
                put(
                    "boardOrientation",
                    "PCB underside up; component and IMU side down; " +
                        "USB-C toward shooter"
                )
                put(
                    "rawSampleAxes",
                    "Unmodified LSM6DS3TR-C sensor coordinates"
                )
                put("analysisAxes", JSONObject().apply {
                    put("roll", "gx")
                    put(
                        "horizontalRight",
                        if (axes.invertXAxis) "-gz" else "+gz"
                    )
                    put(
                        "verticalUp",
                        if (axes.invertYAxis) "-gy" else "+gy"
                    )
                })
                put("displayInversion", JSONObject().apply {
                    put("x", axes.invertXAxis)
                    put("y", axes.invertYAxis)
                })
            })
            put("session", rawSession(session))
        }
        return root.toString(2).toByteArray(Charsets.UTF_8)
    }

    fun jsonFileName(session: TrainingSession) =
        "${safeName(session.name)}-${L10n.text("Rohdaten")}.json"

    private fun rawSession(session: TrainingSession) = JSONObject().apply {
        put("id", session.id)
        put("startedAt", isoDate(session.startedAt))
        put(
            "endedAt",
            session.endedAt?.let(::isoDate) ?: JSONObject.NULL
        )
        put("name", session.name)
        put("program", when (session.program) {
            de.aimtracer.android.model.TrainingProgram.LP20 -> "lp20"
            de.aimtracer.android.model.TrainingProgram.LP40 -> "lp40"
            de.aimtracer.android.model.TrainingProgram.LP60 -> "lp60"
            de.aimtracer.android.model.TrainingProgram.DRY_FIRE -> "dryFire"
            de.aimtracer.android.model.TrainingProgram.FREE_TRAINING ->
                "freeTraining"
        })
        put(
            "plannedShotCount",
            session.program.plannedShots ?: JSONObject.NULL
        )
        put("meytonScore", session.meytonScore ?: JSONObject.NULL)
        put("shots", JSONArray().apply {
            session.shots.forEach { shot ->
                put(JSONObject().apply {
                    put("id", shot.id)
                    put("deviceShotId", shot.deviceShotId)
                    put("receivedAt", isoDate(shot.receivedAt))
                    put("triggerUptimeMs", shot.triggerUptimeMs)
                    put("sampleRateHz", shot.sampleRateHz)
                    put("triggerIndex", shot.triggerIndex)
                    put("audioPeak", shot.audioPeak)
                    put("accelerationPeak", shot.accelerationPeak)
                    put("gyroPeak", shot.gyroPeak)
                    put("samples", JSONArray().apply {
                        shot.samples.forEach { sample ->
                            put(JSONObject().apply {
                                put("index", sample.index)
                                put(
                                    "relativeTimeMs",
                                    if (shot.sampleRateHz == 0) 0.0 else {
                                        (sample.index - shot.triggerIndex) *
                                            1_000.0 / shot.sampleRateHz
                                    }
                                )
                                put("gx", sample.gx.toInt())
                                put("gy", sample.gy.toInt())
                                put("gz", sample.gz.toInt())
                                put("ax", sample.ax.toInt())
                                put("ay", sample.ay.toInt())
                                put("az", sample.az.toInt())
                                put("microphonePeak", sample.microphonePeak)
                                put("isTrigger", sample.isTrigger)
                            })
                        }
                    })
                })
            }
        })
    }

    private fun statisticsRow(
        title: String,
        statistics: MetricStatistics
    ) = listOf(
        title,
        number(statistics.mean),
        number(statistics.median),
        number(statistics.standardDeviation),
        number(statistics.best),
        number(statistics.worst)
    )

    private fun comparisonRow(
        title: String,
        comparison: MetricComparison
    ) = listOf(
        title,
        number(comparison.current),
        number(comparison.reference),
        signedPercent(comparison.improvementPercent)
    )

    private fun csvField(value: String): String =
        if (
            value.any { it == ';' || it == '"' || it == '\n' || it == '\r' }
        ) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }

    internal fun number(value: Double, digits: Int = 3): String {
        val symbols = DecimalFormatSymbols(locale)
        return DecimalFormat(
            if (digits == 0) "0" else "0.${"0".repeat(digits)}",
            symbols
        ).format(value)
    }

    private fun signedPercent(value: Double): String =
        (if (value > 0) "+" else "") + number(value, 1) + " %"

    private fun score(value: Double): String =
        number(value, if (value % 1.0 == 0.0) 0 else 1)

    private fun isoDate(value: Long): String = ISO_DATE.format(Date(value))

    private fun safeName(value: String): String =
        value.replace(Regex("[^\\p{L}\\p{N}_-]+"), "-").trim('-')

    private val ISO_DATE = SimpleDateFormat(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        Locale.US
    ).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
}

private class PdfReport(
    private val document: PdfDocument,
    private val session: TrainingSession,
    private val allSessions: List<TrainingSession>
) {
    private val width = 595
    private val height = 842
    private val margin = 36f
    private val accent = Color.rgb(13, 140, 120)
    private val muted = Color.rgb(90, 99, 110)
    private val light = Color.rgb(242, 244, 244)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val summary = SessionAnalysis.summary(session, allSessions)
    private val previousSessions = SessionAnalysis.previousComparable(
        session,
        allSessions
    )
    private val comparison = SessionAnalysis.comparison(
        session,
        previousSessions
    )
    private var pageNumber = 0
    private var currentPage: PdfDocument.Page? = null

    fun draw() {
        drawOverview()
        drawTable()
    }

    private fun drawOverview() {
        val canvas = newPage()
        text(canvas, "AimTracer", margin, 55f, 25f, accent, bold = true)
        text(
            canvas,
            "TRAININGSPROTOKOLL",
            margin,
            76f,
            9f,
            muted,
            bold = true
        )
        text(canvas, session.name, margin, 116f, 19f, Color.BLACK, bold = true)
        val planned = session.program.plannedShots?.let { " / $it" }.orEmpty()
        text(
            canvas,
            "${session.program.title}  •  ${session.shots.size}$planned Schüsse  •  " +
                DATE_FORMAT.format(Date(session.startedAt)),
            margin,
            141f,
            10f,
            muted
        )

        val cardWidth = (width - margin * 2 - 20f) / 3f
        metricCard(canvas, "RUHIG HALTEN", summary.hold, margin, 172f, cardWidth)
        metricCard(
            canvas,
            "ABZUGSVERHALTEN",
            summary.trigger,
            margin + cardWidth + 10f,
            172f,
            cardWidth
        )
        metricCard(
            canvas,
            "NACHHALTEN",
            summary.followThrough,
            margin + (cardWidth + 10f) * 2,
            172f,
            cardWidth
        )
        trendChart(canvas, RectF(margin, 286f, width - margin, 491f))

        text(canvas, "Vergleich", margin, 541f, 15f, Color.BLACK, bold = true)
        if (comparison == null) {
            text(
                canvas,
                "Noch keine frühere Session desselben Programms vorhanden.",
                margin,
                568f,
                10f,
                muted
            )
        } else {
            text(
                canvas,
                "Gegen den Mittelwert der letzten " +
                    "${comparison.referenceSessionCount} vergleichbaren " +
                    "${session.program.title}-Sessions",
                margin,
                565f,
                9f,
                muted
            )
            comparisonRow(canvas, "Ruhig halten", comparison.hold, 590f)
            comparisonRow(canvas, "Abzugsverhalten", comparison.trigger, 617f)
            comparisonRow(canvas, "Nachhalten", comparison.followThrough, 644f)
        }

        text(
            canvas,
            "Technikindex: ${SessionExporter.number(summary.techniqueIndex, 1)} / 100" +
                "  •  Baseline: ${summary.baseline.sourceSessionCount}/" +
                "${summary.baseline.targetSessionCount} Sessions",
            margin,
            682f,
            10f,
            accent,
            bold = true
        )

        val meytonResult = session.meytonScore?.let {
            "Meyton-Gesamtergebnis: ${SessionExporter.number(
                it,
                if (it % 1.0 == 0.0) 0 else 1
            )} Ringe"
        } ?: "Meyton-Gesamtergebnis: nicht eingetragen"
        text(
            canvas,
            meytonResult,
            margin,
            714f,
            11f,
            Color.BLACK,
            bold = true
        )
        text(
            canvas,
            "AimTracer misst relative Winkelbewegung; RMS, Rang und " +
                "Technikindex sind keine Ringzahl.",
            margin,
            756f,
            8f,
            muted
        )
        finishPage(canvas)
    }

    private fun metricCard(
        canvas: Canvas,
        title: String,
        statistics: MetricStatistics,
        x: Float,
        y: Float,
        cardWidth: Float
    ) {
        paint.color = light
        canvas.drawRoundRect(
            RectF(x, y, x + cardWidth, y + 84f),
            8f,
            8f,
            paint
        )
        text(canvas, title, x + 10f, y + 18f, 8f, muted, bold = true)
        text(
            canvas,
            SessionExporter.number(statistics.mean, 2),
            x + 10f,
            y + 49f,
            20f,
            Color.BLACK,
            bold = true
        )
        text(
            canvas,
            "°/s RMS  •  Best ${SessionExporter.number(statistics.best, 2)}",
            x + 10f,
            y + 70f,
            7.5f,
            muted
        )
    }

    private fun trendChart(canvas: Canvas, rect: RectF) {
        text(canvas, "Verlauf je Schuss", rect.left, rect.top + 16f, 15f, Color.BLACK, true)
        val plot = RectF(rect.left + 34f, rect.top + 35f, rect.right - 8f, rect.bottom - 25f)
        paint.color = Color.LTGRAY
        paint.strokeWidth = 0.7f
        repeat(5) { index ->
            val y = plot.top + plot.height() * index / 4f
            canvas.drawLine(plot.left, y, plot.right, y, paint)
        }
        if (summary.standings.size > 1) {
            val max = summary.standings.maxOf {
                maxOf(
                    it.metrics.holdRms,
                    it.metrics.triggerRms,
                    it.metrics.followThroughRms
                )
            }.coerceAtLeast(0.1)
            line(canvas, summary.standings.map { it.metrics.holdRms }, max, accent, plot)
            line(
                canvas,
                summary.standings.map { it.metrics.triggerRms },
                max,
                Color.rgb(230, 128, 20),
                plot
            )
            line(
                canvas,
                summary.standings.map { it.metrics.followThroughRms },
                max,
                Color.rgb(45, 112, 210),
                plot
            )
        }
        legend(canvas, "Halten", accent, plot.left, plot.bottom + 16f)
        legend(
            canvas,
            "Abzug",
            Color.rgb(230, 128, 20),
            plot.left + 90f,
            plot.bottom + 16f
        )
        legend(
            canvas,
            "Nachhalten",
            Color.rgb(45, 112, 210),
            plot.left + 175f,
            plot.bottom + 16f
        )
    }

    private fun line(
        canvas: Canvas,
        values: List<Double>,
        maximum: Double,
        color: Int,
        rect: RectF
    ) {
        if (values.size < 2) return
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = rect.left + rect.width() * index / (values.size - 1)
            val y = rect.bottom - rect.height() * (value / maximum).toFloat()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        paint.color = color
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.6f
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
    }

    private fun legend(
        canvas: Canvas,
        label: String,
        color: Int,
        x: Float,
        y: Float
    ) {
        paint.color = color
        canvas.drawCircle(x + 3f, y - 3f, 3.5f, paint)
        text(canvas, label, x + 11f, y, 7.5f, muted)
    }

    private fun comparisonRow(
        canvas: Canvas,
        label: String,
        value: MetricComparison,
        y: Float
    ) {
        text(canvas, label, margin, y, 10f, Color.BLACK)
        text(
            canvas,
            "${SessionExporter.number(value.current, 2)} vs. " +
                "${SessionExporter.number(value.reference, 2)} °/s",
            margin + 158f,
            y,
            9f,
            muted
        )
        val prefix = if (value.improvementPercent > 0) "+" else ""
        text(
            canvas,
            "$prefix${SessionExporter.number(value.improvementPercent, 1)} %",
            width - margin - 65f,
            y,
            10f,
            if (value.improvementPercent >= 0) accent else Color.RED,
            bold = true
        )
    }

    private fun drawTable() {
        val columns = listOf(
            "Nr." to 25f,
            "Zeit" to 58f,
            "Rang" to 40f,
            "Technik" to 55f,
            "Halten" to 68f,
            "Abzug" to 68f,
            "Nachhalten" to 68f,
            "Audio" to 60f
        )
        var rowIndex = 0
        val rowsPerPage = 40
        while (rowIndex < summary.standings.size) {
            val canvas = newPage()
            text(canvas, "Schussübersicht", margin, 55f, 18f, Color.BLACK, true)
            text(canvas, session.name, margin, 75f, 9f, muted)
            var y = 96f
            tableRow(
                canvas,
                columns.map { it.first },
                columns,
                y,
                21f,
                header = true
            )
            y += 21f
            // 40 rows occupy y=117..797, with enough room for the footer.
            val pageEnd = minOf(rowIndex + rowsPerPage, summary.standings.size)
            while (rowIndex < pageEnd) {
                val standing = summary.standings[rowIndex]
                tableRow(
                    canvas,
                    tableValues(standing),
                    columns,
                    y,
                    17f,
                    shaded = rowIndex % 2 == 0
                )
                rowIndex += 1
                y += 17f
            }
            finishPage(canvas)
        }
    }

    private fun tableValues(standing: ShotStanding) = listOf(
        standing.ordinal.toString(),
        TIME_FORMAT.format(Date(standing.shot.receivedAt)),
        standing.overallRank.toString(),
        SessionExporter.number(standing.techniqueIndex, 1),
        SessionExporter.number(standing.metrics.holdRms, 2),
        SessionExporter.number(standing.metrics.triggerRms, 2),
        SessionExporter.number(standing.metrics.followThroughRms, 2),
        standing.shot.audioPeak.toString()
    )

    private fun tableRow(
        canvas: Canvas,
        values: List<String>,
        columns: List<Pair<String, Float>>,
        y: Float,
        rowHeight: Float,
        header: Boolean = false,
        shaded: Boolean = false
    ) {
        val total = columns.sumOf { it.second.toDouble() }.toFloat()
        if (header || shaded) {
            paint.color = if (header) accent else light
            canvas.drawRect(margin, y, margin + total, y + rowHeight, paint)
        }
        var x = margin
        columns.forEachIndexed { index, column ->
            text(
                canvas,
                values[index],
                x + 4f,
                y + rowHeight - 5f,
                7.5f,
                if (header) Color.WHITE else Color.BLACK,
                bold = header
            )
            x += column.second
        }
    }

    private fun newPage(): Canvas {
        pageNumber += 1
        val page = document.startPage(
            PdfDocument.PageInfo.Builder(width, height, pageNumber).create()
        )
        currentPage = page
        return page.canvas
    }

    private fun finishPage(canvas: Canvas) {
        text(
            canvas,
            "AimTracer  •  ${DATE_FORMAT.format(Date())}",
            margin,
            height - 18f,
            7f,
            muted
        )
        text(
            canvas,
            "Seite $pageNumber",
            width - margin - 55f,
            height - 18f,
            7f,
            muted
        )
        document.finishPage(
            requireNotNull(currentPage) { "Interne PDF-Seite fehlt." }
        )
        currentPage = null
    }

    private fun text(
        canvas: Canvas,
        value: String,
        x: Float,
        baseline: Float,
        size: Float,
        color: Int,
        bold: Boolean = false
    ) {
        paint.color = color
        paint.textSize = size
        paint.typeface = if (bold) {
            android.graphics.Typeface.DEFAULT_BOLD
        } else {
            android.graphics.Typeface.DEFAULT
        }
        paint.style = Paint.Style.FILL
        canvas.drawText(L10n.text(value), x, baseline, paint)
    }

    companion object {
        private val DATE_FORMAT = SimpleDateFormat(
            "dd.MM.yyyy HH:mm",
            Locale.getDefault()
        )
        private val TIME_FORMAT = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    }
}
