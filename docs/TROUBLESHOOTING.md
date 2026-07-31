# Fehlerdiagnose

## „Schussübertragung war unvollständig“

Firmware 0.1 übertrug die rund 312 Pakete eines Standardschusses als
unbestätigte BLE-Notifications. Auf einzelnen Telefon-/Verbindungs-
kombinationen konnte mindestens ein Paket fehlen, während das Endpaket noch
ankam. Die App verwarf den Schuss dann absichtlich, weil Kennwerte aus einem
unvollständigen Zeitfenster nicht zuverlässig wären.

Firmware 0.2 verwendet für den Schusskanal bestätigte ATT-Indications. Jedes
Paket wird dadurch erst nach Bestätigung des Telefons fortgeschrieben. Die
iPhone-App verarbeitet den wachsenden Schuss außerdem ohne wiederholte
Dictionary-Kopien.

### Upgrade

1. Firmware aus `firmware/AimTracerFirmware` erneut auf das XIAO laden.
2. Die aktualisierte iPhone- oder Android-App bauen und installieren.
3. XIAO und App vollständig neu starten.
4. Neu verbinden und unten in der Live-Ansicht `Firmware 0.2` kontrollieren.
5. Eine Trockentrainingssession starten und zunächst einen manuellen
   Testtrigger auslösen.
6. Warten, bis `Übertragung` wieder `Bereit` anzeigt.
7. Die Session öffnen; der Testschuss muss nun mit etwa 312 Samples erscheinen.

Bestätigte Pakete sind absichtlich langsamer. Je nach BLE-Verbindungsintervall
kann ein Standardschuss mehrere Sekunden zur Übertragung benötigen. Die
Session währenddessen nicht beenden und vor dem nächsten Test warten, bis
`Bereit` erscheint.

Sollte die Meldung mit Firmware 0.2 erneut auftreten, nennt sie jetzt die
empfangene und erwartete Sampleanzahl sowie den ersten fehlenden Index. Diese
drei Angaben zusammen mit der Firmwareanzeige notieren.

Bereits mit 0 Samples gespeicherte Sessions lassen sich nicht nachträglich
reparieren: Die unvollständigen Rohdaten wurden aus Sicherheitsgründen nicht
gespeichert. Die leeren Sessions können gelöscht werden.
