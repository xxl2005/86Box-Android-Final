# 86Box 7.0 – statischer Bug-Audit und Fix-Bericht

**Audit-Datum:** 23. September 2026  
**Quelle:** vom Benutzer bereitgestelltes `86Box-master.zip`  
**Projektstand:** 86Box 7.0 (laut `CMakeLists.txt`)  
**Ergebnis:** 15 Quelldateien geändert; 30 gruppierte, konkret nachvollziehbare Fehler-/Safety-Pfade korrigiert.

## Kurzfazit

Der Audit konzentrierte sich auf reproduzierbare Kontrollflussfehler, Speicher-/Pufferfehler, Null-/Leerstring-Randfälle, Pfadverarbeitung, Kommandozeilenargumente, Konfigurationsmigration, SDL-Monitorbefehle, Vulkan-Geräteauswahl sowie zentrale Logging-/INI-Routinen. Änderungen wurden nur vorgenommen, wenn der Fehler aus dem Quellcode eindeutig belegbar war. Rein stilistische Auffälligkeiten oder nicht bestätigte Verdachtsstellen wurden nicht verändert.

Die geänderten C-Kerndateien wurden mit GCC (`-fsyntax-only`) geprüft. Für die relevanten C-Dateien wurde zusätzlich Clang Static Analyzer ausgeführt. Die geprüften Fix-Dateien bestehen diese Prüfungen. Ein vollständiger 86Box-Build und Laufzeittest war in der bereitgestellten Linux-Analyseumgebung nicht möglich, weil Entwicklungsabhängigkeiten fehlen (Freetype; außerdem Qt6/Vulkan/SDL-Pakete). CMake erreichte den Dependency-Check und brach ausschließlich wegen fehlendem Freetype ab.

## Bestätigte und korrigierte Befunde

