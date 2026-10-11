/* 86Box Android: sampled, low-overhead touch latency observability.
 * SDL3 event.timestamp uses the SDL_GetTicksNS() clock domain. The renderer
 * measurement is only a proxy to the next presentation, not guest cursor E2E.
 */
#include "android_touch_latency.h"
#include <stdatomic.h>
#include <stdint.h>
#include <SDL3/SDL.h>
#include <86box/86box.h>

#define TOUCHLAT_LOG_NS UINT64_C(10000000000)
#define TOUCHLAT_REASONABLE_NS UINT64_C(2000000000)

/* Only the first touch motion after a render is retained; most recent updates
 * must not reset the timer or make output latency look spuriously small. */
static atomic_uint_fast64_t pending_input_ns = ATOMIC_VAR_INIT(0);

/* SDL event loop owns the following stats. */
static uint64_t input_window_ns;
static uint64_t input_last_event_ns;
static uint64_t input_samples, input_age_total_us, input_age_max_us;
static uint64_t input_gap_total_us, input_gap_max_us, input_gap_samples;

/* Render thread owns the following stats. Never read these in event thread. */
static uint64_t present_window_ns;
static uint64_t present_samples, present_total_us, present_max_us;

void
android_touchlat_sdl_motion(uint64_t queued_at_ns)
{
    const uint64_t now = SDL_GetTicksNS();
    uint64_t expected = 0;
    atomic_compare_exchange_strong_explicit(&pending_input_ns, &expected, now,
                                            memory_order_relaxed, memory_order_relaxed);
    if (!input_window_ns) input_window_ns = now;
    if (queued_at_ns && now >= queued_at_ns && now - queued_at_ns < TOUCHLAT_REASONABLE_NS) {
        const uint64_t age_us = (now - queued_at_ns) / UINT64_C(1000);
        input_age_total_us += age_us;
        if (age_us > input_age_max_us) input_age_max_us = age_us;
        input_samples++;
    }
    if (input_last_event_ns && queued_at_ns >= input_last_event_ns &&
        queued_at_ns - input_last_event_ns < TOUCHLAT_REASONABLE_NS) {
        uint64_t gap_us = (queued_at_ns - input_last_event_ns) / UINT64_C(1000);
        input_gap_total_us += gap_us;
        if (gap_us > input_gap_max_us) input_gap_max_us = gap_us;
        input_gap_samples++;
    }
    input_last_event_ns = queued_at_ns;
    if (now - input_window_ns < TOUCHLAT_LOG_NS) return;
    if (input_samples) {
        pclog("[ANDROID][TOUCHLAT] stage=sdl-input events=%llu queueAvgUs=%llu queueMaxUs=%llu intervalAvgUs=%llu intervalMaxUs=%llu note=SDL-event-queue-only\n",
              (unsigned long long) input_samples,
              (unsigned long long) (input_age_total_us / input_samples),
              (unsigned long long) input_age_max_us,
              (unsigned long long) (input_gap_samples ? input_gap_total_us / input_gap_samples : 0),
              (unsigned long long) input_gap_max_us);
    }
    input_window_ns = now;
    input_samples = input_age_total_us = input_age_max_us = 0;
    input_gap_total_us = input_gap_max_us = input_gap_samples = 0;
}

void
android_touchlat_present(uint64_t present_completed_ns)
{
    /* Atomic exchange prevents races with SDL input handling on another thread. */
    uint64_t first_ns = atomic_exchange_explicit(&pending_input_ns, 0, memory_order_relaxed);
    if (!present_window_ns) present_window_ns = present_completed_ns;
    if (first_ns && present_completed_ns >= first_ns &&
        present_completed_ns - first_ns < TOUCHLAT_REASONABLE_NS) {
        uint64_t elapsed_us = (present_completed_ns - first_ns) / UINT64_C(1000);
        present_total_us += elapsed_us;
        if (elapsed_us > present_max_us) present_max_us = elapsed_us;
        present_samples++;
    }
    if (present_completed_ns - present_window_ns < TOUCHLAT_LOG_NS) return;
    if (present_samples) {
        pclog("[ANDROID][TOUCHLAT] stage=next-render-present samples=%llu proxyAvgUs=%llu proxyMaxUs=%llu note=proxy-not-visible-guest-cursor-latency\n",
              (unsigned long long) present_samples,
              (unsigned long long) (present_total_us / present_samples),
              (unsigned long long) present_max_us);
    }
    present_window_ns = present_completed_ns;
    present_samples = present_total_us = present_max_us = 0;
}
