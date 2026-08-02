package de.aimtracer.android

import java.util.Locale

/** Lightweight app-wide localization for UI, BLE messages and exports. */
object L10n {
    val locale: Locale
        get() = Locale.getDefault()

    private val usesGerman: Boolean
        get() = locale.language.equals("de", ignoreCase = true)

    fun text(source: String): String {
        if (usesGerman || source.isBlank()) return source
        exact[source]?.let { return it }

        val patterns = listOf(
            Regex("^Verbinde mit (.+)$") to "Connecting to \$1",
            Regex("^Paket (\\d+)$") to "Packet \$1",
            Regex("^Session beenden • (.+)$") to "End session • \$1",
            Regex("^Vergleich mit (\\d+) Schüssen$") to "Compared with \$1 shots",
            Regex("^Letzte (\\d+) gegen erste (\\d+) Schüsse$") to
                "Last \$1 versus first \$2 shots",
            Regex("^Schuss (\\d+)$") to "Shot \$1",
            Regex("^Seite (\\d+)$") to "Page \$1",
            Regex("^(\\d+) von (\\d+)$") to "\$1 of \$2",
            Regex("^(\\d+(?:[.,]\\d+)?) Ringe$") to "\$1 points",
            Regex("^BLE-Suche fehlgeschlagen \\(Code (.+)\\)\\.$") to
                "BLE scan failed (code \$1).",
            Regex("^Bluetooth-Verbindung fehlgeschlagen \\((.+)\\)\\.$") to
                "Bluetooth connection failed (\$1)."
        )
        val dynamicallyTranslated = patterns
            .firstOrNull { it.first.matches(source) }
            ?.let { it.first.replace(source, it.second) }
            ?: source

        return phraseReplacements.fold(dynamicallyTranslated) { result, replacement ->
            result.replace(replacement.first, replacement.second)
        }
    }

