package net.box86.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Small configurable in-game key overlay.
 *
 * Tap mode behaves like a normal key while the overlay button is touched.
 * Hold mode is deliberately latched: tap once for key-down, tap again for
 * key-up.  That makes combinations such as held TAB + touch mouse possible,
 * because the finger does not have to remain on the key while using the VM.
 */
final class FloatingKeyOverlayView extends FrameLayout {
    interface Listener {
        void onKey(int xtCode, boolean down);
        void onMouseButton(int buttonMask, boolean down);
    }

    private static final String PREF_KEYS = "floating_key_overlay_keys";
    private static final String PREF_EDIT = "floating_key_overlay_edit";
    private static final String PREF_HOLD_PREFIX = "floating_key_overlay_hold_";
    private static final String PREF_X_PREFIX = "floating_key_overlay_x_";
    private static final String PREF_Y_PREFIX = "floating_key_overlay_y_";
    private static final String PREF_SIZE_PREFIX = "floating_key_overlay_size_percent_";
    private static final String PREF_BG_OPACITY = "floating_key_overlay_background_opacity_percent";

    private static final int KEY_SIZE_MIN_PERCENT = 50;
    private static final int KEY_SIZE_MAX_PERCENT = 200;
    private static final int KEY_SIZE_STEP_PERCENT = 10;

    private static final class KeySpec {
        final String id;
        final int xt;
        final int mouseMask;
        KeySpec(String id, int xt) { this(id, xt, 0); }
        KeySpec(String id, int xt, int mouseMask) {
            this.id = id;
            this.xt = xt;
            this.mouseMask = mouseMask;
        }
    }

    private static final KeySpec[] KEY_SPECS = new KeySpec[] {
            new KeySpec("TAB", 0x0f), new KeySpec("ESC", 0x01), new KeySpec("ENTER", 0x1c),
            new KeySpec("SPACE", 0x39), new KeySpec("BACKSPACE", 0x0e),
            new KeySpec("SHIFT", 0x2a), new KeySpec("CTRL", 0x1d), new KeySpec("ALT", 0x38),
            new KeySpec("A",0x1e), new KeySpec("B",0x30), new KeySpec("C",0x2e), new KeySpec("D",0x20),
            new KeySpec("E",0x12), new KeySpec("F",0x21), new KeySpec("G",0x22), new KeySpec("H",0x23),
            new KeySpec("I",0x17), new KeySpec("J",0x24), new KeySpec("K",0x25), new KeySpec("L",0x26),
            new KeySpec("M",0x32), new KeySpec("N",0x31), new KeySpec("O",0x18), new KeySpec("P",0x19),
            new KeySpec("Q",0x10), new KeySpec("R",0x13), new KeySpec("S",0x1f), new KeySpec("T",0x14),
            new KeySpec("U",0x16), new KeySpec("V",0x2f), new KeySpec("W",0x11), new KeySpec("X",0x2d),
            new KeySpec("Y",0x15), new KeySpec("Z",0x2c),
            new KeySpec("1",0x02), new KeySpec("2",0x03), new KeySpec("3",0x04), new KeySpec("4",0x05),
            new KeySpec("5",0x06), new KeySpec("6",0x07), new KeySpec("7",0x08), new KeySpec("8",0x09),
            new KeySpec("9",0x0a), new KeySpec("0",0x0b),
            new KeySpec("F1",0x3b), new KeySpec("F2",0x3c), new KeySpec("F3",0x3d), new KeySpec("F4",0x3e),
            new KeySpec("F5",0x3f), new KeySpec("F6",0x40), new KeySpec("F7",0x41), new KeySpec("F8",0x42),
            new KeySpec("F9",0x43), new KeySpec("F10",0x44), new KeySpec("F11",0x57), new KeySpec("F12",0x58),
            new KeySpec("UP",0x148), new KeySpec("DOWN",0x150), new KeySpec("LEFT",0x14b), new KeySpec("RIGHT",0x14d),
            new KeySpec("INS",0x152), new KeySpec("DEL",0x153), new KeySpec("HOME",0x147), new KeySpec("END",0x14f),
            new KeySpec("PGUP",0x149), new KeySpec("PGDN",0x151),
            // 86Box mouse masks: 1 = left, 2 = right. Never send these as XT scancodes.
            new KeySpec("MOUSE_LEFT", 0, 1), new KeySpec("MOUSE_RIGHT", 0, 2)
    };

