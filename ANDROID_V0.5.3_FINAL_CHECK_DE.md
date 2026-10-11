# 86Box Android v0.5.3 – Finaler Release-Check

Basis: 86Box-Android v0.5.2 Audit Fix.

Diese Version ist der erneut gepruefte Release-Kandidat vor dem ersten echten Android/NDK-Build.

## Zusaetzlich gefundene und behobene Punkte

1. **MDS-Dateihandle bei kurzem Header**
   - `image_load_mds()` konnte bei einem zu kurzen/defekten MDS-Header zurueckkehren, ohne die bereits geoeffnete Datei zu schliessen.
   - Fix: `fclose(fp)` auf diesem Fehlerpfad.

2. **MDS/MDF `track_file_t`-Leak bei mehreren Footern**
   - Bei mehrteiligen/beschaedigten MDS-Strukturen konnte ein Backing-File bereits geoeffnet werden, bevor alle Footer gelesen waren. Scheiterte ein spaeterer Footer, ging der neue `track_file_t` verloren.
   - Fix: Alle Footer werden zuerst geparst; das final ausgewaehlte Backing-File wird danach genau einmal geoeffnet.
   - Ergebnis: Der verwendete Clang Static Analyzer meldet fuer `cdrom_image.c` jetzt 0 Warnungen.

3. **Unsichtbare Android-Profile durch Namen mit fuehrendem Punkt**
   - Ein Profil wie `.retro` konnte als verstecktes Verzeichnis erzeugt werden, waehrend die Profil-Liste versteckte Verzeichnisse bewusst ausblendet.
   - Fix: Fuehrende Punkte werden aus der internen Profilordner-ID entfernt; leere IDs fallen auf `PC` zurueck.

4. **Profil-/Diagnose-Export nach Activity-Neuerstellung**
   - Wurde die Profilmanager-Activity waehrend des Android-Dokumentpickers neu erzeugt (z. B. Rotation/Memory Pressure), konnte der Bezug zum zu exportierenden Profil verloren gehen.
   - Fix: Pending-Exportprofile werden in `onSaveInstanceState()` gespeichert, kanonisch gegen den Profilroot validiert und beim Wiederaufbau restauriert. Abgebrochene Picker loeschen den Pending-Zustand.

5. **Analyzer-Cleanup**
   - Ein wirkungsloser `last_t = -1` Dead Store im CD-ROM-Pfad wurde entfernt.

## Erneut geprueft

- Profil-/Desktop-Portabilitaet inklusive `../`-Containment und Latin-1-CUE.
- JNI-Namensparitaet: 10 Java-Native-Methoden / 10 C-JNI-Implementierungen.
- Java-Parser-Smoke-Test: keine Syntaxfehler-Muster; Android-Klassen koennen ohne installiertes Android SDK hier erwartungsgemaess nicht aufgeloest werden.
- Manifest und Ressourcen-XML.
- Gradle-Metadaten: versionCode 10 / versionName 0.5.3.
- Universal-ABIs: `arm64-v8a` + `x86_64`, Universal-APK aktiv.
- BUILD_APK.bat: alle `goto`/`call`-Ziele vorhanden.
- GCC Syntax-Checks fuer Profiler, SCSI async/sync, IDE async/sync, Android-Bridge und CD-ROM-Parser.
- Clang Static Analyzer fuer Profiler, SCSI, IDE, Android-Bridge und CD-ROM-Parser: 0 Warnungen im geprueften Setup.
- Deterministischer Profiler-Test: 25.0 % CPU/core und 10.0 % Storage bei definierten Testzeiten.
- Keine versehentlich mitgepackten Analyzer-Artefakte.
- Patch wird gegen das unveraenderte v0.5.2-Paket geprueft und muss den v0.5.3-Baum reproduzieren.
- ZIP-Integritaet wird vor Auslieferung geprueft.

## Wichtige verbleibende Grenze

In dieser Laufumgebung ist kein vollstaendiges Android SDK/NDK installiert. Deshalb kann hier weiterhin **kein echter Gradle+NDK-APK-Build und kein Lauf auf realer Android-Hardware** bestaetigt werden. Der Source-/Struktur-/Analyzer-Stand ist release-kandidatenreif; der erste echte Build auf Windows und der anschliessende ARM64-Geraetetest bleiben die entscheidende Laufzeitpruefung, insbesondere fuer Dynarec/JIT, Vulkan, Audio, libslirp, Pointer Capture und Android Lifecycle.
