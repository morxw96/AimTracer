# Sessionauswertung und Export

[English](ANALYSIS_AND_EXPORT.en.md)

AimTracer 0.7 wertet die gespeicherten Schussfenster einer Session aus. Die
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

Alle Kennwerte sind Effektivwerte der für die Visierlinie relevanten
Gyroskop-Winkelgeschwindigkeit in Grad pro Sekunde:

```text
RMS = sqrt(mean(gy² + gz²))
```

Niedrigere Werte bedeuten weniger Winkelbewegung im betrachteten Zeitfenster.
`gy` entspricht Pitch, `gz` Yaw. Rollen (`gx`) bleibt vollständig im
Rohdatenexport, wird aber nicht als Halte- oder Abzugsfehler gewertet.

### Ruhig halten

Das Haltefenster endet ungefähr 180 ms vor dem Trigger. Es beschreibt die
allgemeine Pistolenruhe vor der unmittelbaren Auslösung.

### Abzugsverhalten

Das Abzugsfenster reicht von etwa 150 ms vor dem Trigger bis zum
Triggersample. Der eigentliche Schussimpuls wird nicht dem Abzugsverhalten
zugerechnet.

### Nachhalten

Das Nachhaltefenster reicht von 50 bis 250 ms nach dem Trigger. Die ersten
50 ms werden als pneumatischer Impuls ausgeklammert.

Die genauen Grenzen werden aus der wirklichen IMU-Abtastrate berechnet. Die
Fenster sind in `MotionAnalysis.swift` (iOS) und `MotionAnalysis.kt` (Android)
definiert und können später anhand von Praxismessungen angepasst werden.

## Persönlicher Technikindex

Die ersten drei abgeschlossenen Sessions mit mindestens drei Schüssen bilden pro Programm
eine feste persönliche Baseline. Aus allen zugehörigen Schüssen wird für jeden
Kennwert der Median gebildet. Bis drei Baseline-Sessions vorliegen, zeigt die
App „Einlernphase“; ohne Referenz ist `50` der neutrale Startwert.

Für einen Schuss wird jede Komponente nichtlinear um den persönlichen Median
auf eine Skala von 0 bis 100 abgebildet:

```text
Komponente = 100 / (1 + (Messwert / persönlicher Median)³)
Technikindex = 0,30 × Halten + 0,50 × Abzug + 0,20 × Nachhalten
```

Ein Wert von 50 entspricht der persönlichen Referenz. Über 50 bedeutet weniger
Bewegung, unter 50 mehr Bewegung. Anders als der alte sessioninterne
Vergleichsindex ist der Technikindex deshalb über Sessions desselben Programms
vergleichbar. Die Gewichtung und der Exponent sind mit `CALIBRATION:` markiert.

Fehltrigger sollten aus der Schussliste nach links gewischt und gelöscht
werden, weil sie Mittelwerte und Ranking sonst verzerren. Dabei wird nur der
lokal gespeicherte Schuss entfernt.

## Trend und Langzeitvergleich

Ab acht Schüssen vergleicht AimTracer die ersten mit den letzten Schüssen. Pro
Segment werden höchstens zehn Schüsse verwendet. Ein positiver Prozentwert
bedeutet, dass der RMS-Mittelwert im letzten Segment niedriger war.

Der Langzeitvergleich stellt die aktuelle Session weiterhin dem Mittelwert der
letzten fünf früheren Sessions desselben Programms gegenüber. Zusätzlich zeigt
ein Balkendiagramm den Technikindex der letzten zwölf Sessions auf der festen
Skala 0 bis 100. Die Referenzlinie liegt bei 50.

## Excel-Export

Der Tabellenexport ist eine UTF-8-CSV mit Byte-Order-Mark, Semikolon als
Trennzeichen und deutschem Dezimalkomma. Dadurch lässt sie sich in einer
deutschen Excel-Installation direkt öffnen.

Enthalten sind:

- Sessionmetadaten, Programm und optionales Meyton-Gesamtergebnis,
- Statistik je Kennwert,
- Vergleich mit früheren Sessions,
- eine Zeile je Schuss mit Rang, Technikindex und Roh-Peaks,
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
  `xiao-sense-component-side-down-usb-toward-shooter-v3`.

Die Rohwerte bleiben in den Sensorachsen. Die Metadaten enthalten die
wirksame Anzeigezuordnung, standardmäßig `roll = gx`, `rechts = +gz` und
`oben = +gy`, sowie den Zustand der X/Y-Invertierung. Dadurch können spätere
Bewertungsformeln neu berechnet werden, ohne die Messdaten zu verändern.

Die Dateien werden lokal auf dem Telefon erzeugt. iOS verwendet das Teilen-
Menü, Android den systemweiten Dokumentdialog. Es findet keine
Cloud-Übertragung durch AimTracer statt.
