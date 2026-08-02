package de.aimtracer.android.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.aimtracer.android.AimTracerViewModel
import de.aimtracer.android.BuildConfig
import de.aimtracer.android.L10n
import de.aimtracer.android.analysis.MetricComparison
import de.aimtracer.android.analysis.MotionAnalysis
import de.aimtracer.android.analysis.SessionAnalysis
import de.aimtracer.android.analysis.ShotStanding
import de.aimtracer.android.ble.ConnectionState
import de.aimtracer.android.export.SessionExporter
import de.aimtracer.android.model.DeviceConfiguration
import de.aimtracer.android.model.DevicePowerStatus
import de.aimtracer.android.model.TrainingProgram
import de.aimtracer.android.model.TrainingSession
import de.aimtracer.android.model.TriggerMode
import de.aimtracer.android.protocol.AimTracerCommand
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun LiveScreen(
    viewModel: AimTracerViewModel,
    contentPadding: PaddingValues,
    requestPermissions: () -> Unit
) {
    val ble = viewModel.ble
    val sessions = viewModel.sessions
    var showDevices by remember { mutableStateOf(false) }
    var showProgram by remember { mutableStateOf(false) }
    var showSessionFinish by remember { mutableStateOf(false) }
    val active = sessions.activeSession
    val lastShot = ble.lastShot

    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionTitle("AimTracer", "Android")
            OutlinedButton(
                onClick = {
                    if (!ble.hasRequiredPermissions()) {
                        requestPermissions()
                    } else {
                        showDevices = true
                        ble.startScan()
                    }
                }
            ) {
                Text(if (ble.connectionState.ready) "Verbunden" else "Verbinden")
            }
        }

        ConnectionCard(
            state = ble.connectionState,
            calibrated = ble.status?.calibrated == true,
            powerStatus = ble.powerStatus,
            onDisconnect = ble::disconnect
        )

        if (active == null) {
            Button(
                onClick = { showProgram = true },
                enabled = ble.connectionState.ready,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Session starten")
            }
        } else {
            Button(
                onClick = { showSessionFinish = true },
                enabled = ble.status?.capturing != true &&
                    ble.status?.transmitting != true,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Session beenden • ${active.program.title}")
            }
        }

        ble.status?.takeIf { it.capturing || it.transmitting }?.let { status ->
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        if (status.capturing) {
                            "Schuss wird aufgezeichnet …"
                        } else {
                            "Schuss wird übertragen …"
                        },
                        fontWeight = FontWeight.Bold
                    )
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(
                        if (status.transmitting) {
                            "Paket ${status.transmittingSampleIndex}"
                        } else {
                            "Kurzer Nachlauf von 250 ms"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        TraceCard(lastShot, ble.liveSamples)

        if (lastShot != null) {
            val metrics = MotionAnalysis.metrics(lastShot)
            ShotMetricsRow(metrics)
            active?.let { session ->
                SessionAnalysis.summary(session).standings
                    .firstOrNull { it.shot.id == lastShot.id }
                    ?.let {
                        LiveStandingCard(it, session.shots.size)
                    }
            }
        }
        ble.status?.let { StatusCard(it, ble.powerStatus) }
        Spacer(Modifier.height(8.dp))
    }

    if (showDevices) {
        ModalBottomSheet(
            onDismissRequest = {
                showDevices = false
                ble.stopScan()
            }
        ) {
            Column(
                Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SectionTitle("AimTracer suchen", "Suche endet nach 12 Sekunden")
                if (ble.devices.isEmpty()) {
                    Text(
                        if (ble.connectionState == ConnectionState.Scanning) {
                            "Suche läuft …"
                        } else {
                            "Kein AimTracer-Gerät gefunden."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ble.devices.forEach { device ->
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                ble.connect(device)
                                showDevices = false
                            }
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(15.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(device.name, fontWeight = FontWeight.Bold)
                            Text("${device.rssi} dBm")
                        }
                    }
                }
                TextButton(
                    onClick = ble::startScan,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Erneut suchen")
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }

    if (showProgram) {
        ProgramDialog(
            onDismiss = { showProgram = false },
            onStart = {
                viewModel.startSession(it)
                showProgram = false
            }
        )
    }

    if (showSessionFinish) {
        SessionFinishDialog(
            onDismiss = { showSessionFinish = false },
            onFinish = { meytonScore ->
                viewModel.stopSession(meytonScore)
                showSessionFinish = false
            }
        )
    }
}

@Composable
private fun ConnectionCard(
    state: ConnectionState,
    calibrated: Boolean,
    powerStatus: DevicePowerStatus?,
    onDisconnect: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(state.label, fontWeight = FontWeight.Bold)
                Text(
                    if (calibrated) {
                        "Sensor bereit"
                    } else {
                        "Gerät ruhig halten, bis die Nullung fertig ist"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                powerStatus?.let { power ->
                    Text(
                        "Akku ${powerText(power)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (power.isCritical) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
            if (state.ready) {
                TextButton(onClick = onDisconnect) { Text("Trennen") }
            }
        }
    }
}

@Composable
private fun ProgramDialog(
    onDismiss: () -> Unit,
    onStart: (TrainingProgram) -> Unit
) {
    var selected by remember { mutableStateOf(TrainingProgram.LP40) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Neue Session") },
        text = {
            Column {
                TrainingProgram.entries.forEach { program ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { selected = program }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected == program,
                            onClick = { selected = program }
                        )
                        Column {
                            Text(program.title)
                            program.plannedShots?.let {
                                Text(
                                    "$it Schüsse",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onStart(selected) }) { Text("Starten") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

@Composable
private fun SessionFinishDialog(
    onDismiss: () -> Unit,
    onFinish: (Double?) -> Unit
) {
    var scoreText by remember { mutableStateOf("") }
    val trimmed = scoreText.trim()
    val parsed = trimmed
        .replace(',', '.')
        .takeIf { it.isNotEmpty() }
        ?.toDoubleOrNull()
    val valid = trimmed.isEmpty() || parsed != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Session beenden") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = scoreText,
                    onValueChange = { scoreText = it },
                    label = { Text("Meyton-Gesamtergebnis") },
                    placeholder = { Text("z. B. 299 oder 316,5") },
                    singleLine = true,
                    isError = !valid,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal
                    )
                )
                Text(
                    "Optional. Der Wert erscheint in CSV, JSON und PDF.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onFinish(parsed) },
                enabled = valid
            ) { Text("Beenden") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

@Composable
private fun StatusCard(
    status: de.aimtracer.android.model.DeviceStatus,
    powerStatus: DevicePowerStatus?
) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(Modifier.fillMaxWidth()) {
                StatusValue("Schüsse", status.shotCount.toString(), Modifier.weight(1f))
                StatusValue(
                    "Mikrofon",
                    status.microphonePeak.toString(),
                    Modifier.weight(1f)
                )
            }
            Row(Modifier.fillMaxWidth()) {
                StatusValue(
                    "Verworfen",
                    status.droppedTriggers.toString(),
                    Modifier.weight(1f)
                )
                StatusValue(
                    "Firmware",
                    status.firmwareVersion,
                    Modifier.weight(1f)
                )
            }
            Row(Modifier.fillMaxWidth()) {
                StatusValue(
                    "Übertragung",
                    if (status.transmitting) {
                        "#${status.transmittingShotId}"
                    } else {
                        "Bereit"
                    },
                    Modifier.weight(1f)
                )
                StatusValue(
                    "TX-Sample",
                    if (status.transmitting) {
                        status.transmittingSampleIndex.toString()
                    } else {
                        "–"
                    },
                    Modifier.weight(1f)
                )
            }
            powerStatus?.let { power ->
                Row(Modifier.fillMaxWidth()) {
                    StatusValue(
                        "Akku",
                        powerText(power),
                        Modifier.weight(1f)
                    )
                    StatusValue(
                        "Akkuspannung",
                        String.format(
                            Locale.GERMANY,
                            "%.2f V",
                            power.millivolts / 1000.0
                        ),
                        Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

private fun powerText(power: DevicePowerStatus): String = when {
    power.isCharging -> "${power.levelPercent} % (lädt)"
    power.externalPowerPresent ->
        "${power.levelPercent} % (USB angeschlossen)"
    else -> "${power.levelPercent} %"
}

@Composable
private fun StatusValue(title: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(
            title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SessionsScreen(
    viewModel: AimTracerViewModel,
    contentPadding: PaddingValues
) {
    var selectedSessionId by remember { mutableStateOf<String?>(null) }
    val selected = viewModel.sessions.sessions.firstOrNull {
        it.id == selectedSessionId
    }

    if (selected != null) {
        BackHandler { selectedSessionId = null }
        SessionDetail(
            viewModel,
            selected,
            contentPadding,
            onBack = { selectedSessionId = null }
        )
        return
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            SectionTitle("Sessions", "Training und Langzeitvergleich")
            Spacer(Modifier.height(6.dp))
        }
        if (viewModel.sessions.sessions.isEmpty()) {
            item {
                Text(
                    "Noch keine Sessions. Starte im Live-Tab dein erstes Training.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(viewModel.sessions.sessions, key = { it.id }) { session ->
            SessionListCard(
                session = session,
                onOpen = { selectedSessionId = session.id },
                onDelete = { viewModel.sessions.deleteSession(session.id) }
            )
        }
    }
}

@Composable
private fun SessionListCard(
    session: TrainingSession,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val summary = SessionAnalysis.summary(session)
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column(Modifier.padding(15.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(session.name, fontWeight = FontWeight.Bold)
                Text(
                    session.program.plannedShots?.let {
                        "${session.shots.size}/$it"
                    } ?: session.program.title,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                DATE_TIME.format(Date(session.startedAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (session.shots.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy(15.dp)
                ) {
                    Text("H ${decimal(summary.hold.mean)}")
                    Text("A ${decimal(summary.trigger.mean)}")
                    Text("N ${decimal(summary.followThrough.mean)}")
                }
            }
            TextButton(
                onClick = onDelete,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Löschen", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private enum class RankingSort(val title: String) {
    CHRONOLOGICAL("Reihenfolge"),
    RANKING("Ranking")
}

@Composable
private fun SessionDetail(
    viewModel: AimTracerViewModel,
    session: TrainingSession,
    contentPadding: PaddingValues,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val summary = SessionAnalysis.summary(session)
    val previous = SessionAnalysis.previousComparable(
        session,
        viewModel.sessions.sessions
    )
    val comparison = SessionAnalysis.comparison(session, previous)
    var sort by remember { mutableStateOf(RankingSort.CHRONOLOGICAL) }
    var pendingCsv by remember { mutableStateOf<ByteArray?>(null) }
    var pendingPdf by remember { mutableStateOf<ByteArray?>(null) }
    var pendingJson by remember { mutableStateOf<ByteArray?>(null) }
    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        pendingCsv?.let { writeDocument(context, uri, it) }
        pendingCsv = null
    }
    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        pendingPdf?.let { writeDocument(context, uri, it) }
        pendingPdf = null
    }
    val jsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        pendingJson?.let { writeDocument(context, uri, it) }
        pendingJson = null
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) { Text("‹ Zurück") }
                Spacer(Modifier.width(5.dp))
                SectionTitle(session.program.title, session.name)
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Schüsse", fontWeight = FontWeight.Bold)
                        Text(
                            session.program.plannedShots?.let {
                                "${session.shots.size} von $it"
                            } ?: session.shots.size.toString(),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    session.program.plannedShots?.let {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = {
                                (session.shots.size.toFloat() / it).coerceIn(0f, 1f)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                    session.meytonScore?.let { score ->
                        Spacer(Modifier.height(10.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Meyton-Gesamtergebnis")
                            Text(
                                "${scoreText(score)} Ringe",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
        if (session.shots.isNotEmpty()) {
            item {
                SectionTitle("Mittelwerte")
                Spacer(Modifier.height(7.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricTile(
                        "Ruhig halten",
                        summary.hold.mean,
                        Modifier.weight(1f),
                        "Best ${decimal(summary.hold.best, 2)}"
                    )
                    MetricTile(
                        "Abzug",
                        summary.trigger.mean,
                        Modifier.weight(1f),
                        "Best ${decimal(summary.trigger.best, 2)}"
                    )
                    MetricTile(
                        "Nachhalten",
                        summary.followThrough.mean,
                        Modifier.weight(1f),
                        "Best ${decimal(summary.followThrough.best, 2)}"
                    )
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        SectionTitle("Verlauf", "°/s RMS je Schuss")
                        SessionTrendChart(summary.standings)
                        summary.trend?.let {
                            HorizontalDivider(Modifier.padding(vertical = 10.dp))
                            TrendSummaryRow(it)
                        }
                    }
                }
                OutlinedButton(
                    onClick = {
                        runCatching {
                            SessionExporter.rawJson(session)
                        }.onSuccess {
                            pendingJson = it
                            jsonLauncher.launch(
                                SessionExporter.jsonFileName(session)
                            )
                        }.onFailure {
                            toast(
                                context,
                                it.message ?: "JSON-Export fehlgeschlagen."
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text("Rohdaten als JSON")
                }
            }
            item {
                ComparisonCard(session, comparison)
            }
            item {
                SectionTitle(
                    "Export",
                    "Excel-kompatible Tabelle oder A4-Bericht"
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                SessionExporter.csv(session, previous)
                            }.onSuccess {
                                pendingCsv = it
                                csvLauncher.launch(
                                    SessionExporter.csvFileName(session)
                                )
                            }.onFailure {
                                toast(context, it.message ?: "CSV-Export fehlgeschlagen.")
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Excel/CSV")
                    }
                    Button(
                        onClick = {
                            runCatching {
                                SessionExporter.pdf(session, previous)
                            }.onSuccess {
                                pendingPdf = it
                                pdfLauncher.launch(
                                    SessionExporter.pdfFileName(session)
                                )
                            }.onFailure {
                                toast(context, it.message ?: "PDF-Export fehlgeschlagen.")
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("PDF-Bericht")
                    }
                }
            }
            item {
                SectionTitle(
                    "Schussranking",
                    "Relativer Vergleich innerhalb dieser Session"
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RankingSort.entries.forEach { option ->
                        if (sort == option) {
                            Button(
                                onClick = { sort = option },
                                modifier = Modifier.weight(1f)
                            ) { Text(option.title) }
                        } else {
                            OutlinedButton(
                                onClick = { sort = option },
                                modifier = Modifier.weight(1f)
                            ) { Text(option.title) }
                        }
                    }
                }
            }
            val standings = when (sort) {
                RankingSort.CHRONOLOGICAL ->
                    summary.standings.sortedBy { it.ordinal }
                RankingSort.RANKING ->
                    summary.standings.sortedWith(
                        compareBy<ShotStanding> { it.overallRank }
                            .thenBy { it.ordinal }
                    )
            }
            items(standings, key = { it.shot.id }) { standing ->
                StandingCard(
                    standing,
                    session.shots.size,
                    onDelete = {
                        viewModel.sessions.deleteShot(
                            session.id,
                            standing.shot.id
                        )
                    }
                )
            }
            item {
                Text(
                    "Der Vergleichsindex kombiniert Halten, Abzug und " +
                        "Nachhalten gleichgewichtet. Er ist keine Ringzahl.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ComparisonCard(
    session: TrainingSession,
    comparison: de.aimtracer.android.analysis.SessionComparison?
) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            SectionTitle("Frühere Sessions")
            if (comparison == null) {
                Text(
                    "Noch keine frühere ${session.program.title}-Session für " +
                        "den Langzeitvergleich vorhanden.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "Mittelwert der letzten ${comparison.referenceSessionCount} " +
                        "vergleichbaren Sessions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ComparisonValue("Ruhig halten", comparison.hold)
                ComparisonValue("Abzugsverhalten", comparison.trigger)
                ComparisonValue("Nachhalten", comparison.followThrough)
            }
        }
    }
}

@Composable
private fun ComparisonValue(title: String, comparison: MetricComparison) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(title)
            Text(
                "${decimal(comparison.current, 2)} vs. " +
                    "${decimal(comparison.reference, 2)} °/s",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            signedPercent(comparison.improvementPercent),
            color = if (comparison.improvementPercent >= 0) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun StandingCard(
    standing: ShotStanding,
    shotCount: Int,
    onDelete: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "#${standing.overallRank}  Schuss ${standing.ordinal}",
                        fontWeight = FontWeight.Bold,
                        color = if (standing.overallRank <= 3) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                    Text(
                        "H ${decimal(standing.metrics.holdRms)}  •  " +
                            "A ${decimal(standing.metrics.triggerRms)}  •  " +
                            "N ${decimal(standing.metrics.followThroughRms)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        decimal(standing.comparisonIndex, 0),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Vergleich",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Ränge: ${standing.holdRank}/$shotCount · " +
                        "${standing.triggerRank}/$shotCount · " +
                        "${standing.followThroughRank}/$shotCount",
                    style = MaterialTheme.typography.labelSmall
                )
                TextButton(onClick = onDelete) {
                    Text("Fehltrigger löschen")
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    viewModel: AimTracerViewModel,
    contentPadding: PaddingValues
) {
    val ble = viewModel.ble
    var draft by remember { mutableStateOf(ble.configuration) }
    var triggerMenu by remember { mutableStateOf(false) }
    LaunchedEffect(ble.configuration) { draft = ble.configuration }

    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionTitle("Kalibrierung", "Werte werden direkt an das XIAO gesendet")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Erkennung", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { triggerMenu = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(draft.triggerMode.title)
                }
                DropdownMenu(
                    expanded = triggerMenu,
                    onDismissRequest = { triggerMenu = false }
                ) {
                    TriggerMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.title) },
                            onClick = {
                                draft = draft.copy(triggerMode = mode)
                                triggerMenu = false
                            }
                        )
                    }
                }
                NumberField(
                    "Mikrofon-Schwelle",
                    draft.microphoneThreshold
                ) { draft = draft.copy(microphoneThreshold = it) }
                NumberField(
                    "Accel-Deltaschwelle",
                    draft.accelerationThreshold
                ) { draft = draft.copy(accelerationThreshold = it) }
                NumberField(
                    "Gyro-Schwelle",
                    draft.gyroThreshold
                ) { draft = draft.copy(gyroThreshold = it) }
                NumberField(
                    "Koinzidenz in ms",
                    draft.coincidenceMs
                ) { draft = draft.copy(coincidenceMs = it) }
                NumberField(
                    "Sperrzeit in ms",
                    draft.refractoryMs
                ) { draft = draft.copy(refractoryMs = it) }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Aufnahmefenster", fontWeight = FontWeight.Bold)
                NumberField("Vorlauf in ms", draft.preTriggerMs) {
                    draft = draft.copy(preTriggerMs = it)
                }
                NumberField("Nachlauf in ms", draft.postTriggerMs) {
                    draft = draft.copy(postTriggerMs = it)
                }
                NumberField("Live-Rate in Hz", draft.liveRateHz) {
                    draft = draft.copy(liveRateHz = it.coerceIn(5, 50))
                }
                Text(
                    "IMU-Rate: ${draft.sampleRateHz} Hz",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Button(
            onClick = { viewModel.sendConfiguration(draft) },
            enabled = ble.connectionState.ready,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Einstellungen an Gerät senden")
        }
        OutlinedButton(
            onClick = { ble.send(AimTracerCommand.CALIBRATE) },
            enabled = ble.connectionState.ready,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Gyro-Nullpunkt neu kalibrieren")
        }
        OutlinedButton(
            onClick = { ble.send(AimTracerCommand.MANUAL_TRIGGER) },
            enabled = ble.connectionState.ready,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Manuellen Testtrigger setzen")
        }
        Text(
            "CALIBRATION: Die Startwerte müssen mit montiertem Gehäuse an " +
                "der LP300XT anhand echter Schüsse und Trockentraining geprüft werden.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "Festes Montageprofil: Platinenunterseite oben, Sensorseite " +
                "unten, USB-C zum Schützen. Rohdaten werden unverändert " +
                "exportiert; die Anzeige nutzt rechts = -gz, oben = gx und " +
                "Rollen = gy.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SectionTitle("Über AimTracer")
        Card(Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("AimTracer", fontWeight = FontWeight.Bold)
                    Text(
                        "by Moritz Wenzel",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("App-Version")
                    Text(
                        BuildConfig.VERSION_NAME,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun NumberField(
    title: String,
    value: Int,
    onChange: (Int) -> Unit
) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { text ->
            text.filter(Char::isDigit).toIntOrNull()?.let(onChange)
        },
        label = { Text(title) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().padding(top = 7.dp)
    )
}

private fun writeDocument(context: Context, uri: Uri?, bytes: ByteArray) {
    if (uri == null) return
    runCatching {
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            ?: error("Datei konnte nicht geöffnet werden.")
    }.onSuccess {
        toast(context, "Export gespeichert.")
    }.onFailure {
        toast(context, it.message ?: "Export konnte nicht gespeichert werden.")
    }
}

private fun toast(context: Context, message: String) {
    Toast.makeText(context, L10n.text(message), Toast.LENGTH_LONG).show()
}

private val DATE_TIME: DateFormat = DateFormat.getDateTimeInstance(
    DateFormat.SHORT,
    DateFormat.SHORT
)

private fun scoreText(value: Double): String =
    decimal(value, if (value % 1.0 == 0.0) 0 else 1)
