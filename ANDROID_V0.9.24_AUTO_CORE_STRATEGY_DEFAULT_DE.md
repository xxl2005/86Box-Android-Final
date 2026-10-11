# 86Box Android v0.9.24 – Auto-Core-Strategie als Standard

## Änderung

- `Core strategy` verwendet ab v0.9.24 standardmäßig **Auto (Android/ADPF decides)**.
- Android und ADPF entscheiden damit selbst, auf welchen erlaubten Host-Kernen der Haupt-Emulationsthread läuft.
- `Prime only`, `Big cluster`, `Manual cores` und `Adaptive Anti-Throttle` bleiben vollständig auswählbar.
- Der bisherige Zusatz `Adaptive Anti-Throttle (recommended)` wurde entfernt; Auto ist nun sichtbar als `Auto (Android/ADPF decides) · default` markiert.

## Upgrade-Migration

Bei einem Upgrade von v0.9.22/v0.9.23 kann die alte Standardstrategie `Adaptive Anti-Throttle` bereits in den SharedPreferences gespeichert sein. v0.9.24 führt deshalb einmalig eine Migration durch:

- gespeichertes `Adaptive Anti-Throttle` -> `Auto (Android/ADPF decides)`
- explizit gespeichertes `Prime only`, `Big cluster` oder `Manual cores` bleibt erhalten
- danach wird die vom Benutzer gewählte Strategie wieder normal dauerhaft gespeichert und nicht erneut überschrieben

Die Änderung betrifft nur die Standardauswahl. Native Affinity, ADPF, Multi-Thread Assist, virtuelle Tastatur, Touch/Maus und Medienpfade bleiben unverändert.