    private val exact = mapOf(
        "Kalibrierung" to "Settings",
        "Werte werden direkt an das XIAO gesendet" to
            "Values are sent directly to the XIAO",
        "Nicht verbunden" to "Not connected",
        "Suche läuft" to "Scanning",
        "Verbunden" to "Connected",
        "Dienste werden geladen" to "Loading services",
        "Bluetooth ist ausgeschaltet" to "Bluetooth is turned off",
        "Bluetooth-Berechtigung fehlt." to "Bluetooth permission is missing.",
        "Unbekannter Bluetooth-Fehler." to "Unknown Bluetooth error.",
        "AimTracer ist nicht vollständig verbunden." to
            "AimTracer is not fully connected.",
        "AimTracer-Service wurde nicht gefunden." to
            "The AimTracer service was not found.",
        "GATT-Dienste konnten nicht geladen werden." to
            "GATT services could not be loaded.",
        "Mindestens eine GATT-Characteristic fehlt." to
            "At least one GATT characteristic is missing.",
        "BLE-Benachrichtigungen konnten nicht aktiviert werden." to
            "BLE notifications could not be enabled.",
        "BLE-Akkustand fehlt." to "BLE battery level is missing.",
        "Schreiben auf AimTracer fehlgeschlagen." to
            "Writing to AimTracer failed.",
        "Ohne Bluetooth-Berechtigung kann AimTracer das XIAO nicht suchen." to
            "AimTracer cannot scan for the XIAO without Bluetooth permission.",
        "Live" to "Live",
        "Sessions" to "Sessions",
        "Android" to "Android",
        "Verbinden" to "Connect",
        "Trennen" to "Disconnect",
        "Session starten" to "Start session",
        "Session beenden" to "End session",
        "Schuss wird aufgezeichnet …" to "Recording shot…",
        "Schuss wird übertragen …" to "Transferring shot…",
        "Kurzer Nachlauf von 250 ms" to "Capturing 250 ms of follow-through",
        "Live-Bewegung" to "Live motion",
        "Letzter Schuss" to "Latest shot",
        "Relative Winkelspur" to "Relative angular trace",
        "Blau vor, orange nach der Auslösung" to
            "Blue before, orange after the trigger",
        "Ruhig halten" to "Hold stability",
        "Halten" to "Hold",
        "Abzug" to "Trigger",
        "Abzugsverhalten" to "Trigger behaviour",
        "Nachhalten" to "Follow-through",
        "Einordnung in dieser Session" to "Ranking in this session",
        "Sensor bereit" to "Sensor ready",
        "Gerät ruhig halten, bis die Nullung fertig ist" to
            "Keep the device still until calibration is complete",
        "Bereit" to "Ready",
        "Akku" to "Battery",
        "Akkuspannung" to "Battery voltage",
        "Schüsse" to "Shots",
        "Mikrofon" to "Microphone",
        "Puffer" to "Buffer",
        "Verworfen" to "Dropped",
        "Firmware" to "Firmware",
        "Rate" to "Rate",
        "Übertragung" to "Transfer",
        "TX-Sample" to "TX sample",
        "AimTracer suchen" to "Search for AimTracer",
        "Suche endet nach 12 Sekunden" to "Scan ends after 12 seconds",
        "Suche läuft …" to "Scanning…",
        "Kein AimTracer-Gerät gefunden." to "No AimTracer device found.",
        "Erneut suchen" to "Scan again",
        "Neue Session" to "New session",
        "Programm" to "Program",
        "Trockentraining" to "Dry fire",
        "Freies Training" to "Free training",
        "Starten" to "Start",
        "Meyton-Gesamtergebnis" to "Meyton total",
        "z. B. 299 oder 316,5" to "e.g. 299 or 316.5",
        "Optional. Der Wert erscheint in CSV, JSON und PDF." to
            "Optional. The value is included in CSV, JSON and PDF exports.",
        "Abbrechen" to "Cancel",
        "Beenden" to "Finish",
        "Noch keine Sessions. Starte im Live-Tab dein erstes Training." to
            "No sessions yet. Start your first training session in the Live tab.",
        "Training und Langzeitvergleich" to "Training and long-term comparison",
        "Löschen" to "Delete",
        "Mittelwerte" to "Averages",
        "Verlauf" to "Progress",
        "Frühere Sessions" to "Earlier sessions",
        "Noch keine frühere Session desselben Programms vorhanden." to
            "No earlier session using the same program is available.",
        "Schussranking" to "Shot ranking",
        "Reihenfolge" to "Sequence",
        "Ranking" to "Ranking",
        "Relativer Vergleich innerhalb dieser Session" to
            "Relative comparison within this session",
        "Der Vergleichsindex kombiniert Halten, Abzug und Nachhalten gleichgewichtet. Er ist keine Ringzahl." to
            "The comparison index combines hold, trigger and follow-through with equal weight. It is not a target score.",
        "Fehltrigger löschen" to "Delete false trigger",
        "Export" to "Export",
        "Excel-kompatible Tabelle oder A4-Bericht" to
            "Excel-compatible table or A4 report",
        "Excel/CSV" to "Excel/CSV",
        "PDF-Bericht" to "PDF report",
        "Bericht" to "Report",
        "Rohdaten" to "Raw data",
        "Rohdaten als JSON" to "Raw data as JSON",
        "Export gespeichert." to "Export saved.",
        "Export konnte nicht gespeichert werden." to "Could not save export.",
        "CSV-Export fehlgeschlagen." to "CSV export failed.",
        "PDF-Export fehlgeschlagen." to "PDF export failed.",
        "JSON-Export fehlgeschlagen." to "JSON export failed.",
        "Datei konnte nicht geöffnet werden." to "The file could not be opened.",
        "Session enthält keine Schüsse." to "The session contains no shots.",
        "Erkennung" to "Detection",
        "Nur Mikrofon" to "Microphone only",
        "Nur Bewegung" to "Motion only",
        "Mikrofon + Bewegung" to "Microphone + motion",
        "Mikrofon-Schwelle" to "Microphone threshold",
        "Accel-Deltaschwelle" to "Acceleration-delta threshold",
        "Gyro-Schwelle" to "Gyroscope threshold",
        "Koinzidenz in ms" to "Coincidence in ms",
        "Sperrzeit in ms" to "Refractory period in ms",
        "Aufnahmefenster" to "Capture window",
        "Vorlauf in ms" to "Pre-trigger in ms",
        "Nachlauf in ms" to "Post-trigger in ms",
        "Live-Rate in Hz" to "Live rate in Hz",
        "Einstellungen an Gerät senden" to "Send settings to device",
        "Gyro-Nullpunkt neu kalibrieren" to "Recalibrate gyroscope zero",
        "Manuellen Testtrigger setzen" to "Create manual test trigger",
        "CALIBRATION: Die Startwerte müssen mit montiertem Gehäuse an der LP300XT anhand echter Schüsse und Trockentraining geprüft werden." to
            "CALIBRATION: Verify the starting values with the enclosure mounted on the LP300XT using live shots and dry fire.",
        "Festes Montageprofil: Platinenunterseite oben, Sensorseite unten, USB-C zum Schützen. Rohdaten werden unverändert exportiert; die Anzeige nutzt rechts = -gz, oben = gx und Rollen = gy." to
            "Fixed mounting profile: PCB underside up, sensor side down and USB-C toward the shooter. Raw data is exported unchanged; the display uses right = -gz, up = gx and roll = gy.",
        "Über AimTracer" to "About AimTracer",
        "App-Version" to "App version",
        "by Moritz Wenzel" to "by Moritz Wenzel",
        "AimTracer Trainingsprotokoll" to "AimTracer training report",
        "TRAININGSPROTOKOLL" to "TRAINING REPORT",
        "Beginn" to "Start",
        "Ende" to "End",
        "Geplant" to "Planned",
        "Kennwert" to "Metric",
        "Mittelwert (°/s RMS)" to "Mean (°/s RMS)",
        "Median" to "Median",
        "Standardabweichung" to "Standard deviation",
        "Bestwert" to "Best",
        "Schlechtester Wert" to "Worst",
        "Aktuelle Session" to "Current session",
        "Vorherige Sessions" to "Previous sessions",
        "Veränderung" to "Change",
        "Schuss" to "Shot",
        "Geräte-ID" to "Device ID",
        "Zeit" to "Time",
        "Gesamtrang" to "Overall rank",
        "Vergleichsindex" to "Comparison index",
        "Rang Halten" to "Hold rank",
        "Rang Abzug" to "Trigger rank",
        "Rang Nachhalten" to "Follow-through rank",
        "Audio-Peak" to "Audio peak",
        "Accel-Peak" to "Acceleration peak",
        "Gyro-Peak" to "Gyro peak",
        "Hinweis" to "Note",
        "RUHIG HALTEN" to "HOLD STABILITY",
        "ABZUGSVERHALTEN" to "TRIGGER BEHAVIOUR",
        "NACHHALTEN" to "FOLLOW-THROUGH",
        "Verlauf je Schuss" to "Progress by shot",
        "Mindestens zwei Schüsse für den Verlauf erforderlich." to
            "At least two shots are required for the progress chart.",
        "Schussübersicht" to "Shot overview",
        "Nr." to "No.",
        "Rang" to "Rank",
        "Index" to "Index",
        "Audio" to "Audio",
        "Meyton-Gesamtergebnis: nicht eingetragen" to
            "Meyton total: not entered",
        "Interne PDF-Seite fehlt." to "Internal PDF page is missing."
        ,"BLE-Akkupaket hat nicht 6 Byte." to
            "The BLE battery packet is not 6 bytes long."
        ,"Ungültiges Konfigurationspaket." to "Invalid configuration packet."
        ,"BLE-Paket hat nicht 20 Byte." to "The BLE packet is not 20 bytes long."
        ,"Unvollständiges Schusspaket." to "Incomplete shot packet."
        ,"Ungültiger Schusspakettyp." to "Invalid shot packet type."
        ,"Sample-Reihenfolge ist unvollständig." to
            "The sample sequence is incomplete."
        ,"Prüfsumme stimmt nicht." to "The checksum does not match."
        ,"‹ Zurück" to "‹ Back"
    )

