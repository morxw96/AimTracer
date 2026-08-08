# Feldkalibrierung

[English](CALIBRATION.en.md)

Die Startwerte sind absichtlich konservative Platzhalter. Mikrofonpegel und
Körperschall hängen stark von Gehäuse, Klemmkraft, Einbaulage und Waffe ab.
Ohne Messreihe an der montierten LP300XT wäre eine „fertige“ Schwelle
Scheingenauigkeit.

## 1. Montage und Achsen

1. Das Gehäuse starr und reproduzierbar befestigen.
2. Es darf nicht auf der Kartusche federn oder beim Schuss anschlagen.
3. In der App verbinden und auf „Sensor bereit“ warten.
4. Die Pistole langsam nach links/rechts und oben/unten bewegen.
5. Prüfen, ob die Spur erwartungsgemäß läuft.

AimTracer verwendet ein festes Montageprofil: Platinenunterseite nach oben,
Sensor-/Bestückungsseite nach unten und USB-C zum Schützen. Der Praxistest am
montierten Gerät ergibt: `gx` ist Rollen um die Laufachse, `+gz` ist rechts
und `+gy` ist oben. Die gespeicherten und exportierten
Rohwerte werden dabei nicht verändert.

Ab App 0.7.2 lassen sich die horizontale X- und vertikale Y-Achse in den
Einstellungen getrennt invertieren. Das ist für alternative Montagen gedacht
und verändert ausschließlich den Graphen. RMS-Kennwerte, Rankings und
Technikindex bleiben davon unabhängig.

Die drei RMS-Kennwerte verwenden Pitch und Yaw (`gy` und `gz`). Rollen (`gx`)
bleibt in den Rohdaten, fließt aber nicht in den Technikindex ein. Damit
bewertet der Index vorrangig Bewegungen der Visierlinie.

## 2. Gyro-Nullpunkt

„Gyro-Nullpunkt neu kalibrieren“ wählen und die Pistole ungefähr eine Sekunde
vollkommen ruhig ablegen. Die Firmware mittelt 256 Rohsamples. Währenddessen
ist die Schusserkennung unscharf und wird bei einer laufenden Session danach
automatisch wieder scharf.

Neu nullen:

- nach jeder mechanischen Neumontage,
- nach einer deutlichen Temperaturänderung,
- wenn die Live-Spur bei ruhender Waffe sichtbar driftet.

## 3. Trigger-Datensatz aufnehmen

Zunächst mit „Manuellen Testtrigger setzen“ prüfen, dass komplette Schüsse in
der App ankommen. Danach getrennt aufnehmen:

- mindestens 20 Trockentrainings-Auslösungen,
- mindestens 20 echte Luftpistolenschüsse,
- mindestens 20 bewusst ähnliche Fremdgeräusche am Stand,
- einige normale Bewegungen und Absetzbewegungen.

Notiere je Gruppe Audio-, Accel- und Gyro-Peak. Die Werte sind in der
Schussdetailansicht sichtbar.

## 4. Schwellen wählen

Startwerte:

| Parameter | Startwert |
|---|---:|
| Mikrofon | 7000 |
| Accel-Delta | 1500 |
| Gyro | 2000 |
| Koinzidenz | 40 ms |
| Sperrzeit | 1200 ms |

Empfohlenes Vorgehen:

1. Trigger vorübergehend auf „Nur Mikrofon“ stellen und dessen Schwelle so
   setzen, dass Fremdgeräusche möglichst nicht auslösen.
2. Auf „Nur Bewegung“ wechseln und Accel/Gyro so einstellen, dass normale
   Haltebewegung nicht, die Auslösung aber zuverlässig erkannt wird.
3. Abschließend „Mikrofon + Bewegung“ aktivieren.
4. Koinzidenz nur vergrößern, wenn echte Schüsse ausbleiben; ein zu großes
   Fenster macht Fremdtrigger wahrscheinlicher.
5. Je Änderung nur einen Parameter anfassen und zehn bis zwanzig Versuche
   wiederholen.

## 5. Bewertung

Die drei RMS-Werte der App sind Messwerte in Grad pro Sekunde, keine
Ringprognose. Der Technikindex in AimTracer 0.7 verwendet die ersten drei
Sessions jedes Programms als persönliche Referenz. Bis dahin läuft eine
Einlernphase mit 50 als neutralem Ausgangspunkt. Zeitfenster, Exponent und
Gewichtung bleiben als `CALIBRATION:` markiert und sollten nur anhand weiterer
realer Schießdaten verändert werden.

Der Sensor kennt weder Zielscheibe noch Visierlinie. Er kann relative
Mündungsbewegung, Auslöseimpuls und Nachhalten zeigen, aber keinen tatsächlichen
Treffer bestimmen.
