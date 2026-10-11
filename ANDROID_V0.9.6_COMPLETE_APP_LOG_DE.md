# 86Box Android v0.9.6 – Complete App Log

## Zweck
v0.9.6 erweitert die bisher profilbezogene Diagnose zu einem durchgehenden App-Log.
Der Logger startet bereits in `Application.onCreate()` und bleibt über Profile Manager,
Desktop-Import, Hardware Configurator und VM-Laufzeit hinweg aktiv.

## Neu
- Prozessweiter Log unter `files/86box/app-logs/app-*.log`.
- Activity-Lifecycle wird protokolliert (create/start/resume/pause/stop/destroy).
- Java-Uncaught-Exceptions werden zusätzlich appweit gesichert.
- Profil-/Native-Logs bleiben erhalten und werden zusätzlich ins App-Gesamtlog gespiegelt.
- Hardware Configurator protokolliert den kompletten CPU-Auswahlpfad:
  - rohe `86box.cfg` Werte (`machine`, `cpu_family`, `cpu_speed`, `cpu_multi`, `fpu_type`)
  - gefilterte und vollständige CPU-Familien-/Taktlisten
  - exakter CPU-Treffer und jeden Fallback
  - tatsächlich selektierten Spinner-Eintrag
- Desktop-Import protokolliert CPU-Werte vor Rewrite, nach Rewrite und im finalen Profil.
- Neuer Profile-Manager-Button **EXPORT COMPLETE APP LOG**.
- Der Export enthält App-/Manager-/Profil-/Native-Logs und Konfigurationsmetadaten,
  aber keine HDD-, Floppy-, CD/DVD-Images oder ROM-Binaries.

## 66-MHz-Diagnose
Mit demselben Win98SeLight-Profil reproduzieren:
1. Desktop-Profil importieren.
2. Hardware Configurator öffnen.
3. Prüfen, ob weiterhin 66 MHz statt 200 MHz angezeigt wird.
4. Zurück zum Profile Manager.
5. **EXPORT COMPLETE APP LOG** drücken.
6. Die erzeugte ZIP für die nächste Analyse bereitstellen.

Die entscheidenden Zeilen sind mit `CpuTrace` markiert.
