# 86Box Android v0.8.0 – Vollständiger Core-Hardwarekatalog

## Warum v0.7.x noch klein wirken konnte

Der Android-Konfigurator hatte bereits eine JNI-Abfrage, aber bei einem über viele Hotfixes weitergepflegten Projekt konnte ein älteres `libmain.so` weiterverwendet werden. Zusätzlich zeigte die Oberfläche standardmäßig nur Geräte, die zur aktuell gewählten Hauptplatine kompatibel sind.

## Neu in v0.8.0

- sichtbarer Core-Selbsttest statt stiller Fallback-Liste
- Anzeige der echten Kataloggrößen direkt oben im Hardware-Konfigurator
- `All machine types` zeigt die komplette Motherboardliste in einem Dropdown
- `Full catalogue` zeigt auf Wunsch auch zum Board inkompatible Hardware zum Durchsuchen/Testen
- CPU-Familien und CPU-Modelle können im Full-Catalog-Modus vollständig betrachtet werden
- zusätzliche Desktop-Kategorien:
  - COM1–COM4 + Geräteauswahl
  - LPT1–LPT4 + Geräteauswahl
  - ISA RTC/Clock-Karten
  - ISA RAM-Erweiterungen 1–4
  - MCA RAM-Erweiterungen 1–4
  - ISA ROM-Karten 1–4
  - Bugger, POST Card, Unit Tester, Novell Key Card, Soft Power
- weiterhin dynamisch aus den 86Box-Core-Registries; keine kleine handgeschriebene Java-Hardwareliste

## Wichtig

Der normale Modus filtert wie der Desktop-Konfigurator nach Mainboard-/Bus-Kompatibilität. Der Full-Catalog-Modus ist bewusst eine Expertenansicht; inkompatible Kombinationen können nicht booten.

Der v0.8.0-Hotfix erzwingt gezielt nur den Neuaufbau des Android-`plat`-Objekttargets und von `libmain.so`. vcpkg, NDK, SDL-Abhängigkeiten und die restlichen Native-Builds bleiben erhalten.
