# 86Box Android v0.8.8 - Second Bug Audit

Beim erneuten Audit von v0.8.7 wurden mehrere Randfehler behoben.

## Emulationsgeschwindigkeit (%)
- Der Core-Wert wird jetzt mit C11-Atomic zwischen Emulationsthread und JNI/UI
  ausgetauscht. Damit gibt es keinen nativen Data-Race mehr.
- Beim Start und beim Ende einer VM wird der Wert auf "nicht verfuegbar"
  zurueckgesetzt. Eine neue VM zeigt daher nicht kurz den Prozentwert der
  vorherigen VM.
- Der Android-Updater laeuft nicht mehr im Hintergrund weiter:
  onPause -> Stop, onResume -> Start.
- Wenn "Show emulation speed (%)" ausgeschaltet ist, wird auch der periodische
  Updater gestoppt.

## HDD
- CREATE HDD kann keine RAW-Platte mehr versehentlich als .vhd/.hdi/.hdx
  benennen. RAW-Erstellung verwendet .img oder .raw.
- Bekannte inkompatible strukturierte Formate wie QCOW/QCOW2/VDI/VMDK/VHDX
  werden beim Import abgewiesen statt als RAW behandelt zu werden.
- Differencing VHD wird beim Einzeldatei-Import abgewiesen, weil die Parent-VHD
  fehlen wuerde. Fixed und Dynamic VHD bleiben erlaubt.
- Die automatische IDE-Kanalsuche beruecksichtigt jetzt:
  HDD, CD-ROM, Legacy ZIP, RDisk, MO und Tape (ATAPI).
  Dadurch werden IDE/ATAPI-Kanaele nicht doppelt vergeben.

## Profile ZIP
- Nach Android-Dateidialogen wird Fullscreen sofort wiederhergestellt.
- ZIP-Pfadschutz bleibt aktiv.
- Zusaetzlich gibt es eine maximale Eintragszahl und eine dynamische
  Speicherplatzgrenze, damit ein defektes/aufgeblaehtes Profil-ZIP nicht den
  App-Speicher komplett fuellt.

## Tests
- Alle Android-Java-Dateien wurden mit dem Java-Compiler syntaktisch geparst:
  keine Parsefehler.
- HddProfileStorage wurde mit Android-Stubs kompiliert.
- Test: RAW-Datei "bad.vhd" wird als "bad.img" erzeugt.
- Test: belegte ATAPI-Kanaele durch CD/RDisk/MO/Tape werden erkannt.
- Test: VHD-Typ 4 (Differencing) wird korrekt erkannt.
- C11 Atomic-Minimaltest mit Clang erfolgreich.

Da Native/JNI-Code geaendert wurde, ist ein sauberer ARM64+x86_64 Native-Rebuild
notwendig.
