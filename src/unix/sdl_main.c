#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>
#include <wchar.h>
#ifdef __ANDROID__
#include <math.h>
#endif

#ifdef USE_SDL2_LIB
#include <SDL.h>
#else
#include <SDL3/SDL.h>
#    ifdef __ANDROID__
#        include <SDL3/SDL_main.h>
#    endif
#endif

#ifdef USE_SDL2_LIB
#define SDL_EVENT_KEY_UP SDL_KEYUP
#define SDL_EVENT_KEY_DOWN SDL_KEYDOWN
#define SDL_GetCurrentThreadID SDL_ThreadID
#define SDL_EVENT_QUIT SDL_QUIT
#define SDL_EVENT_RENDER_DEVICE_RESET SDL_RENDER_DEVICE_RESET
#define SDL_EVENT_RENDER_TARGETS_RESET SDL_RENDER_TARGETS_RESET
#define SDL_EVENT_MOUSE_WHEEL SDL_MOUSEWHEEL
#define SDL_EVENT_MOUSE_MOTION SDL_MOUSEMOTION
#define SDL_EVENT_FINGER_DOWN SDL_FINGERDOWN
#define SDL_EVENT_FINGER_UP SDL_FINGERUP
#define SDL_EVENT_FINGER_MOTION SDL_FINGERMOTION
#define SDL_EVENT_MOUSE_BUTTON_DOWN SDL_MOUSEBUTTONDOWN
#define SDL_EVENT_MOUSE_BUTTON_UP SDL_MOUSEBUTTONUP
#define SDL_Mutex SDL_mutex
#endif

#ifdef _WIN32
#    include <windows.h>
#endif

#include <86box/86box.h>
#include <86box/keyboard.h>
#include <86box/mouse.h>
#include <86box/plat.h>
#include <86box/thread.h>
#include <86box/timer.h>
#include <86box/nvr.h>
#include <86box/version.h>
#include <86box/video.h>
#include <86box/ui.h>
#include <86box/gdbstub.h>

#include "sdl_monitor.h"
#include "sdl_render.h"
#include "sdl_osd.h"
#ifdef __ANDROID__
#include "android_bridge.h"
#include "android_touch_latency.h"
#include "android_cpu_cycles.h"
#endif

extern SDL_Window         *sdl_win;

int             rctrl_is_lalt;
int             update_icons;
int             kbd_req_capture;
int             hide_status_bar;
int             hide_tool_bar;
bool            fast_forward = false;
int             fixed_size_x = 640;
int             fixed_size_y = 480;
extern int      title_set;
extern char     sdl_win_title[512];
SDL_Mutex      *blitmtx;
#ifdef USE_SDL2_LIB
SDL_threadID    eventthread;
#else
SDL_ThreadID    eventthread;
#endif
int      exit_event         = 0;
int      fullscreen_pending = 0;

// Two keys to be pressed together to open the OSD, variables to make them configurable in future
static uint16_t osd_open_first_key = SDL_SCANCODE_RCTRL;
static uint16_t osd_open_second_key = SDL_SCANCODE_F11;

static const uint16_t sdl_to_xt[0x200] = {
    [SDL_SCANCODE_ESCAPE]       = 0x01,
    [SDL_SCANCODE_1]            = 0x02,
    [SDL_SCANCODE_2]            = 0x03,
    [SDL_SCANCODE_3]            = 0x04,
    [SDL_SCANCODE_4]            = 0x05,
    [SDL_SCANCODE_5]            = 0x06,
    [SDL_SCANCODE_6]            = 0x07,
    [SDL_SCANCODE_7]            = 0x08,
    [SDL_SCANCODE_8]            = 0x09,
    [SDL_SCANCODE_9]            = 0x0A,
    [SDL_SCANCODE_0]            = 0x0B,
    [SDL_SCANCODE_MINUS]        = 0x0C,
    [SDL_SCANCODE_EQUALS]       = 0x0D,
    [SDL_SCANCODE_BACKSPACE]    = 0x0E,
    [SDL_SCANCODE_TAB]          = 0x0F,
    [SDL_SCANCODE_Q]            = 0x10,
    [SDL_SCANCODE_W]            = 0x11,
    [SDL_SCANCODE_E]            = 0x12,
    [SDL_SCANCODE_R]            = 0x13,
    [SDL_SCANCODE_T]            = 0x14,
    [SDL_SCANCODE_Y]            = 0x15,
    [SDL_SCANCODE_U]            = 0x16,
    [SDL_SCANCODE_I]            = 0x17,
    [SDL_SCANCODE_O]            = 0x18,
    [SDL_SCANCODE_P]            = 0x19,
    [SDL_SCANCODE_LEFTBRACKET]  = 0x1A,
    [SDL_SCANCODE_RIGHTBRACKET] = 0x1B,
    [SDL_SCANCODE_RETURN]       = 0x1C,
    [SDL_SCANCODE_LCTRL]        = 0x1D,
    [SDL_SCANCODE_A]            = 0x1E,
    [SDL_SCANCODE_S]            = 0x1F,
    [SDL_SCANCODE_D]            = 0x20,
    [SDL_SCANCODE_F]            = 0x21,
    [SDL_SCANCODE_G]            = 0x22,
    [SDL_SCANCODE_H]            = 0x23,
    [SDL_SCANCODE_J]            = 0x24,
    [SDL_SCANCODE_K]            = 0x25,
    [SDL_SCANCODE_L]            = 0x26,
    [SDL_SCANCODE_SEMICOLON]    = 0x27,
    [SDL_SCANCODE_APOSTROPHE]   = 0x28,
    [SDL_SCANCODE_GRAVE]        = 0x29,
    [SDL_SCANCODE_LSHIFT]       = 0x2A,
    [SDL_SCANCODE_BACKSLASH]    = 0x2B,
    [SDL_SCANCODE_Z]            = 0x2C,
    [SDL_SCANCODE_X]            = 0x2D,
    [SDL_SCANCODE_C]            = 0x2E,
    [SDL_SCANCODE_V]            = 0x2F,
    [SDL_SCANCODE_B]            = 0x30,
    [SDL_SCANCODE_N]            = 0x31,
    [SDL_SCANCODE_M]            = 0x32,
    [SDL_SCANCODE_COMMA]        = 0x33,
    [SDL_SCANCODE_PERIOD]       = 0x34,
    [SDL_SCANCODE_SLASH]        = 0x35,
    [SDL_SCANCODE_RSHIFT]       = 0x36,
    [SDL_SCANCODE_KP_MULTIPLY]  = 0x37,
    [SDL_SCANCODE_LALT]         = 0x38,
    [SDL_SCANCODE_SPACE]        = 0x39,
    [SDL_SCANCODE_CAPSLOCK]     = 0x3A,
    [SDL_SCANCODE_F1]           = 0x3B,
    [SDL_SCANCODE_F2]           = 0x3C,
    [SDL_SCANCODE_F3]           = 0x3D,
    [SDL_SCANCODE_F4]           = 0x3E,
    [SDL_SCANCODE_F5]           = 0x3F,
    [SDL_SCANCODE_F6]           = 0x40,
    [SDL_SCANCODE_F7]           = 0x41,
    [SDL_SCANCODE_F8]           = 0x42,
    [SDL_SCANCODE_F9]           = 0x43,
    [SDL_SCANCODE_F10]          = 0x44,
    [SDL_SCANCODE_NUMLOCKCLEAR] = 0x45,
    [SDL_SCANCODE_SCROLLLOCK]   = 0x46,
    [SDL_SCANCODE_HOME]         = 0x147,
    [SDL_SCANCODE_UP]           = 0x148,
    [SDL_SCANCODE_PAGEUP]       = 0x149,
    [SDL_SCANCODE_KP_MINUS]     = 0x4A,
    [SDL_SCANCODE_LEFT]         = 0x14B,
    [SDL_SCANCODE_KP_5]         = 0x4C,
    [SDL_SCANCODE_RIGHT]        = 0x14D,
    [SDL_SCANCODE_KP_PLUS]      = 0x4E,
    [SDL_SCANCODE_END]          = 0x14F,
    [SDL_SCANCODE_DOWN]         = 0x150,
    [SDL_SCANCODE_PAGEDOWN]     = 0x151,
    [SDL_SCANCODE_INSERT]       = 0x152,
    [SDL_SCANCODE_DELETE]       = 0x153,
    [SDL_SCANCODE_F11]          = 0x57,
    [SDL_SCANCODE_F12]          = 0x58,

    [SDL_SCANCODE_KP_ENTER]  = 0x11c,
    [SDL_SCANCODE_RCTRL]     = 0x11d,
    [SDL_SCANCODE_KP_DIVIDE] = 0x135,
    [SDL_SCANCODE_RALT]      = 0x138,
    [SDL_SCANCODE_KP_9]      = 0x49,
    [SDL_SCANCODE_KP_8]      = 0x48,
    [SDL_SCANCODE_KP_7]      = 0x47,
    [SDL_SCANCODE_KP_6]      = 0x4D,
    [SDL_SCANCODE_KP_4]      = 0x4B,
    [SDL_SCANCODE_KP_3]      = 0x51,
    [SDL_SCANCODE_KP_2]      = 0x50,
    [SDL_SCANCODE_KP_1]      = 0x4F,
    [SDL_SCANCODE_KP_0]      = 0x52,
    [SDL_SCANCODE_KP_PERIOD] = 0x53,

    [SDL_SCANCODE_LGUI]        = 0x15B,
    [SDL_SCANCODE_RGUI]        = 0x15C,
    [SDL_SCANCODE_APPLICATION] = 0x15D,
    [SDL_SCANCODE_PRINTSCREEN] = 0x137
};

