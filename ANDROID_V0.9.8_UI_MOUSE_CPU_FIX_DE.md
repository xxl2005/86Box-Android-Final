# 86Box Android v0.9.8 – CPU-Anzeige, responsive UI, VM-Skalierung und physische Maus

## 1. Desktop-Profil CPU-Anzeige (200 MHz -> 66 MHz)
Der v0.9.7-Komplettlog zeigt, dass `cpu_speed=200000000` und `cpu_multi=3` korrekt geladen und zunächst als **200 MHz** ausgewählt werden. Erst nach Freigabe der Start-Sperre treffen verspätete Android-Spinner-Callbacks ein. Ein unveränderter CPU-Family-Callback baut die Frequenzliste ohne bevorzugten Wert erneut auf und landet auf Zeile 0 (**66 MHz**).

v0.9.8 verwendet deshalb nicht mehr nur eine Zeit-/Frame-Sperre. Für Machine Type, Machine, CPU Family und CPU Speed wird zusätzlich ein stabiler Selection-Key gespeichert. Ein Callback löst abhängige Aktualisierungen nur aus, wenn sich der tatsächliche ausgewählte Wert geändert hat. Verspätete, inhaltlich unveränderte Start-Callbacks werden ignoriert und protokolliert.

## 2. Automatische UI-Skalierung
Profile Manager, Hardware Configurator und VM-Overlay verwenden adaptive dp-Skalierung abhängig von `screenWidthDp`/`screenHeightDp`. Auf kompakten Displays werden Aktionszeilen vertikal angeordnet; auf großen Tablets werden Bedienelemente leicht vergrößert.

Der Profile Manager ist als komplette Seite scrollbar. Touch-/Input-Einstellungen und das VM-Display-Menü sind ebenfalls scrollbar. Der rechte VM-Touch-Toolbar bleibt ein vertikal scrollbareres Overlay mit sichtbarer Scrollbar.

## 3. VM-Bildgröße / Seitenverhältnis
Im VM-Overlay gibt es neu **VIEW**. Dort stehen zur Verfügung:
- Stretch to screen
- Force 4:3
- Auto Fit / keep guest ratio (Standard)
- Integer scale
- Integer 4:3
- VM image size 50–300 %
- AUTO FIT + 100 %

Die Auswahl wird gespeichert und über die Android-Bridge direkt an den SDL-Renderer übergeben. Werte unter 100 % zeigen ein zentriertes kleineres VM-Bild; Werte über 100 % zoomen und können per Zwei-Finger-Pan verschoben werden.

## 4. Physische Maus in der VM
Die Android-Eingabeschicht fordert Pointer Capture jetzt nicht erst beim Klick an, sondern auch bei Mouse Hover/Move über der VM-Fläche. Relative Achsen (`AXIS_RELATIVE_X/Y`) werden bevorzugt; `SOURCE_MOUSE_RELATIVE` und absolute Delta-Berechnung dienen als Fallbacks.

Zusätzlich zeichnet der Komplett-Logger begrenzt Java-MouseEvents, Capture-Status und berechnete Deltas auf. Im nativen 86Box-Log werden die ersten Hardware-Mouse-Move/Button/Wheel-Ereignisse samt `mouse_ready` protokolliert. Damit lässt sich bei einem gerätespezifischen Problem eindeutig erkennen, ob ein Event bei Android, JNI oder dem 86Box-Mouse-Core verloren geht.

## 5. Nebenbefund aus v0.9.7-Log
Das Desktop-Profil enthält noch einen nicht auf Android verfügbaren Linux-CD-ROM-Pfad (`/home/tobias/.../LLGD1.cue`). Dieser ist unabhängig von der CPU-/Mauskorrektur und muss auf Android neu gemountet werden. Ein Native-Log meldete dabei außerdem fehlendes `libaaruformat.so` für den betreffenden Image-Ladeversuch.
