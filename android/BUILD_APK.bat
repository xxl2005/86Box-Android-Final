@echo off
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul

for %%I in ("%~dp0..") do set "ROOT=%%~fI"
set "ANDROID_DIR=%ROOT%\android"
set "THIRD=%ROOT%\third_party_android"
set "SDL_DIR=%THIRD%\SDL"
set "VCPKG_ROOT=%THIRD%\vcpkg"
set "PATCH_SCRIPT=%ANDROID_DIR%\patches\APPLY_ANDROID_BUILD_FIXES.ps1"
set "NATIVE_BUILD_ARM64=%ROOT%\build-android-arm64"
set "NATIVE_BUILD_X64=%ROOT%\build-android-x86_64"
set "JNI_ARM64=%ANDROID_DIR%\app\src\main\jniLibs\arm64-v8a"
set "JNI_X64=%ANDROID_DIR%\app\src\main\jniLibs\x86_64"
set "SDL_JAVA_DST=%ANDROID_DIR%\app\src\main\java\org\libsdl\app"

rem Keep Build Center/APK filenames tied to Gradle's canonical app version.
set "APP_VERSION="
set "APP_VERSION_CODE="
for /f "tokens=2" %%V in ('findstr /C:"versionName" "%ANDROID_DIR%\app\build.gradle"') do set "APP_VERSION=%%V"
if defined APP_VERSION set "APP_VERSION=!APP_VERSION:'=!"
for /f "tokens=2" %%V in ('findstr /R /C:"^[ ]*versionCode [0-9][0-9]*" "%ANDROID_DIR%\app\build.gradle"') do set "APP_VERSION_CODE=%%V"
if not defined APP_VERSION set "APP_VERSION=0.11.1-original-cpu-cycles-audited"
if not defined APP_VERSION_CODE set "APP_VERSION_CODE=76"

set "OUT_UNIVERSAL=%ROOT%\86Box-Android-universal64-v%APP_VERSION%-debug.apk"
set "OUT_ARM64=%ROOT%\86Box-Android-arm64-v%APP_VERSION%-debug.apk"
set "OUT_X64=%ROOT%\86Box-Android-x86_64-v%APP_VERSION%-debug.apk"
set "NDK_VERSION=28.2.13676358"
set "GRADLE_VERSION=8.13"
set "SDL_TAG=release-3.4.16"
set "NINJA_VERSION=1.13.1"

if /I "%~1"=="build" goto autobuild
if /I "%~1"=="diagnose" goto autodiag

:menu
cls
echo ============================================================
echo  86Box Android v%APP_VERSION% - APK BUILD CENTER
echo  UNIVERSAL 64-BIT: ARM64 + x86_64 / Vulkan / libslirp / HW input
echo ============================================================
echo.
echo [1] Diagnose
echo [2] Android SDK + NDK Komponenten installieren
echo [3] DEBUG UNIVERSAL APK bauen ^(ARM64 + x86_64^)
echo [4] Native Builds neu bauen ^(CMake clean^)
echo [5] Komplett aufraeumen ^(Build/Downloads bleiben getrennt^)
echo [6] APK-Ausgabeordner anzeigen
echo [0] Ende
echo.
set /p "CHOICE=Auswahl: "
if "%CHOICE%"=="1" (
    call :diagnose
    pause
    goto menu
)
if "%CHOICE%"=="2" (
    call :install_sdk
    pause
    goto menu
)
if "%CHOICE%"=="3" (
    call :build_apk
    pause
    goto menu
)
if "%CHOICE%"=="4" (
    call :clean_native
    pause
    goto menu
)
if "%CHOICE%"=="5" (
    call :clean_all
    pause
    goto menu
)
if "%CHOICE%"=="6" (
    explorer "%ROOT%"
    goto menu
)
if "%CHOICE%"=="0" exit /b 0
goto menu

:autobuild
call :build_apk
exit /b %ERRORLEVEL%

:autodiag
call :diagnose
exit /b %ERRORLEVEL%