typedef struct sdl_blit_params {
    int x;
    int y;
    int w;
    int h;
} sdl_blit_params;

sdl_blit_params params  = { 0, 0, 0, 0 };
int             blitreq = 0;

volatile int cpu_thread_run = 1;

void
main_thread(UNUSED(void *param))
{
    uint32_t old_time;
    uint32_t new_time;
    int      drawits;
    int      frames;

    
    is_cpu_thread = 1;

#ifdef USE_SDL2_LIB
    SDL_SetThreadPriority(SDL_THREAD_PRIORITY_HIGH);
#else
    SDL_SetCurrentThreadPriority(SDL_THREAD_PRIORITY_HIGH);
#endif
    framecountx = 0;
#ifdef __ANDROID__
    int android_perf_applied = android_get_performance_mode();
    int android_perf_generation_applied = android_get_performance_generation();
    int android_perf_poll = 0;
    uint32_t cycles_last_log_ms = SDL_GetTicks();
    android_apply_cpu_thread_performance_tuning(android_perf_applied);
#endif
    // title_update = 1;
    old_time = SDL_GetTicks();
    drawits = frames = 0;
    while (!is_quit && cpu_thread_run)
    {
#ifdef __ANDROID__
        if (++android_perf_poll >= 250) {
            int next_mode = android_get_performance_mode();
            int next_generation = android_get_performance_generation();
            android_perf_poll = 0;
            if (next_mode != android_perf_applied || next_generation != android_perf_generation_applied) {
                android_perf_applied = next_mode;
                android_perf_generation_applied = next_generation;
                android_apply_cpu_thread_performance_tuning(android_perf_applied);
            }
        }
#endif
#ifdef __ANDROID__
        if ((uint32_t)(SDL_GetTicks()-cycles_last_log_ms)>=15000u) {
            char cycles_info[512];
            android_cpu_cycles_get_status(cycles_info,sizeof(cycles_info));
            pclog("[ANDROID][CPUCYCLES] %s\n",cycles_info);
            cycles_last_log_ms=SDL_GetTicks();
        }
#endif
        /* See if it is time to run a frame of code. */
        new_time = SDL_GetTicks();

#ifdef USE_GDBSTUB
        if (gdbstub_next_asap && (drawits <= 0))
            drawits = 10;
        else
            drawits += (new_time - old_time);
#else
        drawits += (new_time - old_time);
#endif

        old_time = new_time;
        if ((drawits > 0 || fast_forward) && !dopause) {
            /* Yes, so do one frame now. */
            drawits -= force_10ms ? 10 : 1;
            if (drawits > 50 || fast_forward)
                drawits = 0;

            /* Run a block of code. */
            pc_run();

            /* Every 200 frames we save the machine status. */
            if (++frames >= (force_10ms ? 200 : 2000) && nvr_dosave) {
                nvr_save();
                nvr_dosave = 0;
                frames     = 0;
            }
        }
        else /* Just so we dont overload the host OS. */
            SDL_Delay(1);

        /* If needed, handle a screen resize. */
        if (atomic_load(&doresize_monitors[0]) && !video_fullscreen && !is_quit) {

            if (vid_resize & 2)
                plat_resize(fixed_size_x, fixed_size_y, 0);
            else
                plat_resize(scrnsz_x, scrnsz_y, 0);

            atomic_store(&doresize_monitors[0], 1);
        }
    }

    is_quit = 1;
}

thread_t *thMain = NULL;

void
do_start(void)
{
    /* We have not stopped yet. */
    is_quit = 0;

    /* Initialize the high-precision timer. */
    timer_freq = SDL_GetPerformanceFrequency();

    /* Start the emulator, really. */
    thMain = thread_create(main_thread, NULL);
}

