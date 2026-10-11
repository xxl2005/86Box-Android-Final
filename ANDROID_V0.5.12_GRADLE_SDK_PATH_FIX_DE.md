# 86Box Android v0.6.0

## Gradle / local.properties SDK-Pfad-Fix

Beide nativen Bibliothekssätze bauen inzwischen erfolgreich:

- arm64-v8a: libmain.so + libSDL3.so
- x86_64: libmain.so + libSDL3.so

Der Build brach erst beim Android-Gradle-Plugin ab:

    java.io.IOException:
    The filename, directory name, or volume label syntax is incorrect

Ursache:
Die bisherige PowerShell-Zeile verwendete:

    .Replace('\\','/')

Bei .NET String.Replace bedeutet das zwei Backslashes als Suchtext.
Ein normaler Windows-Pfad enthält aber einzelne Backslashes. Dadurch wurde
`sdk.dir` mit rohen Backslashes in `local.properties` geschrieben.

Java-Properties interpretiert Backslashes als Escape-Zeichen, wodurch der
SDK-Pfad beschädigt wurde.

v0.6.0 schreibt den SDK-Pfad nun sicher als:

    sdk.dir=C:/Users/.../AppData/Local/Android/Sdk

Dabei wird `ANDROID_SDK_ROOT` direkt aus der Umgebung gelesen, in einen
vollen Pfad normalisiert und jeder einzelne Backslash durch `/` ersetzt.

Alle bisherigen Native-, SDL3-, ZLIB-, GLib- und NDK-Fixes bleiben enthalten.
