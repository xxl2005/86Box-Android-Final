# 86Box Android v0.3.1 – Physische Maus + Tastatur

Basis: 86Box Android v0.3.0. Alle bisherigen Funktionen bleiben enthalten.

## Neu in v0.3.1

### Physische USB-/Bluetooth-Tastatur

- direkte Android-Hardware-KeyEvents statt nur Bildschirm-IME
- bevorzugt rohe Linux/HID-Scancodes -> 86Box XT/AT-Scancodes
- Android-KeyCode-Fallback fuer Geraete ohne brauchbaren Hardware-Scancode
- Buchstaben/Ziffern/Satzzeichen
- linke/rechte Shift-, Ctrl- und Alt-Tasten, AltGr
- F1-F12
- Pfeiltasten, Home/End, PageUp/PageDown, Insert/Delete
- kompletter NumPad-Grundsatz
- Windows-/Meta- und Menue-Tasten
- deutsche ISO-102nd-Taste `< > |`
- Android-Keyrepeat wird bei gehaltenen Hardwaretasten nicht doppelt in den Gast eingespeist; die PC-Tastaturemulation uebernimmt Typematic Repeat
- bei Home/App-Wechsel werden alle gedrueckten Tasten geloest, um "haengende" Ctrl/Alt/Shift-Tasten im Gast zu vermeiden

### Physische USB-/Bluetooth-Maus

- relative Mausbewegung
- Android Pointer Capture (ab minSdk 28 verfuegbar)
- kein Stoppen am Android-Bildschirmrand waehrend der Maus gefangen ist
- Linksklick
- Rechtsklick
- mittlere Maustaste
- X1 / X2 Seitentasten
- Mausrad vertikal
- Hardware-Maus benutzt dieselbe Geschwindigkeitsregelung 25–300 %
- Klick in die Emulatorflaeche kann den Maus-Lock aktivieren
- zusaetzlicher `MOUSE`-Button in der Android-Toolbar
- `Ctrl+Alt+G` gibt die Maus wieder frei, analog zum Desktop-86Box-Shortcut
- Android-Toolbar und virtueller Joystick bleiben vor dem Lock mit Touch/Maus bedienbar

### Parallelbetrieb mit Touch

Touch wird nicht entfernt. Die vorhandenen Funktionen bleiben gleichzeitig nutzbar:

- Touch-Maus
- Tap links / Zwei-Finger-Tap rechts
- virtueller Maus-Joystick
- L/R Bildschirmbuttons
- Bildschirmtastatur / deutsche UTF-8-IME
- Pinch-Zoom + Zwei-Finger-Pan

## Weiterhin aus v0.3.0 enthalten

- Universal64: arm64-v8a + x86_64
- ARM64/x86_64 New Dynarec
- Multi-Core Stage 1
- SDL3, OpenAL
- Vulkan bevorzugt mit GPU/GLES/Software-Fallback
- libslirp User-Mode NAT
- CUE/BIN-Multi-Datei-Import
- FDD/CD/HDD Android-Dateiauswahl
- Auto-Pause bei Home/App-Wechsel
- Hard Reset und 86Box OSD

## Hinweis

Ein realer Android-SDK/NDK-Build und Geraetetest bleibt fuer die erste v0.3.1-APK erforderlich. Die Hardware-Eingabeschicht ist so aufgebaut, dass native 86Box-Zustandsaenderungen ueber SDL-User-Events auf dem Emulator-Hauptthread ausgefuehrt werden.
