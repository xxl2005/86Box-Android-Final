# 86Box Android v0.8.0 – Hardwareprofile + Diagnose-Log + Multi-Core Stage 2

Diese Version baut auf v0.5.1 auf und enthält einen zusätzlichen Fehler-/Sicherheits-Audit. Neu ist ein VM-/Hardwareprofil-Manager mit getrennten `86box.cfg`-, NVR/CMOS- und Medienverzeichnissen sowie Import normaler Desktop-86Box-VM-Ordner und Profil-ZIPs. Multi-Core Stage 2, Performance-Profiler, Touch, Vulkan/GLES-Fallback, Netzwerk und Hardware-Eingabe bleiben enthalten.

## VM-/Hardwareprofile und Desktop-Import

Beim App-Start erscheint zuerst der Profil-Manager. Profile koennen angelegt, gestartet, umbenannt, dupliziert, geloescht sowie als ZIP importiert/exportiert werden. `IMPORT DESKTOP PROFILE` uebernimmt einen normalen 86Box-VM-Ordner mit `86box.cfg`. Windows-Pfade werden auf Android-Pfade normalisiert; absolute Pfade werden nach Moeglichkeit auf mitkopierte Dateien abgebildet. Das Original bleibt als `86box.desktop-original.cfg` erhalten und nicht aufloesbare externe Medienpfade werden in `IMPORT_REPORT.txt` protokolliert. Eine vorhandene v0.4-Einzel-VM wird automatisch als `Legacy v0.4` migriert.

## Diagnose-/Fehlerlog (v0.5.1)

Jede VM schreibt pro Sitzung getrennte Logs nach `profiles/<VM>/logs/`:

- `android-*.log` fuer Android/UI/Lifecycle, Build-/Geraeteinformationen und Java-Fehler
- `native-*.log` fuer das komplette 86Box-Nativelog ueber `--logfile`
- `java-crash-last.txt` fuer den letzten ungefangenen Java-Stacktrace
- `native-crash-last.txt` als Marker fuer SIGSEGV/SIGABRT/SIGBUS/SIGILL/SIGFPE

Der Toolbar-Button `LOG` zeigt die letzten Logzeilen direkt auf dem Geraet und kann ein Diagnose-ZIP exportieren. Im Profilmanager steht `Export diagnostics` auch fuer eine VM zur Verfuegung, die nicht mehr startet. Diagnose-ZIPs enthalten Konfiguration und Logs, aber bewusst keine HDD-, FDD- oder CD-Images. Aeltere Sitzungslogs werden automatisch begrenzt.

## Schnellstart unter Windows

1. Android Studio installieren.
2. Im SDK Manager `Android SDK Command-line Tools (latest)` aktivieren.
3. Optional den eigenen 86Box-ROM-Satz nach `android/app/src/main/assets/86box/roms/` kopieren.
4. `android\\BUILD_APK.bat` starten.
5. Bei Bedarf zuerst **[2] Android SDK + NDK Komponenten installieren**.
6. Danach **[3] DEBUG UNIVERSAL APK bauen (ARM64 + x86_64)**.

Standard-Ausgabe:

`86Box-Android-universal64-v0.8.0-debug.apk`

Zusaetzlich koennen entstehen:

- `86Box-Android-arm64-v0.8.0-debug.apk`
- `86Box-Android-x86_64-v0.8.0-debug.apk`

Der Build verwendet Android API 35, minSdk 28, NDK r28c, SDL3 3.4.16, Gradle 8.13, vcpkg und Ninja.

## Multi-Core Stage 2 / SCSI

Bei echten SCSI-Festplatten startet 86Box READ(6), READ(10) und READ(12) bereits in einem separaten `scsi-read` Host-Thread. Der jeweilige SCSI-Controller wartet erst unmittelbar vor dem ersten Zugriff auf den Datenpuffer. Dadurch kann reale Host-I/O-Zeit mit der bereits vorhandenen emulierten SCSI-/Controller-Latenz ueberlappen.

Schreibzugriffe bleiben in Stage 2 absichtlich synchron. Fuer SCSI-Writes muesste die Status-/Fehlerbestaetigung weiter nach hinten verschoben werden; ein einfaches Write-Behind koennte dem Gast sonst `GOOD` melden, bevor der Host-Write wirklich abgeschlossen ist.

CMake-Schalter:

- `ASYNC_SCSI_READS=ON` (Standard)
- `PERF_PROFILE=ON` im Android-Build

## Performance-Profiler

Der Toolbar-Button `PERF` startet eine Messung. Beim zweiten Tippen wird sie beendet und direkt als Dialog angezeigt. Zusaetzlich schreibt der Profiler alle 5 Sekunden eine Zusammenfassung ins 86Box-Log. Gemessen werden:

- `CPU/core` – Zeit in der emulierten CPU-Ausfuehrung
- `I/O+chipset` – zentrale Port-I/O-/Chipset-Handler
- `graphics` – Video-Blit-Worker
- `sound` – Sound-Poll/Mixing
- `storage` – Host-HDD-Reads/Writes, auch in Worker-Threads
- `SCSI wait` – Restzeit, in der ein Controller trotz Overlap noch auf den SCSI-Read warten musste

