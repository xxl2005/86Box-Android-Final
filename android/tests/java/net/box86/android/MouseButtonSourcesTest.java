package net.box86.android;

import java.util.ArrayList;
import java.util.List;

/** Host-JVM tests for physical/overlay mouse ownership and clean releases. */
public final class MouseButtonSourcesTest {
    private static int cases;
    private static void check(String label, boolean ok) {
        cases++;
        if (!ok) throw new AssertionError(label);
    }
    public static void main(String[] args) {
        MouseButtonSources m = new MouseButtonSources();
        List<String> sent = new ArrayList<>();
        MouseButtonSources.Sender output = (bit, down) -> sent.add(bit + ":" + down);

        m.setOverlayButton(1, true, output);
        check("left touch presses mouse", sent.size() == 1 && "1:true".equals(sent.get(0)));
        m.setPhysicalMask(1, output);
        check("physical + overlay left co-exist", sent.size() == 1);
        m.setOverlayButton(1, false, output);
        check("overlay up cannot release held physical left", sent.size() == 1 && m.effectiveMask() == 1);
        m.setPhysicalMask(0, output);
        check("last owner releases left", sent.size() == 2 && "1:false".equals(sent.get(1)));

        m.setOverlayButton(1, true, output);
        m.setOverlayButton(2, true, output);
        check("both overlay buttons simultaneous", m.effectiveMask() == 3);
        m.setOverlayButton(1, false, output);
        check("right remains down after left release", m.effectiveMask() == 2);
        m.setOverlayButton(2, false, output);
        check("both released after final right release", m.effectiveMask() == 0);

        int n = sent.size();
        m.setOverlayButton(0, true, output);
        m.setOverlayButton(4, true, output);
        check("invalid overlay masks ignored", sent.size() == n && m.effectiveMask() == 0);
        m.setPhysicalMask(1 | 2 | 4, output);
        check("physical middle mouse still supported", m.effectiveMask() == 7);
        m.setOverlayButton(1, true, output);
        m.setPhysicalMask(0, output);
        check("physical mouse release keeps overlay left", m.effectiveMask() == 1);
        m.reset();
        check("native global release resets both sources", m.effectiveMask() == 0);
        m.setOverlayButton(1, true, output);
        check("new overlay press works after native reset", m.effectiveMask() == 1 && "1:true".equals(sent.get(sent.size() - 1)));
        System.out.println("PASS: " + cases + " independent mouse-button source cases");
    }
}
