# 86Box Android v0.3.0 – Funktionsstand

Basis: 86Box 7.0 + Audit-Fixes + Multi-Core Stage 1 + Android v0.2.0 Touch-Frontend.

## Neu in v0.3.0

### 1. Vulkan bevorzugt, automatischer Renderer-Fallback

Das SDL3-Android-Frontend setzt die Renderer-Prioritaet auf:

`vulkan,gpu,opengles2,software`

Auf einem Android-Geraet mit nutzbarem Vulkan-Treiber wird damit der SDL3-Vulkan-Renderer bevorzugt. Falls er nicht initialisiert werden kann, versucht SDL automatisch die nachfolgenden Renderer. In den Touch-Einstellungen kann der aktuell aktive SDL-Renderer angezeigt/aktualisiert werden.

Dies ist der SDL3-2D-Vulkan-Pfad fuer die 86Box-Bildausgabe. Es ist nicht der Qt-Desktop-Vulkan-Renderer von 86Box.

### 2. Pinch-Zoom + Zwei-Finger-Pan

- Pinch-Zoom: 100–300 %
- Zwei Finger gemeinsam verschieben: vergroesserten Bildausschnitt verschieben
- Pan wird an die sichtbaren Bildgrenzen geklemmt, sodass kein leerer Rand eingeblendet wird
- bei Rueckkehr auf 100 % wird Pan automatisch auf die Mitte zurueckgesetzt
- `Reset zoom + pan` setzt Zoom und Position gemeinsam zurueck

### 3. Deutsche/UTF-8 Android-IME

SDL3-Textinput wird als UTF-8 gelesen und fuer den Gast in XT-Tastendruecke umgesetzt.

Eingebaut sind zwei Gast-Keymaps:

- US/QWERTY
- Deutsch/QWERTZ

Die deutsche Map unterstuetzt unter anderem:

- Y/Z-Tausch
- ae/oe/ue als `ä/ö/ü` sowie Grossbuchstaben
- `ß`, `§`, `°`, `´`, `` ` ``, `^`, `µ`, `€`
- typische AltGr-Zeichen wie `@`, `{`, `[`, `]`, `}`, `\\`, `|`, `~`
- normale Ziffern, Satzzeichen, Enter, Tab und Leerzeichen

Die Einstellung wird gespeichert. Auf einem Android-System mit deutscher Systemsprache ist die deutsche Gast-Keymap beim ersten Start standardmaessig aktiviert.

Wichtig: Ein PC-Gast erhaelt Tastatur-Scancodes, keine beliebigen Unicode-Codepoints. Zeichen, fuer die die ausgewaehlte PC-Tastatur keinen sinnvollen Tastendruck besitzt, werden daher nicht vorgetaeuscht.

### 4. CUE/BIN Multi-Datei-Import

Beim CD-Button erlaubt Android SAF jetzt Mehrfachauswahl. Eine typische Auswahl kann z. B. enthalten:

- `spiel.cue`
- `track01.bin`
- `track02.bin`
- weitere BIN/IMG-Trackdateien

Alle ausgewaehlten Dateien werden zusammen in einen eigenen privaten Session-Ordner kopiert. Die CUE-Datei wird anschliessend lokal neu geschrieben, damit ihre `FILE`-Referenzen auf die tatsaechlich importierten Dateinamen zeigen.

Pro Disc-Set wird genau eine `.cue` akzeptiert. Werden versehentlich mehrere CUE-Sheets gemeinsam ausgewaehlt, bricht der Import sauber mit einer Fehlermeldung ab.

Vor dem Mounten prueft der Importer, ob alle von der CUE referenzierten Trackdateien vorhanden sind. Fehlt eine Datei, wird das Set nicht halb gemountet, sondern mit einer Fehlermeldung abgebrochen.

Einzelne ISO/CHD/BIN-Images koennen weiterhin direkt verwendet werden.

### 5. Echtes libslirp-Netzwerk

Der bisherige Android-Netzwerk-Stub ist im Standardbuild ersetzt durch das reale 86Box-`net_slirp.c` mit `libslirp`.

Das Windows-Build-Center installiert fuer beide Android-ABIs:

- `libslirp:arm64-android`
- `libslirp:x64-android`

und benutzt ein Host-`pkgconf`, damit CMake die statischen vcpkg-Abhaengigkeiten korrekt findet. Bei Android wird pkg-config mit `--static` verwendet, damit transitive/private Abhaengigkeiten von libslirp beim Linken nicht verloren gehen.

In einer 86Box-Maschinenkonfiguration muss fuer die betreffende Netzwerkkarte `SLiRP` als Netztyp ausgewaehlt sein. Android besitzt bereits die `INTERNET`-Berechtigung.

Beim Aktivieren des realen Unix/Android-SLiRP-Pfads wurde ausserdem die `pollfd`-Kapazitaetsverwaltung korrigiert: `pfd_size` wird jetzt als Anzahl von Eintraegen statt irrtuemlich als Bytezahl initialisiert. Die Poll-Liste wird bei Fehlern und beim Beenden freigegeben.

## Weiterhin enthalten aus v0.2.0

- Universal64 APK: `arm64-v8a` + `x86_64`
- ARM64/x86_64 New Dynarec
- Multi-Core Stage 1 / asynchrone IDE-PIO-Schreibzugriffe
- SDL3-Frontend und OpenAL-Audio
- Touch-Maus, Tap links, Zwei-Finger-Tap rechts
- L/R-Bildschirmbuttons
- virtueller Maus-Joystick
- Mausgeschwindigkeit 25–300 %
- Touch-Toolbar
- Pause/Resume
- automatische Pause bei Home/App-Wechsel
- Hard Reset mit Sicherheitsabfrage
- 86Box SDL-OSD Schnellzugriff
- FDD/CD/HDD Android-Dateiauswahl
- FDD/CD direktes Mounten ueber den nativen 86Box-Hauptthread
- sichere HDD-Importfunktion ohne riskanten Live-Hot-Swap
- private Android-Pfade fuer ROMs, Assets, VMs, Temp und Medien

## Noch nicht vollstaendig

- echter APK-Laufzeittest auf ARM64-Hardware und x86_64-Android
- Dynarec W^X/JIT-Kompatibilitaet auf realen Android-Geraeten
- eigenes Android-GUI zum Aendern der kompletten 86Box-Maschinen-/NIC-Konfiguration
- Live-Hot-Swap fest konfigurierter HDDs
- FluidSynth, RtMidi, MUNT/MT-32, SoundCanvas/CLAP und VNC
- vollstaendige Abbildung jeder weltweit existierenden Unicode-Schrift auf Gast-Tastendruecke
- generische VM-Save-States, fuer die 86Box keinen einfachen universellen Snapshot-Pfad bereitstellt
