# 86Box Android v0.9.1 - Fifth Bug Audit

Beim fünften Audit von v0.9.0 wurden weitere Robustheits- und Importfehler
gefunden und behoben.

## Native / JNI
- Die von Java/JNI und SDL/Render gemeinsam verwendeten Werte für
  Mausgeschwindigkeit, Tap-to-click, Zoom/Pan und Tastaturlayout sind jetzt
  C11-atomar. Damit gibt es bei parallelen JNI-/SDL-Zugriffen keinen C-Data-Race
  mehr.

## HDD
- Beim Ersetzen einer HDD wird ein bereits gespeicherter IDE/ATAPI-, SCSI-,
  MFM-, XTA- oder ESDI-Kanal nicht mehr blind übernommen.
- Wenn ein Desktop-Profil bereits eine Doppelbelegung enthält, sucht Android
  automatisch einen freien Kanal.
- IDE-Adressen werden wie der 86Box-Core normalisiert; SCSI-Adressen werden
  validiert/normalisiert.

## CD/FDD / CUE
- Medienimport hält mindestens 64 MB App-Speicher frei und prüft die vom
  Android-Provider gemeldete Dateigröße vor dem Kopieren.
- Unbekannte Dateigrößen werden während des Streams begrenzt.
- Teilkopien werden bei Fehlern entfernt.
- CUE-Sheets mit klassischen CR-only-Zeilenenden werden jetzt korrekt erkannt.
- Ungewöhnlich große CUE-Dateien (> 8 MB) werden abgewiesen, statt komplett in
  den Java-Heap geladen zu werden.

## Desktop-/Profilimport
- Desktop-Ordnerimport hat jetzt dieselbe 64-MB-Speicherreserve wie ZIP/HDD.
- Maximal 100000 Einträge und maximal 128 Verzeichnisebenen.
- Größenlimit wird auch während des Kopierens kontrolliert.
- Profil-ZIP-Import sperrt ebenfalls extrem tiefe Pfade.
- 86box.cfg > 16 MB wird abgewiesen, um Files.readAllBytes-OOM zu verhindern.
- Sehr große CUE-Dateien werden bei der Import-Normalisierung nicht in den Heap
  geladen.
- Der veraltete Text "v0.8.0 desktop profile import" im Importbericht wurde
  entfernt.

## ROM ZIP
- ROM-Import behält 64 MB freien App-Speicher sowohl beim Entpacken als auch
  beim anschließenden atomaren Merge.
- ZIP-Pfade sind zusätzlich auf 4096 Zeichen und 128 Ebenen begrenzt.

## Hardware-Katalog
- Falls der native Core-Katalog ausnahmsweise nicht geladen werden kann, sind
  SAVE und SAVE & START jetzt deaktiviert. Dadurch kann ein Desktop-Profil nicht
  versehentlich mit der minimalen Notfall-Fallback-Hardware überschrieben
  werden.

## SAF Export
- Profile-/ROM-/Diagnose-Export versucht zuerst "rwt" und fällt bei Providern,
  die diesen Modus nicht unterstützen, auf "w" zurück.

## Tests
- 11 Android-Java-Quelldateien mit Javac-Parser: 0 Syntaxfehler.
- HddProfileStorage mit Android-Stubs kompiliert.
- Regressionstests: IDE/ATAPI/SCSI/MFM-Kollisionsreparatur bestanden.
- ProfilePortability Pfad-Rewrite + 16-MB-OOM-Guard bestanden.
- ROM ZIP Import/Merge sowie Deep-Nesting-Guard bestanden.
- C11 Atomic-Header/Globals mit Clang -std=c11 -Werror kompiliert.
- CR-only CUE-Line-Splitting getestet.

Da Native/JNI-Code geändert wurde, muss ARM64+x86_64 vollständig frisch
kompiliert werden. Der Hotfix löscht dafür bewusst die alten Native-/Gradle-
Compile-Caches, lässt vcpkg, NDK, SDL-Quellen und Downloads aber erhalten.
