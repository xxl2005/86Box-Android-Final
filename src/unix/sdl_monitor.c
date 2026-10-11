#include <stdlib.h>
#include <stdio.h>
#include <stdint.h>
#include <stdbool.h>
#include <string.h>
#include <limits.h>
#include <errno.h>
#include <unistd.h>

#include <86box/86box.h>
#include <86box/plat.h>
#include <86box/plat_dynld.h>
#include <86box/timer.h>
#include <86box/scsi_device.h>
#include <86box/cdrom.h>
#include <86box/fdd.h>
#include <86box/mo.h>
#include <86box/rdisk.h>
#include <86box/scsi_tape.h>
#include <86box/version.h>
#include <86box/video.h>

#include "sdl_monitor.h"

#if ENABLE_READLINE
static char *(*f_readline)(const char *)          = NULL;
static int (*f_add_history)(const char *)         = NULL;
static void (*f_rl_callback_handler_remove)(void) = NULL;

static void *libedit_handle       = NULL;
static dllimp_t libedit_imports[] = {
    // clang-format off
    { "readline",                   &f_readline },
    { "add_history",                &f_add_history },
    { "rl_callback_handler_remove", &f_rl_callback_handler_remove },
    { NULL, NULL }
    // clang-format on
};
#endif

extern int exit_event, fullscreen_pending;
extern bool fast_forward;

#define MONITOR_CART_NUM 2

void
monitor_init(void)
{
#if ENABLE_READLINE
    libedit_handle = dynld_module(LIBEDIT_LIBRARY, libedit_imports);

    if (!libedit_handle) {
        f_readline                   = NULL;
        f_add_history                = NULL;
        f_rl_callback_handler_remove = NULL;

        fprintf(stderr, "sdl_monitor: couldn't load " LIBEDIT_LIBRARY ", line editing will be limited.\n");
    }
#endif
}

void
monitor_close(void)
{
#if ENABLE_READLINE
    if (f_rl_callback_handler_remove)
        f_rl_callback_handler_remove();
#endif
}

static bool
monitor_parse_media_id(const char *arg, uint8_t limit, uint8_t *id)
{
    char *end = NULL;
    long  value;

    if ((arg == NULL) || (id == NULL) || (limit == 0))
        return false;

    errno = 0;
    value = strtol(arg, &end, 10);
    if ((errno != 0) || (end == arg) || (*end != '\0') || (value < 0) || (value >= limit)) {
        fprintf(stderr, "Invalid drive id '%s' (valid range: 0-%u).\n", arg, (unsigned int) limit - 1);
        return false;
    }

    *id = (uint8_t) value;
    return true;
}

static bool
monitor_parse_write_protect(const char *arg, uint8_t *wp)
{
    char *end = NULL;
    long  value;

    if ((arg == NULL) || (wp == NULL))
        return false;

    errno = 0;
    value = strtol(arg, &end, 10);
    if ((errno != 0) || (end == arg) || (*end != '\0') || (value < 0) || (value > 1)) {
        fprintf(stderr, "Invalid write-protect value '%s' (expected 0 or 1).\n", arg);
        return false;
    }

    *wp = (uint8_t) value;
    return true;
}