void
do_stop(void)
{
    if (SDL_GetCurrentThreadID() != eventthread) {
        exit_event = 1;
        return;
    }
    if (blitreq) {
        blitreq = 0;
        video_blit_complete();
    }

#ifdef USE_SDL2_LIB
    while (SDL_TryLockMutex(blitmtx) == SDL_MUTEX_TIMEDOUT) {
#else
    while (!SDL_TryLockMutex(blitmtx)) {
#endif
        if (blitreq) {
            blitreq = 0;
            video_blit_complete();
        }
    }
    startblit();

    is_quit = 1;
    sdl_close();

    pc_close(thMain);

    thMain = NULL;
}


extern void sdl_blit(int x, int y, int w, int h);

typedef struct mouseinputdata {
    int deltax;
    int deltay;
    int deltaz;
    int mousebuttons;
} mouseinputdata;

SDL_Mutex *mousemutex;
int        real_sdl_w;
int        real_sdl_h;

#ifdef __ANDROID__
typedef struct android_touch_finger_t {
    SDL_FingerID id;
    float x;
    float y;
    float start_x;
    float start_y;
    int used;
} android_touch_finger_t;

static android_touch_finger_t android_fingers[4];
static int      android_touch_max_fingers = 0;
static int      android_touch_moved = 0;
static uint64_t android_touch_started_ms = 0;
static float    android_pinch_start_distance = 0.0f;
static int      android_pinch_start_zoom = 100;
static float    android_pinch_start_cx = 0.0f;
static float    android_pinch_start_cy = 0.0f;
static int      android_pinch_start_pan_x = 0;
static int      android_pinch_start_pan_y = 0;
static int      android_mouse_release_mask = 0;
static uint64_t android_mouse_release_deadline = 0;
static int      android_touch_long_press_fired = 0;
static int      android_touch_left_drag_active = 0;
static int      android_touch_drag_candidate = 0;
static int      android_touch_drag_trace_budget = 24;
static float    android_touch_path_distance = 0.0f;
static uint64_t android_last_tap_up_ms = 0;
static float    android_last_tap_x = 0.0f;
static float    android_last_tap_y = 0.0f;
static int      android_lifecycle_paused_by_us = 0;
static int      android_hw_mouse_trace_budget = 24;

#define ANDROID_TOUCH_TAP_MAX_MS       500U
#define ANDROID_TOUCH_DOUBLE_GAP_MS    500U
#define ANDROID_TOUCH_LONG_PRESS_MS   2000U
#define ANDROID_TOUCH_HOLD_DRAG_MS      350U
#define ANDROID_TOUCH_DOUBLE_DIST       0.060f
#define ANDROID_TOUCH_DRAG_DIST         0.008f
#define ANDROID_TOUCH_HOLD_DRAG_DIST    0.0035f

static int
android_touch_count(void)
{
    int count = 0;
    for (int i = 0; i < 4; ++i)
        if (android_fingers[i].used)
            ++count;
    return count;
}

static int
android_touch_find(SDL_FingerID id)
{
    for (int i = 0; i < 4; ++i)
        if (android_fingers[i].used && android_fingers[i].id == id)
            return i;
    return -1;
}

static int
android_touch_add(SDL_FingerID id, float x, float y)
{
    int existing = android_touch_find(id);
    if (existing >= 0)
        return existing;
    for (int i = 0; i < 4; ++i) {
        if (!android_fingers[i].used) {
            android_fingers[i].used = 1;
            android_fingers[i].id = id;
            android_fingers[i].x = android_fingers[i].start_x = x;
            android_fingers[i].y = android_fingers[i].start_y = y;
            return i;
        }
    }
    return -1;
}

static void
android_touch_remove(SDL_FingerID id)
{
    int i = android_touch_find(id);
    if (i >= 0)
        android_fingers[i].used = 0;
}

static float
android_touch_distance(void)
{
    const android_touch_finger_t *a = NULL;
    const android_touch_finger_t *b = NULL;
    for (int i = 0; i < 4; ++i) {
        if (!android_fingers[i].used)
            continue;
        if (!a)
            a = &android_fingers[i];
        else {
            b = &android_fingers[i];
            break;
        }
    }
    if (!a || !b)
        return 0.0f;
    const float dx = a->x - b->x;
    const float dy = a->y - b->y;
    return sqrtf(dx * dx + dy * dy);
}

static int
android_touch_centroid(float *cx, float *cy)
{
    float sx = 0.0f, sy = 0.0f;
    int count = 0;
    for (int i = 0; i < 4; ++i) {
        if (!android_fingers[i].used)
            continue;
        sx += android_fingers[i].x;
        sy += android_fingers[i].y;
        ++count;
    }
    if (count == 0)
        return 0;
    *cx = sx / (float) count;
    *cy = sy / (float) count;
    return count;
}

static void
android_touch_set_left_drag(int down)
{
    if (!mousemutex)
        return;

    SDL_LockMutex(mousemutex);
    int buttons = mouse_get_buttons_ex();
    if (down) {
        /* A click pulse may still have a scheduled release.  Once a real
           drag starts, ownership of the left button belongs to the drag. */
        android_mouse_release_mask &= ~1;
        if (!android_mouse_release_mask)
            android_mouse_release_deadline = 0;
        mouse_set_buttons_ex(buttons | 1);
    } else {
        mouse_set_buttons_ex(buttons & ~1);
    }
    SDL_UnlockMutex(mousemutex);

    android_touch_left_drag_active = down ? 1 : 0;
    if (android_touch_drag_trace_budget > 0) {
        pclog("[ANDROID][TOUCH] left-drag %s\n", down ? "START" : "END");
        --android_touch_drag_trace_budget;
    }
}

static void
android_mouse_click_pulse(int mask)
{
    if (!mousemutex)
        return;
    SDL_LockMutex(mousemutex);
    mouse_set_buttons_ex(mouse_get_buttons_ex() | mask);
    SDL_UnlockMutex(mousemutex);
    android_mouse_release_mask |= mask;
    android_mouse_release_deadline = SDL_GetTicks() + 55;
}

static void
android_release_due_buttons(void)
{
    if (!android_mouse_release_mask || SDL_GetTicks() < android_mouse_release_deadline || !mousemutex)
        return;
    SDL_LockMutex(mousemutex);
    mouse_set_buttons_ex(mouse_get_buttons_ex() & ~android_mouse_release_mask);
    SDL_UnlockMutex(mousemutex);
    android_mouse_release_mask = 0;
    android_mouse_release_deadline = 0;
}

static void
android_touch_down(const SDL_TouchFingerEvent *finger)
{
    const int before = android_touch_count();
    android_touch_add(finger->fingerID, finger->x, finger->y);
    const int count = android_touch_count();
    if (before == 0 && count == 1) {
        android_touch_started_ms = SDL_GetTicks();
        android_touch_max_fingers = 1;
        android_touch_moved = 0;
        android_touch_long_press_fired = 0;
        android_touch_left_drag_active = 0;
        android_touch_drag_candidate = 1;
        android_touch_path_distance = 0.0f;
        android_pinch_start_distance = 0.0f;
    } else if (count > android_touch_max_fingers) {
        android_touch_max_fingers = count;
    }
    if (count == 2) {
        if (android_touch_left_drag_active)
            android_touch_set_left_drag(0);
        android_touch_drag_candidate = 0;
        android_pinch_start_distance = android_touch_distance();
        android_pinch_start_zoom = android_view_zoom_percent;
        android_touch_centroid(&android_pinch_start_cx, &android_pinch_start_cy);
        android_pinch_start_pan_x = android_view_pan_x_permille;
        android_pinch_start_pan_y = android_view_pan_y_permille;
    }
}

static void
android_touch_motion(const SDL_TouchFingerEvent *finger)
{
    int idx = android_touch_find(finger->fingerID);
    if (idx < 0)
        idx = android_touch_add(finger->fingerID, finger->x, finger->y);
    if (idx >= 0) {
        /* Accumulate actual finger travel.  A normal immediate swipe only
           moves the guest pointer.  A finger that first rests briefly and
           then moves becomes a real held-left-button drag, so desktop icons,
           title bars and resize borders can be manipulated naturally. */
        const float prev_x = android_fingers[idx].x;
        const float prev_y = android_fingers[idx].y;
        android_touch_path_distance += fabsf(finger->x - prev_x) +
                                       fabsf(finger->y - prev_y);
        android_fingers[idx].x = finger->x;
        android_fingers[idx].y = finger->y;

        const float displacement =
                fabsf(finger->x - android_fingers[idx].start_x) +
                fabsf(finger->y - android_fingers[idx].start_y);
        const uint64_t elapsed = SDL_GetTicks() - android_touch_started_ms;

        /* If a real swipe starts before the hold window, keep it a pointer
           move for the complete gesture; never suddenly convert it into a
           drag after 350 ms. */
        if (android_touch_drag_candidate && elapsed < ANDROID_TOUCH_HOLD_DRAG_MS &&
            (android_touch_path_distance >= ANDROID_TOUCH_DRAG_DIST ||
             displacement >= ANDROID_TOUCH_DRAG_DIST))
            android_touch_drag_candidate = 0;

        if (!android_touch_left_drag_active && android_touch_drag_candidate &&
            !android_touch_long_press_fired && android_touch_count() == 1 &&
            elapsed >= ANDROID_TOUCH_HOLD_DRAG_MS &&
            (android_touch_path_distance >= ANDROID_TOUCH_HOLD_DRAG_DIST ||
             displacement >= ANDROID_TOUCH_HOLD_DRAG_DIST)) {
            android_touch_set_left_drag(1);
            android_touch_moved = 1;
            android_last_tap_up_ms = 0;
        }

        if (android_touch_path_distance >= ANDROID_TOUCH_DRAG_DIST ||
            displacement >= ANDROID_TOUCH_DRAG_DIST || android_touch_left_drag_active)
            android_touch_moved = 1;
    }

    const int count = android_touch_count();
    if (count >= 2 && android_pinch_start_distance > 0.01f) {
        const float distance = android_touch_distance();
        float cx = android_pinch_start_cx, cy = android_pinch_start_cy;
        android_touch_centroid(&cx, &cy);
        if (distance > 0.01f) {
            int zoom = (int) ((float) android_pinch_start_zoom * distance / android_pinch_start_distance + 0.5f);
            if (zoom < 50) zoom = 50;
            if (zoom > 300) zoom = 300;
            if (abs(zoom - android_pinch_start_zoom) >= 3)
                android_touch_moved = 1;
            android_view_zoom_percent = zoom;
            if (zoom == 100) {
                android_view_pan_x_permille = 0;
                android_view_pan_y_permille = 0;
            }
        }
        if (android_view_zoom_percent > 100) {
            int pan_x = android_pinch_start_pan_x + (int) ((cx - android_pinch_start_cx) * 1000.0f);
            int pan_y = android_pinch_start_pan_y + (int) ((cy - android_pinch_start_cy) * 1000.0f);
            if (pan_x < -2000) pan_x = -2000;
            if (pan_x >  2000) pan_x =  2000;
            if (pan_y < -2000) pan_y = -2000;
            if (pan_y >  2000) pan_y =  2000;
            if (abs(pan_x - android_pinch_start_pan_x) > 8 || abs(pan_y - android_pinch_start_pan_y) > 8)
                android_touch_moved = 1;
            android_view_pan_x_permille = pan_x;
            android_view_pan_y_permille = pan_y;
        }
        return;
    }

    if (count == 1 && (mouse_capture || video_fullscreen)) {
        int w = 0, h = 0;
        SDL_GetWindowSize(sdl_win, &w, &h);
        const float speed = (float) android_touch_mouse_speed / 100.0f;
        /* Preserve subpixel deltas for the proven low-latency touch path. */
        if (mousemutex) {
            SDL_LockMutex(mousemutex);
            mouse_scalef((double) finger->dx * (double) w * (double) speed,
                         (double) finger->dy * (double) h * (double) speed);
            SDL_UnlockMutex(mousemutex);
        }
    }
}

static void
android_touch_up(const SDL_TouchFingerEvent *finger)
{
    const int before = android_touch_count();
    const uint64_t now = SDL_GetTicks();
    const uint64_t elapsed = now - android_touch_started_ms;

    const int ended_left_drag = android_touch_left_drag_active;
    if (ended_left_drag) {
        android_touch_set_left_drag(0);
        android_touch_moved = 1;
        android_last_tap_up_ms = 0;
    }

    if (!ended_left_drag && android_touch_tap_to_click && !android_touch_moved && !android_touch_long_press_fired) {
        if (before == 1 && android_touch_max_fingers == 1 && elapsed <= ANDROID_TOUCH_TAP_MAX_MS) {
            /*
             * One short tap is one normal left click. Two short taps therefore
             * produce two properly separated click pulses and are recognised by
             * Windows/guest OSes as a normal double click. Keep a small spatial
             * history so a second tap far away starts a fresh sequence.
             */
            const float dx = finger->x - android_last_tap_x;
            const float dy = finger->y - android_last_tap_y;
            const int nearby = (dx * dx + dy * dy) <=
                    (ANDROID_TOUCH_DOUBLE_DIST * ANDROID_TOUCH_DOUBLE_DIST);
            const int in_time = android_last_tap_up_ms &&
                    (now - android_last_tap_up_ms) <= ANDROID_TOUCH_DOUBLE_GAP_MS;

            android_mouse_click_pulse(1);
            if (in_time && nearby) {
                /* Mark the pair consumed; a third tap starts a new sequence. */
                android_last_tap_up_ms = 0;
            } else {
                android_last_tap_up_ms = now;
                android_last_tap_x = finger->x;
                android_last_tap_y = finger->y;
            }
        } else if (before == 2 && android_touch_max_fingers == 2 &&
                   elapsed <= ANDROID_TOUCH_TAP_MAX_MS) {
            /* Preserve the existing two-finger right-click shortcut. */
            android_mouse_click_pulse(2);
            android_last_tap_up_ms = 0;
        }
    }

    android_touch_remove(finger->fingerID);
    if (android_touch_count() == 0) {
        android_touch_max_fingers = 0;
        android_touch_moved = 0;
        android_touch_long_press_fired = 0;
        android_touch_left_drag_active = 0;
        android_touch_drag_candidate = 0;
        android_touch_path_distance = 0.0f;
        android_pinch_start_distance = 0.0f;
    }
}

static void
android_touch_long_press_tick(void)
{
    if (!android_touch_tap_to_click || android_touch_long_press_fired ||
        android_touch_left_drag_active || android_touch_moved || android_touch_count() != 1 ||
        android_touch_max_fingers != 1)
        return;

    const uint64_t now = SDL_GetTicks();
    if (now - android_touch_started_ms < ANDROID_TOUCH_LONG_PRESS_MS)
        return;

    /* Exactly one stationary finger for two seconds = guest right click. */
    android_touch_drag_candidate = 0;
    android_mouse_click_pulse(2);
    android_touch_long_press_fired = 1;
    android_last_tap_up_ms = 0;
}

static void
android_touch_cancel(const SDL_TouchFingerEvent *finger)
{
    if (android_touch_left_drag_active)
        android_touch_set_left_drag(0);
    android_touch_drag_candidate = 0;
    android_touch_remove(finger->fingerID);
    if (android_touch_count() == 0) {
        android_touch_max_fingers = 0;
        android_touch_moved = 0;
        android_touch_long_press_fired = 0;
        android_touch_left_drag_active = 0;
        android_touch_drag_candidate = 0;
        android_touch_path_distance = 0.0f;
        android_pinch_start_distance = 0.0f;
    }
}

static void
android_send_xt(uint16_t xt, int shift, int altgr)
{
    if (!xt)
        return;
    if (shift)
        keyboard_input(1, 0x2a);
    if (altgr)
        keyboard_input(1, 0x138);
    keyboard_input(1, xt);
    keyboard_input(0, xt);
    if (altgr)
        keyboard_input(0, 0x138);
    if (shift)
        keyboard_input(0, 0x2a);
}

static uint32_t
android_utf8_next(const unsigned char **pp)
{
    const unsigned char *p = *pp;
    uint32_t cp;
    if (*p < 0x80) {
        *pp = p + 1;
        return *p;
    }
    if ((*p & 0xe0) == 0xc0 && p[1] && (p[1] & 0xc0) == 0x80) {
        cp = ((uint32_t)(p[0] & 0x1f) << 6) | (uint32_t)(p[1] & 0x3f);
        *pp = p + 2;
        return cp >= 0x80 ? cp : 0xfffd;
    }
    if ((*p & 0xf0) == 0xe0 && p[1] && p[2] && (p[1] & 0xc0) == 0x80 && (p[2] & 0xc0) == 0x80) {
        cp = ((uint32_t)(p[0] & 0x0f) << 12) | ((uint32_t)(p[1] & 0x3f) << 6) | (uint32_t)(p[2] & 0x3f);
        *pp = p + 3;
        return cp >= 0x800 ? cp : 0xfffd;
    }
    if ((*p & 0xf8) == 0xf0 && p[1] && p[2] && p[3] && (p[1] & 0xc0) == 0x80 && (p[2] & 0xc0) == 0x80 && (p[3] & 0xc0) == 0x80) {
        cp = ((uint32_t)(p[0] & 0x07) << 18) | ((uint32_t)(p[1] & 0x3f) << 12) |
             ((uint32_t)(p[2] & 0x3f) << 6) | (uint32_t)(p[3] & 0x3f);
        *pp = p + 4;
        return (cp >= 0x10000 && cp <= 0x10ffff) ? cp : 0xfffd;
    }
    *pp = p + 1;
    return 0xfffd;
}

static int
android_us_codepoint(uint32_t cp, uint16_t *xt, int *shift, int *altgr)
{
    *shift = 0;
    *altgr = 0;
    if (cp >= 'a' && cp <= 'z') { *xt = 0; }
    else if (cp >= 'A' && cp <= 'Z') { *xt = 0; *shift = 1; }
    if ((cp >= 'a' && cp <= 'z') || (cp >= 'A' && cp <= 'Z')) {
        static const uint16_t letters[26] = {
            0x1e,0x30,0x2e,0x20,0x12,0x21,0x22,0x23,0x17,0x24,0x25,0x26,0x32,
            0x31,0x18,0x19,0x10,0x13,0x1f,0x14,0x16,0x2f,0x11,0x2d,0x15,0x2c
        };
        uint32_t c = (cp >= 'A' && cp <= 'Z') ? cp - 'A' : cp - 'a';
        *xt = letters[c];
        return 1;
    }
    if (cp >= '1' && cp <= '9') { *xt = (uint16_t)(0x02 + (cp - '1')); return 1; }
    if (cp == '0') { *xt = 0x0b; return 1; }
    switch (cp) {
        case ' ': *xt=0x39; return 1; case '\t': *xt=0x0f; return 1;
        case '\n': case '\r': *xt=0x1c; return 1;
        case '-': *xt=0x0c; return 1; case '_': *xt=0x0c; *shift=1; return 1;
        case '=': *xt=0x0d; return 1; case '+': *xt=0x0d; *shift=1; return 1;
        case '[': *xt=0x1a; return 1; case '{': *xt=0x1a; *shift=1; return 1;
        case ']': *xt=0x1b; return 1; case '}': *xt=0x1b; *shift=1; return 1;
        case '\\': *xt=0x2b; return 1; case '|': *xt=0x2b; *shift=1; return 1;
        case ';': *xt=0x27; return 1; case ':': *xt=0x27; *shift=1; return 1;
        case '\'': *xt=0x28; return 1; case '"': *xt=0x28; *shift=1; return 1;
        case '`': *xt=0x29; return 1; case '~': *xt=0x29; *shift=1; return 1;
        case ',': *xt=0x33; return 1; case '<': *xt=0x33; *shift=1; return 1;
        case '.': *xt=0x34; return 1; case '>': *xt=0x34; *shift=1; return 1;
        case '/': *xt=0x35; return 1; case '?': *xt=0x35; *shift=1; return 1;
        case '!': *xt=0x02; *shift=1; return 1; case '@': *xt=0x03; *shift=1; return 1;
        case '#': *xt=0x04; *shift=1; return 1; case '$': *xt=0x05; *shift=1; return 1;
        case '%': *xt=0x06; *shift=1; return 1; case '^': *xt=0x07; *shift=1; return 1;
        case '&': *xt=0x08; *shift=1; return 1; case '*': *xt=0x09; *shift=1; return 1;
        case '(': *xt=0x0a; *shift=1; return 1; case ')': *xt=0x0b; *shift=1; return 1;
        default: return 0;
    }
}

static int
android_de_codepoint(uint32_t cp, uint16_t *xt, int *shift, int *altgr)
{
    *shift = 0;
    *altgr = 0;
    /* Letter keys are QWERTZ at the guest side. */
    if ((cp >= 'a' && cp <= 'z') || (cp >= 'A' && cp <= 'Z')) {
        int upper = (cp >= 'A' && cp <= 'Z');
        uint32_t lower = upper ? cp + ('a' - 'A') : cp;
        if (lower == 'y') lower = 'z';
        else if (lower == 'z') lower = 'y';
        if (!android_us_codepoint(lower, xt, shift, altgr)) return 0;
        *shift = upper;
        return 1;
    }
    if (cp >= '1' && cp <= '9') { *xt = (uint16_t)(0x02 + (cp - '1')); return 1; }
    if (cp == '0') { *xt = 0x0b; return 1; }
    switch (cp) {
        case 0x00e4: *xt=0x28; return 1; case 0x00c4: *xt=0x28; *shift=1; return 1; /* ä Ä */
        case 0x00f6: *xt=0x27; return 1; case 0x00d6: *xt=0x27; *shift=1; return 1; /* ö Ö */
        case 0x00fc: *xt=0x1a; return 1; case 0x00dc: *xt=0x1a; *shift=1; return 1; /* ü Ü */
        case 0x00df: *xt=0x0c; return 1; /* ß */
        case 0x00a7: *xt=0x04; *shift=1; return 1; /* § */
        case 0x00b0: *xt=0x29; *shift=1; return 1; /* ° */
        case 0x00b4: *xt=0x0d; return 1; /* ´ dead-key position */
        case '`': *xt=0x0d; *shift=1; return 1;
        case '^': *xt=0x29; return 1;
        case 0x20ac: *xt=0x12; *altgr=1; return 1; /* € = AltGr+E */
        case 0x00b5: *xt=0x32; *altgr=1; return 1; /* µ */
        case ' ': *xt=0x39; return 1; case '\t': *xt=0x0f; return 1;
        case '\n': case '\r': *xt=0x1c; return 1;
        case '!': *xt=0x02; *shift=1; return 1;
        case '"': *xt=0x03; *shift=1; return 1;
        case '$': *xt=0x05; *shift=1; return 1;
        case '%': *xt=0x06; *shift=1; return 1;
        case '&': *xt=0x07; *shift=1; return 1;
        case '/': *xt=0x08; *shift=1; return 1;
        case '(': *xt=0x09; *shift=1; return 1;
        case ')': *xt=0x0a; *shift=1; return 1;
        case '=': *xt=0x0b; *shift=1; return 1;
        case '?': *xt=0x0c; *shift=1; return 1;
        case '+': *xt=0x1b; return 1; case '*': *xt=0x1b; *shift=1; return 1;
        case '~': *xt=0x1b; *altgr=1; return 1;
        case '#': *xt=0x2b; return 1; case '\'': *xt=0x2b; *shift=1; return 1;
        case ',': *xt=0x33; return 1; case ';': *xt=0x33; *shift=1; return 1;
        case '.': *xt=0x34; return 1; case ':': *xt=0x34; *shift=1; return 1;
        case '-': *xt=0x35; return 1; case '_': *xt=0x35; *shift=1; return 1;
        case '<': *xt=0x56; return 1; case '>': *xt=0x56; *shift=1; return 1;
        case '|': *xt=0x56; *altgr=1; return 1;
        case '@': *xt=0x10; *altgr=1; return 1; /* AltGr+Q */
        case '{': *xt=0x08; *altgr=1; return 1;
        case '[': *xt=0x09; *altgr=1; return 1;
        case ']': *xt=0x0a; *altgr=1; return 1;
        case '}': *xt=0x0b; *altgr=1; return 1;
        case '\\': *xt=0x0c; *altgr=1; return 1;
        default: return 0;
    }
}

static void
android_type_text(const char *text)
{
    if (!text)
        return;
    const unsigned char *p = (const unsigned char *) text;
    while (*p) {
        uint32_t cp = android_utf8_next(&p);
        uint16_t xt = 0;
        int shift = 0, altgr = 0;
        int ok = android_keyboard_layout ?
            android_de_codepoint(cp, &xt, &shift, &altgr) :
            android_us_codepoint(cp, &xt, &shift, &altgr);
        if (ok)
            android_send_xt(xt, shift, altgr);
    }
}

static void
android_handle_user_event(SDL_Event *event, int *flag_osd_open)
{
    if (event->user.code == ANDROID_USER_EVENT_CONTROL) {
        const int action = (int) (intptr_t) event->user.data1;
        const int value = (int) (intptr_t) event->user.data2;
        switch (action) {
            case ANDROID_CTRL_TOGGLE_PAUSE:
                plat_pause(dopause ^ 1);
                break;
            case ANDROID_CTRL_HARD_RESET: {
                const int was_paused = dopause;
                plat_pause(1);
                pc_reset_hard();
                plat_pause(was_paused);
                break;
            }
            case ANDROID_CTRL_LEFT_CLICK:
                android_mouse_click_pulse(1);
                break;
            case ANDROID_CTRL_RIGHT_CLICK:
                android_mouse_click_pulse(2);
                break;
            case ANDROID_CTRL_MOUSE_SPEED:
                android_touch_mouse_speed = value < 25 ? 25 : (value > 300 ? 300 : value);
                break;
            case ANDROID_CTRL_TAP_TO_CLICK:
                android_touch_tap_to_click = value ? 1 : 0;
                break;
            case ANDROID_CTRL_ZOOM:
                android_view_zoom_percent = value < 50 ? 50 : (value > 300 ? 300 : value);
                if (android_view_zoom_percent <= 100) {
                    android_view_pan_x_permille = 0;
                    android_view_pan_y_permille = 0;
                }
                break;
            case ANDROID_CTRL_DISPLAY_SCALE_MODE:
                android_view_scale_mode = value < FULLSCR_SCALE_FULL ? FULLSCR_SCALE_FULL :
                        (value > FULLSCR_SCALE_INT43 ? FULLSCR_SCALE_INT43 : value);
                android_view_pan_x_permille = 0;
                android_view_pan_y_permille = 0;
                break;
            case ANDROID_CTRL_ZOOM_RESET:
                android_view_zoom_percent = 100;
                android_view_pan_x_permille = 0;
                android_view_pan_y_permille = 0;
                break;
            case ANDROID_CTRL_LIFECYCLE_PAUSE:
                if (!dopause) {
                    plat_pause(1);
                    android_lifecycle_paused_by_us = 1;
                } else {
                    android_lifecycle_paused_by_us = 0;
                }
                break;
            case ANDROID_CTRL_LIFECYCLE_RESUME:
                if (android_lifecycle_paused_by_us) {
                    plat_pause(0);
                    android_lifecycle_paused_by_us = 0;
                }
                break;
            case ANDROID_CTRL_OPEN_OSD:
                if (*flag_osd_open) {
                    osd_close(*event);
                    *flag_osd_open = 0;
                } else {
                    *flag_osd_open = osd_open(*event);
                }
                break;
            case ANDROID_CTRL_MOUSE_MOVE: {
                const uint32_t packed = (uint32_t) value;
                const int dx = (int16_t) (packed >> 16);
                const int dy = (int16_t) (packed & 0xffffu);
                if (mousemutex) {
                    SDL_LockMutex(mousemutex);
                    mouse_scale(dx, dy);
                    SDL_UnlockMutex(mousemutex);
                }
                break;
            }
            case ANDROID_CTRL_TEXT_INPUT_START:
                if (sdl_win)
                    SDL_StartTextInput(sdl_win);
                break;
            case ANDROID_CTRL_KEYBOARD_LAYOUT:
                android_keyboard_layout = value ? 1 : 0;
                break;
            default:
                break;
        }
    } else if (event->user.code == ANDROID_USER_EVENT_MEDIA) {
        const int media_type = (int) (intptr_t) event->user.data1;
        char *path = (char *) event->user.data2;
        if (path) {
            if (media_type == ANDROID_MEDIA_FLOPPY)
                floppy_mount(0, path, 0);
            else if (media_type == ANDROID_MEDIA_CDROM)
                cdrom_mount(0, path);
            free(path);
        }
    } else if (event->user.code == ANDROID_USER_EVENT_HW_KEY) {
        const uint16_t xt = (uint16_t) (intptr_t) event->user.data1;
        const int down = (int) (intptr_t) event->user.data2;
        if (xt != 0)
            keyboard_input(down != 0, xt);
    } else if (event->user.code == ANDROID_USER_EVENT_HW_MOUSE_MOVE) {
        const int dx = (int) (intptr_t) event->user.data1;
        const int dy = (int) (intptr_t) event->user.data2;
        if (android_hw_mouse_trace_budget > 0) {
            --android_hw_mouse_trace_budget;
            pclog("[ANDROID][INPUT] HW_MOUSE_MOVE dx=%d dy=%d mouse_ready=%d\n",
                  dx, dy, mousemutex != NULL);
        }
        if (mousemutex) {
            SDL_LockMutex(mousemutex);
            mouse_scale(dx, dy);
            SDL_UnlockMutex(mousemutex);
        }
    } else if (event->user.code == ANDROID_USER_EVENT_HW_MOUSE_BUTTON) {
        const int mask = (int) (intptr_t) event->user.data1;
        const int down = (int) (intptr_t) event->user.data2;
        if (android_hw_mouse_trace_budget > 0) {
            --android_hw_mouse_trace_budget;
            pclog("[ANDROID][INPUT] HW_MOUSE_BUTTON mask=%d down=%d mouse_ready=%d\n",
                  mask, down, mousemutex != NULL);
        }
        if (mousemutex && mask) {
            SDL_LockMutex(mousemutex);
            if (down)
                mouse_set_buttons_ex(mouse_get_buttons_ex() | mask);
            else
                mouse_set_buttons_ex(mouse_get_buttons_ex() & ~mask);
            SDL_UnlockMutex(mousemutex);
        }
    } else if (event->user.code == ANDROID_USER_EVENT_HW_MOUSE_WHEEL) {
        const int steps = (int) (intptr_t) event->user.data1;
        if (android_hw_mouse_trace_budget > 0) {
            --android_hw_mouse_trace_budget;
            pclog("[ANDROID][INPUT] HW_MOUSE_WHEEL steps=%d mouse_ready=%d\n",
                  steps, mousemutex != NULL);
        }
        if (mousemutex && steps) {
            SDL_LockMutex(mousemutex);
            mouse_set_z(steps);
            SDL_UnlockMutex(mousemutex);
        }
    } else if (event->user.code == ANDROID_USER_EVENT_HW_MOUSE_CAPTURE) {
        const int captured = (int) (intptr_t) event->user.data1;
        /* Android already owns OS-level pointer capture.  Mirror only the 86Box
           core capture flag here; PS/2/serial mouse pollers intentionally do not
           emit guest packets unless mouse_capture/fullscreen is active. */
        mouse_capture = captured ? 1 : 0;
        pclog("[ANDROID][INPUT] HW_MOUSE_CAPTURE captured=%d native_mouse_capture=%d\n",
              captured, mouse_capture);
    } else if (event->user.code == ANDROID_USER_EVENT_HW_RELEASE_ALL) {
        keyboard_all_up();
        if (mousemutex) {
            SDL_LockMutex(mousemutex);
            mouse_set_buttons_ex(0);
            mouse_clear_z();
            SDL_UnlockMutex(mousemutex);
        }
    }
}
#endif

uint32_t
#ifdef USE_SDL2_LIB
timer_onesec(uint32_t interval, UNUSED(void *param))
#else
timer_onesec(UNUSED(void *param), UNUSED(SDL_TimerID timerID), uint32_t interval)
#endif
{
    pc_onesec();
    return interval;
}

/* Convert an SDL button number to a mouse_get_buttons_ex() mask. */
static int
sdl_mouse_buttonmask(uint8_t button)
{
    int ret;

    switch (button) {
        case SDL_BUTTON_LEFT:
            ret = 1;
            break;
        case SDL_BUTTON_RIGHT:
            ret = 2;
            break;
        case SDL_BUTTON_MIDDLE:
            ret = 4;
            break;
        case SDL_BUTTON_X1:
            ret = 8;
            break;
        case SDL_BUTTON_X2:
            ret = 16;
            break;
        default:
            ret = 0;
            break;
    }

    return ret;
}

extern int gfxcard[GFXCARD_MAX];
int
main(int argc, char **argv)
{
    SDL_Event event;
    int       ret = 0;

#ifdef __ANDROID__
    /* libmain.so remains loaded while switching profiles in the same app
       process. Track the full native lifetime so the Java profile manager never
       opens the hardware editor while SDL/86Box is still tearing down. */
    android_set_runtime_active(1);
    android_set_emulator_ready(0);
    android_set_emu_speed_percent(-1);
#endif
    SDL_Init(0);
    ret = pc_init(argc, argv);
#ifdef __ANDROID__
    android_install_native_crash_marker();
    always_log("[ANDROID] Native crash marker installed.\n");
#endif
    if (ret == 0) {
#ifdef __ANDROID__
        android_uninstall_native_crash_marker();
        android_set_runtime_active(0);
#endif
        return 0;
    }
    if (!pc_init_roms()) {
        ui_msgbox_header(MBX_FATAL, "No ROMs found.", EMU_NAME " could not find any usable ROM images.\n\nPlease download a ROM set and extract it into the \"roms\" directory.");
        SDL_Quit();
#ifdef __ANDROID__
        android_uninstall_native_crash_marker();
        android_set_runtime_active(0);
#endif
        return 6;
    }
    pc_init_modules();

    for (uint8_t i = 1; i < GFXCARD_MAX; i++)
        gfxcard[i]  = 0;
    eventthread = SDL_GetCurrentThreadID();
    blitmtx     = SDL_CreateMutex();
    if (!blitmtx) {
        fprintf(stderr, "Failed to create blit mutex: %s", SDL_GetError());
#ifdef __ANDROID__
        android_uninstall_native_crash_marker();
        android_set_runtime_active(0);
#endif
        SDL_Quit();
        return -1;
    }

    monitor_init();

    mousemutex = SDL_CreateMutex();

    if (start_in_fullscreen)
        video_fullscreen = 1;

    sdl_initho();

    /* Fire up the machine. */
    pc_reset_hard_init();
#ifdef __ANDROID__
    android_set_emulator_ready(1);
#endif

    /* Set the PAUSE mode depending on the renderer. */
    plat_pause(0);

    /* Initialize the rendering window, or fullscreen. */
    do_start();

#ifndef USE_CLI
    thread_create(monitor_thread, NULL);
#endif

    SDL_AddTimer(1000, timer_onesec, NULL);
    while (!is_quit)
    {
        static int mouse_inside = 0;
        static int osd_first_key_pressed = 0;
        static int flag_osd_open = 0;

        if (!SDL_WaitEventTimeout(&event, 1))
            goto check_flags;
        do {
#ifdef __ANDROID__
            if (event.type == SDL_EVENT_USER) {
                android_handle_user_event(&event, &flag_osd_open);
                continue;
            }
#endif
            if (flag_osd_open == 1)
            {
                // route almost everything to the OSD
                switch (event.type)
                {
                    case SDL_EVENT_QUIT:
                    {
                        exit_event = 1;
                        break;
                    }
                    case SDL_EVENT_RENDER_DEVICE_RESET:
                    case SDL_EVENT_RENDER_TARGETS_RESET:
                    {
                        extern void sdl_reinit_texture(void);

                        printf("reinit tex\n");
                        sdl_reinit_texture();
                        break;
                    }
#ifdef USE_SDL2_LIB
                    case SDL_WINDOWEVENT:
                        {
                            switch (event.window.event) {
                                case SDL_WINDOWEVENT_ENTER:
                                    mouse_inside = 1;
                                    break;
                                case SDL_WINDOWEVENT_LEAVE:
                                    mouse_inside = 0;
                                    break;
                            }
                            break;
                        }
#else
                    case SDL_EVENT_WINDOW_MOUSE_ENTER:
                        mouse_inside = 1;
                        break;
                    case SDL_EVENT_WINDOW_MOUSE_LEAVE:
                        mouse_inside = 0;
                        break;
#endif
                    
                    default:
                    {
                        // route everything else
                        flag_osd_open = osd_handle(event);

                        if (flag_osd_open == 0)
                        {
                            // close it
                            osd_close(event);
                        }

                        break;
                    }
                }
            }
            else
            {
                switch (event.type)
                {
                    case SDL_EVENT_QUIT:
                        exit_event = 1;
                        break;
                    case SDL_EVENT_MOUSE_WHEEL:
                        {
                            if (mouse_capture || video_fullscreen) {
                                if (event.wheel.direction == SDL_MOUSEWHEEL_FLIPPED) {
                                    event.wheel.x *= -1;
                                    event.wheel.y *= -1;
                                }
                                SDL_LockMutex(mousemutex);
                                mouse_set_z(event.wheel.y);
                                SDL_UnlockMutex(mousemutex);
                            }
                            break;
                        }
                    case SDL_EVENT_MOUSE_MOTION:
                        {
                            if (mouse_capture || video_fullscreen) {
                                SDL_LockMutex(mousemutex);
                                mouse_scale(event.motion.xrel, event.motion.yrel);
                                SDL_UnlockMutex(mousemutex);
                            }
                            break;
                        }
                    /* Touch events */
                    case SDL_EVENT_FINGER_DOWN:
                        {
#ifdef __ANDROID__
                            android_touch_down(&event.tfinger);
#endif
                            break;
                        }
                    case SDL_EVENT_FINGER_UP:
                        {
#ifdef __ANDROID__
                            android_touch_up(&event.tfinger);
#endif
                            break;
                        }
#ifdef __ANDROID__
                    case SDL_EVENT_FINGER_CANCELED:
                        android_touch_cancel(&event.tfinger);
                        break;
#endif
                    case SDL_EVENT_FINGER_MOTION:
                        {
#ifdef __ANDROID__
                            android_touchlat_sdl_motion(event.common.timestamp);
                            android_touch_motion(&event.tfinger);
#else
                            // See SDL_TouchFingerEvent
                            if (mouse_capture || video_fullscreen) {
                                SDL_LockMutex(mousemutex);
                                int w, h;
                                SDL_GetWindowSize(sdl_win, &w, &h);
                                mouse_scale((int)(event.tfinger.dx * w), (int)(event.tfinger.dy * h));
                                SDL_UnlockMutex(mousemutex);
                            }
#endif
                            break;
                        }

                    case SDL_EVENT_MOUSE_BUTTON_DOWN:
                    case SDL_EVENT_MOUSE_BUTTON_UP:
                        {
                            if ((event.button.button == SDL_BUTTON_LEFT)
                                && !(mouse_capture || video_fullscreen)
#ifdef USE_SDL2_LIB
                                && event.button.state == SDL_RELEASED
#else
                                && !event.button.down
#endif
                                && mouse_inside) {
                                plat_mouse_capture(1);
                                break;
                            }
                            if (mouse_capture && (sdl_mouse_buttonmask(event.button.button) & mouse_get_release_buttons()) && !video_fullscreen) {
                                plat_mouse_capture(0);
                                break;
                            }
                            if (mouse_capture || video_fullscreen) {
                                int buttonmask = sdl_mouse_buttonmask(event.button.button);

                                if (buttonmask == 0)
                                    printf("Unknown mouse button %d\n", event.button.button);
                                SDL_LockMutex(mousemutex);
#ifdef USE_SDL2_LIB
                                if (event.button.state == SDL_PRESSED)
#else
                                if (event.button.down)
#endif
                                    mouse_set_buttons_ex(mouse_get_buttons_ex() | buttonmask);
                                else
                                    mouse_set_buttons_ex(mouse_get_buttons_ex() & ~buttonmask);
                                SDL_UnlockMutex(mousemutex);
                            }
                            break;
                        }
                    case SDL_EVENT_RENDER_DEVICE_RESET:
                    case SDL_EVENT_RENDER_TARGETS_RESET:
                        {
                            extern void sdl_reinit_texture(void);

                            sdl_reinit_texture();
                            break;
                        }
#ifdef __ANDROID__
                    case SDL_EVENT_TEXT_INPUT:
                        android_type_text(event.text.text);
                        break;
#endif
                    case SDL_EVENT_KEY_DOWN:
                    case SDL_EVENT_KEY_UP:
                        {
                            uint16_t xtkey = 0;

#ifdef USE_SDL2_LIB
                            if (event.key.keysym.scancode == osd_open_first_key)
#else
                            if (event.key.scancode == osd_open_first_key)
#endif
                            {
                                if (event.type == SDL_EVENT_KEY_DOWN)
                                    osd_first_key_pressed = 1;
                                else
                                    osd_first_key_pressed = 0;
                            }
#ifdef USE_SDL2_LIB
                            else if (osd_first_key_pressed && event.type == SDL_EVENT_KEY_DOWN && event.key.keysym.scancode == osd_open_second_key)
#else
                            else if (osd_first_key_pressed && event.type == SDL_EVENT_KEY_DOWN && event.key.scancode == osd_open_second_key)
#endif
                            {
                                // open OSD!
                                flag_osd_open = osd_open(event);

                                // we can assume alt-gr has been released, tell this also to the virtual machine
                                osd_first_key_pressed = 0;
                                keyboard_input(0, sdl_to_xt[osd_open_first_key]);
                                break;
                            }
                            else
                            {
                                // invalidate osd_first_key_pressed is something happens between its keydown and keydown for G
                                osd_first_key_pressed = 0;
                            }
#ifdef USE_SDL2_LIB
                            switch (event.key.keysym.scancode) {
                                default:
                                    xtkey = sdl_to_xt[event.key.keysym.scancode];
                                    break;
                            }
#else
                            switch (event.key.scancode) {
                                default:
                                    xtkey = sdl_to_xt[event.key.scancode];
                                    break;
                            }
#endif
#ifdef USE_SDL2_LIB
                            const int key_down = event.key.state == SDL_PRESSED;
#else
                            const int key_down = event.key.down;
#endif
                            if (key_down && (keyboard_get_shift() & 0x11) && (keyboard_get_shift() & 0x22) && (xtkey == 0x22) && mouse_capture) {
                                /* Prevent an Alt-based shortcut from looking like a standalone
                                 * Alt press to the guest when the held modifiers are released. */
                                keyboard_input(0, xtkey);
                                keyboard_all_up();
                                plat_mouse_capture(0);
                                break;
                            }
                            keyboard_input(key_down, xtkey);
                            break;
                        }
#ifdef USE_SDL2_LIB
                    case SDL_WINDOWEVENT:
                        {
                            switch (event.window.event) {
                                case SDL_WINDOWEVENT_ENTER:
                                    mouse_inside = 1;
                                    break;
                                case SDL_WINDOWEVENT_LEAVE:
                                    mouse_inside = 0;
                                    break;
                            }
                            break;
                        }
#else
                    case SDL_EVENT_WINDOW_MOUSE_ENTER:
                        mouse_inside = 1;
                        break;
                    case SDL_EVENT_WINDOW_MOUSE_LEAVE:
                        mouse_inside = 0;
                        break;
#endif
                    default:
                    {
                        // printf("Unhandled SDL event: %d\n", event.type);
                        break;
                    }
                }
            }
        } while (SDL_PollEvent(&event));

check_flags:
#ifdef __ANDROID__
        android_touch_long_press_tick();
        android_release_due_buttons();
#endif
        if (blitreq) {
            extern void sdl_blit(int x, int y, int w, int h);
            sdl_blit(params.x, params.y, params.w, params.h);
        }
        /* Drawing the OSD can ask it to close -- posting a message does -- so
         * finish that here instead of waiting for the next input event. */
        if (flag_osd_open && osd_take_pending_close())
            flag_osd_open = 0;
        if (title_set) {
            ui_window_title_real();
        }
        if (video_fullscreen && keyboard_isfsexit()) {
            sdl_set_fs(0);
            video_fullscreen = 0;
        }
        if (fullscreen_pending) {
            sdl_set_fs(video_fullscreen);
            fullscreen_pending = 0;
        }
        if (exit_event) {
#ifdef __ANDROID__
            android_set_emulator_ready(0);
#endif
            do_stop();
            break;
        }
    }
    printf("\n");
    SDL_DestroyMutex(blitmtx);
    SDL_DestroyMutex(mousemutex);
#ifdef __ANDROID__
    android_set_emulator_ready(0);
    android_set_emu_speed_percent(-1);
    always_log("[ANDROID] Normal emulator shutdown.\n");
    android_uninstall_native_crash_marker();
#endif
    SDL_Quit();
    monitor_close();
#ifdef __ANDROID__
    android_set_runtime_active(0);
#endif
    return 0;
}

#ifdef _WIN32
int WINAPI
WinMain(UNUSED(HINSTANCE instance), UNUSED(HINSTANCE previous), UNUSED(LPSTR command_line), UNUSED(int show))
{
    return main(__argc, __argv); // :')
}
#endif

void
startblit(void)
{
    SDL_LockMutex(blitmtx);
}

void
endblit(void)
{
    SDL_UnlockMutex(blitmtx);
}
