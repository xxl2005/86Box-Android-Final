# 86Box Android v0.9.13 – Touch-Drag + Log-Korrektur

## Touch

Die bestehende Touchpad-Steuerung aus v0.9.11/v0.9.12 bleibt erhalten:

- Streichen: nur Mauszeiger bewegen.
- Kurz tippen: Linksklick.
- Doppeltippen: Doppelklick.
- Zwei Sekunden ruhig halten: Rechtsklick.
- Zwei-Finger-Tipp: Rechtsklick.

Neu in v0.9.13:

- Finger etwa 0,35 Sekunden ruhig auf einem Ziel halten und danach ziehen: echte linke Maustaste wird gedrückt gehalten.
- Beim Loslassen wird die linke Maustaste zuverlässig freigegeben.
- Damit lassen sich in Windows Desktop-Symbole verschieben, Fenster an der Titelleiste ziehen, Fensterrahmen/Ecken vergrößern/verkleinern und andere klassische Drag-and-Drop-Vorgänge ausführen.
- Beginnt die Bewegung sofort, bleibt die Geste für ihre gesamte Dauer ein normaler Mauszeiger-Swipe und wird nicht später unerwartet zu einem Drag.
- Zweiter Finger, Cancel oder Touch-Abbruch geben einen aktiven linken Drag sicher frei.

## Diagnose

- Der komplette v0.9.12-Testlog bestätigt funktionierenden physischen Pointer-Capture auf der SDL-Surface und echte Dell-Mouse-Relativbewegungen bis in HW_MOUSE_MOVE.
- CD-Dateiauswahl wurde sowohl mit Abbrechen als auch erfolgreicher Auswahl getestet; dieselbe VM wurde jeweils fortgesetzt.
- Kein ANR, kein Java-Crash und kein nativer Crash im geprüften Lauf.
- HardwareConfig aktualisiert nach dem Speichern nun auch seine In-Memory-INI-Instanz, damit `after SAVE` im CpuTrace tatsächlich den geschriebenen Dateistand zeigt und nicht einen alten Cachewert.

## Bekannte importierte Altlast

Ein Desktop-Profil enthält weiterhin einen alten Linux-CD-Pfad `/home/tobias/.../LLGD1.cue`. Dieser kann auf Android nicht geladen werden. Nach Auswahl einer Android-Datei funktioniert das CD-Mounten laut Testlog korrekt.
