/* Original 86Box CPU/Dynarec retained: optional Android scheduler cycle budgets. */
#ifndef EMU_86BOX_ANDROID_CPU_CYCLES_H
#define EMU_86BOX_ANDROID_CPU_CYCLES_H
#include <stdint.h>
#include <stddef.h>
/* 0=original, 1=fixed percentage, 2=auto, 3=max (nominal), 4=custom kcps. */
void android_cpu_cycles_set_mode(int mode);
void android_cpu_cycles_set_fixed_percent(int percent);
void android_cpu_cycles_set_custom_kcps(int kcps);
void android_cpu_cycles_observe_speed(int percent);
int32_t android_cpu_cycles_budget(int32_t nominal, int quantum_us);
void android_cpu_cycles_get_status(char *out, size_t capacity);
#endif