:locate_sdk
set "SDK="
if defined ANDROID_SDK_ROOT if exist "%ANDROID_SDK_ROOT%" set "SDK=%ANDROID_SDK_ROOT%"
if not defined SDK if defined ANDROID_HOME if exist "%ANDROID_HOME%" set "SDK=%ANDROID_HOME%"
if not defined SDK if exist "%LOCALAPPDATA%\Android\Sdk" set "SDK=%LOCALAPPDATA%\Android\Sdk"
if defined SDK set "ANDROID_SDK_ROOT=%SDK%"
exit /b 0

:locate_sdkmanager
set "SDKMANAGER="
if not defined SDK exit /b 1
if exist "%SDK%\cmdline-tools\latest\bin\sdkmanager.bat" set "SDKMANAGER=%SDK%\cmdline-tools\latest\bin\sdkmanager.bat"
if defined SDKMANAGER exit /b 0
for /f "delims=" %%F in ('dir /b /s "%SDK%\cmdline-tools\*\bin\sdkmanager.bat" 2^>nul') do if not defined SDKMANAGER set "SDKMANAGER=%%F"
if defined SDKMANAGER (exit /b 0) else exit /b 1

:diagnose
call :locate_sdk
echo.
echo [86Box Android Diagnose]
echo ROOT: %ROOT%
echo App-Version: %APP_VERSION% ^(versionCode %APP_VERSION_CODE%^)
echo Universal APK ABIs: arm64-v8a + x86_64
echo Renderer: Vulkan bevorzugt, SDL3-Fallback GPU/GLES2/Software
echo Netzwerk: echtes libslirp (user-mode NAT)
if defined SDK (echo [OK] Android SDK: %SDK%) else echo [FEHLT] Android SDK nicht gefunden
where java >nul 2>&1 && (java -version 2>&1 | findstr /i "version") || echo [FEHLT] Java/JDK nicht im PATH
where git >nul 2>&1 && (git --version) || echo [FEHLT] Git nicht im PATH
where cmake >nul 2>&1 && (cmake --version | findstr /i "cmake version") || echo [FEHLT] CMake nicht im PATH
if defined SDK if exist "%SDK%\ndk\%NDK_VERSION%\build\cmake\android.toolchain.cmake" (
    echo [OK] NDK r28c: %SDK%\ndk\%NDK_VERSION%
) else (
    echo [FEHLT] NDK %NDK_VERSION%
)
if defined SDK if exist "%SDK%\platforms\android-35\android.jar" (echo [OK] Android API 35) else echo [FEHLT] Android API 35
if defined SDK if exist "%SDK%\build-tools\35.0.0\aapt2.exe" (echo [OK] Build Tools 35.0.0) else echo [FEHLT] Build Tools 35.0.0
if exist "%SDL_DIR%\CMakeLists.txt" (echo [OK] SDL3 Source: %SDL_DIR%) else echo [INFO] SDL3 wird beim Build geladen
if exist "%VCPKG_ROOT%\vcpkg.exe" (echo [OK] vcpkg vorhanden) else echo [INFO] vcpkg wird beim Build geladen
if exist "%OUT_UNIVERSAL%" (echo [OK] Universal APK vorhanden: %OUT_UNIVERSAL%) else echo [INFO] Noch keine Universal APK gebaut
if exist "%OUT_ARM64%" echo [OK] ARM64 Split APK: %OUT_ARM64%
if exist "%OUT_X64%" echo [OK] x86_64 Split APK: %OUT_X64%
exit /b 0

:install_sdk
call :locate_sdk
if not defined SDK (
    echo [ERROR] Android SDK wurde nicht gefunden.
    echo Installiere Android Studio und im SDK Manager die "Android SDK Command-line Tools (latest)".
    echo Danach BUILD_APK.bat erneut starten.
    exit /b 1
)
call :locate_sdkmanager
if errorlevel 1 (
    echo [ERROR] sdkmanager.bat fehlt.
    echo Android Studio ^> SDK Manager ^> SDK Tools ^> Android SDK Command-line Tools ^(latest^) aktivieren.
    exit /b 1
)
echo.
echo [INFO] Installiere/aktualisiere Android API 35, Build Tools und NDK r28c ...
echo [INFO] Falls Lizenzfragen erscheinen: mit y bestaetigen.
call "%SDKMANAGER%" --sdk_root="%SDK%" "platform-tools" "platforms;android-35" "build-tools;35.0.0" "ndk;%NDK_VERSION%"
if errorlevel 1 (
    echo [ERROR] sdkmanager meldete einen Fehler.
    exit /b 1
)
echo [OK] Android SDK/NDK Komponenten vorhanden.
exit /b 0

