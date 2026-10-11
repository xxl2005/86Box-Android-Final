#ifndef EMU_86BOX_ANDROID_BRIDGE_H
#define EMU_86BOX_ANDROID_BRIDGE_H

#ifdef __ANDROID__

#include <stdatomic.h>
#include <stddef.h>

enum {
    ANDROID_CTRL_TOGGLE_PAUSE = 1,
    ANDROID_CTRL_HARD_RESET = 2,
    ANDROID_CTRL_LEFT_CLICK = 3,
    ANDROID_CTRL_RIGHT_CLICK = 4,
    ANDROID_CTRL_MOUSE_SPEED = 5,
    ANDROID_CTRL_TAP_TO_CLICK = 6,
    ANDROID_CTRL_ZOOM = 7,
    ANDROID_CTRL_ZOOM_RESET = 8,
    ANDROID_CTRL_LIFECYCLE_PAUSE = 9,
    ANDROID_CTRL_LIFECYCLE_RESUME = 10,
    ANDROID_CTRL_OPEN_OSD = 11,
    ANDROID_CTRL_MOUSE_MOVE = 12,
    ANDROID_CTRL_TEXT_INPUT_START = 13,
    ANDROID_CTRL_KEYBOARD_LAYOUT = 14,
    ANDROID_CTRL_PERF_PROFILE = 15,
    ANDROID_CTRL_DISPLAY_SCALE_MODE = 16,
    ANDROID_CTRL_HOST_PERFORMANCE_MODE = 17,
    ANDROID_CTRL_CPU_CYCLES_MODE = 23,
    ANDROID_CTRL_CPU_CYCLES_FIXED_PERCENT = 24,
    ANDROID_CTRL_CPU_CYCLES_CUSTOM_KCPS = 25
};

enum {
    ANDROID_MEDIA_FLOPPY = 1,
    ANDROID_MEDIA_CDROM = 2
};

enum {
    ANDROID_USER_EVENT_CONTROL = 0x8601,
    ANDROID_USER_EVENT_MEDIA = 0x8602,
    ANDROID_USER_EVENT_HW_KEY = 0x8603,
    ANDROID_USER_EVENT_HW_MOUSE_MOVE = 0x8604,
    ANDROID_USER_EVENT_HW_MOUSE_BUTTON = 0x8605,
    ANDROID_USER_EVENT_HW_MOUSE_WHEEL = 0x8606,
    ANDROID_USER_EVENT_HW_RELEASE_ALL = 0x8607,
    ANDROID_USER_EVENT_HW_MOUSE_CAPTURE = 0x8608
};

extern _Atomic int android_touch_mouse_speed;
extern _Atomic int android_touch_tap_to_click;
extern _Atomic int android_view_zoom_percent;
extern _Atomic int android_view_pan_x_permille;
extern _Atomic int android_view_pan_y_permille;
extern _Atomic int android_view_scale_mode;
extern _Atomic int android_keyboard_layout;
extern _Atomic int android_performance_mode;
void android_set_emulator_ready(int ready);
int android_is_emulator_ready(void);
void android_set_runtime_active(int active);
int android_is_runtime_active(void);
void android_set_emu_speed_percent(int speed_percent);
void android_set_renderer_info(const char *name);
void android_get_renderer_info(char *out, size_t out_size);
int android_get_performance_mode(void);
int android_get_performance_generation(void);
void android_apply_cpu_thread_performance_tuning(int mode);

void android_install_native_crash_marker(void);
void android_uninstall_native_crash_marker(void);

#endif

#endif
