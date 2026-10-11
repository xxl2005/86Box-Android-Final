# 86Box Android v0.11.0 – Bereinigter 86Box-CPU-Kern / CPU Cycles

## Was entfernt wurde
- Experimentelle CPU-Worker und Code-Snapshots (Stage A/B/C)
- Dolphin-inspirierte Kernzuweisung und CPU-/Render-Isolation (Stage D)
- JIT-Hotpath- und CPU/Voodoo-Profiler (Stages E/F)
- Thread-Assist v2, dessen Registrierungs-Hooks in C++/pthread-Threads und
  die Kernzuweisungs-Logik
- Alte DOSBox-Pure-artige adaptive CPU-Steuerung v0.9.30–31
- Java-Schalter/JNI-Verbindungen/CMake-Einträge dieser Komponenten

## Bewusst erhalten
- CPU-/Dynarec-Implementierung aus dem bereits getesteten v0.9.36-Quellstand
  (keine neuen JIT-Hooks oder Änderung der x86-Ausführungsroutine)
- Ursprüngliche PC-Hardwareprofile, Datenträger, Windows 98, Audio, Voodoo
- Funktionierende Subpixel-Touch-Maus, physische Maus/Tastatur, Multi-Touch,
  Game-Key-Overlay und virtuelle Tastatur mit Tippanimation
- Normale Android-Host-Leistungsprofile und Voodoo-Renderthread-Optionen

## Neue CPU-Zyklen-Steuerung unter BOOST
Original (Default): gleiches Zyklusbudget wie 86Box ohne weitere Anpassung.
Fixed: 25–100 % des vom PC-Profil vorgegebenen Zyklusbudgets.
Auto: nach drei aufeinanderfolgenden Geschwindigkeits-Misswerten (unter 94 %)
  wird ein konservativer Zyklus-Limit-Schritt vorgenommen; nach vier guten
  Geschwindigkeitsmeldungen wird vorsichtig erhöht. Skala 50–100 %.
Max: volles nominales 86Box-Zyklusbudget ohne künstlichen Overclock.
Custom: eigene Grenze in kcycles/Sekunde (1.000–500.000), niemals mehr als
  die im PC-Profil vorgesehene Zahl.

Diese Optionen verändern die *Menge* der je Scheduler-Quantum ausgeführten
Original-86Box-Zyklen, nicht x86-Instruktionen, JIT-Code, PIT-Timing oder
BIOS-/CPU-Identität. Ein niedrigeres Budget kann das effektive CPU-Tempo
reduzieren und damit das Zeitverhalten von Windows-98-Software verändern.
Auto ist ein experimenteller Regler, aber keine zusätzliche CPU-Engine.

Der Button `SHOW CPU CYCLES DIAGNOSTICS` und
`[ANDROID][CPUCYCLES]` im Log unterscheiden konfiguriertes vs. begrenztes
Zyklusbudget. `budget_cap` ist eine theoretische Budgetgrenze, **keine**
gemessene Ist-Frequenz; die angezeigte Emulationsgeschwindigkeit separat
prüfen.

## Test
1. Die alte v0.9.36-Installation bleibt eigenständig bestehen (experimentelle
   Paket-ID wurde nicht wiederverwendet).
2. Das Projekt via `android\BUILD_APK.bat`, Option 3 als Debug Universal-APK
   ARM64+x86_64 kompilieren. Android-SDK/NDK werden auf dem Windows-PC benötigt.
3. Zunächst `Original 86Box`, Windows-98-Spiel und Touch testen.
4. Dann `Fixed 75%` und `Original` vergleichen; später Auto/Custom.
5. Bei alten Android-SharedPreferences werden Stage-A–F-Schalter entfernt und
   NICHT wiederverwendet; CPU Cycles startet standardmäßig bei Original.

## Prüfgrenze
Die lokalen CPU-Cycles-C11-Stresstests mit Sanitizern, Touch-Tests sowie
Byte-Vergleiche zur CPU-Basis wurden ausgeführt. Ein echter Android-Gradle-
Build oder Laufzeittest kann nur auf einem passenden SDK/NDK-Host erfolgen.
