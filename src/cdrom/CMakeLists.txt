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
# Authors: David Hrdlička, <hrdlickadavid@outlook.com>
#          Jasmine Iwanek, <jriwanek@gmail.com>
#
#          Copyright 2020-2021 David Hrdlička.
#          Copyright 2024-2026 Jasmine Iwanek.
#

if(ANDROID)
    set(WITH_SYSTEM_ZLIB OFF)
    set(WITH_SYSTEM_ZSTD OFF)
else()
    set(WITH_SYSTEM_ZLIB ON)
    set(WITH_SYSTEM_ZSTD ON)
endif()
add_subdirectory(libchdr)

if(ANDROID)
    find_package(SndFile CONFIG REQUIRED)
else()
    find_package(PkgConfig REQUIRED)
    pkg_check_modules(SNDFILE REQUIRED IMPORTED_TARGET sndfile)
endif()

add_library(cdrom OBJECT
    cdrom.c
    cdrom_aaru.c
    cdrom_image.c
    cdrom_image_viso.c
    cdrom_mitsumi.c
    cdrom_mke.c
    cdrom_chd.c
)
target_link_libraries(cdrom chdr-static)
if(ANDROID)
    target_link_libraries(86Box SndFile::sndfile)
else()
    if(NOT WIN32)
        target_include_directories(86Box PRIVATE PkgConfig::SNDFILE)
    endif()
    if(CMAKE_SYSTEM_NAME STREQUAL "FreeBSD")
        target_include_directories(cdrom PRIVATE /usr/local/include)
    endif()
    target_link_libraries(86Box PkgConfig::SNDFILE)
endif()

if (WIN32)
    # MSYS2
    target_link_libraries(86Box -static ${SNDFILE_STATIC_LIBRARIES})
endif()