    private final Activity activity;
    private final Listener listener;
    private final SharedPreferences prefs;
    private final Map<String, Button> buttons = new LinkedHashMap<>();
    private final Set<String> latched = new LinkedHashSet<>();
    private final Set<String> momentaryDown = new LinkedHashSet<>();
    // Physical finger IDs are independent of button IDs. Several fingers may hold
    // different keys at once (or even the same key without an early key-up).
    private final KeyTouchTracker isolatedTouchOwners = new KeyTouchTracker();
    private boolean german;
    private boolean editMode;

    FloatingKeyOverlayView(Activity activity, Listener listener, SharedPreferences prefs, boolean german) {
        super(activity);
        this.activity = activity;
        this.listener = listener;
        this.prefs = prefs;
        this.german = german;
        this.editMode = prefs.getBoolean(PREF_EDIT, false);
        setClipChildren(false);
        setClipToPadding(false);
        setMotionEventSplittingEnabled(true);
        setClickable(false);
        setFocusable(false);
        rebuild();
    }

    void setGermanLayout(boolean german) {
        if (this.german == german) return;
        releaseAll();
        this.german = german;
        rebuild();
    }

    void releaseAll() {
        isolatedTouchOwners.clear();
        for (Button button : buttons.values()) button.setPressed(false);
        for (String id : new LinkedHashSet<>(momentaryDown)) {
            KeySpec spec = find(id);
            if (spec != null) safeSend(spec, false);
        }
        momentaryDown.clear();
        for (String id : new LinkedHashSet<>(latched)) {
            KeySpec spec = find(id);
            if (spec != null) safeSend(spec, false);
        }
        latched.clear();
        updateButtonStates();
    }

    void showConfigurationDialog() {
        final Set<String> enabled = new LinkedHashSet<>(prefs.getStringSet(PREF_KEYS, new LinkedHashSet<>()));
        final Map<String, CheckBox> enableChecks = new LinkedHashMap<>();
        final Map<String, CheckBox> holdChecks = new LinkedHashMap<>();
        final Map<String, Integer> sizeValues = new LinkedHashMap<>();

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(8), dp(14), dp(8));
        content.setBackgroundColor(0xff151515);

        TextView info = new TextView(activity);
        info.setText("Choose individual in-game keyboard keys and MOUSE LEFT / MOUSE RIGHT. A mouse button clicks while touched; HOLD = tap once to keep a keyboard or mouse button pressed, tap again to release. Use − / + for each button size. Enable MOVE MODE to drag buttons to any position.");
        info.setTextColor(0xffd8d8d8);
        info.setTextSize(13);
        info.setPadding(0, 0, 0, dp(8));
        content.addView(info);

        CheckBox move = new CheckBox(activity);
        move.setText("MOVE MODE (drag overlay keys instead of sending them)");
        move.setTextColor(0xffeeeeee);
        move.setButtonTintList(checkBoxTint());
        move.setChecked(editMode);
        content.addView(move);

        CheckBox resetPositions = new CheckBox(activity);
        resetPositions.setText("Reset positions of selected keys");
        resetPositions.setTextColor(0xffeeeeee);
        resetPositions.setButtonTintList(checkBoxTint());
        content.addView(resetPositions);

        final int[] backgroundOpacity = new int[] {
                clamp(prefs.getInt(PREF_BG_OPACITY, 100), 0, 100)
        };
        TextView opacityLabel = new TextView(activity);
        opacityLabel.setText("KEY BACKGROUND OPACITY: " + backgroundOpacity[0] + "%");
        opacityLabel.setTextColor(0xffeeeeee);
        opacityLabel.setTextSize(13);
        opacityLabel.setPadding(0, dp(8), 0, 0);
        content.addView(opacityLabel);

