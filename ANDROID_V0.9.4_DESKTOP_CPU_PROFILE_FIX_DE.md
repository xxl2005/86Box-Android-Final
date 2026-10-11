# 86Box Android v0.9.4 – Desktop-CPU-Profil Fix

## Behobener Fehler
Bei importierten Desktop-86Box-Profilen konnte die Profil-Info z. B. **200 MHz** anzeigen,
während der Android Hardware Configurator auf den ersten CPU-Eintrag (z. B. **66 MHz**)
zurückfiel. Wurde danach gespeichert, konnte dadurch die korrekte Desktop-CPU-Konfiguration
überschrieben werden.

## Ursache
Der Android-Configurator berücksichtigte beim Wiederherstellen der CPU nur `cpu_speed`.
Wenn kein Treffer ausgewählt wurde, setzte der allgemeine Spinner-Code pauschal Eintrag 0.
Desktop-86Box identifiziert die konfigurierte CPU dagegen primär über die Kombination aus
`cpu_speed` und `cpu_multi` und verwendet danach eine definierte kompatible Fallback-Regel.

## Fix in v0.9.4
- `cpu_speed` **und** `cpu_multi` werden beim Laden des Profils berücksichtigt.
- Exakte Kombination aus Takt + Multiplikator wird bevorzugt.
- Ohne exakten Treffer wird – wie in `src/config.c` – die erste kompatible CPU mit mindestens
  dem angeforderten Takt gewählt, andernfalls die schnellste kompatible CPU.
- Der alte pauschale CPU-Fallback auf Spinner-Eintrag 0 wurde entfernt.
- Beim Wechsel von Mainboard/Filter bleibt auch der Multiplikator der aktuellen CPU erhalten.

## Regressionstest
Ein gültiges Desktop-Profil mit z. B.

```ini
[Machine]
cpu_family = pentium_p54c
cpu_speed = 200000000
cpu_multi = 3
```

muss im Hardware Configurator den passenden **200-MHz**-CPU-Eintrag auswählen, sofern diese
CPU für das gewählte Mainboard kompatibel ist. SAVE darf `cpu_speed`/`cpu_multi` nicht auf
den ersten CPU-Eintrag zurücksetzen.
