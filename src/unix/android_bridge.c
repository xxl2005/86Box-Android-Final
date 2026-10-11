#include <stdint.h>
#include <stdatomic.h>
#include <stdlib.h>
#include <string.h>
#include <signal.h>
#include <fcntl.h>
#include <unistd.h>
#include <stdio.h>
#include <pthread.h>
#include <errno.h>
#include <stdarg.h>
#include <sched.h>
#include <sys/resource.h>
#include <sys/syscall.h>

#include <jni.h>
#include <SDL3/SDL.h>

#include <86box/86box.h>
#include <86box/plat.h>
#include <86box/perf_profile.h>
#include <86box/device.h>
#include <86box/machine.h>
#include <86box/video.h>
#include <86box/sound.h>
#include <86box/thread.h>
#include <86box/timer.h>
#include <86box/network.h>
#include <86box/mouse.h>
#include <86box/hdc.h>
#include <86box/scsi.h>
#include <86box/fdc_ext.h>
#include <86box/midi.h>
#include <86box/cdrom_interface.h>
#include <86box/char.h>
#include <86box/isartc.h>
#include <86box/isamem.h>
#include <86box/mcamem.h>
#include <86box/isarom.h>
#include "cpu.h"

#include "android_bridge.h"
#include "android_cpu_cycles.h"


static int android_crash_fd = -1;
static const int android_crash_signals[] = { SIGSEGV, SIGABRT, SIGBUS, SIGILL, SIGFPE };
static struct sigaction android_old_crash_actions[5];
static unsigned char android_old_crash_valid[5];

static void
android_native_crash_signal(int sig)
{
    if (android_crash_fd >= 0) {
        switch (sig) {
            case SIGSEGV: { static const char m[] = "Native crash: SIGSEGV\n"; write(android_crash_fd, m, sizeof(m) - 1); break; }
            case SIGABRT: { static const char m[] = "Native crash: SIGABRT\n"; write(android_crash_fd, m, sizeof(m) - 1); break; }
            case SIGBUS:  { static const char m[] = "Native crash: SIGBUS\n";  write(android_crash_fd, m, sizeof(m) - 1); break; }
            case SIGILL:  { static const char m[] = "Native crash: SIGILL\n";  write(android_crash_fd, m, sizeof(m) - 1); break; }
            case SIGFPE:  { static const char m[] = "Native crash: SIGFPE\n";  write(android_crash_fd, m, sizeof(m) - 1); break; }
            default:      { static const char m[] = "Native crash: unknown signal\n"; write(android_crash_fd, m, sizeof(m) - 1); break; }
        }
        { static const char m[] = "See the matching native-*.log for the last emulator messages.\n"; write(android_crash_fd, m, sizeof(m) - 1); }
        fsync(android_crash_fd);
    }

    /* Preserve Android's/debuggerd's pre-existing fatal-signal handler.
       SA_RESETHAND has already removed our own handler before entry, but the
       platform handler saved during installation must be restored explicitly. */
    for (size_t i = 0; i < sizeof(android_crash_signals) / sizeof(android_crash_signals[0]); ++i) {
        if ((android_crash_signals[i] == sig) && android_old_crash_valid[i]) {
            struct sigaction old_action = android_old_crash_actions[i];
            android_old_crash_valid[i] = 0;
            if (old_action.sa_handler == SIG_IGN) {
                memset(&old_action, 0, sizeof(old_action));
                old_action.sa_handler = SIG_DFL;
                sigemptyset(&old_action.sa_mask);
            }
            sigaction(sig, &old_action, NULL);
            break;
        }
    }

    raise(sig); /* Re-deliver on the crashing thread to Android's original handler. */
    _exit(128 + sig); /* Safety fallback if a non-terminating handler unexpectedly returns. */
}

void
android_install_native_crash_marker(void)
{
    char path[1200];
    const char *slash;
    struct sigaction sa;

    if (android_crash_fd >= 0)
        return;

    slash = strrchr(log_path, '/');
    if (slash != NULL) {
        size_t dir_len = (size_t) (slash - log_path);
        if (dir_len > sizeof(path) - 32)
            dir_len = sizeof(path) - 32;
        memcpy(path, log_path, dir_len);
        path[dir_len] = '\0';
        strncat(path, "/native-crash-last.txt", sizeof(path) - strlen(path) - 1);
    } else {
        snprintf(path, sizeof(path), "native-crash-last.txt");
    }

    android_crash_fd = open(path, O_CREAT | O_WRONLY | O_TRUNC, 0600);
    if (android_crash_fd < 0)
        return;

    memset(&sa, 0, sizeof(sa));
    sa.sa_handler = android_native_crash_signal;
    sigemptyset(&sa.sa_mask);
    sa.sa_flags = SA_RESETHAND | SA_NODEFER;
    memset(android_old_crash_valid, 0, sizeof(android_old_crash_valid));
    for (size_t i = 0; i < sizeof(android_crash_signals) / sizeof(android_crash_signals[0]); ++i) {
        if (sigaction(android_crash_signals[i], &sa, &android_old_crash_actions[i]) == 0)
            android_old_crash_valid[i] = 1;
    }
}

void
android_uninstall_native_crash_marker(void)
{
    for (size_t i = 0; i < sizeof(android_crash_signals) / sizeof(android_crash_signals[0]); ++i) {
        if (android_old_crash_valid[i]) {
            sigaction(android_crash_signals[i], &android_old_crash_actions[i], NULL);
            android_old_crash_valid[i] = 0;
        }
    }
    if (android_crash_fd >= 0) {
        close(android_crash_fd);
        android_crash_fd = -1;
    }
}

