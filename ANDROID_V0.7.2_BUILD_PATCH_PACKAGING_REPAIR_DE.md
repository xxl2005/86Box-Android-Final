# 86Box Android v0.7.2

## Reparatur des zentralen Android-Build-Patch-Skripts

Beim Upgrade ueber den kleinen v0.7.0 -> v0.7.1 Hotfix wurde
`android/BUILD_APK.bat` aktualisiert, aber die von ihm erwartete Datei

    android/patches/APPLY_ANDROID_BUILD_FIXES.ps1

nicht mit in den Hotfix gepackt.

Das COMPLETE-ZIP v0.7.1 enthielt die Datei bereits korrekt. Betroffen waren
vor allem bestehende, ueber mehrere Hotfixes weitergepflegte Arbeitsordner.

v0.7.2:
- liefert das zentrale Patch-Skript auch im Reparatur-Hotfix mit,
- behaelt ROM ZIP Import/Export aus v0.7.1,
- behaelt den dynamischen Hardware-Katalog aus v0.7.0,
- erkennt unsere aelteren SDL-Pfadfixe und vermeidet dann einen unnoetigen
  kompletten Native-Rebuild,
- behaelt vcpkg, NDK und Downloads.

Nach Anwendung:
    android\BUILD_APK.bat
    [3] DEBUG UNIVERSAL APK bauen
