# 86Box Android v0.9.14 – Performance Auto-Tuning

## Ziel
Eine Universal-APK bleibt auf ARM64 und x86_64 kompatibel, optimiert den laufenden 86Box-CPU-Thread aber zur Laufzeit fuer unterschiedliche Host-SoCs.

## Modi
- Auto (Standard)
- Balanced
- Performance
- Maximum

Auto erkennt u. a.:
- Qualcomm Oryon / Snapdragon 8 Elite-Familie
- Qualcomm ARM64 / Snapdragon-Klasse
- MediaTek / Dimensity-Klasse
- Rockchip RK3588 (Cortex-A76/A55)
- generisches ARM64
- x86_64 Android

## Native Optimierung
- Release-Build: generisches ARM64 bleibt erhalten (`-march=armv8-a`) fuer universelle Kompatibilitaet.
- Android Release nutzt ThinLTO (`-flto=thin`).
- Performance/Maximum analysieren die vom Android-Prozess erlaubten CPU-Kerne anhand `cpu_capacity` bzw. maximaler Frequenz.
- Performance bevorzugt die schnelle Kernklasse (>=80% des besten Host-Scores).
- Maximum bevorzugt die Top-Kernklasse (>=90%, mindestens zwei Kerne wenn verfuegbar).
- Beim Wechsel auf Balanced wird die urspruengliche Android-CPU-Affinity wiederhergestellt.
- Prioritaets-/Affinity-Fehler sind nicht fatal; Android darf die Anfrage ablehnen und 86Box laeuft weiter.

## Display/Thermik
Performance und Maximum fordern 60 Hz fuer die Android-Ausgabe an. Das reduziert bei 120/144/165-Hz-Geraeten Display-/GPU-Last und laesst mehr thermisches Budget fuer den emulierten CPU-Thread. Balanced ueberlaesst die Bildrate Android.

## UI/Diagnose
- Neuer `BOOST`-Button im VM-Overlay.
- Speed-Anzeige zeigt z. B. `96% · Performance/Oryon`.
- Komplettlog protokolliert Host-Erkennung, effektiven Modus, Nice-Prioritaet und CPU-Affinity-Ergebnis.
