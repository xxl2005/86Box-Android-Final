# 86Box Android v0.5.6 – NDK/vcpkg Space-Path Fix

## Behobener Fehler
Beim ersten echten ARM64-Abhaengigkeitsbuild scheiterte `gettext-libintl:arm64-android` im Autotools-`configure` mit Exit-Code 77. vcpkg warnte unmittelbar davor, dass der Android-NDK-Compilerpfad Leerzeichen enthaelt (`C:\Users\Example User\...`).

## Loesung
`BUILD_APK.bat` benutzt fuer vcpkg und den nativen CMake-Android-Build jetzt einen leerzeichenfreien NDK-Pfad:

1. Zuerst wird ein vorhandener Windows-8.3-Kurzpfad verwendet, sofern er wirklich leerzeichenfrei und gueltig ist.
2. Falls kein brauchbarer Kurzpfad existiert, wird unter `third_party_android\ndk-r28c-no-spaces` automatisch eine NTFS-Verzeichnis-Junction auf das installierte NDK angelegt.
3. `ANDROID_NDK_HOME`, `ANDROID_NDK_ROOT`, `VCPKG_CHAINLOAD_TOOLCHAIN_FILE` und `ANDROID_NDK` verwenden danach ausschliesslich diesen Build-Pfad.
4. Android Studio/Gradle duerfen das echte SDK weiterhin unter `C:\Users\Example User\...` benutzen; es muss nichts verschoben oder neu installiert werden.

## Cache
Bereits heruntergeladene und erfolgreich gebaute vcpkg-Pakete bleiben erhalten. Der Build kann nach dem Hotfix mit Menuepunkt `[3]` erneut gestartet werden.

## Harmloser 404 im Log
Der vorherige 404 fuer `ftpmirror.gnu.org/automake-1.17.tar.gz` war kein Abbruch: vcpkg wechselte automatisch auf `ftp.gnu.org` und lud die Datei erfolgreich.