_Atomic int android_touch_mouse_speed = 100;
_Atomic int android_touch_tap_to_click = 1;
_Atomic int android_view_zoom_percent = 100;
_Atomic int android_view_pan_x_permille = 0;
_Atomic int android_view_pan_y_permille = 0;
_Atomic int android_view_scale_mode = FULLSCR_SCALE_KEEPRATIO;
_Atomic int android_keyboard_layout = 0; /* 0=US, 1=German QWERTZ */
_Atomic int android_performance_mode = 0; /* 0=balanced, 1=performance, 2=maximum */
static _Atomic unsigned long long android_manual_core_mask = 0ULL;
static _Atomic int android_manual_core_mask_enabled = 0;
/* v0.9.28: when true, Performance/Maximum keep priority + ADPF but do not
   pin the CPU thread. Android's scheduler may use the full allowed mask. */
static _Atomic int android_android_decides_cores = 1;
static _Atomic int android_performance_generation = 0;
static _Atomic int android_cpu_thread_tid = -1;
static pthread_mutex_t android_perf_state_mutex = PTHREAD_MUTEX_INITIALIZER;
static char android_perf_state[768] = "not applied yet";
static _Atomic int android_emulator_ready = 0;
static _Atomic int android_runtime_active = 0;
static _Atomic int android_emu_speed_percent = -1;
static _Atomic unsigned int android_emu_speed_generation = 0;
static int android_direct_lifecycle_pause = 0;
static pthread_mutex_t android_renderer_info_mutex = PTHREAD_MUTEX_INITIALIZER;
static char android_renderer_info[128] = "initializing";


static void
android_perf_trace(const char *fmt, ...)
{
    char message[768];
    char path[1200];
    const char *slash;
    va_list ap;
    FILE *f;

    va_start(ap, fmt);
    vsnprintf(message, sizeof(message), fmt, ap);
    va_end(ap);

    pthread_mutex_lock(&android_perf_state_mutex);
    snprintf(android_perf_state, sizeof(android_perf_state), "%s", message);
    pthread_mutex_unlock(&android_perf_state_mutex);

    pclog("[ANDROID][PERF] %s\n", message);

    /* pclog/--logfile can be backend-dependent on Android. Keep a tiny,
       guaranteed performance trace beside the session logs so COMPLETE APP LOG
       always contains the scheduler result even when the normal native log is
       empty or delayed. */
    slash = strrchr(log_path, '/');
    if (slash != NULL) {
        size_t dir_len = (size_t) (slash - log_path);
        if (dir_len > sizeof(path) - 40)
            dir_len = sizeof(path) - 40;
        memcpy(path, log_path, dir_len);
        path[dir_len] = '\0';
        strncat(path, "/native-performance.log", sizeof(path) - strlen(path) - 1);
    } else {
        snprintf(path, sizeof(path), "native-performance.log");
    }
    f = fopen(path, "a");
    if (f != NULL) {
        fprintf(f, "%s\n", message);
        fflush(f);
        fclose(f);
    }
}

static void
android_get_perf_state(char *out, size_t out_size)
{
    if (out == NULL || out_size == 0)
        return;
    pthread_mutex_lock(&android_perf_state_mutex);
    snprintf(out, out_size, "%s", android_perf_state);
    pthread_mutex_unlock(&android_perf_state_mutex);
}

void
android_set_emulator_ready(int ready)
{
    atomic_store_explicit(&android_emulator_ready, ready ? 1 : 0, memory_order_release);
}

int
android_is_emulator_ready(void)
{
    return atomic_load_explicit(&android_emulator_ready, memory_order_acquire);
}

void
android_set_runtime_active(int active)
{
    atomic_store_explicit(&android_runtime_active, active ? 1 : 0, memory_order_release);
}

int
android_is_runtime_active(void)
{
    return atomic_load_explicit(&android_runtime_active, memory_order_acquire);
}

void
android_set_emu_speed_percent(int speed_percent)
{
    if (speed_percent < 0)
        speed_percent = -1; /* -1 means "not available yet". */
    else if (speed_percent > 9999)
        speed_percent = 9999;
    android_cpu_cycles_observe_speed(speed_percent);
    atomic_store_explicit(&android_emu_speed_percent, speed_percent, memory_order_relaxed);
    atomic_fetch_add_explicit(&android_emu_speed_generation, 1U, memory_order_relaxed);
}




#define ANDROID_AFFINITY_MAX_CPUS 256
#define ANDROID_AFFINITY_WORD_BITS ((int) (sizeof(unsigned long) * 8U))
#define ANDROID_AFFINITY_WORDS ((ANDROID_AFFINITY_MAX_CPUS + ANDROID_AFFINITY_WORD_BITS - 1) / ANDROID_AFFINITY_WORD_BITS)

typedef struct android_cpu_mask_t {
    unsigned long bits[ANDROID_AFFINITY_WORDS];
} android_cpu_mask_t;

static void
android_cpu_mask_zero(android_cpu_mask_t *mask)
{
    memset(mask, 0, sizeof(*mask));
}

static int
android_cpu_mask_isset(int cpu, const android_cpu_mask_t *mask)
{
    if (cpu < 0 || cpu >= ANDROID_AFFINITY_MAX_CPUS)
        return 0;
    return (mask->bits[cpu / ANDROID_AFFINITY_WORD_BITS] &
            (1UL << (cpu % ANDROID_AFFINITY_WORD_BITS))) != 0;
}

static void
android_cpu_mask_set(int cpu, android_cpu_mask_t *mask)
{
    if (cpu < 0 || cpu >= ANDROID_AFFINITY_MAX_CPUS)
        return;
    mask->bits[cpu / ANDROID_AFFINITY_WORD_BITS] |=
        (1UL << (cpu % ANDROID_AFFINITY_WORD_BITS));
}



static int
android_mask_count(const android_cpu_mask_t *mask)
{
    int count=0;
    for (int cpu=0;cpu<ANDROID_AFFINITY_MAX_CPUS;++cpu)
        if (android_cpu_mask_isset(cpu,mask)) ++count;
    return count;
}

