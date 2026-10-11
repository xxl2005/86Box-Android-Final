# 86Box Android v0.8.3

## Sichtbarer EXPORT PROFILE ZIP Button

Die Profil-ZIP-Exportfunktion war bereits im Drei-Punkte-Menue jedes Profils
vorhanden. v0.8.3 macht sie nun auch direkt im oberen VM-Manager sichtbar.

Neue, klar getrennte Anordnung:

    NEW                 | IMPORT DESKTOP
    IMPORT PROFILE ZIP  | EXPORT PROFILE ZIP
    IMPORT ROM ZIP      | EXPORT ROMS

Beim Antippen von EXPORT PROFILE ZIP:
1. Liste aller vorhandenen Profile anzeigen.
2. Gewuenschtes Profil waehlen.
3. Android-Speicherdialog oeffnen.
4. Komplettes Profil als ZIP exportieren.

Der bestehende Export ueber das Drei-Punkte-Menue bleibt ebenfalls erhalten.

Der Fix betrifft nur die Android-Java-Oberflaeche.
Kein Native/JNI-Rebuild ist erforderlich.
