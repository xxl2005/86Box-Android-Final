# 86Box Android v0.9.7 – verzögerte Spinner-Callbacks / 200-MHz-Fix

Der v0.9.6-Komplettlog hat die Ursache des 66-MHz-Problems eindeutig gezeigt:

- Desktop-Profil importiert korrekt `cpu_speed = 200000000` und `cpu_multi = 3`.
- Hardware-Katalog findet korrekt Celeron Mendocino 200 MHz ×3.
- Der Frequency-Spinner wird zunächst korrekt auf 200 MHz gesetzt.
- Nach `onCreate()` liefert Android jedoch noch verzögerte `Spinner.onItemSelected`-Callbacks.
- Ein verzögerter CPU-Family-Callback rief `refreshCpuSpeed(null, null, null, false)` auf.
- Ohne Preferred-CPU fiel der Adapter auf Zeile 0 = 66 MHz zurück.

## Fix

Während der initialen UI-Befüllung bleiben Spinner-Mutationscallbacks gesperrt. Die Sperre
wird erst nach zwei UI-Animationsframes aufgehoben, wenn die verzögerten Start-Callbacks
abgearbeitet sind. Danach reagieren die Spinner wieder normal auf echte Benutzerauswahl.

Der Komplett-Logger bleibt enthalten und protokolliert unter `CpuTrace`, welche verzögerten
Callbacks unterdrückt wurden sowie den Zeitpunkt, an dem die Sperre freigegeben wird.