static int
android_sched_getaffinity(pid_t tid, android_cpu_mask_t *mask)
{
#if defined(SYS_sched_getaffinity)
    long rc;
    android_cpu_mask_zero(mask);
    rc = syscall(SYS_sched_getaffinity, tid, sizeof(*mask), mask);
    return rc < 0 ? -1 : 0;
#else
    (void) tid;
    (void) mask;
    errno = ENOSYS;
    return -1;
#endif
}

static int
android_sched_setaffinity(pid_t tid, const android_cpu_mask_t *mask)
{
#if defined(SYS_sched_setaffinity)
    long rc = syscall(SYS_sched_setaffinity, tid, sizeof(*mask), mask);
    return rc < 0 ? -1 : 0;
#else
    (void) tid;
    (void) mask;
    errno = ENOSYS;
    return -1;
#endif
}

static long
android_read_long_file(const char *path)
{
    FILE *f = fopen(path, "r");
    long value = 0;
    if (f == NULL)
        return 0;
    if (fscanf(f, "%ld", &value) != 1)
        value = 0;
    fclose(f);
    return value;
}


int
android_get_performance_mode(void)
{
    int mode = atomic_load_explicit(&android_performance_mode, memory_order_relaxed);
    if (mode < 0) mode = 0;
    if (mode > 2) mode = 2;
    return mode;
}

int
android_get_performance_generation(void)
{
    return atomic_load_explicit(&android_performance_generation, memory_order_relaxed);
}

void
android_apply_cpu_thread_performance_tuning(int mode)
{
#if defined(__ANDROID__)
    pid_t tid = (pid_t) syscall(SYS_gettid);
    atomic_store_explicit(&android_cpu_thread_tid, (int) tid, memory_order_relaxed);
    int desired_nice = 0;
    int nice_rc;
    int actual_nice;
    int nice_errno = 0;

    if (mode < 0) mode = 0;
    if (mode > 2) mode = 2;
    if (mode == 1) desired_nice = -4;
    if (mode == 2) desired_nice = -10;

    errno = 0;
    nice_rc = setpriority(PRIO_PROCESS, tid, desired_nice);
    nice_errno = errno;
    errno = 0;
    actual_nice = getpriority(PRIO_PROCESS, tid);

#if defined(__aarch64__)
    {
        static __thread android_cpu_mask_t original_allowed;
        static __thread int original_allowed_valid = 0;
        android_cpu_mask_t allowed;
        android_cpu_mask_t target;
        long score[ANDROID_AFFINITY_MAX_CPUS];
        long max_score = 0;
        int allowed_count = 0;
        int selected_count = 0;
        int cpu;
        int affinity_rc = -1;
        int affinity_errno = 0;

        android_cpu_mask_zero(&allowed);
        android_cpu_mask_zero(&target);
        memset(score, 0, sizeof(score));
        if (!original_allowed_valid && android_sched_getaffinity(tid, &original_allowed) == 0)
            original_allowed_valid = 1;
        if (original_allowed_valid) {
            allowed = original_allowed;
        } else if (android_sched_getaffinity(tid, &allowed) == 0) {
            original_allowed = allowed;
            original_allowed_valid = 1;
        }

        if (mode == 0) {
            if (original_allowed_valid) {
                errno = 0;
                affinity_rc = android_sched_setaffinity(tid, &original_allowed);
                affinity_errno = errno;
            }
            android_perf_trace("CPU thread mode=balanced tid=%d nice_requested=%d nice_rc=%d errno=%d actual_nice=%d affinity_restore_rc=%d affinity_errno=%d",
                  (int) tid, desired_nice, nice_rc, nice_errno, actual_nice, affinity_rc, affinity_errno);
            return;
        }

        if (original_allowed_valid && atomic_load_explicit(&android_manual_core_mask_enabled, memory_order_relaxed)) {
            uint64_t manual_mask = (uint64_t) atomic_load_explicit(&android_manual_core_mask, memory_order_relaxed);
            if (manual_mask != 0ULL) {
                for (cpu = 0; cpu < 64 && cpu < ANDROID_AFFINITY_MAX_CPUS; ++cpu) {
                    if ((manual_mask & (1ULL << cpu)) != 0ULL && android_cpu_mask_isset(cpu, &allowed)) {
                        android_cpu_mask_set(cpu, &target);
                        ++selected_count;
                    }
                }
                if (selected_count > 0) {
                    errno = 0;
                    affinity_rc = android_sched_setaffinity(tid, &target);
                    affinity_errno = errno;
                    android_perf_trace("CPU thread mode=%d tid=%d nice_requested=%d nice_rc=%d errno=%d actual_nice=%d manual_mask=0x%016llx affinity_selected=%d affinity_rc=%d affinity_errno=%d",
                          mode, (int) tid, desired_nice, nice_rc, nice_errno, actual_nice,
                          (unsigned long long) manual_mask, selected_count, affinity_rc, affinity_errno);
                    return;
                }
                android_perf_trace("Manual core mask 0x%016llx had no allowed CPUs; falling back to automatic selection",
                      (unsigned long long) manual_mask);
                android_cpu_mask_zero(&target);
            }
        }

        /* Core Strategy = Auto (Android/ADPF decides): keep Performance/Maximum
           priority and Java ADPF hints, but restore the full Android-provided
           affinity mask instead of selecting two CPUs ourselves. */
        if (original_allowed_valid && atomic_load_explicit(&android_android_decides_cores, memory_order_relaxed)) {
            allowed_count = android_mask_count(&original_allowed);
            errno = 0;
            affinity_rc = android_sched_setaffinity(tid, &original_allowed);
            affinity_errno = errno;
            android_perf_trace("CPU thread mode=%d tid=%d nice_requested=%d nice_rc=%d errno=%d actual_nice=%d affinity=android/adpf allowed=%d affinity_restore_rc=%d affinity_errno=%d",
                  mode, (int) tid, desired_nice, nice_rc, nice_errno, actual_nice,
                  allowed_count, affinity_rc, affinity_errno);
            return;
        }

        if (original_allowed_valid) {
            for (cpu = 0; cpu < ANDROID_AFFINITY_MAX_CPUS; ++cpu) {
                char path[160];
                long capacity = 0;
                long freq = 0;
                if (!android_cpu_mask_isset(cpu, &allowed))
                    continue;
                ++allowed_count;
                snprintf(path, sizeof(path), "/sys/devices/system/cpu/cpu%d/cpu_capacity", cpu);
                capacity = android_read_long_file(path);
                snprintf(path, sizeof(path), "/sys/devices/system/cpu/cpu%d/cpufreq/cpuinfo_max_freq", cpu);
                freq = android_read_long_file(path);
                if (freq <= 0) {
                    snprintf(path, sizeof(path), "/sys/devices/system/cpu/cpu%d/cpufreq/scaling_max_freq", cpu);
                    freq = android_read_long_file(path);
                }
                /* cpu_capacity is preferable; frequency is a safe universal fallback. */
                score[cpu] = capacity > 0 ? capacity * 10000000L + freq : freq;
                if (score[cpu] > max_score)
                    max_score = score[cpu];
            }

            if (max_score > 0) {
                const long threshold_num = mode >= 2 ? 90 : 80;
                for (cpu = 0; cpu < ANDROID_AFFINITY_MAX_CPUS; ++cpu) {
                    if (android_cpu_mask_isset(cpu, &allowed) && score[cpu] > 0 && score[cpu] * 100 >= max_score * threshold_num) {
                        android_cpu_mask_set(cpu, &target);
                        ++selected_count;
                    }
                }
                /* Avoid pinning a hot emulator thread to only one prime core. */
                while (selected_count < 2 && selected_count < allowed_count) {
                    int best_cpu = -1;
                    long best = -1;
                    for (cpu = 0; cpu < ANDROID_AFFINITY_MAX_CPUS; ++cpu) {
                        if (android_cpu_mask_isset(cpu, &allowed) && !android_cpu_mask_isset(cpu, &target) && score[cpu] > best) {
                            best = score[cpu];
                            best_cpu = cpu;
                        }
                    }
                    if (best_cpu < 0)
                        break;
                    android_cpu_mask_set(best_cpu, &target);
                    ++selected_count;
                }
                errno = 0;
                affinity_rc = android_sched_setaffinity(tid, &target);
                affinity_errno = errno;
            }
        }
        android_perf_trace("CPU thread mode=%d tid=%d nice_requested=%d nice_rc=%d errno=%d actual_nice=%d affinity_selected=%d/%d affinity_rc=%d affinity_errno=%d",
              mode, (int) tid, desired_nice, nice_rc, nice_errno, actual_nice,
              selected_count, allowed_count, affinity_rc, affinity_errno);
    }
#else
    android_perf_trace("CPU thread mode=%d tid=%d nice_requested=%d nice_rc=%d errno=%d actual_nice=%d affinity=not-arm64",
          mode, (int) tid, desired_nice, nice_rc, nice_errno, actual_nice);
#endif
#else
    (void) mode;
#endif
}