Die Kategorien sind Hostzeiten und duerfen sich ueberschneiden. Bei echter Parallelitaet koennen ihre Prozentwerte zusammen ueber 100 % liegen. Auf Desktop-Builds kann der Profiler mit `-DPERF_PROFILE=ON` einkompiliert und zur Laufzeit mit `86BOX_PERF_PROFILE=1` aktiviert werden.

## Vulkan

SDL3 wird unter Android mit folgender Renderer-Prioritaet gestartet:

`vulkan -> gpu -> opengles2 -> software`

Damit wird Vulkan bevorzugt, aber die App bleibt auf Geraeten ohne funktionierenden Vulkan-Renderer startfaehig. Unter `⚙` wird der aktuell aktive SDL-Renderer angezeigt.

## Touch und Zoom/Pan

- Ein Finger ziehen: relative Maus
- kurzer Ein-Finger-Tap: Linksklick
- kurzer Zwei-Finger-Tap: Rechtsklick
- zwei Finger auseinander/zusammen: Zoom 100–300 %
- zwei Finger gemeinsam verschieben: Pan des vergroesserten Bildes
- Zurueck auf 100 %: Pan wird automatisch zentriert
- virtueller Maus-Joystick unten links
- getrennte L/R-Tasten
- Mausgeschwindigkeit 25–300 %

## Deutsche/UTF-8-Tastatur

Der `⌨`-Button aktiviert SDL3 Text Input und oeffnet die Android-IME. Committed Text kommt als UTF-8 im SDL-Hauptthread an und wird in PC-XT-Scancodes umgesetzt.

Unter `⚙` kann `German guest keyboard` aktiviert/deaktiviert werden. Die deutsche QWERTZ-Map umfasst u. a. `ä ö ü ß € § ° µ` sowie gaengige AltGr-Zeichen. Physische Tastaturen werden seit v0.3.1 bevorzugt ueber rohe Android/Linux-HID-Scancodes direkt in 86Box-XT/AT-Scancodes umgesetzt. Dadurch funktionieren auch QWERTZ, AltGr, F-Tasten, NumPad, Pfeile und die deutsche ISO-Taste < > | stabiler.


## Physische USB-/Bluetooth-Maus und Tastatur

- USB-HID- und Bluetooth-Tastaturen werden direkt erkannt, ohne besondere Android-Berechtigung.
- bevorzugt werden rohe Linux/HID-Scancodes benutzt; nur wenn ein Geraet keinen Scancode liefert, greift ein Android-KeyCode-Fallback.
- unterstuetzt: Buchstaben/Ziffern, Ctrl/Alt/Shift/AltGr, F1-F12, Pfeile, Home/End, Insert/Delete, PageUp/PageDown, NumPad, Windows/Menu sowie ISO-102nd-Key.
- gehaltene Tasten werden nicht durch Android-Keyrepeat dupliziert; die emulierte PC-Tastatur uebernimmt Typematic Repeat.
- physische Maus: links/rechts/mittel sowie X1/X2 und Mausrad.
- Klick in die Emulatorflaeche oder der `MOUSE`-Toolbar-Button aktiviert Android Pointer Capture fuer echte relative Bewegung ohne Bildschirmrand.
- `Ctrl+Alt+G` loest den Maus-Lock wieder, passend zum Desktop-86Box-Verhalten.
- beim Home/App-Wechsel werden gedrueckte Hardware-Tasten und Maustasten geloest, damit im Gast keine Taste haengen bleibt.
- dieselbe Mausgeschwindigkeitseinstellung (25-300 %) wirkt auch auf die physische Maus.

## CD-ROM / CUE-BIN

Der `CD`-Button erlaubt Mehrfachauswahl im Android-Dateidialog. Bei einem CUE/BIN-Set muessen genau eine CUE und alle referenzierten Trackdateien gemeinsam markiert werden. Die Dateien werden in denselben privaten Session-Ordner kopiert; die lokale CUE wird auf die importierten Namen angepasst und auf fehlende Tracks geprueft, bevor sie gemountet wird. Mehrere gleichzeitig ausgewaehlte CUE-Sheets werden abgewiesen.

Einzelne ISO/CHD/BIN-Dateien funktionieren weiterhin direkt.

## libslirp-Netzwerk

v0.3.0 baut den echten 86Box-libslirp-Pfad fuer `arm64-v8a` und `x86_64`. Androids `INTERNET`-Berechtigung ist gesetzt.

Damit ein Gast ihn verwendet, muss in seiner 86Box-Maschinenkonfiguration eine emulierte Netzwerkkarte vorhanden und als Netztyp `SLiRP` ausgewaehlt sein. Das komplette Desktop-Qt-Konfigurationsfenster wurde nicht auf Android uebernommen; der Backend-Treiber selbst ist jedoch enthalten.

## Home/App-Wechsel

