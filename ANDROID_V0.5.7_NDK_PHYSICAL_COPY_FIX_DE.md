# 86Box Android v0.5.7 – NDK Physical Copy Fix

## Problem
Auf Windows kann der Android-NDK-Pfad unter einem Benutzerprofil mit Leerzeichen liegen, z. B. `C:\Users\Example User\...`. vcpkg/Autotools expandiert 8.3-Kurzpfade bzw. Junctions beim CMake-Compiler-Setup wieder auf diesen physischen Pfad. `gettext-libintl` bricht dann beim `configure`-Schritt mit Exit-Code 77 ab.

## Fix
Das Build Center erzeugt einmalig eine **echte** NDK-Kopie an einem leerzeichenfreien Pfad auf dem Projektlaufwerk, z. B.:

`K:\86BoxAndroidToolchains\ndk-28.2.13676358`

Diese Kopie ist absichtlich keine Junction und kein 8.3-Alias. Danach setzt das Script `ANDROID_NDK_HOME`, `ANDROID_NDK_ROOT`, `ANDROID_NDK` und den CMake-Toolchainpfad auf diese Kopie. Zusätzlich werden nur die vcpkg-Caches `buildtrees\detect_compiler` und `buildtrees\gettext-libintl` entfernt, damit die alte Compilerpfaderkennung nicht wiederverwendet wird.

Die vorhandenen SDL/vcpkg-Downloads und bereits installierten Pakete bleiben bestehen.

## Erwartete Kontrolle
Beim nächsten Build muss die Compilerzeile einen Pfad wie

`Compiler found: K:/86BoxAndroidToolchains/ndk-28.2.13676358/.../clang++.exe`

enthalten. Ein erneuter Pfad `C:/Users/Example User/...` würde bedeuten, dass die alte Toolchain noch verwendet wird.
