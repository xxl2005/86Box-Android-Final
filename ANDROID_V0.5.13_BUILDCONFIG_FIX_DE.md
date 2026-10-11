# 86Box Android v0.6.0

## Android Gradle Plugin BuildConfig-Fix

Der Native-Build ist für beide ABIs erfolgreich:

- arm64-v8a: `libmain.so` + `libSDL3.so`
- x86_64: `libmain.so` + `libSDL3.so`

Gradle erreicht anschließend `:app:compileDebugJavaWithJavac`, scheitert
aber in `DiagnosticLogger.java`, weil `BuildConfig` nicht erzeugt wurde.

Fehler:

```text
error: cannot find symbol
BuildConfig.VERSION_NAME
BuildConfig.VERSION_CODE
BuildConfig.BUILD_TYPE
```

Ursache:
Das Projekt verwendet Android Gradle Plugin 8.13.2. Für die App war die
BuildConfig-Generierung nicht explizit aktiviert.

Fix in `android/app/build.gradle`:

```gradle
buildFeatures {
    buildConfig true
}
```

Damit erzeugt AGP wieder `net.box86.android.BuildConfig`, das im gleichen
Package wie `DiagnosticLogger.java` liegt.

Alle bisherigen Fixes bleiben enthalten.
