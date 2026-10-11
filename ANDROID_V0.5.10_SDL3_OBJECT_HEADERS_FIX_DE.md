# 86Box Android v0.6.0

## SDL3-Header-Fix für OBJECT-Libraries

Der ARM64-Build erreichte 1114/1138 Objekte und brach in
`src/unix/sdl_main.c` ab:

    fatal error: 'SDL3/SDL.h' file not found

Ursache:
`SDL3::Headers` war nur an das finale `86Box`-Target gebunden.
Die Targets `plat`, `ui` und `osd_backend` sind OBJECT-Libraries und
kompilieren separat; sie erben die Include-Pfade des finalen Targets nicht.

Fix:
- SDL3-Interface-Target wird ermittelt (`SDL3::Headers` bevorzugt).
- `plat` erhält den Target direkt.
- `ui` und `osd_backend` erhalten denselben Target ebenfalls.
- Fallback auf `SDL3_INCLUDE_DIRS`, falls kein SDL3-Target vorhanden ist.

Alle bisherigen Fixes aus v0.5.9 bleiben enthalten.
