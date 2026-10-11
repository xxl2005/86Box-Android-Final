# 86Box Android v0.9.32 – 3-Runden-Audit (Versionskorrektur)

## Runde 1 – Versionskonsistenz / Build Center

Beim realen Windows-Build wurde ein Versionsfehler im Build Center sichtbar:

- `android/app/build.gradle` war bereits korrekt auf `versionName '0.9.32'` und `versionCode 67`.
- `android/BUILD_APK.bat` verwendete jedoch noch den Titel `v0.9.31` und kopierte Universal-/ARM64-/x86_64-APKs unter v0.9.31-Dateinamen.
- Der Fehler betraf damit Build-Center-Anzeige und Ausgabe-Dateinamen; der Gradle-App-Metadatensatz selbst war bereits v0.9.32.

Korrektur:

- `BUILD_APK.bat` liest `versionName` und `versionCode` nun direkt aus `android/app/build.gradle`.
- APK-Dateinamen werden daraus automatisch gebildet.
- Der Build-Center-Titel verwendet dieselbe gelesene Version.
- Diagnose zeigt zusätzlich `App-Version` und `versionCode` an.
- Keine aktive v0.9.31-Referenz bleibt im Android-Buildpfad zurück.

Erwartete Ausgabe bei diesem Stand:

- `86Box-Android-universal64-v0.9.32-debug.apk`
- `86Box-Android-arm64-v0.9.32-debug.apk`
- `86Box-Android-x86_64-v0.9.32-debug.apk`

## Runde 2 – Regression / ARM64-New-Dynarec

Vergleich gegen v0.9.31 zeigt nur die vorgesehenen Änderungen:

- `src/codegen_new/codegen_backend_arm64.c`
- `src/codegen_new/codegen_backend_arm64.h`
- `src/codegen_new/codegen_backend_arm64_ops.c`
- `src/cpu/386_dynarec.c`
- `android/app/build.gradle`
- `android/BUILD_APK.bat` (Versionskorrektur plus bereits vorhandener Build-Center-Pfad)

Neu hinzugekommen sind ausschließlich die v0.9.32-Dokumentationsdateien.

Bytegleich zu v0.9.31 blieben unter anderem Android Bridge / DOSBox Pure Smooth v2, Thread Assist v2, HostPerformanceTuner/ADPF, Voodoo, virtuelle Tastatur/Scaler, Floating Game-Key Overlay, Box86Activity sowie SDL-Maus/Touch/Lifecycle.

ARM64-Hotpath-Prüfungen:

- AArch64 `LDR literal` W/X Encoding gegen Clang/LLVM-Assembler geprüft: bytegleich.
- Neues `MOVX_IMM`-Verfahren mit 200.007 64-Bit-Testwerten simuliert: alle rekonstruierten Werte exakt identisch.
- 64K-Codeblock-Tabelle mit vorhandenen `uint16_t`-Listen geprüft: Index 0 bleibt Sentinel, 1..65535 sind eindeutig darstellbar.
- Pointer-Literale werden robust über `uintptr_t` gespeichert; `_Static_assert` sichert die ARM64-64-Bit-Größe ab.
- Der neue JIT-Aufruf mit X0-CPU-State ist ausschließlich für Android ARM64 aktiv; x86_64 behält den bisherigen parameterlosen Aufruf.

Der vom Benutzer ausgeführte Windows/NDK-Build hat die v0.9.32-Quellen bereits erfolgreich für ARM64 und x86_64 kompiliert und Gradle `assembleDebug` erfolgreich abgeschlossen. Die dabei sichtbare v0.9.31-Ausgabebezeichnung war der in Runde 1 korrigierte Build-Center-/Dateinamenfehler.

## Runde 3 – Paketprüfung

- Version: `0.9.32` / `versionCode 67`.
- Build Center bezieht die Versionsnummer nun aus der Gradle-Quelle statt aus einer zweiten unabhängigen Hardcodierung.
- Keine aktive v0.9.31-Referenz im Android-Buildpfad.
- Keine unbeabsichtigten Änderungen an Eingabe, Voodoo, ADPF, Thread Assist oder Adaptive CPU.
- 0 leere Quell-/Builddateien.
- 0 case-insensitive Pfadkollisionen.
- Finales ZIP wird nach dieser Korrektur erneut per CRC/`unzip -t` geprüft.
- SHA-256 wird nach dem finalen Packaging angegeben.