| ID | Bereich / Datei | Befund | Mögliche Auswirkung | Korrektur |
|---|---|---|---|---|
| F01 | `src/unix/CMakeLists.txt` | SDL2-Zweig prüfte versehentlich `SDL3::SDL3`, bevor `SDL2::SDL2` gelinkt wurde. | SDL2-Build kann falsch aufgelöst/übersprungen werden. | Target-Prüfung auf `SDL2::SDL2` korrigiert. |
| F02 | `src/device.c` | S3 Trio3D/ViRGE-Migration verglich den noch leeren `bios_name`-Puffer mit `"Generic"`. | Spezialmigration war unerreichbar. | Vergleich auf den tatsächlichen `bios`-Parameter umgestellt; NULL-Schutz ergänzt. |
| F03 | `src/device.c` | Nach spezieller Tseng-Migration lief durch ein separates `if` zusätzlich die generische Migration. | Konfigurationssektion konnte doppelt/falsch migriert werden. | Zweiten Zweig zu `else if` gemacht. |
| F04 | `src/device.c` | Temporäre `device_t`-Kopie wurde bei zwei frühen Rückgaben nicht freigegeben. | Speicherleck bei Duplikat-/Limitpfaden. | `free()` vor beiden frühen Returns ergänzt. |
| F05 | `src/unix/sdl_plat_unix.c` | `tm_year` und `tm_mon` wurden direkt als Kalenderjahr/-monat verwendet. | Falsche Temp-Dateinamen, z. B. Jahr 126 und Monat 8 für September 2026. | `+1900` bzw. `+1`, plus NULL-Prüfung für `localtime`. |
| F06 | `src/unix/sdl_plat_unix.c` | Temp-/Datenpfade konnten ohne sichere Terminierung bzw. ohne Platz für Slash+NUL weitergereicht werden. | Out-of-bounds-Lesen/Schreiben bei langen Pfaden. | Gemeinsame, begrenzte Verzeichniskopie mit reserviertem Slash+NUL. |
| F07 | `src/unix/sdl_plat.c` | `path_slash()`/`path_get_slash()` indexierten `strlen(path)-1` auch bei NULL/leer. | Unterlauf/ungültiger Speicherzugriff. | NULL-/Leerstring-Guards ergänzt. |
| F08 | `src/unix/sdl_plat.c` | `SDL_GetBasePath()` wurde ohne NULL-/Leerprüfung verwendet. | Crash bei fehlgeschlagener SDL-Pfadabfrage. | Fallback auf Emulatornamen und Größenprüfung ergänzt. |
| F09 | `src/qt/qt_platform.cpp` | Qt-Pfadhelfer hatten denselben Leerstring-Unterlauf und konnten bei exakt vollem Puffer noch `/` anhängen. | Out-of-bounds-Zugriff. | Guards plus `qt_copy_dir_path()` mit Platz für Slash+NUL. |
| F10 | `src/unix/sdl_monitor.c` | Medienpfadpuffer (u. a. `cdload`) waren teilweise nicht initialisiert; Parser nutzte anschließend `strlen/strcat`. | Unbestimmtes Verhalten/Crash. | Puffer initialisiert und Parser neu begrenzt. |
| F11 | `src/unix/sdl_monitor.c` | Bei „Pfad zu lang“ wurde der Pfad trotz Erkennung weiter per `strcat` angehängt. | Echter Stack-Pufferüberlauf möglich. | Bounded Parsing via Längenprüfung/`memcpy`; überlange Pfade werden abgelehnt. |
| F12 | `src/unix/sdl_monitor.c` | Laufwerksnummern wurden ungeprüft direkt als Arrayindex verwendet. | OOB-Zugriff z. B. mit `cdeject 250`. | Strikte Bereichsprüfung je Gerätetyp (CD, Floppy, MO, RDisk, Tape, Cartridge). |
| F13 | `src/unix/sdl_monitor.c` | Write-Protect-/Zahlenargumente akzeptierten unvalidierte Werte. | Ungültige Zustände/unerwartete Indizes. | `strtol`-basierte vollständige Zahlenvalidierung; WP auf 0/1 begrenzt. |
| F14 | `src/unix/sdl_monitor.c` | Rückgabewert von `strdup()` wurde vor NULL-Prüfung benutzt. | NULL-Dereferenz bei Allocation-Fehler. | NULL-Prüfung vor Stringzugriff. |
| F15 | `src/utils/ini.c` | Sektion-/Entry-Namen wurden ohne sichere Begrenzung in 128-Byte-Puffer kopiert. | Pufferüberlauf bzw. fehlendes NUL. | Begrenzte Kopie, Terminierung, Allocation-/NULL-Prüfungen. |
| F16 | `src/utils/ini.c` | Lange Stringwerte konnten ohne NUL enden und danach konvertiert werden. | Out-of-bounds-Lesen bei Charset-Konvertierung. | Begrenzte Kopie + erzwungenes NUL für char/wchar. |
| F17 | `src/utils/ini.c` | `memcpy(ent->wdata, ..., sizeof_w(...))` verwendete Elementzahl als Bytezahl und konnte Quelle falsch lesen. | Falsche Kopiergröße, fehlende Terminierung, potenzielles OOB-Lesen. | `wcsncpy` mit Elementgrenze und NUL. |
| F18 | `src/utils/ini.c` | Quote-Stripping indexierte `strlen()-1`/`wcslen()-1` bei leeren Strings und mischte UTF-8-Byteoffset mit wchar-Index. | Unterlauf und falsches Abschneiden. | Separate char/wchar-Suche, Längenguards und sichere Terminierung. |
| F19 | `src/mem/rom.c` | `rom_check()` nutzte `fn[strlen(fn)-1]` ohne NULL-/Leerprüfung. | Unterlauf/Crash bei leerem Pfad. | Frühzeitige NULL-/Leerprüfung. |
| F20 | `src/qt/qt_vulkanwindowrenderer.cpp` | Zähler für `vkEnumeratePhysicalDevices` war uninitialisiert. | Undefiniertes Verhalten bei Fehlerpfad. | Initialisierung auf 0 + Rückgabecode-/Zero-Device-Behandlung. |
| F21 | `src/qt/qt_vulkanwindowrenderer.cpp` | Queue-Indizes wurden zwischen GPU-Kandidaten nicht zurückgesetzt; Graphics-Fallback schrieb außerdem in `present_queue` statt `gfx_queue`. | Falsche Queue-Auswahl auf Multi-GPU-/Queue-Family-Systemen. | Indizes pro Kandidat resetten; richtige Variable gesetzt. |
| F22 | `src/qt/qt_vulkanwindowrenderer.cpp` | Negativer gespeicherter GPU-Index wurde vor `clamp` zu unsigned gecastet. | Auswahl konnte auf letztes Gerät springen. | Clamp im signed Integer-Bereich. |
| F23 | `src/qt/qt_vulkanwindowrenderer.cpp` | Wenn die konfigurierte GPU ungeeignet war, wurde eine brauchbare Ersatz-GPU gefunden, danach aber trotzdem immer „No usable...“ geworfen. | Vulkan konnte auf Multi-GPU-Systemen fälschlich komplett scheitern. | Erfolg des Fallbacks wird verfolgt; Exception nur, wenn wirklich keine GPU geeignet ist. |
| F24 | `src/disk/mo.c` | Länge eines neuen Imagepfads wurde aus `strlen(alter Zielpfad)-1` berechnet. | Bei leerem Ziel unsigned Unterlauf; Pfadgrenze wirkungslos/Abschneiden. | Länge aus Quellpfad und echter Zielpuffergröße berechnet. |
| F25 | `src/disk/rdisk.c` | Gleicher Zielpfad-Längenfehler wie bei MO. | Unterlauf/fehlerhafte Pfadkopie. | Sichere Quelllänge, `memmove`, explizites NUL. |
| F26 | `src/qt/qt_vmmanager_system.cpp` | Zeiger auf `search_directory.toUtf8().data()` überlebte das temporäre `QByteArray`; zusätzlich `strlen()-1` bei leerem String. | Dangling Pointer/Unterlauf. | Direkte `QString::endsWith()`-Prüfung ohne temporären Rohzeiger. |
| F27 | `src/utils/log.c` + `src/86box.c` | Mehrere zentrale Logger nutzten `vsprintf` auf feste Puffer. | Formatierte Logausgabe konnte Stack-/Heap-Puffer überschreiben. | Durch `vsnprintf` mit echter Puffergröße ersetzt. |
| F28 | `src/utils/log.c` + `src/86box.c` | Logger-Gerätenamen, `--logfile`, `--vmname`, `--vmmpath` und ein früher `pclog_ex`-Return waren unzureichend begrenzt bzw. leakten. | Überlauf, fehlendes NUL, Speicherleck. | Begrenzte Kopien, NUL-Terminierung, Allocation-Prüfungen; Leak beseitigt. |
| F29 | `src/86box.c` | Benutzerkontrollierte Daten-/ROM-/Asset-/Config-Pfade wurden per `strcpy/strcat` in feste 1024-Byte-Puffer übernommen; `--lang` konnte ohne Folgeargument lesen; Windows-`--hwnd` griff auf feste Positionen ohne Formatprüfung zu. | Stack/Global-Pufferüberlauf bzw. OOB-Lesen/-Schreiben bei fehlerhaften CLI-Argumenten. | Gemeinsame Größenprüfung, Platz für Slash+NUL reserviert, fehlende Argumentprüfung, strikte `--hwnd`-Syntax. |
| F30 | `src/floppy/fdd_86f.c` | Bis zu 4096 Zeichen langer Imagepfad wurde per `memcpy(strlen+1)` in 2048-Byte-Puffer kopiert. | Pufferüberlauf im komprimierten 86F-Pfad. | Auf Zielgröße begrenzt und explizit terminiert. |

