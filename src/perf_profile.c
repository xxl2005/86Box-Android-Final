/*
 * Stage 2 internal host-time profiler.
 * Times are host wall-clock microseconds. Categories are intentionally
 * independent and can overlap when worker/render threads run in parallel.
 */
#include <inttypes.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>

#include <86box/86box.h>
#include <86box/perf_profile.h>
#include <86box/plat.h>

#if defined(_MSC_VER)
# include <windows.h>
static volatile LONG64 perf_time_us[PERF_PROFILE_CATEGORY_COUNT];
static volatile LONG64 perf_calls[PERF_PROFILE_CATEGORY_COUNT];
static volatile LONG perf_enabled;
# define PERF_LOAD64(p) ((uint64_t) InterlockedCompareExchange64((volatile LONG64 *)(p), 0, 0))
# define PERF_ADD64(p,v) ((void) InterlockedExchangeAdd64((volatile LONG64 *)(p), (LONG64) (v)))
# define PERF_STORE64(p,v) ((void) InterlockedExchange64((volatile LONG64 *)(p), (LONG64) (v)))
# define PERF_INC64(p) ((uint64_t) InterlockedIncrement64((volatile LONG64 *)(p)))
# define PERF_LOAD_ENABLED() ((int) InterlockedCompareExchange(&perf_enabled, 0, 0))
# define PERF_STORE_ENABLED(v) ((void) InterlockedExchange(&perf_enabled, (LONG) (v)))
#else
static uint64_t perf_time_us[PERF_PROFILE_CATEGORY_COUNT];
static uint64_t perf_calls[PERF_PROFILE_CATEGORY_COUNT];
static int perf_enabled;
# define PERF_LOAD64(p) __atomic_load_n((p), __ATOMIC_RELAXED)
# define PERF_ADD64(p,v) ((void) __atomic_fetch_add((p), (uint64_t) (v), __ATOMIC_RELAXED))
# define PERF_STORE64(p,v) __atomic_store_n((p), (uint64_t) (v), __ATOMIC_RELAXED)
# define PERF_INC64(p) __atomic_add_fetch((p), (uint64_t) 1, __ATOMIC_RELAXED)
# define PERF_LOAD_ENABLED() __atomic_load_n(&perf_enabled, __ATOMIC_RELAXED)
# define PERF_STORE_ENABLED(v) __atomic_store_n(&perf_enabled, (v), __ATOMIC_RELAXED)
#endif

static uint64_t perf_started_us;
static uint64_t perf_seconds;

static const char *const perf_names[PERF_PROFILE_CATEGORY_COUNT] = {
    "CPU/core", "I/O+chipset", "graphics", "sound", "storage", "SCSI wait"
};

void
perf_profile_reset(void)
{
    for (int i = 0; i < PERF_PROFILE_CATEGORY_COUNT; i++) {
        PERF_STORE64(&perf_time_us[i], 0);
        PERF_STORE64(&perf_calls[i], 0);
    }
    PERF_STORE64(&perf_started_us, plat_timer_read_us());
    PERF_STORE64(&perf_seconds, 0);
}

void
perf_profile_set_enabled(int enabled)
{
    enabled = !!enabled;
    if (enabled && !PERF_LOAD_ENABLED())
        perf_profile_reset();
    PERF_STORE_ENABLED(enabled);
    pclog("[PERF] profiler %s\n", enabled ? "enabled" : "disabled");
}

int
perf_profile_is_enabled(void)
{
    return PERF_LOAD_ENABLED();
}

uint64_t
perf_profile_begin(perf_profile_category_t category)
{
    if (!PERF_LOAD_ENABLED() || category < 0 || category >= PERF_PROFILE_CATEGORY_COUNT)
        return 0;
    return plat_timer_read_us();
}

void
perf_profile_add_us(perf_profile_category_t category, uint64_t elapsed_us)
{
    if (!PERF_LOAD_ENABLED() || category < 0 || category >= PERF_PROFILE_CATEGORY_COUNT)
        return;
    PERF_ADD64(&perf_time_us[category], elapsed_us);
    PERF_ADD64(&perf_calls[category], 1);
}

void
perf_profile_end(perf_profile_category_t category, uint64_t started_us)
{
    if (!started_us || !PERF_LOAD_ENABLED())
        return;
    const uint64_t now = plat_timer_read_us();
    if (now >= started_us)
        perf_profile_add_us(category, now - started_us);
}

int
perf_profile_format(char *buffer, size_t size)
{
    if (!buffer || !size)
        return 0;

    const uint64_t now = plat_timer_read_us();
    const uint64_t started = PERF_LOAD64(&perf_started_us);
    const uint64_t wall = (started && now >= started) ? now - started : 1;
    size_t pos = 0;
    int n = snprintf(buffer, size,
                     "86Box host profiler (%.2f s)\nHost-time / wall-time; parallel categories can exceed 100%%.\n",
                     (double) wall / 1000000.0);
    if (n < 0) return 0;
    pos = (size_t) n < size ? (size_t) n : size - 1;

    for (int i = 0; i < PERF_PROFILE_CATEGORY_COUNT && pos < size; i++) {
        const uint64_t us = PERF_LOAD64(&perf_time_us[i]);
        const uint64_t calls = PERF_LOAD64(&perf_calls[i]);
        const double pct = 100.0 * (double) us / (double) wall;
        n = snprintf(buffer + pos, size - pos, "%s: %.1f%%  %.3f s  calls=%" PRIu64 "\n",
                     perf_names[i], pct, (double) us / 1000000.0, calls);
        if (n < 0) break;
        if ((size_t) n >= size - pos) { pos = size - 1; break; }
        pos += (size_t) n;
    }
    buffer[pos] = 0;
    return (int) pos;
}

void
perf_profile_tick_1s(void)
{
    if (!PERF_LOAD_ENABLED())
        return;
    if (PERF_INC64(&perf_seconds) >= 5) {
        char summary[1024];
        PERF_STORE64(&perf_seconds, 0);
        perf_profile_format(summary, sizeof(summary));
        pclog("[PERF]\n%s", summary);
    }
}
