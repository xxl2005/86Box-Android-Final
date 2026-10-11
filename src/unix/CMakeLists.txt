#
# 86Box    A hypervisor and IBM PC system emulator that specializes in
#          running old operating systems and software designed for IBM
#          PC systems and compatibles from 1981 through fairly recent
#          system designs based on the PCI bus.
#
#          This file is part of the 86Box distribution.
#
#          CMake build script.
#
# Authors: Cacodemon345
#          David Hrdlička, <hrdlickadavid@outlook.com>
#          Jasmine Iwanek, <jriwanek@gmail.com>
#
#          Copyright 2021      Cacodemon345.
#          Copyright 2021      David Hrdlička.
#          Copyright 2021      Andreas J. Reichel.
#          Copyright 2021-2024 Jasmine Iwanek.
#

add_library(plat OBJECT
    sdl_main.c
    sdl_plat.c
    ../qt/sdl_joystick.c
)

if(ANDROID)
    target_sources(plat PRIVATE android_bridge.c android_touch_latency.c android_cpu_cycles.c)
endif()

if(WIN32)
    target_sources(plat PRIVATE
        ../qt/win_netsocket.c
        ../qt/win_dynld.c
        sdl_plat_win.c
    )
    target_link_libraries(86Box ws2_32)
else()
    target_sources(plat PRIVATE
        sdl_plat_unix.c
        unix_netsocket.c
        unix_dynld.c
    )
endif()

if (NOT CPPTHREADS)
    if (NOT WIN32)
        set(THREADS_PREFER_PTHREAD_FLAG TRUE)
    endif()
    find_package(Threads REQUIRED)
    target_link_libraries(86Box Threads::Threads)

    if (WIN32)
        target_sources(plat PRIVATE ../qt/win_thread.c)
    else()
        target_sources(plat PRIVATE unix_thread.c)
    endif()
endif()

if(SDL2)
    find_package(SDL2 REQUIRED)
    add_compile_definitions(USE_SDL2_LIB)
    include_directories(${SDL2_INCLUDE_DIRS})
    if(STATIC_BUILD AND TARGET SDL2::SDL2-static)
        target_link_libraries(86Box SDL2::SDL2-static)
    elseif(TARGET SDL2::SDL2)
        target_link_libraries(86Box SDL2::SDL2)
    else()
        target_link_libraries(86Box ${SDL2_LIBRARIES})
    endif()
else()
    if(NOT TARGET SDL3::SDL3 AND NOT TARGET SDL3::SDL3-shared)
        find_package(SDL3 REQUIRED)
    endif()
    include_directories(${SDL3_INCLUDE_DIRS})
    # 86BOX_ANDROID_SDL3_OBJECT_HEADERS_FIX
    # 86Box links SDL3 headers on the final 86Box target, but plat/ui/osd_backend
    # are OBJECT libraries and compile independently. Give them an SDL3 interface
    # target as well so <SDL3/SDL.h> is available during their own compilation.
    set(_86BOX_SDL3_INTERFACE_TARGET "")
    if(TARGET SDL3::Headers)
        set(_86BOX_SDL3_INTERFACE_TARGET SDL3::Headers)
        target_link_libraries(86Box SDL3::Headers)
    elseif(TARGET SDL3::SDL3)
        set(_86BOX_SDL3_INTERFACE_TARGET SDL3::SDL3)
    elseif(TARGET SDL3::SDL3-shared)
        set(_86BOX_SDL3_INTERFACE_TARGET SDL3::SDL3-shared)
    elseif(TARGET SDL3::SDL3-static)
        set(_86BOX_SDL3_INTERFACE_TARGET SDL3::SDL3-static)
    endif()

    if(_86BOX_SDL3_INTERFACE_TARGET)
        target_link_libraries(plat ${_86BOX_SDL3_INTERFACE_TARGET})
        message(STATUS "86Box Android SDL3 fix: plat gets headers from ${_86BOX_SDL3_INTERFACE_TARGET}")
    elseif(SDL3_INCLUDE_DIRS)
        target_include_directories(plat PRIVATE ${SDL3_INCLUDE_DIRS})
        message(STATUS "86Box Android SDL3 fix: plat gets SDL3_INCLUDE_DIRS fallback")
    endif()
    if(STATIC_BUILD AND TARGET SDL3::SDL3-static)
        target_link_libraries(86Box SDL3::SDL3-static)
    elseif(TARGET SDL3::SDL3)
        target_link_libraries(86Box SDL3::SDL3)
    elseif(TARGET SDL3::SDL3-shared)
        target_link_libraries(86Box SDL3::SDL3-shared)
    else()
        target_link_libraries(86Box ${SDL3_LIBRARIES})
    endif()
endif()

option(USE_SDL_SHADER "Build SDL GLES2 shader pipeline" OFF)

add_library(ui OBJECT
    sdl_render.c
    sdl_cdrom.c
    sdl_monitor.c
    sdl_ui.c
)