## Geänderte Dateien

1. `src/86box.c`
2. `src/device.c`
3. `src/disk/mo.c`
4. `src/disk/rdisk.c`
5. `src/floppy/fdd_86f.c`
6. `src/mem/rom.c`
7. `src/qt/qt_platform.cpp`
8. `src/qt/qt_vmmanager_system.cpp`
9. `src/qt/qt_vulkanwindowrenderer.cpp`
10. `src/unix/CMakeLists.txt`
11. `src/unix/sdl_monitor.c`
12. `src/unix/sdl_plat.c`
13. `src/unix/sdl_plat_unix.c`
14. `src/utils/ini.c`
15. `src/utils/log.c`

Quellcode-Diff (ohne diesen Bericht): ungefähr **426 Einfügungen und 175 Entfernungen**.

## Validierung

### Erfolgreich

- `src/86box.c`: GCC Syntax PASS; Clang Static Analyzer PASS.
- `src/device.c`: GCC Syntax PASS; Clang Static Analyzer PASS.
- `src/utils/ini.c`: GCC Syntax PASS; Clang Static Analyzer PASS.
- `src/utils/log.c`: GCC Syntax PASS; Clang Static Analyzer PASS.
- `src/mem/rom.c`: GCC Syntax PASS; Clang Static Analyzer PASS.
- `src/disk/mo.c`: GCC Syntax PASS; Clang Static Analyzer PASS.
- `src/disk/rdisk.c`: GCC Syntax PASS; Clang Static Analyzer PASS.
- `src/unix/sdl_monitor.c`: GCC Syntax PASS; Clang Static Analyzer PASS.
- `src/floppy/fdd_86f.c`: GCC Syntax PASS, auch mit `D86F_COMPRESS` aktiviert.
- Projektweiter Hochrisiko-Scan der bearbeiteten Dateien: keine verbliebenen `vsprintf()`-Aufrufe; die gezielt entfernten unbeschränkten CLI-Pfadkopien sind nicht mehr vorhanden.