    private val phraseReplacements = listOf(
        "Meyton-Gesamtergebnis:" to "Meyton total:",
        "Schussübertragung war unvollständig" to "Shot transfer was incomplete",
        "Erstes fehlendes Paket:" to "First missing packet:",
        "Mittelwert der letzten" to "Average of the last",
        "Gegen den Mittelwert der letzten" to "Compared with the average of the last",
        "Noch keine frühere" to "No earlier",
        "-Session für den Langzeitvergleich vorhanden." to
            " session is available for long-term comparison.",
        "vergleichbaren Sessions" to "comparable sessions",
        "vergleichbaren" to "comparable",
        "Ränge:" to "Ranks:",
        "Schussübersicht" to "Shot overview",
        " Schüsse" to " shots",
        " Schuss" to " shot",
        " Ringe" to " points",
        " (lädt)" to " (charging)",
        "USB angeschlossen" to "USB connected",
        "Akku " to "Battery ",
        "IMU-Rate:" to "IMU rate:",
        "Freies Training" to "Free training",
        "Trockentraining" to "Dry fire",
        "Ruhig halten" to "Hold stability",
        "Abzugsverhalten" to "Trigger behaviour",
        "Nachhalten" to "Follow-through",
        "Vergleichsindex sind keine Ringzahl." to
            "the comparison index are not target scores.",
        "Vergleichsindex gelten nur innerhalb dieser Session und sind keine Ringzahl." to
            "the comparison index apply only within this session and are not target scores.",
        "Niedrigere RMS-Werte bedeuten weniger Winkelbewegung. Rang und " to
            "Lower RMS values indicate less angular movement. Ranks and ",
        "AimTracer misst relative Winkelbewegung; RMS, Rang und " to
            "AimTracer measures relative angular movement; RMS, ranks and "
    )
}
