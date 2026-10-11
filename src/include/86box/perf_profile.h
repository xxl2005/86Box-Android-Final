/* Internal host-time profiler used by the Stage 2 multicore work. */
#ifndef EMU_PERF_PROFILE_H
#define EMU_PERF_PROFILE_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef enum perf_profile_category_t {
    PERF_PROFILE_CPU = 0,
    PERF_PROFILE_IO_CHIPSET,
    PERF_PROFILE_VIDEO,
    PERF_PROFILE_SOUND,
    PERF_PROFILE_STORAGE,
    PERF_PROFILE_SCSI_WAIT,
    PERF_PROFILE_CATEGORY_COUNT
} perf_profile_category_t;

#ifdef USE_PERF_PROFILE
extern void     perf_profile_set_enabled(int enabled);
extern int      perf_profile_is_enabled(void);
extern void     perf_profile_reset(void);
extern uint64_t perf_profile_begin(perf_profile_category_t category);
extern void     perf_profile_end(perf_profile_category_t category, uint64_t started_us);
extern void     perf_profile_add_us(perf_profile_category_t category, uint64_t elapsed_us);
extern void     perf_profile_tick_1s(void);
extern int      perf_profile_format(char *buffer, size_t size);
#else
static inline void perf_profile_set_enabled(int enabled) { (void) enabled; }
static inline int perf_profile_is_enabled(void) { return 0; }
static inline void perf_profile_reset(void) { }
static inline uint64_t perf_profile_begin(perf_profile_category_t category) { (void) category; return 0; }
static inline void perf_profile_end(perf_profile_category_t category, uint64_t started_us) { (void) category; (void) started_us; }
static inline void perf_profile_add_us(perf_profile_category_t category, uint64_t elapsed_us) { (void) category; (void) elapsed_us; }
static inline void perf_profile_tick_1s(void) { }
static inline int perf_profile_format(char *buffer, size_t size) { if (size) buffer[0] = 0; return 0; }
#endif

#ifdef __cplusplus
}
#endif
#endif
