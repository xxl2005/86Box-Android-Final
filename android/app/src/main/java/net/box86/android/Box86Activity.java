package net.box86.android;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.hardware.input.InputManager;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.KeyCharacterMap;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.libsdl.app.SDLActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Android touch/control shell for the SDL frontend of 86Box.
 *
 * v0.8.0 keeps the Android touch/hardware-input stack, Stage 2 host I/O
 * parallelism and profiler, and runs each VM from an isolated hardware-profile
 * directory selected by ProfileManagerActivity.
 */
public final class Box86Activity extends SDLActivity {
    private static final String TAG = "86BoxAndroid";

    private static final int REQ_FLOPPY = 1001;
    private static final int REQ_CDROM = 1002;
    private static final int REQ_EXPORT_DIAGNOSTICS = 1004;

    private static final int CTRL_TOGGLE_PAUSE = 1;
    private static final int CTRL_HARD_RESET = 2;
    private static final int CTRL_LEFT_CLICK = 3;
    private static final int CTRL_RIGHT_CLICK = 4;
    private static final int CTRL_MOUSE_SPEED = 5;
    private static final int CTRL_TAP_TO_CLICK = 6;
    private static final int CTRL_ZOOM = 7;
    private static final int CTRL_ZOOM_RESET = 8;
    private static final int CTRL_LIFECYCLE_PAUSE = 9;
    private static final int CTRL_LIFECYCLE_RESUME = 10;
    private static final int CTRL_OPEN_OSD = 11;
    private static final int CTRL_MOUSE_MOVE = 12;
    private static final int CTRL_TEXT_INPUT_START = 13;
    private static final int CTRL_KEYBOARD_LAYOUT = 14;
    private static final int CTRL_PERF_PROFILE = 15;
    private static final int CTRL_DISPLAY_SCALE_MODE = 16;
    private static final int CTRL_HOST_PERFORMANCE_MODE = 17;
    private static final int CTRL_CPU_CYCLES_MODE = 23;
    private static final int CTRL_CPU_CYCLES_FIXED_PERCENT = 24;
    private static final int CTRL_CPU_CYCLES_CUSTOM_KCPS = 25;

    private static final int CORE_STRATEGY_AUTO = 0;
    private static final int CORE_STRATEGY_PRIME = 1;
    private static final int CORE_STRATEGY_BIG = 2;
    private static final int CORE_STRATEGY_MANUAL = 3;
    private static final int CORE_STRATEGY_ADAPTIVE = 4;
    private static final long ADAPTIVE_CANDIDATE_HOLD_MS = 8_000L;
    private static final long ADAPTIVE_MIN_SWITCH_MS = 20_000L;

    private static final int MEDIA_FLOPPY = 1;
    private static final int MEDIA_CDROM = 2;
    private static final long MEDIA_IMPORT_FREE_RESERVE = 64L * 1024L * 1024L;
    private static final long MAX_CUE_BYTES = 8L * 1024L * 1024L;

    private static native void nativeControl(int action, int value);
    private static native void nativeSetManualCoreMask(long mask, boolean enabled);
    private static native void nativeMountMedia(int mediaType, String path);
    private static native void nativeLifecyclePause(boolean paused);
    private static native String nativeGetRendererInfo();
    private static native String nativeGetPerfSummary();
    private static native int nativeGetEmulationSpeedPercent();
    private static native int nativeGetCpuThreadTid();
    private static native String nativeGetHostPerfState();
    private static native String nativeGetCpuCyclesState();
    private static native void nativeHardwareKey(int xtCode, boolean down);
    private static native void nativeHardwareMouseMove(int dx, int dy);
    private static native void nativeHardwareMouseButton(int buttonMask, boolean down);
    private static native void nativeHardwareMouseWheel(int verticalSteps);
    private static native void nativeHardwareMouseCapture(boolean captured);
    private static native void nativeHardwareReleaseAll();

    private FrameLayout overlayRoot;
    private LinearLayout toolbar;
    private ScrollView toolbarScroll;
    private Button restoreToolbarButton;
    private Button pauseButton;
    private JoystickView joystick;
    private TextView speedPercentText;
    private SharedPreferences prefs;
    private boolean userPaused = false;
    private int mouseSpeed = 100;
    private int zoomPercent = 100;
    private int displayScaleMode = 2; // FULLSCR_SCALE_KEEPRATIO = automatic fit without distortion
    private boolean germanKeyboard = false;
    private boolean hardwareInputEnabled = true;
    private boolean perfProfileEnabled = false;
    private boolean speedPercentVisible = true;
    private int speedOverlaySizePercent = 60;
    private int speedOverlayOpacityPercent = 30;
    private boolean speedOverlayPositionCustom = false;
    private int speedOverlayXPermille = 0;
    private int speedOverlayYPermille = 0;
    private boolean activityDestroyed = false;
    private boolean hardwareMouseCaptured = false;
    private View hardwareMouseCaptureTarget;
    private int hardwareButtonsDown = 0;
    private float lastHardwareMouseX = Float.NaN;
    private float lastHardwareMouseY = Float.NaN;
    private float hardwareDxRemainder = 0f;
    private float hardwareDyRemainder = 0f;
    private float hardwareWheelRemainder = 0f;
    private final Set<Integer> hardwarePressedXt = new LinkedHashSet<>();
    private boolean suppressMouseReleaseG = false;
    private int hardwareMouseTraceBudget = 48;
    private InputManager inputManager;
    private boolean inputDeviceListenerRegistered = false;
    private boolean externalPickerActive = false;
    private int performanceMode = HostPerformanceTuner.MODE_AUTO;
    private int effectivePerformanceMode = HostPerformanceTuner.MODE_BALANCED;
    private HostPerformanceTuner.HostProfile hostPerformanceProfile;
    private boolean manualCoreSelectionEnabled = false;
    private long manualCoreMask = 0L;
    private int coreStrategy = CORE_STRATEGY_AUTO;
    private int cpuCyclesMode = 0; // 0 = unmodified 86Box core and nominal budget
    private int cpuCyclesFixedPercent = 100;
    private int cpuCyclesCustomKcps = 50000;
    private long adaptiveActiveMask = 0L;
    private long adaptiveCandidateMask = 0L;
    private long adaptiveCandidateSinceMs = 0L;
    private long adaptiveLastSwitchMs = 0L;
    private String adaptiveCoreState = "warming-up";
    private VirtualPcKeyboardView virtualKeyboard;
    private boolean virtualKeyboardVisible = false;
    private FloatingKeyOverlayView floatingKeyOverlay;
    // v0.9.35: independent touchscreen streams for game keys and the emulated PC.
    // Finger IDs are stable across ACTION_POINTER_DOWN/UP, unlike action indexes.
    private final Set<Integer> vmTouchPointers = new LinkedHashSet<>();
    private long vmTouchDownTime = 0L;
    private boolean gameTouchWasRouted = false;
    // After a canceled route, swallow remnants of the old gesture until UP.
    // Otherwise Android can receive POINTER_UP without a matching DOWN.
    private boolean suppressStaleTouchGesture = false;
    private int speedDiagSamples = 0;
    private long speedDiagSum = 0L;
    private int speedDiagMin = Integer.MAX_VALUE;
    private int speedDiagMax = Integer.MIN_VALUE;
    private int speedDiagTick = 0;
    private final ArrayDeque<PerfHistorySample> perfHistory = new ArrayDeque<>();
    private HostPerformanceTuner.FrequencySample lastFrequencySample;
    private double bestPerfWindowSpeed10 = -1.0;
    private double bestPerfWindowFreqRatio10 = -1.0;
    private String lastPerfTrendDiagnosis = "warming-up";

    private static final class PerfHistorySample {
        final long timeMs;
        final int speedPercent;
        final int runningCpu;
        final long currentKHz;
        final long maxKHz;

        PerfHistorySample(long timeMs, int speedPercent, int runningCpu, long currentKHz, long maxKHz) {
            this.timeMs = timeMs;
            this.speedPercent = speedPercent;
            this.runningCpu = runningCpu;
            this.currentKHz = currentKHz;
            this.maxKHz = maxKHz;
        }
    }
    private int externalPickerRequestCode = -1;

    private final InputManager.InputDeviceListener inputDeviceListener = new InputManager.InputDeviceListener() {
        @Override public void onInputDeviceAdded(int deviceId) {
            InputDevice device = InputDevice.getDevice(deviceId);
            if (isPhysicalMouseDevice(device)) {
                DiagnosticLogger.i(TAG, "Physical mouse connected: " + describeInputDevice(device));
                scheduleAutoCapturePhysicalMouse("device-added");
            }
        }

        @Override public void onInputDeviceChanged(int deviceId) {
            InputDevice device = InputDevice.getDevice(deviceId);
            if (isPhysicalMouseDevice(device)) {
                DiagnosticLogger.i(TAG, "Physical mouse changed: " + describeInputDevice(device));
                scheduleAutoCapturePhysicalMouse("device-changed");
            }
        }

        @Override public void onInputDeviceRemoved(int deviceId) {
            DiagnosticLogger.i(TAG, "Input device removed id=" + deviceId);
            if (!hasPhysicalMouseConnected())
                setHardwareMouseCaptured(false);
        }
    };