### Nicht vollständig ausführbar in dieser Umgebung

CMake konnte Compiler/Features erkennen, stoppte aber an fehlenden Entwicklungsabhängigkeiten:

- Freetype: fehlt (erste harte CMake-Fehlermeldung)
- Qt6 Development: fehlt
- Vulkan Development: fehlt
- SDL2/SDL3 Development: fehlt

Deshalb konnten die Qt-/Vulkan-/SDL-Plattformteile hier nicht als vollständiges 86Box-Binary gelinkt und anschließend zur Laufzeit getestet werden. Das ist eine Einschränkung der Analyseumgebung und kein gemeldeter Source-Buildfehler des gepatchten Baums.

Beim Clang-Analyzer für `fdd_86f.c` wurde ein bereits vorhandener möglicher Modulo-/Division-durch-Null-Pfad über Funktionszeiger gemeldet. Die tatsächlichen Aufrufer von `d86f_prepare_pretrack()` verwenden in diesem Pfad die positive `common_get_raw_size()`-Berechnung; der Befund ließ sich daher nicht als realer Laufzeitfehler bestätigen und wurde bewusst **nicht** verändert.

## Nicht als Bug behandelte Verdachtsstellen

- Die zunächst auffällige `gfxcard_2`-Schleife ist im aktuellen Stand kein Mehrslot-Fehler, da `GFXCARD_MAX` 2 ist.
- Ein zunächst vermuteter früher `return` in der Vulkan-Shader-Recreation entstand nur durch gekürzte Kontextausgabe; der vollständige Kontrollfluss ist korrekt.
- Weitere ältere APIs wie `path_append_filename()` besitzen keine Zielgrößenangabe. Eine vollständige API-Neugestaltung wäre ein separates Refactoring und wurde nicht in diesen minimalen Fix-Satz aufgenommen.

