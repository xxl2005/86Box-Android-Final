# 86Box Android v0.9.12 – Physische Maus: SDL-Surface Pointer-Capture

## Ursache aus dem v0.9.10 Komplettlog

Die Dell Mouse wurde vom Android InputManager erkannt und Android meldete Pointer Capture als aktiv. Im selben Lauf erschien aber kein einziger `MouseEvent captured`, `Physical mouse delta` oder `HW_MOUSE_MOVE`-Eintrag. Damit lag der Fehler vor JNI: Die Capture-Anforderung lief auf dem Activity/Decor-View, während Android erfasste Mausereignisse an die fokussierte View ausliefert. Bei SDL ist das die SDL SurfaceView.

Zusätzlich wurde die Logitech G915 Tastatur zeitweise fälschlich als Maus-Kandidat erkannt, weil zusammengesetzte Input-Source-Bitmasken die Maus-Bits enthalten konnten. Das verursachte unnötige Capture-Wechsel.

## Fix

- SDL `SurfaceView` wird im laufenden View-Baum automatisch gefunden.
- `OnCapturedPointerListener` wird direkt an dieser SDL-Surface installiert.
- `requestPointerCapture()` und `releasePointerCapture()` laufen auf derselben SDL-Surface statt pauschal auf dem Decor-View.
- Der Capture-Target-Klassenname wird mitgeloggt.
- Beim Resume wird der Capture-Target nochmals nach Aufbau des SDL-View-Baums installiert.
- Auto-Erkennung einer physischen Maus verlangt jetzt zusätzlich echte Maus-Bewegungsachsen. Composite-Keyboards ohne Pointer-Achsen lösen kein Auto-Capture mehr aus.
- Bestehende native Kette bleibt erhalten: captured MotionEvent -> JNI -> HW_MOUSE_MOVE/BUTTON/WHEEL -> 86Box mouse core / PS/2 guest mouse.

Version: 0.9.12 / versionCode 47