static bool
monitor_process_media_command(uint8_t *id, uint8_t id_limit, char *fn, uint8_t *wp, char **xargv, int cmdargc)
{
    if ((id == NULL) || (fn == NULL) || (xargv == NULL) || (cmdargc < 3) ||
        (xargv[1] == NULL) || (xargv[2] == NULL))
        return true;

    fn[0] = '\0';
    if (wp != NULL)
        *wp = 0;

    if (!monitor_parse_media_id(xargv[1], id_limit, id))
        return true;

    if (xargv[2][0] == '\'' || xargv[2][0] == '"') {
        const char quote  = xargv[2][0];
        bool       closed = false;
        size_t     used   = 0;

        for (int curarg = 2; curarg < cmdargc; curarg++) {
            const char *part = xargv[curarg];
            if (part == NULL)
                return true;

            if (curarg == 2)
                part++;

            size_t part_len = strlen(part);
            bool   closes   = (part_len > 0) && (part[part_len - 1] == quote);
            if (closes)
                part_len--;

            size_t extra_space = closes ? 0 : 1;
            if (used + part_len + extra_space >= PATH_MAX) {
                fprintf(stderr, "Path name too long.\n");
                return true;
            }

            memcpy(fn + used, part, part_len);
            used += part_len;
            fn[used] = '\0';

            if (closes) {
                closed = true;
                if (wp != NULL) {
                    if ((curarg + 1 >= cmdargc) ||
                        !monitor_parse_write_protect(xargv[curarg + 1], wp))
                        return true;
                }
                break;
            }

            fn[used++] = ' ';
            fn[used]   = '\0';
        }

        if (!closed) {
            fprintf(stderr, "Unterminated quoted path.\n");
            return true;
        }
    } else {
        size_t len = strlen(xargv[2]);
        if (len >= PATH_MAX) {
            fprintf(stderr, "Path name too long.\n");
            return true;
        }

        memcpy(fn, xargv[2], len + 1);
        if (wp != NULL) {
            if ((cmdargc < 4) || !monitor_parse_write_protect(xargv[3], wp))
                return true;
        }
    }

    return fn[0] == '\0';
}

// From musl.
static char *
local_strsep(char **str, const char *sep)
{
    char *s = *str;
    char *end;

    if (!s)
        return NULL;
    end = s + strcspn(s, sep);
    if (*end)
        *end++ = 0;
    else
        end = 0;
    *str = end;

    return s;
}