## Einschätzung für die nächsten Schritte

Für eine verbesserte Windows-Version ist dieser Baum eine gute Basis; als nächstes sollte ein vollständiger MSVC/MinGW-Build mit Qt/Vulkan/SDL-Abhängigkeiten sowie Laufzeittests für Vulkan-Multi-GPU, Medienwechsel und Konfigurationsmigration folgen.

Für Android/ARM64 sind bereits entscheidende Grundlagen vorhanden: ARM64 wird im CMake erkannt, der neue ARM64-Dynarec ist vorhanden, SDL ist als alternative Plattformschicht vorhanden und Vulkan existiert bereits als Renderer. Der eigentliche Android-Port benötigt vor allem Android-Lifecycle/Activity, Dateizugriff/SAF, Touch-/Gamepad-Eingabe, Audio, Packaging sowie eine Prüfung des Dynarec-Speichermodells (W^X/RWX/JIT-Regeln auf Android).

## Audit-Grenze

Dieser Bericht ist ein umfangreicher statischer Audit mit gezielten Compiler-/Analyzer-Prüfungen, **keine mathematische Garantie, dass der gesamte 86Box-Code fehlerfrei ist**. Der Quellbaum umfasst ungefähr eine Million Zeilen einschließlich eingebundener Bibliotheken. Für höhere Sicherheit wären zusätzlich vollständige Builds auf Windows/Linux/macOS, Sanitizer-Läufe (ASan/UBSan), automatisierte Medien-/Geräte-Regressionstests und Vulkan-Tests auf mehreren GPUs/Treibern erforderlich.

## v0.9.14 Performance Auto-Tuning
- Universal-ISA beibehalten; keine Oryon-only Instruktionen im gemeinsamen ARM64-Binary.
- Host-Erkennung fuer Qualcomm/Oryon, MediaTek, RK3588, Generic ARM64 und x86_64.
- Auto/Balanced/Performance/Maximum inklusive sicherem Fallback.
- ARM64 CPU-Thread-Affinity anhand cpu_capacity/max_freq; urspruengliche Maske wird fuer Balanced restauriert.
- Android Release ThinLTO aktiviert.
- Performance/Maximum bevorzugen 60-Hz-Hostausgabe fuer thermischen Headroom.

## v0.9.15 – Android NDK Affinity Build-Fix

- Reproduzierter v0.9.14-Fehler: `cpu_set_t`, `CPU_*`, `sched_getaffinity()` und `sched_setaffinity()` waren im Android-NDK-Build nicht deklariert.
- Fix: eigene CPU-Bitmaske + direkte `SYS_sched_getaffinity`/`SYS_sched_setaffinity`-Systemaufrufe.
- Runde 1: Affinity-Helfer mit Clang `-std=gnu11 -Wall -Wextra -Werror` kompiliert und Smoke-Test bestanden.
- Runde 2: Diff zu v0.9.14 geprüft; Touch, physische Maus, CD/Lifecycle und Performance-Tuning erhalten; Version 0.9.15 / Code 50 konsistent.
- Runde 3: ZIP-/Namens-/0-Byte-/Versionsintegrität geprüft.


## v0.9.16 – Overlay/Performance Polish
- `IMPORT DESKTOP PROFILE` UI-Text.
- Frei verschiebbare Emulations-%-Anzeige mit Größe/Deckkraft/Reset; Standardgröße 90 %.
- Overlay-Polling 1 s.
- Maximum CPU-Priorität -10 mit bestehendem Fallback.
- Drei Audit-Runden siehe `ANDROID_BUILD_VALIDATION_v0.9.16.txt`.

## v0.9.17 – Manuelle Host-CPU-Kernauswahl

