# 86Box Android v0.9.25

## Thread-Assist CPPTHREADS-Fix
Android baut 86Box mit `CPPTHREADS=ON`; deshalb laeuft `thread_create_named()` ueber `src/thread.cpp`. v0.9.25 registriert und deregistriert die dort gestarteten 86Box-Threads nun direkt bei Android Thread Assist. Damit koennen Voodoo-, Audio- und weitere Helper-Threads wirklich vom Haupt-CPU-Thread getrennt werden.

## Frei konfigurierbares Game-Key-Overlay
Der neue Toolbar-Knopf `KEYS` oeffnet die Overlay-Konfiguration. Einzelne PC-Tasten (TAB, A-Z, 0-9, F1-F12, Pfeile, Modifier usw.) koennen hinzugefuegt oder entfernt werden.

- Normal: Taste ist nur waehrend der Beruehrung gedrueckt.
- HOLD: Ein Tap sendet Key-Down und haelt die Taste nach Loslassen weiter; der naechste Tap sendet Key-Up. So kann z.B. TAB fuer ein Inventar gehalten werden, waehrend anschliessend die Touch-Maus frei bedient wird.
- MOVE MODE: Overlay-Tasten lassen sich frei verschieben; Positionen werden relativ zum Bildschirm gespeichert.
- Beim Pause/Destroy werden alle gehaltenen Overlay-Tasten sicher freigegeben.
- DE/QWERTZ beruecksichtigt die logische Y/Z-Belegung.
