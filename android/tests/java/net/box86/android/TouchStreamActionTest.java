package net.box86.android;

/** Execute with the host JDK; validates the action translator used by the app. */
public final class TouchStreamActionTest {
    private static int cases = 0;

    private static void expect(String title, int expected, int actual) {
        cases++;
        if (expected != actual) throw new AssertionError(title + ": expected " + expected + " got " + actual);
    }

    public static void main(String[] args) {
        // Finger 1 holds LEFT; finger 2 starts touching the VM (its own DOWN).
        expect("VM starts second", TouchStreamAction.DOWN,
                TouchStreamAction.forVm(TouchStreamAction.POINTER_DOWN, 1, true));
        // VM finger 2 continues while a third finger holds SPACE (only MOVE for VM).
        expect("overlay down while VM held", TouchStreamAction.MOVE,
                TouchStreamAction.forVm(TouchStreamAction.POINTER_DOWN, 1, false));
        // Two VM fingers + an overlay finger all coexisting.
        expect("second VM finger down", TouchStreamAction.POINTER_DOWN,
                TouchStreamAction.forVm(TouchStreamAction.POINTER_DOWN, 2, true));
        expect("second VM finger up", TouchStreamAction.POINTER_UP,
                TouchStreamAction.forVm(TouchStreamAction.POINTER_UP, 2, true));
        expect("last VM finger up", TouchStreamAction.UP,
                TouchStreamAction.forVm(TouchStreamAction.POINTER_UP, 1, true));
        expect("overlay up while VM held", TouchStreamAction.MOVE,
                TouchStreamAction.forVm(TouchStreamAction.POINTER_UP, 1, false));
        expect("VM motion", TouchStreamAction.MOVE,
                TouchStreamAction.forVm(TouchStreamAction.MOVE, 2, false));
        expect("cancel releases VM", TouchStreamAction.CANCEL,
                TouchStreamAction.forVm(TouchStreamAction.CANCEL, 2, false));
        expect("first VM finger", TouchStreamAction.DOWN,
                TouchStreamAction.forVm(TouchStreamAction.DOWN, 1, true));
        expect("last VM finger", TouchStreamAction.UP,
                TouchStreamAction.forVm(TouchStreamAction.UP, 1, true));
        System.out.println("PASS: " + cases + " touchscreen action cases");
    }
}