- BOOST-Menü um `MANUAL CORES…` erweitert.
- Bis zu 64 Host-Kerne einzeln wählbar; Frequenz/cpu_capacity werden angezeigt, schnellste Klasse mit ★ markiert.
- Manuelle Maske wird gespeichert und mit Androids erlaubter Affinity geschnitten.
- Ungültige/leere Zielmaske fällt sicher auf Auto zurück; Balanced restauriert die Original-Affinity.
- Generation-Counter wendet Kernwechsel auch ohne Performance-Moduswechsel laufend neu an.
- Drei Prüf-Runden siehe `ANDROID_BUILD_VALIDATION_v0.9.17.txt`.


## v0.9.18 – Speed-Overlay Standardwerte
- Standardgröße von 90 % auf 60 % geändert.
- Standard-Hintergrunddeckkraft von 60 % auf 30 % geändert.
- Bestehende gespeicherte Benutzereinstellungen werden weiterhin respektiert.
- Manuelle Kernwahl, Touch, physische Maus, CD-Keepalive und Performance-Tuning unverändert.
- Drei Prüf-Runden siehe `ANDROID_BUILD_VALIDATION_v0.9.18.txt`.

## v0.9.19 – Host-Performance-Fenster scrollbar

- Das komplette Host-Performance-Fenster wurde in eine ScrollView verschoben.
- Host-Erkennung, MANUAL CORES, Hinweistext sowie Auto/Balanced/Performance/Maximum sind gemeinsam scrollbar.
- Native Performance-/Affinity-, Touch-, Maus- und CD-Keepalive-Pfade bleiben unverändert.
- Drei Prüf-Runden siehe `ANDROID_BUILD_VALIDATION_v0.9.19.txt`.


## v0.9.20 – Performance-Diagnose / ADPF / Voodoo
- ADPF fuer nativen CPU-Thread, native Scheduler-State via JNI und garantierter native-performance.log.
- Thermal/CPU-Headroom-Diagnose und Speed min/avg/max.
- Voodoo Render Threads Auto/1/2/4.
- Version 0.9.20 / Code 55.


## v0.9.21 – Langzeit-Frequenzmonitor

- Live-Core und Live-Frequenz des nativen CPU-Threads.
- All-Core-Frequenzsnapshot alle 10 Sekunden.
- 10/30/60/120-Sekunden-Trends und automatische Drop-Klassifikation.
- Version 0.9.21 / Code 56.

## v0.9.22 – Adaptive Anti-Throttle + virtuelle PC-Tastatur

### Prüfrunde 1
- Java-Struktur von Box86Activity, HostPerformanceTuner und VirtualPcKeyboardView geprüft.
- Adaptive-Hysterese simuliert: <95 %, >=15 % Taktvorteil, 8 s stabil, 20 s Cooldown.
- Virtuelle PC-Tastatur: direkter XT-Scancode-Pfad, Key-Up bei ACTION_UP/ACTION_CANCEL, Modifier-Release bei Pause/Schließen, DE-QWERTZ/F1-F12/Navigation/Numpad geprüft.

### Prüfrunde 2
- Native Affinity/ADPF (`android_bridge.c`) und CPU-Loop (`sdl_main.c`) bytegleich zu v0.9.21.
- VmKeepAliveService und DiagnosticLogger bytegleich.
- dispatchKeyEvent, dispatchGenericMotionEvent, Medien-Picker, Pointer-Capture und Hardware-Input-Release unverändert.
- Langzeitmonitor läuft auch bei ausgeblendetem Speed-Overlay weiter.

### Prüfrunde 3
- Versionskonsistenz: v0.9.22 / versionCode 57.
- Änderungsumfang auf HostPerformanceTuner, Box86Activity, neue VirtualPcKeyboardView, Versionierung und v0.9.22-Doku begrenzt.
- Keine 0-Byte-Quell-/Builddateien, keine case-insensitiven Pfadkollisionen.

## v0.9.23 – Experimental Multi-Thread Assist + dunkle virtuelle Tastatur

