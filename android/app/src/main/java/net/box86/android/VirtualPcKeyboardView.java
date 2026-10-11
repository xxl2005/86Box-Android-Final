package net.box86.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Direct-PC virtual keyboard for Android. It emits the same XT scan codes as
 * the physical-keyboard bridge and therefore does not depend on IME text
 * commits or SDL's soft keyboard.
 *
 * v0.9.27: responsive screen fitting + persistent manual size scaling.
 */
final class VirtualPcKeyboardView extends FrameLayout {
    interface Listener {
        void onKey(int xtCode, boolean down);
        void onClose();
        void onOpenSystemKeyboard();
    }

    private static final String PREF_SCALE = "virtual_keyboard_scale_percent";
    private static final int SCALE_MIN = 50;
    private static final int SCALE_MAX = 140;
    private static final int SCALE_STEP = 10;

    private final Listener listener;
    private final SharedPreferences prefs;
    private final LinearLayout rows;
    private final Set<Integer> heldMomentary = new LinkedHashSet<>();
    private final Set<Integer> latchedModifiers = new LinkedHashSet<>();
    private final Map<Integer, Button> modifierButtons = new LinkedHashMap<>();
    private final ArrayList<Button> momentaryButtons = new ArrayList<>();
    private boolean german;
    private boolean modifierLock;
    private int userScalePercent;
    private float effectiveScale = 1.0f;
    private Button modifierLockButton;
    private Button scaleButton;

