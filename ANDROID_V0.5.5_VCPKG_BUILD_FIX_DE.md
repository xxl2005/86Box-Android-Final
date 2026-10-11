# 86Box Android v0.5.5 – vcpkg Build-Fix

Der erste reale Windows/Android-Build mit v0.5.4 zeigte, dass die aktuelle vcpkg-Version im Projektordner automatisch `vcpkg.json` erkennt und damit Manifest Mode aktiviert. Einzelne Paketargumente (`vcpkg install pkgconf:x64-windows`) sind im Manifest Mode nicht erlaubt.

Der Android-Build ist jedoch bewusst als Classic-Mode-Build aufgebaut: CMake wird bereits mit `-DVCPKG_MANIFEST_MODE=OFF` konfiguriert und erwartet die Bibliotheken unter `%VCPKG_ROOT%\installed\<triplet>`.

v0.5.5 setzt deshalb bei allen expliziten Paketinstallationen `--classic`:

- `pkgconf:x64-windows`
- ARM64 Android: freetype, libpng, libsndfile, openal-soft, libslirp
- x86_64 Android: freetype, libpng, libsndfile, openal-soft, libslirp

Damit verwenden Installation und CMake denselben vcpkg-Modus und denselben Installationsbaum.
