/* Experimental touch performance diagnostics (no input behavior changes). */
#ifndef BOX86_ANDROID_TOUCH_LATENCY_H
#define BOX86_ANDROID_TOUCH_LATENCY_H
#include <stdint.h>
#ifdef __cplusplus
extern "C" {
#endif
void android_touchlat_sdl_motion(uint64_t queued_at_ns);
void android_touchlat_present(uint64_t present_completed_ns);
#ifdef __cplusplus
}
#endif
#endif