### Runde 1 – Struktur und Thread-Verteilung
- Java/C-Delimiter-Prüfung für die geänderten Dateien bestanden.
- Host-Thread-Verteilungs-Smoke-Test mit OnePlus-ähnlicher 8-Core-Topologie bestanden: CPU6/7 reserviert, Helper round-robin auf CPU0–5. Die echte Auswahl nutzt Live-Frequenzen mit Capacity/Max-Fallback.
- Volle Host-Affinity wird vor CPU-Pinning erfasst, damit neue pthreads nicht auf der geerbten CPU6/7-Maske gefangen bleiben.
- Virtuelle Tastatur: normale Tasten #202020, Control-Tasten #292929, separate Pressed/Latched-Zustände.

### Runde 2 – Regression
- HostPerformanceTuner, VmKeepAliveService, DiagnosticLogger, 86box.c, Voodoo-Core und Sound-Core unverändert.
- Bestehende CPU-Tuning-Funktion in android_bridge.c unverändert.
- Touch/physische Maus/CD-/Lifecycle-Bereich in Box86Activity nach dem Profiler-Marker unverändert.
- Voodoo-Renderthreads sowie CD/FDD/HDD-Soundworker verwenden den registrierten thread_create-Pfad.

### Runde 3 – Paket
- Version 0.9.23 / versionCode 58.
- Keine leeren Quell-/Builddateien.
- Keine case-insensitiven Pfadkollisionen.
- ZIP-Integrität geprüft.

## v0.9.24 – Auto (Android/ADPF decides) als Core-Strategy-Standard

- Prüfung 1: Default-/Upgrade-Migration und Java-Struktur bestanden. Fresh/default -> Auto; alter Adaptive-Standard -> Auto; Prime/Big/Manual bleiben erhalten.
- Prüfung 2: Native Bridge, SDL, HostPerformanceTuner, VirtualPcKeyboardView, VmKeepAliveService und DiagnosticLogger bytegleich zu v0.9.23; zentrale Touch/Maus/CD/ADPF/Thread-Assist-Marker vorhanden.
- Prüfung 3: Version 0.9.24 / Code 59 konsistent; keine leeren Quell-/Builddateien; keine case-insensitiven Pfadkollisionen.


## v0.9.25 - CPPTHREADS Thread Assist + Game Key Overlay

### Runde 1
- Android nutzt `CPPTHREADS=ON`; Registrierung wurde in den real verwendeten `src/thread.cpp`-Pfad aufgenommen.
- `thread.cpp` mit Clang++ Syntaxcheck bestanden.
- FloatingKeyOverlayView und Box86Activity strukturell auf Klammern/Parenthesen, HOLD-/Cancel-/Release-Pfade geprueft.

### Runde 2
- Kritische v0.9.24-Dateien fuer native Affinity/ADPF, SDL, physische Maus, Keepalive, Logger und Volltastatur bytegleich geblieben.
- HOLD-State-Modell geprueft: erster Tap = Key-Down bleibt aktiv; Maus kann danach in neuer Touch-Geste bedient werden; zweiter Tap = Key-Up.
- Aenderungsumfang auf thread.cpp, Box86Activity, neue FloatingKeyOverlayView, Versionierung und Doku begrenzt.

### Runde 3
- Voodoo-Renderthreads (1-4), FIFO, Audio- und weitere Helper laufen ueber `thread_create` -> `thread_create_named` und werden dadurch nun in `thread.cpp` registriert.
- Version 0.9.25 / versionCode 60 konsistent.
- Keine case-insensitiven Pfadkollisionen und keine leeren Quell-/Builddateien.
- ZIP-Integritaet abschliessend geprueft.

## v0.9.26 - Game Key Overlay Contrast Fix

