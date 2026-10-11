# 86Box Android v0.8.5

## Android-Taskbar / Systemleisten

- Profilmanager, Hardware-Konfigurator und Emulator verwenden jetzt denselben robusten Immersive-Fullscreen-Modus.
- Auf Android 11+ werden Status- und Navigationsleisten per WindowInsetsController ausgeblendet.
- Zusaetzlich bleiben die Legacy-IMMERSIVE_STICKY-Flags als OEM-/Samsung-Tablet-Fallback aktiv.
- Nach Rueckkehr aus einem Android-Dateidialog wird der Fullscreen-Modus automatisch wiederhergestellt.

## HDD-Freeze bei laufender VM

Der bisherige HDD-Import aus der laufenden Emulator-Toolbar wurde entfernt. Eine feste HDD kann nicht sicher waehrend einer laufenden VM ersetzt werden. Der HDD-Button zeigt nun einen Hinweis und kann die VM schliessen sowie direkt den Hardware-Konfigurator oeffnen.

Floppy und CD-ROM bleiben weiterhin als Laufzeit-Medien mountbar.

## Neuer HDD-Bereich im Hardware-Profil

Neue Sektion `Hard disks` mit:

- Anzeige HDD 1-4 inklusive Image, Bus/Kanal, virtueller Groesse und bei dynamischen Images zusaetzlich physischer Dateigroesse.
- `IMPORT HDD IMAGE`: Slot waehlen, Image per Android-Dateidialog importieren. Das Image wird nach `media/hdd` des Profils kopiert und in `86box.cfg` eingetragen.
- VHD-Geometrie wird aus dem VHD-Footer uebernommen. HDI/HDX-Geometrie wird aus dem Header gelesen. RAW-Images erhalten die gleiche CHS-Berechnung wie der 86Box-Core.
- `CREATE HDD`: erstellt ein sparse RAW `.img` mit frei waehbarer Groesse (1-130000 MB) und traegt es als feste HDD ein.
- Ein freier IDE-Kanal wird automatisch gesucht; bei einem bereits belegten Slot bleibt dessen vorhandener Bus/Kanal nach Moeglichkeit erhalten.
- Beim Ersetzen eines belegten Slots erscheint eine Sicherheitsabfrage. Das alte Image wird nicht geloescht.

## Build-Cache-Regel

Diese Version aendert nur Android/Java-Code. Deshalb wird nur der Gradle-App-Build geloescht. Bei kuenftigen Aenderungen an C/C++, JNI, CMake oder libmain.so werden die nativen ARM64/x86_64 Build-Verzeichnisse bewusst komplett geloescht und frisch aufgebaut, damit kein alter Native-/Compile-Cache in der APK bleibt.