Beim Verlassen der App pausiert 86Box automatisch. Beim Zurueckkehren wird nur eine von Android gesetzte Hintergrundpause geloest. Eine vorher manuell gesetzte Pause bleibt erhalten.

## ROMs

Lege einen rechtmaessig verwendeten 86Box-ROM-Satz vor dem Build unter

`android/app/src/main/assets/86box/roms/`

ab. Beim ersten Start werden die eingebetteten Dateien in den privaten App-Speicher kopiert.

## Warum nur 64 Bit?

86Box 7.0 verweigert 32-Bit-Host-Builds. Deshalb enthaelt die Universal-APK `arm64-v8a + x86_64`, nicht `armeabi-v7a` oder 32-Bit-x86.

## Bekannte Testgrenze

Dieses Quell-/Buildpaket wurde strukturell, per CMake, Preprocessor, JNI-Abgleich und statischer Analyse geprueft. In der Chat-Ausfuehrungsumgebung ist kein Android SDK/NDK installiert; deshalb kann hier keine echte Gradle/NDK-APK erzeugt oder auf einem Android-Geraet gestartet werden. Der reale APK-Build erfolgt ueber `BUILD_APK.bat` auf dem Windows-PC mit Android SDK/NDK.

## Audit-Fixes v0.5.2

v0.5.2 haertet Desktop-Profilpfade, CUE/BIN-Import, Profil-/Diagnoselog-Lifecycle, den Stage-2-Profiler und die IDE/SCSI-Worker. Zusaetzlich wurden im CD-ROM-Core feste Puffer im CUE/TOC-Parser begrenzt und malformed Disc-Images gegen OOB/NULL-Zugriffe abgesichert. Details: `ANDROID_V0.5.2_AUDIT_FIXES_DE.md`.


## Finaler Release-Check v0.8.0

v0.8.0 ist der vor dem ersten echten Android-Build erneut gepruefte Release-Kandidat. Zusaetzlich zu den v0.5.2-Audit-Fixes wurden zwei MDS/MDF-Ressourcenlecks geschlossen, die letzten bekannten Clang-Analyzer-Hinweise im CD-ROM-Pfad beseitigt, versteckte Profilordner durch Namen mit fuehrendem Punkt verhindert und laufende Profil-/Diagnose-Exporte gegen Activity-Neuerstellung abgesichert. Details: `ANDROID_V0.5.6_FINAL_CHECK_DE.md`.

## Zweiter Release-Check v0.8.0

Vor dem ersten echten APK-Build wurde ein weiterer unabhängiger Audit-Durchlauf durchgeführt. Dabei wurden Hardware-Hotkey-Key-Up, alte ANSI/Windows-1252 Desktop-Konfigurationen, CUE-BOM/Encoding, Import-Staging-Cleanup, ROM/Asset-Extraktion sowie SDK/JDK-Buildprüfungen weiter gehärtet. Details stehen in `ANDROID_V0.5.6_SECOND_RELEASE_CHECK_DE.md` im Projektstamm.


## v0.8.0 – NDK-Pfad ohne Leerzeichen

Bei Windows-Benutzerpfaden mit Leerzeichen kopiert das Build Center den NDK einmalig physisch nach `\86BoxAndroidToolchains\ndk-28.2.13676358` auf das Projektlaufwerk. Das verhindert Autotools/vcpkg-Fehler durch kanonisierte 8.3- oder Junction-Pfade.


## v0.8.0 – Dynamischer 86Box-Hardwarekatalog

Der Android-Hardwarekonfigurator liest die Hardwarelisten jetzt direkt aus dem kompilierten 86Box-Core.
Dadurch werden Mainboards, passende CPU-Familien/Frequenzen/FPU sowie kompatible Grafik-, Sound-,
Netzwerk-, Maus- und Storage-Controller nicht mehr als kleine feste Java-Liste gepflegt.

Enthaltene Bereiche: Machine type, Motherboard, CPU, FPU, RAM, Wait states, PIT, Dynarec,
Video 1/2, Voodoo/8514/XGA/DA2, Maus, Sound 1-4, MIDI Out, Netzwerk 1-4 + SLiRP,
FDC, CD-ROM-Interface, HDC 1-4 und SCSI 1-4.

Die ROM-Dateien bleiben getrennt. Der Katalog zeigt die im Core kompilierte Hardware; ein fehlendes
BIOS/ROM wird beim VM-Start von 86Box gemeldet. Offizieller ROM-Bestand: https://github.com/86box/roms

## ROM ZIP Verwaltung (v0.8.0)
- globale ROM-Bibliothek unter `files/86box/roms`
- `IMPORT ROM ZIP` akzeptiert auch GitHub-Download-ZIPs aus `86box/roms`
- Wrapper-Ordner wie `roms-main/` / `roms-master/` werden automatisch erkannt
- Import aktualisiert gleiche ROM-Pfade, ohne andere vorhandene ROMs zu löschen
- `EXPORT ROMS` erzeugt ein wieder importierbares ZIP mit `roms/` als Wurzel
- sicherer ZIP-Import mit Path-Traversal- und Größenprüfung
