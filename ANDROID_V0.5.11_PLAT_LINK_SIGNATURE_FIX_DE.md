# 86Box Android v0.6.0

## CMake-Fix: target_link_libraries Signatur für `plat`

Der v0.5.10 SDL3-Header-Fix funktionierte grundsätzlich:
`plat` erhielt `SDL3::Headers`.

CMake brach jedoch während der Konfiguration ab, weil für dasselbe Target
zwei verschiedene `target_link_libraries()`-Signaturen verwendet wurden.

Neu:

```cmake
target_link_libraries(plat ${_86BOX_SDL3_INTERFACE_TARGET})
```

Damit verwendet der neue SDL3-Aufruf dieselbe plain signature wie der
bestehende Aufruf:

```cmake
target_link_libraries(plat ${CMAKE_DL_LIBS})
```

Alle bisherigen Android-Fixes bleiben enthalten.
