# 86Box Android v0.7.2 – ROM ZIP Import / Export

v0.7.2 erweitert den Profil-Manager um eine globale ROM-Bibliotheksverwaltung.
Die ROMs liegen weiterhin gemeinsam für alle VM-Profile unter dem privaten
Android-Pfad `files/86box/roms`.

## Neu im VM Manager

- `IMPORT ROM ZIP`
  - akzeptiert normale ROM-ZIPs
  - akzeptiert direkt GitHub-`Download ZIP`-Archive des offiziellen `86box/roms`-Repositories
  - erkennt Wrapper-Ordner wie `roms-main/` oder `roms-master/` automatisch
  - akzeptiert auch von der App selbst exportierte Archive mit oberstem `roms/`-Ordner
  - vorhandene Dateien mit gleichem Pfad werden aktualisiert
  - andere bereits vorhandene ROM-Dateien bleiben erhalten

- `EXPORT ROMS`
  - exportiert die aktuell installierte gemeinsame ROM-Bibliothek über Android SAF
  - erzeugt ein portables ZIP mit oberstem Ordner `roms/`
  - das Export-ZIP kann später direkt wieder importiert werden

## Sicherheit

Der ZIP-Importer blockiert `../`-/Pfadtraversal-Einträge und extrahiert niemals
außerhalb seines temporären Importordners. Zusätzlich gelten Grenzen für Anzahl
der ZIP-Einträge und die entpackte Gesamtgröße. Git-/README-/Lizenz-Metadaten
eines GitHub-Repository-ZIPs werden nicht in den Laufzeit-ROM-Ordner übernommen.

## Verhalten der ROM-Bibliothek

ROMs sind global und werden von allen Hardwareprofilen verwendet. Ein ROM-Import
löscht keine Profile, NVR/CMOS-Daten oder VM-Medien. Die eingebetteten Android-
Platzhalter enthalten weiterhin keine fremden BIOS-ROMs; echte 86Box-ROMs werden
vom Benutzer importiert.

## Build

Die Änderung betrifft nur Java/Gradle. ARM64- und x86_64-Native-Builds sowie
vcpkg-Abhängigkeiten müssen für den Hotfix nicht gelöscht werden.
