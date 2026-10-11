# 86Box Android v0.9.11 – Touch-/Dateidialog-Lifecycle-Fix

## Touch
- SDL erzeugt auf Android keine synthetischen Mausereignisse mehr aus Fingerbewegungen.
- Die eigene Touchpad-Steuerung bleibt allein zuständig.
- Linksklick erfolgt ausschließlich beim Finger-Loslassen, wenn keine Drag-Schwelle überschritten wurde.
- Die Drag-Erkennung summiert kleine Bewegungen und verwendet eine niedrigere 0.8%-Schwelle.
- Swipe = nur Mausbewegung; Tap = Linksklick; Doppeltap = Doppelklick; 2 s halten = Rechtsklick.

## Android-Dateidialog / laufende VM
- SDL3 Android `SDL_ANDROID_BLOCK_ON_PAUSE` wird vor SDL_Init auf 0 gesetzt.
- Die VM wird weiterhin über die eigene 86Box-Lifecycle-Pause angehalten.
- Der Foreground-Keepalive aus v0.9.9 bleibt aktiv.
- Zusätzliche Lifecycle-Logs zeigen jetzt vor/nach der nativen Pause sowie nach Rückkehr aus SDLActivity.onPause(), falls der Prozess erneut verschwindet.

Version: 0.9.11 / versionCode 46

## Prozess-Enddiagnose
- Ab Android 11 protokolliert der naechste App-Start bis zu drei historische Prozess-Endgruende via ApplicationExitInfo.
- Dadurch wird ein erneutes Verschwinden der VM als LOW_MEMORY, ANR, CRASH, SIGNALED, USER_REQUESTED usw. sichtbar.

## Native 86Box Pause
- Der Android-Lifecycle ruft nicht mehr das synchron blockierende `do_pause(1)` auf.
- Android verwendet `do_pause_android_lifecycle()`: Pause wird angefordert, der CPU-Thread quittiert asynchron.
- Desktop-/normale 86Box-Pause verwendet weiterhin unveraendert `do_pause()`.
- Verhindert Busy-Wait/ANR im UI-Thread beim Wechsel zu DocumentsUI/Home.
