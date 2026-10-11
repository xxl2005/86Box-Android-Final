package net.box86.android;

/**
 * Action mapping for Android multitouch when some fingers operate overlay keys
 * while others operate the emulated PC. Pure Java so its edge cases are
 * testable with the host JDK without an Android runtime.
 */
final class TouchStreamAction {
    // Stable MotionEvent masked action numbers (DOWN, UP, MOVE, CANCEL,
    // POINTER_DOWN, POINTER_UP). Android handles the pointer index separately.
    static final int DOWN = 0;
    static final int UP = 1;
    static final int MOVE = 2;
    static final int CANCEL = 3;
    static final int POINTER_DOWN = 5;
    static final int POINTER_UP = 6;

    private TouchStreamAction() { }

    static int forVm(int androidAction, int vmCountIncludingChanged, boolean changedIsVm) {
        if (androidAction == CANCEL) return CANCEL;
        if (androidAction == MOVE) return MOVE;
        if (changedIsVm && (androidAction == DOWN || androidAction == POINTER_DOWN))
            return vmCountIncludingChanged == 1 ? DOWN : POINTER_DOWN;
        if (changedIsVm && (androidAction == UP || androidAction == POINTER_UP))
            return vmCountIncludingChanged == 1 ? UP : POINTER_UP;
        // A key-only finger change must not be interpreted as a VM button press.
        return MOVE;
    }
}
