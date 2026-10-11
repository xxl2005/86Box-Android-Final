# 86Box Android v0.9.33 – Startup/Thread-Assist, HID, Native-Log und 200-MHz-Polish

Version: 0.9.33 / versionCode 68

## Thread Assist v2 – Startreihenfolge behoben

Wenn Helper-Threads vor dem eigentlichen CPU-Thread registriert wurden, war die reservierte CPU-Maske noch leer. Dadurch erhielten die Helper vorübergehend alle Host-CPUs. v0.9.33 reserviert bereits in dieser Startphase synthetisch die staerksten zwei Host-CPUs (bei kleinen Systemen einen). Sobald die echte CPU-Thread-Affinity bekannt ist, ersetzt der normale Rebalance diese Startmaske.

Erwarteter Log beim fruehen Start:

`helper_pool_cpus=6 ... reserved_cpus=2 synthetic_reserved=1`

statt temporaer:

`helper_pool_cpus=8 ... reserved_cpus=0`

## Physische Maus – Composite-Keyboard-Schutz

Der bereits vorhandene MotionRange-Filter bleibt erhalten. Zusaetzlich werden explizit als alphabetische `keyboard`/`kbd`-Geraete identifizierte Composite-HIDs nicht fuer automatische Maus-Capture ausgewaehlt. Physische Tastatureingaben selbst werden dadurch nicht veraendert.

## Native-Log 0-Byte-Fix

Fruehe Android-Performance-Ausgaben konnten `stdlog` auf stdout festlegen, noch bevor `--logfile` geparst war. Spaeter wurde dann nicht mehr auf die eigentliche `native-*.log` umgeschaltet. v0.9.33 bindet den Logger nachtraeglich an `log_path`, sobald der Pfad verfuegbar ist, und oeffnet im Append-Modus.

## 200-MHz Adaptive CPU – Smooth v3

Der v0.9.32-Log zeigt, dass 200 MHz auf dem Testgeraet ueber laengere Abschnitte real mit 100 % laufen koennen. Deshalb wird 200 MHz nicht kuenstlich begrenzt. Der Adaptive-Regler verlangt nun drei statt zwei aufeinanderfolgende echte Speed-Samples unter 96 %, bevor das Ziel reduziert wird. Kurze Shader-/Audio-/Cache-Spitzen senken damit die Gastfrequenz seltener. Bei anhaltender echter Ueberlast greift der Regler weiterhin und schuetzt die Echtzeitgeschwindigkeit.

## Unveraendert

- Touch und Aufloesungs-Mapping
- virtuelle Tastatur
- Game-Key-Overlay inklusive Transparenz
- physische Tastatur
- rechte/linke Mausbuttons und Wheel
- Voodoo-/Renderer-Einstellungen
- ARM64 New-Dynarec-Hotpath aus v0.9.32