:ensure_tools
where java >nul 2>&1 || (echo [ERROR] JDK 17 oder neuer fehlt im PATH. & exit /b 1)
where javac >nul 2>&1 || (echo [ERROR] JDK Compiler javac fehlt im PATH. & exit /b 1)
set "JAVAC_VERSION="
for /f "tokens=2" %%V in ('javac -version 2^>^&1') do set "JAVAC_VERSION=%%V"
set "JDK_MAJOR="
for /f "tokens=1 delims=." %%M in ("%JAVAC_VERSION%") do set "JDK_MAJOR=%%M"
if not defined JDK_MAJOR (echo [ERROR] JDK-Version konnte nicht erkannt werden: %JAVAC_VERSION% & exit /b 1)
if %JDK_MAJOR% LSS 17 (echo [ERROR] JDK 17 oder neuer erforderlich. Gefunden: %JAVAC_VERSION% & exit /b 1)
where git >nul 2>&1 || (echo [ERROR] Git fehlt im PATH. & exit /b 1)
where cmake >nul 2>&1 || (echo [ERROR] CMake fehlt im PATH. & exit /b 1)
call :locate_sdk
if not defined SDK (
    echo [ERROR] Android SDK nicht gefunden. Bitte zuerst Menuepunkt [2].
    exit /b 1
)
set "NEED_SDK_COMPONENTS="
if not exist "%SDK%\ndk\%NDK_VERSION%\build\cmake\android.toolchain.cmake" set "NEED_SDK_COMPONENTS=1"
if not exist "%SDK%\platforms\android-35\android.jar" set "NEED_SDK_COMPONENTS=1"
if not exist "%SDK%\build-tools\35.0.0\aapt2.exe" set "NEED_SDK_COMPONENTS=1"
if defined NEED_SDK_COMPONENTS (
    echo [INFO] Android SDK/NDK Komponenten fehlen - starte Installation.
    call :install_sdk
    if errorlevel 1 exit /b 1
)
set "NDK=%SDK%\ndk\%NDK_VERSION%"
call :prepare_ndk_build_path
if errorlevel 1 exit /b 1
set "ANDROID_NDK_HOME=%NDK_BUILD%"
set "ANDROID_NDK_ROOT=%NDK_BUILD%"
exit /b 0

:prepare_ndk_build_path
rem vcpkg/Autotools expands 8.3 names and junctions back to the physical SDK path.
rem If that path contains spaces (for example C:\Users\Example User\...), configure can fail.
rem Use a REAL NDK copy at a path with no spaces; this is intentionally not a junction.
set "NDK_BUILD="
set "NDK_LOCAL="
for %%D in ("%ROOT%") do set "ROOT_DRIVE=%%~dD"
if defined ROOT_DRIVE set "NDK_LOCAL=!ROOT_DRIVE!\86BoxAndroidToolchains\ndk-%NDK_VERSION%"
if not defined NDK_LOCAL (echo [ERROR] Konnte Ziellaufwerk fuer lokalen NDK nicht bestimmen. & exit /b 1)
if not "!NDK_LOCAL: =!"=="!NDK_LOCAL!" (echo [ERROR] Lokaler NDK-Pfad enthaelt Leerzeichen: !NDK_LOCAL! & exit /b 1)
set "NDK_MARKER=!NDK_LOCAL!\.86box_ndk_complete"
if exist "!NDK_MARKER!" if exist "!NDK_LOCAL!\build\cmake\android.toolchain.cmake" (
    set "NDK_BUILD=!NDK_LOCAL!"
    echo [OK] Lokaler NDK ohne Leerzeichen: !NDK_BUILD!
    exit /b 0
)
echo [INFO] vcpkg/Autotools benoetigt einen PHYSISCHEN NDK-Pfad ohne Leerzeichen.
echo [INFO] Einmalige NDK-Kopie ^(~2.1 GB^) nach: !NDK_LOCAL!
if not exist "!ROOT_DRIVE!\86BoxAndroidToolchains" mkdir "!ROOT_DRIVE!\86BoxAndroidToolchains" 2>nul
if not exist "!ROOT_DRIVE!\86BoxAndroidToolchains" (
    echo [ERROR] Konnte !ROOT_DRIVE!\86BoxAndroidToolchains nicht anlegen.
    exit /b 1
)
robocopy "%NDK%" "!NDK_LOCAL!" /MIR /COPY:DAT /DCOPY:DAT /R:2 /W:1 /NFL /NDL /NJH /NJS /NP
set "ROBO_RC=!ERRORLEVEL!"
if !ROBO_RC! GEQ 8 (
    echo [ERROR] NDK-Kopie fehlgeschlagen. Robocopy-Code: !ROBO_RC!
    exit /b 1
)
if not exist "!NDK_LOCAL!\build\cmake\android.toolchain.cmake" (
    echo [ERROR] Lokale NDK-Kopie ist unvollstaendig: !NDK_LOCAL!
    exit /b 1
)
>"!NDK_MARKER!" echo %NDK_VERSION%
set "NDK_BUILD=!NDK_LOCAL!"
echo [OK] Lokaler NDK ohne Leerzeichen bereit: !NDK_BUILD!
exit /b 0

