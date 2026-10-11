# 86Box Android v0.8.6 - Bug Audit Fix

Gefundene und behobene Punkte nach statischer Pruefung von v0.8.5:

1. HDD-Dateiauswahl: Pending-Slot wird ueber Activity-Neustarts/Rotation erhalten.
2. Android Fullscreen wird auch nach abgebrochenen Dateidialogen wieder gesetzt.
3. IDE-Autobelegung erzeugt bei komplett belegten Kanaelen keine Doppelbelegung mehr.
4. Importierte HDI/HDX/VHD-Dateien werden vor Eintrag in 86box.cfg validiert.
5. 86box.cfg wird sicherer ueber Files.move/ATOMIC_MOVE ersetzt statt vorher geloescht.
6. VM -> Hardware-Konfigurator: Rueckkehr geht zuerst ueber den Profilmanager, damit
   SDL/86Box Zeit zum nativen Teardown hat und nicht parallel am Profil arbeitet.

Der Hotfix erzwingt gemaess Projektregel einen sauberen Native-/Compile-Rebuild
(ARM64 + x86_64), behaelt aber vcpkg/Downloads, NDK und SDL-Quellen.
