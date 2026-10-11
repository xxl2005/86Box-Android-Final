# 86Box Android v0.9.21 – Langzeit-Frequenzmonitor

Diese Version erweitert die v0.9.20-Performance-Diagnose, um einen zeitabhängigen Leistungsabfall nach einigen Minuten eindeutig zu messen.

Neu:

- jede Sekunde: 86Box-Emulationsgeschwindigkeit, tatsächlich zuletzt verwendeter Host-CPU-Core des nativen Emulationsthreads sowie aktuelle/maximale Frequenz dieses Cores
- alle 10 Sekunden: Snapshot der aktuellen/maximalen Frequenz aller sichtbaren Host-CPU-Kerne
- rollierende 10/30/60/120-Sekunden-Mittelwerte für Emulations-% und Host-Takt
- automatische Trendklassifikation:
  - `CORRELATED_DROP` – Emulationsgeschwindigkeit und Host-Takt fallen gemeinsam
  - `SPEED_DROP_WITH_STABLE_FREQ` – Emulation fällt, Host-Takt bleibt weitgehend stabil
  - `SPEED_DROP_FREQ_UNAVAILABLE` – Taktmessung wird vom Gerät nicht bereitgestellt
- Host-Performance-Fenster zeigt Live-Core/Frequenz und den aktuellen Trend
- bestehende ADPF-, Thermal-, CPU-Headroom-, Affinity-, Manual-Core- und Voodoo-Diagnose bleibt erhalten

Die Messung ist absichtlich leichtgewichtig: pro Sekunde werden nur `/proc/self/task/<tid>/stat` und die Frequenzdateien des aktiven Cores gelesen; der teurere All-Core-Snapshot erfolgt nur alle zehn Sekunden.

Version: 0.9.21 / versionCode 56.
