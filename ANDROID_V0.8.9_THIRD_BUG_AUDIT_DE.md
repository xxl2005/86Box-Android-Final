# 86Box Android v0.8.9 - Third Bug Audit

Beim dritten Audit von v0.8.8 wurden weitere Randfehler behoben.

## Native Lifecycle / HW-Editor
- `android_emulator_ready` ist jetzt atomar und wird nicht mehr unsynchronisiert
  zwischen Java/JNI und dem nativen Emulator-Thread gelesen/geschrieben.
- Neuer atomarer Runtime-Status deckt die komplette Lebensdauer des nativen
  SDL/86Box-Laufs ab.
- `Close VM & open hardware` verwendet keinen geratenen 500-ms-Delay mehr.
  Der Profilmanager wartet auf das echte Ende des nativen Teardowns und oeffnet
  den Hardware-Editor erst danach. Timeout: 10 Sekunden.
- Auch fruehe native Exit-Pfade setzen den Runtime-Status wieder sauber zurueck.
- Beim Fehler beim Erzeugen des SDL-Blit-Mutex wird der Android-Crash-Marker
  entfernt und SDL sauber beendet.

## HDD-Import
- Nicht unterstuetzte Formate (.qcow/.qcow2/.vdi/.vmdk/.vhdx) werden jetzt
  VOR dem Kopieren abgewiesen. Ein riesiges VHDX wird also nicht erst komplett
  in den App-Speicher kopiert und danach verworfen.
- Vor dem Kopieren wird die vom Android-Dateianbieter gemeldete Dateigroesse mit
  dem freien App-Speicher verglichen. 64 MB Reserve bleiben frei.
- Wenn der Anbieter keine Groesse meldet, kontrolliert der Streaming-Copy die
  kopierte Datenmenge selbst und bricht vor dem Fuellen des Speichers ab.
- HDI/HDX werden zusaetzlich auf abgeschnittene Datendateien geprueft.

## Profil-Dateioperationen
- Ein fehlgeschlagener Fallback-Move hinterlaesst keinen halb kopierten
  Profilordner mehr.
- Auch ein fehlgeschlagenes Duplizieren entfernt den unvollstaendigen Zielordner.

## Audit
- ZIP-Integritaet geprueft.
- Alle Android-Java-Dateien erneut auf Syntaxfehler geprueft.
- HddProfileStorage mit Android-Stubs kompiliert.
- Unsupported-Format-Preflight getestet.
- Truncated-HDI/HDX-Erkennung getestet.
- C11 Atomic-Lifecycle-Minimaltest mit Clang erfolgreich.
- JNI Java/Native Symbolnamen kontrolliert.

Da Native/JNI-Code geaendert wurde, ist ein kompletter frischer ARM64+x86_64
Native-Rebuild erforderlich.