    VirtualPcKeyboardView(Context context, Listener listener, boolean german, SharedPreferences prefs) {
        super(context);
        this.listener = listener;
        this.german = german;
        this.prefs = prefs;
        this.userScalePercent = clamp(prefs != null ? prefs.getInt(PREF_SCALE, 100) : 100,
                SCALE_MIN, SCALE_MAX);
        effectiveScale = calculateEffectiveScale();

        setBackgroundColor(0xee101010);
        setMotionEventSplittingEnabled(true);
        setPadding(px(4), px(4), px(4), px(4));

        ScrollView vertical = new ScrollView(context);
        vertical.setFillViewport(false);
        vertical.setMotionEventSplittingEnabled(true);
        vertical.setVerticalScrollBarEnabled(true);
        vertical.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

        rows = new LinearLayout(context);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setMotionEventSplittingEnabled(true);
        vertical.addView(rows, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(vertical, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        rebuild();
    }

    void setGermanLayout(boolean german) {
        if (this.german == german) return;
        releaseAll();
        this.german = german;
        rebuild();
    }

    void refreshForScreen() {
        float next = calculateEffectiveScale();
        if (Math.abs(next - effectiveScale) < 0.015f) return;
        releaseAll();
        effectiveScale = next;
        setPadding(px(4), px(4), px(4), px(4));
        rebuild();
    }

    int getUserScalePercent() {
        return userScalePercent;
    }

    void releaseAll() {
        for (Integer xt : new LinkedHashSet<>(heldMomentary)) safeSend(xt, false);
        heldMomentary.clear();
        // Cancel visual effects too when Android focus/configuration changes.
        for (Button b : momentaryButtons) {
            b.animate().cancel();
            b.setScaleX(1f);
            b.setScaleY(1f);
            b.setPressed(false);
        }
        for (Integer xt : new LinkedHashSet<>(latchedModifiers)) safeSend(xt, false);
        latchedModifiers.clear();
        updateModifierButtons();
    }

    private void changeUserScale(int delta) {
        int next = clamp(userScalePercent + delta, SCALE_MIN, SCALE_MAX);
        if (next == userScalePercent) return;
        releaseAll();
        userScalePercent = next;
        if (prefs != null) prefs.edit().putInt(PREF_SCALE, userScalePercent).apply();
        effectiveScale = calculateEffectiveScale();
        rebuild();
    }

    private void resetUserScale() {
        if (userScalePercent == 100) return;
        releaseAll();
        userScalePercent = 100;
        if (prefs != null) prefs.edit().putInt(PREF_SCALE, userScalePercent).apply();
        effectiveScale = calculateEffectiveScale();
        rebuild();
    }

    private void rebuild() {
        rows.removeAllViews();
        modifierButtons.clear();
        momentaryButtons.clear();
        latchedModifiers.clear();
        heldMomentary.clear();

        // Header line 1: title + close. Keeping this separate avoids cramped
        // controls on narrow phone screens.
        LinearLayout titleRow = new LinearLayout(getContext());
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.setPadding(0, 0, 0, px(2));

        TextView title = new TextView(getContext());
        title.setText(german ? "PC KEYBOARD · DE/QWERTZ" : "PC KEYBOARD · US/QWERTY");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSp(11.5f));
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(px(8), 0, px(8), 0);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, px(34), 1f));
        titleRow.addView(controlButton("✕", v -> listener.onClose()));
        rows.addView(titleRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Header line 2: modifiers / manual scaler / IME.
        LinearLayout controls = new LinearLayout(getContext());
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setPadding(0, 0, 0, px(2));

        modifierLockButton = controlButton(modifierLock ? "MOD: ON" : "MOD: OFF", v -> {
            modifierLock = !modifierLock;
            modifierLockButton.setText(modifierLock ? "MOD: ON" : "MOD: OFF");
        });
        controls.addView(modifierLockButton, weightedControl(1.6f));
        controls.addView(controlButton("−", v -> changeUserScale(-SCALE_STEP)), weightedControl(0.65f));
        scaleButton = controlButton(userScalePercent + "%", v -> resetUserScale());
        controls.addView(scaleButton, weightedControl(1.0f));
        controls.addView(controlButton("+", v -> changeUserScale(SCALE_STEP)), weightedControl(0.65f));
        controls.addView(controlButton("IME", v -> listener.onOpenSystemKeyboard()), weightedControl(1.0f));
        rows.addView(controls, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addRow(new Key("Esc",0x01), new Key("F1",0x3b), new Key("F2",0x3c), new Key("F3",0x3d),
                new Key("F4",0x3e), new Key("F5",0x3f), new Key("F6",0x40), new Key("F7",0x41),
                new Key("F8",0x42), new Key("F9",0x43), new Key("F10",0x44), new Key("F11",0x57),
                new Key("F12",0x58), new Key("PrtSc",0x137), new Key("Pause",0x145), new Key("ScrLk",0x46));

        addRow(new Key("`",0x29), new Key("1",0x02), new Key("2",0x03), new Key("3",0x04),
                new Key("4",0x05), new Key("5",0x06), new Key("6",0x07), new Key("7",0x08),
                new Key("8",0x09), new Key("9",0x0a), new Key("0",0x0b), new Key("-",0x0c),
                new Key("=",0x0d), new Key("Backspace",0x0e, 88));

        addRow(new Key("Tab",0x0f, 64), new Key("Q",0x10), new Key("W",0x11), new Key("E",0x12),
                new Key("R",0x13), new Key("T",0x14), new Key("Y",0x15), new Key("U",0x16),
                new Key("I",0x17), new Key("O",0x18), new Key("P",0x19), new Key("[",0x1a),
                new Key("]",0x1b), new Key("\\",0x2b));

        addRow(new Key("Caps",0x3a, 64), new Key("A",0x1e), new Key("S",0x1f), new Key("D",0x20),
                new Key("F",0x21), new Key("G",0x22), new Key("H",0x23), new Key("J",0x24),
                new Key("K",0x25), new Key("L",0x26), new Key(";",0x27), new Key("'",0x28),
                new Key("Enter",0x1c, 84));

        addRow(new Key("Shift",0x2a, 72, true), new Key("<>",0x56), new Key("Z",0x2c), new Key("X",0x2d),
                new Key("C",0x2e), new Key("V",0x2f), new Key("B",0x30), new Key("N",0x31),
                new Key("M",0x32), new Key(",",0x33), new Key(".",0x34), new Key("/",0x35),
                new Key("RShift",0x36, 76, true));

        addRow(new Key("Ctrl",0x1d, 64, true), new Key("Win",0x15b,64,true), new Key("Alt",0x38,64,true),
                new Key("Space",0x39,190), new Key("AltGr",0x138,72,true), new Key("Menu",0x15d,64,true),
                new Key("RCtrl",0x11d,68,true));

        addRow(new Key("Ins",0x152), new Key("Home",0x147), new Key("PgUp",0x149),
                new Key("Del",0x153), new Key("End",0x14f), new Key("PgDn",0x151),
                new Key("↑",0x148), new Key("←",0x14b), new Key("↓",0x150), new Key("→",0x14d));

        addRow(new Key("Num",0x45), new Key("/",0x135), new Key("*",0x37), new Key("-",0x4a),
                new Key("7",0x47), new Key("8",0x48), new Key("9",0x49), new Key("+",0x4e),
                new Key("4",0x4b), new Key("5",0x4c), new Key("6",0x4d),
                new Key("1",0x4f), new Key("2",0x50), new Key("3",0x51),
                new Key("0",0x52,76), new Key(".",0x53), new Key("NumEnter",0x11c,82));
    }

    private void addRow(Key... keys) {
        // Every row always fits the current screen width. Key widthDp is used
        // only as a relative weight, so no horizontal scrolling is required.
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setMotionEventSplittingEnabled(true);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, px(1), 0, px(1));
        for (Key spec : keys) row.addView(makeKey(spec));
        rows.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, px(44)));
    }

    private Button makeKey(Key spec) {
        Button b = new Button(getContext());
        b.setText(layoutLabel(spec));
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSp(11.0f));
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setBackgroundTintList(keyTint(false));
        // For virtual buttons we consume touch ourselves, so Android's default
        // Button pressed-state/ripple would not necessarily fire. Explicit
        // pressed tint + an inexpensive view-scale pulse is reliable.
        b.setStateListAnimator(null);
        b.setClickable(true);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(px(2), 0, px(2), 0);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, px(41), Math.max(1, spec.widthDp));
        lp.setMargins(px(1), 0, px(1), 0);
        b.setLayoutParams(lp);
        if (spec.modifier) {
            modifierButtons.put(spec.xt, b);
            b.setOnClickListener(v -> {
                toggleModifier(spec.xt);
                flashModifier(b);
            });
        } else {
            momentaryButtons.add(b);
            b.setOnTouchListener((view, event) -> {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        animateKeyDown(b);
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        animateKeyUp(b);
                        break;
                    default:
                        break;
                }
                return handleMomentary(spec.xt, event);
            });
        }
        return b;
    }

    private String layoutLabel(Key k) {
        if (!german) return k.label;
        switch (k.xt) {
            case 0x29: return "^";
            case 0x0c: return "ß";
            case 0x0d: return "´";
            case 0x15: return "Z";
            case 0x2c: return "Y";
            case 0x1a: return "Ü";
            case 0x1b: return "+";
            case 0x2b: return "#";
            case 0x27: return "Ö";
            case 0x28: return "Ä";
            case 0x35: return "-";
            default: return k.label;
        }
    }

    private boolean handleMomentary(int xt, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (heldMomentary.add(xt)) safeSend(xt, true);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (heldMomentary.remove(xt)) safeSend(xt, false);
                if (!modifierLock) releaseLatchedModifiers();
                return true;
            default:
                return true;
        }
    }

    // Visual-only feedback. The actual XT scancode is sent synchronously in
    // handleMomentary/toggleModifier without waiting for either animation.
    private void animateKeyDown(Button b) {
        b.animate().cancel();
        b.setPressed(true);
        b.animate().scaleX(0.94f).scaleY(0.94f).setDuration(55).start();
    }

    private void animateKeyUp(Button b) {
        b.animate().cancel();
        b.setPressed(false);
        b.animate().scaleX(1f).scaleY(1f).setDuration(115).start();
    }

    private void flashModifier(Button b) {
        b.animate().cancel();
        b.setScaleX(0.94f);
        b.setScaleY(0.94f);
        b.animate().scaleX(1f).scaleY(1f).setDuration(115).start();
    }

    private void toggleModifier(int xt) {
        if (latchedModifiers.remove(xt)) {
            safeSend(xt, false);
        } else {
            latchedModifiers.add(xt);
            safeSend(xt, true);
        }
        updateModifierButtons();
    }

    private void releaseLatchedModifiers() {
        for (Integer xt : new LinkedHashSet<>(latchedModifiers)) safeSend(xt, false);
        latchedModifiers.clear();
        updateModifierButtons();
    }

    private void updateModifierButtons() {
        for (Map.Entry<Integer, Button> e : modifierButtons.entrySet()) {
            boolean on = latchedModifiers.contains(e.getKey());
            e.getValue().setAlpha(1.0f);
            e.getValue().setBackgroundTintList(keyTint(on));
        }
    }

    private Button controlButton(String text, View.OnClickListener listener) {
        Button b = new Button(getContext());
        b.setText(text);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSp(10.0f));
        b.setTextColor(Color.WHITE);
        b.setBackgroundTintList(controlTint());
        b.setAllCaps(false);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(px(4), 0, px(4), 0);
        b.setOnClickListener(listener);
        b.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(34)));
        return b;
    }

    private LinearLayout.LayoutParams weightedControl(float weight) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, px(34), weight);
        lp.setMargins(px(1), 0, px(1), 0);
        return lp;
    }

    private ColorStateList keyTint(boolean latched) {
        int normal = latched ? 0xff356c51 : 0xff202020;
        int pressed = latched ? 0xff48a473 : 0xff278ec8;
        return new ColorStateList(
                new int[][] { new int[] { android.R.attr.state_pressed }, new int[] {} },
                new int[] { pressed, normal });
    }

    private ColorStateList controlTint() {
        return new ColorStateList(
                new int[][] { new int[] { android.R.attr.state_pressed }, new int[] {} },
                new int[] { 0xff454545, 0xff292929 });
    }

    private void safeSend(int xt, boolean down) {
        try { listener.onKey(xt, down); } catch (Throwable ignored) { }
    }

    /**
     * Automatic fitting is based on both screen width and screen height. The
     * manual percentage then scales that fitted size, so the keyboard stays
     * usable across phones/tablets/resolutions while still allowing the user
     * to make it deliberately smaller or larger.
     */
    private float calculateEffectiveScale() {
        Configuration c = getResources().getConfiguration();
        float widthDp = c.screenWidthDp > 0 ? c.screenWidthDp : 800f;
        float heightDp = c.screenHeightDp > 0 ? c.screenHeightDp : 420f;
        float widthFit = widthDp / 920f;
        float heightFit = (heightDp * 0.82f) / 410f;
        float autoFit = Math.min(1.0f, Math.min(widthFit, heightFit));
        autoFit = Math.max(0.55f, autoFit);
        float manual = clamp(userScalePercent, SCALE_MIN, SCALE_MAX) / 100.0f;
        return Math.max(0.42f, Math.min(1.40f, autoFit * manual));
    }

    private int px(int baseDp) {
        return Math.max(1, Math.round(baseDp * effectiveScale * getResources().getDisplayMetrics().density));
    }

    private float textSp(float baseSp) {
        return Math.max(7.0f, Math.min(15.0f, baseSp * effectiveScale));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class Key {
        final String label;
        final int xt;
        final int widthDp;
        final boolean modifier;
        Key(String label, int xt) { this(label, xt, 50, false); }
        Key(String label, int xt, int widthDp) { this(label, xt, widthDp, false); }
        Key(String label, int xt, int widthDp, boolean modifier) {
            this.label = label;
            this.xt = xt;
            this.widthDp = widthDp;
            this.modifier = modifier;
        }
    }
}
