package net.box86.android;

public final class KeyTouchTrackerTest {
    private static int cases = 0;
    private static void check(String title, boolean value) {
        cases++;
        if (!value) throw new AssertionError(title);
    }

    public static void main(String[] args) {
        KeyTouchTracker tracker = new KeyTouchTracker();
        check("first finger captures LEFT", tracker.capture(2, "LEFT"));
        check("second finger captures UP", tracker.capture(6, "UP"));
        check("third finger captures LEFT", tracker.capture(11, "LEFT"));
        check("cannot reassign already held pointer", !tracker.capture(2, "SPACE"));
        check("all three pointers tracked", tracker.pointerIds().size() == 3);
        check("first LEFT released", "LEFT".equals(tracker.release(2)));
        check("LEFT still held by third finger", tracker.anyPointerOnKey("LEFT"));
        check("UP still held", tracker.anyPointerOnKey("UP"));
        check("last LEFT released", "LEFT".equals(tracker.release(11)));
        check("LEFT no longer held", !tracker.anyPointerOnKey("LEFT"));
        check("UP unaffected", tracker.anyPointerOnKey("UP"));
        check("UP released", "UP".equals(tracker.release(6)));
        check("all released", tracker.pointerIds().isEmpty());
        check("pointer IDs may be reused after up", tracker.capture(2, "SPACE"));
        tracker.clear();
        check("clear cancels all pointers", tracker.pointerIds().isEmpty());
        check("mouse-left captured", tracker.capture(3, "MOUSE_LEFT"));
        check("mouse-right captured at same time", tracker.capture(5, "MOUSE_RIGHT"));
        check("second finger can hold mouse-left", tracker.capture(7, "MOUSE_LEFT"));
        check("first left released", "MOUSE_LEFT".equals(tracker.release(3)));
        check("second left finger keeps left pressed", tracker.anyPointerOnKey("MOUSE_LEFT"));
        check("right still held independently", tracker.anyPointerOnKey("MOUSE_RIGHT"));
        check("last left released", "MOUSE_LEFT".equals(tracker.release(7)));
        check("right not accidentally released", tracker.anyPointerOnKey("MOUSE_RIGHT"));
        check("right released", "MOUSE_RIGHT".equals(tracker.release(5)));
        check("no mouse fingers remain", tracker.pointerIds().isEmpty());
        System.out.println("PASS: " + cases + " key-pointer ownership cases");
    }
}
