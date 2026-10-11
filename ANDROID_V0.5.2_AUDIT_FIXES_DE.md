# 86Box Android v0.5.2 – Fehler-/Stabilitätsaudit

Basis: 86Box-Android v0.5.1 Diagnostics.

## Bestätigte und behobene Fehler

1. **Desktop-Profilpfade konnten ein Profil verlassen.** Relative Werte wie `../AnderesProfil/disk.vhd` wurden bislang als gültiger Android-Pfad übernommen. v0.5.2 normalisiert Pfade komponentenweise und erlaubt nur Ziele innerhalb des importierten Profils. Eine tatsächlich mitimportierte Datei kann weiterhin über eindeutiges Dateinamen-/Suffix-Matching gefunden werden; andernfalls bleibt der Originalwert erhalten und wird im `IMPORT_REPORT.txt` als ungelöst markiert.
2. **Windows drive-relative Pfade** wie `C:disk.vhd` wurden nicht als hostabhängige Desktop-Pfade erkannt. Sie werden jetzt wie andere Laufwerkspfade behandelt.
3. **Nicht-UTF-8-CUE-Dateien konnten still beschädigt werden.** Der frühere UTF-8-Decoder ersetzte ungültige Bytes, ohne eine Exception auszulösen. v0.5.2 verwendet strikte UTF-8-Prüfung und einen byteerhaltenden ISO-8859-1/ANSI-Fallback für ältere westliche CUE-Dateien.
4. **Fehlgeschlagener CUE/BIN-Import hinterließ große Teilkopien.** Die komplette Import-Session wird bei Fehlern jetzt rekursiv entfernt; auch eine nur teilweise kopierte Einzeldatei wird gelöscht.
5. **Mehrfachauswahl ohne CUE war mehrdeutig.** Zuvor konnte bei mehreren Dateien eine ISO/CHD ausgewählt werden, während weitere kopierte Dateien unbenutzt liegen blieben. Mehrfachauswahl ohne CUE wird jetzt eindeutig abgewiesen.
6. **Legacy-v0.4-Migration konnte den gerade geöffneten Manager-Logordner verschieben.** Migration läuft jetzt vor dem Start des Manager-Loggers und `manager-diagnostics` ist zusätzlich vom Verschieben ausgeschlossen.
7. **Logger-Kontext konnte nach Rückkehr aus einer VM auf dem VM-Profil bleiben.** `onResume()` stellt den Manager-Kontext wieder her; bestehende Manager-Sessions werden dabei wiederverwendet statt unnötig dupliziert.
8. **Java-Crashlogger hatte Sichtbarkeitsrisiken zwischen Threads.** Die aktiven Log-/Profilreferenzen sind jetzt `volatile` und bleiben für den Uncaught-Exception-Handler sichtbar.
9. **Performance-Profiler hatte C-Datenrennen** auf Startzeit und 5-Sekunden-Zähler. Beide werden jetzt mit denselben atomaren Operationen wie die Profilkategorien verwaltet.
10. **IDE-/SCSI-Worker lasen `running` unsynchronisiert in der Schleifenbedingung.** Die Stop-Abfrage erfolgt jetzt nach dem synchronisierenden Event-Wakeup.
11. **IDE-/SCSI-Worker prüften Event-/Thread-Erzeugung nicht.** Bei Speicherdruck konnte ein NULL-Event verwendet oder Ressourcen geleakt werden. v0.5.2 fällt in diesem Fall sauber auf den synchronen I/O-Pfad zurück und protokolliert die Ursache.
12. **Native Crash-Marker re-signalisierte prozessweit mit `kill()`.** Jetzt wird das Signal mit `raise()` auf dem abstürzenden Thread erneut ausgelöst. Beim normalen Emulatorende werden außerdem die zuvor installierten Signal-Handler wiederhergestellt.
13. **Native Crash-Marker konnte Androids vorherigen Fatal-Signalhandler beim Absturz umgehen.** Durch `SA_RESETHAND` wäre nach unserem Marker nur der Default-Handler aktiv gewesen; Android/debuggerd hätte dabei seine normale Tombstone-/Crashverarbeitung verlieren können. v0.5.2 stellt den zuvor gespeicherten Handler vor dem Re-Raise wieder her und beendet nur dann per `_exit`, falls ein unerwartet nicht terminierender Handler zurückkehrt.
14. **ZIPs aus Windows-Werkzeugen mit Backslash-Einträgen** werden beim Profilimport vor der bereits vorhandenen Canonical-Path/ZIP-Slip-Prüfung normalisiert.
15. **CUE/TOC-Parser kopierte Tokens ohne Zielgrößenprüfung.** Sehr lange `FILE`, Zeit- oder Flag-Tokens konnten feste 64/128/256-Byte-Puffer überschreiben. Der Parser hat jetzt für jeden Token eine explizite Zielgröße und bricht bei Überlänge sauber ab.
16. **CUE-Trackpfade konnten beim Zusammensetzen überlaufen.** Verzeichnis + Dateiname werden jetzt vor dem Kopieren gegen den Zielpuffer geprüft.
17. **Malformed `TRACK`-Zeilen konnten vor den Stringanfang lesen.** Vor `line[len-2]`/`line[len-1]` wird jetzt `len >= 2` geprüft.
18. **MDS-Abschluss konnte `ct == NULL` dereferenzieren** und schrieb bei Spezialtracks über einen veralteten `ci`-Zeiger immer nur in denselben Index. Der Loop läuft jetzt nur mit gültigem Track und setzt `ct->idx[i].type` für den tatsächlich bearbeiteten Index.
19. **CUE-Zeichenklassifikation verwendete potentiell negative `char`-Werte** direkt mit `islower/toupper`. Die Übergabe erfolgt jetzt über `unsigned char`.
20. **Ein Clang-Analyzer-Ausgabefile (`hdc_ide.plist`) war versehentlich im Android-Quellpaket gelandet.** Es ist kein Laufzeitbestandteil und wurde aus v0.5.2 entfernt, damit Buildpaket und Patches nur echte Projektdateien enthalten.

## Bewusst nicht als bestätigt geändert

Der ARM64/x86_64-Dynarec verwendet weiterhin ausführbaren JIT-Speicher. Ein echter Android-Gerätetest des JIT/W^X-Verhaltens ist weiterhin erforderlich; ohne reproduzierbaren Gerätefehler wurde der Allocator in diesem Audit nicht spekulativ umgebaut.

Ein kompletter Gradle/NDK-Linklauf ist in dieser Ausführungsumgebung weiterhin nicht möglich, da kein Android SDK/NDK installiert ist. Die statischen, Compiler-, Analyzer-, Java- und Paketprüfungen stehen im separaten Validierungsbericht.
