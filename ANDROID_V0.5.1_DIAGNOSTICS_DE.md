# 86Box Android v0.5.1 – Diagnose-/Fehlerlog

v0.5.1 erweitert v0.5.0 um ein persistentes Diagnose-System. Alle bisherigen Funktionen bleiben erhalten.

## Log-Ebenen

- `profiles/<VM>/logs/android-*.log`: Android/UI/Lifecycle/Profilaktionen, Geräte- und Buildinformationen sowie Java-Fehler.
- `profiles/<VM>/logs/native-*.log`: vollständiges 86Box-Log über `--logfile`.
- `profiles/<VM>/logs/java-crash-last.txt`: letzter ungefangener Java-Absturz mit Stacktrace.
- `profiles/<VM>/logs/native-crash-last.txt`: Crash-Marker für SIGSEGV, SIGABRT, SIGBUS, SIGILL und SIGFPE.

Pro Logtyp werden die letzten Sitzungen automatisch begrenzt, damit die Logs nicht unbegrenzt wachsen.

## LOG-Button

Im laufenden Emulator zeigt `LOG` die letzten Android- und 86Box-Nativelogzeilen. Über `EXPORT ZIP` wird ein Diagnosepaket erzeugt.

Das Diagnose-ZIP enthält Logs, System-/Buildinformationen und Konfigurationsdateien. HDD-, FDD- und CD-Images werden absichtlich **nicht** mitexportiert.

## Profilmanager

Im Drei-Punkte-Menü eines Hardwareprofils gibt es `Export diagnostics`. Dadurch kann auch ein Profil, das nicht mehr startet, als Diagnose-ZIP exportiert werden.

## Native Crash-Marker

Der kleine Signalhandler schreibt nur einen Crash-Marker und übergibt den Absturz danach wieder an Androids normale Crashbehandlung. Er versucht ausdrücklich nicht, unsichere Backtraces im Signalhandler zu erzeugen.
