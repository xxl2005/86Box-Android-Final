# 86Box Android v0.9.22 – Adaptive Anti-Throttle + virtuelle PC-Tastatur

## Adaptive Anti-Throttle
- Neue Core-Strategien: Auto, Prime only, Big cluster, Manual und Adaptive Anti-Throttle.
- Adaptive ist bei Neuinstallationen/ohne alte Manual-Core-Auswahl der Standard.
- Wenn die Emulationsgeschwindigkeit unter 95 % fällt und ein anderes Kernpaar mindestens 15 % höher taktet, muss dieser Vorteil 8 Sekunden stabil bleiben.
- Zwischen Umschaltungen liegen mindestens 20 Sekunden (Hysterese/Cooldown), damit der CPU-Thread nicht ständig zwischen Clustern springt.
- Jeder Wechsel wird als `ADAPTIVE_SWITCH` geloggt, inklusive altem/neuem Kernpaar, GHz und Emulations-%.
- Balanced deaktiviert die adaptive Affinity und gibt die Kernauswahl wieder Android zurück.

## Eigene virtuelle PC-Tastatur
- Der Toolbar-Button ⌨ öffnet jetzt eine 86Box-eigene Tastatur statt nur Androids IME.
- Direkter XT-Scancode-Pfad wie bei physischer USB/Bluetooth-Tastatur.
- Enthält Esc, F1–F12, Zahlen, Buchstaben, Backspace/Tab/Enter, beide Shift/Ctrl/Alt, AltGr, Windows/Menu, Navigation, Pfeile, Print Screen, Pause, Scroll Lock und Numpad.
- Deutsche QWERTZ-Beschriftung wird automatisch aus der bestehenden Gast-Tastatur-Einstellung übernommen.
- Modifier können vorgemerkt werden; `MOD LOCK` entscheidet, ob sie nach der nächsten normalen Taste automatisch losgelassen werden oder eingerastet bleiben.
- `IME` im Keyboard öffnet weiterhin optional die Android-Systemtastatur.
- Beim Schließen/Pausieren werden gehaltene Tasten sicher losgelassen.

## Diagnose
- Langzeitmonitor läuft weiter, auch wenn das Speed-Overlay ausgeblendet ist.
