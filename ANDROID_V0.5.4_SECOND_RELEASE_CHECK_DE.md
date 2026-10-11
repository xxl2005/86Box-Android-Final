# 86Box Android v0.5.4 – Zweiter Release-Check

Basis: v0.5.3 Release Candidate. Dieser Durchlauf wurde auf Wunsch vor dem ersten echten Android-Build noch einmal mit anderem Schwerpunkt wiederholt: Android-Lifecycle/Eingabe, Desktop-Profil-Portabilität, Profil-Import-Fehlerpfade, eingebettete ROMs/Assets sowie Build-Center-Robustheit.

## Neu bestätigte und behobene Fehler

1. **Ctrl+Alt+G erzeugte einen verwaisten G-Key-Up im Gast.** Beim Freigeben der physischen Maus wurde G-Key-Down geschluckt, G-Key-Up aber weitergereicht. v0.5.4 konsumiert beide Hälften des Hotkeys und ignoriert außerdem unerwartete ACTION_MULTIPLE-Ereignisse im Raw-Hardware-Pfad.
2. **ANSI/Windows-1252 Desktop-`86box.cfg` konnte nicht importiert werden.** Der alte Code verlangte UTF-8 und konnte mit `MalformedInputException` abbrechen. Der Import erkennt jetzt UTF-8 (mit/ohne BOM), UTF-16LE/BE BOM und fällt bei alten Windows-Dateien auf Windows-1252 zurück; die Android-Arbeitskopie wird anschließend als UTF-8 gespeichert. Das Desktop-Original bleibt separat gesichert.
3. **CUE-Textimport war bei BOM/alten ANSI-Dateien nicht vollständig robust.** Desktop-CUEs und Android-Multi-Datei-CUEs verwenden nun dieselbe strikte Dekodierung und werden als UTF-8 normalisiert.
4. **Profil-Normalisierung konnte einen verwaisten `*-normalized`-Ordner hinterlassen**, wenn ein Fehler mitten im Verschieben/Kopieren auftrat. Ein `finally`-Cleanup entfernt den sibling staging tree jetzt auch auf Fehlerpfaden.
5. **Eingebettete ROMs/Assets wurden bei jedem Emulatorstart erneut überschrieben.** Die Extraktion läuft jetzt vor dem VM-Start im Profil-Worker, nutzt einen install/revision-spezifischen Abschlussmarker, kopiert nur fehlende Dateien und schreibt neue Assets zuerst in temporäre Dateien. Vorhandene bzw. vom Benutzer aktualisierte ROMs werden nicht überschrieben.
6. **Schneller Doppel-Tap auf START konnte zwei parallele VM-Starts auslösen.** Der Profilmanager sperrt nun einen laufenden Startvorgang bis zur Übergabe an die Emulator-Activity.
7. **Asset-Marker hatte bei paralleler Vorbereitung denselben Temp-Dateinamen.** Temp-Marker sind jetzt thread-spezifisch.
8. **BUILD_APK.bat prüfte nur, ob irgendein Java vorhanden war.** v0.5.4 verlangt explizit `javac` und JDK >= 17.
9. **Fehlende API-35/Build-Tools wurden nicht automatisch nachinstalliert, wenn das NDK bereits vorhanden war.** `ensure_tools` prüft nun NDK, Android API 35 und Build Tools 35.0.0 unabhängig voneinander und ruft bei Bedarf `sdkmanager` auf.

## Reproduzierbare Tests

- Windows-1252 `86box.cfg` mit Nicht-ASCII-Dateiname: Import + UTF-8-Ausgabe erfolgreich.
- UTF-8-BOM und UTF-16LE-BOM: Dekodierung erfolgreich.
- ANSI-CUE mit Nicht-ASCII-Dateiname: UTF-8-Normalisierung und Slash-Konvertierung erfolgreich.
- RuntimeAssets-Harness: erste Extraktion erfolgreich; vorhandenes benutzergeändertes ROM bleibt erhalten; neue Assets einer späteren APK-Revision werden ergänzt.
- Java-Parser-Smoke: keine Syntaxfehler-Muster; die erwarteten `android.*`-Symbolfehler bleiben ohne installiertes Android SDK.
- JNI: 10 Java-Native-Methoden / 10 Implementierungen.
- Manifest/Resource XML: gültig.
- Gradle: versionCode 11, versionName 0.5.4; arm64-v8a + x86_64 + Universal APK.
- BUILD_APK.bat: alle `goto`/`call :label` Ziele vorhanden.
- Native `src/` ist gegenüber v0.5.3 byte-identisch; damit gelten die dort erneut durchgeführten GCC/Clang-0-Warnungen für die modifizierten Hochrisiko-C-Pfade unverändert.

## Noch nicht behauptet

Ein vollständiger Gradle/NDK-Build und ein realer ARM64-Gerätetest sind in dieser Container-Umgebung weiterhin nicht möglich, weil kein Android SDK/NDK installiert ist. v0.5.4 ist daher ein statisch und deterministisch geprüfter Release Candidate, kein behaupteter Device-Runtime-Pass.