    private final Runnable speedPercentUpdater = new Runnable() {
        @Override
        public void run() {
            if (activityDestroyed)
                return;
            updateSpeedPercentOverlay();
            if (overlayRoot != null && !activityDestroyed)
                overlayRoot.postDelayed(this, 1000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prepareRuntimeTree();
        File initialProfile = getCurrentProfileDir();
        String initialProfileName = getIntent() == null ? null : getIntent().getStringExtra(ProfileManagerActivity.EXTRA_PROFILE_NAME);
        DiagnosticLogger.startSession(this, initialProfile, initialProfileName);
        DiagnosticLogger.i(TAG, "Box86Activity onCreate");
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences("86box_android_ui", MODE_PRIVATE);
        mouseSpeed = clamp(prefs.getInt("mouse_speed", 100), 25, 300);
        zoomPercent = clamp(prefs.getInt("zoom", 100), 50, 300);
        displayScaleMode = clamp(prefs.getInt("display_scale_mode", 2), 0, 4);
        boolean systemGerman = "de".equalsIgnoreCase(Locale.getDefault().getLanguage());
        germanKeyboard = prefs.getBoolean("keyboard_de", systemGerman);
        hardwareInputEnabled = prefs.getBoolean("hardware_input", true);
        speedPercentVisible = prefs.getBoolean("show_speed_percent", true);
        speedOverlaySizePercent = clamp(prefs.getInt("speed_overlay_size_percent", 60), 60, 160);
        speedOverlayOpacityPercent = clamp(prefs.getInt("speed_overlay_opacity_percent", 30), 20, 100);
        speedOverlayPositionCustom = prefs.getBoolean("speed_overlay_position_custom", false);
        speedOverlayXPermille = clamp(prefs.getInt("speed_overlay_x_permille", 0), 0, 1000);
        speedOverlayYPermille = clamp(prefs.getInt("speed_overlay_y_permille", 0), 0, 1000);
        inputManager = (InputManager) getSystemService(Context.INPUT_SERVICE);
        performanceMode = clamp(prefs.getInt("performance_mode", HostPerformanceTuner.MODE_AUTO),
                HostPerformanceTuner.MODE_AUTO, HostPerformanceTuner.MODE_MAXIMUM);
        manualCoreSelectionEnabled = prefs.getBoolean("manual_core_selection_enabled", false);
        manualCoreMask = prefs.getLong("manual_core_mask", 0L);
        if (prefs.contains("core_strategy")) {
            coreStrategy = clamp(prefs.getInt("core_strategy", CORE_STRATEGY_AUTO), CORE_STRATEGY_AUTO, CORE_STRATEGY_ADAPTIVE);
        } else {
            coreStrategy = manualCoreSelectionEnabled && manualCoreMask != 0L ? CORE_STRATEGY_MANUAL : CORE_STRATEGY_AUTO;
            prefs.edit().putInt("core_strategy", coreStrategy).apply();
        }
        // v0.9.24 changes the default from Adaptive Anti-Throttle to Android/ADPF scheduling.
        // Migrate the old implicit Adaptive default once, while preserving explicit Prime/Big/Manual choices.
        if (!prefs.getBoolean("core_strategy_default_auto_migrated_v0924", false)) {
            if (coreStrategy == CORE_STRATEGY_ADAPTIVE) {
                coreStrategy = CORE_STRATEGY_AUTO;
            }
            prefs.edit()
                    .putInt("core_strategy", coreStrategy)
                    .putBoolean("core_strategy_default_auto_migrated_v0924", true)
                    .apply();
        }
        hostPerformanceProfile = HostPerformanceTuner.detect();
        // Old experimental helper settings are deliberately NOT migrated.
        // Clear stale preferences so an upgrade cannot silently re-enable them.
        prefs.edit().remove("experimental_thread_assist")
                .remove("experimental_multicore_engine_stage_a")
                .remove("experimental_dolphin_dualcore")
                .remove("experimental_jit_profiler_e")
                .remove("experimental_execution_profiler_f").apply();
        cpuCyclesMode = clamp(prefs.getInt("clean_cpu_cycles_mode", 0), 0, 4);
        cpuCyclesFixedPercent = clamp(prefs.getInt("clean_cpu_cycles_fixed_percent", 100), 25, 100);
        cpuCyclesCustomKcps = clamp(prefs.getInt("clean_cpu_cycles_custom_kcps", 50000), 1000, 500000);
        // Keep periodic diagnostics file I/O off the Android UI/input thread.
        touchDiagLogThread = new HandlerThread("86BoxTouchLatencyLog");
        touchDiagLogThread.start();
        touchDiagLogHandler = new Handler(touchDiagLogThread.getLooper());
        applyPerformanceMode("onCreate");

        setImmersiveMode();
        buildTouchOverlay();
        installPhysicalMouseCaptureTarget("onCreate");

        postNativeControl(CTRL_MOUSE_SPEED, mouseSpeed);
        postNativeControl(CTRL_TAP_TO_CLICK, prefs.getBoolean("tap_to_click", true) ? 1 : 0);
        postNativeControl(CTRL_ZOOM, zoomPercent);
        postNativeControl(CTRL_DISPLAY_SCALE_MODE, displayScaleMode);
        postNativeControl(CTRL_KEYBOARD_LAYOUT, germanKeyboard ? 1 : 0);
        DiagnosticLogger.i(TAG, "Display init mode=" + displayScaleModeName(displayScaleMode) +
                " zoom=" + zoomPercent + "% screen=" + getResources().getConfiguration().screenWidthDp +
                "x" + getResources().getConfiguration().screenHeightDp + "dp");
    }

    @Override
    protected void onPause() {
        DiagnosticLogger.i(TAG, "onPause: releasing input and pausing emulator externalPicker=" + externalPickerActive +
                " request=" + externalPickerRequestCode);
        unregisterPhysicalMouseListener();
        stopSpeedPercentUpdater();
        // Android lifecycle pause is intentionally non-blocking in v0.9.11+ so
        // opening DocumentsUI/Home cannot stall the UI thread while the guest pauses.
        cancelIndependentTouchRouting("onPause");
        releaseHardwareInput();
        releaseVirtualKeyboardKeys();
        releaseFloatingKeyOverlayKeys();
        setHardwareMouseCaptured(false);
        DiagnosticLogger.i(TAG, "onPause: before native lifecycle pause externalPicker=" + externalPickerActive);
        setLifecyclePause(true);
        DiagnosticLogger.i(TAG, "onPause: native lifecycle pause complete externalPicker=" + externalPickerActive);
        super.onPause();
        DiagnosticLogger.i(TAG, "onPause: SDLActivity pause returned externalPicker=" + externalPickerActive);
    }

    @Override
    protected void onResume() {
        super.onResume();
        DiagnosticLogger.i(TAG, "onResume externalPicker=" + externalPickerActive +
                " request=" + externalPickerRequestCode);
        setImmersiveMode();
        applyPerformanceMode("resume");
        releaseHardwareInput();
        setLifecyclePause(false);
        registerPhysicalMouseListener();
        installPhysicalMouseCaptureTarget("resume");
        View decor = getWindow() == null ? null : getWindow().getDecorView();
        if (decor != null)
            decor.postDelayed(() -> installPhysicalMouseCaptureTarget("resume-post"), 80L);
        startSpeedPercentUpdater();
        scheduleAutoCapturePhysicalMouse("resume");
    }

    @Override
    protected void onDestroy() {
        DiagnosticLogger.i(TAG, "onDestroy externalPicker=" + externalPickerActive +
                " request=" + externalPickerRequestCode);
        activityDestroyed = true;
        if (touchDiagLogThread != null) {
            touchDiagLogThread.quitSafely();
            touchDiagLogThread = null;
            touchDiagLogHandler = null;
        }
        unregisterPhysicalMouseListener();
        stopSpeedPercentUpdater();
        if (!externalPickerActive)
            stopVmPickerKeepAlive();
        HostPerformanceTuner.closeAdpf();
        cancelIndependentTouchRouting("onDestroy");
        releaseVirtualKeyboardKeys();
        releaseFloatingKeyOverlayKeys();
        super.onDestroy();
    }

    @Override
    protected String[] getArguments() {
        File root = new File(getFilesDir(), "86box");
        File profile = getCurrentProfileDir();
        File roms = new File(root, "roms");
        File assets = new File(root, "assets");
        applyVoodooRenderThreadSettingBeforeLaunch(profile);
        File nativeLog = DiagnosticLogger.getNativeLogFile(profile);
        DiagnosticLogger.i(TAG, "Starting native 86Box: profile=" + profile.getAbsolutePath() + " log=" + nativeLog.getAbsolutePath());
        return new String[] {
                "--vmpath", profile.getAbsolutePath(),
                "--rompath", roms.getAbsolutePath(),
                "--assetpath", assets.getAbsolutePath(),
                "--logfile", nativeLog.getAbsolutePath(),
                "--fullscreen",
                "--noconfirm"
        };
    }

    private void buildTouchOverlay() {
        overlayRoot = new FrameLayout(this);
        // Explicit even on devices with vendor-specific Android ViewGroup behavior.
        View content = findViewById(android.R.id.content);
        if (content instanceof ViewGroup)
            ((ViewGroup) content).setMotionEventSplittingEnabled(true);
        overlayRoot.setMotionEventSplittingEnabled(true);
        overlayRoot.setClipChildren(false);
        overlayRoot.setClipToPadding(false);
        addContentView(overlayRoot, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        createFloatingKeyOverlay();

        toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.VERTICAL);
        toolbar.setGravity(Gravity.CENTER_HORIZONTAL);
        toolbar.setPadding(dp(4), dp(5), dp(4), dp(5));
        toolbar.setBackgroundColor(0x66000000);

        addToolbarButton("⌨", "86Box virtual PC keyboard", v -> toggleVirtualKeyboard());
        addToolbarButton("KEYS", "Configure floating game key overlay", v -> showFloatingKeyOverlaySettings());
        addToolbarButton("L", "Left mouse click", v -> postNativeControl(CTRL_LEFT_CLICK, 0));
        addToolbarButton("R", "Right mouse click", v -> postNativeControl(CTRL_RIGHT_CLICK, 0));
        addToolbarButton("MOUSE", "Capture/release physical mouse", v -> setHardwareMouseCaptured(!hardwareMouseCaptured));
        addToolbarButton("BOOST", "Host CPU performance mode", v -> showPerformanceSettings());
        addToolbarButton("PERF", "Start/stop internal performance profiler", v -> togglePerformanceProfiler());
        addToolbarButton("LOG", "View / export diagnostic logs", v -> showDiagnosticLogs());
        addToolbarButton("VM", "Return to hardware profile manager", v -> returnToProfileManager());

        pauseButton = addToolbarButton("Ⅱ", "Pause / resume", v -> {
            postNativeControl(CTRL_TOGGLE_PAUSE, 0);
            userPaused = !userPaused;
            pauseButton.setText(userPaused ? "▶" : "Ⅱ");
        });

        addToolbarButton("↻", "Hard reset", v -> confirmHardReset());
        addToolbarButton("FDD", "Load floppy image", v -> openMediaPicker(REQ_FLOPPY));
        addToolbarButton("CD", "Load CD-ROM image", v -> openMediaPicker(REQ_CDROM));
        addToolbarButton("HDD", "Manage fixed hard disks (VM must be stopped)", v -> showOfflineHddManager());
        addToolbarButton("MENU", "86Box OSD menu", v -> postNativeControl(CTRL_OPEN_OSD, 0));
        addToolbarButton("VIEW", "VM image size / aspect ratio", v -> showDisplaySettings());
        addToolbarButton("⛶", "Hide toolbar", v -> setToolbarHidden(true));
        addToolbarButton("⚙", "Touch settings", v -> showTouchSettings());

        toolbarScroll = new ScrollView(this);
        toolbarScroll.setFillViewport(false);
        toolbarScroll.setVerticalScrollBarEnabled(true);
        toolbarScroll.setScrollbarFadingEnabled(false);
        toolbarScroll.setSmoothScrollingEnabled(true);
        toolbarScroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        toolbarScroll.setBackgroundColor(Color.TRANSPARENT);
        toolbarScroll.addView(toolbar, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        FrameLayout.LayoutParams toolbarLp = new FrameLayout.LayoutParams(dp(72), ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.END | Gravity.CENTER_VERTICAL);
        toolbarLp.topMargin = dp(6);
        toolbarLp.bottomMargin = dp(6);
        toolbarLp.rightMargin = dp(6);
        overlayRoot.addView(toolbarScroll, toolbarLp);

        restoreToolbarButton = new Button(this);
        restoreToolbarButton.setText("≡");
        restoreToolbarButton.setTextSize(18);
        restoreToolbarButton.setTextColor(Color.WHITE);
        restoreToolbarButton.setBackgroundColor(0x88000000);
        restoreToolbarButton.setVisibility(View.GONE);
        restoreToolbarButton.setOnClickListener(v -> setToolbarHidden(false));
        FrameLayout.LayoutParams restoreLp = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.END | Gravity.TOP);
        restoreLp.topMargin = dp(8);
        restoreLp.rightMargin = dp(8);
        overlayRoot.addView(restoreToolbarButton, restoreLp);

        speedPercentText = new TextView(this);
        speedPercentText.setText("--%");
        speedPercentText.setTextColor(Color.WHITE);
        speedPercentText.setGravity(Gravity.CENTER);
        speedPercentText.setClickable(true);
        speedPercentText.setFocusable(false);
        speedPercentText.setVisibility(speedPercentVisible ? View.VISIBLE : View.GONE);
        speedPercentText.setOnTouchListener(new SpeedOverlayDragListener());
        FrameLayout.LayoutParams speedLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.START | Gravity.TOP);
        speedLp.leftMargin = dp(10);
        speedLp.topMargin = dp(10);
        overlayRoot.addView(speedPercentText, speedLp);
        applySpeedOverlayStyle();
        overlayRoot.post(this::applySpeedOverlayPosition);
        updateSpeedPercentOverlay();
        startSpeedPercentUpdater();

        joystick = new JoystickView(this);
        joystick.setVisibility(prefs.getBoolean("joystick", true) ? View.VISIBLE : View.GONE);
        int joystickSize = Math.max(dp(104), Math.min(dp(154), getResources().getDisplayMetrics().heightPixels / 3));
        FrameLayout.LayoutParams joyLp = new FrameLayout.LayoutParams(joystickSize, joystickSize, Gravity.START | Gravity.BOTTOM);
        joyLp.leftMargin = dp(16);
        joyLp.bottomMargin = dp(18);
        overlayRoot.addView(joystick, joyLp);
    }

    private void startSpeedPercentUpdater() {
        if (overlayRoot == null || activityDestroyed)
            return;
        overlayRoot.removeCallbacks(speedPercentUpdater);
        overlayRoot.post(speedPercentUpdater);
    }

    private void stopSpeedPercentUpdater() {
        if (overlayRoot != null)
            overlayRoot.removeCallbacks(speedPercentUpdater);
    }

    private void updateSpeedPercentOverlay() {
        if (speedPercentText == null)
            return;

        speedPercentText.setVisibility(speedPercentVisible ? View.VISIBLE : View.GONE);

        try {
            int percent = nativeGetEmulationSpeedPercent();
            boolean fullFrequencySnapshot = speedDiagTick >= 9;
            if (percent >= 0) {
                speedDiagSamples++;
                speedDiagSum += percent;
                speedDiagMin = Math.min(speedDiagMin, percent);
                speedDiagMax = Math.max(speedDiagMax, percent);
                HostPerformanceTuner.reportAdpfWork(percent);
            }
            int nativeTid = safeNativeCpuThreadTid();
            lastFrequencySample = HostPerformanceTuner.sampleFrequencies(nativeTid, fullFrequencySnapshot);
            recordPerformanceHistory(percent, lastFrequencySample);
            updateAdaptiveCoreStrategy(percent);
            ensureAdpfForNativeCpuThread("overlay");
            if (++speedDiagTick >= 10) {
                speedDiagTick = 0;
                updatePerformanceTrendDiagnosis();
                logPerformanceDiagnostics("periodic", percent);
            }
            String perfSuffix = " · " + HostPerformanceTuner.modeName(effectivePerformanceMode);
            if (hostPerformanceProfile != null) perfSuffix += "/" + hostPerformanceProfile.shortName();
            if (speedPercentVisible) {
                speedPercentText.setText(percent < 0 ? "--%" + perfSuffix :
                        String.format(Locale.ROOT, "%d%%", percent) + perfSuffix);
            }
        } catch (UnsatisfiedLinkError e) {
            speedPercentText.setText("--%");
        } catch (Throwable t) {
            DiagnosticLogger.w(TAG, "Could not update emulation speed overlay", t);
            speedPercentText.setText("--%");
        }
    }

    private void applySpeedOverlayStyle() {
        if (speedPercentText == null)
            return;
        float textSp = 16.0f * speedOverlaySizePercent / 100.0f;
        speedPercentText.setTextSize(textSp);
        int horizontal = Math.max(2, Math.round(dp(8) * speedOverlaySizePercent / 100.0f));
        int vertical = Math.max(1, Math.round(dp(4) * speedOverlaySizePercent / 100.0f));
        speedPercentText.setPadding(horizontal, vertical, horizontal, vertical);
        int alpha = clamp(Math.round(255.0f * speedOverlayOpacityPercent / 100.0f), 0, 255);
        speedPercentText.setBackgroundColor(Color.argb(alpha, 0, 0, 0));
    }

    private void applySpeedOverlayPosition() {
        if (speedPercentText == null || overlayRoot == null)
            return;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) speedPercentText.getLayoutParams();
        if (!speedOverlayPositionCustom || overlayRoot.getWidth() <= 0 || overlayRoot.getHeight() <= 0) {
            lp.leftMargin = dp(10);
            lp.topMargin = dp(10);
            speedPercentText.setLayoutParams(lp);
            return;
        }
        int maxLeft = Math.max(0, overlayRoot.getWidth() - speedPercentText.getWidth());
        int maxTop = Math.max(0, overlayRoot.getHeight() - speedPercentText.getHeight());
        lp.leftMargin = Math.round(maxLeft * speedOverlayXPermille / 1000.0f);
        lp.topMargin = Math.round(maxTop * speedOverlayYPermille / 1000.0f);
        speedPercentText.setLayoutParams(lp);
    }