void
android_set_renderer_info(const char *name)
{
    if (name == NULL || name[0] == '\0')
        name = "initializing";
    pthread_mutex_lock(&android_renderer_info_mutex);
    snprintf(android_renderer_info, sizeof(android_renderer_info), "%s", name);
    pthread_mutex_unlock(&android_renderer_info_mutex);
}

void
android_get_renderer_info(char *out, size_t out_size)
{
    if (out == NULL || out_size == 0)
        return;
    pthread_mutex_lock(&android_renderer_info_mutex);
    snprintf(out, out_size, "%s", android_renderer_info);
    pthread_mutex_unlock(&android_renderer_info_mutex);
}


static void
android_push_hardware_event(int code, intptr_t data1, intptr_t data2)
{
    SDL_Event event;
    SDL_zero(event);
    event.type = SDL_EVENT_USER;
    event.user.code = code;
    event.user.data1 = (void *) data1;
    event.user.data2 = (void *) data2;
    SDL_PushEvent(&event);
}

static void
android_push_control(int action, int value)
{
    SDL_Event event;
    SDL_zero(event);
    event.type = SDL_EVENT_USER;
    event.user.code = ANDROID_USER_EVENT_CONTROL;
    event.user.data1 = (void *)(intptr_t) action;
    event.user.data2 = (void *)(intptr_t) value;
    SDL_PushEvent(&event);
}