        android.widget.SeekBar opacity = new android.widget.SeekBar(activity);
        opacity.setMax(100);
        opacity.setProgress(backgroundOpacity[0]);
        content.addView(opacity, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView opacityInfo = new TextView(activity);
        opacityInfo.setText("0% = transparent key background; labels stay fully visible. HOLD state remains visible through the label color.");
        opacityInfo.setTextColor(0xffbdbdbd);
        opacityInfo.setTextSize(12);
        opacityInfo.setPadding(0, 0, 0, dp(8));
        content.addView(opacityInfo);
        opacity.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                backgroundOpacity[0] = clamp(progress, 0, 100);
                opacityLabel.setText("KEY BACKGROUND OPACITY: " + backgroundOpacity[0] + "%");
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) { }
        });

        for (KeySpec spec : KEY_SPECS) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            CheckBox on = new CheckBox(activity);
            on.setText(displayLabel(spec));
            on.setTextColor(0xffeeeeee);
            on.setButtonTintList(checkBoxTint());
            on.setChecked(enabled.contains(spec.id));
            row.addView(on, new LinearLayout.LayoutParams(0, dp(42), 1f));

            CheckBox hold = new CheckBox(activity);
            hold.setText("HOLD");
            hold.setTextColor(0xffeeeeee);
            hold.setButtonTintList(checkBoxTint());
            hold.setChecked(prefs.getBoolean(PREF_HOLD_PREFIX + spec.id, false));
            row.addView(hold, new LinearLayout.LayoutParams(dp(78), dp(42)));

            int initialSize = clamp(prefs.getInt(PREF_SIZE_PREFIX + spec.id, 100),
                    KEY_SIZE_MIN_PERCENT, KEY_SIZE_MAX_PERCENT);
            sizeValues.put(spec.id, initialSize);

            Button smaller = sizeAdjustButton("−");
            TextView sizeLabel = new TextView(activity);
            sizeLabel.setText(initialSize + "%");
            sizeLabel.setTextColor(0xffeeeeee);
            sizeLabel.setTextSize(12);
            sizeLabel.setGravity(Gravity.CENTER);
            Button larger = sizeAdjustButton("+");

            smaller.setContentDescription(displayLabel(spec) + " smaller");
            larger.setContentDescription(displayLabel(spec) + " larger");
            smaller.setOnClickListener(v -> {
                int value = clamp(sizeValues.get(spec.id) - KEY_SIZE_STEP_PERCENT,
                        KEY_SIZE_MIN_PERCENT, KEY_SIZE_MAX_PERCENT);
                sizeValues.put(spec.id, value);
                sizeLabel.setText(value + "%");
            });
            larger.setOnClickListener(v -> {
                int value = clamp(sizeValues.get(spec.id) + KEY_SIZE_STEP_PERCENT,
                        KEY_SIZE_MIN_PERCENT, KEY_SIZE_MAX_PERCENT);
                sizeValues.put(spec.id, value);
                sizeLabel.setText(value + "%");
            });

            row.addView(smaller, new LinearLayout.LayoutParams(dp(36), dp(36)));
            row.addView(sizeLabel, new LinearLayout.LayoutParams(dp(48), dp(42)));
            row.addView(larger, new LinearLayout.LayoutParams(dp(36), dp(36)));

            enableChecks.put(spec.id, on);
            holdChecks.put(spec.id, hold);
            content.addView(row);
        }

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.setBackgroundColor(0xff151515);
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView dialogTitle = new TextView(activity);
        dialogTitle.setText("GAME KEY OVERLAY");
        dialogTitle.setTextColor(0xfff5f5f5);
        dialogTitle.setTextSize(20);
        dialogTitle.setPadding(dp(20), dp(16), dp(20), dp(10));
        dialogTitle.setBackgroundColor(0xff151515);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setCustomTitle(dialogTitle)
                .setView(scroll)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Apply", null)
                .create();
        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null)
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(0xff151515));
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(0xfff0f0f0);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(0xffc8c8c8);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            releaseAll();
            Set<String> newEnabled = new LinkedHashSet<>();
            SharedPreferences.Editor e = prefs.edit();
            for (KeySpec spec : KEY_SPECS) {
                CheckBox on = enableChecks.get(spec.id);
                CheckBox hold = holdChecks.get(spec.id);
                if (on != null && on.isChecked()) newEnabled.add(spec.id);
                e.putBoolean(PREF_HOLD_PREFIX + spec.id, hold != null && hold.isChecked());
                Integer size = sizeValues.get(spec.id);
                e.putInt(PREF_SIZE_PREFIX + spec.id, clamp(size == null ? 100 : size,
                        KEY_SIZE_MIN_PERCENT, KEY_SIZE_MAX_PERCENT));
                if (resetPositions.isChecked()) {
                    e.remove(PREF_X_PREFIX + spec.id);
                    e.remove(PREF_Y_PREFIX + spec.id);
                }
            }
            editMode = move.isChecked();
            e.putStringSet(PREF_KEYS, newEnabled);
            e.putBoolean(PREF_EDIT, editMode);
            e.putInt(PREF_BG_OPACITY, backgroundOpacity[0]);
            e.apply();
            rebuild();
            Toast.makeText(activity,
                    editMode ? "Key overlay MOVE MODE: drag keys, then disable MOVE MODE when finished" :
                            (newEnabled.isEmpty() ? "Game key overlay disabled" : "Game key overlay updated"),
                    Toast.LENGTH_LONG).show();
            dialog.dismiss();
            });
        });
        dialog.show();
    }

    /**
     * Box86Activity splits full-screen touchscreen gestures by pointer ID before
     * Android's ViewGroup routing. Ordinary VM fingers must NOT be swallowed by
     * a full-screen overlay when another finger is holding a game key.
     */
    boolean supportsIndependentTouch() {
        return !editMode && getVisibility() == View.VISIBLE && !buttons.isEmpty();
    }

    boolean captureIndependentPointer(int pointerId, float windowX, float windowY) {
        if (!supportsIndependentTouch() || isolatedTouchOwners.owns(pointerId)) return false;
        // Reverse draw order: larger/overlapping buttons select the topmost key.
        List<Map.Entry<String, Button>> entries = new ArrayList<>(buttons.entrySet());
        for (int i = entries.size() - 1; i >= 0; --i) {
            Map.Entry<String, Button> entry = entries.get(i);
            Button button = entry.getValue();
            if (button.getVisibility() != View.VISIBLE || button.getWidth() <= 0 || button.getHeight() <= 0)
                continue;
            int[] position = new int[2];
            button.getLocationInWindow(position);
            if (windowX < position[0] || windowX >= position[0] + button.getWidth() ||
                    windowY < position[1] || windowY >= position[1] + button.getHeight())
                continue;

            KeySpec spec = find(entry.getKey());
            if (spec == null) continue;
            isolatedTouchOwners.capture(pointerId, spec.id);
            button.setPressed(true);
            if (!prefs.getBoolean(PREF_HOLD_PREFIX + spec.id, false) && momentaryDown.add(spec.id))
                safeSend(spec, true);
            return true;
        }
        return false;
    }

    void finishIndependentPointer(int pointerId, boolean canceled) {
        String id = isolatedTouchOwners.release(pointerId);
        if (id == null) return;
        // Do not release a key until the LAST finger on that key has lifted.
        if (isolatedTouchOwners.anyPointerOnKey(id)) return;
        Button button = buttons.get(id);
        if (button != null) button.setPressed(false);
        KeySpec spec = find(id);
        if (spec == null) return;
        if (prefs.getBoolean(PREF_HOLD_PREFIX + id, false)) {
            // HOLD toggles only on a completed tap, never on ACTION_CANCEL.
            if (!canceled) {
                if (latched.remove(id)) safeSend(spec, false);
                else { latched.add(id); safeSend(spec, true); }
                updateButtonStates();
            }
        } else if (momentaryDown.remove(id)) {
            safeSend(spec, false);
        }
    }

    void cancelIndependentPointers() {
        for (Integer pointerId : isolatedTouchOwners.pointerIds())
            finishIndependentPointer(pointerId, true);
    }

    private void rebuild() {
        releaseAll();
        removeAllViews();
        buttons.clear();
        Set<String> enabled = new LinkedHashSet<>(prefs.getStringSet(PREF_KEYS, new LinkedHashSet<>()));
        int ordinal = 0;
        for (KeySpec spec : KEY_SPECS) {
            if (!enabled.contains(spec.id)) continue;
            Button b = createButton(spec, ordinal++);
            buttons.put(spec.id, b);
            addView(b);
            b.post(() -> applySavedPosition(spec, b));
        }
        updateButtonStates();
    }

    private Button createButton(KeySpec spec, int ordinal) {
        Button b = new Button(activity);
        b.setText(displayLabel(spec));
        b.setContentDescription(spec.mouseMask == 1 ? "Left mouse button" :
                spec.mouseMask == 2 ? "Right mouse button" : displayLabel(spec));
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setPadding(dp(3), 0, dp(3), 0);
        b.setMinWidth(0);
        b.setMinHeight(0);
        b.setBackground(buttonBackground(false));
        int width = (spec.id.length() >= 5 ? 64 : 52);
        int sizePercent = clamp(prefs.getInt(PREF_SIZE_PREFIX + spec.id, 100),
                KEY_SIZE_MIN_PERCENT, KEY_SIZE_MAX_PERCENT);
        int scaledWidth = Math.max(1, Math.round(width * (sizePercent / 100f)));
        int scaledHeight = Math.max(1, Math.round(46 * (sizePercent / 100f)));
        float baseTextSize = spec.id.length() >= 5 ? 9f : 12f;
        b.setTextSize(Math.max(7f, baseTextSize * (sizePercent / 100f)));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(scaledWidth), dp(scaledHeight), Gravity.START | Gravity.TOP);
        lp.leftMargin = dp(10 + (ordinal % 5) * 58);
        lp.topMargin = dp(84 + (ordinal / 5) * 52);
        b.setLayoutParams(lp);
        b.setOnTouchListener((view, event) -> handleButtonTouch(spec, b, event));
        return b;
    }

    private boolean handleButtonTouch(KeySpec spec, Button b, MotionEvent event) {
        if (editMode) return handleMoveTouch(spec, b, event);
        boolean hold = prefs.getBoolean(PREF_HOLD_PREFIX + spec.id, false);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                b.setPressed(true);
                if (!hold && momentaryDown.add(spec.id)) safeSend(spec, true);
                return true;
            case MotionEvent.ACTION_UP:
                b.setPressed(false);
                if (hold) {
                    if (latched.remove(spec.id)) safeSend(spec, false);
                    else { latched.add(spec.id); safeSend(spec, true); }
                    updateButtonStates();
                } else if (momentaryDown.remove(spec.id)) {
                    safeSend(spec, false);
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                b.setPressed(false);
                if (!hold && momentaryDown.remove(spec.id)) safeSend(spec, false);
                return true;
            default:
                return true;
        }
    }

    private float moveDownRawX, moveDownRawY;
    private int moveStartX, moveStartY;

    private boolean handleMoveTouch(KeySpec spec, Button b, MotionEvent event) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) b.getLayoutParams();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                moveDownRawX = event.getRawX();
                moveDownRawY = event.getRawY();
                moveStartX = lp.leftMargin;
                moveStartY = lp.topMargin;
                b.setPressed(true);
                return true;
            case MotionEvent.ACTION_MOVE: {
                int nx = moveStartX + Math.round(event.getRawX() - moveDownRawX);
                int ny = moveStartY + Math.round(event.getRawY() - moveDownRawY);
                int maxX = Math.max(0, getWidth() - b.getWidth());
                int maxY = Math.max(0, getHeight() - b.getHeight());
                lp.leftMargin = clamp(nx, 0, maxX);
                lp.topMargin = clamp(ny, 0, maxY);
                b.setLayoutParams(lp);
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                b.setPressed(false);
                savePosition(spec, b);
                return true;
            default:
                return true;
        }
    }

    private void savePosition(KeySpec spec, Button b) {
        if (getWidth() <= b.getWidth() || getHeight() <= b.getHeight()) return;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) b.getLayoutParams();
        int xp = Math.round(lp.leftMargin * 1000f / Math.max(1, getWidth() - b.getWidth()));
        int yp = Math.round(lp.topMargin * 1000f / Math.max(1, getHeight() - b.getHeight()));
        prefs.edit().putInt(PREF_X_PREFIX + spec.id, clamp(xp, 0, 1000))
                .putInt(PREF_Y_PREFIX + spec.id, clamp(yp, 0, 1000)).apply();
    }

    private void applySavedPosition(KeySpec spec, Button b) {
        String xk = PREF_X_PREFIX + spec.id;
        String yk = PREF_Y_PREFIX + spec.id;
        if (!prefs.contains(xk) || !prefs.contains(yk) || getWidth() <= 0 || getHeight() <= 0) return;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) b.getLayoutParams();
        int xp = clamp(prefs.getInt(xk, 0), 0, 1000);
        int yp = clamp(prefs.getInt(yk, 0), 0, 1000);
        lp.leftMargin = Math.round((getWidth() - b.getWidth()) * (xp / 1000f));
        lp.topMargin = Math.round((getHeight() - b.getHeight()) * (yp / 1000f));
        b.setLayoutParams(lp);
    }

    private void updateButtonStates() {
        for (Map.Entry<String, Button> entry : buttons.entrySet()) {
            boolean on = latched.contains(entry.getKey());
            Button b = entry.getValue();
            b.setBackground(buttonBackground(on));
            b.setTextColor(on ? 0xff80cbc4 : Color.WHITE);
            b.setAlpha(editMode ? 0.78f : 1.0f);
        }
    }



    private Button sizeAdjustButton(String label) {
        Button button = new Button(activity);
        button.setText(label);
        button.setTextSize(18);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setPadding(0, 0, 0, 0);
        button.setBackground(buttonBackground(false));
        return button;
    }

    private ColorStateList checkBoxTint() {
        return new ColorStateList(
                new int[][] {
                        new int[] { android.R.attr.state_checked },
                        new int[] {}
                },
                new int[] { 0xff80cbc4, 0xff9e9e9e });
    }

    private StateListDrawable buttonBackground(boolean latchedOn) {
        int opacityPercent = clamp(prefs.getInt(PREF_BG_OPACITY, 100), 0, 100);
        int alpha = Math.round(255f * (opacityPercent / 100f));
        int normalRgb = latchedOn ? 0x00355f56 : 0x00202020;
        int pressedRgb = latchedOn ? 0x00477a6f : 0x00383838;
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] { android.R.attr.state_pressed }, keyBackground(withAlpha(pressedRgb, alpha), opacityPercent));
        states.addState(new int[] {}, keyBackground(withAlpha(normalRgb, alpha), opacityPercent));
        return states;
    }

    private GradientDrawable keyBackground(int color, int opacityPercent) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(6));
        // Keep a faint outline even at 0% so a fully transparent key can still be located.
        int strokeAlpha = Math.max(72, Math.round(255f * (opacityPercent / 100f)));
        bg.setStroke(dp(1), Color.argb(strokeAlpha, 0x6a, 0x6a, 0x6a));
        return bg;
    }

    private static int withAlpha(int rgb, int alpha) {
        return Color.argb(clamp(alpha, 0, 255), Color.red(rgb), Color.green(rgb), Color.blue(rgb));
    }

    private void safeSend(KeySpec spec, boolean down) {
        try {
            if (spec.mouseMask != 0) listener.onMouseButton(spec.mouseMask, down);
            else listener.onKey(resolveXt(spec), down);
        } catch (Throwable ignored) { }
    }

    private int resolveXt(KeySpec spec) {
        if (!german) return spec.xt;
        if ("Y".equals(spec.id)) return 0x2c;
        if ("Z".equals(spec.id)) return 0x15;
        return spec.xt;
    }

    private String displayLabel(KeySpec spec) {
        switch (spec.id) {
            case "UP": return "↑";
            case "DOWN": return "↓";
            case "LEFT": return "←";
            case "RIGHT": return "→";
            case "MOUSE_LEFT": return german ? "MAUS L" : "L-CLICK";
            case "MOUSE_RIGHT": return german ? "MAUS R" : "R-CLICK";
            default: return spec.id;
        }
    }

    private static KeySpec find(String id) {
        for (KeySpec spec : KEY_SPECS) if (spec.id.equals(id)) return spec;
        return null;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