:ensure_ninja
set "NINJA_EXE="
where ninja >nul 2>&1
if not errorlevel 1 (
    for /f "delims=" %%F in ('where ninja') do if not defined NINJA_EXE set "NINJA_EXE=%%F"
    exit /b 0
)
set "NINJA_HOME=%THIRD%\ninja-%NINJA_VERSION%"
set "NINJA_EXE=%NINJA_HOME%\ninja.exe"
if exist "%NINJA_EXE%" exit /b 0
mkdir "%NINJA_HOME%" 2>nul
echo [INFO] Lade Ninja %NINJA_VERSION% ...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $u='https://github.com/ninja-build/ninja/releases/download/v%NINJA_VERSION%/ninja-win.zip'; $z='%THIRD%\ninja.zip'; Invoke-WebRequest -UseBasicParsing $u -OutFile $z; Expand-Archive -Force $z '%NINJA_HOME%'; Remove-Item -Force $z"
if errorlevel 1 exit /b 1
if not exist "%NINJA_EXE%" (echo [ERROR] Ninja Download fehlgeschlagen. & exit /b 1)
exit /b 0

:ensure_gradle
set "GRADLE_HOME=%THIRD%\gradle-%GRADLE_VERSION%"
set "GRADLE_EXE=%GRADLE_HOME%\bin\gradle.bat"
if exist "%GRADLE_EXE%" exit /b 0
mkdir "%THIRD%" 2>nul
echo [INFO] Lade Gradle %GRADLE_VERSION% ...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $u='https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip'; $z='%THIRD%\gradle.zip'; Invoke-WebRequest -UseBasicParsing $u -OutFile $z; Expand-Archive -Force $z '%THIRD%'; Remove-Item -Force $z"
if errorlevel 1 exit /b 1
if not exist "%GRADLE_EXE%" (echo [ERROR] Gradle Download fehlgeschlagen. & exit /b 1)
exit /b 0

:ensure_sources
if not exist "%THIRD%" mkdir "%THIRD%"
if not exist "%SDL_DIR%\CMakeLists.txt" (
    echo [INFO] Lade SDL3 %SDL_TAG% ...
    git clone --depth 1 --branch %SDL_TAG% https://github.com/libsdl-org/SDL.git "%SDL_DIR%"
    if errorlevel 1 exit /b 1
)
if not exist "%VCPKG_ROOT%\bootstrap-vcpkg.bat" (
    echo [INFO] Lade vcpkg ...
    git clone --depth 1 https://github.com/microsoft/vcpkg.git "%VCPKG_ROOT%"
    if errorlevel 1 exit /b 1
)
if not exist "%VCPKG_ROOT%\vcpkg.exe" (
    echo [INFO] Bootstrap vcpkg ...
    call "%VCPKG_ROOT%\bootstrap-vcpkg.bat" -disableMetrics
    if errorlevel 1 exit /b 1
)
exit /b 0

