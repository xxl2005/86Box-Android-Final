# 86Box Android v0.8.7

## Echte Emulationsgeschwindigkeit in Prozent

Android zeigt nun optional den echten `speed_percent` des 86Box-Cores als
Overlay oben links an.

Beispiele:
    100%  = Zielgeschwindigkeit wird erreicht
     75%  = Emulator erreicht etwa drei Viertel der Zielgeschwindigkeit
    120%  = Core meldet eine Geschwindigkeit oberhalb 100%

Wichtig:
Die Zahl wird NICHT aus Android-FPS geschaetzt. Sie stammt direkt aus der
bestehenden 86Box-Core-Funktion `ui_emu_status(int speed_percent)`.

Option:
    Touch settings -> Show emulation speed (%)

Die Option ist standardmaessig EIN und wird dauerhaft gespeichert.

Technik:
- `sdl_render.c` uebergibt den Core-Wert an die Android Bridge.
- JNI `nativeGetEmulationSpeedPercent()` liefert ihn an Java.
- Das Android-Overlay aktualisiert sich alle 500 ms.
- Wenn der native Wert noch nicht bereit ist, wird `--%` angezeigt.

Da Native-/JNI-Code geaendert wurde, muss ein sauberer ARM64+x86_64 Rebuild
erfolgen.
