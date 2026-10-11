param(
    [Parameter(Mandatory=$true)]
    [string]$Root
)

$ErrorActionPreference = 'Stop'

$rootPath = (Resolve-Path $Root).Path
$sdlCmake = Join-Path $rootPath 'third_party_android\SDL\CMakeLists.txt'
$vcpkg = Join-Path $rootPath 'third_party_android\vcpkg'
$glibPort = Join-Path $vcpkg 'ports\glib\portfile.cmake'

if (-not (Test-Path -LiteralPath $sdlCmake)) {
    throw "SDL CMakeLists.txt nicht gefunden: $sdlCmake"
}
if (-not (Test-Path -LiteralPath $glibPort)) {
    throw "vcpkg GLib portfile.cmake nicht gefunden: $glibPort"
}

$nativeBuildNeedsReset = $false

# SDL path fix for Windows-hosted Android cross builds.
# Values passed with -D may keep native backslashes. SDL uses SDL3_SOURCE_DIR
# in try_compile() and file(GLOB CONFIGURE_DEPENDS), which later produces
# generated CMake scripts. Backslashes there can be parsed as \C or \8 escapes.
$sdlMarker = '# 86BOX_ANDROID_SDL_WINDOWS_PATH_FIX'
$sdlText = [System.IO.File]::ReadAllText($sdlCmake)

$hasLegacySdlPathFix =
    $sdlText.Contains('# 86BOX_ANDROID_GLOBAL_CMAKE_MODULE_PATH_FIX') -or
    $sdlText.Contains('# 86BOX_ANDROID_SDL_SOURCE_DIR_NORMALIZE_FIX') -or
    ($sdlText.Contains('file(TO_CMAKE_PATH "${SDL3_SOURCE_DIR}" SDL3_SOURCE_DIR)') -and
     $sdlText.Contains('string(REPLACE "\\" "/" CMAKE_MODULE_PATH "${CMAKE_MODULE_PATH}")'))

if (-not $sdlText.Contains($sdlMarker) -and -not $hasLegacySdlPathFix) {
    $needle = 'list(APPEND CMAKE_MODULE_PATH "${SDL3_SOURCE_DIR}/cmake")'
    $idx = $sdlText.IndexOf($needle, [System.StringComparison]::Ordinal)
    if ($idx -lt 0) {
        throw 'SDL CMAKE_MODULE_PATH append line not found.'
    }

    $replacement = @'
# 86BOX_ANDROID_SDL_WINDOWS_PATH_FIX
file(TO_CMAKE_PATH "${SDL3_SOURCE_DIR}" SDL3_SOURCE_DIR)
list(APPEND CMAKE_MODULE_PATH "${SDL3_SOURCE_DIR}/cmake")
string(REPLACE "\\" "/" CMAKE_MODULE_PATH "${CMAKE_MODULE_PATH}")
message(STATUS "86Box SDL hotfix: normalized SDL3_SOURCE_DIR and CMAKE_MODULE_PATH")
'@

    $sdlText = $sdlText.Remove($idx, $needle.Length).Insert($idx, $replacement)
    [System.IO.File]::WriteAllText($sdlCmake, $sdlText, [System.Text.UTF8Encoding]::new($false))
    Write-Host '[OK] SDL Windows-Pfadfix eingetragen.'
    $nativeBuildNeedsReset = $true
} elseif ($hasLegacySdlPathFix) {
    Write-Host '[OK] Bereits vorhandener aelterer SDL Windows-Pfadfix erkannt; kein Native-Rebuild noetig.'
} else {
    Write-Host '[OK] SDL Windows-Pfadfix bereits vorhanden.'
}

# GLib/Meson GDB auto-load fix. The GDB helper install path can embed a second
# Windows drive prefix under share/gdb/auto-load and Python rejects it with
# WinError 123. These helper scripts are not needed by the Android APK.
$glibMarker = '# BEGIN 86BOX_ANDROID_GLIB_GDB_AUTOLOAD_HOTFIX'
$glibText = [System.IO.File]::ReadAllText($glibPort)