JNIEXPORT jboolean JNICALL
Java_net_box86_android_NativeHardwareCatalog_nativeRuntimeActive(JNIEnv *env, jclass clazz)
{
    (void) env;
    (void) clazz;
    return android_is_runtime_active() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_net_box86_android_Box86Activity_nativeGetEmulationSpeedPercent(JNIEnv *env, jclass clazz)
{
    (void) env;
    (void) clazz;
    return (jint) atomic_load_explicit(&android_emu_speed_percent, memory_order_relaxed);
}

JNIEXPORT void JNICALL
Java_net_box86_android_Box86Activity_nativeControl(JNIEnv *env, jclass clazz, jint action, jint value)
{
    (void) env;
    (void) clazz;

    /* Settings are safe to update directly and may be sent by Java before
       SDL's event loop is fully running. Keep runtime actions on the SDL thread. */
    switch ((int) action) {
        case ANDROID_CTRL_MOUSE_SPEED:
            if (value < 25) value = 25;
            if (value > 300) value = 300;
            android_touch_mouse_speed = (int) value;
            return;

        case ANDROID_CTRL_TAP_TO_CLICK:
            android_touch_tap_to_click = value ? 1 : 0;
            return;

        case ANDROID_CTRL_ZOOM:
            if (value < 50) value = 50;
            if (value > 300) value = 300;
            android_view_zoom_percent = (int) value;
            if (value <= 100) {
                android_view_pan_x_permille = 0;
                android_view_pan_y_permille = 0;
            }
            return;

        case ANDROID_CTRL_DISPLAY_SCALE_MODE:
            if (value < FULLSCR_SCALE_FULL) value = FULLSCR_SCALE_FULL;
            if (value > FULLSCR_SCALE_INT43) value = FULLSCR_SCALE_INT43;
            android_view_scale_mode = (int) value;
            android_view_pan_x_permille = 0;
            android_view_pan_y_permille = 0;
            return;

        case ANDROID_CTRL_ZOOM_RESET:
            android_view_zoom_percent = 100;
            android_view_pan_x_permille = 0;
            android_view_pan_y_permille = 0;
            return;

        case ANDROID_CTRL_KEYBOARD_LAYOUT:
            android_keyboard_layout = value ? 1 : 0;
            return;

        case ANDROID_CTRL_PERF_PROFILE:
            perf_profile_set_enabled(value ? 1 : 0);
            return;

        case ANDROID_CTRL_HOST_PERFORMANCE_MODE:
            if (value < 0) value = 0;
            if (value > 2) value = 2;
            atomic_store_explicit(&android_performance_mode, (int) value, memory_order_relaxed);
            atomic_fetch_add_explicit(&android_performance_generation, 1, memory_order_relaxed);
            return;

        case ANDROID_CTRL_CPU_CYCLES_MODE:
            android_cpu_cycles_set_mode((int)value);
            return;
        case ANDROID_CTRL_CPU_CYCLES_FIXED_PERCENT:
            android_cpu_cycles_set_fixed_percent((int)value);
            return;
        case ANDROID_CTRL_CPU_CYCLES_CUSTOM_KCPS:
            android_cpu_cycles_set_custom_kcps((int)value);
            return;


        default:
            break;
    }

    android_push_control((int) action, (int) value);
}

JNIEXPORT jstring JNICALL
Java_net_box86_android_Box86Activity_nativeGetCpuCyclesState(JNIEnv *env, jclass clazz)
{
    (void) clazz;
    char text[512];
    android_cpu_cycles_get_status(text, sizeof(text));
    return (*env)->NewStringUTF(env, text);
}

JNIEXPORT jint JNICALL
Java_net_box86_android_Box86Activity_nativeGetCpuThreadTid(JNIEnv *env, jclass clazz)
{
    (void) env;
    (void) clazz;
    return (jint) atomic_load_explicit(&android_cpu_thread_tid, memory_order_relaxed);
}

JNIEXPORT jstring JNICALL
Java_net_box86_android_Box86Activity_nativeGetHostPerfState(JNIEnv *env, jclass clazz)
{
    (void) clazz;
    char state[768];
    android_get_perf_state(state, sizeof(state));
    return (*env)->NewStringUTF(env, state);
}

JNIEXPORT void JNICALL
Java_net_box86_android_Box86Activity_nativeSetManualCoreMask(JNIEnv *env, jclass clazz, jlong mask, jboolean enabled)
{
    (void) env;
    (void) clazz;
    atomic_store_explicit(&android_manual_core_mask, (unsigned long long) mask, memory_order_relaxed);
    atomic_store_explicit(&android_manual_core_mask_enabled, enabled ? 1 : 0, memory_order_relaxed);
    atomic_store_explicit(&android_android_decides_cores, enabled ? 0 : 1, memory_order_relaxed);
    atomic_fetch_add_explicit(&android_performance_generation, 1, memory_order_relaxed);
    pclog("[ANDROID][PERF] manual core selection enabled=%d mask=0x%016llx android_adpf_decides=%d\n",
          enabled ? 1 : 0, (unsigned long long) mask, enabled ? 0 : 1);
}

JNIEXPORT void JNICALL
Java_net_box86_android_Box86Activity_nativeMountMedia(JNIEnv *env, jclass clazz, jint media_type, jstring path)
{
    (void) clazz;
    if (path == NULL)
        return;

    const char *utf = (*env)->GetStringUTFChars(env, path, NULL);
    if (utf == NULL)
        return;

    const size_t utf_len = strlen(utf);
    char *copy = (char *) malloc(utf_len + 1);
    if (copy != NULL)
        memcpy(copy, utf, utf_len + 1);
    (*env)->ReleaseStringUTFChars(env, path, utf);
    if (copy == NULL)
        return;

    SDL_Event event;
    SDL_zero(event);
    event.type = SDL_EVENT_USER;
    event.user.code = ANDROID_USER_EVENT_MEDIA;
    event.user.data1 = (void *)(intptr_t) media_type;
    event.user.data2 = copy;

    if (!SDL_PushEvent(&event))
        free(copy);
}


JNIEXPORT jstring JNICALL
Java_net_box86_android_Box86Activity_nativeGetRendererInfo(JNIEnv *env, jclass clazz)
{
    (void) clazz;
    char name[128];
    android_get_renderer_info(name, sizeof(name));
    return (*env)->NewStringUTF(env, name);
}

JNIEXPORT jstring JNICALL
Java_net_box86_android_Box86Activity_nativeGetPerfSummary(JNIEnv *env, jclass clazz)
{
    (void) clazz;
    char summary[1024];
    if (!perf_profile_format(summary, sizeof(summary)) || summary[0] == '\0')
        return (*env)->NewStringUTF(env, "Profiler disabled or no data collected yet.");
    return (*env)->NewStringUTF(env, summary);
}


JNIEXPORT void JNICALL
Java_net_box86_android_Box86Activity_nativeLifecyclePause(JNIEnv *env, jclass clazz, jboolean paused)
{
    (void) env;
    (void) clazz;

    if (!android_is_emulator_ready())
        return;

    if (paused) {
        if (!dopause) {
            do_pause_android_lifecycle(1);
            android_direct_lifecycle_pause = 1;
        } else {
            android_direct_lifecycle_pause = 0;
        }
    } else if (android_direct_lifecycle_pause) {
        do_pause_android_lifecycle(0);
        android_direct_lifecycle_pause = 0;
    }
}


JNIEXPORT void JNICALL
Java_net_box86_android_Box86Activity_nativeHardwareKey(JNIEnv *env, jclass clazz, jint xt_code, jboolean down)
{
    (void) env;
    (void) clazz;
    if (xt_code <= 0 || xt_code > 0x1ff)
        return;
    android_push_hardware_event(ANDROID_USER_EVENT_HW_KEY, (intptr_t) xt_code, down ? 1 : 0);
}

JNIEXPORT void JNICALL
Java_net_box86_android_Box86Activity_nativeHardwareMouseMove(JNIEnv *env, jclass clazz, jint dx, jint dy)
{
    (void) env;
    (void) clazz;
    if (dx == 0 && dy == 0)
        return;
    android_push_hardware_event(ANDROID_USER_EVENT_HW_MOUSE_MOVE, (intptr_t) dx, (intptr_t) dy);
}

JNIEXPORT void JNICALL
Java_net_box86_android_Box86Activity_nativeHardwareMouseButton(JNIEnv *env, jclass clazz, jint button_mask, jboolean down)
{
    (void) env;
    (void) clazz;
    if ((button_mask & ~31) != 0 || button_mask == 0)
        return;
    android_push_hardware_event(ANDROID_USER_EVENT_HW_MOUSE_BUTTON, (intptr_t) button_mask, down ? 1 : 0);
}

JNIEXPORT void JNICALL
Java_net_box86_android_Box86Activity_nativeHardwareMouseWheel(JNIEnv *env, jclass clazz, jint vertical_steps)
{
    (void) env;
    (void) clazz;
    if (vertical_steps == 0)
        return;
    android_push_hardware_event(ANDROID_USER_EVENT_HW_MOUSE_WHEEL, (intptr_t) vertical_steps, 0);
}

JNIEXPORT void JNICALL
Java_net_box86_android_Box86Activity_nativeHardwareMouseCapture(JNIEnv *env, jclass clazz, jboolean captured)
{
    (void) env;
    (void) clazz;
    android_push_hardware_event(ANDROID_USER_EVENT_HW_MOUSE_CAPTURE, captured ? 1 : 0, 0);
}

JNIEXPORT void JNICALL
Java_net_box86_android_Box86Activity_nativeHardwareReleaseAll(JNIEnv *env, jclass clazz)
{
    (void) env;
    (void) clazz;
    android_push_hardware_event(ANDROID_USER_EVENT_HW_RELEASE_ALL, 0, 0);
}

/* -------------------------------------------------------------------------
 * Android dynamic hardware catalogue
 * -------------------------------------------------------------------------
 * The Java configurator queries the same compiled registries used by the
 * desktop UI instead of maintaining a tiny duplicate list.
 */
typedef struct android_catalog_buffer_t {
    char   *data;
    size_t  len;
    size_t  cap;
} android_catalog_buffer_t;

static int
android_catalog_reserve(android_catalog_buffer_t *b, size_t extra)
{
    size_t need = b->len + extra + 1;
    if (need <= b->cap)
        return 1;

    size_t new_cap = b->cap ? b->cap : 4096;
    while (new_cap < need)
        new_cap *= 2;

    char *p = (char *) realloc(b->data, new_cap);
    if (p == NULL)
        return 0;

    b->data = p;
    b->cap = new_cap;
    return 1;
}

static int
android_catalog_append(android_catalog_buffer_t *b, const char *s)
{
    if (s == NULL)
        s = "";
    size_t n = strlen(s);
    if (!android_catalog_reserve(b, n))
        return 0;
    memcpy(b->data + b->len, s, n);
    b->len += n;
    b->data[b->len] = 0;
    return 1;
}

static int
android_catalog_append_field(android_catalog_buffer_t *b, const char *s)
{
    if (s == NULL)
        s = "";
    for (const unsigned char *p = (const unsigned char *) s; *p; ++p) {
        char c = (char) *p;
        if (c == '\t' || c == '\r' || c == '\n')
            c = ' ';
        if (!android_catalog_reserve(b, 1))
            return 0;
        b->data[b->len++] = c;
        b->data[b->len] = 0;
    }
    return 1;
}

static int
android_catalog_append_int(android_catalog_buffer_t *b, long long value)
{
    char temp[64];
    snprintf(temp, sizeof(temp), "%lld", value);
    return android_catalog_append(b, temp);
}

static int
android_catalog_append_double(android_catalog_buffer_t *b, double value)
{
    char temp[64];
    snprintf(temp, sizeof(temp), "%.8g", value);
    return android_catalog_append(b, temp);
}

static int
android_catalog_row_begin(android_catalog_buffer_t *b, const char *label, const char *value, int id)
{
    return android_catalog_append_field(b, label) &&
           android_catalog_append(b, "\t") &&
           android_catalog_append_field(b, value) &&
           android_catalog_append(b, "\t") &&
           android_catalog_append_int(b, id);
}

static int
android_catalog_col_int(android_catalog_buffer_t *b, long long value)
{
    return android_catalog_append(b, "\t") && android_catalog_append_int(b, value);
}

static int
android_catalog_col_double(android_catalog_buffer_t *b, double value)
{
    return android_catalog_append(b, "\t") && android_catalog_append_double(b, value);
}

static int
android_catalog_row_end(android_catalog_buffer_t *b)
{
    return android_catalog_append(b, "\n");
}

static void
android_catalog_device_label(const device_t *dev, char *out, size_t out_size)
{
    if (out_size == 0)
        return;
    out[0] = 0;
    if (dev == NULL)
        return;

    char temp[1024] = { 0 };
    device_get_name(dev, 1, temp);
    if (temp[0] == 0)
        snprintf(temp, sizeof(temp), "%s", dev->name ? dev->name : dev->internal_name);
    snprintf(out, out_size, "%s", temp);
}

static int
android_catalog_add_device(android_catalog_buffer_t *b, const device_t *dev, const char *internal_name, int id)
{
    if (dev == NULL || internal_name == NULL)
        return 0;

    char name[1200];
    android_catalog_device_label(dev, name, sizeof(name));
    if (!android_catalog_row_begin(b, name, internal_name, id))
        return 0;
    if (!android_catalog_col_int(b, (long long) dev->flags))
        return 0;
    return android_catalog_row_end(b);
}

JNIEXPORT jstring JNICALL
Java_net_box86_android_NativeHardwareCatalog_nativeCatalog(JNIEnv *env, jclass clazz,
                                                            jint kind, jint machine_id,
                                                            jint item_id, jint slot,
                                                            jboolean include_incompatible)
{
    (void) clazz;
    android_catalog_buffer_t b = { 0 };
    android_catalog_reserve(&b, 4096);
    if (b.data != NULL)
        b.data[0] = 0;

    const int mid = (int) machine_id;
    const int iid = (int) item_id;
    const int sl = (int) slot;
    const int show_all = include_incompatible ? 1 : 0;

    switch ((int) kind) {
        case 1: /* MACHINE_TYPES */
            for (int i = 1; i < MACHINE_TYPE_MAX; ++i) {
                const char *name = machine_types[i].name;
                if (name == NULL)
                    continue;
                android_catalog_row_begin(&b, name, "", machine_types[i].id);
                android_catalog_row_end(&b);
            }
            break;

        case 2: /* MACHINES */
            for (int i = 0; i < machine_count(); ++i) {
                int type = machine_get_type(i);
                if (iid >= 0 && type != iid)
                    continue;
                const char *name = machine_getname(i);
                const char *internal = machine_get_internal_name_ex(i);
                if (name == NULL || internal == NULL)
                    continue;
                android_catalog_row_begin(&b, name, internal, i);
                android_catalog_col_int(&b, type);
                android_catalog_col_int(&b, machine_get_min_ram(i));
                android_catalog_col_int(&b, machine_get_max_ram(i));
                android_catalog_col_int(&b, machine_get_ram_granularity(i));
                /* Extra column 4: whether this motherboard has an internal HDC.
                   Android uses it to mirror the desktop hdc_1 default exactly. */
                android_catalog_col_int(&b, machine_has_flags(i, MACHINE_HDC) ? 1 : 0);
                android_catalog_row_end(&b);
            }
            break;

        case 3: /* CPU_FAMILIES */
            if (show_all || (mid >= 0 && mid < machine_count())) {
                for (int i = 0; cpu_families[i].package != 0; ++i) {
                    if (!show_all && !cpu_family_is_eligible(&cpu_families[i], mid))
                        continue;
                    char name[512];
                    snprintf(name, sizeof(name), "%s %s",
                             cpu_families[i].manufacturer ? cpu_families[i].manufacturer : "",
                             cpu_families[i].name ? cpu_families[i].name : "");
                    android_catalog_row_begin(&b, name, cpu_families[i].internal_name, i);
                    android_catalog_row_end(&b);
                }
            }
            break;

        case 4: /* CPU_SPEEDS */
            if ((show_all || (mid >= 0 && mid < machine_count())) && iid >= 0) {
                const cpu_family_t *family = &cpu_families[iid];
                if (family->package != 0) {
                    for (int c = 0; family->cpus[c].cpu_type != 0; ++c) {
                        if (!show_all && !cpu_is_eligible(family, c, mid))
                            continue;
                        const CPU *cpu = &family->cpus[c];
                        char label[256];
                        if (cpu->rspeed >= 1000000)
                            snprintf(label, sizeof(label), "%s MHz", cpu->name ? cpu->name : "");
                        else
                            snprintf(label, sizeof(label), "%s", cpu->name ? cpu->name : "");
                        char speed[64];
                        snprintf(speed, sizeof(speed), "%u", cpu->rspeed);
                        android_catalog_row_begin(&b, label, speed, c);
                        android_catalog_col_double(&b, cpu->multi);
                        android_catalog_col_int(&b, cpu->cpu_flags);
                        android_catalog_col_int(&b, (long long) cpu->cpu_type);
                        android_catalog_row_end(&b);
                    }
                }
            }
            break;

        case 5: /* FPUS */
            if (iid >= 0 && sl >= 0 && cpu_families[iid].package != 0) {
                const CPU *cpu = &cpu_families[iid].cpus[sl];
                if (cpu->cpu_type != 0 && cpu->fpus != NULL) {
                    for (int f = 0; cpu->fpus[f].name != NULL; ++f) {
                        android_catalog_row_begin(&b,
                                                  cpu->fpus[f].name,
                                                  cpu->fpus[f].internal_name,
                                                  cpu->fpus[f].type);
                        android_catalog_row_end(&b);
                    }
                }
            }
            break;

        case 10: /* VIDEO */
            if (show_all || (mid >= 0 && mid < machine_count())) {
                for (int c = 0;; ++c) {
                    const device_t *dev = video_card_getdevice(c);
                    if (dev == NULL)
                        break;
                    if (!show_all && c == 1 && (sl > 0 || machine_get_vid_device(mid) == NULL))
                        continue;
                    if (!show_all && !device_is_valid(dev, mid))
                        continue;
                    android_catalog_add_device(&b, dev, video_get_internal_name(c), c);
                }
            }
            break;

        case 11: /* SOUND */
            if (show_all || (mid >= 0 && mid < machine_count())) {
                for (int c = 0;; ++c) {
                    const device_t *dev = sound_card_getdevice(c);
                    if (dev == NULL)
                        break;
                    if (!show_all && c == 1 && (sl > 0 || machine_get_snd_device(mid) == NULL))
                        continue;
                    if (!show_all && !device_is_valid(dev, mid))
                        continue;
                    android_catalog_add_device(&b, dev, sound_card_get_internal_name(c), c);
                }
            }
            break;

        case 12: /* NETWORK */
            if (show_all || (mid >= 0 && mid < machine_count())) {
                for (int c = 0;; ++c) {
                    const device_t *dev = network_card_getdevice(c);
                    if (dev == NULL)
                        break;
                    if (!show_all && c == 1 && (sl > 0 || machine_get_net_device(mid) == NULL))
                        continue;
                    if (!show_all && !device_is_valid(dev, mid))
                        continue;
                    android_catalog_add_device(&b, dev, network_card_get_internal_name(c), c);
                }
            }
            break;

        case 13: /* MOUSE */
            if (show_all || (mid >= 0 && mid < machine_count())) {
                for (int c = 0; c < mouse_get_ndev(); ++c) {
                    const device_t *dev = mouse_get_device(c);
                    if (dev == NULL)
                        continue;
                    if (!show_all && c == MOUSE_TYPE_INTERNAL && !machine_has_flags(mid, MACHINE_MOUSE))
                        continue;
                    if (!show_all && !device_is_valid(dev, mid))
                        continue;
                    android_catalog_add_device(&b, dev, mouse_get_internal_name(c), c);
                }
            }
            break;

        case 14: /* HDC */
            if (show_all || (mid >= 0 && mid < machine_count())) {
                for (int c = 0;; ++c) {
                    const device_t *dev = hdc_get_device(c);
                    if (dev == NULL)
                        break;
                    if (!show_all && c == HDC_INTERNAL && (sl > 0 || !machine_has_flags(mid, MACHINE_HDC)))
                        continue;
                    if (!show_all && !device_is_valid(dev, mid))
                        continue;
                    android_catalog_add_device(&b, dev, hdc_get_internal_name(c), c);
                }
            }
            break;

        case 15: /* SCSI */
            if (show_all || (mid >= 0 && mid < machine_count())) {
                for (int c = 0;; ++c) {
                    const device_t *dev = scsi_card_getdevice(c);
                    if (dev == NULL)
                        break;
                    if (!show_all && !device_is_valid(dev, mid))
                        continue;
                    android_catalog_add_device(&b, dev, scsi_card_get_internal_name(c), c);
                }
            }
            break;

        case 16: /* FDC */
            if (show_all || (mid >= 0 && mid < machine_count())) {
                for (int c = 0;; ++c) {
                    const device_t *dev = fdc_card_getdevice(c);
                    if (dev == NULL)
                        break;
                    if (!show_all && !device_is_valid(dev, mid))
                        continue;
                    android_catalog_add_device(&b, dev, fdc_card_get_internal_name(c), c);
                }
            }
            break;

        case 17: /* MIDI_OUT */
            if (show_all || (mid >= 0 && mid < machine_count())) {
                for (int c = 0;; ++c) {
                    const device_t *dev = midi_out_device_getdevice(c);
                    if (dev == NULL)
                        break;
                    if (!show_all && !device_is_valid(dev, mid))
                        continue;
                    android_catalog_add_device(&b, dev, midi_out_device_get_internal_name(c), c);
                }
            }
            break;

        case 18: /* CDROM_INTERFACE */
            if (show_all || (mid >= 0 && mid < machine_count())) {
                for (int c = 0;; ++c) {
                    const device_t *dev = cdrom_interface_get_device(c);
                    if (dev == NULL)
                        break;
                    if (!show_all && !device_is_valid(dev, mid))
                        continue;
                    android_catalog_add_device(&b, dev, cdrom_interface_get_internal_name(c), c);
                }
            }
            break;

        case 19: /* CHAR_COM */
            for (int c = 0;; ++c) {
                const device_t *dev = char_get_device(c);
                if (dev == NULL)
                    break;
                if ((dev->flags & DEVICE_COM) == 0)
                    continue;
                if (!show_all && mid >= 0 && !device_is_valid(dev, mid))
                    continue;
                android_catalog_add_device(&b, dev, dev->internal_name, c);
            }
            break;

        case 20: /* CHAR_LPT */
            for (int c = 0;; ++c) {
                const device_t *dev = char_get_device(c);
                if (dev == NULL)
                    break;
                if ((dev->flags & DEVICE_LPT) == 0)
                    continue;
                if (!show_all && mid >= 0 && !device_is_valid(dev, mid))
                    continue;
                android_catalog_add_device(&b, dev, dev->internal_name, c);
            }
            break;

        case 21: /* ISA_RTC */
            for (int c = 0;; ++c) {
                const device_t *dev = isartc_get_device(c);
                if (dev == NULL)
                    break;
                if (!show_all && mid >= 0 && !device_is_valid(dev, mid))
                    continue;
                android_catalog_add_device(&b, dev, isartc_get_internal_name(c), c);
            }
            break;

        case 22: /* ISA_MEM */
            for (int c = 0;; ++c) {
                const device_t *dev = isamem_get_device(c);
                if (dev == NULL)
                    break;
                if (!show_all && mid >= 0 && !device_is_valid(dev, mid))
                    continue;
                android_catalog_add_device(&b, dev, isamem_get_internal_name(c), c);
            }
            break;

        case 23: /* MCA_MEM */
            for (int c = 0;; ++c) {
                const device_t *dev = mcamem_get_device(c);
                if (dev == NULL)
                    break;
                if (!show_all && mid >= 0 && !device_is_valid(dev, mid))
                    continue;
                android_catalog_add_device(&b, dev, mcamem_get_internal_name(c), c);
            }
            break;

        case 24: /* ISA_ROM */
            for (int c = 0;; ++c) {
                const device_t *dev = isarom_get_device(c);
                if (dev == NULL)
                    break;
                if (!show_all && mid >= 0 && !device_is_valid(dev, mid))
                    continue;
                android_catalog_add_device(&b, dev, isarom_get_internal_name(c), c);
            }
            break;

        default:
            break;
    }

    if (b.data == NULL)
        return (*env)->NewStringUTF(env, "");

    jstring result = (*env)->NewStringUTF(env, b.data);
    free(b.data);
    return result;
}
