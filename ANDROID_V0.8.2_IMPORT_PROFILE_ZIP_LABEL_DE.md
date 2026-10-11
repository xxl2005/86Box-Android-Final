# 86Box Android v0.8.2

## Profil-ZIP-Import wieder sichtbar und eindeutig beschriftet

Der Profil-ZIP-Import war technisch weiterhin vorhanden, wurde in v0.8.1
aber aus der Benutzeroberflaeche entfernt.

v0.8.2 stellt den Button wieder her und benennt ihn eindeutig:

    IMPORT PROFILE ZIP

Dadurch ist jetzt klar getrennt:

    IMPORT PROFILE ZIP   -> komplettes 86Box-VM-/Hardwareprofil importieren
    IMPORT ROM ZIP       -> gemeinsame 86Box-ROM-Bibliothek importieren

Unveraendert erhalten bleiben:
- NEW
- IMPORT DESKTOP
- IMPORT ROM ZIP
- EXPORT ROMS
- Profil-Export
- dynamischer 86Box Hardware-Katalog
- Full catalogue / All machine types
- ARM64 + x86_64 Universal Build

Der Fix betrifft nur die Android-Java-Oberflaeche. Kein Native/JNI-Rebuild
ist erforderlich.
