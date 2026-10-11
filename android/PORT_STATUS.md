# Port Status – v0.8.0

## Fertig im Source-/Build-Paket

- [x] 86Box-Core als Android `libmain.so`
- [x] SDL3 Android Entry Point
- [x] Universal64 `arm64-v8a + x86_64`
- [x] ARM64/x86_64 New Dynarec Buildpfade
- [x] Multi-Core Stage 1 / asynchrone IDE-PIO-Writes
- [x] Multi-Core Stage 2 / asynchrone SCSI-Festplatten-Reads (READ 6/10/12)
- [x] interner Hostzeit-Profiler: CPU/Core, I/O+Chipset, Grafik, Sound, Storage, SCSI-Wait
- [x] Android `PERF`-Button fuer Start/Stop + Ergebnisdialog
- [x] OpenAL-Audio
- [x] Immersive Fullscreen
- [x] Vulkan-bevorzugter SDL3 Renderer mit Fallbackkette
- [x] Anzeige des aktiven Renderers im Android-Einstellungsdialog
- [x] Touch-Maus und virtueller Maus-Joystick
- [x] Pinch-Zoom 100–300 %
- [x] Zwei-Finger-Pan mit Rand-Clamping
- [x] automatisches Zentrieren bei 100 % Zoom
- [x] Android-IME / SDL3 UTF-8 Text Input
- [x] US-QWERTY Gast-Keymap
- [x] deutsche QWERTZ Gast-Keymap inkl. Umlauten/AltGr-Sonderzeichen
- [x] Pause/Resume und Auto-Pause bei Home/App-Wechsel
- [x] Hard Reset und 86Box SDL-OSD
- [x] Android SAF FDD/CD/HDD Import
- [x] Multi-Datei CUE/BIN CD-Import
- [x] CUE FILE-Pfad-Rewrite und Missing-Track-Pruefung
- [x] echtes libslirp/User-Mode-Netzwerk im Android-Standardbuild
- [x] libslirp fuer ARM64 und x86_64 via vcpkg
- [x] Android-private ROM-/Asset-/Temp-/Media-Pfade
- [x] physische USB/Bluetooth-Tastatur mit roher HID/XT-Scancode-Bruecke
- [x] physische Maus mit Android Pointer Capture, 5 Tasten und Mausrad
- [x] persistentes Android-/86Box-Log pro VM/Sitzung
- [x] Java-Crash-Stacktrace und nativer Crash-Signalmarker
- [x] LOG-Viewer + Diagnose-ZIP-Export ohne Medienimages

## Noch real zu testen / spaeter auszubauen

- [ ] echter Gradle/NDK-Compile auf einem Windows-PC mit installiertem Android SDK/NDK
- [ ] realer ARM64-Geraetestart und Dynarec/W^X-Test
- [ ] realer x86_64-Android-Test
- [ ] Vulkan-Laufzeittest auf mehreren GPU-Treibern
- [ ] libslirp Gast-Netzwerktest mit mehreren emulierten NICs
- [ ] Android-GUI fuer komplette Maschinen-/NIC-Konfiguration
- [ ] HDD-Konfigurations-UI statt nur sicherem Import
- [ ] optionale FluidSynth/RtMidi/MUNT/SoundCanvas/VNC-Portierung

- v0.3.0: libslirp Unix/Android pollfd capacity fix (entry count, cleanup)

- v0.3.1: physische USB/Bluetooth-Tastatur und Maus, Android Pointer Capture, Mausrad/5 Tasten, direkte HID/XT-Scancode-Bruecke und Stuck-Key-Schutz
- v0.4.0: Multi-Core Stage 2 mit SCSI-Read-Overlap und internem Performance-Profiler

- v0.5.0: VM-/Hardwareprofile, Desktop-Ordnerimport, ZIP Import/Export, Pfad-Migration, v0.4 Legacy-Migration
- v0.5.1: persistentes Diagnose-/Fehlerlog, LOG-Viewer, Diagnose-ZIP, Java-Crashlog und nativer Crash-Marker

- v0.5.2: Audit-Fixes für Profilpfade/CUE-Import, fehlgeschlagene Medienimporte, Profiler/IDE/SCSI-Threading sowie Crash-Handler-Lifecycle

- v0.8.0: finaler Release-Check: MDS/MDF-Leaks behoben, CD-ROM-Analyzer 0 Warnungen, versteckte Profilnamen verhindert und Export-Zustand ueber Activity-Neustarts erhalten.

## ROM ZIP Verwaltung (v0.8.0)
- globale ROM-Bibliothek unter `files/86box/roms`
- `IMPORT ROM ZIP` akzeptiert auch GitHub-Download-ZIPs aus `86box/roms`
- Wrapper-Ordner wie `roms-main/` / `roms-master/` werden automatisch erkannt
- Import aktualisiert gleiche ROM-Pfade, ohne andere vorhandene ROMs zu löschen
- `EXPORT ROMS` erzeugt ein wieder importierbares ZIP mit `roms/` als Wurzel
- sicherer ZIP-Import mit Path-Traversal- und Größenprüfung
