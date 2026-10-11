# 86Box Android v0.9.28 – True Auto / ADPF + Thread Assist v2

Version: 0.9.28 / versionCode 63

## Core Strategy Auto wirklich Android/ADPF

`Auto (Android/ADPF decides)` setzt den nativen CPU-Thread nicht mehr automatisch auf zwei Host-Kerne. Performance/Maximum behalten den angeforderten Nice-Wert und die ADPF Performance Hint Session, aber die originale von Android erlaubte CPU-Affinity wird wiederhergestellt. Damit kann Android den seriellen 86Box-CPU/Dynarec-Thread über alle erlaubten Kerne verschieben.

Prime, Big, Manual und Adaptive benutzen weiterhin ihre expliziten Kernmasken.

## Experimental Multi-Thread Assist v2

Die 86Box-Helferthreads werden nicht mehr einzeln auf einen festen Host-Kern gepinnt. Stattdessen erhalten Voodoo-/Audio-/IDE-/Geräte-Worker gemeinsam eine breite schnelle Helper-Maske. Android darf die Worker innerhalb dieses Pools selbst verteilen.

Wenn der Haupt-CPU-Thread im Auto-Modus die volle Host-Maske besitzt, reserviert Thread Assist v2 zwei der stärksten Host-CPUs als Scheduler-Headroom und baut den Helper-Pool aus den übrigen schnellen/geeigneten CPUs. Bei einer expliziten Prime/Big/Manual/Adaptive-Maske werden genau diese CPU-Kerne aus dem Helper-Pool entfernt.

Der Native-Log protokolliert u. a.:

- `affinity=android/adpf allowed=N`
- `THREAD_ASSIST v2 ... shared_pool=0x...`
- Anzahl erkannter und erfolgreich angewendeter Helper-Threads
- effektive reservierte CPU-Maske

## Kompatibilität

Touch, physische Maus/Tastatur, virtuelle Tastatur, Game-Key-Overlay, CD-Keepalive, Speed-Overlay, Voodoo-Thread-Auswahl und die bestehenden Performance-Diagnosen bleiben erhalten.