:apply_source_fixes
if not exist "%PATCH_SCRIPT%" (
    echo [ERROR] Android Build-Fix-Skript fehlt:
    echo         %PATCH_SCRIPT%
    exit /b 1
)
echo [INFO] Wende Android Build-Kompatibilitaetsfixes an ...
powershell -NoProfile -ExecutionPolicy Bypass -File "%PATCH_SCRIPT%" -Root "%ROOT%"
if errorlevel 1 (
    echo [ERROR] Android Build-Kompatibilitaetsfixes fehlgeschlagen.
    exit /b 1
)
exit /b 0

:install_native_deps
echo [INFO] Baue Android-Abhaengigkeiten fuer ARM64 und x86_64 ...
echo [INFO] Installiere Host-pkgconf fuer libslirp CMake-Erkennung ...
"%VCPKG_ROOT%\vcpkg.exe" install pkgconf:x64-windows --classic
if errorlevel 1 exit /b 1
set "PKGCONF_EXE=%VCPKG_ROOT%\installed\x64-windows\tools\pkgconf\pkgconf.exe"
if not exist "%PKGCONF_EXE%" (
    set "PKGCONF_EXE="
    for /r "%VCPKG_ROOT%\installed\x64-windows" %%F in (pkgconf.exe) do if not defined PKGCONF_EXE set "PKGCONF_EXE=%%F"
)
if not exist "%PKGCONF_EXE%" (echo [ERROR] pkgconf.exe wurde nicht gefunden. & exit /b 1)
pushd "%VCPKG_ROOT%"
set "ANDROID_NDK_HOME=%NDK_BUILD%"
set "ANDROID_NDK_ROOT=%NDK_BUILD%"
rem Remove only compiler-detection/failed gettext caches so vcpkg cannot reuse the old spaced NDK path.
if exist "%VCPKG_ROOT%\buildtrees\detect_compiler" rmdir /s /q "%VCPKG_ROOT%\buildtrees\detect_compiler"
if exist "%VCPKG_ROOT%\buildtrees\gettext-libintl" rmdir /s /q "%VCPKG_ROOT%\buildtrees\gettext-libintl"
"%VCPKG_ROOT%\vcpkg.exe" install freetype:arm64-android libpng:arm64-android libsndfile:arm64-android openal-soft:arm64-android libslirp:arm64-android --classic --clean-after-build
if errorlevel 1 (popd & exit /b 1)
"%VCPKG_ROOT%\vcpkg.exe" install freetype:x64-android libpng:x64-android libsndfile:x64-android openal-soft:x64-android libslirp:x64-android --classic --clean-after-build
set "VCPKG_RC=%ERRORLEVEL%"
popd
if not "%VCPKG_RC%"=="0" exit /b %VCPKG_RC%
exit /b 0

:sync_sdl_java
if not exist "%SDL_DIR%\android-project\app\src\main\java\org\libsdl\app" (
    echo [ERROR] SDL Android Java Glue fehlt.
    exit /b 1
)
if exist "%SDL_JAVA_DST%" rmdir /s /q "%SDL_JAVA_DST%"
mkdir "%SDL_JAVA_DST%"
robocopy "%SDL_DIR%\android-project\app\src\main\java\org\libsdl\app" "%SDL_JAVA_DST%" /E /NFL /NDL /NJH /NJS /NP >nul
if errorlevel 8 (echo [ERROR] SDL Java Glue konnte nicht kopiert werden. & exit /b 1)
exit /b 0

