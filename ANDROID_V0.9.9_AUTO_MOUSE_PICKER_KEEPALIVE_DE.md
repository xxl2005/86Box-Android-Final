# 86Box Android v0.9.9 – automatische physische Maus + VM-Dateidialog-Keepalive

## Physische Maus

Der v0.9.8-Komplettlog zeigt, dass das OnePlus 15 physische Mausbewegungen korrekt an Java und JNI liefert (`HW_MOUSE_MOVE`, `mouse_ready=1`). Der 86Box-PS/2-Treiber sendet Bewegungen jedoch nur an den Gast, wenn das native 86Box-Flag `mouse_capture` aktiv ist. Android Pointer Capture und dieses native Flag waren bisher voneinander getrennt.

v0.9.9 spiegelt Android Pointer Capture deshalb über ein eigenes JNI/SDL-User-Event in `mouse_capture`. Zusätzlich lauscht die Activity mit `InputManager.InputDeviceListener` auf USB-/Bluetooth-Mäuse. Eine bereits verbundene oder neu angeschlossene physische Maus wird bei fokussierter VM automatisch erfasst; Strg+Alt+G löst sie weiterhin.

## CD-/Floppy-Dateidialog

Im v0.9.8-Log verschwindet der komplette App-Prozess, während Android DocumentsUI geöffnet ist: `Box86Activity` wird pausiert/gestoppt, danach startet ein neuer Prozess direkt im Profile Manager. Bei einem großen Gast (hier 512 MB RAM plus JIT/Renderer) kann Android den nun im Hintergrund befindlichen Emulatorprozess als Cache-Prozess beenden.

v0.9.9 startet unmittelbar vor dem externen Android-Dateidialog einen kleinen `specialUse`-Foreground-Service. Die VM wird weiterhin sauber pausiert, der Prozess bleibt während der Dateiauswahl aber in Foreground-Priorität. Beim Zurückkehren – auch bei Abbruch ohne Dateiauswahl – wird der Dienst sofort beendet und die VM fortgesetzt.

Der Keepalive läuft nur während eines CD-/Floppy-/Diagnose-Dateidialogs und nicht dauerhaft.
