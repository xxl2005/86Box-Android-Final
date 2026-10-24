# 86Box Android v0.11.3 – Game-Key-Overlay mit Maustasten

## Neu
- In **GAME KEY OVERLAY** gibt es zusätzlich zwei einzeln aktivierbare Tasten: **MAUS L** (linke Maustaste) und **MAUS R** (rechte Maustaste). Auf Englisch: **L-CLICK / R-CLICK**.
- Beides sind **echte emulierte Maustasten** (86Box-Mausmasken 1 und 2), **keine Tastatur-Scancodes**.
- Beide sind wie alle bisherigen Game-Keys separat verschiebbar (MOVE MODE), größenverstellbar (50–200 %) und nutzen die vorhandene Hintergrund-Transparenz.
- Standard ist kurzes/gedrücktes Klicken: Finger auflegen = Mouse Down; loslassen = Mouse Up. Optional **HOLD**: einmal tippen = dauerhaft drücken (z. B. Drag), erneut tippen = loslassen.
- Gleichzeitige Eingaben funktionieren: linke und rechte Overlay-Maustaste, Overlay + VM-Touch, Overlay + physische Maus. Physische Maus und Overlay können denselben Button unabhängig halten, ohne ein vorzeitiges Loslassen.
- Bei App-Wechsel, Fokusverlust, Konfigurationswechsel und Touch-Abbruch werden Tasten freigegeben und hängende Eingaben verhindert.

## Benutzung
1. 86Box starten, **GAME KEY OVERLAY** in den App-Einstellungen öffnen.
2. Haken bei **MAUS L** und/oder **MAUS R** setzen, **Apply** drücken. Alte Einstellungen bleiben erhalten; neue Maustasten sind zunächst optional und deaktiviert.
3. Bei Bedarf MOVE MODE einschalten und beide Buttons positionieren. MOVE MODE danach ausschalten, um zu klicken.
4. HOLD nur aktivieren, wenn die Maustaste nach dem Antippen eingerastet bleiben soll.

## Build-Status
- Reine Java-Eingabe- und Touchtracking-Tests wurden auf der Host-JVM ausgeführt.
- Der vollständige Android-Build wurde in [GitHub Actions](https://github.com/xxl2005/86Box-Android-Final/actions/runs/40042627752) mit SDK 35, NDK r28c und Gradle 8.13 erfolgreich abgeschlossen.
- Die [v0.11.3-Preview-APK](https://github.com/xxl2005/86Box-Android-Final/releases/tag/v0.11.3-preview) wurde auf App-Namen, Versionsnummer 0.11.3 (Buildcode 85), beide Prozessorarchitekturen und Signatur geprüft. Ein Android-Gerätetest wurde nicht durchgeführt.

## Codeänderungen
- `FloatingKeyOverlayView.java`: zwei Mouse-KeySpecs, echte Maustastenereignisse, Labels, Einstellungen und unabhängiges Touchtracking.
- `Box86Activity.java`: Weiterleitung über den bestehenden JNI-Mausbuttonpfad; Koordination mit physischer Maus und globale Freigabe.
- `MouseButtonSources.java`: getrennte Quellen und kombinierte Down-/Up-Transitions.
- `src/unix/sdl_main.c`: SDL-Touch-Klickpuls und Touch-Drag dürfen einen weiterhin gedrückten Overlay-Mausbutton nicht vorzeitig freigeben.
- `MouseButtonSourcesTest.java`: Host-JDK-Tests der Linksklick/Rechtsklick-Eingaben und Eigentümerwechsel.

Die Game-Key-Erweiterung ändert den 86Box-CPU-Kern, GPU-Renderer und die übrigen Game-Keys nicht. Für den reproduzierbaren APK-Build wurden zusätzliche Windows-Build-Kompatibilitätskorrekturen ergänzt.