void
monitor_execute_line(char *line)
{
    if (line) {
        char *xargv[512];
        int   cmdargc = 0;
        char *linecpy, *linespn;

        linecpy = linespn = strdup(line);

        if (linecpy == NULL) {
            return;
        }

        linecpy[strcspn(linecpy, "\r\n")] = 0;

#if ENABLE_READLINE
        if (f_add_history)
            f_add_history(linecpy);
#endif

        memset(xargv, 0, sizeof(xargv));
        while (1) {
            xargv[cmdargc++] = local_strsep(&linespn, " ");
            if (xargv[cmdargc - 1] == NULL || cmdargc >= 512)
                break;
        }
        cmdargc--;
        if (strncasecmp(xargv[0], "help", 4) == 0) {
            printf(
                "fddload <id> <filename> <wp> - Load floppy disk image into drive <id>.\n"
                "cdload <id> <filename> - Load CD-ROM image into drive <id>.\n"
                "rdiskload <id> <filename> <wp> - Load removable disk image into removable disk drive <id>.\n"
                "cartload <id> <filename> <wp> - Load cartridge image into cartridge drive <id>.\n"
                "moload <id> <filename> <wp> - Load MO image into MO drive <id>.\n\n"
                "tapeload <id> <filename> <wp> - Load tape image into tape drive <id>.\n\n"
                "fddeject <id> - eject disk from floppy drive <id>.\n"
                "cdeject <id> - eject disc from CD-ROM drive <id>.\n"
                "rdiskeject <id> - eject removable disk image from removable disk drive <id>.\n"
                "carteject <id> - eject cartridge from drive <id>.\n"
                "moeject <id> - eject image from MO drive <id>.\n\n"
                "tapeeject <id> - eject image from tape drive <id>.\n\n"
                "hardreset - hard reset the emulated system.\n"
                "pause - pause the the emulated system.\n"
                "fastfwd - toggle fast forward.\n"
                "screenshot - save a screenshot.\n"
                "fullscreen - toggle fullscreen.\n"
                "version - print version and license information.\n"
                "exit - exit " EMU_NAME ".\n");
        } else if (strncasecmp(xargv[0], "exit", 4) == 0) {
            exit_event = 1;
        } else if (strncasecmp(xargv[0], "version", 7) == 0) {
#ifndef EMU_GIT_HASH
#    define EMU_GIT_HASH "0000000"
#endif

#if defined(__aarch64__) || defined(_M_ARM64)
#    define ARCH_STR "arm64"
#elif defined(__x86_64) || defined(__x86_64__) || defined(__amd64) || defined(_M_X64)
#    define ARCH_STR "x86_64"
#else
#    define ARCH_STR "unknown arch"
#endif

#ifdef USE_DYNAREC
#    ifdef USE_NEW_DYNAREC
#        define DYNAREC_STR "new dynarec"
#    else
#        define DYNAREC_STR "old dynarec"
#    endif
#else
#    define DYNAREC_STR "no dynarec"
#endif

            printf(
                "%s v%s [%s] [%s, %s]\n\n"
                "An emulator of old computers\n"
                "Authors: Miran Grča (OBattler), RichardG867, Jasmine Iwanek, TC1995, coldbrewed, Teemu Korhonen (Manaatti), "
                "Joakim L. Gilje, Adrien Moulin (elyosh), Daniel Balsom (gloriouscow), Cacodemon345, Fred N. van Kempen (waltje), "
                "Tiseno100, reenigne, and others.\n"
                "With previous core contributions from Sarah Walker, leilei, JohnElliott, greatpsycho, and others.\n\n"
                "Released under the GNU General Public License version 2 or later. See LICENSE for more information.\n",
                EMU_NAME, EMU_VERSION_FULL, EMU_GIT_HASH, ARCH_STR, DYNAREC_STR);
        } else if (strncasecmp(xargv[0], "fullscreen", 10) == 0) {
            video_fullscreen   = video_fullscreen ? 0 : 1;
            fullscreen_pending = 1;
        } else if (strncasecmp(xargv[0], "screenshot", 10) == 0) {
            startblit();
            ++monitors[0].mon_screenshots_raw;
            endblit();
            device_force_redraw();
        } else if (strncasecmp(xargv[0], "pause", 5) == 0) {
            plat_pause(dopause ^ 1);
            printf("%s", dopause ? "Paused.\n" : "Unpaused.\n");
        } else if (strncasecmp(xargv[0], "fastfwd", 7) == 0) {
            fast_forward ^= 1;
            printf("%s", fast_forward ? "Fast forward on.\n" : "Fast forward off.\n");
        } else if (strncasecmp(xargv[0], "hardreset", 9) == 0) {
            pc_reset_hard();
        } else if (strncasecmp(xargv[0], "cdload", 6) == 0 && cmdargc >= 3) {
            uint8_t id;
            bool    err = false;
            char    fn[PATH_MAX] = { 0 };

            if (!xargv[2] || !xargv[1]) {
                free(linecpy);
                return;
            }
            err = monitor_process_media_command(&id, CDROM_NUM, fn, NULL, xargv, cmdargc);
            if (!err) {
                printf("Inserting disc into CD-ROM drive %hhu: %s\n", id, fn);
                cdrom_mount(id, fn);
            }
        } else if (strncasecmp(xargv[0], "fddeject", 8) == 0 && cmdargc >= 2) {
            uint8_t id;
            if (monitor_parse_media_id(xargv[1], FDD_NUM, &id))
                floppy_eject(id);
        } else if (strncasecmp(xargv[0], "cdeject", 7) == 0 && cmdargc >= 2) {
            uint8_t id;
            if (monitor_parse_media_id(xargv[1], CDROM_NUM, &id))
                cdrom_eject(id);
        } else if (strncasecmp(xargv[0], "moeject", 7) == 0 && cmdargc >= 2) {
            uint8_t id;
            if (monitor_parse_media_id(xargv[1], MO_NUM, &id))
                mo_eject(id);
        } else if (strncasecmp(xargv[0], "carteject", 9) == 0 && cmdargc >= 2) {
            uint8_t id;
            if (monitor_parse_media_id(xargv[1], MONITOR_CART_NUM, &id))
                cartridge_eject(id);
        } else if (strncasecmp(xargv[0], "rdiskeject", 10) == 0 && cmdargc >= 2) {
            uint8_t id;
            if (monitor_parse_media_id(xargv[1], RDISK_NUM, &id))
                rdisk_eject(id);
        } else if (strncasecmp(xargv[0], "tapeeject", 9) == 0 && cmdargc >= 2) {
            uint8_t id;
            if (monitor_parse_media_id(xargv[1], TAPE_NUM, &id))
                tape_eject(id);
        } else if (strncasecmp(xargv[0], "fddload", 7) == 0 && cmdargc >= 4) {
            uint8_t id;
            uint8_t wp;
            bool    err = false;
            char    fn[PATH_MAX] = { 0 };

            if (!xargv[2] || !xargv[1]) {
                free(linecpy);
                return;
            }
            err = monitor_process_media_command(&id, FDD_NUM, fn, &wp, xargv, cmdargc);
            if (!err) {
                printf("Inserting disk into floppy drive %c: %s\n", id + 'A', fn);
                floppy_mount(id, fn, wp);
            }
        } else if (strncasecmp(xargv[0], "moload", 6) == 0 && cmdargc >= 4) {
            uint8_t id;
            uint8_t wp;
            bool    err = false;
            char    fn[PATH_MAX] = { 0 };

            if (!xargv[2] || !xargv[1]) {
                free(linecpy);
                return;
            }
            err = monitor_process_media_command(&id, MO_NUM, fn, &wp, xargv, cmdargc);
            if (!err) {
                printf("Inserting into mo drive %hhu: %s\n", id, fn);
                mo_mount(id, fn, wp);
            }
        } else if (strncasecmp(xargv[0], "cartload", 8) == 0 && cmdargc >= 4) {
            uint8_t id;
            uint8_t wp;
            bool    err = false;
            char    fn[PATH_MAX] = { 0 };

            if (!xargv[2] || !xargv[1]) {
                free(linecpy);
                return;
            }
            err = monitor_process_media_command(&id, MONITOR_CART_NUM, fn, &wp, xargv, cmdargc);
            if (!err) {
                printf("Inserting tape into cartridge holder %hhu: %s\n", id, fn);
                cartridge_mount(id, fn, wp);
            }
        } else if (strncasecmp(xargv[0], "rdiskload", 9) == 0 && cmdargc >= 4) {
            uint8_t id;
            uint8_t wp;
            bool    err = false;
            char    fn[PATH_MAX] = { 0 };

            if (!xargv[2] || !xargv[1]) {
                free(linecpy);
                return;
            }
            err = monitor_process_media_command(&id, RDISK_NUM, fn, &wp, xargv, cmdargc);
            if (!err) {
                printf("Inserting disk into removable disk drive %c: %s\n", id + 'A', fn);
                rdisk_mount(id, fn, wp);
            }
        } else if (strncasecmp(xargv[0], "tapeload", 8) == 0 && cmdargc >= 4) {
            uint8_t id;
            uint8_t wp;
            bool    err = false;
            char    fn[PATH_MAX] = { 0 };

            if (!xargv[2] || !xargv[1]) {
                free(linecpy);
                return;
            }
            err = monitor_process_media_command(&id, TAPE_NUM, fn, &wp, xargv, cmdargc);
            if (!err) {
                printf("Inserting disk into tape drive %c: %s\n", id + 'A', fn);
                tape_mount(id, fn, wp);
            }
        }
        free(linecpy);
    }
}

void
monitor_thread(UNUSED(void *param))
{
#if !defined(USE_CLI)
    if (isatty(fileno(stdin)) && isatty(fileno(stdout))) {
        char  *line = NULL;

        printf(EMU_NAME " monitor console.\n");
        while (!exit_event) {
            if (feof(stdin))
                break;

#    ifdef ENABLE_READLINE
            if (f_readline) {
                line = f_readline("(" EMU_NAME ") ");
                monitor_execute_line(line);
                free(line);
            } else {
#    endif
                char tmp[1024] = { 0 };

                printf("(" EMU_NAME ") ");
                line = fgets(tmp, sizeof(tmp), stdin);

                monitor_execute_line(line);
#    ifdef ENABLE_READLINE
            }
#    endif
            line = NULL;
        }
    }
#endif
}
