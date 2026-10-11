# 86Box Android v0.11.1 – Quellcode-Audit (Original CPU + Cycles)

## Behobene Fehler

- `src/config.c`: Zwei alte Aufrufe `android_set_cpu_execution_mode()` und `android_get_cpu_execution_mode()` aus dem entfernten DOSBox-adaptiven CPU-Experiment verursachten Compiler-/Linkerfehler. Beide entfernt. Beim Speichern wird der alte INI-Wert `android_cpu_engine` bereinigt. Der originale 86Box-CPU-Kern bleibt unverändert.
- `android/app/src/main/res/values/strings.xml`: Veraltete Anzeige `86Box Multi-Core EXP` ersetzt.
- versionCode 83 und versionName `0.11.1-original-cpu-cycles-audited`.

## Unverändert

- Alle originalen CPU- und Dynarec-Implementierungen des geprüften v0.9.36-Basisstands.
- Zyklen-Modi Original, Fixed, Auto, Max, Custom; Touch, Multi-Touch, virtuelle Tastatur, PC-Manager, Game-Key-Overlay.

## Testgrenzen

- Quellcode-Checks und native Tests können unabhängig vom Android-SDK durchgeführt werden.
- Ein vollständiger universeller APK-Build auf Windows mit installiertem SDK/NDK und Gerätetests bleiben nötig.