if(NOT SDL2)
    if(_86BOX_SDL3_INTERFACE_TARGET)
        target_link_libraries(ui PRIVATE ${_86BOX_SDL3_INTERFACE_TARGET})
    elseif(SDL3_INCLUDE_DIRS)
        target_include_directories(ui PRIVATE ${SDL3_INCLUDE_DIRS})
    endif()
endif()

if(USE_SDL_SHADER)
    target_compile_definitions(ui PRIVATE USE_SDL_SHADER_PIPELINE)

    find_library(GLESV2_LIBRARY GLESv2)
    find_path(GLESV2_INCLUDE_DIR GLES2/gl2.h)

    if(NOT GLESV2_LIBRARY OR NOT GLESV2_INCLUDE_DIR)
        message(FATAL_ERROR "USE_SDL_SHADER=ON requires GLESv2 and GLES2/gl2.h; use -DUSE_SDL_SHADER=OFF to build without the shader pipeline")
    endif()

    target_sources(ui PRIVATE sdl_shader.c)

    target_link_libraries(86Box ${GLESV2_LIBRARY})
    target_include_directories(ui PRIVATE ${GLESV2_INCLUDE_DIR})
endif()

target_sources(ui PRIVATE sdl_osd.cpp)

# Build the SDL ImGui backend separately so AUTOMOC skips it.
set(OSD_DIR ${CMAKE_SOURCE_DIR}/src/osd)
if(SDL2)
    add_library(osd_backend OBJECT
        ${OSD_DIR}/imgui/imgui_impl_sdl2.cpp
    )
else()
    add_library(osd_backend OBJECT
        ${OSD_DIR}/imgui/imgui_impl_sdl3.cpp
    )
endif()

if(NOT SDL2)
    if(_86BOX_SDL3_INTERFACE_TARGET)
        target_link_libraries(osd_backend PRIVATE ${_86BOX_SDL3_INTERFACE_TARGET})
    elseif(SDL3_INCLUDE_DIRS)
        target_include_directories(osd_backend PRIVATE ${SDL3_INCLUDE_DIRS})
    endif()
endif()

if(USE_SDL_SHADER)
    target_sources(osd_backend PRIVATE ${OSD_DIR}/imgui/imgui_impl_opengl3.cpp)
    target_compile_definitions(osd_backend PRIVATE IMGUI_IMPL_OPENGL_ES2)
else()
    if(SDL2)
        target_sources(osd_backend PRIVATE ${OSD_DIR}/imgui/imgui_impl_sdlrenderer2.cpp)
    else()
        target_sources(osd_backend PRIVATE ${OSD_DIR}/imgui/imgui_impl_sdlrenderer3.cpp)
    endif()
endif()

target_include_directories(osd_backend PRIVATE ${OSD_DIR}/imgui)
target_link_libraries(86Box osd_backend)

target_include_directories(ui PRIVATE ${OSD_DIR} ${OSD_DIR}/imgui)

if(CMAKE_SYSTEM_NAME STREQUAL "Linux")
    target_sources(plat PRIVATE linux_cdrom_ioctl.c)
    target_sources(plat PRIVATE dummy_floppy_ioctl.c)
elseif(CMAKE_SYSTEM_NAME STREQUAL "FreeBSD" OR CMAKE_SYSTEM_NAME STREQUAL "Darwin")
    target_sources(plat PRIVATE dummy_cdrom_ioctl.c)
    target_sources(plat PRIVATE unix_floppy_ioctl.c)
elseif(WIN32)
    target_sources(plat PRIVATE ../qt/win_cdrom_ioctl.c)
    target_sources(plat PRIVATE dummy_floppy_ioctl.c)
else()
    target_sources(plat PRIVATE dummy_cdrom_ioctl.c)
    target_sources(plat PRIVATE dummy_floppy_ioctl.c)
endif()

target_compile_definitions(plat PUBLIC _FILE_OFFSET_BITS=64)
target_link_libraries(plat ${CMAKE_DL_LIBS})

if(APPLE)
    target_sources(plat PRIVATE macOSXGlue.m)
endif()

if(WIN32)
    enable_language(RC)
    target_sources(86Box PUBLIC ../qt/86Box-qt.rc)

    # CMake 3.22 messed this up for clang/clang++
    # See https://gitlab.kitware.com/cmake/cmake/-/issues/22611
    if(MSVC OR (NOT MINGW AND CMAKE_VERSION VERSION_GREATER_EQUAL 3.22))
        # MSVC linker adds its own manifest to the executable, which fails if
        # we include ours in 86Box.rc. We therefore need to pass the manifest
        # directly as as a source file, so the linker can use that instead.
        set_property(SOURCE ../qt/86Box-qt.rc DIRECTORY .. PROPERTY COMPILE_DEFINITIONS NO_INCLUDE_MANIFEST)
        target_sources(86Box PRIVATE ../qt/86Box.manifest)
    endif()

    if (MINGW)
        add_compile_definitions(NTDDI_VERSION=0x06010000)
    endif()
endif()
