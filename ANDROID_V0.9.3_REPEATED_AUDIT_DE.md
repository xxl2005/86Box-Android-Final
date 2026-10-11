# 86Box Android v0.9.3 – Repeated Audit

Dieser Stand basiert auf v0.9.2 und wurde in mehreren aufeinanderfolgenden
Pruefdurchgaengen erneut kontrolliert.

## Behoben

- Maschinenklassen-Wechsel im Hardware-Konfigurator ist jetzt explizit:
  ein Mainboard wird nur erhalten, wenn es wirklich zur neu gewaehlten Klasse
  gehoert (oder "All machine types" aktiv ist).
- HDC-Slot 1 verwendet jetzt denselben Default wie der 86Box-Core:
  `internal` nur bei Mainboards mit internem HDC, sonst `none`.
  Damit geht im Full-Catalog-Modus eine bewusst gesetzte Auswahl nicht durch
  einen falschen Android-Default verloren.
- Profilmanager verarbeitet Rotation/Tablet-Konfigurationswechsel in-place.
  Laufende Desktop-/ZIP-/ROM-Importe verlieren dadurch nicht mehr ihre Activity
  bzw. ihren Fortschrittsdialog durch eine Rotation.
- Importierte CUE-Dateien werden Windows-kompatibel portiert:
  - CR/LF/CR-only werden verstanden,
  - Backslash-Pfade werden nur in FILE-Zeilen normalisiert,
  - Gross-/Kleinschreibung von Track-Dateien wird auf dem case-sensitiven
    Android-Dateisystem aufgeloest,
  - absolute Desktop-Trackpfade werden auf eine eindeutige importierte Datei
    zurueckgefuehrt, wenn moeglich.

## Wiederholte Pruefungen

- alle 11 Android-Java-Quelldateien mit dem JDK-Parser: keine Syntaxfehler
- ProfilePortability / IniConfig / RomLibraryManager mit javac kompiliert
- CUE Case-Sensitivity/Windows-Pfad Regressionstest: OK
- IniConfig Save/Reload/16-MB-Limit Regressionstest: OK
- ROM ZIP Merge/Traversal/Depth Regressionstest: OK
- HDD 5-88 IDE-Kollision, ATAPI-Erhalt und CHS Regressionstest: OK
- android_bridge.c: clang -Wall -Wextra -Werror Syntaxcheck: OK
- android_bridge.c: clang Static Analyzer: OK
- JNI Java-Deklarationen gegen native Exporte: OK
- AndroidManifest XML: OK

## Build-Regel

Da der native Hardwarekatalog erweitert wurde, muss ARM64 + x86_64 sauber neu
kompiliert werden. Der Hotfix entfernt deshalb bewusst die Native-/Compile-
Caches und alten jniLibs. vcpkg, NDK, SDL-Quellen und Downloads bleiben erhalten.
