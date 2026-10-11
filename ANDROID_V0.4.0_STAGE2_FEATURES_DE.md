# 86Box Android v0.4.0 – Multi-Core Stage 2

v0.4.0 baut direkt auf v0.3.1 auf. Alle bisherigen Android-Funktionen bleiben enthalten; neu sind ein sicher ueberlappender SCSI-Read-Pfad und ein interner Hostzeit-Profiler.

## 1. SCSI-Read-Overlap

Bei echten SCSI-Festplatten werden die Befehle READ(6), READ(10) und READ(12) nicht mehr zwingend komplett auf dem Emulator-Hauptthread vom Host-Datentraeger gelesen.

- pro SCSI-Festplatte kann ein eigener `scsi-read` Worker aktiv werden;
- der Worker liest die angeforderten 512-Byte-Sektoren in den bereits vorhandenen 86Box-Datenpuffer;
- die emulierte Controller-/Seek-/Transferzeit laeuft waehrenddessen weiter;
- jeder unterstuetzte SCSI-Controller ruft unmittelbar vor seinem ersten DATA-IN-Zugriff `scsi_device_wait_ready()` auf;
- ist der Host-Read bereits fertig, entsteht praktisch keine zusaetzliche Blockierzeit;
- ist er noch nicht fertig, wartet der Controller nur die Restzeit;
- Host-Lesefehler werden weiterhin als SCSI CHECK CONDITION an den Gast weitergegeben;
- Reset/Close/Abort verbrauchen ausstehende Requests vor dem Freigeben des Puffers.

Eingebundene Controllerpfade: generischer SCSI-Bus, x54x, AIC7xxx, NCR53C8xx, PC-SCSI/ESP, AHA-1740, BusLogic, QLogic und IBM Spock.

SCSI-Writes bleiben in Stage 2 absichtlich synchron. Ein simples Write-Behind koennte sonst dem Gast einen erfolgreichen Status melden, bevor der Host-Write wirklich abgeschlossen oder ein Fehler bekannt ist.

CMake: `ASYNC_SCSI_READS=ON` (Standard). Mit `OFF` bleibt das bisherige synchrone Verhalten erhalten.

## 2. Interner Performance-Profiler

Der Profiler misst Host-Wallclock-Zeit in Mikrosekunden und benutzt atomare Zaehler, damit Worker-Threads gemeinsam erfasst werden koennen.

Kategorien:

- `CPU/core`: Ausfuehrungszeit des emulierten CPU-Blocks (`cpu_exec`)
- `I/O+chipset`: Zeit in den zentralen IN/OUT-Porthandlern
- `graphics`: Zeit des Video-Blit-Workers
- `sound`: Zeit in `sound_poll` / Mixing
- `storage`: reale HDD-Image Reads/Writes, einschliesslich IDE-/SCSI-Worker
- `SCSI wait`: Restzeit, die ein Controller trotz SCSI-Read-Overlap noch blockiert

Die Kategorien sind absichtlich nicht additiv. Sound oder I/O koennen innerhalb eines CPU-Blocks stattfinden; Grafik und Storage koennen gleichzeitig in anderen Host-Threads laufen. Darum koennen die angezeigten Prozentwerte zusammen ueber 100 % liegen. Genau das macht echte Parallelitaet sichtbar.

CMake: `PERF_PROFILE=OFF` bleibt fuer normale Desktop-Builds Standard. Der Android-Build aktiviert es automatisch. Desktop kann mit `-DPERF_PROFILE=ON` gebaut werden; `86BOX_PERF_PROFILE=1` aktiviert die Messung zur Laufzeit und schreibt alle 5 Sekunden eine Zusammenfassung ins Log.

## 3. Android PERF-Button

Die Android-Toolbar hat jetzt `PERF`:

1. erster Tap: Zaehler zuruecksetzen und Profiling starten;
2. 86Box normal benutzen, Spiel/Windows/DOS/Benchmark laufen lassen;
3. zweiter Tap: Messung stoppen und Zusammenfassung als Android-Dialog anzeigen.

Der Profiler veraendert keine Gast-Timingwerte; er misst nur Hostzeit. Wie jeder Profiler erzeugt er einen kleinen Mess-Overhead, besonders bei sehr vielen Port-I/O-Zugriffen. Deshalb ist er abschaltbar.

## 4. Build-Ausgabe

`android\\BUILD_APK.bat` -> `[3] DEBUG UNIVERSAL APK bauen (ARM64 + x86_64)`

Ziel: `86Box-Android-universal64-v0.4.0-debug.apk`

Enthaltene ABIs: `arm64-v8a` und `x86_64`.

## 5. Was Stage 2 nicht macht

Die emulierte x86-CPU selbst bleibt ein Hauptthread. Stage 2 versucht nicht, CPU-Instruktionen parallel auszufuehren. Das waere wegen Speicherreihenfolge, IRQs, Timern und Geraetezustand ein wesentlich groesserer Genauigkeitsumbau.

Der sinnvolle naechste Schritt nach realen Messungen ist, anhand der Profildaten gezielt weitere Host-Arbeit aus dem CPU-Thread zu verlagern, statt blind Threads hinzuzufuegen.
