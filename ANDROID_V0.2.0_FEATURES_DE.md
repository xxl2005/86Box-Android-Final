# 86Box Android v0.2.0 – Funktionsstand

Basis: 86Box 7.0 Audit/Fix + Multi-Core Stage 1 + Android Universal-64-Bit-Port.

## Neu in v0.2.0

### Touch/Mouse
- relative Ein-Finger-Maussteuerung direkt auf dem Emulatorbild
- Ein-Finger-Tap = linker Mausklick
- Zwei-Finger-Tap = rechter Mausklick
- separate L/R-Touchbuttons
- virtueller analoger Maus-Joystick unten links
- einstellbare Mausgeschwindigkeit 25–300 %

### Anzeige
- Pinch-Zoom 100–300 %
- Zoom-Regler und Reset auf 100 %
- immersive Android-Vollbilddarstellung
- ausblendbare/scrollbare rechte Toolbar

### Emulatorsteuerung
- Pause/Fortsetzen
- automatische Pause bei Home/App-Wechsel
- automatische Fortsetzung nur bei einer von Android gesetzten Hintergrundpause
- manuelle Pause bleibt beim App-Wechsel erhalten
- Hard Reset mit Sicherheitsdialog
- direkter Zugriff auf das originale 86Box-SDL-OSD

### Medien
- Android Storage Access Framework (`ACTION_OPEN_DOCUMENT`)
- FDD-Image importieren + sofort in Floppy 0 mounten
- CD-ROM-Image importieren + sofort in CD-ROM 0 mounten
- HDD-Image in den privaten App-Speicher importieren
- Medienaktionen werden als Events im 86Box/SDL-Hauptthread ausgefuehrt

### Tastatur
- Android-Bildschirmtastatur-Button
- SDL3 Text Input wird aktiviert
- committed ASCII-Text wird als XT/AT-kompatible Scancodefolge in den Gast eingespeist
- physische SDL-Tastaturunterstuetzung bleibt erhalten

### Stabilitaet
- Java/JNI-Steuerungs-IDs automatisch gegengeprueft
- fruehe Home/Lifecycle-Pause wird ignoriert, bis der Emulator wirklich bereit ist
- Touch-Einstellungen koennen schon vor dem laufenden SDL-Eventloop sicher gesetzt werden
- native Aktionen laufen nicht direkt aus dem Android-UI-Thread gegen den Emulatorzustand

## Weiter aus v0.1.1 enthalten
- Universal-APK: `arm64-v8a` + `x86_64`
- ARM64/x86_64 New Dynarec
- Multi-Core Stage 1 / asynchrone IDE-PIO-Writes
- SDL3
- OpenAL
- private Android-Pfade fuer ROMs, Assets, Temp, VMs und Medien
- Windows `BUILD_APK.bat`
- NDK r28c / API 35 Buildprofil

## Bewusste Grenzen dieser Stufe
- Vulkan ist im Android-SDL-Frontend noch nicht aktiviert.
- Netzwerk verwendet weiterhin den sicheren Null/Stub-Pfad; libslirp kommt spaeter.
- HDD wird in v0.2.0 importiert, aber nicht live in eine laufende feste Controller-Konfiguration eingesteckt.
- CUE/BIN-Sets mit mehreren Dateien werden noch nicht als zusammengehoeriges Set importiert.
- Pinch-Zoom ist zentriert; Pan/Scroll des vergroesserten Ausschnitts fehlt noch.
- Android-IME ist in dieser Stufe ASCII/US-Keymap; Unicode/Umlaute/nationale Layouts folgen spaeter.
- Es wird kein universeller VM-Save-State behauptet; 86Box hat hier keinen trivialen allgemeinen Snapshot-Pfad.

## Buildstatus in dieser Analyseumgebung
Der Android-spezifische CMake-Zweig wurde erfolgreich konfiguriert (mit Test-/Fake-Package-Targets), JNI-Bridge und C-Quellpfade wurden statisch geprueft. Ein echter Gradle/NDK-APK-Compile konnte hier nicht gestartet werden, weil kein Android SDK/NDK in der Laufumgebung installiert ist. `BUILD_APK.bat` ist fuer den realen Windows-Build vorbereitet.