if (-not $glibText.Contains($glibMarker)) {
    $block = @'
# BEGIN 86BOX_ANDROID_GLIB_GDB_AUTOLOAD_HOTFIX
if(VCPKG_TARGET_IS_ANDROID AND CMAKE_HOST_WIN32)
    set(_86BOX_GLIB_MESON "${SOURCE_PATH}/glib/meson.build")
    file(READ "${_86BOX_GLIB_MESON}" _86BOX_GLIB_MESON_TEXT)

    set(_86BOX_GDB_OLD [=[if host_system != 'windows'
  gdb_install = true]=])
    set(_86BOX_GDB_NEW [=[if host_system != 'windows' and build_machine.system() != 'windows'
  gdb_install = true]=])

    string(FIND "${_86BOX_GLIB_MESON_TEXT}" "${_86BOX_GDB_NEW}" _86BOX_GDB_NEW_POS)
    if(_86BOX_GDB_NEW_POS EQUAL -1)
        string(FIND "${_86BOX_GLIB_MESON_TEXT}" "${_86BOX_GDB_OLD}" _86BOX_GDB_OLD_POS)
        if(_86BOX_GDB_OLD_POS EQUAL -1)
            message(FATAL_ERROR "86Box GLib hotfix: expected gdb_install block not found")
        endif()
        string(REPLACE
            "${_86BOX_GDB_OLD}"
            "${_86BOX_GDB_NEW}"
            _86BOX_GLIB_MESON_TEXT
            "${_86BOX_GLIB_MESON_TEXT}"
        )
        file(WRITE "${_86BOX_GLIB_MESON}" "${_86BOX_GLIB_MESON_TEXT}")
        message(STATUS "86Box GLib hotfix: disabled GDB auto-load install for Windows-hosted Android cross-build")
    endif()

    unset(_86BOX_GLIB_MESON)
    unset(_86BOX_GLIB_MESON_TEXT)
    unset(_86BOX_GDB_OLD)
    unset(_86BOX_GDB_NEW)
    unset(_86BOX_GDB_NEW_POS)
    unset(_86BOX_GDB_OLD_POS)
endif()
# END 86BOX_ANDROID_GLIB_GDB_AUTOLOAD_HOTFIX
'@

    $lines = [System.Collections.Generic.List[string]]::new()
    [void]$lines.AddRange([string[]]($glibText -split "`r?`n"))

    $start = -1
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match '^\s*vcpkg_extract_source_archive\s*\(\s*SOURCE_PATH') {
            $start = $i
            break
        }
    }
    if ($start -lt 0) {
        throw 'vcpkg_extract_source_archive(SOURCE_PATH ...) not found in GLib port.'
    }

    $end = -1
    for ($i = $start + 1; $i -lt $lines.Count; $i++) {
        if ($lines[$i].Trim() -eq ')') {
            $end = $i
            break
        }
    }
    if ($end -lt 0) {
        throw 'End of GLib vcpkg_extract_source_archive block not found.'
    }

    $blockLines = [string[]]($block -split "`r?`n")
    for ($j = $blockLines.Length - 1; $j -ge 0; $j--) {
        $lines.Insert($end + 1, $blockLines[$j])
    }
    $lines.Insert($end + 1, '')

    [System.IO.File]::WriteAllText($glibPort, ($lines -join "`r`n"), [System.Text.UTF8Encoding]::new($false))
    Write-Host '[OK] GLib Android/Windows-Host Fix eingetragen.'

    $glibCleanup = @(
        (Join-Path $vcpkg 'buildtrees\glib'),
        (Join-Path $vcpkg 'packages\glib_arm64-android'),
        (Join-Path $vcpkg 'packages\glib_x64-android')
    )
    foreach ($path in $glibCleanup) {
        if (Test-Path -LiteralPath $path) {
            Remove-Item -LiteralPath $path -Recurse -Force
            Write-Host "[OK] Alter GLib-Zustand entfernt: $path"
        }
    }
} else {
    Write-Host '[OK] GLib Android/Windows-Host Fix bereits vorhanden.'
}

if ($nativeBuildNeedsReset) {
    $buildDirs = @(
        (Join-Path $rootPath 'build-android-arm64'),
        (Join-Path $rootPath 'build-android-x64'),
        (Join-Path $rootPath 'build-android-x86_64')
    )
    foreach ($dir in $buildDirs) {
        if (Test-Path -LiteralPath $dir) {
            Remove-Item -LiteralPath $dir -Recurse -Force
            Write-Host "[OK] Alter nativer CMake-Build entfernt: $dir"
        }
    }
}

Write-Host '[OK] Android Build-Kompatibilitaetsfixes bereit.'
