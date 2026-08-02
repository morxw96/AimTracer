# Sessionauswertung und Export

[English](ANALYSIS_AND_EXPORT.en.md)

AimTracer 0.2 wertet die gespeicherten Schussfenster einer Session aus. Die
Kennwerte sind für den Vergleich des eigenen Bewegungsablaufs gedacht. Sie
berechnen weder den Zielpunkt noch eine Ringzahl.

## Programme

Beim Start einer Session stehen fünf Programme zur Auswahl:

- `LP20` mit 20 geplanten Schüssen,
- `LP40` mit 40 geplanten Schüssen,
- `LP60` mit 60 geplanten Schüssen,
- Trockentraining ohne feste Schusszahl,
- freies Training ohne feste Schusszahl.

Historische Vergleiche verwenden nur frühere Sessions desselben Programms.
Alte Sessions aus AimTracer 0.1 bleiben lesbar und werden als freies Training
behandelt.

## Drei Kennwerte

Alle Kennwerte sind Effektivwerte der dreidimensionalen
Gyroskop-Winkelgeschwindigkeit in Grad pro Sekunde:

```text
RMS = sqrt(mean(gx² + gy² + gz²))
```

Niedrigere Werte bedeuten weniger Winkelbewegung im betrachteten Zeitfenster.
Da der Betrag aller drei Achsen verwendet wird, beeinflusst die feste
Montage-Achsentransformation diese drei RMS-Werte nicht. Sie bestimmt aber die
sichtbare Links-/Rechts- und Oben-/Unten-Richtung der Spur.

### Ruhig halten

Das Haltefenster endet ungefähr 167 ms vor dem Trigger. Es beschreibt die
allgemeine Pistolenruhe vor der unmittelbaren Auslösung.

### Abzugsverhalten

Das Abzugsfenster reicht von etwa 150 ms vor bis 80 ms nach dem Trigger. Es
reagiert damit auf Bewegungen rund um die Schussabgabe.

### Nachhalten

Das Nachhaltefenster umfasst die ersten 250 ms ab Trigger.

Die genauen Grenzen werden aus der wirklichen IMU-Abtastrate berechnet. Die
Fenster sind in `MotionAnalysis.swift` (iOS) und `MotionAnalysis.kt` (Android)
definiert und können später anhand von Praxismessungen angepasst werden.

## Ranking

Für jeden Kennwert werden alle Schüsse einer Session aufsteigend sortiert:

- der niedrigste RMS-Wert erhält Rang 1,
- gleiche Werte erhalten denselben Rang,
- jeder Rang wird in einen relativen Wert von 0 bis 100 umgerechnet.

Der Vergleichsindex ist derzeit der gleichgewichtete Mittelwert der drei
relativen Werte:

```text
Vergleichsindex = (Halten + Abzug + Nachhalten) / 3
```

Anschließend wird anhand dieses Index der Gesamtrang bestimmt. Die Gewichtung
ist im Code mit `CALIBRATION:` markiert. Vor einer abweichenden Gewichtung
sollten genügend echte LP40-Aufzeichnungen zusammen mit Meyton-Ergebnissen
vorliegen.

Der Index ist nur innerhalb derselben Session gültig. Ein Wert von 90 in
Session A ist nicht direkt mit 90 in Session B vergleichbar.

Fehltrigger sollten aus der Schussliste nach links gewischt und gelöscht
werden, weil sie Mittelwerte und Ranking sonst verzerren. Dabei wird nur der
lokal gespeicherte Schuss entfernt.

## Trend und Langzeitvergleich

Ab acht Schüssen vergleicht AimTracer die ersten mit den letzten Schüssen. Pro
Segment werden höchstens zehn Schüsse verwendet. Ein positiver Prozentwert
bedeutet, dass der RMS-Mittelwert im letzten Segment niedriger war.

Der Langzeitvergleich stellt die aktuelle Session dem Mittelwert der letzten
fünf früheren Sessions desselben Programms gegenüber. Auch hier bedeutet ein
positiver Prozentwert weniger Winkelbewegung als in den Vergleichssessions.

## Excel-Export

Der Tabellenexport ist eine UTF-8-CSV mit Byte-Order-Mark, Semikolon als
Trennzeichen und deutschem Dezimalkomma. Dadurch lässt sie sich in einer
deutschen Excel-Installation direkt öffnen.

Enthalten sind:

- Sessionmetadaten, Programm und optionales Meyton-Gesamtergebnis,
- Statistik je Kennwert,
- Vergleich mit früheren Sessions,
- eine Zeile je Schuss mit Rang, Vergleichsindex und Roh-Peaks,
- ein Hinweis zur Interpretation.

## PDF-Export

Der A4-Bericht enthält:

- Session- und Programmdaten,
- Mittel- und Bestwerte,
- einen farbigen Verlauf der drei Kennwerte,
- Vergleich mit früheren Sessions,
- das beim Sessionende eingegebene Meyton-Gesamtergebnis,
- vollständige Schusstabelle mit automatischem Seitenumbruch.

Eine LP40-Serie belegt genau eine A4-Tabellenseite; bei längeren Sessions
folgen weitere Seiten mit jeweils bis zu 40 Schüssen.

## Rohdaten-JSON

Der JSON-Export verwendet das Format `aimtracer-raw-session` mit
`schemaVersion` 1. Er enthält:

- Sessiondaten einschließlich Programm und Meyton-Gesamtergebnis,
- Triggerzeitpunkt, Abtastrate und Peaks jedes Schusses,
- jedes unveränderte `gx/gy/gz`-, `ax/ay/az`- und Mikrofonsample,
- eine daraus berechnete relative Samplezeit in Millisekunden,
- das feste Montageprofil
  `xiao-sense-component-side-down-usb-toward-shooter`.

Die Rohwerte bleiben in den Sensorachsen. Als Metadaten ist die
Anzeigezuordnung `roll = gy`, `rechts = -gz`, `oben = gx` enthalten. Dadurch
können spätere Bewertungsformeln neu berechnet werden, ohne die Messdaten zu
verändern.

Die Dateien werden lokal auf dem Telefon erzeugt. iOS verwendet das Teilen-
Menü, Android den systemweiten Dokumentdialog. Es findet keine
Cloud-Übertragung durch AimTracer statt.