:configure_one
set "ABI=%~1"
set "TRIPLET=%~2"
set "BUILD_DIR=%~3"
echo [INFO] CMake Android %ABI% konfigurieren ...
if not exist "%BUILD_DIR%" mkdir "%BUILD_DIR%"
if not defined PKGCONF_EXE set "PKGCONF_EXE=%VCPKG_ROOT%\installed\x64-windows\tools\pkgconf\pkgconf.exe"
set "PKG_CONFIG_PATH=%VCPKG_ROOT%\installed\%TRIPLET%\lib\pkgconfig;%VCPKG_ROOT%\installed\%TRIPLET%\share\pkgconfig"
set "PKG_CONFIG_LIBDIR=%PKG_CONFIG_PATH%"
set "PKG_CONFIG_SYSROOT_DIR="
set "SDL_DIR_CMAKE=%SDL_DIR:\=/%"
cmake -S "%ROOT%" -B "%BUILD_DIR%" -G Ninja ^
  -DCMAKE_MAKE_PROGRAM="%NINJA_EXE%" ^
  -DCMAKE_BUILD_TYPE=Release ^
  -DCMAKE_TOOLCHAIN_FILE="%VCPKG_ROOT%\scripts\buildsystems\vcpkg.cmake" ^
  -DVCPKG_CHAINLOAD_TOOLCHAIN_FILE="%NDK_BUILD%\build\cmake\android.toolchain.cmake" ^
  -DVCPKG_MANIFEST_MODE=OFF ^
  -DVCPKG_TARGET_TRIPLET=%TRIPLET% ^
  -DPKG_CONFIG_EXECUTABLE="%PKGCONF_EXE%" ^
  -DANDROID_NDK="%NDK_BUILD%" ^
  -DANDROID_ABI=%ABI% ^
  -DANDROID_PLATFORM=android-28 ^
  -DANDROID_STL=c++_static ^
  -DSDL3_SOURCE_DIR="%SDL_DIR_CMAKE%" ^
  -DQT=OFF -DSDL2=OFF -DDISCORD=OFF -DVNC=OFF ^
  -DRTMIDI=OFF -DFLUIDSYNTH=OFF -DMUNT=OFF -DSOUNDCANVAS=OFF ^
  -DOPENAL=ON -DDYNAREC=ON -DASYNC_PIO_WRITES=ON -DASYNC_SCSI_READS=ON -DPERF_PROFILE=ON -DANDROID_REAL_SLIRP=ON ^
  -DBUILD_TESTING=OFF -DBUILD_BENCHMARKS=OFF
if errorlevel 1 exit /b 1
exit /b 0

:build_one
set "ABI=%~1"
set "BUILD_DIR=%~2"
echo [INFO] Kompiliere 86Box + SDL3 fuer %ABI% Android ...
cmake --build "%BUILD_DIR%" --target 86Box -- -j 8
if errorlevel 1 exit /b 1
exit /b 0

:stage_one
set "ABI=%~1"
set "TRIPLET=%~2"
set "BUILD_DIR=%~3"
set "JNI_DIR=%~4"
if exist "%JNI_DIR%" rmdir /s /q "%JNI_DIR%"
mkdir "%JNI_DIR%"
set "PS_STAGE=$ErrorActionPreference='Stop'; $dst='%JNI_DIR%'; $main=Get-ChildItem -Path '%BUILD_DIR%' -Recurse -File -Filter 'libmain.so' | Select-Object -First 1; if(-not $main){throw 'libmain.so fuer %ABI% nicht gefunden'}; Copy-Item $main.FullName $dst -Force; $sdl=Get-ChildItem -Path '%BUILD_DIR%' -Recurse -File -Filter 'libSDL3.so' | Select-Object -First 1; if(-not $sdl){throw 'libSDL3.so fuer %ABI% nicht gefunden'}; Copy-Item $sdl.FullName $dst -Force; Get-ChildItem -Path '%VCPKG_ROOT%\installed\%TRIPLET%' -Recurse -File -Filter '*.so' -ErrorAction SilentlyContinue | Where-Object {$_.FullName -notmatch '\\debug\\'} | ForEach-Object {Copy-Item $_.FullName $dst -Force}; Write-Host '[OK] %ABI% Native Libraries:'; Get-ChildItem $dst -Filter '*.so' | ForEach-Object {Write-Host ('  ' + $_.Name + '  ' + [math]::Round($_.Length/1MB,2) + ' MB')}"
powershell -NoProfile -ExecutionPolicy Bypass -Command "%PS_STAGE%"
if errorlevel 1 exit /b 1
exit /b 0

:build_all_native
call :configure_one arm64-v8a arm64-android "%NATIVE_BUILD_ARM64%" || exit /b 1
call :build_one arm64-v8a "%NATIVE_BUILD_ARM64%" || exit /b 1
call :stage_one arm64-v8a arm64-android "%NATIVE_BUILD_ARM64%" "%JNI_ARM64%" || exit /b 1
call :configure_one x86_64 x64-android "%NATIVE_BUILD_X64%" || exit /b 1
call :build_one x86_64 "%NATIVE_BUILD_X64%" || exit /b 1
call :stage_one x86_64 x64-android "%NATIVE_BUILD_X64%" "%JNI_X64%" || exit /b 1
exit /b 0

