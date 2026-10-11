# 86Box Android v0.8.1

## Profil-ZIP-Import-Button entfernt

Entfernt wurde ausschließlich der obere Profil-Button:

    IMPORT ZIP

Unveraendert erhalten bleiben:
- NEW
- IMPORT DESKTOP
- IMPORT ROM ZIP
- EXPORT ROMS
- Profil-Exportfunktionen
- dynamischer 86Box Hardware-Katalog
- ROM-Verwaltung
- ARM64 + x86_64 Build

Die interne alte Profil-ZIP-Importlogik bleibt im Quellcode erhalten, ist aber
nicht mehr ueber die normale Benutzeroberflaeche aufrufbar.

Der Fix betrifft nur Java/Android-GUI. Kein Native/JNI-Rebuild ist noetig.
