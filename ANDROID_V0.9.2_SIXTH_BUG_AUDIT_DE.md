# 86Box Android v0.9.2 - Sixth Bug Audit

Beim sechsten Audit von v0.9.1 wurden weitere Robustheitsfehler behoben.

## Hardware-Konfigurator / Profil-Schutz
- Wenn eine vorhandene `86box.cfg` nicht sicher geladen werden kann, wird sie nicht mehr
  still durch eine leere/default Konfiguration ersetzt.
- SAVE, SAVE & START sowie HDD Import/Create werden in diesem Fall deaktiviert.
- Oben im Hardware-Konfigurator erscheint `CONFIG LOAD FAILED` mit der Ursache.
- `IniConfig` akzeptiert maximal 16 MB grosse 86box.cfg-Dateien, damit eine defekte
  Konfiguration nicht ungebremst Java-Heap verbraucht.
- Tablet-Rotation/configChanges erzeugen den Hardware-Konfigurator nicht neu; noch nicht
  gespeicherte Spinner/Checkbox-Werte bleiben dadurch erhalten.

## HDD
- Ungueltige IDE-Adressen werden nicht mehr per Bitmaske auf einen anderen Kanal gefaltet.
  Beispiel: `4:2` wurde vorher zu `0:0`; jetzt wird die Adresse als ungueltig erkannt und
  ein wirklich freier Kanal gesucht.

## SDL / JNI Renderer
- `nativeGetRendererInfo()` greift nicht mehr aus dem Android-UI-Thread direkt auf den
  SDL_Renderer zu, waehrend der Renderthread ihn zerstoeren/neu anlegen kann.
- Der Renderthread schreibt stattdessen einen mutex-geschuetzten Renderer-Namen-Snapshot;
  JNI liest nur diesen Snapshot. Dadurch wird ein moeglicher Use-after-free/Data-Race beim
  Oeffnen der Touch-Einstellungen waehrend Renderer-Reinit/Shutdown entfernt.

## Profil duplizieren
- Vor dem Duplizieren wird der logische Platzbedarf geprueft. Der Vorgang wird abgelehnt,
  wenn dadurch weniger als 64 MB App-Speicher frei blieben. Bei Fehlern bleibt die bereits
  vorhandene Cleanup-Logik aktiv.

## Tests
- Strict IDE Normalisierung + 16-MB cfg Guard per Java-Test: OK.
- Renderer Snapshot Mutex Paralleltest mit Clang `-Wall -Wextra -Werror`: OK.
- Java Syntax-Scan der geaenderten HardwareConfigActivity: keine Parserfehler.
- ZIP-Inhalt/Integritaet wird beim Packen nochmals geprueft.

Da SDL/JNI/native Code geaendert wurde, muss ARM64 + x86_64 sauber neu kompiliert werden.
