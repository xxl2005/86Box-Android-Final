# 86Box Android v0.9.0 - Fourth Bug Audit

Beim vierten Audit von v0.8.9 wurden reproduzierbare Fehler in der
HDD-Platzierungs- und VHD-Prueflogik gefunden und behoben.

## HDD-Busbelegung

- Der Android-Hardwareeditor verwaltet weiterhin HDD 1-4 in der GUI.
- Fuer die Kollisionspruefung werden jetzt aber alle 88 HDD-Slots des
  86Box-Cores (HDD_NUM=88) eingelesen. Dadurch kann ein importiertes
  Desktop-Profil mit z.B. HDD 5-88 keinen bereits belegten IDE-Kanal
  unbemerkt doppelt belegen.
- Ein vorhandener HDD-Bus `atapi` bleibt beim Ersetzen/Importieren erhalten.
  v0.8.9 hat diesen Bus irrtuemlich auf `ide` zurueckgesetzt.
- MFM/XTA/ESDI pruefen jetzt beide Kanaele gegen alle 88 HDD-Slots und melden
  einen Fehler, wenn der jeweilige Bus voll ist.
- SCSI sucht jetzt eine wirklich freie Bus/ID-Kombination und beruecksichtigt
  alle HDDs sowie SCSI-CD-ROM, ZIP/RDisk, MO und Tape.

## VHD-Pruefung

86Box MiniVHD prueft beim Oeffnen Checksummen. v0.8.9 pruefte nur Cookie,
Geometrie und Disk-Typ. Dadurch konnte eine beschaedigte VHD den Android-
Import bestehen und erst beim VM-Start im Core scheitern.

v0.9.0 prueft vor dem Eintragen in 86box.cfg:
- Footer-Cookie
- Footer-Checksumme
- Disk-Typ
- bei Dynamic/Differencing: Sparse-Header-Offset
- Sparse-Header-Cookie `cxsparse`
- Sparse-Header-Checksumme

Differencing VHD bleibt beim Einzeldatei-Import weiterhin absichtlich
gesperrt, weil die Parent-VHD fehlt.

## Audit-Tests

- Reproduktion v0.8.9: HDD 5 auf IDE 0:0 wurde zuvor von der Android-Suche
  uebersehen.
- v0.9.0: HDD 1 waehlt in diesem Test korrekt IDE 0:1.
- ATAPI-Bus bleibt ATAPI.
- SCSI-Belegung durch andere HDD/CD-ROM wird erkannt.
- Voller MFM-Bus wird abgewiesen.
- Gueltige Fixed- und Dynamic-VHD-Checksummen werden akzeptiert.
- Manipulierte Footer- und Sparse-Checksummen werden abgewiesen.
- HddProfileStorage + IniConfig wurden mit Android-Stubs kompiliert und die
  genannten Tests ausgefuehrt.

Der Hotfix erzwingt gemaess Projektregel wieder einen sauberen
ARM64+x86_64 Native-/Compile-Neuaufbau; vcpkg, NDK, SDL-Quellen und Downloads
bleiben erhalten.
