# 86Box Android v0.5.0 – VM-/Hardwareprofile + Desktop-Import

v0.5.0 baut auf v0.4.0 auf. Multi-Core Stage 2, Vulkan/GLES-Fallback, Touch, physische Maus/Tastatur, libslirp, CUE/BIN und der PERF-Profiler bleiben enthalten.

## Neu: VM-/Hardwareprofil-Manager

Die App startet nun zuerst in einem Profil-Manager. Jede virtuelle Maschine erhält ein eigenes Verzeichnis unter:

`files/86box/profiles/<profil>/`

Darin liegen unter anderem:

- `86box.cfg` – komplette emulierte Hardwarekonfiguration
- NVR/CMOS/BIOS-Zustand und weitere VM-lokale Dateien
- `media/floppy/`
- `media/cdrom/`
- `media/hdd/`
- `profile.properties` – Android-Anzeigename/Quelle

Die gemeinsamen 86Box-ROMs und Assets bleiben außerhalb der Profile und werden nicht unnötig dupliziert.

## Profilfunktionen

- Neues Profil anlegen
- Profil starten
- Umbenennen
- Duplizieren
- Löschen
- Als ZIP exportieren
- Profil-ZIP importieren
- Zur Profilübersicht aus der laufenden VM über den `VM`-Toolbarbutton zurückkehren

## Desktop-86Box-Profile auf Android laden

`IMPORT DESKTOP` öffnet Androids Ordnerauswahl (Storage Access Framework). Wähle den Desktop-VM-Ordner, in dem `86box.cfg` liegt. Der komplette Ordner wird in den privaten Android-Profilbereich kopiert.

Beim Import wird:

1. `86box.cfg` gesucht und geprüft.
2. Das unveränderte Original als `86box.desktop-original.cfg` gesichert.
3. Relative Windows-Pfade wie `disks\\win98.vhd` in Android-kompatible `disks/win98.vhd` umgewandelt.
4. Absolute Windows-/Linux-Pfade nach Möglichkeit auf mitkopierte Dateien im Profil abgebildet.
5. CUE-Dateien auf `/`-Pfadtrenner normalisiert.
6. Ein `IMPORT_REPORT.txt` mit nicht auflösbaren externen Pfaden angelegt.

So lassen sich normale Desktop-86Box-VMs übernehmen, wenn ihre Laufwerksimages zusammen mit der VM im ausgewählten Ordner liegen.

### Externe Desktop-Laufwerke

Wenn ein Desktop-Profil beispielsweise `D:\\VMs\\disk.vhd` referenziert, die VHD aber **außerhalb** des importierten VM-Ordners liegt, kann Android sie beim Ordnerimport nicht automatisch mitkopieren. Der ursprüngliche Eintrag wird deshalb nicht still gelöscht. Er erscheint im `IMPORT_REPORT.txt`; anschließend kann das Image über die Android-Medienauswahl importiert/neu zugeordnet werden.

`ioctl://`-Hostgeräte sind plattformspezifisch und können nicht von Windows/Linux auf Android übernommen werden.

## Alte Android-v0.4-Konfiguration

Beim ersten v0.5.0-Start wird eine vorhandene alte Einzel-VM automatisch in das Profil `Legacy v0.4` migriert. ROMs und Assets bleiben gemeinsam erhalten.

## APK

Universal64 bleibt Standard:

`86Box-Android-universal64-v0.5.0-debug.apk`

Enthaltene ABIs:

- `arm64-v8a`
- `x86_64`
