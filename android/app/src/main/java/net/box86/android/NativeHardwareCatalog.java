package net.box86.android;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Read-only hardware catalogue exposed directly by the compiled 86Box core.
 *
 * The Android UI deliberately does not duplicate the huge 86Box hardware
 * tables. New machines/devices added to the core automatically become visible
 * here after rebuilding the APK.
 */
final class NativeHardwareCatalog {
    static final int MACHINE_TYPES = 1;
    static final int MACHINES = 2;
    static final int CPU_FAMILIES = 3;
    static final int CPU_SPEEDS = 4;
    static final int FPUS = 5;
    static final int VIDEO = 10;
    static final int SOUND = 11;
    static final int NETWORK = 12;
    static final int MOUSE = 13;
    static final int HDC = 14;
    static final int SCSI = 15;
    static final int FDC = 16;
    static final int MIDI_OUT = 17;
    static final int CDROM_INTERFACE = 18;
    static final int CHAR_COM = 19;
    static final int CHAR_LPT = 20;
    static final int ISA_RTC = 21;
    static final int ISA_MEM = 22;
    static final int MCA_MEM = 23;
    static final int ISA_ROM = 24;

    static final class Item {
        final String label;
        final String value;
        final int id;
        final String[] extra;

        Item(String[] f) {
            label = f.length > 0 ? f[0] : "";
            value = f.length > 1 ? f[1] : "";
            id = parseInt(f.length > 2 ? f[2] : "-1", -1);
            if (f.length > 3) {
                extra = new String[f.length - 3];
                System.arraycopy(f, 3, extra, 0, extra.length);
            } else {
                extra = new String[0];
            }
        }

        int extraInt(int index, int def) {
            return index >= 0 && index < extra.length ? parseInt(extra[index], def) : def;
        }

        long extraLong(int index, long def) {
            if (index < 0 || index >= extra.length) return def;
            try { return Long.parseLong(extra[index]); } catch (Exception ignored) { return def; }
        }

        double extraDouble(int index, double def) {
            if (index < 0 || index >= extra.length) return def;
            try { return Double.parseDouble(extra[index]); } catch (Exception ignored) { return def; }
        }

        @Override public String toString() { return label; }
    }

    private static boolean loaded;
    private static String loadError = "";

    static {
        try {
            System.loadLibrary("SDL3");
            System.loadLibrary("main");
            loaded = true;
        } catch (Throwable t) {
            loaded = false;
            loadError = t.toString();
        }
    }

    private NativeHardwareCatalog() { }

    private static native String nativeCatalog(int kind, int machineId, int itemId, int slot, boolean includeIncompatible);
    private static native boolean nativeRuntimeActive();

    static boolean isRuntimeActive() {
        if (!loaded) return false;
        try { return nativeRuntimeActive(); }
        catch (Throwable t) { return false; }
    }

    static boolean isAvailable() { return loaded; }
    static String getLoadError() { return loadError; }

    static List<Item> get(int kind, int machineId, int itemId, int slot) {
        return getInternal(kind, machineId, itemId, slot, false);
    }

    static List<Item> getAll(int kind, int machineId, int itemId, int slot) {
        return getInternal(kind, machineId, itemId, slot, true);
    }

    static boolean probe() {
        if (!loaded) return false;
        List<Item> types = get(MACHINE_TYPES, -1, -1, 0);
        List<Item> machines = get(MACHINES, -1, -1, 0);
        if (types.isEmpty() || machines.size() < 10) {
            if (loadError == null || loadError.isEmpty())
                loadError = "86Box core catalogue returned no machine table";
            return false;
        }
        return true;
    }

    private static List<Item> getInternal(int kind, int machineId, int itemId, int slot, boolean includeIncompatible) {
        if (!loaded) return Collections.emptyList();
        try {
            String raw = nativeCatalog(kind, machineId, itemId, slot, includeIncompatible);
            if (raw == null || raw.isEmpty()) return Collections.emptyList();
            ArrayList<Item> out = new ArrayList<>();
            for (String line : raw.split("\n")) {
                if (line == null || line.isEmpty()) continue;
                out.add(new Item(line.split("\t", -1)));
            }
            return out;
        } catch (Throwable t) {
            loadError = t.toString();
            return Collections.emptyList();
        }
    }

    static Item findValue(List<Item> items, String value) {
        if (items == null || value == null) return null;
        for (Item item : items)
            if (item.value.equalsIgnoreCase(value)) return item;
        return null;
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception ignored) { return def; }
    }
}
