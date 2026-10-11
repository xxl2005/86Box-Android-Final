# 86Box Android v0.8.4

## Android Hardware-Katalog: Header-Reihenfolge korrigiert

Der frische v0.8.3 Build erreichte den nativen 86Box-Build bis 1119/1138 und
scheiterte dann in `src/unix/android_bridge.c`.

Fehler:
    network.h: unknown type name 'mutex_t'
    network.h: unknown type name 'pc_timer_t'

Ursache:
`86box/network.h` verwendet Typen aus:
    86box/thread.h
    86box/timer.h

Die neue Android-Hardwarekatalog-JNI-Bruecke band `network.h` jedoch vor
diesen beiden Headern ein.

Fix:
    #include <86box/thread.h>
    #include <86box/timer.h>
    #include <86box/network.h>

Diese Reihenfolge entspricht auch den bestehenden 86Box-Netzwerk-/Qt-Dateien.

Wichtig:
- GLib 2.90.0 baute im neuen Test bereits erfolgreich.
- libslirp baute ebenfalls erfolgreich.
- vcpkg/NDK/SDL werden nicht geloescht.
- Nur android_bridge.c muss neu kompiliert und libmain.so neu gelinkt werden.

Danach:
    android\BUILD_APK.bat
    [3] DEBUG UNIVERSAL APK bauen

Nicht [4] oder [5].
