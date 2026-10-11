# 86Box Android v0.9.10 – Touchpad-Gesten

Der VM-Touchbereich arbeitet jetzt wie ein Notebook-Touchpad:

- Ein Finger streichen: relativen Gast-Mauszeiger bewegen.
- Einmal kurz tippen: linker Mausklick.
- Zweimal kurz tippen: zwei getrennte linke Klickimpulse; Windows/der Gast erkennt sie als Doppelklick.
- Einen Finger etwa 2 Sekunden ruhig halten: rechter Mausklick; beim anschließenden Loslassen wird kein zusätzlicher Linksklick erzeugt.
- Zwei-Finger-Tipp bleibt zusätzlich als schneller Rechtsklick erhalten.
- Pinch-Zoom und Zwei-Finger-Pan bleiben unverändert.

Die Gesten hängen weiterhin an der vorhandenen Option „Tap to click“. Physische USB-/Bluetooth-Maus und Touchpad-Gesten verwenden denselben Gast-Mauspfad, ohne die automatische Hardware-Maus-Erfassung zu ersetzen.