- GAME KEY OVERLAY Dialog explizit dunkel (#151515), inklusive Custom Title und Scroll-Inhalt.
- Key-/HOLD-/MOVE-Texte explizit hell und Checkbox-Tints kontrastreich.
- In-Game Overlay-Tasten nutzen eigene dunkle StateListDrawable-Hintergruende, unabhaengig vom Android Button-Theme.
- HOLD/latched, MOVE MODE, Touch-Maus und Key-Up-Sicherheit unveraendert.
- Kritische Runtime-Dateien (thread.cpp, android_bridge.c, HostPerformanceTuner, Box86Activity, KeepAlive, VirtualPcKeyboardView) bytegleich zu v0.9.25.
- Version 0.9.26 / versionCode 61.


## v0.9.27 - Virtual Keyboard Screen Fit + Scaler

- Runde 1: Java-Struktur, 50-140%-Scaler, persistente Speicherung, Auto-Fit und Key-Up-Sicherheit geprüft.
- Runde 2: Thread Assist, ADPF/Affinity, Game-Key-Overlay, physische Maus, Touch, CD-Keepalive und Logger gegen v0.9.26 geprüft; kritische Dateien bytegleich.
- Runde 3: Versions-/Paketintegrität, 0-Byte-Quell-/Builddateien, case-insensitive Pfadkollisionen und ZIP-CRC geprüft.
- Version: 0.9.27 / versionCode 62.


## v0.9.28 - True Auto / ADPF + Thread Assist v2

- Version: 0.9.28 / versionCode 63.
- Core Strategy Auto setzt keine 2-Core-Affinity mehr; native Performance/Maximum-Prioritaet und ADPF bleiben aktiv.
- Native Diagnose erwartet bei Auto: `affinity=android/adpf allowed=N`.
- Thread Assist v2 verwendet eine gemeinsame Helper-CPU-Maske statt One-Thread-One-Core-Pinning.
- Auto/8-Core-Oryon-Simulation: CPU6/7 reservierter Scheduler-Headroom, gemeinsamer Helper-Pool CPU0-5.
- Separate C-Smoke-Pruefung der Helper-Pool-Logik mit clang `-Wall -Wextra -Werror` bestanden.
- Regressionen: virtuelle Tastatur/Scaler, Floating Game Key Overlay, ADPF-Tuner, Keepalive, Logger, CPPTHREADS-Registrierung, SDL-Hauptloop und Voodoo-Core unveraendert.
- Drei Audit-Runden durchgefuehrt.

## v0.9.29 - Game Key Overlay Transparency

### Runde 1
- GAME KEY OVERLAY besitzt einen gespeicherten Hintergrund-Opacity-Regler von 0-100 %.
- 0 % blendet nur den Tastenhintergrund aus; Text bleibt voll sichtbar.
- Ein dünner Rand bleibt bei 0 % sichtbar; HOLD/latched wird zusätzlich über türkisfarbene Beschriftung kenntlich gemacht.
- Java-Delimiter-/Strukturprüfung bestanden.

### Runde 2
- Box86Activity, VirtualPcKeyboardView, HostPerformanceTuner, VmKeepAliveService, DiagnosticLogger, android_bridge.c, thread.cpp und sdl_main.c bytegleich zu v0.9.28.
- Änderungsumfang vor dem Audit-Report auf FloatingKeyOverlayView, Versionierung und v0.9.29-Doku begrenzt.

### Runde 3
- Version 0.9.29 / versionCode 64 konsistent.
- Keine leeren Quell-/Builddateien.
- Keine case-insensitiven Pfadkollisionen.
- ZIP-Integrität geprüft.


## v0.9.31 – DOSBox Pure Adaptive CPU Smooth v2
- Runde 1: Controller-Modell + Clang-Syntaxcheck des geaenderten android_bridge-Prefix bestanden.
- Runde 2: Regression gegen v0.9.30 bestanden; CPU-Loop, Thread Assist, SDL, Voodoo, Touch/Maus, Tastaturen und Keepalive bytegleich.
- Runde 3: Paket-/Versions-/Pfad-/ZIP-Pruefung vor Release.
