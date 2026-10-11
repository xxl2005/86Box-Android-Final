# 86Box Android v0.6.0

## Neuer Fix

Der Android-Build erreichte erstmals den echten 86Box-C/C++-Compile-Schritt.
Clang brach dort ab, weil `86BOX_ANDROID` als Präprozessor-Makro ungültig ist:
C/C++-Bezeichner dürfen nicht mit einer Ziffer beginnen.

Geändert:

```cmake
target_compile_definitions(86Box PRIVATE EMU_86BOX_ANDROID=1)
```

Der alte Name `86BOX_ANDROID` wurde im Quellcode nicht abgefragt; er kam nur an
dieser einen CMake-Stelle als Compiler-Definition vor.

v0.6.0 enthält außerdem weiterhin die bisherigen Android-Fixes:
- physischer NDK-Pfad ohne Leerzeichen
- GLib/Meson GDB-Autoload-Fix
- SDL globaler CMake-Pfadfix
- SDL VerifyGlobs-Pfadfix
- ZLIB::ZLIB-Kompatibilitätsfix
- Universal 64-bit: ARM64 + x86_64
- Vulkan / libslirp / Hardware-Input
