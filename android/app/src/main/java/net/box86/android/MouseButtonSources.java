package net.box86.android;

/** Keeps independently held physical and on-screen mouse buttons from releasing each other. */
final class MouseButtonSources {
    interface Sender { void send(int mouseMask, boolean down); }

    private int physicalMask;
    private int overlayMask;

    void setPhysicalMask(int mask, Sender sender) {
        int before = effectiveMask();
        physicalMask = mask & 31;
        sendTransitions(before, effectiveMask(), sender);
    }

    void setOverlayButton(int mask, boolean down, Sender sender) {
        if (mask != 1 && mask != 2) return;
        int before = effectiveMask();
        overlayMask = down ? overlayMask | mask : overlayMask & ~mask;
        sendTransitions(before, effectiveMask(), sender);
    }

    int effectiveMask() { return physicalMask | overlayMask; }

    /** Use only together with the native RELEASE_ALL event. */
    void reset() {
        physicalMask = 0;
        overlayMask = 0;
    }

    private static void sendTransitions(int before, int after, Sender sender) {
        int changed = before ^ after;
        for (int bit : new int[] { 1, 2, 4, 8, 16 }) {
            if ((changed & bit) != 0) sender.send(bit, (after & bit) != 0);
        }
    }
}
