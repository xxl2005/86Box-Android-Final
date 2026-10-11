# 86Box Android v0.9.17 – Manuelle Host-CPU-Kernauswahl

Neu im BOOST-Menü:

- `MANUAL CORES…` zeigt bis zu 64 Host-CPU-Kerne mit maximaler Frequenz und, wenn Android sie veröffentlicht, `cpu_capacity`.
- Die schnellste Kernklasse wird mit `★` markiert.
- `USE SELECTED` speichert die Auswahl dauerhaft und überschreibt in Performance/Maximum (sowie Auto→Performance) nur die automatische CPU-Affinity.
- `AUTO CORES` schaltet wieder auf die automatische Kernwahl zurück.
- Eine leere Auswahl wird nicht übernommen.
- Nicht erlaubte/offline Kerne werden nativ ausgefiltert; wenn die gesamte manuelle Maske ungültig ist, fällt 86Box automatisch auf die bestehende Kernwahl zurück.
- Balanced stellt weiterhin die ursprüngliche Android-Affinity wieder her.

Technik:

- Java speichert eine 64-Bit-Kernmaske in SharedPreferences.
- JNI überträgt Maske + Aktivstatus direkt als `jlong`/`jboolean`.
- Ein Performance-Generation-Counter sorgt dafür, dass die laufende CPU-Emulation die neue Maske übernimmt, auch wenn sich der Performance-Modus selbst nicht ändert.

Version: 0.9.17 / versionCode 52
