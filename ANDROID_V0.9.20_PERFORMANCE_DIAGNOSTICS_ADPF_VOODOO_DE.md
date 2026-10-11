# 86Box Android v0.9.20 – Performance-Diagnose, ADPF und Voodoo-Threads

## Neu
- ADPF PerformanceHintManager fuer den echten nativen 86Box-CPU-Thread (API 31+).
- Native Scheduler-Diagnose (TID, requested/actual nice, Affinity-Ergebnis) wird per JNI in den App-Log gespiegelt.
- Zusaetzliche `native-performance.log` im Profil-Logordner, damit Scheduler-Daten auch dann exportiert werden, wenn `--logfile` leer bleibt.
- COMPLETE APP LOG legt den erwarteten `native-*.log` schon vor dem Start an und exportiert ihn auch leer.
- Thermal-Status; Android 16 CPU-Headroom wird per API-36-Reflection abgefragt, wenn vorhanden.
- Speed-Verlauf im Log: aktuell / Durchschnitt / Minimum / Maximum / Samples.
- Voodoo Render Threads: Auto / 1 / 2 / 4. Auto waehlt auf starken 6+ Kern Hosts standardmaessig 4, sonst 2/1.
- Voodoo-Thread-Aenderungen gelten ab dem naechsten VM-Start.
- Host-Performance-Fenster zeigt Runtime-Diagnose und bietet Refresh.

## Sicherheit / Kompatibilitaet
- Universal ARM64/x86_64 bleibt erhalten.
- Manueller Core-Modus bleibt erhalten.
- Bei nicht unterstuetztem ADPF/Headroom faellt die App ohne Crash zurueck.
- Touch, physische Maus, CD-Keepalive und Lifecycle-Fixes bleiben unveraendert.

Version: 0.9.20 / versionCode 55.
