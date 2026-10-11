# 86Box Android v0.9.16 – Speed Overlay + Performance Polish

## Änderungen
- Profil-Manager: `IMPORT DESKTOP` heißt jetzt `IMPORT DESKTOP PROFILE`.
- Emulationsgeschwindigkeits-Anzeige standardmäßig leicht kleiner (90 % der bisherigen Größe).
- Prozent-Anzeige kann direkt mit dem Finger frei verschoben werden; Position wird relativ zum Bildschirm gespeichert und bei anderer Auflösung geklemmt.
- Neue Optionen in Touch Settings: Größe 60–160 %, Hintergrund-Deckkraft 20–100 %, Position zurücksetzen.
- Standard-Hintergrund bleibt 60 % deckend; Text bleibt vollständig lesbar.
- Aktualisierung der Prozent-Anzeige von 500 ms auf 1000 ms reduziert, um UI/JNI-Overhead etwas zu senken.
- Maximum-Modus fordert für den nativen 86Box-CPU-Thread `nice=-10` statt `-8` an. Falls Android dies ablehnt, läuft 86Box sicher weiter und protokolliert den tatsächlichen Wert.

## Hintergrund
Der v0.9.15-Komplettlog des OnePlus 15 zeigt Qualcomm Oryon / SM8850 mit 8 CPUs bis 4,61 GHz. Maximum bindet den Haupt-Emulationsthread erfolgreich an 2/8 schnelle Kerne. Die zusätzliche Prioritätsstufe ist deshalb bewusst klein gehalten; weitere große Gewinne würden eher Änderungen am ARM64-New-Dynarec erfordern.
