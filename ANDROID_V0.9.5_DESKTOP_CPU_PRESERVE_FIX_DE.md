# 86Box Android v0.9.5 – Desktop-CPU Preserve Fix

## Problem
Ein importiertes Desktop-Profil konnte in der Profil-Info korrekt z. B. 200 MHz anzeigen, waehrend der Hardware Configurator weiterhin 66 MHz zeigte.

## Ursache
Der v0.9.4-Fix verbesserte die Auswahl von `cpu_speed` + `cpu_multi`, setzte aber voraus, dass die konfigurierte CPU-Familie und CPU-Zeile bereits im kompatibilitaetsgefilterten Android-Katalog sichtbar waren. War die Familie oder die konkrete CPU-Zeile dort ausgeblendet, fiel der Spinner weiterhin auf den ersten sichtbaren Eintrag zurueck.

## Fix
- Beim initialen Oeffnen eines importierten Desktop-Profils wird die konfigurierte `cpu_family` aus dem vollen kompilierten 86Box-Katalog wieder in die sichtbare Liste aufgenommen, falls sie nur durch den Filter fehlt.
- Dasselbe gilt fuer die exakt konfigurierte CPU-Zeile (`cpu_speed` + `cpu_multi`).
- Es wird nur der konfigurierte ausgeblendete Eintrag ergaenzt; andere inkompatible CPUs bleiben verborgen.
- Sobald der Benutzer Mainboard oder CPU selbst aendert, gilt wieder der normale Kompatibilitaetsfilter.

Damit bleibt ein importiertes 200-MHz-Profil beim blossen Oeffnen des Hardware Configurators als 200 MHz sichtbar und wird nicht mehr still auf 66 MHz umgebogen.