    private void clampAndPersistSpeedOverlayPosition() {
        if (speedPercentText == null || overlayRoot == null || overlayRoot.getWidth() <= 0 || overlayRoot.getHeight() <= 0)
            return;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) speedPercentText.getLayoutParams();
        int maxLeft = Math.max(0, overlayRoot.getWidth() - speedPercentText.getWidth());
        int maxTop = Math.max(0, overlayRoot.getHeight() - speedPercentText.getHeight());
        lp.leftMargin = clamp(lp.leftMargin, 0, maxLeft);
        lp.topMargin = clamp(lp.topMargin, 0, maxTop);
        speedPercentText.setLayoutParams(lp);
        speedOverlayPositionCustom = true;
        speedOverlayXPermille = maxLeft > 0 ? clamp(Math.round(lp.leftMargin * 1000.0f / maxLeft), 0, 1000) : 0;
        speedOverlayYPermille = maxTop > 0 ? clamp(Math.round(lp.topMargin * 1000.0f / maxTop), 0, 1000) : 0;
        prefs.edit()
                .putBoolean("speed_overlay_position_custom", true)
                .putInt("speed_overlay_x_permille", speedOverlayXPermille)
                .putInt("speed_overlay_y_permille", speedOverlayYPermille)
                .apply();
    }

    private final class SpeedOverlayDragListener implements View.OnTouchListener {
        private float downRawX;
        private float downRawY;
        private int startLeft;
        private int startTop;
        private boolean moved;

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            if (overlayRoot == null || speedPercentText == null)
                return false;
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) speedPercentText.getLayoutParams();
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downRawX = event.getRawX();
                    downRawY = event.getRawY();
                    startLeft = lp.leftMargin;
                    startTop = lp.topMargin;
                    moved = false;
                    view.getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int dx = Math.round(event.getRawX() - downRawX);
                    int dy = Math.round(event.getRawY() - downRawY);
                    if (!moved && (Math.abs(dx) >= dp(2) || Math.abs(dy) >= dp(2)))
                        moved = true;
                    if (moved) {
                        int maxLeft = Math.max(0, overlayRoot.getWidth() - speedPercentText.getWidth());
                        int maxTop = Math.max(0, overlayRoot.getHeight() - speedPercentText.getHeight());
                        lp.leftMargin = clamp(startLeft + dx, 0, maxLeft);
                        lp.topMargin = clamp(startTop + dy, 0, maxTop);
                        speedPercentText.setLayoutParams(lp);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.getParent().requestDisallowInterceptTouchEvent(false);
                    if (moved)
                        clampAndPersistSpeedOverlayPosition();
                    return true;
                default:
                    return true;
            }
        }
    }

    private void applyPerformanceMode(String reason) {
        if (hostPerformanceProfile == null)
            hostPerformanceProfile = HostPerformanceTuner.detect();
        effectivePerformanceMode = HostPerformanceTuner.resolveMode(performanceMode, hostPerformanceProfile);
        HostPerformanceTuner.applyActivityHints(this, effectivePerformanceMode);
        postNativeControl(CTRL_HOST_PERFORMANCE_MODE, HostPerformanceTuner.toNativeMode(effectivePerformanceMode));
        applyCpuCyclesSettings();
        applyCoreStrategy("perf-" + reason);
        DiagnosticLogger.i("HostPerf", "apply reason=" + reason +
                " selected=" + HostPerformanceTuner.modeName(performanceMode) +
                " effective=" + HostPerformanceTuner.modeName(effectivePerformanceMode) +
                " coreStrategy=" + coreStrategySummary() +
                " host=" + HostPerformanceTuner.describe(hostPerformanceProfile));
        if (overlayRoot != null) {
            overlayRoot.postDelayed(() -> {
                ensureAdpfForNativeCpuThread("perf-" + reason);
                logPerformanceDiagnostics("perf-" + reason, safeNativeSpeedPercent());
            }, 750L);
        }
        updateSpeedPercentOverlay();
    }

    private String coreStrategyName(int strategy) {
        switch (strategy) {
            case CORE_STRATEGY_PRIME: return "Prime only";
            case CORE_STRATEGY_BIG: return "Big cluster";
            case CORE_STRATEGY_MANUAL: return "Manual";
            case CORE_STRATEGY_ADAPTIVE: return "Adaptive Anti-Throttle";
            default: return "Auto";
        }
    }

    private String coreStrategySummary() {
        String base = coreStrategyName(coreStrategy);
        if (coreStrategy == CORE_STRATEGY_MANUAL) return base + " · " + manualCoreSummary();
        if (coreStrategy == CORE_STRATEGY_ADAPTIVE) {
            String mask = adaptiveActiveMask == 0L ? "Auto" : maskToCpuList(adaptiveActiveMask);
            return base + " · " + mask + " · " + adaptiveCoreState;
        }
        return base;
    }

    private static String maskToCpuList(long mask) {
        if (mask == 0L) return "Auto";
        StringBuilder sb = new StringBuilder("CPU ");
        boolean first = true;
        for (int cpu = 0; cpu < 64; cpu++) {
            if ((mask & (1L << cpu)) == 0L) continue;
            if (!first) sb.append(',');
            sb.append(cpu);
            first = false;
        }
        return first ? "Auto" : sb.toString();
    }

    private long coreMaskForStrategy() {
        if (hostPerformanceProfile == null) hostPerformanceProfile = HostPerformanceTuner.detect();
        int count = Math.min(64, Math.max(1, hostPerformanceProfile.cpuCount));
        switch (coreStrategy) {
            case CORE_STRATEGY_PRIME:
                return HostPerformanceTuner.recommendedCoreMask(count, 2);
            case CORE_STRATEGY_BIG:
                return HostPerformanceTuner.recommendedBigClusterMask(count, 2);
            case CORE_STRATEGY_MANUAL:
                return manualCoreSelectionEnabled ? manualCoreMask : 0L;
            case CORE_STRATEGY_ADAPTIVE:
                if (adaptiveActiveMask == 0L) {
                    long live = HostPerformanceTuner.bestLiveCoreMask(count, 2);
                    adaptiveActiveMask = live != 0L ? live : HostPerformanceTuner.recommendedCoreMask(count, 2);
                    adaptiveLastSwitchMs = android.os.SystemClock.elapsedRealtime();
                    adaptiveCoreState = "initial " + HostPerformanceTuner.coreMaskFrequencySummary(adaptiveActiveMask);
                }
                return adaptiveActiveMask;
            default:
                return 0L;
        }
    }

    private void applyCoreStrategy(String reason) {
        if (effectivePerformanceMode < HostPerformanceTuner.MODE_PERFORMANCE || coreStrategy == CORE_STRATEGY_AUTO) {
            try { nativeSetManualCoreMask(0L, false); } catch (UnsatisfiedLinkError ignored) { }
            if (coreStrategy == CORE_STRATEGY_ADAPTIVE) adaptiveCoreState = "inactive in Balanced";
            DiagnosticLogger.i("HostPerf", "core strategy reason=" + reason + " strategy=" + coreStrategySummary() + " native=Android/ADPF full-affinity");
            return;
        }
        long mask = coreMaskForStrategy();
        boolean enabled = mask != 0L;
        try {
            nativeSetManualCoreMask(mask, enabled);
            DiagnosticLogger.i("HostPerf", "core strategy reason=" + reason + " strategy=" + coreStrategySummary() +
                    " mask=0x" + Long.toHexString(mask) + " freq={" + HostPerformanceTuner.coreMaskFrequencySummary(mask) + "}");
        } catch (UnsatisfiedLinkError e) {
            DiagnosticLogger.w(TAG, "Core strategy bridge not ready", e);
        }
    }

    private void updateAdaptiveCoreStrategy(int speedPercent) {
        if (coreStrategy != CORE_STRATEGY_ADAPTIVE || effectivePerformanceMode < HostPerformanceTuner.MODE_PERFORMANCE)
            return;
        if (hostPerformanceProfile == null) hostPerformanceProfile = HostPerformanceTuner.detect();
        int count = Math.min(64, Math.max(1, hostPerformanceProfile.cpuCount));
        long now = android.os.SystemClock.elapsedRealtime();
        long candidate = HostPerformanceTuner.bestLiveCoreMask(count, 2);
        if (candidate == 0L) {
            adaptiveCoreState = "live frequency unavailable";
            adaptiveCandidateMask = 0L;
            return;
        }
        if (adaptiveActiveMask == 0L) {
            adaptiveActiveMask = candidate;
            adaptiveLastSwitchMs = now;
            adaptiveCoreState = "initial " + HostPerformanceTuner.coreMaskFrequencySummary(candidate);
            applyCoreStrategy("adaptive-initial");
            return;
        }
        double currentKHz = HostPerformanceTuner.averageCurrentKHz(adaptiveActiveMask);
        double candidateKHz = HostPerformanceTuner.averageCurrentKHz(candidate);
        boolean speedUnderTarget = speedPercent >= 0 && speedPercent < 95;
        boolean candidateBetter = candidate != adaptiveActiveMask && currentKHz > 0.0 && candidateKHz >= currentKHz * 1.15;
        if (!speedUnderTarget || !candidateBetter) {
            adaptiveCandidateMask = 0L;
            adaptiveCandidateSinceMs = 0L;
            adaptiveCoreState = String.format(Locale.ROOT, "hold %s speed=%d%% avg=%.2fGHz",
                    maskToCpuList(adaptiveActiveMask), speedPercent, currentKHz / 1_000_000.0);
            return;
        }
        if (adaptiveCandidateMask != candidate) {
            adaptiveCandidateMask = candidate;
            adaptiveCandidateSinceMs = now;
            adaptiveCoreState = String.format(Locale.ROOT, "candidate %s %.2f→%.2fGHz",
                    maskToCpuList(candidate), currentKHz / 1_000_000.0, candidateKHz / 1_000_000.0);
            return;
        }
        long stableMs = now - adaptiveCandidateSinceMs;
        long sinceSwitchMs = now - adaptiveLastSwitchMs;
        if (stableMs >= ADAPTIVE_CANDIDATE_HOLD_MS && sinceSwitchMs >= ADAPTIVE_MIN_SWITCH_MS) {
            long old = adaptiveActiveMask;
            adaptiveActiveMask = candidate;
            adaptiveCandidateMask = 0L;
            adaptiveCandidateSinceMs = 0L;
            adaptiveLastSwitchMs = now;
            adaptiveCoreState = String.format(Locale.ROOT, "switched %s→%s %.2f→%.2fGHz speed=%d%%",
                    maskToCpuList(old), maskToCpuList(candidate), currentKHz / 1_000_000.0, candidateKHz / 1_000_000.0, speedPercent);
            DiagnosticLogger.i("HostPerf", "ADAPTIVE_SWITCH " + adaptiveCoreState);
            applyCoreStrategy("adaptive-switch");
        } else {
            adaptiveCoreState = String.format(Locale.ROOT, "waiting %s %.2f→%.2fGHz %ds/%ds",
                    maskToCpuList(candidate), currentKHz / 1_000_000.0, candidateKHz / 1_000_000.0,
                    stableMs / 1000L, ADAPTIVE_CANDIDATE_HOLD_MS / 1000L);
        }
    }

    private void showCoreStrategySettings() {
        final String[] labels = {
                "Auto (Android/ADPF decides) · default",
                "Prime only (2 fastest rated cores)",
                "Big cluster (next fast pair)",
                "Manual cores…",
                "Adaptive Anti-Throttle"
        };
        int checked = clamp(coreStrategy, CORE_STRATEGY_AUTO, CORE_STRATEGY_ADAPTIVE);
        new AlertDialog.Builder(this)
                .setTitle("Core strategy")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    coreStrategy = clamp(which, CORE_STRATEGY_AUTO, CORE_STRATEGY_ADAPTIVE);
                    prefs.edit().putInt("core_strategy", coreStrategy).apply();
                    adaptiveActiveMask = 0L;
                    adaptiveCandidateMask = 0L;
                    adaptiveCandidateSinceMs = 0L;
                    if (coreStrategy == CORE_STRATEGY_MANUAL) {
                        dialog.dismiss();
                        showManualCoreSettings();
                        return;
                    }
                    applyCoreStrategy("strategy-user");
                    updateSpeedPercentOverlay();
                    Toast.makeText(this, "Core strategy: " + coreStrategyName(coreStrategy), Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                })
                .setNegativeButton("CANCEL", null)
                .show();
    }

    private String manualCoreSummary() {
        if (!manualCoreSelectionEnabled || manualCoreMask == 0L)
            return "Auto";
        StringBuilder sb = new StringBuilder();
        for (int cpu = 0; cpu < 64; cpu++) {
            if ((manualCoreMask & (1L << cpu)) == 0L)
                continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(cpu);
        }
        return sb.length() == 0 ? "Auto" : "CPU " + sb;
    }

    private void showManualCoreSettings() {
        if (hostPerformanceProfile == null)
            hostPerformanceProfile = HostPerformanceTuner.detect();
        final int count = Math.min(64, Math.max(1, hostPerformanceProfile.cpuCount));
        final String[] labels = HostPerformanceTuner.buildCoreLabels(count);
        final boolean[] checked = new boolean[count];
        long seedMask = manualCoreMask;
        if (seedMask == 0L)
            seedMask = HostPerformanceTuner.recommendedCoreMask(count, 2);
        for (int cpu = 0; cpu < count; cpu++)
            checked[cpu] = (seedMask & (1L << cpu)) != 0L;

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Manual CPU cores")
                .setMultiChoiceItems(labels, checked, (d, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("USE SELECTED", null)
                .setNeutralButton("AUTO CORES", null)
                .setNegativeButton("CANCEL", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                long mask = 0L;
                for (int cpu = 0; cpu < count; cpu++)
                    if (checked[cpu]) mask |= (1L << cpu);
                if (mask == 0L) {
                    Toast.makeText(this, "Select at least one CPU core", Toast.LENGTH_SHORT).show();
                    return;
                }
                manualCoreMask = mask;
                manualCoreSelectionEnabled = true;
                coreStrategy = CORE_STRATEGY_MANUAL;
                prefs.edit().putLong("manual_core_mask", manualCoreMask)
                        .putBoolean("manual_core_selection_enabled", true)
                        .putInt("core_strategy", coreStrategy).apply();
                applyCoreStrategy("user-selected");
                updateSpeedPercentOverlay();
                Toast.makeText(this, "Manual cores: " + manualCoreSummary(), Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                manualCoreSelectionEnabled = false;
                coreStrategy = CORE_STRATEGY_AUTO;
                prefs.edit().putBoolean("manual_core_selection_enabled", false)
                        .putInt("core_strategy", coreStrategy).apply();
                applyCoreStrategy("user-auto");
                updateSpeedPercentOverlay();
                Toast.makeText(this, "Core selection: Auto", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
        });
        dialog.show();
    }

    private int safeNativeSpeedPercent() {
        try { return nativeGetEmulationSpeedPercent(); }
        catch (Throwable ignored) { return -1; }
    }

    private String safeNativeHostPerfState() {
        try {
            String s = nativeGetHostPerfState();
            return s == null || s.trim().isEmpty() ? "not available" : s;
        } catch (Throwable t) {
            return "not ready";
        }
    }

    private String safeNativeCpuCyclesState() {
        try { return nativeGetCpuCyclesState(); }
        catch (Throwable ignored) { return "Cycles diagnostics not ready"; }
    }

    private void applyCpuCyclesSettings() {
        // Set the parameter values BEFORE enabling the mode.
        postNativeControl(CTRL_CPU_CYCLES_FIXED_PERCENT, cpuCyclesFixedPercent);
        postNativeControl(CTRL_CPU_CYCLES_CUSTOM_KCPS, cpuCyclesCustomKcps);
        postNativeControl(CTRL_CPU_CYCLES_MODE, cpuCyclesMode);
    }

    private int safeNativeCpuThreadTid() {
        try { return nativeGetCpuThreadTid(); }
        catch (Throwable ignored) { return -1; }
    }

    private void ensureAdpfForNativeCpuThread(String reason) {
        int tid = safeNativeCpuThreadTid();
        HostPerformanceTuner.applyAdpf(this, effectivePerformanceMode, tid);
        if (!"overlay".equals(reason))
            DiagnosticLogger.i("HostPerf", "ADPF apply reason=" + reason + " nativeTid=" + tid +
                    " health=" + HostPerformanceTuner.runtimeHealth(this));
    }

    private void recordPerformanceHistory(int speedPercent, HostPerformanceTuner.FrequencySample frequency) {
        if (frequency == null) return;
        perfHistory.addLast(new PerfHistorySample(frequency.timestampMs, speedPercent, frequency.runningCpu,
                frequency.currentKHz, frequency.maxKHz));
        long cutoff = frequency.timestampMs - 130_000L;
        while (!perfHistory.isEmpty() && perfHistory.peekFirst().timeMs < cutoff)
            perfHistory.removeFirst();
    }

    private String perfWindowSummary(long windowMs) {
        if (perfHistory.isEmpty()) return "n/a";
        long now = perfHistory.peekLast().timeMs;
        long cutoff = now - windowMs;
        long speedSum = 0L;
        int speedCount = 0;
        double ratioSum = 0.0;
        int ratioCount = 0;
        long curSum = 0L;
        int curCount = 0;
        int lastCpu = -1;
        for (PerfHistorySample sample : perfHistory) {
            if (sample.timeMs < cutoff) continue;
            if (sample.speedPercent >= 0) {
                speedSum += sample.speedPercent;
                speedCount++;
            }
            if (sample.currentKHz > 0 && sample.maxKHz > 0) {
                ratioSum += sample.currentKHz * 100.0 / sample.maxKHz;
                ratioCount++;
                curSum += sample.currentKHz;
                curCount++;
            }
            if (sample.runningCpu >= 0) lastCpu = sample.runningCpu;
        }
        if (speedCount == 0) return "n/a";
        double speedAvg = speedSum / (double) speedCount;
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "%.1f%%", speedAvg));
        if (curCount > 0) sb.append(String.format(Locale.ROOT, " @%.2fGHz", (curSum / (double) curCount) / 1_000_000.0));
        if (ratioCount > 0) sb.append(String.format(Locale.ROOT, " (%.0f%%max)", ratioSum / ratioCount));
        if (lastCpu >= 0) sb.append(" cpu").append(lastCpu);
        sb.append(" n=").append(speedCount);
        return sb.toString();
    }

    private double perfWindowAverageSpeed(long windowMs) {
        if (perfHistory.isEmpty()) return -1.0;
        long now = perfHistory.peekLast().timeMs;
        long cutoff = now - windowMs;
        long sum = 0L;
        int count = 0;
        for (PerfHistorySample sample : perfHistory) {
            if (sample.timeMs < cutoff || sample.speedPercent < 0) continue;
            sum += sample.speedPercent;
            count++;
        }
        return count > 0 ? sum / (double) count : -1.0;
    }

    private double perfWindowAverageFreqRatio(long windowMs) {
        if (perfHistory.isEmpty()) return -1.0;
        long now = perfHistory.peekLast().timeMs;
        long cutoff = now - windowMs;
        double sum = 0.0;
        int count = 0;
        for (PerfHistorySample sample : perfHistory) {
            if (sample.timeMs < cutoff || sample.currentKHz <= 0 || sample.maxKHz <= 0) continue;
            sum += sample.currentKHz * 100.0 / sample.maxKHz;
            count++;
        }
        return count > 0 ? sum / count : -1.0;
    }

    private void updatePerformanceTrendDiagnosis() {
        double speed10 = perfWindowAverageSpeed(10_000L);
        double freq10 = perfWindowAverageFreqRatio(10_000L);
        if (speed10 > bestPerfWindowSpeed10) bestPerfWindowSpeed10 = speed10;
        if (freq10 > bestPerfWindowFreqRatio10) bestPerfWindowFreqRatio10 = freq10;
        if (speed10 < 0) {
            lastPerfTrendDiagnosis = "speed-unavailable";
            return;
        }
        boolean speedDropped = bestPerfWindowSpeed10 >= 95.0 && speed10 <= bestPerfWindowSpeed10 - 8.0;
        boolean freqDropped = freq10 >= 0.0 && bestPerfWindowFreqRatio10 >= 0.0 &&
                freq10 <= bestPerfWindowFreqRatio10 - 8.0;
        if (speedDropped && freqDropped) {
            lastPerfTrendDiagnosis = String.format(Locale.ROOT,
                    "CORRELATED_DROP speed %.1f→%.1f%% and host-freq-ratio %.0f→%.0f%%",
                    bestPerfWindowSpeed10, speed10, bestPerfWindowFreqRatio10, freq10);
        } else if (speedDropped && freq10 >= 0.0) {
            lastPerfTrendDiagnosis = String.format(Locale.ROOT,
                    "SPEED_DROP_WITH_STABLE_FREQ speed %.1f→%.1f%% host-freq-ratio %.0f%%",
                    bestPerfWindowSpeed10, speed10, freq10);
        } else if (speedDropped) {
            lastPerfTrendDiagnosis = String.format(Locale.ROOT,
                    "SPEED_DROP_FREQ_UNAVAILABLE speed %.1f→%.1f%%", bestPerfWindowSpeed10, speed10);
        } else {
            lastPerfTrendDiagnosis = "stable/no-correlated-drop";
        }
    }

    private void logPerformanceDiagnostics(String reason, int currentPercent) {
        long avg = speedDiagSamples > 0 ? Math.round(speedDiagSum / (double) speedDiagSamples) : -1L;
        int min = speedDiagSamples > 0 ? speedDiagMin : -1;
        int max = speedDiagSamples > 0 ? speedDiagMax : -1;
        if (lastFrequencySample == null || lastFrequencySample.allCoreSummary.isEmpty())
            lastFrequencySample = HostPerformanceTuner.sampleFrequencies(safeNativeCpuThreadTid(), true);
        String activeFrequency = lastFrequencySample == null ? "n/a" : lastFrequencySample.activeSummary();
        String allFrequencies = lastFrequencySample == null || lastFrequencySample.allCoreSummary.isEmpty() ?
                "n/a" : lastFrequencySample.allCoreSummary;
        DiagnosticLogger.i("PerfDiag", "reason=" + reason +
                " speed=" + currentPercent + "% avg=" + avg + "% min=" + min + "% max=" + max + "% samples=" + speedDiagSamples +
                " windows={10s:" + perfWindowSummary(10_000L) +
                ",30s:" + perfWindowSummary(30_000L) +
                ",60s:" + perfWindowSummary(60_000L) +
                ",120s:" + perfWindowSummary(120_000L) + "}" +
                " trend={" + lastPerfTrendDiagnosis + "}" +
                " activeHost={" + activeFrequency + "}" +
                " allCoreFreq=" + allFrequencies +
                " mode=" + HostPerformanceTuner.modeName(effectivePerformanceMode) +
                " cores=" + coreStrategySummary() +
                " voodoo=" + voodooThreadSettingSummary() +
                " health={" + HostPerformanceTuner.runtimeHealth(this) + "}" +
                " native={" + safeNativeHostPerfState() + "}" +
                " cycles={" + safeNativeCpuCyclesState() + "}");
    }

    private String voodooThreadSettingSummary() {
        SharedPreferences p = prefs != null ? prefs : getSharedPreferences("86box_android_ui", MODE_PRIVATE);
        int setting = p.getInt("voodoo_render_threads", 0);
        if (setting == 0) {
            HostPerformanceTuner.HostProfile hp = hostPerformanceProfile != null ? hostPerformanceProfile : HostPerformanceTuner.detect();
            return "Auto → " + HostPerformanceTuner.recommendedVoodooRenderThreads(hp);
        }
        return String.valueOf(setting);
    }

    private void showVoodooRenderThreadSettings() {
        final int[] values = {0, 1, 2, 4};
        final String[] labels = {"Auto (recommended)", "1 render thread", "2 render threads", "4 render threads"};
        SharedPreferences p = prefs != null ? prefs : getSharedPreferences("86box_android_ui", MODE_PRIVATE);
        int current = p.getInt("voodoo_render_threads", 0);
        int checked = 0;
        for (int i = 0; i < values.length; i++) if (values[i] == current) checked = i;
        new AlertDialog.Builder(this)
                .setTitle("Voodoo render threads")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    int value = values[which];
                    p.edit().putInt("voodoo_render_threads", value).apply();
                    DiagnosticLogger.i("HostPerf", "Voodoo render threads setting=" + (value == 0 ? "Auto" : value) +
                            " effective-next-start=" + voodooThreadSettingSummary());
                    Toast.makeText(this, "Voodoo threads: " + voodooThreadSettingSummary() + " · restart VM to apply", Toast.LENGTH_LONG).show();
                    dialog.dismiss();
                })
                .setNegativeButton("CANCEL", null)
                .show();
    }

    private void applyVoodooRenderThreadSettingBeforeLaunch(File profile) {
        SharedPreferences p = getSharedPreferences("86box_android_ui", MODE_PRIVATE);
        int setting = p.getInt("voodoo_render_threads", 0);
        try {
            applyVoodooRenderThreadSetting(profile, setting, "launch");
        } catch (Throwable t) {
            DiagnosticLogger.w("HostPerf", "Could not apply Voodoo render threads before launch", t);
        }
    }

    private void applyVoodooRenderThreadSetting(File profile, int setting, String reason) throws IOException {
        if (profile == null) return;
        File cfgFile = new File(profile, "86box.cfg");
        if (!cfgFile.isFile()) return;
        IniConfig cfg = IniConfig.load(cfgFile);
        String gfx = cfg.get("Video", "gfxcard", "").toLowerCase(Locale.ROOT);
        if (!gfx.contains("voodoo")) {
            DiagnosticLogger.i("HostPerf", "Voodoo tuning skipped reason=" + reason + " gfx=" + gfx);
            return;
        }
        HostPerformanceTuner.HostProfile hp = hostPerformanceProfile != null ? hostPerformanceProfile : HostPerformanceTuner.detect();
        int threads = setting == 0 ? HostPerformanceTuner.recommendedVoodooRenderThreads(hp) : setting;
        if (threads != 1 && threads != 2 && threads != 4) threads = 2;
        String old = cfg.get("3Dfx Voodoo Graphics", "render_threads", "2");
        cfg.set("3Dfx Voodoo Graphics", "render_threads", String.valueOf(threads));
        cfg.save(cfgFile);
        DiagnosticLogger.i("HostPerf", "Voodoo render threads reason=" + reason + " setting=" + setting +
                " old=" + old + " new=" + threads + " gfx=" + gfx);
    }

    private void showPerformanceSettings() {
        if (hostPerformanceProfile == null)
            hostPerformanceProfile = HostPerformanceTuner.detect();

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(12), dp(18), dp(14));
        scroll.addView(panel, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView detected = new TextView(this);
        detected.setText("Detected host:\n" + HostPerformanceTuner.describe(hostPerformanceProfile) +
                "\n\nAuto currently resolves to: " +
                HostPerformanceTuner.modeName(HostPerformanceTuner.resolveMode(HostPerformanceTuner.MODE_AUTO, hostPerformanceProfile)) +
                "\nCore strategy: " + coreStrategySummary() +
                "\nVoodoo threads: " + voodooThreadSettingSummary() +
                "\n" + HostPerformanceTuner.runtimeHealth(this) +
                "\nHost frequency: " + HostPerformanceTuner.frequencyState(safeNativeCpuThreadTid()) +
                "\nTrend: " + lastPerfTrendDiagnosis +
                "\nNative CPU: " + safeNativeHostPerfState() +
                "\nCPU cycles: " + safeNativeCpuCyclesState());
        panel.addView(detected);

        Button strategyButton = new Button(this);
        strategyButton.setText("CORE STRATEGY…");
        strategyButton.setAllCaps(false);
        strategyButton.setOnClickListener(v -> showCoreStrategySettings());
        panel.addView(strategyButton);

        Button coreButton = new Button(this);
        coreButton.setText("MANUAL CORES…");
        coreButton.setAllCaps(false);
        coreButton.setOnClickListener(v -> showManualCoreSettings());
        panel.addView(coreButton);

        Button voodooButton = new Button(this);
        voodooButton.setText("VOODOO RENDER THREADS…");
        voodooButton.setAllCaps(false);
        voodooButton.setOnClickListener(v -> showVoodooRenderThreadSettings());
        panel.addView(voodooButton);

        TextView cyclesTitle = new TextView(this);
        cyclesTitle.setText("CPU Cycles – original 86Box CPU core");
        cyclesTitle.setTextSize(18f);
        cyclesTitle.setPadding(0, dp(12), 0, dp(6));
        panel.addView(cyclesTitle);

        final String[] cycleNames = {
                "Original 86Box (default)", "Fixed (%)", "Auto (adaptive)",
                "Max (full profile budget)", "Custom (kcycles/second)"};
        android.widget.RadioGroup cycleModes = new android.widget.RadioGroup(this);
        cycleModes.setOrientation(android.widget.RadioGroup.VERTICAL);
        for (int i = 0; i < cycleNames.length; i++) {
            android.widget.RadioButton option = new android.widget.RadioButton(this);
            option.setId(View.generateViewId());
            option.setTag(Integer.valueOf(i));
            option.setText(cycleNames[i]);
            option.setChecked(cpuCyclesMode == i);
            option.setOnClickListener(v -> {
                cpuCyclesMode = (Integer) v.getTag();
                prefs.edit().putInt("clean_cpu_cycles_mode", cpuCyclesMode).apply();
                applyCpuCyclesSettings();
                DiagnosticLogger.i("CPUCycles", "mode=" + cpuCyclesMode);
            });
            cycleModes.addView(option);
        }
        panel.addView(cycleModes);

        TextView fixedLabel = new TextView(this);
        fixedLabel.setText("Fixed budget: " + cpuCyclesFixedPercent + "% of configured CPU");
        panel.addView(fixedLabel);
        SeekBar fixedControl = new SeekBar(this);
        fixedControl.setMax(75);
        fixedControl.setProgress(cpuCyclesFixedPercent - 25);
        fixedControl.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onStartTrackingTouch(SeekBar b) {}
            public void onStopTrackingTouch(SeekBar b) {}
            public void onProgressChanged(SeekBar b, int progress, boolean fromUser) {
                if (!fromUser) return;
                cpuCyclesFixedPercent = 25 + progress;
                fixedLabel.setText("Fixed budget: " + cpuCyclesFixedPercent + "% of configured CPU");
                prefs.edit().putInt("clean_cpu_cycles_fixed_percent", cpuCyclesFixedPercent).apply();
                postNativeControl(CTRL_CPU_CYCLES_FIXED_PERCENT, cpuCyclesFixedPercent);
            }
        });
        panel.addView(fixedControl);

        TextView customLabel = new TextView(this);
        customLabel.setText("Custom cap: " + cpuCyclesCustomKcps + " kcycles/second");
        panel.addView(customLabel);
        SeekBar customControl = new SeekBar(this);
        customControl.setMax(499);
        customControl.setProgress(Math.max(0, Math.min(499, cpuCyclesCustomKcps / 1000 - 1)));
        customControl.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onStartTrackingTouch(SeekBar b) {}
            public void onStopTrackingTouch(SeekBar b) {}
            public void onProgressChanged(SeekBar b, int progress, boolean fromUser) {
                if (!fromUser) return;
                cpuCyclesCustomKcps = (progress + 1) * 1000;
                customLabel.setText("Custom cap: " + cpuCyclesCustomKcps + " kcycles/second");
                prefs.edit().putInt("clean_cpu_cycles_custom_kcps", cpuCyclesCustomKcps).apply();
                postNativeControl(CTRL_CPU_CYCLES_CUSTOM_KCPS, cpuCyclesCustomKcps);
            }
        });
        panel.addView(customControl);

        TextView cycleInfo = new TextView(this);
        cycleInfo.setPadding(0, dp(6), 0, dp(8));
        cycleInfo.setText("Original and Max use the unmodified 86Box CPU budget. " +
                "Fixed/Custom limit scheduler CPU cycles; Auto responds slowly to observed " +
                "emulation speed. This is NOT an additional CPU core or a CPU overclock. " +
                "Lower values can change Windows 98 timing. Previous experiments A–F are removed.");
        panel.addView(cycleInfo);

        Button cycleDiag = new Button(this);
        cycleDiag.setAllCaps(false);
        cycleDiag.setText("SHOW CPU CYCLES DIAGNOSTICS");
        cycleDiag.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("86Box original CPU – Cycles")
                .setMessage(safeNativeCpuCyclesState())
                .setPositiveButton("OK", null).show());
        panel.addView(cycleDiag);

        Button refreshDiag = new Button(this);
        refreshDiag.setText("REFRESH PERFORMANCE DIAGNOSTICS");
        refreshDiag.setAllCaps(false);
        refreshDiag.setOnClickListener(v -> {
            ensureAdpfForNativeCpuThread("manual-refresh");
            logPerformanceDiagnostics("manual-refresh", safeNativeSpeedPercent());
            detected.setText("Detected host:\n" + HostPerformanceTuner.describe(hostPerformanceProfile) +
                    "\n\nAuto currently resolves to: " +
                    HostPerformanceTuner.modeName(HostPerformanceTuner.resolveMode(HostPerformanceTuner.MODE_AUTO, hostPerformanceProfile)) +
                    "\nCore strategy: " + coreStrategySummary() +
                    "\nVoodoo threads: " + voodooThreadSettingSummary() +
                    "\n" + HostPerformanceTuner.runtimeHealth(this) +
                    "\nHost frequency: " + HostPerformanceTuner.frequencyState(safeNativeCpuThreadTid()) +
                    "\nTrend: " + lastPerfTrendDiagnosis +
                    "\nNative CPU: " + safeNativeHostPerfState() +
                "\nCPU cycles: " + safeNativeCpuCyclesState());
        });
        panel.addView(refreshDiag);

        TextView hint = new TextView(this);
        hint.setPadding(0, dp(10), 0, dp(8));
        hint.setText("v0.9.28 makes Auto truly Android/ADPF-controlled: Performance/Maximum keep priority and ADPF hints but no longer pin the guest CPU thread to two host cores. The experimental Multi-Core CPU workers and profilers have been removed. Voodoo render threads remain Auto/1/2/4 and apply on the next VM start.");
        panel.addView(hint);

        TextView modeLabel = new TextView(this);
        modeLabel.setPadding(0, dp(6), 0, dp(4));
        modeLabel.setText("Performance mode:");
        panel.addView(modeLabel);

        String[] choices = {"Auto (recommended)", "Balanced", "Performance", "Maximum"};
        android.widget.RadioGroup modes = new android.widget.RadioGroup(this);
        modes.setOrientation(android.widget.RadioGroup.VERTICAL);
        android.widget.RadioButton[] modeButtons = new android.widget.RadioButton[choices.length];
        for (int i = 0; i < choices.length; ++i) {
            android.widget.RadioButton button = new android.widget.RadioButton(this);
            button.setText(choices[i]);
            button.setId(View.generateViewId());
            button.setPadding(0, dp(3), 0, dp(3));
            modeButtons[i] = button;
            modes.addView(button, new android.widget.RadioGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        int checked = clamp(performanceMode, HostPerformanceTuner.MODE_AUTO, HostPerformanceTuner.MODE_MAXIMUM);
        modeButtons[checked].setChecked(true);
        panel.addView(modes);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Host performance")
                .setView(scroll)
                .setNegativeButton("CLOSE", null)
                .create();

        for (int i = 0; i < modeButtons.length; ++i) {
            final int which = i;
            modeButtons[i].setOnClickListener(v -> {
                performanceMode = clamp(which, HostPerformanceTuner.MODE_AUTO, HostPerformanceTuner.MODE_MAXIMUM);
                prefs.edit().putInt("performance_mode", performanceMode).apply();
                applyPerformanceMode("user");
                Toast.makeText(this, "Performance: " + HostPerformanceTuner.modeName(performanceMode) +
                        " → " + HostPerformanceTuner.modeName(effectivePerformanceMode), Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
        }
        dialog.show();
    }

    private void togglePerformanceProfiler() {
        try {
            if (!perfProfileEnabled) {
                nativeControl(CTRL_PERF_PROFILE, 1);
                perfProfileEnabled = true;
                DiagnosticLogger.i(TAG, "Performance profiler started");
                Toast.makeText(this, "Performance profiler started", Toast.LENGTH_SHORT).show();
                return;
            }

            String summary = nativeGetPerfSummary();
            nativeControl(CTRL_PERF_PROFILE, 0);
            perfProfileEnabled = false;
            DiagnosticLogger.i(TAG, "Performance profiler stopped\n" + summary);
            if (summary == null || summary.trim().isEmpty())
                summary = "No profiler data available.";

            new AlertDialog.Builder(this)
                    .setTitle("86Box performance")
                    .setMessage(summary)
                    .setPositiveButton("OK", null)
                    .show();
        } catch (UnsatisfiedLinkError e) {
            perfProfileEnabled = false;
            DiagnosticLogger.w(TAG, "Performance profiler bridge not ready", e);
            Toast.makeText(this, "Profiler not ready yet", Toast.LENGTH_SHORT).show();
        }
    }

    private Button addToolbarButton(String text, String description, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(text.length() > 2 ? 9 : 14);
        button.setTextColor(Color.WHITE);
        button.setContentDescription(description);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setPadding(0, 0, 0, 0);
        button.setBackgroundColor(0x88404040);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(58), dp(40));
        lp.bottomMargin = dp(3);
        toolbar.addView(button, lp);
        return button;
    }

    private void setToolbarHidden(boolean hidden) {
        toolbarScroll.setVisibility(hidden ? View.GONE : View.VISIBLE);
        restoreToolbarButton.setVisibility(hidden ? View.VISIBLE : View.GONE);
        if (joystick != null && hidden) {
            joystick.setVisibility(View.GONE);
        } else if (joystick != null && prefs.getBoolean("joystick", true)) {
            joystick.setVisibility(View.VISIBLE);
        }
        setImmersiveMode();
    }

    private void createFloatingKeyOverlay() {
        if (overlayRoot == null || floatingKeyOverlay != null) return;
        floatingKeyOverlay = new FloatingKeyOverlayView(this, (xtCode, down) -> {
            try { nativeHardwareKey(xtCode, down); }
            catch (UnsatisfiedLinkError e) { DiagnosticLogger.w(TAG, "Floating key overlay bridge not ready", e); }
        }, prefs, germanKeyboard);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START | Gravity.TOP);
        overlayRoot.addView(floatingKeyOverlay, lp);
        DiagnosticLogger.i(TAG, "Floating game key overlay created");
    }

    private void showFloatingKeyOverlaySettings() {
        cancelIndependentTouchRouting("open-game-key-settings");
        releaseFloatingKeyOverlayKeys();
        if (floatingKeyOverlay == null) createFloatingKeyOverlay();
        if (floatingKeyOverlay != null) floatingKeyOverlay.showConfigurationDialog();
    }

    private void releaseFloatingKeyOverlayKeys() {
        if (floatingKeyOverlay != null) floatingKeyOverlay.releaseAll();
    }

    private void toggleVirtualKeyboard() {
        cancelIndependentTouchRouting("toggle-virtual-keyboard");
        if (virtualKeyboard == null) createVirtualKeyboard();
        if (virtualKeyboard == null) return;
        if (virtualKeyboardVisible) {
            hideVirtualKeyboard();
        } else {
            virtualKeyboard.setGermanLayout(germanKeyboard);
            virtualKeyboard.setVisibility(View.VISIBLE);
            virtualKeyboardVisible = true;
            if (joystick != null) joystick.setVisibility(View.GONE);
            DiagnosticLogger.i(TAG, "Virtual PC keyboard shown layout=" + (germanKeyboard ? "DE" : "US"));
        }
    }

    private void createVirtualKeyboard() {
        if (overlayRoot == null || virtualKeyboard != null) return;
        virtualKeyboard = new VirtualPcKeyboardView(this, new VirtualPcKeyboardView.Listener() {
            @Override public void onKey(int xtCode, boolean down) {
                try { nativeHardwareKey(xtCode, down); }
                catch (UnsatisfiedLinkError e) { DiagnosticLogger.w(TAG, "Virtual keyboard bridge not ready", e); }
            }
            @Override public void onClose() { hideVirtualKeyboard(); }
            @Override public void onOpenSystemKeyboard() { hideVirtualKeyboard(); showAndroidKeyboard(); }
        }, germanKeyboard, prefs);
        FrameLayout.LayoutParams lp = buildVirtualKeyboardLayoutParams();
        virtualKeyboard.setVisibility(View.GONE);
        overlayRoot.addView(virtualKeyboard, lp);
        virtualKeyboard.refreshForScreen();
    }


    private FrameLayout.LayoutParams buildVirtualKeyboardLayoutParams() {
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int minHeight = Math.round(180f * getResources().getDisplayMetrics().density);
        int maxHeight = Math.max(minHeight, screenHeight - dp(8));
        int keyboardHeight = Math.max(minHeight, Math.min(maxHeight, Math.round(screenHeight * 0.78f)));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, keyboardHeight, Gravity.START | Gravity.BOTTOM);
        lp.leftMargin = dp(4);
        lp.rightMargin = dp(4);
        lp.bottomMargin = dp(4);
        return lp;
    }

    private void updateVirtualKeyboardLayout() {
        if (virtualKeyboard == null) return;
        virtualKeyboard.setLayoutParams(buildVirtualKeyboardLayoutParams());
        virtualKeyboard.refreshForScreen();
        virtualKeyboard.requestLayout();
        DiagnosticLogger.i(TAG, "Virtual PC keyboard screen-fit refreshed scale=" +
                virtualKeyboard.getUserScalePercent() + "% screen=" +
                getResources().getConfiguration().screenWidthDp + "x" +
                getResources().getConfiguration().screenHeightDp + "dp");
    }

    private void hideVirtualKeyboard() {
        cancelIndependentTouchRouting("hide-virtual-keyboard");
        if (virtualKeyboard != null) {
            virtualKeyboard.releaseAll();
            virtualKeyboard.setVisibility(View.GONE);
        }
        virtualKeyboardVisible = false;
        if (joystick != null) joystick.setVisibility(prefs.getBoolean("joystick", true) ? View.VISIBLE : View.GONE);
        setImmersiveMode();
    }

    private void releaseVirtualKeyboardKeys() {
        if (virtualKeyboard != null) virtualKeyboard.releaseAll();
    }

    private void showAndroidKeyboard() {
        // Tell SDL to enable text input first.  Android IMEs often commit text
        // instead of producing hardware-style key-down/key-up events.
        postNativeControl(CTRL_TEXT_INPUT_START, 1);
        getWindow().getDecorView().postDelayed(() -> {
            try {
                // InputType.TYPE_CLASS_TEXT == 1; keep this wrapper independent of extra widgets.
                if (!SDLActivity.showTextInput(1, 0, 0, 1, 1)) {
                    View focus = getWindow().getDecorView();
                    focus.setFocusableInTouchMode(true);
                    focus.requestFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.showSoftInput(focus, InputMethodManager.SHOW_IMPLICIT);
                }
            } catch (Throwable t) {
                DiagnosticLogger.w(TAG, "SDL keyboard request failed", t);
                Toast.makeText(this, "Keyboard could not be opened", Toast.LENGTH_SHORT).show();
            }
        }, 50);
    }

    private void confirmHardReset() {
        new AlertDialog.Builder(this)
                .setTitle("86Box Hard Reset")
                .setMessage("Reset the emulated PC now? Unsaved guest data can be lost.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Reset", (d, w) -> postNativeControl(CTRL_HARD_RESET, 0))
                .show();
    }

    private void showTouchSettings() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(18);
        panel.setPadding(pad, pad, pad, pad);

        TextView rendererLabel = new TextView(this);
        rendererLabel.setText("Renderer: " + getRendererInfoSafe() + " (Vulkan preferred, automatic fallback)");
        panel.addView(rendererLabel);

        TextView networkLabel = new TextView(this);
        networkLabel.setText("Network: libslirp user-mode NAT is built in (select SLiRP in the 86Box machine configuration)");
        panel.addView(networkLabel);

        CheckBox emuSpeed = new CheckBox(this);
        emuSpeed.setText("Show emulation speed (%)");
        emuSpeed.setChecked(speedPercentVisible);
        emuSpeed.setOnCheckedChangeListener((button, checked) -> {
            speedPercentVisible = checked;
            prefs.edit().putBoolean("show_speed_percent", checked).apply();
            updateSpeedPercentOverlay();
            startSpeedPercentUpdater(); // diagnostics/adaptive anti-throttle must keep running even when hidden
        });
        panel.addView(emuSpeed);

        TextView emuSpeedHint = new TextView(this);
        emuSpeedHint.setText("Shows the real 86Box core speed value (100% = target emulation speed). Drag the percentage box directly to place it anywhere on screen.");
        panel.addView(emuSpeedHint);

        TextView overlaySizeLabel = new TextView(this);
        overlaySizeLabel.setText("Speed overlay size: " + speedOverlaySizePercent + "%");
        panel.addView(overlaySizeLabel);

        SeekBar overlaySize = new SeekBar(this);
        overlaySize.setMax(100);
        overlaySize.setProgress(speedOverlaySizePercent - 60);
        panel.addView(overlaySize);
        overlaySize.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                speedOverlaySizePercent = progress + 60;
                overlaySizeLabel.setText("Speed overlay size: " + speedOverlaySizePercent + "%");
                prefs.edit().putInt("speed_overlay_size_percent", speedOverlaySizePercent).apply();
                applySpeedOverlayStyle();
                if (overlayRoot != null) overlayRoot.post(Box86Activity.this::clampAndPersistSpeedOverlayPosition);
            }
        });

        TextView overlayOpacityLabel = new TextView(this);
        overlayOpacityLabel.setText("Speed overlay background opacity: " + speedOverlayOpacityPercent + "%");
        panel.addView(overlayOpacityLabel);

        SeekBar overlayOpacity = new SeekBar(this);
        overlayOpacity.setMax(80);
        overlayOpacity.setProgress(speedOverlayOpacityPercent - 20);
        panel.addView(overlayOpacity);
        overlayOpacity.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                speedOverlayOpacityPercent = progress + 20;
                overlayOpacityLabel.setText("Speed overlay background opacity: " + speedOverlayOpacityPercent + "%");
                prefs.edit().putInt("speed_overlay_opacity_percent", speedOverlayOpacityPercent).apply();
                applySpeedOverlayStyle();
            }
        });

        Button resetOverlay = new Button(this);
        resetOverlay.setText("Reset speed overlay position");
        resetOverlay.setOnClickListener(v -> {
            speedOverlayPositionCustom = false;
            speedOverlayXPermille = 0;
            speedOverlayYPermille = 0;
            prefs.edit()
                    .putBoolean("speed_overlay_position_custom", false)
                    .putInt("speed_overlay_x_permille", 0)
                    .putInt("speed_overlay_y_permille", 0)
                    .apply();
            applySpeedOverlayPosition();
        });
        panel.addView(resetOverlay);

        TextView speedLabel = new TextView(this);
        speedLabel.setText("Mouse speed (touch + physical): " + mouseSpeed + "%");
        panel.addView(speedLabel);

        SeekBar speed = new SeekBar(this);
        speed.setMax(275);
        speed.setProgress(mouseSpeed - 25);
        panel.addView(speed);
        speed.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mouseSpeed = progress + 25;
                speedLabel.setText("Mouse speed (touch + physical): " + mouseSpeed + "%");
                prefs.edit().putInt("mouse_speed", mouseSpeed).apply();
                postNativeControl(CTRL_MOUSE_SPEED, mouseSpeed);
            }
        });

        TextView zoomLabel = new TextView(this);
        zoomLabel.setText("Display zoom: " + zoomPercent + "% (50-300%, pinch zoom + two-finger pan)");
        panel.addView(zoomLabel);

        SeekBar zoom = new SeekBar(this);
        zoom.setMax(250);
        zoom.setProgress(zoomPercent - 50);
        panel.addView(zoom);
        zoom.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                zoomPercent = progress + 50;
                zoomLabel.setText("Display zoom: " + zoomPercent + "% (50-300%, pinch zoom + two-finger pan)");
                prefs.edit().putInt("zoom", zoomPercent).apply();
                postNativeControl(CTRL_ZOOM, zoomPercent);
            }
        });

        CheckBox tap = new CheckBox(this);
        tap.setText("Touchpad gestures: swipe = move; tap = left click; double tap = double click; hold ~0.35s then drag = hold left button (move icons / resize windows); hold 2 seconds = right click; two-finger tap = right click");
        tap.setChecked(prefs.getBoolean("tap_to_click", true));
        tap.setOnCheckedChangeListener((button, checked) -> {
            prefs.edit().putBoolean("tap_to_click", checked).apply();
            postNativeControl(CTRL_TAP_TO_CLICK, checked ? 1 : 0);
        });
        panel.addView(tap);

        CheckBox joy = new CheckBox(this);
        joy.setText("Virtual mouse joystick");
        joy.setChecked(prefs.getBoolean("joystick", true));
        joy.setOnCheckedChangeListener((button, checked) -> {
            prefs.edit().putBoolean("joystick", checked).apply();
            if (joystick != null) joystick.setVisibility(checked ? View.VISIBLE : View.GONE);
        });
        panel.addView(joy);

        CheckBox german = new CheckBox(this);
        german.setText("German guest keyboard (QWERTZ: ä ö ü ß € / AltGr)");
        german.setChecked(germanKeyboard);
        german.setOnCheckedChangeListener((button, checked) -> {
            germanKeyboard = checked;
            prefs.edit().putBoolean("keyboard_de", checked).apply();
            postNativeControl(CTRL_KEYBOARD_LAYOUT, checked ? 1 : 0);
            if (virtualKeyboard != null) virtualKeyboard.setGermanLayout(checked);
            if (floatingKeyOverlay != null) floatingKeyOverlay.setGermanLayout(checked);
        });
        panel.addView(german);

        Button systemKeyboard = new Button(this);
        systemKeyboard.setText("Open Android system keyboard");
        systemKeyboard.setOnClickListener(v -> showAndroidKeyboard());
        panel.addView(systemKeyboard);

        CheckBox hardware = new CheckBox(this);
        hardware.setText("Physical USB/Bluetooth keyboard + mouse (direct PC input)");
        hardware.setChecked(hardwareInputEnabled);
        hardware.setOnCheckedChangeListener((button, checked) -> {
            hardwareInputEnabled = checked;
            prefs.edit().putBoolean("hardware_input", checked).apply();
            if (!checked) {
                releaseHardwareInput();
                setHardwareMouseCaptured(false);
            }
        });
        panel.addView(hardware);

        TextView hardwareHint = new TextView(this);
        hardwareHint.setText("Physical mouse: click the emulator area or use MOUSE to lock the pointer. Ctrl+Alt+G releases it. Mouse wheel, middle/right and X1/X2 buttons are supported.");
        panel.addView(hardwareHint);

        Button refreshRenderer = new Button(this);
        refreshRenderer.setText("Refresh renderer info");
        refreshRenderer.setOnClickListener(v -> rendererLabel.setText(
                "Renderer: " + getRendererInfoSafe() + " (Vulkan preferred, automatic fallback)"));
        panel.addView(refreshRenderer);

        Button resetZoom = new Button(this);
        resetZoom.setText("Reset zoom + pan to 100% / center");
        resetZoom.setOnClickListener(v -> {
            zoomPercent = 100;
            zoom.setProgress(50);
            prefs.edit().putInt("zoom", 100).apply();
            postNativeControl(CTRL_ZOOM_RESET, 0);
        });
        panel.addView(resetZoom);

        ScrollView settingsScroll = new ScrollView(this);
        settingsScroll.setFillViewport(false);
        settingsScroll.setVerticalScrollBarEnabled(true);
        settingsScroll.setScrollbarFadingEnabled(false);
        settingsScroll.addView(panel, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
                .setTitle("86Box Android input controls")
                .setView(settingsScroll)
                .setPositiveButton("OK", null)
                .show();
    }

    private void showDisplaySettings() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(14);
        panel.setPadding(pad, pad, pad, pad);

        TextView autoHint = new TextView(this);
        autoHint.setText("Auto Fit adapts the VM picture to phones, tablets and desktop mode without stretching the guest aspect ratio.");
        panel.addView(autoHint);

        TextView modeLabel = new TextView(this);
        modeLabel.setText("VM image scaling");
        panel.addView(modeLabel);
        Spinner mode = new Spinner(this);
        String[] modes = {"Stretch to screen", "Force 4:3", "Auto Fit / keep guest ratio", "Integer scale", "Integer 4:3"};
        ArrayAdapter<String> modeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, modes);
        mode.setAdapter(modeAdapter);
        mode.setSelection(displayScaleMode);
        panel.addView(mode, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        mode.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                int next = clamp(position, 0, 4);
                if (next == displayScaleMode) return;
                displayScaleMode = next;
                prefs.edit().putInt("display_scale_mode", displayScaleMode).apply();
                postNativeControl(CTRL_DISPLAY_SCALE_MODE, displayScaleMode);
                DiagnosticLogger.i(TAG, "Display scale mode -> " + displayScaleModeName(displayScaleMode));
            }
        });

        TextView zoomLabel = new TextView(this);
        zoomLabel.setPadding(0, dp(8), 0, 0);
        zoomLabel.setText("VM image size: " + zoomPercent + "%");
        panel.addView(zoomLabel);
        SeekBar zoom = new SeekBar(this);
        zoom.setMax(250);
        zoom.setProgress(zoomPercent - 50);
        panel.addView(zoom);
        zoom.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                zoomPercent = progress + 50;
                zoomLabel.setText("VM image size: " + zoomPercent + "%");
                prefs.edit().putInt("zoom", zoomPercent).apply();
                postNativeControl(CTRL_ZOOM, zoomPercent);
            }
        });

        Button auto = new Button(this);
        auto.setText("AUTO FIT + 100%");
        auto.setOnClickListener(v -> {
            displayScaleMode = 2;
            zoomPercent = 100;
            mode.setSelection(2);
            zoom.setProgress(50);
            prefs.edit().putInt("display_scale_mode", 2).putInt("zoom", 100).apply();
            postNativeControl(CTRL_DISPLAY_SCALE_MODE, 2);
            postNativeControl(CTRL_ZOOM_RESET, 0);
            zoomLabel.setText("VM image size: 100%");
        });
        panel.addView(auto);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.setScrollbarFadingEnabled(false);
        scroll.addView(panel, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this)
                .setTitle("VM display")
                .setView(scroll)
                .setPositiveButton("CLOSE", null)
                .show();
    }

    private static String displayScaleModeName(int mode) {
        switch (mode) {
            case 0: return "stretch";
            case 1: return "4:3";
            case 2: return "auto-fit";
            case 3: return "integer";
            case 4: return "integer-4:3";
            default: return "auto-fit";
        }
    }

    private void showDiagnosticLogs() {
        TextView text = new TextView(this);
        text.setText(DiagnosticLogger.tailCurrentLogs(240));
        text.setTextSize(11);
        text.setTextIsSelectable(true);
        text.setPadding(dp(12), dp(8), dp(12), dp(8));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(text, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
                .setTitle("86Box diagnostic log")
                .setView(scroll)
                .setNeutralButton("EXPORT ZIP", (d, w) -> exportDiagnosticZip())
                .setPositiveButton("CLOSE", null)
                .show();
    }

    private void exportDiagnosticZip() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/zip");
        String profileName = getIntent() == null ? "86Box" : getIntent().getStringExtra(ProfileManagerActivity.EXTRA_PROFILE_NAME);
        if (profileName == null || profileName.trim().isEmpty()) profileName = "86Box";
        i.putExtra(Intent.EXTRA_TITLE, sanitizeFileName(profileName) + "-diagnostics.zip");
        launchExternalPicker(i, REQ_EXPORT_DIAGNOSTICS, "diagnostics export");
    }

    private void openMediaPicker(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        if (requestCode == REQ_CDROM) {
            // CUE sheets frequently reference one or more BIN/IMG audio/data files.
            // Android SAF can return all of them in one ClipData selection.
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }
        launchExternalPicker(intent, requestCode, requestCode == REQ_CDROM ? "CD-ROM" : "floppy");
    }

    private void launchExternalPicker(Intent intent, int requestCode, String label) {
        if (externalPickerActive) {
            DiagnosticLogger.w(TAG, "Ignoring duplicate external picker request=" + requestCode +
                    " activeRequest=" + externalPickerRequestCode);
            return;
        }
        beginExternalPickerSession(requestCode);
        DiagnosticLogger.i(TAG, "Preparing external picker label=" + label + " request=" + requestCode +
                " profile=" + getCurrentProfileDir().getAbsolutePath());
        View decor = getWindow() == null ? null : getWindow().getDecorView();
        Runnable launch = () -> {
            if (!externalPickerActive || externalPickerRequestCode != requestCode)
                return;
            try {
                DiagnosticLogger.i(TAG, "Launching external picker label=" + label + " request=" + requestCode);
                startActivityForResult(intent, requestCode);
            } catch (Throwable t) {
                endExternalPickerSession(requestCode, "launch-failed");
                DiagnosticLogger.e(TAG, "No Android file picker available request=" + requestCode, t);
                Toast.makeText(this, "No Android file picker available", Toast.LENGTH_LONG).show();
            }
        };
        /* Give the foreground service one main-loop turn to promote the process
           before DocumentsUI takes focus.  This closes the short OOM-priority
           race seen on the 512 MB OnePlus test VM. */
        if (decor != null) decor.postDelayed(launch, 160L);
        else launch.run();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        boolean pickerRequest = requestCode == REQ_FLOPPY || requestCode == REQ_CDROM || requestCode == REQ_EXPORT_DIAGNOSTICS;
        if (pickerRequest)
            endExternalPickerSession(requestCode, resultCode == RESULT_OK ? "result-ok" : "result-cancelled");
        DiagnosticLogger.i(TAG, "Activity result request=" + requestCode + " result=" + resultCode +
                " data=" + (data == null ? "null" : String.valueOf(data.getData())));
        setImmersiveMode();
        if (resultCode != RESULT_OK || data == null) {
            scheduleAutoCapturePhysicalMouse("picker-return-cancel");
            return;
        }

        if (requestCode == REQ_EXPORT_DIAGNOSTICS && data.getData() != null) {
            final Uri destination = data.getData();
            new Thread(() -> {
                try {
                    String profileName = getIntent() == null ? null : getIntent().getStringExtra(ProfileManagerActivity.EXTRA_PROFILE_NAME);
                    DiagnosticLogger.exportDiagnostics(this, getCurrentProfileDir(), profileName, destination);
                    DiagnosticLogger.i(TAG, "Diagnostics ZIP exported to SAF destination");
                    runOnUiThread(() -> Toast.makeText(this, "Diagnostic ZIP exported", Toast.LENGTH_LONG).show());
                } catch (Throwable e) {
                    DiagnosticLogger.e(TAG, "Diagnostics export failed", e);
                    runOnUiThread(() -> Toast.makeText(this, "Diagnostics export failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            }, "86Box-log-export").start();
            return;
        }

        final List<Uri> uris = collectUris(data);
        if (uris.isEmpty()) return;

        final String type;
        if (requestCode == REQ_FLOPPY) type = "floppy";
        else if (requestCode == REQ_CDROM) type = "cdrom";
        else return;

        Toast.makeText(this, "Importing " + type + (uris.size() > 1 ? " set" : " image") + "…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                if (requestCode == REQ_CDROM) {
                    File mount = importCdSelection(uris);
                    runOnUiThread(() -> {
                        mountNative(MEDIA_CDROM, mount);
                        Toast.makeText(this,
                                mount.getName().toLowerCase(Locale.ROOT).endsWith(".cue") ?
                                        "CUE/BIN CD set imported and mounted" : "CD-ROM image mounted",
                                Toast.LENGTH_SHORT).show();
                    });
                } else {
                    File local = copyUriIntoRuntime(uris.get(0), type);
                    runOnUiThread(() -> {
                        mountNative(MEDIA_FLOPPY, local);
                        Toast.makeText(this, "Floppy image mounted", Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                DiagnosticLogger.e(TAG, "Media import failed", e);
                runOnUiThread(() -> Toast.makeText(this,
                        "Media import failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }, "86Box-media-import").start();
    }

    private List<Uri> collectUris(Intent data) {
        Set<Uri> unique = new LinkedHashSet<>();
        Uri single = data.getData();
        if (single != null) unique.add(single);
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); ++i) {
                Uri uri = clip.getItemAt(i).getUri();
                if (uri != null) unique.add(uri);
            }
        }
        return new ArrayList<>(unique);
    }

    private File importCdSelection(List<Uri> uris) throws IOException {
        File base = new File(getCurrentProfileDir(), "media/cdrom");
        File session = new File(base, "set-" + System.currentTimeMillis());
        if (!session.mkdirs() && !session.isDirectory())
            throw new IOException("Could not create CD import directory");

        try {
            Map<String, String> selectedNames = new HashMap<>();
            List<File> copied = new ArrayList<>();
            File cue = null;
            int cueCount = 0;
            for (Uri uri : uris) {
                String original = queryDisplayName(uri);
                String safe = sanitizeFileName(original);
                if (safe.isEmpty()) safe = "cd-track.bin";
                File out = uniqueFile(session, safe);
                copyUriToFile(uri, out);
                copied.add(out);
                selectedNames.put(original.toLowerCase(Locale.ROOT), out.getName());
                selectedNames.put(baseName(original).toLowerCase(Locale.ROOT), out.getName());
                selectedNames.put(out.getName().toLowerCase(Locale.ROOT), out.getName());
                if (out.getName().toLowerCase(Locale.ROOT).endsWith(".cue")) {
                    ++cueCount;
                    if (cueCount > 1)
                        throw new IOException("Select exactly one .cue sheet per CD set");
                    cue = out;
                }
            }

            if (cue != null) {
                rewriteAndValidateCue(cue, selectedNames, session);
                return cue;
            }

            // A single ISO/CHD/BIN can be mounted directly. Multi-file selections
            // without a CUE are ambiguous and must not leave orphaned copied tracks.
            if (copied.size() == 1)
                return copied.get(0);
            throw new IOException("Multiple CD files selected but no .cue sheet was included");
        } catch (IOException | RuntimeException e) {
            deleteRecursive(session);
            throw e;
        }
    }

    private void rewriteAndValidateCue(File cue, Map<String, String> selectedNames, File session) throws IOException {
        if (cue.length() > MAX_CUE_BYTES)
            throw new IOException("CUE sheet is unexpectedly large (maximum 8 MB)");
        byte[] bytes = Files.readAllBytes(cue.toPath());
        String text = ProfilePortability.decodeImportedText(bytes);

        Pattern fileLine = Pattern.compile("(?i)^(\\s*FILE\\s+)(?:\\\"([^\\\"]+)\\\"|(\\S+))(\\s+.*)$");
        StringBuilder rewritten = new StringBuilder(text.length() + 128);
        List<String> missing = new ArrayList<>();
        String[] lines = text.split("\\r\\n|\\n|\\r", -1);
        for (String line : lines) {
            Matcher m = fileLine.matcher(line);
            if (m.matches()) {
                String reference = m.group(2) != null ? m.group(2) : m.group(3);
                String mapped = selectedNames.get(reference.toLowerCase(Locale.ROOT));
                if (mapped == null)
                    mapped = selectedNames.get(baseName(reference).toLowerCase(Locale.ROOT));
                if (mapped != null) {
                    line = m.group(1) + "\"" + mapped + "\"" + m.group(4);
                } else {
                    File expected = new File(session, baseName(reference));
                    if (!expected.isFile()) missing.add(reference);
                }
            }
            rewritten.append(line).append('\n');
        }
        if (!missing.isEmpty())
            throw new IOException("CUE references missing track file(s): " + String.join(", ", missing));
        Files.write(cue.toPath(), rewritten.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String baseName(String name) {
        String normalized = name.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        return slash >= 0 ? normalized.substring(slash + 1) : normalized;
    }

    private static File uniqueFile(File dir, String requestedName) {
        File candidate = new File(dir, requestedName);
        if (!candidate.exists()) return candidate;
        String name = requestedName;
        String stem = name;
        String ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            stem = name.substring(0, dot);
            ext = name.substring(dot);
        }
        for (int i = 2; ; ++i) {
            candidate = new File(dir, stem + "_" + i + ext);
            if (!candidate.exists()) return candidate;
        }
    }

    private void copyUriToFile(Uri uri, File out) throws IOException {
        File parent = out.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs())
            throw new IOException("Could not create media directory");

        File storageRoot = parent != null ? parent : getFilesDir();
        long usable = storageRoot.getUsableSpace();
        if (usable > 0 && usable <= MEDIA_IMPORT_FREE_RESERVE)
            throw new IOException("Not enough free app storage to import media");
        long maxCopyBytes = usable > 0 ? usable - MEDIA_IMPORT_FREE_RESERVE : 0L;
        long declaredSize = queryOpenableSize(uri);
        if (declaredSize > 0 && maxCopyBytes > 0 && declaredSize > maxCopyBytes)
            throw new IOException("Selected media is larger than the available app storage (64 MB reserve kept free)");

        boolean complete = false;
        try (InputStream input = getContentResolver().openInputStream(uri);
             OutputStream output = new FileOutputStream(out, false)) {
            if (input == null) throw new IOException("Could not open selected file");
            byte[] buffer = new byte[1024 * 1024];
            long copied = 0L;
            int count;
            while ((count = input.read(buffer)) >= 0) {
                if (count <= 0) continue;
                copied += count;
                if (maxCopyBytes > 0 && copied > maxCopyBytes)
                    throw new IOException("Media import would leave less than 64 MB free app storage");
                output.write(buffer, 0, count);
            }
            complete = true;
        } finally {
            if (!complete && out.exists() && !out.delete())
                DiagnosticLogger.w(TAG, "Could not remove partial import: " + out);
        }
    }

    private long queryOpenableSize(Uri uri) {
        try (Cursor cursor = getContentResolver().query(
                uri, new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (idx >= 0 && !cursor.isNull(idx))
                    return Math.max(-1L, cursor.getLong(idx));
            }
        } catch (Throwable ignored) { }
        return -1L;
    }

    private static void deleteRecursive(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursive(child);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    private File copyUriIntoRuntime(Uri uri, String type) throws IOException {
        File mediaDir = new File(getCurrentProfileDir(), "media/" + type);
        //noinspection ResultOfMethodCallIgnored
        mediaDir.mkdirs();
        String fileName = sanitizeFileName(queryDisplayName(uri));
        if (fileName.isEmpty()) fileName = type + "-image.bin";
        File out = uniqueFile(mediaDir, fileName);
        copyUriToFile(uri, out);
        return out;
    }

    private String queryDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    String name = cursor.getString(idx);
                    if (name != null) return name;
                }
            }
        } catch (Throwable ignored) {
        }
        String path = uri.getLastPathSegment();
        return path == null ? "" : path;
    }

    private static String sanitizeFileName(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
    }

    private void mountNative(int mediaType, File local) {
        try {
            nativeMountMedia(mediaType, local.getAbsolutePath());
        } catch (UnsatisfiedLinkError e) {
            DiagnosticLogger.e(TAG, "Native media bridge unavailable", e);
        }
    }

    private String getRendererInfoSafe() {
        try {
            String name = nativeGetRendererInfo();
            return name == null || name.isEmpty() ? "unknown" : name;
        } catch (UnsatisfiedLinkError e) {
            return "initializing";
        }
    }

    private void setLifecyclePause(boolean paused) {
        try {
            nativeLifecyclePause(paused);
        } catch (UnsatisfiedLinkError e) {
            // Very early lifecycle transitions can happen before libmain is ready.
            DiagnosticLogger.w(TAG, "Lifecycle pause bridge not ready", e);
        }
    }

    private void postNativeControl(int action, int value) {
        try {
            nativeControl(action, value);
        } catch (UnsatisfiedLinkError e) {
            DiagnosticLogger.w(TAG, "Native control bridge not ready: action=" + action, e);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (!hardwareInputEnabled || !isPhysicalKeyboardEvent(event) || isAndroidSystemKey(event.getKeyCode()))
            return super.dispatchKeyEvent(event);

        int xt = linuxScanCodeToXt(event.getScanCode());
        if (xt == 0)
            xt = androidKeyCodeToXt(event.getKeyCode());
        if (xt == 0)
            return super.dispatchKeyEvent(event);

        final int action = event.getAction();
        if (action != KeyEvent.ACTION_DOWN && action != KeyEvent.ACTION_UP)
            return super.dispatchKeyEvent(event);
        final boolean down = action == KeyEvent.ACTION_DOWN;
        if (down && event.getRepeatCount() > 0)
            return true; // The emulated keyboard/controller handles typematic repeat.

        // Same release chord used by desktop 86Box. Consume both halves of the G
        // keystroke; otherwise the guest would receive a Break without a Make.
        if (down && xt == 0x22 && hardwareMouseCaptured && physicalCtrlAltHeld()) {
            suppressMouseReleaseG = true;
            setHardwareMouseCaptured(false);
            return true;
        }
        if (!down && xt == 0x22 && suppressMouseReleaseG) {
            suppressMouseReleaseG = false;
            return true;
        }

        if (down) {
            if (hardwarePressedXt.add(xt))
                sendHardwareKey(xt, true);
        } else {
            hardwarePressedXt.remove(xt);
            sendHardwareKey(xt, false);
        }
        return true;
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (!hardwareInputEnabled || !isPhysicalMouseEvent(event))
            return super.dispatchGenericMotionEvent(event);
        traceHardwareMouseEvent("generic", event);
        if (!hardwareMouseCaptured) {
            int action = event.getActionMasked();
            boolean canCapture = action == MotionEvent.ACTION_BUTTON_PRESS ||
                    action == MotionEvent.ACTION_HOVER_MOVE || action == MotionEvent.ACTION_MOVE;
            if (canCapture && !pointIsOverAndroidOverlay(event.getX(), event.getY())) {
                setHardwareMouseCaptured(true);
                // The event which requested capture can already contain a real delta/click.
                handlePhysicalMouseMotion(event);
                handlePhysicalMouseButtons(event);
                handlePhysicalMouseWheel(event);
                return true;
            }
            return super.dispatchGenericMotionEvent(event);
        }

        handlePhysicalMouseMotion(event);
        handlePhysicalMouseButtons(event);
        handlePhysicalMouseWheel(event);
        return true;
    }

    // Touch latency diagnostics (introduced in Experiment 3): sampled touch path. A measurement-only
    // feature; it does not change the input coordinates, pointer IDs or gestures.
    // Clock domain: MotionEvent timestamps and SystemClock.uptimeMillis().
    private HandlerThread touchDiagLogThread;
    private Handler touchDiagLogHandler;
    private long touchDiagWindowStartMs;
    private long touchDiagLastEventTimeMs;
    private long touchDiagCount;
    private long touchDiagHistoryCount;
    private long touchDiagArrivalSumMs;
    private long touchDiagArrivalMaxMs;
    private long touchDiagDispatchSumUs;
    private long touchDiagDispatchMaxUs;
    private long touchDiagIntervalSumMs;
    private long touchDiagIntervalMaxMs;
    private long touchDiagIntervalCount;

    private void traceTouchLatency(MotionEvent event, long dispatchStartNs, long arrivalMs) {
        // Called only for touchscreen input. Roll up numbers every ten seconds;
        // no per-motion file I/O or allocation in the steady-state hot path.
        final long nowMs = SystemClock.uptimeMillis();
        if (touchDiagWindowStartMs == 0L) touchDiagWindowStartMs = nowMs;
        final long ageMs = Math.max(0L, arrivalMs - event.getEventTime());
        final long dispatchUs = Math.max(0L, (System.nanoTime() - dispatchStartNs) / 1_000L);
        touchDiagCount++;
        touchDiagHistoryCount += event.getHistorySize();
        touchDiagArrivalSumMs += ageMs;
        touchDiagArrivalMaxMs = Math.max(touchDiagArrivalMaxMs, ageMs);
        touchDiagDispatchSumUs += dispatchUs;
        touchDiagDispatchMaxUs = Math.max(touchDiagDispatchMaxUs, dispatchUs);
        if (touchDiagLastEventTimeMs > 0L && event.getEventTime() >= touchDiagLastEventTimeMs) {
            long interval = event.getEventTime() - touchDiagLastEventTimeMs;
            touchDiagIntervalSumMs += interval;
            touchDiagIntervalMaxMs = Math.max(touchDiagIntervalMaxMs, interval);
            touchDiagIntervalCount++;
        }
        touchDiagLastEventTimeMs = event.getEventTime();
        if (nowMs - touchDiagWindowStartMs < 10_000L) return;
        final long n = touchDiagCount;
        final String diagLine = "stage=android-dispatch" +
                " events=" + n + " historySamples=" + touchDiagHistoryCount +
                " eventAgeAvgMs=" + (touchDiagArrivalSumMs / n) +
                " eventAgeMaxMs=" + touchDiagArrivalMaxMs +
                " dispatchAvgUs=" + (touchDiagDispatchSumUs / n) +
                " dispatchMaxUs=" + touchDiagDispatchMaxUs +
                " eventIntervalAvgMs=" + (touchDiagIntervalCount > 0 ? touchDiagIntervalSumMs / touchDiagIntervalCount : 0) +
                " eventIntervalMaxMs=" + touchDiagIntervalMaxMs +
                " note=aggregate-not-end-to-end";
        if (touchDiagLogHandler != null)
            touchDiagLogHandler.post(() -> DiagnosticLogger.i("TOUCHLAT", diagLine));
        touchDiagWindowStartMs = nowMs;
        touchDiagCount = touchDiagHistoryCount = touchDiagArrivalSumMs = 0L;
        touchDiagArrivalMaxMs = touchDiagDispatchSumUs = touchDiagDispatchMaxUs = 0L;
        touchDiagIntervalSumMs = touchDiagIntervalMaxMs = touchDiagIntervalCount = 0L;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (!event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN))
            return dispatchTouchEventRouted(event);
        long begin = System.nanoTime();
        long arrivalMs = SystemClock.uptimeMillis();
        try {
            return dispatchTouchEventRouted(event);
        } finally {
            traceTouchLatency(event, begin, arrivalMs);
        }
    }

    private boolean dispatchTouchEventRouted(MotionEvent event) {
        if (hardwareInputEnabled && isPhysicalMouseEvent(event)) {
            traceHardwareMouseEvent("touch-dispatch", event);
            if (!hardwareMouseCaptured) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN && !pointIsOverAndroidOverlay(event.getX(), event.getY())) {
                    setHardwareMouseCaptured(true);
                    handlePhysicalMouseButtons(event);
                    return true;
                }
                return super.dispatchTouchEvent(event);
            }

            handlePhysicalMouseMotion(event);
            handlePhysicalMouseButtons(event);
            handlePhysicalMouseWheel(event);
            return true;
        }
        // Hardware mouse and stylus paths remain unchanged. Touchscreen fingers
        // are isolated before Window/ViewGroup dispatch so a game key cannot
        // capture the whole gesture and starve the SDL surface of other fingers.
        if (event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN) &&
                isIndependentTouchAction(event.getActionMasked())) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) suppressStaleTouchGesture = false;
            if (suppressStaleTouchGesture) {
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)
                    suppressStaleTouchGesture = false;
                return true;
            }
            if (floatingKeyOverlay != null && floatingKeyOverlay.supportsIndependentTouch())
                return dispatchIndependentTouchEvent(event);
            if (!vmTouchPointers.isEmpty() || gameTouchWasRouted) {
                // Settings or visibility changed while fingers were still held.
                // Do not forward the orphaned POINTER_UP/MOVE to SDL.
                cancelIndependentTouchRouting("overlay-changed");
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)
                    suppressStaleTouchGesture = false;
                return true;
            }
        }
        return super.dispatchTouchEvent(event);
    }

    private static boolean isIndependentTouchAction(int action) {
        return action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN ||
                action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_POINTER_UP ||
                action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL;
    }

    private void resetIndependentTouchRouting() {
        vmTouchPointers.clear();
        vmTouchDownTime = 0L;
        gameTouchWasRouted = false;
        if (floatingKeyOverlay != null) floatingKeyOverlay.cancelIndependentPointers();
    }

    /** Cancel SDL's VM stream BEFORE clearing pointers, including on lost UP/focus. */
    private void cancelIndependentTouchRouting(String reason) {
        boolean hadVm = !vmTouchPointers.isEmpty();
        boolean hadGame = gameTouchWasRouted;
        if (hadVm) {
            long now = android.os.SystemClock.uptimeMillis();
            long start = vmTouchDownTime > 0L && vmTouchDownTime <= now ? vmTouchDownTime : now;
            MotionEvent cancel = MotionEvent.obtain(start, now, MotionEvent.ACTION_CANCEL,
                    0f, 0f, 0);
            cancel.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            try {
                super.dispatchTouchEvent(cancel);
            } finally {
                cancel.recycle();
            }
        }
        resetIndependentTouchRouting();
        if (hadVm || hadGame) {
            suppressStaleTouchGesture = true;
            DiagnosticLogger.i(TAG, "MultiTouch stream cancelled reason=" + reason +
                    " hadVm=" + hadVm + " hadGame=" + hadGame);
        }
    }

    private boolean dispatchIndependentTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        int changedId = -1;
        boolean changedIsVm = false;
        if (action == MotionEvent.ACTION_DOWN) {
            // If a vendor dropped UP/CANCEL, explicitly cancel SDL's old touch.
            cancelIndependentTouchRouting("new-down");
            suppressStaleTouchGesture = false;
        }

        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            int index = event.getActionIndex();
            changedId = event.getPointerId(index);
            float x = event.getX(index), y = event.getY(index);
            boolean isGame = !pointIsOverAndroidOverlay(x, y) &&
                    !viewContainsPoint(virtualKeyboard, x, y) &&
                    !viewContainsPoint(speedPercentText, x, y) &&
                    floatingKeyOverlay.captureIndependentPointer(changedId, x, y);
            if (isGame) {
                gameTouchWasRouted = true;
                DiagnosticLogger.i(TAG, "MultiTouch overlay pointer DOWN id=" + changedId +
                        " vmFingers=" + vmTouchPointers.size());
            } else {
                changedIsVm = true;
                if (vmTouchPointers.isEmpty()) vmTouchDownTime = event.getEventTime();
                vmTouchPointers.add(changedId);
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            changedId = event.getPointerId(event.getActionIndex());
            changedIsVm = vmTouchPointers.contains(changedId);
        }

        // Rebuild only the VM stream. E.g. finger 1 on LEFT + finger 2 on VM
        // sends VM ACTION_DOWN (not POINTER_DOWN), and LEFT remains held.
        final boolean hasVmPointers = !vmTouchPointers.isEmpty();
        boolean handled = false;
        if (hasVmPointers) {
            boolean unchangedStream = !gameTouchWasRouted &&
                    vmTouchPointers.size() == event.getPointerCount();
            if (unchangedStream) {
                handled = super.dispatchTouchEvent(event);
            } else {
                int forwardedAction = TouchStreamAction.forVm(action,
                        vmTouchPointers.size(), changedIsVm);
                MotionEvent filtered = buildVmTouchEvent(event, forwardedAction, changedId);
                if (filtered != null) {
                    try { handled = super.dispatchTouchEvent(filtered); }
                    finally { filtered.recycle(); }
                } else if (action == MotionEvent.ACTION_CANCEL) {
                    // A vendor may deliver CANCEL with fewer pointer records;
                    // still tell SDL to release the virtual mouse/touch stream.
                    MotionEvent cancel = MotionEvent.obtain(vmTouchDownTime,
                            event.getEventTime(), MotionEvent.ACTION_CANCEL, 0f, 0f,
                            event.getMetaState());
                    cancel.setSource(event.getSource());
                    try { handled = super.dispatchTouchEvent(cancel); }
                    finally { cancel.recycle(); }
                }
            }
        }

        final boolean consumedGameKey = gameTouchWasRouted;
        if (action == MotionEvent.ACTION_CANCEL) {
            resetIndependentTouchRouting();
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            if (changedIsVm) {
                vmTouchPointers.remove(changedId);
                if (vmTouchPointers.isEmpty()) vmTouchDownTime = 0L;
            } else {
                floatingKeyOverlay.finishIndependentPointer(changedId, false);
                DiagnosticLogger.i(TAG, "MultiTouch overlay pointer UP id=" + changedId +
                        " vmFingers=" + vmTouchPointers.size());
            }
            if (action == MotionEvent.ACTION_UP) resetIndependentTouchRouting();
        }
        // Report overlay-owned fingers as handled, even when no VM finger exists.
        return handled || consumedGameKey || hasVmPointers;
    }

    private MotionEvent buildVmTouchEvent(MotionEvent event, int action, int changedId) {
        int count = vmTouchPointers.size();
        if (count == 0) return null;
        MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[count];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[count];
        int destination = 0;
        int changedIndex = -1;
        for (int i = 0; i < event.getPointerCount(); i++) {
            int pointerId = event.getPointerId(i);
            if (!vmTouchPointers.contains(pointerId)) continue;
            props[destination] = new MotionEvent.PointerProperties();
            coords[destination] = new MotionEvent.PointerCoords();
            event.getPointerProperties(i, props[destination]);
            event.getPointerCoords(i, coords[destination]);
            if (pointerId == changedId) changedIndex = destination;
            destination++;
        }
        if (destination != count) return null; // avoid malformed input after a lost pointer
        if (action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_POINTER_UP) {
            if (changedIndex < 0) return null;
            action |= changedIndex << MotionEvent.ACTION_POINTER_INDEX_SHIFT;
        }
        return MotionEvent.obtain(vmTouchDownTime, event.getEventTime(), action, count,
                props, coords, event.getMetaState(), event.getButtonState(),
                event.getXPrecision(), event.getYPrecision(), event.getDeviceId(),
                event.getEdgeFlags(), event.getSource(), event.getFlags());
    }

    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {
        super.onPointerCaptureChanged(hasCapture);
        hardwareMouseCaptured = hasCapture;
        sendHardwareMouseCapture(hasCapture);
        DiagnosticLogger.i(TAG, "Physical mouse pointer capture changed: " + hasCapture +
                " target=" + describeCaptureTarget(hardwareMouseCaptureTarget));
        resetHardwareMouseTracking();
        if (!hasCapture) {
            releaseHardwareButtons();
            setImmersiveMode();
        }
    }

    private boolean handleCapturedPhysicalMouseEvent(MotionEvent event) {
        if (!hardwareInputEnabled || !hardwareMouseCaptured || !isPhysicalMouseEvent(event))
            return false;
        traceHardwareMouseEvent("captured-view", event);
        handlePhysicalMouseMotion(event);
        handlePhysicalMouseButtons(event);
        handlePhysicalMouseWheel(event);
        return true;
    }

    private void installPhysicalMouseCaptureTarget(String reason) {
        View decor = getWindow() == null ? null : getWindow().getDecorView();
        if (decor == null)
            return;

        View content = decor.findViewById(android.R.id.content);
        View target = findSdlSurfaceView(content);
        if (target == null)
            target = findSdlSurfaceView(decor);
        if (target == null)
            target = decor;

        if (hardwareMouseCaptureTarget != null && hardwareMouseCaptureTarget != target) {
            try { hardwareMouseCaptureTarget.setOnCapturedPointerListener(null); } catch (Throwable ignored) {}
        }

        hardwareMouseCaptureTarget = target;
        try {
            target.setFocusable(true);
            target.setFocusableInTouchMode(true);
            target.setOnCapturedPointerListener((view, event) -> handleCapturedPhysicalMouseEvent(event));
            DiagnosticLogger.i(TAG, "Physical mouse capture target installed reason=" + reason +
                    " target=" + describeCaptureTarget(target));
        } catch (Throwable t) {
            DiagnosticLogger.w(TAG, "Could not install captured-pointer listener on SDL surface", t);
        }
    }

    private View findSdlSurfaceView(View root) {
        if (root == null)
            return null;
        if (root instanceof SurfaceView)
            return root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findSdlSurfaceView(group.getChildAt(i));
                if (found != null)
                    return found;
            }
        }
        return null;
    }

    private static String describeCaptureTarget(View view) {
        if (view == null) return "null";
        return view.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(view)) +
                " focus=" + view.hasFocus() + " shown=" + view.isShown();
    }

    private void traceHardwareMouseEvent(String stage, MotionEvent event) {
        if (hardwareMouseTraceBudget <= 0 || event == null) return;
        hardwareMouseTraceBudget--;
        DiagnosticLogger.i(TAG, "MouseEvent " + stage +
                " action=" + event.getActionMasked() +
                " source=0x" + Integer.toHexString(event.getSource()) +
                " device=" + event.getDeviceId() +
                " buttons=0x" + Integer.toHexString(event.getButtonState()) +
                " xy=" + event.getX() + "," + event.getY() +
                " rel=" + event.getAxisValue(MotionEvent.AXIS_RELATIVE_X) + "," +
                event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y) +
                " captured=" + hardwareMouseCaptured);
    }

    private boolean isPhysicalKeyboardEvent(KeyEvent event) {
        if ((event.getSource() & InputDevice.SOURCE_KEYBOARD) != InputDevice.SOURCE_KEYBOARD)
            return false;
        return event.getDeviceId() != KeyCharacterMap.VIRTUAL_KEYBOARD && event.getDeviceId() >= 0;
    }

    private boolean isPhysicalMouseEvent(MotionEvent event) {
        final int source = event.getSource();
        return (source & InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE ||
                (source & InputDevice.SOURCE_MOUSE_RELATIVE) == InputDevice.SOURCE_MOUSE_RELATIVE;
    }

    private static boolean isAndroidSystemKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_HOME:
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_POWER:
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_MUTE:
                return true;
            default:
                return false;
        }
    }

    private void setHardwareMouseCaptured(boolean captured) {
        if (!hardwareInputEnabled && captured)
            return;
        installPhysicalMouseCaptureTarget(captured ? "capture-request" : "capture-release");
        View target = hardwareMouseCaptureTarget;
        if (target == null && getWindow() != null)
            target = getWindow().getDecorView();
        if (target == null)
            return;

        if (captured) {
            resetHardwareMouseTracking();
            try {
                target.setFocusable(true);
                target.setFocusableInTouchMode(true);
                target.requestFocus();
                target.requestPointerCapture();
                hardwareMouseCaptured = true;
                sendHardwareMouseCapture(true);
                DiagnosticLogger.i(TAG, "Physical mouse capture requested target=" + describeCaptureTarget(target) +
                        " hasCapture=" + target.hasPointerCapture());
            } catch (Throwable t) {
                DiagnosticLogger.w(TAG, "Pointer capture unavailable; physical mouse still uses bounded relative motion", t);
                hardwareMouseCaptured = true;
                sendHardwareMouseCapture(true);
            }
        } else {
            try {
                if (target.hasPointerCapture())
                    target.releasePointerCapture();
            } catch (Throwable ignored) {
            }
            hardwareMouseCaptured = false;
            sendHardwareMouseCapture(false);
            DiagnosticLogger.i(TAG, "Physical mouse released target=" + describeCaptureTarget(target));
            resetHardwareMouseTracking();
            releaseHardwareButtons();
        }
    }

    private void handlePhysicalMouseMotion(MotionEvent event) {
        float dx = event.getAxisValue(MotionEvent.AXIS_RELATIVE_X);
        float dy = event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y);

        // Pointer-capture capable Android builds report relative axes.  Some vendors instead
        // expose SOURCE_MOUSE_RELATIVE through getX/getY, while older devices only provide
        // bounded absolute coordinates.  Support all three paths in that order.
        if (Math.abs(dx) < 0.0001f && Math.abs(dy) < 0.0001f) {
            if ((event.getSource() & InputDevice.SOURCE_MOUSE_RELATIVE) == InputDevice.SOURCE_MOUSE_RELATIVE) {
                dx = event.getX();
                dy = event.getY();
            } else {
                float x = event.getX();
                float y = event.getY();
                if (!Float.isNaN(lastHardwareMouseX)) {
                    dx = x - lastHardwareMouseX;
                    dy = y - lastHardwareMouseY;
                } else {
                    dx = 0f;
                    dy = 0f;
                }
                lastHardwareMouseX = x;
                lastHardwareMouseY = y;
            }
        }
        float scale = mouseSpeed / 100.0f;
        hardwareDxRemainder += dx * scale;
        hardwareDyRemainder += dy * scale;
        int ix = (int) hardwareDxRemainder;
        int iy = (int) hardwareDyRemainder;
        hardwareDxRemainder -= ix;
        hardwareDyRemainder -= iy;
        if (ix != 0 || iy != 0) {
            if (hardwareMouseTraceBudget > 0) {
                hardwareMouseTraceBudget--;
                DiagnosticLogger.i(TAG, "Physical mouse delta raw=" + dx + "," + dy +
                        " scaled=" + ix + "," + iy + " source=0x" + Integer.toHexString(event.getSource()));
            }
            sendHardwareMouseMove(ix, iy);
        }
    }

    private void handlePhysicalMouseButtons(MotionEvent event) {
        int now = androidButtonsTo86BoxMask(event.getButtonState());
        if (event.getActionMasked() == MotionEvent.ACTION_BUTTON_PRESS)
            now |= androidButtonTo86BoxMask(event.getActionButton());
        else if (event.getActionMasked() == MotionEvent.ACTION_BUTTON_RELEASE)
            now &= ~androidButtonTo86BoxMask(event.getActionButton());
        else if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL)
            now = 0;

        int changed = hardwareButtonsDown ^ now;
        for (int bit : new int[] {1, 2, 4, 8, 16}) {
            if ((changed & bit) != 0)
                sendHardwareMouseButton(bit, (now & bit) != 0);
        }
        hardwareButtonsDown = now;
    }

    private void handlePhysicalMouseWheel(MotionEvent event) {
        float wheel = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
        if (Math.abs(wheel) < 0.0001f)
            return;
        hardwareWheelRemainder += wheel;
        int steps = (int) hardwareWheelRemainder;
        hardwareWheelRemainder -= steps;
        if (steps != 0)
            sendHardwareMouseWheel(steps);
    }

    private static int androidButtonsTo86BoxMask(int buttons) {
        int mask = 0;
        if ((buttons & MotionEvent.BUTTON_PRIMARY) != 0) mask |= 1;
        if ((buttons & MotionEvent.BUTTON_SECONDARY) != 0) mask |= 2;
        if ((buttons & MotionEvent.BUTTON_TERTIARY) != 0) mask |= 4;
        if ((buttons & MotionEvent.BUTTON_BACK) != 0) mask |= 8;
        if ((buttons & MotionEvent.BUTTON_FORWARD) != 0) mask |= 16;
        return mask;
    }

    private static int androidButtonTo86BoxMask(int button) {
        if (button == MotionEvent.BUTTON_PRIMARY) return 1;
        if (button == MotionEvent.BUTTON_SECONDARY) return 2;
        if (button == MotionEvent.BUTTON_TERTIARY) return 4;
        if (button == MotionEvent.BUTTON_BACK) return 8;
        if (button == MotionEvent.BUTTON_FORWARD) return 16;
        return 0;
    }

    private boolean pointIsOverAndroidOverlay(float x, float y) {
        return viewContainsPoint(toolbarScroll, x, y) || viewContainsPoint(restoreToolbarButton, x, y) ||
                (joystick != null && joystick.getVisibility() == View.VISIBLE && viewContainsPoint(joystick, x, y));
    }

    private static boolean viewContainsPoint(View view, float x, float y) {
        if (view == null || view.getVisibility() != View.VISIBLE)
            return false;
        int[] loc = new int[2];
        view.getLocationInWindow(loc);
        return x >= loc[0] && x < loc[0] + view.getWidth() && y >= loc[1] && y < loc[1] + view.getHeight();
    }

    private boolean physicalCtrlAltHeld() {
        return (hardwarePressedXt.contains(0x1d) || hardwarePressedXt.contains(0x11d)) &&
                (hardwarePressedXt.contains(0x38) || hardwarePressedXt.contains(0x138));
    }

    private void releaseHardwareButtons() {
        if (hardwareButtonsDown == 0)
            return;
        for (int bit : new int[] {1, 2, 4, 8, 16}) {
            if ((hardwareButtonsDown & bit) != 0)
                sendHardwareMouseButton(bit, false);
        }
        hardwareButtonsDown = 0;
    }

    private void releaseHardwareInput() {
        hardwarePressedXt.clear();
        suppressMouseReleaseG = false;
        hardwareButtonsDown = 0;
        resetHardwareMouseTracking();
        try {
            nativeHardwareReleaseAll();
        } catch (UnsatisfiedLinkError e) {
            DiagnosticLogger.w(TAG, "Hardware release bridge not ready", e);
        }
    }

    private void resetHardwareMouseTracking() {
        lastHardwareMouseX = Float.NaN;
        lastHardwareMouseY = Float.NaN;
        hardwareDxRemainder = 0f;
        hardwareDyRemainder = 0f;
        hardwareWheelRemainder = 0f;
    }

    private void sendHardwareKey(int xt, boolean down) {
        try { nativeHardwareKey(xt, down); } catch (UnsatisfiedLinkError e) { DiagnosticLogger.w(TAG, "Hardware keyboard bridge not ready", e); }
    }

    private void sendHardwareMouseMove(int dx, int dy) {
        try { nativeHardwareMouseMove(dx, dy); } catch (UnsatisfiedLinkError e) { DiagnosticLogger.w(TAG, "Hardware mouse bridge not ready", e); }
    }

    private void sendHardwareMouseButton(int mask, boolean down) {
        if (mask == 0) return;
        try { nativeHardwareMouseButton(mask, down); } catch (UnsatisfiedLinkError e) { DiagnosticLogger.w(TAG, "Hardware mouse button bridge not ready", e); }
    }

    private void sendHardwareMouseWheel(int steps) {
        try { nativeHardwareMouseWheel(steps); } catch (UnsatisfiedLinkError e) { DiagnosticLogger.w(TAG, "Hardware mouse wheel bridge not ready", e); }
    }

    private void sendHardwareMouseCapture(boolean captured) {
        try {
            nativeHardwareMouseCapture(captured);
        } catch (UnsatisfiedLinkError e) {
            DiagnosticLogger.w(TAG, "Hardware mouse capture bridge not ready", e);
        }
    }

    private void registerPhysicalMouseListener() {
        if (inputManager == null || inputDeviceListenerRegistered)
            return;
        try {
            inputManager.registerInputDeviceListener(inputDeviceListener, null);
            inputDeviceListenerRegistered = true;
            logConnectedPhysicalMice("listener-register");
        } catch (Throwable t) {
            DiagnosticLogger.w(TAG, "Could not register physical mouse listener", t);
        }
    }

    private void unregisterPhysicalMouseListener() {
        if (inputManager == null || !inputDeviceListenerRegistered)
            return;
        try {
            inputManager.unregisterInputDeviceListener(inputDeviceListener);
        } catch (Throwable ignored) {
        }
        inputDeviceListenerRegistered = false;
    }

    private void logConnectedPhysicalMice(String reason) {
        StringBuilder found = new StringBuilder();
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(id);
            if (!isPhysicalMouseDevice(device))
                continue;
            if (found.length() > 0) found.append(" | ");
            found.append(describeInputDevice(device));
        }
        DiagnosticLogger.i(TAG, "Physical mouse scan reason=" + reason +
                " found=" + (found.length() == 0 ? "none" : found));
    }

    private boolean hasPhysicalMouseConnected() {
        for (int id : InputDevice.getDeviceIds()) {
            if (isPhysicalMouseDevice(InputDevice.getDevice(id)))
                return true;
        }
        return false;
    }

    private static boolean isPhysicalMouseDevice(InputDevice device) {
        if (device == null || device.isVirtual())
            return false;
        int source = device.getSources();
        boolean mouseSource = (source & InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE ||
                (source & InputDevice.SOURCE_MOUSE_RELATIVE) == InputDevice.SOURCE_MOUSE_RELATIVE;
        if (!mouseSource)
            return false;

        /* Some composite HID keyboards expose a misleading SOURCE_MOUSE bit and
         * even synthetic pointer ranges. Never let an explicitly keyboard-named,
         * alphabetic keyboard steal auto-capture from the real mouse. This is an
         * identity guard only; normal physical key events remain untouched. */
        String deviceName = device.getName() == null ? "" : device.getName().toLowerCase(Locale.ROOT);
        if (device.getKeyboardType() == InputDevice.KEYBOARD_TYPE_ALPHABETIC &&
                (deviceName.contains("keyboard") || deviceName.contains("kbd")))
            return false;

        /*
         * Composite keyboards can expose source bit patterns that happen to contain
         * SOURCE_MOUSE while having no pointer axes.  Only auto-capture devices that
         * actually advertise pointer motion.  This keeps keyboards such as the G915
         * from stealing/restarting pointer capture before the real USB mouse arrives.
         */
        for (InputDevice.MotionRange range : device.getMotionRanges()) {
            int axis = range.getAxis();
            int rs = range.getSource();
            boolean pointerRange = (rs & InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE ||
                    (rs & InputDevice.SOURCE_MOUSE_RELATIVE) == InputDevice.SOURCE_MOUSE_RELATIVE;
            if (pointerRange && (axis == MotionEvent.AXIS_X || axis == MotionEvent.AXIS_Y ||
                    axis == MotionEvent.AXIS_RELATIVE_X || axis == MotionEvent.AXIS_RELATIVE_Y))
                return true;
        }
        return false;
    }

    private static String describeInputDevice(InputDevice device) {
        if (device == null) return "null";
        return "id=" + device.getId() +
                " name=" + device.getName() +
                " sources=0x" + Integer.toHexString(device.getSources()) +
                " vendor=" + device.getVendorId() +
                " product=" + device.getProductId();
    }

    private void scheduleAutoCapturePhysicalMouse(String reason) {
        View decor = getWindow() == null ? null : getWindow().getDecorView();
        if (decor == null) return;
        decor.postDelayed(() -> maybeAutoCapturePhysicalMouse(reason), 120L);
    }

    private void maybeAutoCapturePhysicalMouse(String reason) {
        if (activityDestroyed || !hardwareInputEnabled || externalPickerActive || hardwareMouseCaptured)
            return;
        if (!hasWindowFocus()) {
            DiagnosticLogger.i(TAG, "Auto mouse capture deferred reason=" + reason + " windowFocus=false");
            return;
        }
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(id);
            if (isPhysicalMouseDevice(device)) {
                DiagnosticLogger.i(TAG, "Auto mouse capture reason=" + reason + " device=" + describeInputDevice(device));
                setHardwareMouseCaptured(true);
                return;
            }
        }
    }

    private void beginExternalPickerSession(int requestCode) {
        externalPickerActive = true;
        externalPickerRequestCode = requestCode;
        try {
            Intent service = new Intent(this, VmKeepAliveService.class);
            service.putExtra(VmKeepAliveService.EXTRA_PROFILE_NAME,
                    getIntent() == null ? null : getIntent().getStringExtra(ProfileManagerActivity.EXTRA_PROFILE_NAME));
            service.putExtra(VmKeepAliveService.EXTRA_REQUEST_CODE, requestCode);
            startForegroundService(service);
            DiagnosticLogger.i(TAG, "VM keep-alive foreground service requested for picker request=" + requestCode);
        } catch (Throwable t) {
            DiagnosticLogger.e(TAG, "Could not start VM keep-alive foreground service", t);
        }
    }

    private void endExternalPickerSession(int requestCode, String reason) {
        if (externalPickerActive || externalPickerRequestCode == requestCode)
            DiagnosticLogger.i(TAG, "External picker finished request=" + requestCode + " reason=" + reason);
        externalPickerActive = false;
        externalPickerRequestCode = -1;
        stopVmPickerKeepAlive();
        scheduleAutoCapturePhysicalMouse("picker-return");
    }

    private void stopVmPickerKeepAlive() {
        try {
            stopService(new Intent(this, VmKeepAliveService.class));
        } catch (Throwable t) {
            DiagnosticLogger.w(TAG, "Could not stop VM keep-alive service", t);
        }
    }

    /* Linux input-event scan codes used by Android USB/Bluetooth HID keyboards.
       Most alphanumeric codes intentionally match AT set-1; extended keys need E0. */
    private static int linuxScanCodeToXt(int scan) {
        if (scan >= 1 && scan <= 83)
            return scan;
        switch (scan) {
            case 86: return 0x56;  // ISO 102nd key (< > | on German keyboards)
            case 87: return 0x57;  // F11
            case 88: return 0x58;  // F12
            case 96: return 0x11c; // keypad Enter
            case 97: return 0x11d; // right Ctrl
            case 98: return 0x135; // keypad /
            case 99: return 0x137; // Print Screen/SysRq
            case 100: return 0x138; // right Alt / AltGr
            case 102: return 0x147; // Home
            case 103: return 0x148; // Up
            case 104: return 0x149; // Page Up
            case 105: return 0x14b; // Left
            case 106: return 0x14d; // Right
            case 107: return 0x14f; // End
            case 108: return 0x150; // Down
            case 109: return 0x151; // Page Down
            case 110: return 0x152; // Insert
            case 111: return 0x153; // Delete
            case 125: return 0x15b; // left Meta/Windows
            case 126: return 0x15c; // right Meta/Windows
            case 127: return 0x15d; // Menu/Compose
            default: return 0;
        }
    }

    /* Fallback for keyboards/drivers which report scanCode=0. This follows key
       position closely enough for PC guest layouts; raw HID scan codes are preferred. */
    private static int androidKeyCodeToXt(int keyCode) {
        if (keyCode >= KeyEvent.KEYCODE_1 && keyCode <= KeyEvent.KEYCODE_9) return 0x02 + (keyCode - KeyEvent.KEYCODE_1);
        if (keyCode == KeyEvent.KEYCODE_0) return 0x0b;
        if (keyCode >= KeyEvent.KEYCODE_A && keyCode <= KeyEvent.KEYCODE_Z) {
            final int[] xt = {0x1e,0x30,0x2e,0x20,0x12,0x21,0x22,0x23,0x17,0x24,0x25,0x26,0x32,0x31,0x18,0x19,0x10,0x13,0x1f,0x14,0x16,0x2f,0x11,0x2d,0x15,0x2c};
            return xt[keyCode - KeyEvent.KEYCODE_A];
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_ESCAPE: return 0x01;
            case KeyEvent.KEYCODE_MINUS: return 0x0c;
            case KeyEvent.KEYCODE_EQUALS: return 0x0d;
            case KeyEvent.KEYCODE_DEL: return 0x0e;
            case KeyEvent.KEYCODE_TAB: return 0x0f;
            case KeyEvent.KEYCODE_LEFT_BRACKET: return 0x1a;
            case KeyEvent.KEYCODE_RIGHT_BRACKET: return 0x1b;
            case KeyEvent.KEYCODE_ENTER: return 0x1c;
            case KeyEvent.KEYCODE_CTRL_LEFT: return 0x1d;
            case KeyEvent.KEYCODE_SEMICOLON: return 0x27;
            case KeyEvent.KEYCODE_APOSTROPHE: return 0x28;
            case KeyEvent.KEYCODE_GRAVE: return 0x29;
            case KeyEvent.KEYCODE_SHIFT_LEFT: return 0x2a;
            case KeyEvent.KEYCODE_BACKSLASH: return 0x2b;
            case KeyEvent.KEYCODE_COMMA: return 0x33;
            case KeyEvent.KEYCODE_PERIOD: return 0x34;
            case KeyEvent.KEYCODE_SLASH: return 0x35;
            case KeyEvent.KEYCODE_SHIFT_RIGHT: return 0x36;
            case KeyEvent.KEYCODE_ALT_LEFT: return 0x38;
            case KeyEvent.KEYCODE_SPACE: return 0x39;
            case KeyEvent.KEYCODE_CAPS_LOCK: return 0x3a;
            case KeyEvent.KEYCODE_F1: return 0x3b; case KeyEvent.KEYCODE_F2: return 0x3c;
            case KeyEvent.KEYCODE_F3: return 0x3d; case KeyEvent.KEYCODE_F4: return 0x3e;
            case KeyEvent.KEYCODE_F5: return 0x3f; case KeyEvent.KEYCODE_F6: return 0x40;
            case KeyEvent.KEYCODE_F7: return 0x41; case KeyEvent.KEYCODE_F8: return 0x42;
            case KeyEvent.KEYCODE_F9: return 0x43; case KeyEvent.KEYCODE_F10: return 0x44;
            case KeyEvent.KEYCODE_NUM_LOCK: return 0x45;
            case KeyEvent.KEYCODE_SCROLL_LOCK: return 0x46;
            case KeyEvent.KEYCODE_F11: return 0x57; case KeyEvent.KEYCODE_F12: return 0x58;
            case KeyEvent.KEYCODE_NUMPAD_ENTER: return 0x11c;
            case KeyEvent.KEYCODE_CTRL_RIGHT: return 0x11d;
            case KeyEvent.KEYCODE_NUMPAD_DIVIDE: return 0x135;
            case KeyEvent.KEYCODE_SYSRQ: return 0x137;
            case KeyEvent.KEYCODE_ALT_RIGHT: return 0x138;
            case KeyEvent.KEYCODE_MOVE_HOME: return 0x147;
            case KeyEvent.KEYCODE_DPAD_UP: return 0x148;
            case KeyEvent.KEYCODE_PAGE_UP: return 0x149;
            case KeyEvent.KEYCODE_DPAD_LEFT: return 0x14b;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return 0x14d;
            case KeyEvent.KEYCODE_MOVE_END: return 0x14f;
            case KeyEvent.KEYCODE_DPAD_DOWN: return 0x150;
            case KeyEvent.KEYCODE_PAGE_DOWN: return 0x151;
            case KeyEvent.KEYCODE_INSERT: return 0x152;
            case KeyEvent.KEYCODE_FORWARD_DEL: return 0x153;
            case KeyEvent.KEYCODE_META_LEFT: return 0x15b;
            case KeyEvent.KEYCODE_META_RIGHT: return 0x15c;
            case KeyEvent.KEYCODE_MENU: return 0x15d;
            case KeyEvent.KEYCODE_NUMPAD_MULTIPLY: return 0x37;
            case KeyEvent.KEYCODE_NUMPAD_SUBTRACT: return 0x4a;
            case KeyEvent.KEYCODE_NUMPAD_5: return 0x4c;
            case KeyEvent.KEYCODE_NUMPAD_ADD: return 0x4e;
            case KeyEvent.KEYCODE_NUMPAD_0: return 0x52;
            case KeyEvent.KEYCODE_NUMPAD_DOT: return 0x53;
            case KeyEvent.KEYCODE_NUMPAD_1: return 0x4f;
            case KeyEvent.KEYCODE_NUMPAD_2: return 0x50;
            case KeyEvent.KEYCODE_NUMPAD_3: return 0x51;
            case KeyEvent.KEYCODE_NUMPAD_4: return 0x4b;
            case KeyEvent.KEYCODE_NUMPAD_6: return 0x4d;
            case KeyEvent.KEYCODE_NUMPAD_7: return 0x47;
            case KeyEvent.KEYCODE_NUMPAD_8: return 0x48;
            case KeyEvent.KEYCODE_NUMPAD_9: return 0x49;
            default: return 0;
        }
    }

    private void setImmersiveMode() {
        FullscreenUi.apply(this);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (!hasFocus) {
            cancelIndependentTouchRouting("window-focus-lost");
            releaseFloatingKeyOverlayKeys();
        }
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            setImmersiveMode();
            installPhysicalMouseCaptureTarget("window-focus");
            scheduleAutoCapturePhysicalMouse("window-focus");
        }
    }

    private File getCurrentProfileDir() {
        String requested = getIntent() == null ? null : getIntent().getStringExtra(ProfileManagerActivity.EXTRA_PROFILE_PATH);
        File profiles = new File(new File(getFilesDir(), "86box"), "profiles");
        //noinspection ResultOfMethodCallIgnored
        profiles.mkdirs();
        File fallback = new File(profiles, "Default_PC");
        File profile = requested == null || requested.trim().isEmpty() ? fallback : new File(requested);
        try {
            String base = profiles.getCanonicalPath() + File.separator;
            String candidate = profile.getCanonicalPath();
            if (!candidate.startsWith(base)) profile = fallback;
        } catch (IOException e) {
            profile = fallback;
        }
        //noinspection ResultOfMethodCallIgnored
        profile.mkdirs();
        return profile;
    }

    private void showOfflineHddManager() {
        new AlertDialog.Builder(this)
                .setTitle("Hard disks are managed offline")
                .setMessage("Importing or replacing a fixed HDD while the emulator is running can block the VM. " +
                        "Close the VM first, then use Import HDD Image or Create HDD in the hardware profile.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Close VM & open hardware", (d, w) -> closeVmAndOpenHardware())
                .show();
    }

    private void closeVmAndOpenHardware() {
        // Do not start the hardware editor while SDL/86Box is still tearing down.
        // Returning to the profile manager first avoids a short overlap where both
        // activities could touch the same 86box.cfg/native core state.
        releaseHardwareInput();
        setHardwareMouseCaptured(false);
        try { setLifecyclePause(true); } catch (Throwable ignored) { }
        Intent manager = new Intent(this, ProfileManagerActivity.class);
        manager.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        manager.putExtra(ProfileManagerActivity.EXTRA_OPEN_HW_AFTER_VM, getCurrentProfileDir().getAbsolutePath());
        startActivity(manager);
        finish();
    }

    private void returnToProfileManager() {
        releaseHardwareInput();
        setHardwareMouseCaptured(false);
        try { setLifecyclePause(true); } catch (Throwable ignored) { }
        Intent manager = new Intent(this, ProfileManagerActivity.class);
        manager.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(manager);
        finish();
    }


    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateVirtualKeyboardLayout();
    }

    private void prepareRuntimeTree() {
        try {
            RuntimeAssets.prepare(this);
        } catch (IOException e) {
            DiagnosticLogger.e(TAG, "Could not prepare packaged ROM/assets tree", e);
        }
    }

    private float uiScaleFactor() {
        Configuration c = getResources().getConfiguration();
        int w = c.screenWidthDp;
        int h = c.screenHeightDp;
        if ((w > 0 && w < 360) || (h > 0 && h < 420)) return 0.80f;
        if ((w > 0 && w < 480) || (h > 0 && h < 600)) return 0.90f;
        if (w >= 900 && h >= 600) return 1.08f;
        return 1.0f;
    }

    private int dp(int value) {
        return Math.max(1, Math.round(value * getResources().getDisplayMetrics().density * uiScaleFactor()));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private abstract static class SimpleSeekListener implements SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(SeekBar seekBar) { }
        @Override public void onStopTrackingTouch(SeekBar seekBar) { }
    }

    /** Small analog stick which sends relative mouse deltas through SDL's event queue. */
    private final class JoystickView extends View {
        private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint knobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float cx;
        private float cy;
        private float knobX;
        private float knobY;
        private boolean active;
        private int currentMoveX;
        private int currentMoveY;
        private final Runnable repeatMove = new Runnable() {
            @Override public void run() {
                if (!active) return;
                if (currentMoveX != 0 || currentMoveY != 0) {
                    int packed = ((currentMoveX & 0xffff) << 16) | (currentMoveY & 0xffff);
                    postNativeControl(CTRL_MOUSE_MOVE, packed);
                }
                postDelayed(this, 16);
            }
        };

        JoystickView(Context context) {
            super(context);
            setContentDescription("Virtual mouse joystick");
            basePaint.setColor(0x55303030);
            knobPaint.setColor(0xAAE0E0E0);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            cx = getWidth() * 0.5f;
            cy = getHeight() * 0.5f;
            if (!active) {
                knobX = cx;
                knobY = cy;
            }
            float radius = Math.min(getWidth(), getHeight()) * 0.46f;
            canvas.drawCircle(cx, cy, radius, basePaint);
            canvas.drawCircle(knobX, knobY, radius * 0.34f, knobPaint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float radius = Math.min(getWidth(), getHeight()) * 0.46f;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE: {
                    active = true;
                    float dx = event.getX() - cx;
                    float dy = event.getY() - cy;
                    float len = (float)Math.sqrt(dx * dx + dy * dy);
                    if (len > radius && len > 0f) {
                        dx = dx / len * radius;
                        dy = dy / len * radius;
                    }
                    knobX = cx + dx;
                    knobY = cy + dy;
                    float nx = dx / radius;
                    float ny = dy / radius;
                    currentMoveX = Math.round(nx * 14f * mouseSpeed / 100f);
                    currentMoveY = Math.round(ny * 14f * mouseSpeed / 100f);
                    if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                        removeCallbacks(repeatMove);
                        post(repeatMove);
                    }
                    invalidate();
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    active = false;
                    currentMoveX = 0;
                    currentMoveY = 0;
                    removeCallbacks(repeatMove);
                    invalidate();
                    return true;
                default:
                    return true;
            }
        }
    }
}
