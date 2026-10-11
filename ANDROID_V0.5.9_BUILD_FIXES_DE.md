# 86Box Android v0.6.0 – Build-Kompatibilitätsfixes

v0.6.0 bündelt die beim ersten echten Android-Build gefundenen
Windows-Host/CMake/vcpkg-Korrekturen direkt im vollständigen Projekt.

- physische NDK-Kopie ohne Leerzeichen
- GLib/Meson GDB-Autoload-Fix für Windows-Host + Android-Cross-Build
- SDL3-Pfadnormalisierung für try_compile() und VerifyGlobs.cmake
- ZLIB::ZLIB-Kompatibilität für vcpkg Freetype/PNG
- CMake 4.4 CMP0219 auf NEW, falls die Policy vorhanden ist
- ARM64 + x86_64 Universal-APK-Build bleibt aktiv

Build:
  android\BUILD_APK.bat
  [3] DEBUG UNIVERSAL APK bauen

SDL3/vcpkg/Gradle werden beim ersten Build wie bisher geladen. Die
notwendigen Drittanbieter-Patches werden automatisch und idempotent über
android\patches\APPLY_ANDROID_BUILD_FIXES.ps1 angewendet.