:write_local_properties
powershell -NoProfile -ExecutionPolicy Bypass -Command "$p=[IO.Path]::GetFullPath($env:ANDROID_SDK_ROOT).Replace('\','/'); $f=Join-Path $env:ANDROID_DIR 'local.properties'; [IO.File]::WriteAllText($f,('sdk.dir=' + $p + [Environment]::NewLine),[Text.Encoding]::ASCII)"
echo [OK] local.properties: sdk.dir=%SDK:\=/%
exit /b 0

:gradle_build
echo [INFO] Gradle assembleDebug - Universal + ABI-Split APKs ...
pushd "%ANDROID_DIR%"
call "%GRADLE_EXE%" --no-daemon --stacktrace assembleDebug
set "GRADLE_RC=%ERRORLEVEL%"
popd
if not "%GRADLE_RC%"=="0" exit /b %GRADLE_RC%
set "APK_DIR=%ANDROID_DIR%\app\build\outputs\apk\debug"
set "APK_UNI="
set "APK_ARM="
set "APK_X64="
for %%F in ("%APK_DIR%\*universal*debug*.apk") do if exist "%%~fF" set "APK_UNI=%%~fF"
for %%F in ("%APK_DIR%\*arm64-v8a*debug*.apk") do if exist "%%~fF" set "APK_ARM=%%~fF"
for %%F in ("%APK_DIR%\*x86_64*debug*.apk") do if exist "%%~fF" set "APK_X64=%%~fF"
if not defined APK_UNI (
    echo [ERROR] Universal APK wurde nicht gefunden in: %APK_DIR%
    dir /b "%APK_DIR%\*.apk" 2>nul
    exit /b 1
)
copy /y "%APK_UNI%" "%OUT_UNIVERSAL%" >nul
if defined APK_ARM copy /y "%APK_ARM%" "%OUT_ARM64%" >nul
if defined APK_X64 copy /y "%APK_X64%" "%OUT_X64%" >nul
exit /b 0

:build_apk
echo ============================================================
echo  BUILD START - 86Box Android UNIVERSAL 64-BIT DEBUG APK
echo  ABIs: arm64-v8a + x86_64
echo ============================================================
call :ensure_tools || exit /b 1
call :ensure_ninja || exit /b 1
call :ensure_gradle || exit /b 1
call :ensure_sources || exit /b 1
call :apply_source_fixes || exit /b 1
call :install_native_deps || exit /b 1
call :sync_sdl_java || exit /b 1
call :build_all_native || exit /b 1
call :write_local_properties || exit /b 1
call :gradle_build || exit /b 1
echo.
echo ============================================================
echo [OK] UNIVERSAL APK ERZEUGT
echo %OUT_UNIVERSAL%
echo.
echo [INFO] Enthaelt: arm64-v8a + x86_64
echo [INFO] Zusaetzliche Split-APKs werden ebenfalls kopiert, falls vorhanden.
echo ============================================================
exit /b 0

:clean_native
if exist "%NATIVE_BUILD_ARM64%" rmdir /s /q "%NATIVE_BUILD_ARM64%"
if exist "%NATIVE_BUILD_X64%" rmdir /s /q "%NATIVE_BUILD_X64%"
if exist "%JNI_ARM64%" rmdir /s /q "%JNI_ARM64%"
if exist "%JNI_X64%" rmdir /s /q "%JNI_X64%"
mkdir "%JNI_ARM64%" 2>nul
mkdir "%JNI_X64%" 2>nul
echo [OK] ARM64 + x86_64 Native Builds geloescht.
exit /b 0

:clean_all
call :clean_native
if exist "%ANDROID_DIR%\app\build" rmdir /s /q "%ANDROID_DIR%\app\build"
if exist "%ANDROID_DIR%\.gradle" rmdir /s /q "%ANDROID_DIR%\.gradle"
if exist "%OUT_UNIVERSAL%" del /q "%OUT_UNIVERSAL%"
if exist "%OUT_ARM64%" del /q "%OUT_ARM64%"
if exist "%OUT_X64%" del /q "%OUT_X64%"
echo [OK] Build-Ausgaben entfernt. SDL/vcpkg/Gradle Downloads bleiben als Cache erhalten.
exit /b 0
