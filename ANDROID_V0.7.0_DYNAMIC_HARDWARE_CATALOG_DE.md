# 86Box Android v0.7.0 – dynamischer Hardware-Konfigurator

## Ziel

Die kleine feste Hardwareliste aus v0.6.0 wurde ersetzt. Android fragt jetzt die Hardware direkt aus
den in `libmain.so` kompilierten 86Box-Registries ab. Damit folgt die Auswahl derselben Datenbasis wie
der Desktop-Konfigurator.

## Dynamische Bereiche

- Machine types und alle kompilierten Mainboards
- pro Mainboard nur geeignete CPU-Familien und CPU-Frequenzen
- FPU-Liste der ausgewählten CPU
- RAM-Minimum, Maximum und Granularität direkt vom Mainboard
- kompatible Video-Karten (Video #1 und #2)
- Soundkarten 1-4 sowie MIDI Out
- Netzwerkkarten 1-4 mit SLiRP
- Mausgeräte
- Floppy-Controller und CD-ROM-Interface
- HDC 1-4 und SCSI 1-4
- Voodoo, 8514/A, XGA, DA2, Dynarec, Softfloat, Wait states, PIT und Zeitsynchronisation

## Kompatibilitätsfilter

PCI/ISA/VLB/MCA/AGP usw. werden über `device_is_valid()` gegen das gewählte Mainboard gefiltert.
CPU-Familien und Frequenzen werden über `cpu_family_is_eligible()` / `cpu_is_eligible()` gefiltert.
Damit entstehen wesentlich weniger ungültige Kombinationen als mit einer manuell gepflegten Liste.

## ROMs

Die ROMs werden bewusst nicht automatisch aus dem Internet geladen oder in dieses Paket eingebettet.
Der Hardwarekatalog zeigt die im 86Box-Core vorhandenen Geräte. Fehlende BIOS-/Option-ROM-Dateien
werden beim VM-Start gemeldet. Offizieller ROM-Bestand: https://github.com/86box/roms

## Noch nicht Desktop-identisch

Gerätespezifische `Configure`-Dialoge, HDD-Geometrie/Bus-Zuordnung, Floppy-/CD-Laufwerksmodelle,
COM/LPT und die vollständige `Other peripherals`-Seite sind die nächste Ausbaustufe. Die grundlegenden
Hardwarelisten kommen ab v0.7.0 aber bereits direkt aus dem Core und müssen nicht mehr von Hand
nachgeführt werden.
